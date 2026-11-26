package com.snakechase.app

import com.snakechase.app.domain.model.ComboState
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.ScoreBreakdown
import com.snakechase.app.domain.model.ScoreState
import com.snakechase.app.engine.GameEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** A new run must not inherit anything from the last one, except your personal best. */
class RunResetTest {
    @Test
    fun startLevel_clearsComboGreedAndStreak_fromThePreviousRun() {
        val finishedRun = GameState(
            score = ScoreState(
                total = 310,
                breakdown = ScoreBreakdown(survivalPoints = 9, stageBonusPoints = 530, nearMissPoints = 5065),
                combo = ComboState(multiplier = 3f, tier = 3, count = 1495, remainingTimeMs = 2500L),
                personalBest = 400,
            ),
            comboMultiplier = 3f,
            comboChargePct = 0.9f,
            nearMissStreakCount = 1019,
            isOnFire = true,
        )

        val fresh = GameEngine.startLevel(4, finishedRun)

        assertEquals(ComboState(), fresh.score.combo, "combo carried over into the new run")
        assertEquals(0, fresh.score.total)
        assertEquals(ScoreBreakdown(), fresh.score.breakdown)
        assertEquals(400, fresh.score.personalBest, "personal best must survive a restart")
        assertEquals(1f, fresh.comboMultiplier)
        assertEquals(0, fresh.nearMissStreakCount)
        assertFalse(fresh.isOnFire)
    }
}
