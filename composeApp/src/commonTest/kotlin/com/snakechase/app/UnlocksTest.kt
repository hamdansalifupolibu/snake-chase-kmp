package com.snakechase.app

import com.russhwolf.settings.MapSettings
import com.snakechase.app.config.Edition
import com.snakechase.app.config.Unlocks
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.hunt.HuntProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The judge-edition switch opens everything; the store build keeps every lock. */
class UnlocksTest {
    private val judge = Unlocks(everythingOpen = true)
    private val store = Unlocks(everythingOpen = false)

    @Test
    fun judgeEdition_opensEveryLevelSkinAndOrb() {
        assertEquals((1..5).toSet(), judge.openLevels(setOf(1)))
        assertTrue(SnakeSkin.entries.all { judge.skinUsable(it, emptySet()) })
        assertTrue(OrbSkin.entries.all { judge.orbUsable(it, emptySet()) })
        assertEquals(SnakeSkin.GOLDEN_IDOL, judge.wornSkin("GOLDEN_IDOL", emptySet()))
    }

    @Test
    fun storeBuild_keepsEveryLock() {
        assertEquals(setOf(1, 2), store.openLevels(setOf(1, 2)))
        assertFalse(store.skinUsable(SnakeSkin.GOLDEN_IDOL, emptySet()))
        assertTrue(store.skinUsable(SnakeSkin.DEFAULT, emptySet()), "the default snake is always usable")
        assertFalse(store.orbUsable(OrbSkin.PRISM, emptySet()))
        assertTrue(store.orbUsable(OrbSkin.SPARK, emptySet()))
        assertEquals(0, store.walletTopUp(10))
    }

    @Test
    fun aJudgeEditionLook_fallsBackToDefault_inTheStoreBuild() {
        // A judge equipped a skin they never earned; the store build must not let them keep it.
        assertEquals(SnakeSkin.DEFAULT, store.wornSkin("GOLDEN_IDOL", emptySet()))
        assertEquals(OrbSkin.SPARK, store.wornOrb("PRISM", emptySet()))
        assertEquals(SnakeSkin.CANDY_POP, store.wornSkin("CANDY_POP", setOf("CANDY_POP")))
    }

    @Test
    fun judgeWallet_isToppedUp_butNeverTakenFrom() {
        assertEquals(Edition.JUDGE_WALLET - 500, judge.walletTopUp(500))
        assertEquals(0, judge.walletTopUp(Edition.JUDGE_WALLET + 10))
    }

    @Test
    fun hunts_openOnlyInOrderInTheStoreBuild_andAllAtOnceForJudges() {
        val settings = MapSettings()
        assertFalse(HuntProgress(settings).isUnlocked(3))
        assertTrue(HuntProgress(settings, openAll = true).isUnlocked(5))
    }
}
