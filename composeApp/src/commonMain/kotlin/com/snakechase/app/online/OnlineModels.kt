package com.snakechase.app.online

/**
 * One player's online duel: round 1 FLEE (prey vs the AI snake, survive as long as possible)
 * and round 2 HUNT (snake vs the AI prey, catch it as fast as possible). Both players face the
 * same deterministic AI, so the two runs are directly comparable.
 */
data class OnlineRun(
    val fleeMs: Long,
    val huntMs: Long,
    val ghostFlee: GhostTrack = GhostTrack.EMPTY,
    val ghostHunt: GhostTrack = GhostTrack.EMPTY,
) {
    companion object {
        const val MAX_ROUND_MS = 45_000L

        /** Benchmark used when nobody is waiting: a solid but beatable run, no ghost. */
        val HOUSE = OnlineRun(fleeMs = 20_000L, huntMs = 8_000L)
        const val HOUSE_NAME = "House Ghost"
    }
}

/** A challenge we claimed: the opponent already played, their run is fixed. */
data class OnlineOpponent(
    val matchId: String,
    val name: String,
    val uid: String,
    val run: OnlineRun,
)

/** Result of comparing two runs, from "my" point of view. */
data class OnlineVerdict(
    val iWon: Boolean,
    val wonFlee: Boolean,
    val wonHunt: Boolean,
    /** Combined margin in ms (positive = in my favour); used to break a 1–1 split. */
    val marginMs: Long,
)

/** A finished challenge I hosted, shown in the results inbox. */
data class InboxResult(
    val matchId: String,
    val opponentName: String,
    val myRun: OnlineRun,
    val theirRun: OnlineRun,
    val iWon: Boolean,
)

data class LeaderboardRow(
    val name: String,
    val levelId: Int,
    val score: Long,
    val survivalMs: Long,
    /** "MODERATE" / "HARD"; null on rows from before difficulties existed. */
    val difficulty: String? = null,
)
