package com.snakechase.app

import com.snakechase.app.domain.model.ActivePowerUp
import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.PowerUpType
import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.GameEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Walls, bars and blocks are solid for the orb as well as the snakes. Only the invincible
 * shield orb lets you pass through them; a dash protects you but still bumps into them.
 */
class OrbSolidsTest {
    private val wall = Obstacle.Wall(id = "w", rect = Rect(400f, 200f, 30f, 200f))
    private val bar = Obstacle.RotatingBar(id = "b", center = Vec2(420f, 300f), length = 260f, currentAngleRad = (kotlin.math.PI / 2).toFloat(), angularVelocityRad = 0f)

    private fun runInto(obstacle: Obstacle, shield: Boolean, dash: Boolean): GameState {
        var s = GameEngine.startLevel(1, GameState()).copy(phase = GamePhase.PLAYING)
        s = s.copy(obstacles = listOf(obstacle), player = s.player.copy(position = Vec2(300f, 300f), targetPosition = Vec2(300f, 300f)))
        val far = Vec2(600f, 300f)
        repeat(60) {
            val prepared = s.copy(
                activePowerUps = if (shield) listOf(ActivePowerUp(PowerUpType.SHIELD, 5_000L, 5_000L)) else emptyList(),
                player = if (dash) s.player.copy(dashRemainingMs = 500L) else s.player,
                primarySnake = s.primarySnake?.copy(headPosition = Vec2(60f, 560f)),
            )
            s = GameEngine.tick(prepared, far, 16L).newState
        }
        return s
    }

    @Test
    fun dashing_doesNotGhostThroughWallsOrBars() {
        for (obs in listOf(wall, bar)) {
            val s = runInto(obs, shield = false, dash = true)
            assertEquals(GamePhase.PLAYING, s.phase, "a dash still protects you")
            assertTrue(s.player.position.x < 400f, "dashing orb crossed ${obs.id} (x=${s.player.position.x})")
        }
    }

    @Test
    fun theInvincibleShield_passesThroughAnything() {
        for (obs in listOf(wall, bar)) {
            val s = runInto(obs, shield = true, dash = false)
            assertEquals(GamePhase.PLAYING, s.phase)
            assertTrue(s.player.position.x > 450f, "shielded orb should pass ${obs.id} (x=${s.player.position.x})")
        }
    }
}
