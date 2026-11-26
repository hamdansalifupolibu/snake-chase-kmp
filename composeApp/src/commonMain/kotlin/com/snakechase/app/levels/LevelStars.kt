package com.snakechase.app.levels

/**
 * Star rating for clearing a level. Clearing always earns at least one star;
 * scoring 70% of the level's target earns two, 90% earns three.
 * Levels you haven't cleared have no stars at all.
 */
object LevelStars {
    const val TWO_STAR_PCT = 0.70f
    const val THREE_STAR_PCT = 0.90f

    fun forClear(score: Int, levelScoreMax: Int): Int {
        val pct = if (levelScoreMax > 0) score.toFloat() / levelScoreMax else 1f
        return when {
            pct >= THREE_STAR_PCT -> 3
            pct >= TWO_STAR_PCT -> 2
            else -> 1
        }
    }
}
