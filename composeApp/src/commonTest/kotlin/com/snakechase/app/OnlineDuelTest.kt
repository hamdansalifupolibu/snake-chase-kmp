package com.snakechase.app

import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.network.firebase.FirestoreCodec
import com.snakechase.app.network.firebase.FirestoreDocs
import com.snakechase.app.network.firebase.FsDoc
import com.snakechase.app.network.firebase.ServerTimestamp
import com.snakechase.app.online.GhostRecorder
import com.snakechase.app.online.GhostTrack
import com.snakechase.app.online.OnlineDuelRepository
import com.snakechase.app.online.OnlineDuelRules
import com.snakechase.app.online.OnlineRun
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory Firestore with the same optimistic-concurrency semantics as the real one:
 * update(ifUpdateTime) succeeds only if the document is unchanged since it was read.
 */
class FakeFirestore(private val clock: Clock) : FirestoreDocs {
    val docs = mutableMapOf<String, MutableMap<String, FsDoc>>()
    private var version = 0

    private fun nextVersion() = "v${++version}"
    private fun resolve(fields: Map<String, Any?>) = fields.mapValues { (_, v) -> if (v is ServerTimestamp) clock.now() else v }

    override suspend fun create(collection: String, id: String, fields: Map<String, Any?>): FsDoc {
        val col = docs.getOrPut(collection) { mutableMapOf() }
        check(id !in col) { "exists" }
        return FsDoc(id, resolve(fields), nextVersion()).also { col[id] = it }
    }

    override suspend fun get(collection: String, id: String) = docs[collection]?.get(id)

    override suspend fun update(collection: String, id: String, fields: Map<String, Any?>, ifUpdateTime: String?): Boolean {
        val col = docs[collection] ?: return false
        val current = col[id] ?: return false
        if (ifUpdateTime != null && current.updateTime != ifUpdateTime) return false
        col[id] = FsDoc(id, current.fields + resolve(fields), nextVersion())
        return true
    }

    override suspend fun query(collection: String, equals: Map<String, Any>, orderByDesc: String?, limit: Int, maxValue: Long?): List<FsDoc> {
        val all = docs[collection]?.values.orEmpty().filter { d -> equals.all { (k, v) -> d.fields[k] == v } }
            .filter { d -> maxValue == null || orderByDesc == null || ((d.fields[orderByDesc] as? Long) ?: 0L) <= maxValue }
        val sorted = if (orderByDesc != null) all.sortedByDescending { it.fields[orderByDesc] as? Long ?: 0L } else all
        return sorted.take(limit)
    }
}

private class FixedClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class OnlineDuelTest {

    private val clock = FixedClock(Instant.parse("2026-11-19T12:00:00Z"))
    private val db = FakeFirestore(clock)
    private fun repoFor(uid: String) = OnlineDuelRepository(db, currentUid = { uid }, clock = clock)

    private val sampleRun = OnlineRun(
        fleeMs = 18_000,
        huntMs = 6_500,
        ghostFlee = GhostTrack(listOf(Vec2(10f, 20f), Vec2(30f, 40f))),
        ghostHunt = GhostTrack(listOf(Vec2(400f, 460f))),
    )

    @Test
    fun postedChallenge_isClaimedByAnotherPlayer_withGhostIntact() = runTest {
        repoFor("alice").postChallenge(tier = 2, myName = "Alice", run = sampleRun)

        val opp = assertNotNull(repoFor("bob").findOpponent(tier = 2, myName = "Bob"))
        assertEquals("Alice", opp.name)
        assertEquals(18_000, opp.run.fleeMs)
        assertEquals(Vec2(30f, 40f), opp.run.ghostFlee.points[1])
        assertEquals("claimed", db.docs["matches"]!!.values.single().fields["status"])
    }

    @Test
    fun playersNeverMatchTheirOwnChallenge_orOtherTiers() = runTest {
        repoFor("alice").postChallenge(tier = 1, myName = "Alice", run = sampleRun)
        assertNull(repoFor("alice").findOpponent(tier = 1, myName = "Alice"))
        assertNull(repoFor("bob").findOpponent(tier = 3, myName = "Bob"))
    }

    @Test
    fun aChallengeCanOnlyBeClaimedOnce() = runTest {
        repoFor("alice").postChallenge(tier = 0, myName = "Alice", run = sampleRun)
        assertNotNull(repoFor("bob").findOpponent(0, "Bob"))
        assertNull(repoFor("carol").findOpponent(0, "Carol"), "already claimed by Bob")
    }

    @Test
    fun abandonedClaim_isReleasedAfterTimeout() = runTest {
        repoFor("alice").postChallenge(tier = 0, myName = "Alice", run = sampleRun)
        assertNotNull(repoFor("bob").findOpponent(0, "Bob")) // Bob claims, then quits
        clock.instant += 11.minutes
        val opp = assertNotNull(repoFor("carol").findOpponent(0, "Carol"))
        assertEquals("Alice", opp.name)
    }

