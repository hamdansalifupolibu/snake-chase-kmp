package com.snakechase.app.domain.repository

import com.russhwolf.settings.Settings
import com.snakechase.app.domain.model.Difficulty
import com.snakechase.app.domain.model.JoystickSide
import com.snakechase.app.domain.model.ParticleQuality
import com.snakechase.app.domain.model.SteeringMode
import com.snakechase.app.domain.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface SettingsRepository {
    val settings: StateFlow<UserSettings>
    val unlockedLevels: StateFlow<Set<Int>>
    val bestScores: StateFlow<Map<Int, Int>>
    val bestTimes: StateFlow<Map<Int, Long>>

    /** Best star rating (1-3) earned per cleared level. */
    val levelStars: StateFlow<Map<Int, Int>>
    val dailyChallengeEpochDay: StateFlow<Long>
    val dailyChallengeScore: StateFlow<Int>

    fun updateSettings(transform: (UserSettings) -> UserSettings)
    fun saveScore(levelId: Int, score: Int, survivalTimeMs: Long)

    /** Keeps the higher of the stored and the new rating. */
    fun saveLevelStars(levelId: Int, stars: Int)
    fun saveDailyChallengeScore(epochDay: Long, score: Int)
    fun unlockLevel(levelId: Int)
    fun unlockAllLevels()
    fun resetAllProgress()
}

