package com.snakechase.app.domain.model

/**
 * Every skin has a personality: three ratings from 1 to 5 (3 = neutral). Each point above or
 * below 3 is a 2% edge, so the best rating is +4%: you can feel it, but a skin never decides a
 * run. Online and friend duels ignore perks (cosmetic only), so live matches stay fair.
 */
data class Ratings(val a: Int, val b: Int, val c: Int) {
    init {
        require(listOf(a, b, c).all { it in 1..5 })
    }
}

/** Turns a 1..5 rating into a multiplier: 3 -> 1.0, 5 -> 1.04, 1 -> 0.96. */
fun ratingMultiplier(rating: Int): Float = 1f + (rating - 3) * SkinPerks.STEP

/** Orb skins: Speed (top speed), Dash (how soon the dash is back), Graze (near-miss reach). */
data class OrbPerk(val speed: Int, val dash: Int, val graze: Int, val trait: String) {
    val speedMul: Float get() = ratingMultiplier(speed)

    /** Lower is better: a high Dash rating shortens the cooldown. */
    val dashCooldownMul: Float get() = 2f - ratingMultiplier(dash)
    val grazeMul: Float get() = ratingMultiplier(graze)

    companion object {
        val NEUTRAL = OrbPerk(3, 3, 3, "Balanced")
    }
}

/** Snake skins (when you are the snake): Speed, Agility (turning) and Reach (bite distance). */
data class SnakePerk(val speed: Int, val agility: Int, val reach: Int, val trait: String) {
    val speedMul: Float get() = ratingMultiplier(speed)
    val agilityMul: Float get() = ratingMultiplier(agility)
    val reachMul: Float get() = ratingMultiplier(reach)

    companion object {
        val NEUTRAL = SnakePerk(3, 3, 3, "Balanced")
    }
}

object SkinPerks {
    const val STEP = 0.02f

    fun of(orb: OrbSkin): OrbPerk = when (orb) {
        OrbSkin.SPARK -> OrbPerk(3, 3, 3, "Balanced")
        OrbSkin.FIREFLY -> OrbPerk(3, 4, 3, "Fireflies dance around you")
        OrbSkin.BUBBLE -> OrbPerk(2, 3, 5, "Floaty: wider grazes")
        OrbSkin.SNOWFLAKE -> OrbPerk(4, 3, 3, "Slick as ice")
        OrbSkin.EMBER -> OrbPerk(4, 4, 2, "Hot-headed bursts")
        OrbSkin.PIXEL -> OrbPerk(3, 5, 2, "Glitch-fast dashes")
        OrbSkin.MOON -> OrbPerk(3, 3, 5, "Its moon pulls grazes close")
        OrbSkin.HEARTBEAT -> OrbPerk(3, 4, 4, "Lives for the close call")
        OrbSkin.COMET -> OrbPerk(5, 3, 3, "Fastest orb in the sky")
        OrbSkin.SUPERSTAR -> OrbPerk(4, 4, 4, "Star power, all round")
        OrbSkin.PRISM -> OrbPerk(4, 3, 5, "Bends light, and close calls")
    }

    fun of(skin: SnakeSkin): SnakePerk = when (skin) {
        SnakeSkin.DEFAULT -> SnakePerk(3, 3, 3, "Balanced")
        SnakeSkin.CANDY_POP -> SnakePerk(3, 4, 3, "Sugar rush turns")
        SnakeSkin.CORAL_KRAIT -> SnakePerk(3, 3, 4, "Long, warning strike")
        SnakeSkin.NEON_GHOST -> SnakePerk(3, 5, 2, "Phases through tight turns")
        SnakeSkin.NEON_TUBE -> SnakePerk(4, 3, 3, "Current runs fast")
        SnakeSkin.FOREST_PHANTOM -> SnakePerk(3, 4, 4, "Ambush hunter")
        SnakeSkin.ABYSS_SERPENT -> SnakePerk(4, 3, 4, "Deep-sea lunge")
        SnakeSkin.ICE_CRYSTAL -> SnakePerk(4, 4, 2, "Glides on ice")
        SnakeSkin.FEARLESS -> SnakePerk(5, 3, 3, "Never hesitates")
        SnakeSkin.CRUCIBLE_TITAN -> SnakePerk(3, 3, 5, "Molten reach")
        SnakeSkin.PERFECT_DRIFT -> SnakePerk(4, 5, 3, "Drifts every corner")
        SnakeSkin.THE_ENDGAME -> SnakePerk(4, 4, 4, "Inevitable")
        SnakeSkin.GOLDEN_IDOL -> SnakePerk(5, 4, 4, "Gilded predator")
    }
}
