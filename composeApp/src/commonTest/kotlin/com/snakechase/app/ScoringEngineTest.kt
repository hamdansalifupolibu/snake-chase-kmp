package com.snakechase.app

import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.ScoreState
import com.snakechase.app.domain.model.SnakeRole
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.ScoringEngine
import com.snakechase.app.engine.SnakeAI
import com.snakechase.app.levels.Level1NeonDrift
import com.snakechase.app.levels.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoringEngineTest {

    @Test
    fun testNearMissEvaluation_whenClose_detectsNearMiss() {
        val config = Level1NeonDrift.snakeConfig
        val snake = SnakeAI.createInitialSnake(SnakeRole.PRIMARY, Vec2(100f, 100f), config)
        // Player at (135, 100): head radius is 22, player radius 16 -> total 38. Distance is 35 -> edge distance ~0-3 -> Perfect near miss!
        val player = PlayerOrb(position = Vec2(142f, 100f), radius = 16f)

        val result = ScoringEngine.evaluateNearMiss(player, snake, Level1NeonDrift.scoringConfig, currentMultiplier = 1f)
        assertTrue(result.isNearMiss)
        assertTrue(result.pointsAwarded > 0)
    }

    @Test
    fun testTickScore_survivalAccumulates() {
        val scoringConfig = ScoringConfig(survivalPointsPerSec = 10f)
        val initial = ScoreState()
        val noNearMiss = ScoringEngine.NearMissResult(false, false, 0f, 0)

        val updated = ScoringEngine.tickScore(
            currentScore = initial,
            nearMiss = noNearMiss,
            stageBonusToAdd = 0,
            config = scoringConfig,
            deltaTimeSec = 1.0f,
            deltaTimeMs = 1000L,
        )

        assertEquals(10, updated.breakdown.survivalPoints)
        assertEquals(10, updated.total)
    }
}
