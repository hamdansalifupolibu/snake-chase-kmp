package com.snakechase.app

import com.snakechase.app.online.LeaderboardRow
import com.snakechase.app.online.LeaderboardRules
import kotlin.test.Test
import kotlin.test.assertEquals

class LeaderboardRulesTest {
    private val max = mapOf(1 to 100, 2 to 160)

    @Test
    fun keepsEachPlayersBestOnce_dropsImpossibleRows_andSortsByScore() {
        val rows = listOf(
            LeaderboardRow("DanDan", 1, 24_606, 0), // old scoring: above the level max, no time
            LeaderboardRow("Hamdan", 1, 90, 30_000),
            LeaderboardRow("hamdan ", 2, 150, 60_000), // same player, better score
            LeaderboardRow("Hamdan", 1, 80, 25_000),
            LeaderboardRow("Ama", 1, 95, 40_000),
            LeaderboardRow("Kofi", 2, 120, 0), // no survival time recorded
        )
        val clean = LeaderboardRules.clean(rows, { max[it] ?: 0 })
        assertEquals(listOf("hamdan " to 150L, "Ama" to 95L), clean.map { it.name to it.score })
    }

    @Test
    fun respectsTheLimit() {
        val rows = (1..40).map { LeaderboardRow("P$it", 1, it.toLong(), 1_000) }
        assertEquals(25, LeaderboardRules.clean(rows, { 100 }).size)
    }
}
