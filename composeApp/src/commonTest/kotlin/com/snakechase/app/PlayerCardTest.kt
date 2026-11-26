package com.snakechase.app

import com.snakechase.app.profile.Badge
import com.snakechase.app.profile.PlayerCard
import com.snakechase.app.profile.PlayerProfile
import com.snakechase.app.profile.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerCardTest {
    private fun card(
        profile: PlayerProfile = PlayerProfile(name = "Ama"),
        levelStars: Map<Int, Int> = emptyMap(),
        huntStars: Map<Int, Int> = emptyMap(),
    ) = PlayerCard(profile, levelStars, huntStars)

    @Test
    fun aNewPlayer_isAHatchling_withNoBadges_andTheStarterLooksCount() {
        val c = card()
        assertEquals(Rank.HATCHLING, c.rank)
        assertEquals(0, c.rankPoints)
        assertEquals(1, c.skinsOwned, "the default snake is always owned")
        assertEquals(1, c.orbsOwned, "the classic orb is always owned")
        assertTrue(c.earnedBadges.isEmpty())
    }

    @Test
    fun clearingAllWorlds_earnsChampion_andFullStarsEarnStarCollector() {
        val partial = card(levelStars = mapOf(1 to 3, 2 to 1, 3 to 2, 4 to 1, 5 to 1))
        assertTrue(Badge.CHAMPION in partial.earnedBadges)
        assertTrue(Badge.FLAWLESS in partial.earnedBadges)
        assertFalse(Badge.STAR_COLLECTOR in partial.earnedBadges)
        val full = card(levelStars = (1..5).associateWith { 3 })
        assertTrue(Badge.STAR_COLLECTOR in full.earnedBadges)
        assertEquals(15, full.totalLevelStars)
    }

    @Test
    fun ranksClimbWithPoints_andProgressPointsAtTheNextRank() {
        // 15 stars = 150 points: exactly Dodger, at the start of the way to Ghost.
        val c = card(levelStars = (1..5).associateWith { 3 })
        assertEquals(150, c.rankPoints)
        assertEquals(Rank.DODGER, c.rank)
        assertEquals(0f, c.rankProgress)
        val legend = card(profile = PlayerProfile(name = "Ama", duelsWon = 200))
        assertEquals(Rank.LEGEND, legend.rank)
        assertEquals(1f, legend.rankProgress)
    }

    @Test
    fun outOfRangeStars_areClamped_andHuntsNeedAllFive() {
        val c = card(levelStars = mapOf(1 to 9), huntStars = mapOf(1 to 3, 2 to 3, 3 to 3, 4 to 3))
        assertEquals(3, c.totalLevelStars)
        assertFalse(Badge.HUNTER in c.earnedBadges)
        assertTrue(Badge.HUNTER in card(huntStars = (1..5).associateWith { 1 }).earnedBadges)
    }

    @Test
    fun profileShareCard_usesTheFurthestClearedWorld_andTellsTheRank() {
        val c = card(levelStars = mapOf(1 to 3, 2 to 2, 3 to 1))
        val share = com.snakechase.app.share.ShareCards.profile(c, com.snakechase.app.share.ShareIdentity("Ama", 0))
        assertEquals(3, share.levelId)
        assertEquals(c.rank.title, share.headline)
        assertEquals("6", share.bigNumber)
        assertTrue(c.rank.title in share.message, share.message)
    }

    @Test
    fun duelAndLoyaltyBadges() {
        val c = card(profile = PlayerProfile(name = "Ama", duelsWon = 1, bestWinStreak = 5, dailyStreak = 7))
        assertTrue(c.earnedBadges.containsAll(listOf(Badge.DUELIST, Badge.UNSTOPPABLE, Badge.LOYAL)))
    }
}
