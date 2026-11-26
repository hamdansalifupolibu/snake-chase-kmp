package com.snakechase.app

import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.levels.LevelStars
import com.snakechase.app.levels.ScoreBudget
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreBudgetTest {
    @Test
    fun everyLevel_aCleanClearWithoutGrazing_isOneStar() {
        for (level in LevelRegistry.ALL_LEVELS) {
            val max = level.scoringConfig.levelScoreMax
            val stages = level.subStages.indices.sumOf { ScoreBudget.stageBonus(level, it) }
            val survival = (ScoreBudget.survivalPerSec(level) * level.completionTimeMs / 1000f).roundToInt()
            val clean = stages + survival + ScoreBudget.completionBonus(level)
            assertEquals((max * ScoreBudget.STAGES).roundToInt(), stages, "level ${level.id} stage bonuses add up to the budget")
            assertTrue(clean in (max * 0.58f).toInt()..(max * 0.62f).toInt(), "level ${level.id}: clean clear = $clean of $max")
            assertEquals(1, LevelStars.forClear(clean, max), "level ${level.id}: a clean clear is one star")
        }
    }

    @Test
    fun laterStages_areWorthMore_andTheStartPaysNothing() {
        val l1 = LevelRegistry.getById(1)
        assertEquals(0, ScoreBudget.stageBonus(l1, 0))
        val bonuses = (1 until l1.subStages.size).map { ScoreBudget.stageBonus(l1, it) }
        assertEquals(bonuses.sorted(), bonuses, "stage bonuses grow: $bonuses")
        assertEquals(0, ScoreBudget.stageBonus(l1, 99))
    }

    @Test
    fun grazing_isCappedAtItsShare_soAPerfectScoreNeedsAClearToo() {
        for (level in LevelRegistry.ALL_LEVELS) {
            val cfg = ScoreBudget.config(level)
            assertEquals((level.scoringConfig.levelScoreMax * ScoreBudget.GRAZING).roundToInt(), cfg.nearMissCap)
        }
    }

    @Test
    fun grazePoints_scaleWithTheLevel() {
        assertEquals(1, ScoreBudget.grazePoints(LevelRegistry.getById(1)))
        assertEquals(4, ScoreBudget.grazePoints(LevelRegistry.getById(5)))
    }
}
