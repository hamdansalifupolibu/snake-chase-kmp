package com.snakechase.app

import com.snakechase.app.domain.model.DuelMatchResult
import com.snakechase.app.share.ShareCards
import com.snakechase.app.share.ShareIdentity
import com.snakechase.app.share.ShareKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareCardsTest {
    private val me = ShareIdentity("Hamdan", 0)

    @Test
    fun time_isSecondsUnderAMinute_andMinutesAbove() {
        assertEquals("0.0s", ShareCards.formatTime(0))
        assertEquals("42.3s", ShareCards.formatTime(42_345))
        assertEquals("1:05", ShareCards.formatTime(65_900))
        assertEquals("0.0s", ShareCards.formatTime(-5))
    }

    @Test
    fun levelClear_threeStars_isFlawless_andMessageShowsStars() {
        val card = ShareCards.levelClear(3, "Abyssal Chase", 3, 230, 230, 61_000, 7, 7, newBest = true, player = me)
        assertEquals(ShareKind.LEVEL_CLEAR, card.kind)
        assertEquals("Flawless", card.badge)
        assertTrue("3/3 stars" in card.message, card.message)
        assertTrue("Abyssal Chase" in card.message)
        assertTrue(com.snakechase.app.network.firebase.FirebaseConfig.WEB_URL in card.message, "the message links to the web game")
        assertTrue(card.fileName.endsWith(".png"))
    }

    @Test
    fun levelClear_oneStarNewBest_saysNewBest() {
        val card = ShareCards.levelClear(1, "Neon Drift", 1, 60, 100, 30_000, 6, 6, newBest = true, player = me)
        assertEquals("New best!", card.badge)
        assertTrue("1/3 stars" in card.message)
    }

    @Test
    fun run_showsStageReached_andNoStars() {
        val card = ShareCards.run(2, "Neon Grove", 50, 160, 12_000, 3, 7, me)
        assertEquals(0, card.stars)
        assertEquals("3/7", card.stats.first { it.label == "Stage" }.value)
        assertNull(card.badge)
    }

    @Test
    fun duel_isToldFromMySide_whetherIAmPlayerOneOrTwo() {
        val result = DuelMatchResult(
            winnerName = "Hamdan",
            isLocalWinner = true,
            round1TimeMs = 20_000,
            round2TimeMs = 8_000,
            player1Name = "Ama",
            player2Name = "Hamdan",
            player1RoundsWon = 1,
            player2RoundsWon = 2,
            mvpTitle = "Apex Predator",
        )
        val card = ShareCards.duel(result, arenaLevelId = 2, vsAi = false, player = me)
        assertEquals(ShareKind.DUEL_WON, card.kind)
        assertEquals("2 - 1", card.bigNumber)
        assertEquals("vs Ama", card.worldName)
        assertTrue("beat Ama 2-1" in card.message, card.message)
        assertEquals("Apex Predator", card.badge)
    }

    @Test
    fun lostDuel_hasNoBadge_andAsksForARematch() {
        val result = DuelMatchResult(
            winnerName = "Bot",
            isLocalWinner = false,
            round1TimeMs = 5_000,
            round2TimeMs = 9_000,
            player1Name = "Hamdan",
            player2Name = "Bot",
            player1RoundsWon = 0,
            player2RoundsWon = 2,
        )
        val card = ShareCards.duel(result, arenaLevelId = 1, vsAi = true, player = me)
        assertEquals(ShareKind.DUEL_LOST, card.kind)
        assertNull(card.badge)
        assertEquals("0 - 2", card.bigNumber)
        assertTrue("the AI rival" in card.message)
    }
}
