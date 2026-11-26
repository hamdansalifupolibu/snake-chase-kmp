package com.snakechase.app.levels

import kotlin.math.roundToInt

/**
 * How a level's maximum score is shared out, the same way for every level:
 *
 *  - 35% for reaching stages (later stages are worth more, in proportion to each stage's
 *    designed bonus), 20% for survival time, 5% for clearing the level: a clean clear with no
 *    grazing scores 60%, one star;
 *  - the last 40% only comes from grazing the snakes (one graze per pass, times the combo).
 *    Some daring gets you to 70% (two stars), a lot of it to 90% (three).
 *
 * Before this, stage bonuses alone added up to several times the maximum, so every run hit the
 * cap within seconds and scores, stars and "new best" meant nothing.
 */
object ScoreBudget {
    const val STAGES = 0.35f
    const val SURVIVAL = 0.20f
    const val COMPLETION = 0.05f
    const val GRAZING = 0.40f

    /** A new graze can only score again after this long (one graze per pass, not per frame). */
    const val GRAZE_COOLDOWN_MS = 400L

    /** Points for reaching [stageIndex] (stage 0 is where you start, so it pays nothing). */
    fun stageBonus(level: LevelConfig, stageIndex: Int): Int {
        if (stageIndex <= 0 || stageIndex >= level.subStages.size) return 0
        val weights = level.subStages.drop(1).map { it.bonus.coerceAtLeast(1) }
        val total = weights.sum().toFloat()
        val budget = level.scoringConfig.levelScoreMax * STAGES

        // Cumulative rounding, so the stage bonuses always add up to exactly the budget.
        fun upTo(n: Int) = (weights.take(n).sum() / total * budget).roundToInt()
        return upTo(stageIndex) - upTo(stageIndex - 1)
    }

    fun survivalPerSec(level: LevelConfig): Float = level.scoringConfig.levelScoreMax * SURVIVAL / (level.completionTimeMs / 1000f)

    fun completionBonus(level: LevelConfig): Int = (level.scoringConfig.levelScoreMax * COMPLETION).roundToInt()

    /** The level's scoring config with the budgeted survival rate and graze points filled in. */
    fun config(level: LevelConfig): ScoringConfig = level.scoringConfig.copy(
        survivalPointsPerSec = survivalPerSec(level),
        nearMissBasePoints = grazePoints(level),
        completionBonus = completionBonus(level),
        nearMissCap = (level.scoringConfig.levelScoreMax * GRAZING).roundToInt(),
    )

    /** Base points for one graze (doubled when perfect, then times the combo multiplier). */
    fun grazePoints(level: LevelConfig): Int = (level.scoringConfig.levelScoreMax / 100).coerceAtLeast(1)
}