class SettingsRepositoryImpl(
    private val storage: Settings,
) : SettingsRepository {

    private val _settings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _unlockedLevels = MutableStateFlow(loadUnlockedLevels())
    override val unlockedLevels: StateFlow<Set<Int>> = _unlockedLevels.asStateFlow()

    private val _bestScores = MutableStateFlow(loadBestScores())
    override val bestScores: StateFlow<Map<Int, Int>> = _bestScores.asStateFlow()

    private val _bestTimes = MutableStateFlow(loadBestTimes())
    override val bestTimes: StateFlow<Map<Int, Long>> = _bestTimes.asStateFlow()

    private val _levelStars = MutableStateFlow(loadLevelStars())
    override val levelStars: StateFlow<Map<Int, Int>> = _levelStars.asStateFlow()

    private val _dailyChallengeEpochDay = MutableStateFlow(storage.getLong(KEY_DAILY_EPOCH, 0L))
    override val dailyChallengeEpochDay: StateFlow<Long> = _dailyChallengeEpochDay.asStateFlow()

    private val _dailyChallengeScore = MutableStateFlow(storage.getInt(KEY_DAILY_SCORE, 0))
    override val dailyChallengeScore: StateFlow<Int> = _dailyChallengeScore.asStateFlow()

    private fun loadSettings(): UserSettings = UserSettings(
        soundEnabled = storage.getBoolean(KEY_SOUND_ENABLED, true),
        masterVolume = storage.getFloat(KEY_MASTER_VOL, 1.0f),
        bgmVolume = storage.getFloat(KEY_BGM_VOL, 0.75f),
        sfxVolume = storage.getFloat(KEY_SFX_VOL, 1.0f),
        hapticsEnabled = storage.getBoolean(KEY_HAPTICS_ENABLED, true),
        difficulty = Difficulty.byName(storage.getStringOrNull(KEY_DIFFICULTY)),
        difficultyChosen = storage.getBoolean(KEY_DIFFICULTY_CHOSEN, false),
        memeRoasts = storage.getBoolean(KEY_MEME_ROASTS, true),
        tourSeen = storage.getBoolean(KEY_TOUR_SEEN, false),
        firstRunTipsSeen = storage.getBoolean(KEY_FIRST_RUN_TIPS, false),
        steeringMode = try {
            SteeringMode.valueOf(storage.getString(KEY_STEERING_MODE, SteeringMode.POINTER.name))
        } catch (_: Exception) {
            SteeringMode.POINTER
        },
        joystickSide = try {
            JoystickSide.valueOf(storage.getString(KEY_JOYSTICK_SIDE, JoystickSide.LEFT.name))
        } catch (_: Exception) {
            JoystickSide.LEFT
        },
        particleQuality = try {
            ParticleQuality.valueOf(storage.getString(KEY_PARTICLE_QUALITY, ParticleQuality.HIGH.name))
        } catch (_: Exception) {
            ParticleQuality.HIGH
        },
        touchSensitivity = storage.getFloat(KEY_TOUCH_SENSITIVITY, 1.0f),
        playerName = storage.getString(KEY_PLAYER_NAME, "Player 1"),
    )

    private fun loadUnlockedLevels(): Set<Int> {
        val raw = storage.getString(KEY_UNLOCKED_LEVELS, "1,2,3,4,5")
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
            .ifEmpty { setOf(1, 2, 3, 4, 5) }
    }

    private fun loadBestScores(): Map<Int, Int> {
        val scores = mutableMapOf<Int, Int>()
        for (i in 1..5) {
            // Bests saved under the old scoring can sit above today's maximum ("120 / 100"); cap them.
            val max = com.snakechase.app.levels.LevelRegistry.getById(i).scoringConfig.levelScoreMax
            val score = storage.getInt("${KEY_BEST_SCORE}_$i", 0).coerceAtMost(max)
            if (score > 0) scores[i] = score
        }
        return scores
    }

    private fun loadLevelStars(): Map<Int, Int> = (1..5)
        .associateWith { storage.getInt("${KEY_LEVEL_STARS}_$it", 0) }
        .filterValues { it > 0 }

    private fun loadBestTimes(): Map<Int, Long> {
        val times = mutableMapOf<Int, Long>()
        for (i in 1..5) {
            val time = storage.getLong("${KEY_BEST_TIME}_$i", 0L)
            if (time > 0) times[i] = time
        }
        return times
    }

    override fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        storage.putBoolean(KEY_SOUND_ENABLED, updated.soundEnabled)
        storage.putFloat(KEY_MASTER_VOL, updated.masterVolume)
        storage.putFloat(KEY_BGM_VOL, updated.bgmVolume)
        storage.putFloat(KEY_SFX_VOL, updated.sfxVolume)
        storage.putBoolean(KEY_HAPTICS_ENABLED, updated.hapticsEnabled)
        storage.putString(KEY_STEERING_MODE, updated.steeringMode.name)
        storage.putString(KEY_DIFFICULTY, updated.difficulty.name)
        storage.putBoolean(KEY_DIFFICULTY_CHOSEN, updated.difficultyChosen)
        storage.putBoolean(KEY_MEME_ROASTS, updated.memeRoasts)
        storage.putBoolean(KEY_TOUR_SEEN, updated.tourSeen)
        storage.putBoolean(KEY_FIRST_RUN_TIPS, updated.firstRunTipsSeen)
        storage.putString(KEY_JOYSTICK_SIDE, updated.joystickSide.name)
        storage.putString(KEY_PARTICLE_QUALITY, updated.particleQuality.name)
        storage.putFloat(KEY_TOUCH_SENSITIVITY, updated.touchSensitivity)
        storage.putString(KEY_PLAYER_NAME, updated.playerName)
    }

    override fun saveScore(levelId: Int, score: Int, survivalTimeMs: Long) {
        val currentBest = _bestScores.value[levelId] ?: 0
        if (score > currentBest) {
            _bestScores.update { it + (levelId to score) }
            storage.putInt("${KEY_BEST_SCORE}_$levelId", score)
        }

        val currentBestTime = _bestTimes.value[levelId] ?: 0L
        if (survivalTimeMs > currentBestTime) {
            _bestTimes.update { it + (levelId to survivalTimeMs) }
            storage.putLong("${KEY_BEST_TIME}_$levelId", survivalTimeMs)
        }
    }

    override fun saveLevelStars(levelId: Int, stars: Int) {
        val clamped = stars.coerceIn(0, 3)
        if (clamped > (_levelStars.value[levelId] ?: 0)) {
            _levelStars.update { it + (levelId to clamped) }
            storage.putInt("${KEY_LEVEL_STARS}_$levelId", clamped)
        }
    }

    override fun saveDailyChallengeScore(epochDay: Long, score: Int) {
        _dailyChallengeEpochDay.value = epochDay
        _dailyChallengeScore.value = score
        storage.putLong(KEY_DAILY_EPOCH, epochDay)
        storage.putInt(KEY_DAILY_SCORE, score)
    }

    override fun unlockLevel(levelId: Int) {
        val updated = _unlockedLevels.value + levelId
        _unlockedLevels.value = updated
        storage.putString(KEY_UNLOCKED_LEVELS, updated.joinToString(","))
    }

    override fun unlockAllLevels() {
        val updated = setOf(1, 2, 3, 4, 5)
        _unlockedLevels.value = updated
        storage.putString(KEY_UNLOCKED_LEVELS, updated.joinToString(","))
    }

    override fun resetAllProgress() {
        for (i in 1..5) {
            storage.remove("${KEY_BEST_SCORE}_$i")
            storage.remove("${KEY_BEST_TIME}_$i")
            storage.remove("${KEY_LEVEL_STARS}_$i")
        }
        _bestScores.value = emptyMap()
        _levelStars.value = emptyMap()
        _bestTimes.value = emptyMap()
        _unlockedLevels.value = setOf(1)
        storage.putString(KEY_UNLOCKED_LEVELS, "1")
    }

    companion object {
        private const val KEY_SOUND_ENABLED = "sc_audio_enabled"
        private const val KEY_MASTER_VOL = "sc_master_volume"
        private const val KEY_BGM_VOL = "sc_bgm_volume"
        private const val KEY_SFX_VOL = "sc_sfx_volume"
        private const val KEY_HAPTICS_ENABLED = "sc_haptics_enabled"
        private const val KEY_STEERING_MODE = "sc_steering_mode"
        private const val KEY_DIFFICULTY = "sc_difficulty"
        private const val KEY_DIFFICULTY_CHOSEN = "sc_difficulty_chosen"
        private const val KEY_MEME_ROASTS = "sc_meme_roasts"
        private const val KEY_TOUR_SEEN = "sc_tour_seen"
        private const val KEY_FIRST_RUN_TIPS = "sc_first_run_tips"
        private const val KEY_JOYSTICK_SIDE = "sc_joystick_side"
        private const val KEY_PARTICLE_QUALITY = "sc_particle_quality"
        private const val KEY_TOUCH_SENSITIVITY = "sc_touch_sensitivity"
        private const val KEY_PLAYER_NAME = "sc_player_name"
        private const val KEY_UNLOCKED_LEVELS = "sc_unlocked_levels"
        private const val KEY_BEST_SCORE = "sc_best_score"
        private const val KEY_BEST_TIME = "sc_best_time"
        private const val KEY_LEVEL_STARS = "sc_level_stars"
        private const val KEY_DAILY_EPOCH = "sc_daily_epoch"
        private const val KEY_DAILY_SCORE = "sc_daily_score"
    }
}
