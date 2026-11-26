package com.snakechase.app.profile

import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SnakeSkin
import kotlinx.serialization.Serializable

/**
 * The player's identity and wallet, Flappy-Bird simple: a name and an avatar colour, no
 * account or password. Online it is tied to the anonymous Firebase uid of this device.
 */
@Serializable
data class PlayerProfile(
    val name: String = "",
    val avatar: Int = 0,
    val coins: Int = Economy.STARTER_COINS,
    val runsPlayed: Int = 0,
    val duelsWon: Int = 0,
    val duelsLost: Int = 0,
    val winStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val dailyStreak: Int = 0,
    /** Epoch day of the last daily reward claimed, or -1 if never. */
    val lastDailyDay: Long = -1,
    val unlockedSkins: Set<String> = setOf(SnakeSkin.DEFAULT.name),
    val activeSkin: String = SnakeSkin.DEFAULT.name,
    val unlockedOrbs: Set<String> = setOf(OrbSkin.SPARK.name),
    val activeOrb: String = OrbSkin.SPARK.name,
) {
    val isSetUp: Boolean get() = name.isNotBlank()
    val duelsPlayed: Int get() = duelsWon + duelsLost
    val winRatePct: Int get() = if (duelsPlayed == 0) 0 else duelsWon * 100 / duelsPlayed
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/** Avatar colours (ARGB); the avatar is a coloured badge with the player's initial. */
val AVATAR_COLORS: List<Long> = listOf(
    0xFF00F5FF,
    0xFFFF2BD6,
    0xFF30FF9E,
    0xFFFFC233,
    0xFFB388FF,
    0xFFFF5252,
    0xFF4FC3F7,
    0xFFFF8A3D,
)

/** Every coin rule in one place, so balancing the game is a one-file change. */
object Economy {
    const val STARTER_COINS = 500
    const val VS_AI_WIN = 40
    const val MAX_NAME = 16

    /** Day 1..7 of a daily streak; day 7+ keeps paying the top reward. */
    private val DAILY = intArrayOf(100, 150, 200, 250, 300, 400, 600)

    fun dailyReward(streakDay: Int): Int = DAILY[(streakDay.coerceAtLeast(1) - 1).coerceAtMost(DAILY.size - 1)]

    /** Coins for a single-player run: half the score, plus a bonus per star when the level is cleared. */
    fun runReward(score: Int, cleared: Boolean, stars: Int): Int = score.coerceAtLeast(0) / 2 + if (cleared) stars.coerceIn(1, 3) * 25 else 0

    /** Shop price; star skins (earned with three-star clears) have none. */
    fun skinPrice(skin: SnakeSkin): Int? = skin.price

    fun cleanName(raw: String): String = raw.filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }
        .trim().replace(Regex(" +"), " ").take(MAX_NAME)
}

/** Result of opening the app on a new day. */
data class DailyClaim(val streakDay: Int, val coins: Int)
