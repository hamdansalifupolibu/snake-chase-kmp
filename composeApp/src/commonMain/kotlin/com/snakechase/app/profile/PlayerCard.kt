package com.snakechase.app.profile

import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SnakeSkin

/**
 * Everything the profile page shows about a player's progress, gathered in one place from the
 * profile, the level stars and the hunt stars. Plain data, so ranks and badges are unit-tested.
 */
data class PlayerCard(
    val profile: PlayerProfile,
    /** Best stars per level, 1..5 (missing = never cleared). */
    val levelStars: Map<Int, Int>,
    /** Best stars per Snake Hunt stage, 1..5. */
    val huntStars: Map<Int, Int>,
) {
    val totalLevelStars: Int get() = (1..LEVELS).sumOf { (levelStars[it] ?: 0).coerceIn(0, 3) }
    val levelsCleared: Int get() = (1..LEVELS).count { (levelStars[it] ?: 0) > 0 }
    val totalHuntStars: Int get() = (1..LEVELS).sumOf { (huntStars[it] ?: 0).coerceIn(0, 3) }
    val huntsWon: Int get() = (1..LEVELS).count { (huntStars[it] ?: 0) > 0 }
    val skinsOwned: Int get() = SnakeSkin.entries.count { it == SnakeSkin.DEFAULT || it.name in profile.unlockedSkins }
    val orbsOwned: Int get() = OrbSkin.entries.count { it == OrbSkin.SPARK || it.name in profile.unlockedOrbs }

    /**
     * Rank points: everything a player can do counts a little, so duelists, hunters and
     * collectors all climb, but stars (beating the levels well) count the most.
     */
    val rankPoints: Int
        get() = totalLevelStars * 10 + totalHuntStars * 6 + profile.duelsWon * 4 +
            (skinsOwned - 1) * 3 + (orbsOwned - 1) * 3 + profile.runsPlayed.coerceAtMost(100)

    val rank: Rank get() = Rank.entries.last { rankPoints >= it.minPoints }

    /** 0..1 progress from this rank to the next (1 at the top rank). */
    val rankProgress: Float
        get() {
            val next = rank.next ?: return 1f
            return ((rankPoints - rank.minPoints).toFloat() / (next.minPoints - rank.minPoints)).coerceIn(0f, 1f)
        }

    val earnedBadges: List<Badge> get() = Badge.entries.filter { it.isEarnedBy(this) }

    companion object {
        const val LEVELS = 5
    }
}

enum class Rank(val title: String, val minPoints: Int) {
    HATCHLING("Hatchling", 0),
    RUNNER("Runner", 60),
    DODGER("Dodger", 150),
    GHOST("Ghost", 280),
    LEGEND("Jungle Legend", 450),
    ;

    val next: Rank? get() = entries.getOrNull(ordinal + 1)
}

/** Badges to collect on the profile page; each knows how it is earned and says so when locked. */
enum class Badge(val title: String, val hint: String) {
    FIRST_CLEAR("First Clear", "Clear any level"),
    FLAWLESS("Flawless", "Get 3 stars on a level"),
    CHAMPION("Jungle Champion", "Clear all five worlds"),
    STAR_COLLECTOR("Star Collector", "Collect all 15 level stars"),
    DUELIST("Duelist", "Win a duel"),
    UNSTOPPABLE("Unstoppable", "Win 5 duels in a row"),
    HUNTER("Hunter", "Catch the orb in all 5 hunts"),
    COLLECTOR("Collector", "Own 6 snake skins"),
    ORB_HOARDER("Orb Hoarder", "Own 6 orb skins"),
    LOYAL("Loyal", "Play 7 days in a row"),
    ;

    fun isEarnedBy(card: PlayerCard): Boolean = when (this) {
        FIRST_CLEAR -> card.levelsCleared >= 1
        FLAWLESS -> card.levelStars.values.any { it >= 3 }
        CHAMPION -> card.levelsCleared == PlayerCard.LEVELS
        STAR_COLLECTOR -> card.totalLevelStars >= PlayerCard.LEVELS * 3
        DUELIST -> card.profile.duelsWon >= 1
        UNSTOPPABLE -> card.profile.bestWinStreak >= 5
        HUNTER -> card.huntsWon == PlayerCard.LEVELS
        COLLECTOR -> card.skinsOwned >= 6
        ORB_HOARDER -> card.orbsOwned >= 6
        LOYAL -> card.profile.dailyStreak >= 7
    }
}
