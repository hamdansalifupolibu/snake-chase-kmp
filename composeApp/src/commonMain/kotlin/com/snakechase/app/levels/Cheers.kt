package com.snakechase.app.levels

/** What the victory screen says: an optional sticker stamp and the congratulatory line. */
data class Cheer(val stamp: String?, val line: String)

/**
 * The words of a level clear. Each level and star count has a few lines, picked by [forClear]'s
 * seed (the score), so replays don't keep saying the same thing; the stamp celebrates whatever
 * was most special about this clear.
 */
object Cheers {
    fun forClear(levelId: Int, stars: Int, newBest: Boolean, firstClear: Boolean, seed: Int): Cheer {
        val s = stars.coerceIn(1, 3)
        val stamp = when {
            s == 3 -> "Flawless!"
            newBest && !firstClear -> "New best!"
            firstClear -> "First clear!"
            else -> null
        }
        val pool = lines(levelId, s)
        return Cheer(stamp, pool[seed.mod(pool.size)])
    }

    private fun lines(levelId: Int, stars: Int): List<String> = when (levelId) {
        1 -> when (stars) {
            3 -> listOf("Ghost protocol: activated. The snake never had a chance!", "Not a scratch. The neon city will tell stories about this.")
            2 -> listOf("The Neon Drift bows to a worthy runner.", "Slick moves under the city lights!")
            else -> listOf("You made it through. The city is watching.", "Out of the rain and into the record books.")
        }
        2 -> when (stars) {
            3 -> listOf("Canopy sovereign! The hunters lost your trail in the mist.", "The whole grove glowed just for you.")
            2 -> listOf("You wove through the grove like a true ghost.", "The fireflies are cheering for you!")
            else -> listOf("The roots held, but you broke free.", "Out of the grove, still breathing. Nice!")
        }
        3 -> when (stars) {
            3 -> listOf("Apex leviathan! Not even the trench whirlpool could hold you.", "The deep has a new legend, and it's you.")
            2 -> listOf("You outpaced the deep-ocean predators.", "Smooth as a current. The abyss is impressed.")
            else -> listOf("You surfaced from the abyss intact.", "Up from the depths with air to spare!")
        }
        4 -> when (stars) {
            3 -> listOf("Absolute zero mastery! The ice drift is yours.", "Ice cold and untouchable. Perfect run!")
            2 -> listOf("Sub-zero reflexes! The glacier twins are left in the snow.", "You skated past every hunter. Cool!")
            else -> listOf("You thawed the gauntlet and lived.", "Frozen fingers, warm victory!")
        }
        5 -> when (stars) {
            3 -> listOf("The Crucible reveres you. Absolute mastery!", "Forged in fire, without a single burn.")
            2 -> listOf("The Crucible forges legends. You are one.", "You danced through the lava like it was nothing.")
            else -> listOf("You stared into the Core and blinked, but you survived.", "Through the fire and out the other side!")
        }
        else -> listOf("Level complete! Exceptional agility.")
    }

    /** The grand finale, shown once: every world has been cleared. */
    fun champion(totalStars: Int): String = when {
        totalStars >= 15 -> "Fifteen stars out of fifteen. You are the jungle."
        totalStars >= 10 -> "All five worlds conquered. Go back for the missing stars?"
        else -> "All five worlds conquered! Now chase all fifteen stars."
    }
}
