package com.snakechase.app

import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.network.DuelSnapshot
import com.snakechase.app.network.SnapshotInterpolator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SnapshotInterpolatorTest {
    private fun snap(t: Long, x: Float) = DuelSnapshot(t, Vec2(x, 0f), 0f, listOf(Vec2(x - 10f, 0f)), Vec2(x * 2, 0f))

    @Test
    fun blendsBetweenSnapshots_atRenderDelayInThePast() {
        val i = SnapshotInterpolator(delayMs = 100)
        i.add(snap(1_000, 0f))
        i.add(snap(1_050, 50f))
        val s = i.sample(nowMs = 1_125)!! // renders t = 1025, halfway between the two
        assertEquals(25f, s.snakeHead.x)
        assertEquals(15f, s.snakeSegments[0].x)
        assertEquals(50f, s.prey.x)
    }

    @Test
    fun holdsNewestSnapshot_neverExtrapolates() {
        val i = SnapshotInterpolator(delayMs = 100)
        i.add(snap(1_000, 0f))
        i.add(snap(1_050, 50f))
        assertEquals(50f, i.sample(nowMs = 5_000)!!.snakeHead.x) // network stalled: stay put, don't guess
    }

    @Test
    fun ignoresOutOfOrderSnapshots_andStartsEmpty() {
        val i = SnapshotInterpolator(delayMs = 0)
        assertNull(i.sample(0))
        i.add(snap(1_000, 10f))
        i.add(snap(900, 999f)) // late, older packet
        assertEquals(10f, i.sample(2_000)!!.snakeHead.x)
    }
}