    @Test
    fun finishedMatch_showsUpInHostInbox_withWinner() = runTest {
        repoFor("alice").postChallenge(tier = 0, myName = "Alice", run = sampleRun)
        val bob = repoFor("bob")
        val opp = assertNotNull(bob.findOpponent(0, "Bob"))
        bob.finishMatch(opp, OnlineRun(fleeMs = 30_000, huntMs = 5_000), iWon = true)

        val inbox = repoFor("alice").inbox()
        assertEquals(1, inbox.size)
        assertEquals("Bob", inbox[0].opponentName)
        assertEquals(false, inbox[0].iWon)
        assertEquals(30_000, inbox[0].theirRun.fleeMs)
    }

    @Test
    fun leaderboard_returnsHighestScoresFirst() = runTest {
        repoFor("a").submitScore("A", 1, 500, 20_000)
        repoFor("b").submitScore("B", 2, 900, 40_000)
        repoFor("c").submitScore("C", 3, 700, 30_000)
        assertEquals(listOf("B", "C", "A"), repoFor("x").topScores().map { it.name })
    }

    @Test
    fun ghost_encodeDecode_roundTrips_andInterpolates() {
        val track = GhostTrack(listOf(Vec2(0f, 0f), Vec2(100f, 50f), Vec2(200f, 50f)))
        val decoded = GhostTrack.decode(track.encode())
        assertEquals(track.points, decoded.points)
        assertEquals(Vec2(50f, 25f), decoded.positionAt(50)) // halfway between sample 0 and 1
        assertEquals(Vec2(200f, 50f), decoded.positionAt(200))
        assertNull(decoded.positionAt(201), "ghost ends with the run")
        assertTrue(GhostTrack.decode("garbage;1,2;x,y").points == listOf(Vec2(1f, 2f)))
    }

    @Test
    fun recorder_samplesAtFixedRate_regardlessOfFrameRate() {
        val r = GhostRecorder()
        var t = 0L
        // ~144 fps frames (7 ms apart) running just past 1 s; the 1001 ms frame fills the 1000 ms slot.
        while (t <= 1_001) {
            r.record(t, Vec2(t.toFloat(), 0f))
            t += 7
        }
        assertEquals(11, r.finish().points.size) // samples at 0,100,…,1000 ms — not one per frame
    }

    @Test
    fun rules_clearWinsAndLosses() {
        val base = OnlineRun(fleeMs = 20_000, huntMs = 8_000)
        assertTrue(OnlineDuelRules.decide(OnlineRun(25_000, 6_000), base).iWon) // better at both
        assertTrue(!OnlineDuelRules.decide(OnlineRun(15_000, 9_000), base).iWon) // worse at both
    }

    @Test
    fun rules_splitIsDecidedByCombinedMargin() {
        val theirs = OnlineRun(fleeMs = 20_000, huntMs = 8_000)
        // Survived 10 s longer but hunted 2 s slower: +8 s overall → win.
        assertTrue(OnlineDuelRules.decide(OnlineRun(30_000, 10_000), theirs).iWon)
        // Survived 1 s longer but hunted 5 s slower: −4 s overall → loss.
        assertTrue(!OnlineDuelRules.decide(OnlineRun(21_000, 13_000), theirs).iWon)
    }

    @Test
    fun rules_exactTieGoesToThePostedRun() {
        val run = OnlineRun(fleeMs = 20_000, huntMs = 8_000)
        assertTrue(!OnlineDuelRules.decide(run, run).iWon, "challenger must beat the ghost, not match it")
    }

    @Test
    fun rules_bothSidesAlwaysAgreeOnExactlyOneWinner() {
        val samples = listOf(0L, 5_000L, 8_000L, 20_000L, 45_000L)
        for (af in samples) {
            for (ah in samples) {
                for (bf in samples) {
                    for (bh in samples) {
                        val a = OnlineRun(af, ah)
                        val b = OnlineRun(bf, bh)
                        val aWins = OnlineDuelRules.decide(a, b).iWon
                        val bWins = OnlineDuelRules.decide(b, a).iWon
                        // Ties: neither side wins as the challenger, so the posted run (the host) wins.
                        assertTrue(!(aWins && bWins), "both can't win: $a vs $b")
                    }
                }
            }
        }
    }

    @Test
    fun firestoreCodec_roundTripsNestedRun() {
        val fields = mapOf("name" to "Alice", "tier" to 3L, "ok" to true, "run" to OnlineDuelRepository.encodeRun(sampleRun))
        val decoded = FirestoreCodec.decodeFields(FirestoreCodec.encodeFields(fields))
        assertEquals("Alice", decoded["name"])
        assertEquals(3L, decoded["tier"])
        assertEquals(sampleRun.fleeMs, OnlineDuelRepository.parseRun(decoded["run"])!!.fleeMs)
    }
}
