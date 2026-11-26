package com.snakechase.app.engine

import com.snakechase.app.domain.model.ComboState
import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.ScoreState
import com.snakechase.app.domain.model.SnakeEntity
import com.snakechase.app.levels.ScoringConfig
import kotlin.math.max
import kotlin.math.min

object ScoringEngine {

    data class NearMissResult(
        val isNearMiss: Boolean,
        val isPerfect: Boolean,
        val intensity: Float,
        val pointsAwarded: Int,
    )

    /**
     * Checks if the player is grazing closely to snake segments or head.
     * Near miss band: distance between player edge and snake edge is <= 36px.
     * Perfect near miss band: <= 14px.
     */
    fun evaluateNearMiss(
        player: PlayerOrb,
        snake: SnakeEntity,
        config: ScoringConfig,
        currentMultiplier: Float,
        /** Orb skin perk: scales how close counts as a graze (1 = standard). */
        reach: Float = 1f,
    ): NearMissResult {
        var minEdgeDist = Float.MAX_VALUE

        // Check head
        val headDist = CollisionDetector.getCircleEdgeDistance(
            player.position,
            player.radius,
            snake.headPosition,
            snake.headRadius,
        )
        if (headDist in 0f..minEdgeDist) minEdgeDist = headDist

        // Check body segments
        for (seg in snake.segments) {
            val segDist = CollisionDetector.getCircleEdgeDistance(
                player.position,
                player.radius,
                seg.position,
                snake.bodyRadius,
            )
            if (segDist in 0f..minEdgeDist) minEdgeDist = segDist
        }

        return when {
            minEdgeDist <= 14f * reach -> {
                val base = config.nearMissBasePoints * config.perfectMultiplier
                NearMissResult(
                    isNearMiss = true,
                    isPerfect = true,
                    intensity = 1f,
                    pointsAwarded = (base * currentMultiplier).toInt(),
                )
            }
            minEdgeDist <= 38f * reach -> {
                val intensity = (1f - ((minEdgeDist - 14f * reach) / (24f * reach))).coerceIn(0f, 1f)
                NearMissResult(
                    isNearMiss = true,
                    isPerfect = false,
                    intensity = intensity,
                    pointsAwarded = (config.nearMissBasePoints * currentMultiplier).toInt(),
                )
            }
            else -> NearMissResult(false, false, 0f, 0)
        }
    }

    /**
     * Updates ongoing score, survival points per frame, combo decays, and near-miss triggers.
     */
    fun tickScore(
        currentScore: ScoreState,
        nearMiss: NearMissResult,
        stageBonusToAdd: Int,
        config: ScoringConfig,
        deltaTimeSec: Float,
        deltaTimeMs: Long,
        totalSurvivalTimeMs: Long = 0L,
        greedMultiplier: Float = 1.0f,
    ): ScoreState {
        // 1. Survival points calculated from total elapsed survival time + greed multiplier
        val baseSurvival = if (totalSurvivalTimeMs > 0L) {
            (config.survivalPointsPerSec * (totalSurvivalTimeMs / 1000f)).toInt()
        } else {
            val survivalAdd = (config.survivalPointsPerSec * deltaTimeSec).toInt()
            currentScore.breakdown.survivalPoints + survivalAdd
        }
        val bonusGreed = if (greedMultiplier > 1.0f) {
            currentScore.breakdown.survivalPoints + (config.survivalPointsPerSec * deltaTimeSec * (greedMultiplier - 1.0f)).toInt()
        } else {
            baseSurvival
        }
        val newSurvivalTotal = max(baseSurvival, bonusGreed)

        // 2. Stage bonus
        val newStageTotal = currentScore.breakdown.stageBonusPoints + stageBonusToAdd

        // 3. Near miss points & combo handling
        var newNearMissTotal = currentScore.breakdown.nearMissPoints
        var combo = currentScore.combo

        if (nearMiss.isNearMiss && nearMiss.pointsAwarded > 0) {
            newNearMissTotal = (newNearMissTotal + nearMiss.pointsAwarded).coerceAtMost(config.nearMissCap)

            // Advance combo tier
            val nextTier = min(config.comboTiers.size - 1, combo.tier + 1)
            combo = ComboState(
                multiplier = config.comboTiers[nextTier],
                tier = nextTier,
                count = combo.count + 1,
                remainingTimeMs = config.comboDecayMs,
                maxDurationMs = config.comboDecayMs,
            )
        } else {
            // Decay combo timer
            if (combo.remainingTimeMs > 0) {
                val rem = combo.remainingTimeMs - deltaTimeMs
                if (rem <= 0) {
                    combo = ComboState(multiplier = 1f, tier = 0, count = 0, remainingTimeMs = 0L)
                } else {
                    combo = combo.copy(remainingTimeMs = rem)
                }
            }
        }

        val rawTotal = newSurvivalTotal + newStageTotal + newNearMissTotal + currentScore.breakdown.completionBonus
        val total = min(config.levelScoreMax, rawTotal)

        return currentScore.copy(
            total = total,
            breakdown = currentScore.breakdown.copy(
                survivalPoints = newSurvivalTotal,
                stageBonusPoints = newStageTotal,
                nearMissPoints = newNearMissTotal,
            ),
            combo = combo,
            personalBest = max(currentScore.personalBest, total),
        )
    }
}
