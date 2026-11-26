package com.snakechase.app

import com.snakechase.app.levels.Cheers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheersTest {
    @Test
    fun threeStars_isAlwaysFlawless() {
        assertEquals("Flawless!", Cheers.forClear(2, 3, newBest = true, firstClear = true, seed = 0).stamp)
    }

    @Test
    fun firstClear_beatsNewBest_becauseEveryFirstClearIsAlsoABest() {
        assertEquals("First clear!", Cheers.forClear(1, 1, newBest = true, firstClear = true, seed = 0).stamp)
        assertEquals("New best!", Cheers.forClear(1, 2, newBest = true, firstClear = false, seed = 0).stamp)
    }

    @Test
    fun anOrdinaryReplay_hasNoStamp() {
        assertNull(Cheers.forClear(4, 1, newBest = false, firstClear = false, seed = 7).stamp)
    }

    @Test
    fun differentScores_canPickDifferentLines_andNegativeSeedsAreSafe() {
        val a = Cheers.forClear(3, 2, newBest = false, firstClear = false, seed = 0).line
        val b = Cheers.forClear(3, 2, newBest = false, firstClear = false, seed = 1).line
        assertNotEquals(a, b)
        assertTrue(Cheers.forClear(3, 2, newBest = false, firstClear = false, seed = -5).line.isNotBlank())
    }

    @Test
    fun outOfRangeStars_areClamped() {
        assertEquals(Cheers.forClear(5, 3, false, false, 0), Cheers.forClear(5, 9, false, false, 0))
    }
}
