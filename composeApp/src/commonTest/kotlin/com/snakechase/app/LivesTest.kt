package com.snakechase.app

import com.russhwolf.settings.MapSettings
import com.snakechase.app.profile.Lives
import com.snakechase.app.profile.LivesRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivesTest {
    @Test
    fun storeBuild_spendsHearts_thenRefillsOnePerInterval() {
        var now = 0L
        val lives = Lives(MapSettings(), autoRefill = false, now = { now })
        assertEquals(3, lives.current())
        repeat(3) { assertTrue(lives.spend()) }
        assertFalse(lives.spend(), "no hearts left")
        now += Lives.REFILL_MS
        assertEquals(1, lives.current(), "one heart back after one interval")
        now += Lives.REFILL_MS * 5
        assertEquals(3, lives.current(), "never more than the maximum")
    }

    @Test
    fun judgeEdition_refillsTheMomentTheyRunOut() {
        val lives = Lives(MapSettings(), autoRefill = true, now = { 0L })
        repeat(10) { assertTrue(lives.spend(), "always a heart to spend in the judge edition") }
    }

    @Test
    fun refillRule_keepsPartialProgress() {
        val (h, last) = LivesRules.refill(1, 0L, Lives.REFILL_MS + 1000L)
        assertEquals(2, h)
        assertEquals(Lives.REFILL_MS, last, "the extra second counts toward the next heart")
    }
}
