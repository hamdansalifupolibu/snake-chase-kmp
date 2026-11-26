package com.snakechase.app.profile

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Owns the [PlayerProfile]: stored on the device as one JSON value so every change (coins,
 * stats, skins) is saved atomically. [onChanged] lets the online layer mirror it to Firestore.
 */
class ProfileRepository(private val storage: Settings) {

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    /** Called after every change; set by the online sync (null in tests). */
    var onChanged: ((PlayerProfile) -> Unit)? = null

    private fun load(): PlayerProfile {
        val raw = storage.getStringOrNull(KEY) ?: return migrateLegacy()
        return runCatching { json.decodeFromString<PlayerProfile>(raw) }.getOrElse { PlayerProfile() }
    }

    /** Players from before profiles existed keep the name they already typed in Settings. */
    private fun migrateLegacy(): PlayerProfile {
        val legacy = storage.getStringOrNull(LEGACY_NAME_KEY)?.takeIf { it.isNotBlank() && it != "Player 1" }
        return PlayerProfile(name = legacy?.let(Economy::cleanName).orEmpty())
    }

    private fun edit(transform: (PlayerProfile) -> PlayerProfile): PlayerProfile {
        val updated = transform(_profile.value)
        if (updated == _profile.value) return updated
        _profile.value = updated
        storage.putString(KEY, json.encodeToString(PlayerProfile.serializer(), updated))
        onChanged?.invoke(updated)
        return updated
    }

    fun setIdentity(name: String, avatar: Int) {
        val clean = Economy.cleanName(name)
        if (clean.isEmpty()) return
        edit { it.copy(name = clean, avatar = avatar.mod(AVATAR_COLORS.size)) }
        // Keep the legacy setting in sync: duel lobbies and the leaderboard read it.
        storage.putString(LEGACY_NAME_KEY, clean)
    }

    fun addCoins(amount: Int) {
        if (amount > 0) edit { it.copy(coins = it.coins + amount) }
    }

    /** Takes [amount] coins if the player has them; returns false (and changes nothing) otherwise. */
    fun spend(amount: Int): Boolean {
        if (amount < 0 || _profile.value.coins < amount) return false
        edit { it.copy(coins = it.coins - amount) }
        return true
    }

    fun recordRun() = edit { it.copy(runsPlayed = it.runsPlayed + 1) }

    fun recordDuel(won: Boolean) = edit {
        if (won) {
            val streak = it.winStreak + 1
            it.copy(duelsWon = it.duelsWon + 1, winStreak = streak, bestWinStreak = maxOf(it.bestWinStreak, streak))
        } else {
            it.copy(duelsLost = it.duelsLost + 1, winStreak = 0)
        }
    }

    fun unlockSkin(name: String) = edit { it.copy(unlockedSkins = it.unlockedSkins + name) }

    /** [anyway] = judge edition: wear it even though it isn't owned (ownership stays untouched). */
    fun equipSkin(name: String, anyway: Boolean = false) = edit { if (anyway || name in it.unlockedSkins) it.copy(activeSkin = name) else it }

    fun unlockOrb(name: String) = edit { it.copy(unlockedOrbs = it.unlockedOrbs + name) }

    fun equipOrb(name: String, anyway: Boolean = false) = edit { if (anyway || name in it.unlockedOrbs) it.copy(activeOrb = name) else it }

    /**
     * Pays the daily reward the first time the app opens on [today] (epoch day). The streak
     * grows on consecutive days and restarts at day 1 after a missed day. Null if already claimed.
     */
    fun claimDaily(today: Long): DailyClaim? {
        val p = _profile.value
        if (p.lastDailyDay == today) return null
        val day = if (p.lastDailyDay == today - 1) p.dailyStreak + 1 else 1
        val coins = Economy.dailyReward(day)
        edit { it.copy(dailyStreak = day, lastDailyDay = today, coins = it.coins + coins) }
        return DailyClaim(day, coins)
    }

    companion object {
        private const val KEY = "sc_profile_v1"
        private const val LEGACY_NAME_KEY = "sc_player_name"
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
