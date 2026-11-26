package com.snakechase.app

import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.GameEngine
import kotlin.test.Test
import kotlin.test.assertTrue

/** Pointing (or tapping) far away must not teleport the orb: it travels at a capped speed. */
class OrbTopSpeedTest {
    @Test
    fun aFarTarget_isReachedAtTopSpeed_notInstantly() {
        for (level in listOf(1, 4)) {
            var s = GameEngine.startLevel(level, GameState()).copy(phase = GamePhase.PLAYING, godModeEnabled = true)
            val start = s.player.position
            val far = Vec2(if (start.x < 400f) 780f else 20f, start.y)
            repeat(10) { s = GameEngine.tick(s, far, 16L).newState }
            val moved = (s.player.position - start).length()
            val limit = GameEngine.ORB_MAX_SPEED * 0.160f + 1f
            assertTrue(moved <= limit, "level $level: moved $moved in 160 ms, limit $limit")
            assertTrue(moved > 20f, "level $level: the orb should still be moving ($moved)")
        }
    }
}
