package com.snakechase.app.online

/**
 * Turns raw online score rows into a fair "world top" list: one row per player (their best),
 * and rows that the current rules make impossible (a score above the level's maximum, or no
 * survival time, both left over from older versions of the game) are dropped, as are Easy runs.
 */
object LeaderboardRules {
    fun clean(rows: List<LeaderboardRow>, maxScoreFor: (levelId: Int) -> Int, limit: Int = 25): List<LeaderboardRow> = rows
        .filter { it.survivalMs > 0 && it.score in 1..maxScoreFor(it.levelId).toLong() }
        .filter { it.difficulty != "EASY" } // Easy runs are for practice; never on the world board.
        .groupBy { it.name.trim().lowercase() }
        .map { (_, mine) -> mine.maxWith(compareBy<LeaderboardRow> { it.score }.thenBy { -it.survivalMs }) }
        .sortedWith(compareByDescending<LeaderboardRow> { it.score }.thenBy { it.survivalMs })
        .take(limit)
}
