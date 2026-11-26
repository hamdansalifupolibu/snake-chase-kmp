package com.snakechase.app.profile

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hearts for reviving in Levels 1-5, the way modern mobile games do it: you hold up to [MAX];
 * each revive spends one. Empty hearts come back one every [REFILL_MS], or all at once for
 * [REFILL_PRICE] coins. In the judge edition (config/Edition.kt) the hearts refill by
 * themselves the moment they run out, so nobody waits during judging.
 */
class Lives(
    private val storage: Settings,
    private val autoRefill: Boolean,
    private val now: () -> Long,
) {
    private val _hearts = MutableStateFlow(load())
    val hearts: StateFlow<Int> = _hearts.asStateFlow()

    /** Hearts right now, after any timed refills that are due. */
    fun current(): Int {
        val (h, last) = LivesRules.refill(storage.getInt(KEY_HEARTS, MAX), storage.getLong(KEY_LAST, now()), now())
        save(h, last)
        return h
    }

    /** Spends a heart for a revive. False when there are none. */
    fun spend(): Boolean {
        val h = current()
        if (h <= 0) return false
        // Starting the refill clock from the moment the first heart is used.
        val last = if (h == MAX) now() else storage.getLong(KEY_LAST, now())
        val left = h - 1
        if (left == 0 && autoRefill) save(MAX, now()) else save(left, last)
        return true
    }

    /** Milliseconds until the next heart comes back (null when full). */
    fun msToNextHeart(): Long? {
        val h = current()
        if (h >= MAX) return null
        return (storage.getLong(KEY_LAST, now()) + REFILL_MS - now()).coerceAtLeast(0L)
    }

    /** Buys a full set of hearts with coins (store build). */
    fun buyRefill(profiles: ProfileRepository): Boolean {
        if (current() >= MAX || !profiles.spend(REFILL_PRICE)) return false
        save(MAX, now())
        return true
    }

    private fun load(): Int = storage.getInt(KEY_HEARTS, MAX)

    private fun save(h: Int, last: Long) {
        storage.putInt(KEY_HEARTS, h)
        storage.putLong(KEY_LAST, last)
        _hearts.value = h
    }

    companion object {
        const val MAX = 3
        const val REFILL_MS = 15 * 60_000L
        const val REFILL_PRICE = 150
        private const val KEY_HEARTS = "sc_lives_hearts"
        private const val KEY_LAST = "sc_lives_last_refill"
    }
}

/** The refill arithmetic, kept pure so it is unit-tested. */
object LivesRules {
    /** Adds one heart per full [Lives.REFILL_MS] since [lastMs]; returns (hearts, new lastMs). */
    fun refill(hearts: Int, lastMs: Long, nowMs: Long): Pair<Int, Long> {
        if (hearts >= Lives.MAX) return Lives.MAX to nowMs
        val gained = ((nowMs - lastMs) / Lives.REFILL_MS).toInt().coerceAtLeast(0)
        if (gained == 0) return hearts to lastMs
        val h = (hearts + gained).coerceAtMost(Lives.MAX)
        return h to if (h >= Lives.MAX) nowMs else lastMs + gained * Lives.REFILL_MS
    }
}
