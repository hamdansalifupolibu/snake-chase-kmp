package com.snakechase.app

import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.GameEngine
import com.snakechase.app.engine.Vortex
import com.snakechase.app.levels.LevelRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VortexTest {
    private val whirl = Obstacle.Cyclone(id = "w", position = Vec2(400f, 300f), visualRadius = 110f, spinDir = 1f)

    @Test
    fun theWaterWorld_hasExactlyOneWhirlpool() {
        assertEquals(1, LevelRegistry.getById(3).obstacleConfig.cyclones.size)
    }

    @Test
    fun aVortex_pullsInAndSpins_andDoesNothingOutsideItsReach() {
        val pos = Vec2(470f, 300f) // right of the eye
        val d = Vortex.drift(pos, listOf(whirl), 0.1f)
        assertTrue(d.x < 0f, "pulled toward the eye")
        assertTrue(d.y > 0f, "spun around it")
        assertEquals(Vec2.ZERO, Vortex.drift(Vec2(700f, 300f), listOf(whirl), 0.1f))
    }

    @Test
    fun swimmingIntoAVortex_isNotDeath_butItCarriesTheOrb() {
        var s = GameEngine.startLevel(3, GameState()).copy(phase = GamePhase.PLAYING)
        s = s.copy(
            obstacles = listOf(whirl),
            primarySnake = s.primarySnake?.copy(headPosition = Vec2(60f, 560f)),
            player = s.player.copy(position = Vec2(430f, 300f), targetPosition = Vec2(430f, 300f)),
        )
        val hold = Vec2(430f, 300f)
        repeat(20) { s = GameEngine.tick(s, hold, 16L).newState }
        assertEquals(GamePhase.PLAYING, s.phase, "a whirlpool must not kill the orb")
        assertNotEquals(hold, s.player.position, "the orb is carried off its spot")
    }
}
