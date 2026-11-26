package com.snakechase.app

import com.snakechase.app.domain.model.ActivePowerUp
import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.PowerUpType
import com.snakechase.app.engine.GameEngine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every power-up must end when its time is up (the shield used to renew itself forever). */
class PowerUpTimingTest {
    @Test
    fun everyPowerUp_endsWhenItsTimeIsUp() {
        for (type in PowerUpType.entries) {
            var s = GameEngine.startLevel(1, GameState()).copy(
                phase = GamePhase.PLAYING,
                godModeEnabled = true, // keep the run alive; this test is about the clock only
                activePowerUps = listOf(ActivePowerUp(type, remainingMs = 2_000L, maxMs = 2_000L)),
            )
            var t = 0L
            while (t < 1_500L) {
                s = GameEngine.tick(s, null, 16L).newState
                t += 16L
            }
            assertTrue(s.activePowerUps.any { it.type == type }, "$type still running at 1.5 s")
            if (type == PowerUpType.SHIELD) assertTrue(s.shieldState.isActive)
            while (t < 2_600L) {
                s = GameEngine.tick(s, null, 16L).newState
                t += 16L
            }
            assertFalse(s.activePowerUps.any { it.type == type }, "$type should have ended after 2 s")
            assertFalse(s.shieldState.isActive, "shield state must end with the shield ($type)")
        }
    }
}
