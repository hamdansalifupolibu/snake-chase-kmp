package com.snakechase.app.config

import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SnakeSkin

/**
 * The one build switch between the contest build and the store build.
 *
 * Judge edition (contest): every level, hunt, snake skin and orb skin is open and the wallet is
 * kept topped up, so judges can see everything without grinding. Nothing is *given*: ownership,
 * stars and badges stay real, so judges still see the "New skin!" and "New orb!" moments when
 * they earn something.
 *
 * Play Store release: set [JUDGE_EDITION] to false and every normal lock and price is back.
 */
object Edition {
    const val JUDGE_EDITION: Boolean = true

    /** In judge edition the wallet never drops below this, so every arena tier is affordable. */
    const val JUDGE_WALLET: Int = 100_000

    val unlocks: Unlocks = Unlocks(everythingOpen = JUDGE_EDITION)
}

/** What a player may use right now. All lock checks go through here, so one flag opens them all. */
class Unlocks(val everythingOpen: Boolean) {
    fun openLevels(unlocked: Set<Int>): Set<Int> = if (everythingOpen) ALL_LEVELS else unlocked

    fun skinUsable(skin: SnakeSkin, owned: Set<String>): Boolean = everythingOpen || skin == SnakeSkin.DEFAULT || skin.name in owned

    fun orbUsable(orb: OrbSkin, owned: Set<String>): Boolean = everythingOpen || orb == OrbSkin.SPARK || orb.name in owned

    /** The skin to wear: a saved choice that is no longer usable (e.g. after judge edition) falls back to the default. */
    fun wornSkin(saved: String, owned: Set<String>): SnakeSkin = SnakeSkin.entries.firstOrNull { it.name == saved }?.takeIf { skinUsable(it, owned) } ?: SnakeSkin.DEFAULT

    fun wornOrb(saved: String, owned: Set<String>): OrbSkin = OrbSkin.byName(saved).takeIf { orbUsable(it, owned) } ?: OrbSkin.SPARK

    /** Coins to add so the wallet reaches [Edition.JUDGE_WALLET] (0 outside judge edition). */
    fun walletTopUp(coins: Int): Int = if (everythingOpen) (Edition.JUDGE_WALLET - coins).coerceAtLeast(0) else 0

    private companion object {
        val ALL_LEVELS = (1..5).toSet()
    }
}
