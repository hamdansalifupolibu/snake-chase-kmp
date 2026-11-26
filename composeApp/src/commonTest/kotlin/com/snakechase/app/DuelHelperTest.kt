package com.snakechase.app

import com.snakechase.app.domain.model.AiDifficulty
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.MultiplayerRole
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.MultiplayerEngine
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** VS AI: the human prey gets faster against a meaner AI, plus a speed orb now and then. */
class DuelHelperTest {
    @Test
    fun yourSpeedEdgeOverTheAiSnake_shrinksAsTheAiGetsHarder() {
        val edge = AiDifficulty.entries.map { MultiplayerEngine.humanPreySpeed(it) / (260f * MultiplayerEngine.aiSnakeTuning(it).speed) }
        assertTrue(edge[0] > edge[1] && edge[1] > edge[2], "$edge")
        assertTrue(edge[0] > 2f, "on Easy the orb is more than twice as fast as the snake")
    }

    @Test
    fun theAiSnake_isGentlerOnEasierDifficulties() {
        val t = AiDifficulty.entries.map { MultiplayerEngine.aiSnakeTuning(it) }
        assertTrue(t[0].speed < t[1].speed && t[1].speed < t[2].speed, "$t")
        assertTrue(t[0].turnRate < t[2].turnRate && t[0].lookaheadSec < t[2].lookaheadSec)
        // As prey against the easy AI you are clearly faster than the snake.
        assertTrue(MultiplayerEngine.humanPreySpeed(AiDifficulty.EASY) > 260f * t[0].speed * 1.4f)
    }

    @Test
    fun aHelperOrb_appearsInPreyRounds_andBoostsWhenTouched() {
        var s = MultiplayerEngine.startMatch(DuelMode.VS_AI_RIVAL, "Me", "AI", difficulty = AiDifficulty.HARD)
            .copy(phase = DuelPhase.ROUND_PLAYING, localRole = MultiplayerRole.PREY)
        var t = 0L
        while (t < MultiplayerEngine.HELPER_FIRST_MS + 50L && s.phase == DuelPhase.ROUND_PLAYING) {
            // Keep the prey well away from the snake so the round keeps going.
            s = MultiplayerEngine.tick(s.copy(snake = s.snake?.copy(headPosition = Vec2(40f, 40f))), s.prey.position, null, 16L).newState
            t += 16L
        }
        val orb = assertNotNull(s.helperOrb, "a helper orb shows up after ${MultiplayerEngine.HELPER_FIRST_MS} ms")
        s = MultiplayerEngine.tick(s.copy(prey = s.prey.copy(position = orb)), orb, null, 16L).newState
        assertNull(s.helperOrb, "collected")
        assertTrue(s.preyBoostMs > 0L, "boost running")
    }
}
