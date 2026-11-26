package com.snakechase.app.domain.model

/**
 * What a single run achieved: the input to the orb skins you earn by playing well.
 * Combo and streak use the HUD's own numbers: each counts grazes (one per pass by the snake).
 */
data class RunStats(
    val survivalMs: Long = 0L,
    val maxCombo: Int = 0,
    val maxStreak: Int = 0,
    val dashesUsed: Int = 0,
    /** A judge tool helped (god mode, a stage jump): the run can't earn anything. */
    val assisted: Boolean = false,
)

/** Skill goals that unlock an orb skin the moment a run meets them. */
enum class OrbGoal(val hint: String) {
    NO_DASH_45S("Survive 45 s in one run without dashing"),
    COMBO_25("Reach Combo 25 in one run"),
    SURVIVE_60S("Survive 60 s in one run"),
    STREAK_40("Reach Streak 40 in one run"),
    ;

    fun isMetBy(run: RunStats): Boolean = !run.assisted &&
        when (this) {
            NO_DASH_45S -> run.dashesUsed == 0 && run.survivalMs >= 45_000L
            COMBO_25 -> run.maxCombo >= 25
            SURVIVE_60S -> run.survivalMs >= 60_000L
            STREAK_40 -> run.maxStreak >= 40
        }
}

/**
 * Skins for the orb you steer. Each has its own core and its own trail (ui/canvas/OrbPainter.kt).
 * Names are saved in player profiles, so entries must keep their names.
 */
enum class OrbSkin(
    val displayName: String,
    val description: String,
    val rarity: SkinRarity,
    /** Shop price in coins; null = can only be earned by playing. */
    val price: Int? = null,
    val goal: OrbGoal? = null,
) {
    SPARK("Neon Spark", "The classic glowing orb.", SkinRarity.COMMON, price = 0),
    FIREFLY("Firefly", "A blinking glow that leaves dots of light behind it.", SkinRarity.COMMON, price = 150),
    BUBBLE("Bubble", "A wobbly bubble trailing tiny bubbles.", SkinRarity.COMMON, price = 200),
    SNOWFLAKE("Snowflake", "A spinning ice crystal with a frosty trail.", SkinRarity.RARE, price = 500),
    EMBER("Ember", "A flickering flame that throws sparks.", SkinRarity.RARE, price = 600),
    PIXEL("Pixel", "A retro 8-bit block with a pixel trail.", SkinRarity.RARE, goal = OrbGoal.NO_DASH_45S),
    MOON("Moon", "A cratered little moon in a silver haze.", SkinRarity.EPIC, price = 1000),
    HEARTBEAT("Heartbeat", "A heart that beats, leaving hearts behind.", SkinRarity.EPIC, goal = OrbGoal.COMBO_25),
    COMET("Comet", "A blazing head with a long glowing tail.", SkinRarity.EPIC, goal = OrbGoal.SURVIVE_60S),
    SUPERSTAR("Superstar", "A spinning gold star in a burst of sparkles.", SkinRarity.LEGENDARY, goal = OrbGoal.STREAK_40),
    PRISM("Prism", "A rainbow orb trailing a rainbow ribbon.", SkinRarity.LEGENDARY, price = 2500),
    ;

    val unlockHint: String
        get() = when {
            this == SPARK -> "Yours from the start"
            goal != null -> goal.hint
            else -> "Buy it in the shop"
        }

    companion object {
        /** Orbs whose skill goal this run met. */
        fun earnedBy(run: RunStats): List<OrbSkin> = entries.filter { it.goal?.isMetBy(run) == true }

        fun byName(name: String?): OrbSkin = entries.firstOrNull { it.name == name } ?: SPARK
    }
}
