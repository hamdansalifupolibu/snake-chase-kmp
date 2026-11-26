package com.snakechase.app.online

import com.snakechase.app.network.firebase.FirestoreDocs
import com.snakechase.app.network.firebase.OnlineException
import com.snakechase.app.network.firebase.ServerTimestamp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Asynchronous "ghost duel" matchmaking on Firestore.
 *
 *  findOpponent → claims a waiting challenge (optimistic concurrency, so two players can never
 *                 claim the same one) → play against the stored run → finishMatch.
 *  No opponent  → play against the house benchmark → postChallenge, so the next player
 *                 meets *your* ghost and you get the result in the inbox.
 *
 * Queries use equality filters only (status, tier, day), so no composite indexes are needed.
 */
class OnlineDuelRepository(
    private val db: FirestoreDocs,
    /** Signs in if needed and returns this install's Firebase uid. */
    private val currentUid: suspend () -> String,
    private val clock: Clock = Clock.System,
) {

    suspend fun myUid(): String = currentUid()

    /** Finds and claims someone else's open challenge in [tier], or returns null. */
    suspend fun findOpponent(tier: Int, myName: String): OnlineOpponent? {
        val me = currentUid()
        val now = clock.now()
        val days = listOf(dayKey(now), dayKey(now - 1.days))

        val open = days.flatMap { d -> db.query(MATCHES, mapOf("status" to "open", "tier" to tier.toLong(), "day" to d.toLong())) }
        // A guest who claimed but never finished releases the challenge after a timeout.
        val stale = days.flatMap { d -> db.query(MATCHES, mapOf("status" to "claimed", "tier" to tier.toLong(), "day" to d.toLong())) }
            .filter { doc -> (doc.fields["claimedAt"] as? Instant)?.let { now - it > CLAIM_TIMEOUT } == true }

        val candidates = (open + stale)
            .filter { it.fields["hostUid"] != me }
            .filter { (it.fields["createdAt"] as? Instant)?.let { c -> now - c < 1.days } ?: true }
            .shuffled() // spread players across challenges instead of all racing for the newest

        for (doc in candidates) {
            val hostRun = parseRun(doc.fields["hostRun"]) ?: continue
            val claimed = db.update(
                MATCHES,
                doc.id,
                mapOf(
                    "status" to "claimed",
                    "guestUid" to me,
                    "guestName" to myName.take(MAX_NAME),
                    "claimedAt" to ServerTimestamp,
                ),
                ifUpdateTime = doc.updateTime,
            )
            if (claimed) {
                return OnlineOpponent(
                    matchId = doc.id,
                    name = doc.fields["hostName"] as? String ?: "Rival",
                    uid = doc.fields["hostUid"] as? String ?: "",
                    run = hostRun,
                )
            }
            // Someone else claimed it first — try the next one.
        }
        return null
    }

    /** Publishes my run as an open challenge for the next player in [tier]. */
    suspend fun postChallenge(tier: Int, myName: String, run: OnlineRun): String {
        val id = FirestoreDocs.newId()
        db.create(
            MATCHES,
            id,
            mapOf(
                "status" to "open",
                "tier" to tier.toLong(),
                "day" to dayKey(clock.now()).toLong(),
                "hostUid" to currentUid(),
                "hostName" to myName.take(MAX_NAME),
                "hostRun" to encodeRun(run),
                "createdAt" to ServerTimestamp,
            ),
        )
        return id
    }

    /** Records my run on a claimed challenge and settles it. */
    suspend fun finishMatch(opponent: OnlineOpponent, run: OnlineRun, iWon: Boolean) {
        val me = currentUid()
        val ok = db.update(
            MATCHES,
            opponent.matchId,
            mapOf(
                "status" to "done",
                "guestRun" to encodeRun(run),
                "winnerUid" to if (iWon) me else opponent.uid,
                "finishedAt" to ServerTimestamp,
            ),
            ifUpdateTime = null,
        )
        if (!ok) throw OnlineException("That challenge expired before you finished.")
    }

    /** My hosted challenges that someone has played. */
    suspend fun inbox(limit: Int = 10): List<InboxResult> {
        val me = currentUid()
        return db.query(MATCHES, mapOf("hostUid" to me, "status" to "done"), limit = limit).mapNotNull { doc ->
            val mine = parseRun(doc.fields["hostRun"]) ?: return@mapNotNull null
            val theirs = parseRun(doc.fields["guestRun"]) ?: return@mapNotNull null
            InboxResult(
                matchId = doc.id,
                opponentName = doc.fields["guestName"] as? String ?: "Rival",
                myRun = mine,
                theirRun = theirs,
                iWon = doc.fields["winnerUid"] == me,
            )
        }
    }

    suspend fun submitScore(name: String, levelId: Int, score: Long, survivalMs: Long, difficulty: String = "MODERATE") {
        db.create(
            SCORES,
            FirestoreDocs.newId(),
            mapOf(
                "uid" to currentUid(),
                "name" to name.take(MAX_NAME),
                "levelId" to levelId.toLong(),
                "score" to score,
                "survivalMs" to survivalMs,
                "difficulty" to difficulty,
                "createdAt" to ServerTimestamp,
            ),
        )
    }

    /** Mirrors the public part of my profile (never coins) to profiles/{uid}. */
    suspend fun saveProfile(p: com.snakechase.app.profile.PlayerProfile) {
        if (!p.isSetUp) return
        val me = currentUid()
        val fields = mapOf(
            "name" to p.name.take(MAX_NAME),
            "avatar" to p.avatar.toLong(),
            "duelsWon" to p.duelsWon.toLong(),
            "duelsLost" to p.duelsLost.toLong(),
            "bestWinStreak" to p.bestWinStreak.toLong(),
            "updatedAt" to ServerTimestamp,
        )
        val updated = db.get(PROFILES, me) != null && db.update(PROFILES, me, fields, ifUpdateTime = null)
        if (!updated) db.create(PROFILES, me, fields)
    }

    /**
     * The best scores, newest rules only: rows above the highest possible level score (left over
     * from an older scoring system) are excluded by the query itself, so they can never crowd
     * real players out of the list.
     */
    suspend fun topScores(limit: Int = 25, maxScore: Long? = null): List<LeaderboardRow> = db.query(SCORES, emptyMap(), orderByDesc = "score", limit = limit, maxValue = maxScore).map { doc ->
        LeaderboardRow(
            name = doc.fields["name"] as? String ?: "Player",
            levelId = (doc.fields["levelId"] as? Long)?.toInt() ?: 1,
            score = doc.fields["score"] as? Long ?: 0L,
            survivalMs = doc.fields["survivalMs"] as? Long ?: 0L,
            difficulty = doc.fields["difficulty"] as? String,
        )
    }

    companion object {
        const val MATCHES = "matches"
        const val SCORES = "scores"
        const val PROFILES = "profiles"
        const val MAX_NAME = 24
        val CLAIM_TIMEOUT = 10.minutes

        /** UTC calendar day as yyyymmdd, so queries can bucket by day without an index. */
        fun dayKey(instant: Instant): Int {
            val d = instant.toLocalDateTime(TimeZone.UTC).date
            return d.year * 10_000 + d.monthNumber * 100 + d.dayOfMonth
        }

        fun encodeRun(run: OnlineRun): Map<String, Any?> = mapOf(
            "fleeMs" to run.fleeMs.coerceIn(0, OnlineRun.MAX_ROUND_MS),
            "huntMs" to run.huntMs.coerceIn(0, OnlineRun.MAX_ROUND_MS),
            "ghostFlee" to run.ghostFlee.encode(),
            "ghostHunt" to run.ghostHunt.encode(),
        )

        fun parseRun(raw: Any?): OnlineRun? {
            @Suppress("UNCHECKED_CAST")
            val m = raw as? Map<String, Any?> ?: return null
            val flee = m["fleeMs"] as? Long ?: return null
            val hunt = m["huntMs"] as? Long ?: return null
            return OnlineRun(
                fleeMs = flee,
                huntMs = hunt,
                ghostFlee = GhostTrack.decode(m["ghostFlee"] as? String),
                ghostHunt = GhostTrack.decode(m["ghostHunt"] as? String),
            )
        }
    }
}
