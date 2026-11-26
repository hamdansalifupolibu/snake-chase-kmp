package com.snakechase.app

import com.snakechase.app.domain.model.SkinRarity
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.profile.Economy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinRulesTest {
    @Test
    fun starSkins_unlockOnlyWithThreeStars_onTheirLevel() {
        assertEquals(emptyList(), SnakeSkin.earnedByStars(mapOf(1 to 2, 3 to 2)))
        assertEquals(listOf(SnakeSkin.PERFECT_DRIFT), SnakeSkin.earnedByStars(mapOf(1 to 3)))
        assertEquals(listOf(SnakeSkin.FEARLESS, SnakeSkin.THE_ENDGAME), SnakeSkin.earnedByStars(mapOf(3 to 3, 5 to 3)))
    }

    @Test
    fun goldenIdol_needsThreeStarsOnEveryLevel() {
        val four = (1..4).associateWith { 3 }
        assertTrue(SnakeSkin.GOLDEN_IDOL !in SnakeSkin.earnedByStars(four))
        assertTrue(SnakeSkin.GOLDEN_IDOL in SnakeSkin.earnedByStars(four + (5 to 3)))
    }

    @Test
    fun prices_followTheCatalog_andStarSkinsCantBeBought() {
        assertEquals(0, Economy.skinPrice(SnakeSkin.DEFAULT))
        assertEquals(200, Economy.skinPrice(SnakeSkin.CANDY_POP))
        assertEquals(1500, Economy.skinPrice(SnakeSkin.CRUCIBLE_TITAN))
        SnakeSkin.entries.filter { it.threeStarGoal > 0 }.forEach { assertNull(Economy.skinPrice(it), it.name) }
        // Every buyable skin costs more as rarity rises (no Common pricier than a Rare, etc.).
        val maxByRarity = SkinRarity.entries.map { r -> SnakeSkin.entries.filter { it.rarity == r }.mapNotNull { it.price }.maxOrNull() ?: 0 }
        val minByRarity = SkinRarity.entries.map { r -> SnakeSkin.entries.filter { it.rarity == r && it != SnakeSkin.DEFAULT }.mapNotNull { it.price }.minOrNull() ?: Int.MAX_VALUE }
        for (i in 1 until SkinRarity.entries.size) {
            if (minByRarity[i] != Int.MAX_VALUE) assertTrue(minByRarity[i] >= maxByRarity[i - 1], "rarity ${SkinRarity.entries[i]} undercuts the tier below")
        }
    }

    @Test
    fun savedSkinNames_stayStable() {
        // Profiles store skins by name; renaming an entry would silently drop a player's skin.
        val original = listOf("DEFAULT", "NEON_GHOST", "FOREST_PHANTOM", "ABYSS_SERPENT", "ICE_CRYSTAL", "CRUCIBLE_TITAN", "PERFECT_DRIFT", "FEARLESS", "THE_ENDGAME")
        original.forEach { name -> assertTrue(SnakeSkin.entries.any { it.name == name }, name) }
    }
}
