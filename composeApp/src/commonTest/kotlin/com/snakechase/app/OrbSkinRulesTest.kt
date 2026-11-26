package com.snakechase.app

import com.snakechase.app.domain.model.OrbGoal
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.RunStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The orb skins you earn by playing well, and the shop rules around them. */
class OrbSkinRulesTest {
    @Test
    fun everyOrb_isEitherBuyableOrEarnable_neverBoth() {
        OrbSkin.entries.forEach { orb ->
            val buyable = orb.price != null
            val earnable = orb.goal != null
            assertTrue(buyable != earnable, "${orb.name} must be bought or earned, not both or neither")
        }
        assertEquals(0, OrbSkin.SPARK.price, "the classic orb is free")
    }

    @Test
    fun aShortRun_earnsNothing() {
        assertEquals(emptyList(), OrbSkin.earnedBy(RunStats(survivalMs = 10_000L, maxCombo = 10, maxStreak = 12, dashesUsed = 1)))
    }

    @Test
    fun noDashGoal_needsZeroDashes() {
        val calm = RunStats(survivalMs = 46_000L, dashesUsed = 0)
        val dashed = calm.copy(dashesUsed = 1)
        assertTrue(OrbGoal.NO_DASH_45S.isMetBy(calm))
        assertTrue(!OrbGoal.NO_DASH_45S.isMetBy(dashed))
        assertEquals(listOf(OrbSkin.PIXEL), OrbSkin.earnedBy(calm))
    }

    @Test
    fun aGreatRun_canEarnSeveralOrbsAtOnce() {
        val run = RunStats(survivalMs = 61_000L, maxCombo = 30, maxStreak = 45, dashesUsed = 0)
        assertEquals(
            setOf(OrbSkin.PIXEL, OrbSkin.HEARTBEAT, OrbSkin.COMET, OrbSkin.SUPERSTAR),
            OrbSkin.earnedBy(run).toSet(),
        )
    }

    @Test
    fun godModeRun_earnsNothing() {
        val run = RunStats(survivalMs = 90_000L, maxCombo = 5_000, maxStreak = 5_000, dashesUsed = 0, assisted = true)
        assertEquals(emptyList(), OrbSkin.earnedBy(run), "judge god mode must not farm orb skins")
    }

    @Test
    fun unknownSavedName_fallsBackToSpark() {
        assertEquals(OrbSkin.SPARK, OrbSkin.byName("NOT_A_SKIN"))
        assertEquals(OrbSkin.SPARK, OrbSkin.byName(null))
        assertEquals(OrbSkin.COMET, OrbSkin.byName("COMET"))
    }
}
