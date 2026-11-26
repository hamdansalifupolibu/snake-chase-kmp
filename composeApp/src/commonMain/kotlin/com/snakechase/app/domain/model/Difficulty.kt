package com.snakechase.app.domain.model

import kotlinx.serialization.Serializable

/**
 * How hard levels 1-5 play. Scoring rules are identical on every difficulty; each run carries
 * a badge with its difficulty, and only Moderate and Hard runs go to the online leaderboard.
 */
@Serializable
enum class Difficulty(
    val label: String,
    val blurb: String,
    /** Multiplies every snake's speed. */
    val snakeSpeed: Float,
    /** Share of each obstacle kind that appears (at least one of every kind stays). */
    val obstacleShare: Float,
    /** A power-up orb appears about this often once a run is going. */
    val powerUpEverySec: Long,
    /** Quiet time after an orb is picked up or fades before the next may appear. */
    val powerUpGapMs: Long,
    /** Easy spawns only the power-ups that save you (shield, ghost walk, speed). */
    val helpfulPowerUpsOnly: Boolean,
    val countsOnline: Boolean,
) {
    EASY(
        label = "Easy",
        blurb = "Slower snakes, fewer obstacles and plenty of helpful power-ups.",
        snakeSpeed = 0.74f,
        obstacleShare = 0.5f,
        powerUpEverySec = 9L,
        powerUpGapMs = 6_000L,
        helpfulPowerUpsOnly = true,
        countsOnline = false,
    ),
    MODERATE(
        label = "Moderate",
        blurb = "The intended challenge. A fair fight for everyone.",
        snakeSpeed = 0.88f,
        obstacleShare = 0.75f,
        powerUpEverySec = 14L,
        powerUpGapMs = 9_000L,
        helpfulPowerUpsOnly = false,
        countsOnline = true,
    ),
    HARD(
        label = "Hard",
        blurb = "Every obstacle and full-speed snakes. For experts.",
        snakeSpeed = 1.0f,
        obstacleShare = 1.0f,
        powerUpEverySec = 20L,
        powerUpGapMs = 12_000L,
        helpfulPowerUpsOnly = false,
        countsOnline = true,
    ),
    ;

    /** Snake speed on this level. Easy ramps gently from Level 1 (55%) to Level 5 (72%). */
    fun snakeSpeedFor(levelId: Int): Float = if (this == EASY) EASY_SPEED_BY_LEVEL.getOrElse(levelId - 1) { 0.72f } else snakeSpeed

    /** Share of each obstacle kind kept on this level. Easy: a third on Level 1, half by Level 5. */
    fun obstacleShareFor(levelId: Int): Float = if (this == EASY) EASY_OBSTACLES_BY_LEVEL.getOrElse(levelId - 1) { 0.5f } else obstacleShare

    companion object {
        val RECOMMENDED = MODERATE

        private val EASY_SPEED_BY_LEVEL = listOf(0.55f, 0.60f, 0.64f, 0.68f, 0.72f)
        private val EASY_OBSTACLES_BY_LEVEL = listOf(0.34f, 0.4f, 0.45f, 0.5f, 0.5f)

        /**
         * Easy snakes are lazier, not just slower (Pac-Man style): wider turns, barely any
         * prediction of where you're heading, no flanking ambush, and a longer warm-up.
         */
        fun easySnake(p: com.snakechase.app.levels.SnakePersonalityConfig) = p.copy(
            baseTurnRate = p.baseTurnRate * 0.75f,
            maxTurnRate = p.maxTurnRate * 0.72f,
            lookaheadMs = (p.lookaheadMs * 0.35f).toLong(),
            flankOffsetPx = 0f,
            warmUpDurationMs = p.warmUpDurationMs * 2,
        )

        fun byName(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: RECOMMENDED
    }
}
