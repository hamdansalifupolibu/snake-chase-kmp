package com.snakechase.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.HapticStyle
import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.RunStats
import com.snakechase.app.domain.model.ScoreState
import com.snakechase.app.domain.model.SnakeRole
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.domain.model.SoundId
import com.snakechase.app.domain.model.StageToast
import com.snakechase.app.domain.model.UserSettings
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.domain.repository.SettingsRepository
import com.snakechase.app.engine.GameEngine
import com.snakechase.app.engine.ObstacleController
import com.snakechase.app.engine.SnakeAI
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.levels.LevelStars
import com.snakechase.app.online.PendingScore
import com.snakechase.app.online.UploadQueue
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.platform.platformName
import com.snakechase.app.profile.Economy
import com.snakechase.app.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.pow

class GameViewModel(
    private val audioManager: AudioManager,
    private val hapticsManager: HapticsManager,
    private val settingsRepository: SettingsRepository,
    private val profiles: ProfileRepository,
    private val uploads: UploadQueue,
    private val huntProgress: com.snakechase.app.hunt.HuntProgress,
    private val lives: com.snakechase.app.profile.Lives,
) : ViewModel() {

    /** Where a revive picks the run back up: the run as it was about half a second before the catch. */
    private var reviveCheckpoint: GameState? = null

    /** Every lock goes through this: the judge edition opens them all (config/Edition.kt). */
    private val unlocks = com.snakechase.app.config.Edition.unlocks

    /** A fresh level run: reset this run's tracking (orb-skin goals, the new-best popup). */
    /**
     * Spends a heart and resumes the run from just before the catch: same score and stage, a short
     * shield, and the snakes knocked back (stunned) so the revive is a real second chance.
     */
    private fun revive() {
        val checkpoint = reviveCheckpoint ?: return
        if (_state.value.phase != GamePhase.GAME_OVER || !lives.spend()) return
        reviveCheckpoint = null
        audioManager.stopMemeRoast()
        audioManager.playSfx(SoundId.POWER_UP)
        hapticsManager.trigger(HapticStyle.HEAVY)
        latestInputPos = null
        virtualCursorPos = checkpoint.player.position
        recentFrames.clear()
        _deathReplay.value = null
        val shield = com.snakechase.app.domain.model.ActivePowerUp(com.snakechase.app.domain.model.PowerUpType.SHIELD, REVIVE_SHIELD_MS, REVIVE_SHIELD_MS)
        fun stun(s: com.snakechase.app.domain.model.SnakeEntity) = s.copy(stunRemainingMs = REVIVE_STUN_MS, mode = com.snakechase.app.domain.model.SnakeMode.STUNNED)
        val current = _state.value
        _state.value = checkpoint.copy(
            phase = GamePhase.PLAYING,
            settings = current.settings,
            score = current.score,
            activePowerUps = checkpoint.activePowerUps.filter { it.type != com.snakechase.app.domain.model.PowerUpType.SHIELD } + shield,
            shieldState = com.snakechase.app.domain.model.ShieldState(isActive = true, remainingMs = REVIVE_SHIELD_MS, maxMs = REVIVE_SHIELD_MS),
            primarySnake = checkpoint.primarySnake?.let(::stun),
            splitSnake = checkpoint.splitSnake?.let(::stun),
            extraSnakes = checkpoint.extraSnakes.map(::stun),
            reviveHearts = 0,
            reviveOffered = false,
            lastRoastMeme = null,
        )
        audioManager.playBgm(checkpoint.levelId)
    }

    private fun prepareRun(state: GameState): GameState {
        reviveCheckpoint = null
        audioManager.stopMemeRoast()
        recentFrames.clear()
        _deathReplay.value = null
        // Fresh input for every start and restart: the orb waits at its spawn point until the
        // player moves (an old target would pull it back to where the last run ended).
        virtualCursorPos = state.player.position
        joystickVector = Vec2.ZERO
        latestInputPos = null
        run = RunStats()
        bestToBeat = settingsRepository.bestScores.value[state.levelId] ?: 0
        bestAnnounced = false
        return state.copy(lastRoastMeme = null)
    }

    /** This run's numbers so far, for the orb skins earned by playing (see [OrbGoal]). */
    private var run = RunStats()

    /** The stored best when the run began, and whether passing it was already celebrated. */
    private var bestToBeat = 0
    private var bestAnnounced = true

    /**
     * Every frame: update the run's numbers and unlock any orb skin whose goal was just met.
     * It is saved at once (so quitting can't lose it) and celebrated on the result screen.
     * [gameMs] is game time (what the HUD shows), so bullet time doesn't count extra.
     */
    private fun trackRun(gameMs: Long) {
        val s = _state.value
        run = run.copy(
            survivalMs = run.survivalMs + gameMs,
            maxCombo = maxOf(run.maxCombo, s.score.combo.count),
            maxStreak = maxOf(run.maxStreak, s.nearMissStreakCount),
            assisted = run.assisted || s.godModeEnabled,
        )
        announceNewBest(s)
        val owned = profiles.profile.value.unlockedOrbs
        val earned = OrbSkin.earnedBy(run).filter { it.name !in owned }
        if (earned.isEmpty()) return
        earned.forEach { profiles.unlockOrb(it.name) }
        audioManager.playSfx(SoundId.STAGE_ADVANCE, 1.6f)
        hapticsManager.trigger(HapticStyle.MEDIUM)
        _state.update { it.copy(newOrbSkins = it.newOrbSkins + earned) }
    }

    /**
     * The moment the score passes the best you had before this run: a gold "New best!" pops
     * over the orb with a chime, once per run. A first-ever run (no best yet) stays quiet.
     */
    private fun announceNewBest(s: GameState) {
        if (bestAnnounced || bestToBeat <= 0 || s.score.total <= bestToBeat) return
        bestAnnounced = true
        val popup = com.snakechase.app.engine.ParticleSystem.spawnScorePopup(s.player.position + Vec2(0f, -48f), "NEW BEST!", 0xFFFFD23F)
            .copy(maxLifeMs = 1400L, targetRadius = 22f)
        audioManager.playSfx(SoundId.LEVEL_COMPLETE, 1.5f)
        hapticsManager.trigger(HapticStyle.MEDIUM)
        _state.update { it.copy(particles = it.particles + popup) }
    }

    /**
     * Saves locally and, when it is a new personal best, publishes it to the global
     * leaderboard in the background. Offline or Firebase errors never affect gameplay.
     */
    private fun recordScore(levelId: Int, score: Int, survivalTimeMs: Long) {
        val previousBest = settingsRepository.bestScores.value[levelId] ?: 0
        settingsRepository.saveScore(levelId, score, survivalTimeMs)
        if (score <= previousBest || score <= 0) return
        // Same scoring on every difficulty, but only Moderate and Hard runs go to the world board.
        val difficulty = _state.value.runDifficulty
        if (!difficulty.countsOnline) return
        val name = settingsRepository.settings.value.playerName.trim()
            .takeIf { it.isNotEmpty() && it != "Player 1" } ?: "$platformName Player"
        // Queued on the device first, so a best made offline still reaches the leaderboard later.
        uploads.enqueue(PendingScore(name, levelId, score.toLong(), survivalTimeMs, difficulty.name))
    }

    /** Pays coins for a finished single-player run and counts it in the profile. */
    private fun payRun(score: Int, cleared: Boolean, stars: Int) {
        val coins = Economy.runReward(score, cleared, stars)
        profiles.recordRun()
        profiles.addCoins(coins)
        _state.update { it.copy(lastRunCoins = coins) }
    }

    private fun today(): Long = Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays().toLong()

    /** First open of the day pays the streak reward (only once the player has a profile). */
    private fun claimDailyIfDue() {
        if (!profiles.profile.value.isSetUp) return
        profiles.claimDaily(today())?.let { claim -> _state.update { it.copy(dailyClaim = claim) } }
    }

    private val _state = MutableStateFlow(
        GameState(
            settings = settingsRepository.settings.value,
            unlockedLevelIds = unlocks.openLevels(settingsRepository.unlockedLevels.value),
            judgeEdition = unlocks.everythingOpen,
            bestScores = settingsRepository.bestScores.value,
            bestTimes = settingsRepository.bestTimes.value,
            levelStarRatings = settingsRepository.levelStars.value,
        ),
    )
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** The last moments of the run, kept so a catch can be replayed in slow motion. */
    private val recentFrames = ArrayDeque<GameState>()
    private val _deathReplay = MutableStateFlow<DeathReplay?>(null)
    val deathReplay: StateFlow<DeathReplay?> = _deathReplay.asStateFlow()

    private var latestInputPos: Vec2? = null
    private var joystickVector: Vec2 = Vec2.ZERO
    private var virtualCursorPos: Vec2 = Vec2(400f, 420f)

    init {
        audioManager.preloadMusic(_state.value.levelId)
        applySettingsToHardware(settingsRepository.settings.value)

        // Profile is the source of truth for wallet, stats and owned skins.
        viewModelScope.launch {
            profiles.profile.collect { p ->
                _state.update {
                    it.copy(
                        profile = p,
                        unlockedSkins = p.unlockedSkins,
                        activeSnakeSkin = unlocks.wornSkin(p.activeSkin, p.unlockedSkins),
                        activeOrbSkin = unlocks.wornOrb(p.activeOrb, p.unlockedOrbs),
                    )
                }
            }
        }
        claimDailyIfDue()
        // Judge edition: keep the wallet full so every arena tier and price is within reach.
        profiles.addCoins(unlocks.walletTopUp(profiles.profile.value.coins))
        // Star skins already earned (e.g. stars won before a skin's rule existed) are granted on start.
        SnakeSkin.earnedByStars(settingsRepository.levelStars.value)
            .filter { it.name !in profiles.profile.value.unlockedSkins }
            .forEach { profiles.unlockSkin(it.name) }

        // Observe settings changes
        viewModelScope.launch {
            settingsRepository.settings.collect { newSettings ->
                applySettingsToHardware(newSettings)
                _state.update { it.copy(settings = newSettings) }
            }
        }

        // Observe unlocked levels & high scores
        viewModelScope.launch {
            settingsRepository.unlockedLevels.collect { unlocked ->
                _state.update { it.copy(unlockedLevelIds = unlocks.openLevels(unlocked)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.levelStars.collect { stars ->
                _state.update { it.copy(levelStarRatings = stars) }
            }
        }
        viewModelScope.launch {
            settingsRepository.bestScores.collect { scores ->
                _state.update { it.copy(bestScores = scores) }
            }
        }
        viewModelScope.launch {
            settingsRepository.bestTimes.collect { times ->
                _state.update { it.copy(bestTimes = times) }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.dailyChallengeEpochDay,
                settingsRepository.dailyChallengeScore,
            ) { epochDay, score ->
                com.snakechase.app.domain.model.DailyChallengeInfo.getCurrentChallenge(epochDay, score)
            }.collect { info ->
                _state.update { it.copy(dailyChallengeInfo = info) }
            }
        }
    }

    private fun applySettingsToHardware(settings: UserSettings) {
        audioManager.setMuted(!settings.soundEnabled)
        val master = settings.masterVolume
        audioManager.setVolume(settings.sfxVolume * master, settings.bgmVolume * master)
        hapticsManager.setEnabled(settings.hapticsEnabled)
    }

    fun handleIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.PlayerMove -> {
                latestInputPos = intent.position
                virtualCursorPos = intent.position
            }
            is GameIntent.PlayerDragDelta -> {
                val sensitivity = _state.value.settings.touchSensitivity
                val current = virtualCursorPos
                val nextX = (current.x + intent.delta.x * sensitivity).coerceIn(12f, 788f)
                val nextY = (current.y + intent.delta.y * sensitivity).coerceIn(12f, 588f)
                virtualCursorPos = Vec2(nextX, nextY)
                latestInputPos = virtualCursorPos
            }
            is GameIntent.DeathReplayDone -> _deathReplay.value = null
            is GameIntent.Revive -> revive()
            is GameIntent.BuyHearts -> if (lives.buyRefill(profiles)) {
                audioManager.playSfx(SoundId.UNLOCK)
                _state.update { it.copy(reviveHearts = lives.current(), nextHeartInMs = null) }
            }
            is GameIntent.PlayerAimAt -> {
                virtualCursorPos = Vec2(intent.pos.x.coerceIn(12f, 788f), intent.pos.y.coerceIn(12f, 588f))
                latestInputPos = virtualCursorPos
            }
            is GameIntent.JoystickVector -> {
                joystickVector = intent.vector
            }
            is GameIntent.TriggerPhaseDash -> {
                val current = _state.value
                if (current.phase == GamePhase.PLAYING && current.player.dashCooldownMs <= 0L && !current.player.isDashing) {
                    run = run.copy(dashesUsed = run.dashesUsed + 1)
                    audioManager.playSfx(SoundId.DASH)
                    hapticsManager.trigger(HapticStyle.DASH_BURST)
                    _state.update { s ->
                        s.copy(
                            player = s.player.copy(
                                isDashing = true,
                                dashRemainingMs = 180L,
                                dashCooldownMs = (7000L * com.snakechase.app.domain.model.SkinPerks.of(s.activeOrbSkin).dashCooldownMul).toLong(),
                            ),
                        )
                    }
                }
            }
            is GameIntent.SelectLevel -> {
                // Warm up this world's music now, so it starts with the countdown, not after it.
                audioManager.preloadMusic(intent.levelId)
                _state.update { it.copy(levelId = intent.levelId) }
            }
            is GameIntent.StartSelectedLevel -> {
                _state.update { current ->
                    prepareRun(GameEngine.startLevel(current.levelId, current).copy(isDailyChallenge = false))
                }
                audioManager.playBgm(_state.value.levelId)
            }
            is GameIntent.StartDailyChallenge -> {
                val dailyInfo = _state.value.dailyChallengeInfo ?: return
                audioManager.stopBgm()
                _state.update { current ->
                    prepareRun(GameEngine.startLevel(dailyInfo.levelId, current).copy(isDailyChallenge = true))
                }
                audioManager.playBgm(dailyInfo.levelId)
            }
            is GameIntent.CountdownTick -> {
                // One beep on each of 3, 2, 1 and the long beep on GO, exactly as each number appears.
                if (intent.count > 0) {
                    audioManager.playSfx(SoundId.COUNTDOWN_TICK)
                    hapticsManager.trigger(HapticStyle.LIGHT)
                } else {
                    audioManager.playSfx(SoundId.COUNTDOWN_GO)
                    hapticsManager.trigger(HapticStyle.MEDIUM)
                }
                _state.update { it.copy(countdownNumber = intent.count) }
            }
            is GameIntent.CountdownFinished -> {
                _state.update { it.copy(phase = GamePhase.PLAYING) }
            }
            is GameIntent.OpenMultiplayer -> {
                audioManager.stopBgm()
                _state.update { it.copy(phase = GamePhase.MULTIPLAYER_PLAYING) }
            }
            is GameIntent.OpenSnakeHunt -> {
                audioManager.playBgm(0) // Eclipse, like the duels
                _state.update { it.copy(phase = GamePhase.SNAKE_HUNT) }
            }
            is GameIntent.FrameTick -> {
                if (_state.value.phase == GamePhase.PLAYING) {
                    // Apply Joystick Velocity if in Virtual Stick Mode
                    if (_state.value.settings.steeringMode == com.snakechase.app.domain.model.SteeringMode.VIRTUAL_STICK && joystickVector.length() > 0.05f) {
                        val dtSec = intent.deltaMs / 1000f
                        // Analog feel: a small tilt creeps, only a full push runs (gentler on the thumb).
                        val tilt = joystickVector.length().coerceAtMost(1f)
                        val curve = tilt.pow(1.5f) / tilt
                        val speedPxPerSec = JOYSTICK_TOP_SPEED * _state.value.settings.touchSensitivity * curve
                        val nextX = (virtualCursorPos.x + joystickVector.x * speedPxPerSec * dtSec).coerceIn(12f, 788f)
                        val nextY = (virtualCursorPos.y + joystickVector.y * speedPxPerSec * dtSec).coerceIn(12f, 588f)
                        virtualCursorPos = Vec2(nextX, nextY)
                        latestInputPos = virtualCursorPos
                    }

                    val hudBefore = _state.value.hud.survivalTimeMs
                    val result = GameEngine.tick(_state.value, latestInputPos, intent.deltaMs)
                    _state.value = result.newState
                    if (result.events.playerDied) {
                        // A revive (hearts) resumes from just before the catch; daily trials don't revive.
                        reviveCheckpoint = recentFrames.firstOrNull()?.takeIf { !it.isDailyChallenge }
                        _state.update {
                            it.copy(
                                reviveOffered = reviveCheckpoint != null,
                                reviveHearts = lives.current(),
                                nextHeartInMs = lives.msToNextHeart(),
                            )
                        }
                        // The catch replays in slow motion before the game-over panel (DeathReplayView).
                        if (recentFrames.size >= 2) {
                            _deathReplay.value = DeathReplay(recentFrames.toList() + result.newState, result.newState.player.position)
                        }
                        recentFrames.clear()
                    } else {
                        recentFrames.addLast(result.newState)
                        while (recentFrames.size > DeathReplay.FRAMES) recentFrames.removeFirst()
                    }
                    trackRun((result.newState.hud.survivalTimeMs - hudBefore).coerceIn(0L, intent.deltaMs))

                    // Trigger sound & haptics from engine events. A death waits for its replay:
                    // a slowed whoosh now, the crunch and the heavy buzz when the snake bites.
                    if (result.events.playerDied && _deathReplay.value != null) {
                        audioManager.playSfx(SoundId.BULLET_TIME, 0.6f)
                        hapticsManager.trigger(HapticStyle.HEARTBEAT_URGENT)
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(DeathReplay.PLAYBACK_MS)
                            audioManager.playSfx(SoundId.DEATH, 1f)
                            hapticsManager.trigger(HapticStyle.HEAVY)
                        }
                    } else {
                        result.events.playSound?.let { audioManager.playSfx(it, result.events.soundPitch) }
                        result.events.playHaptic?.let { hapticsManager.trigger(it) }
                    }

                    if (result.events.levelCompleted) {
                        completeLevel()
                    } else if (result.events.playerDied) {
                        audioManager.stopBgm()
                        // Save score on death
                        val currentLevel = _state.value.levelId
                        val finalScore = _state.value.score.total
                        val survivalTime = _state.value.hud.survivalTimeMs
                        recordScore(currentLevel, finalScore, survivalTime)
                        if (_state.value.isDailyChallenge) {
                            _state.value.dailyChallengeInfo?.let {
                                settingsRepository.saveDailyChallengeScore(it.epochDay, finalScore)
                            }
                        }

                        payRun(finalScore, cleared = false, stars = 0)

                        // Increment sub-stage deaths for progressive retry mechanic
                        val newSubStageDeaths = _state.value.subStageDeaths + 1
                        _state.update { it.copy(subStageDeaths = newSubStageDeaths) }

                        // Meme roast (Settings → Meme roasts): a short clip mocks the failed run once the
                        // slow-motion catch has played. It fades out as soon as the player goes again.
                        // Only every MEME_EVERY_LOSSES-th loss, so it teases rather than nags.
                        val lossesSinceRoast = _state.value.deathsSinceLastRoast + 1
                        val roastNow = _state.value.settings.memeRoasts && lossesSinceRoast >= MEME_EVERY_LOSSES
                        _state.update { it.copy(deathsSinceLastRoast = if (roastNow) 0 else lossesSinceRoast) }
                        if (roastNow) {
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(DeathReplay.PLAYBACK_MS + ROAST_DELAY_MS)
                                if (_state.value.phase != GamePhase.GAME_OVER) return@launch
                                val roast = audioManager.playMemeRoast()
                                _state.update { it.copy(lastRoastMeme = roast) }
                            }
                        }
                    }
                }
            }
            is GameIntent.PauseGame -> {
                if (_state.value.phase == GamePhase.PLAYING) {
                    _state.update { it.copy(phase = GamePhase.PAUSED) }
                }
            }
            is GameIntent.ResumeGame -> {
                if (_state.value.phase == GamePhase.PAUSED) {
                    _state.update { it.copy(phase = GamePhase.PLAYING, showSettingsOverlay = false) }
                }
            }
            is GameIntent.RestartLevel -> {
                audioManager.stopBgm()
                _state.update { current ->
                    prepareRun(GameEngine.startLevel(current.levelId, current).copy(showSettingsOverlay = false))
                }
                audioManager.playBgm(_state.value.levelId)
            }
            is GameIntent.ReturnToMenu -> {
                audioManager.stopBgm()
                audioManager.stopMemeRoast()
                _state.update { it.copy(phase = GamePhase.MENU, showSettingsOverlay = false, isDailyChallenge = false) }
            }
            is GameIntent.OpenSettings -> {
                _state.update { it.copy(showSettingsOverlay = true) }
            }
            is GameIntent.CloseSettings -> {
                _state.update { it.copy(showSettingsOverlay = false) }
            }
            is GameIntent.UpdateSettings -> {
                settingsRepository.updateSettings(intent.transform)
            }
            is GameIntent.ResetProgress -> {
                settingsRepository.resetAllProgress()
                huntProgress.clearAll()
            }
            is GameIntent.OpenAbout -> {
                _state.update { it.copy(showAboutOverlay = true) }
            }
            is GameIntent.CloseAbout -> {
                _state.update { it.copy(showAboutOverlay = false) }
            }
            is GameIntent.OpenLeaderboard -> {
                _state.update { it.copy(showLeaderboardOverlay = true) }
            }
            is GameIntent.CloseLeaderboard -> {
                _state.update { it.copy(showLeaderboardOverlay = false) }
            }
            is GameIntent.OpenBestiary -> {
                _state.update { it.copy(showBestiaryOverlay = true) }
            }
            is GameIntent.CloseBestiary -> {
                _state.update { it.copy(showBestiaryOverlay = false) }
            }
            is GameIntent.OpenSkinSelect -> {
                _state.update { it.copy(showSkinSelectOverlay = true) }
            }
            is GameIntent.CloseSkinSelect -> {
                _state.update { it.copy(showSkinSelectOverlay = false) }
            }
            is GameIntent.EquipSkin -> profiles.equipSkin(intent.skin.name, anyway = unlocks.everythingOpen)
            is GameIntent.BuySkin -> {
                val price = Economy.skinPrice(intent.skin)
                val owned = intent.skin.name in profiles.profile.value.unlockedSkins
                if (price != null && !owned && profiles.spend(price)) {
                    profiles.unlockSkin(intent.skin.name)
                    profiles.equipSkin(intent.skin.name)
                    audioManager.playSfx(SoundId.LEVEL_COMPLETE)
                }
            }
            is GameIntent.EquipOrb -> profiles.equipOrb(intent.orb.name, anyway = unlocks.everythingOpen)
            is GameIntent.BuyOrb -> {
                val price = intent.orb.price
                val owned = intent.orb.name in profiles.profile.value.unlockedOrbs
                if (price != null && !owned && profiles.spend(price)) {
                    profiles.unlockOrb(intent.orb.name)
                    profiles.equipOrb(intent.orb.name)
                    audioManager.playSfx(SoundId.LEVEL_COMPLETE)
                }
            }
            is GameIntent.OrbCelebrationSeen -> _state.update { it.copy(newOrbSkins = it.newOrbSkins.drop(1)) }
            is GameIntent.SaveProfile -> {
                profiles.setIdentity(intent.name, intent.avatar)
                settingsRepository.updateSettings { it.copy(playerName = profiles.profile.value.name) }
                claimDailyIfDue()
            }
            is GameIntent.OpenProfile -> _state.update { it.copy(showProfileOverlay = true) }
            is GameIntent.CloseProfile -> _state.update { it.copy(showProfileOverlay = false) }
            is GameIntent.DismissDailyReward -> _state.update { it.copy(dailyClaim = null) }
            is GameIntent.ToggleJudgeMode -> {
                _state.update { it.copy(judgeModeActive = !it.judgeModeActive) }
            }
            is GameIntent.ToggleGodMode -> {
                _state.update { it.copy(godModeEnabled = !it.godModeEnabled) }
            }
            is GameIntent.SetDevTargetLevel -> {
                _state.update { it.copy(devTargetLevelId = intent.levelId) }
            }
            is GameIntent.ToggleDevPanelCollapsed -> {
                _state.update { it.copy(devPanelCollapsed = !it.devPanelCollapsed) }
            }
            is GameIntent.ToggleDevResetScore -> {
                _state.update { it.copy(devResetScoreOnJump = !it.devResetScoreOnJump) }
            }
            is GameIntent.ToggleDevSoloStageMode -> {
                _state.update { it.copy(devSoloStageMode = !it.devSoloStageMode) }
            }
            is GameIntent.StepSubStage -> {
                val currentLvlId = _state.value.devTargetLevelId
                val level = LevelRegistry.getById(currentLvlId)
                val currentIdx = _state.value.hud.currentSubStage
                val nextIdx = (currentIdx + intent.delta).coerceIn(0, level.subStages.size - 1)
                handleIntent(
                    GameIntent.JumpToSubStage(
                        subStageIndex = nextIdx,
                        targetLevelId = currentLvlId,
                        resetScore = _state.value.devResetScoreOnJump,
                    ),
                )
            }
            is GameIntent.ReplayCurrentSubStage -> {
                val currentLvlId = _state.value.devTargetLevelId
                val currentIdx = _state.value.hud.currentSubStage
                handleIntent(
                    GameIntent.JumpToSubStage(
                        subStageIndex = currentIdx,
                        targetLevelId = currentLvlId,
                        resetScore = _state.value.devResetScoreOnJump,
                    ),
                )
            }
            is GameIntent.JumpToSubStage -> {
                val targetLvlId = intent.targetLevelId ?: _state.value.devTargetLevelId
                val level = LevelRegistry.getById(targetLvlId)
                val targetStage = level.subStages.getOrNull(intent.subStageIndex)
                if (targetStage != null) {
                    audioManager.playBgm(targetLvlId)
                    // A judge stage jump skips ahead (with a grace period): it can't earn orb skins.
                    run = RunStats(assisted = true)
                    _state.update { current ->
                        val baseState = if (current.primarySnake == null || current.levelId != targetLvlId || current.phase == GamePhase.MENU || current.phase == GamePhase.GAME_OVER || current.phase == GamePhase.LEVEL_COMPLETE) {
                            GameEngine.startLevel(targetLvlId, current).copy(phase = GamePhase.PLAYING)
                        } else {
                            current
                        }
                        val solo = current.devSoloStageMode
                        val newObstacles = ObstacleController.initializeObstacles(level.obstacleConfig, intent.subStageIndex, soloStage = solo)

                        // Check if split snake should be active up to this stage
                        var splitSnake = baseState.splitSnake
                        if (!solo) {
                            if (splitSnake == null) {
                                val hasSplitUnlocked = level.splitAtSubStage in 0..intent.subStageIndex
                                if (hasSplitUnlocked && baseState.primarySnake != null) {
                                    val spawnPos = Vec2(baseState.arenaSize.x - baseState.primarySnake.headPosition.x, baseState.primarySnake.headPosition.y)
                                    splitSnake = SnakeAI.createInitialSnake(SnakeRole.SPLIT, spawnPos, level.snakeConfig)
                                }
                            }
                        } else {
                            // If solo stage mode, only spawn split snake if this specific stage unlocks it
                            splitSnake = if (intent.subStageIndex == level.splitAtSubStage && baseState.primarySnake != null) {
                                val spawnPos = Vec2(baseState.arenaSize.x - baseState.primarySnake.headPosition.x, baseState.primarySnake.headPosition.y)
                                SnakeAI.createInitialSnake(SnakeRole.SPLIT, spawnPos, level.snakeConfig)
                            } else {
                                null
                            }
                        }

                        val safePlayerPos = Vec2(720f, 100f)
                        val safePlayer = baseState.player.copy(
                            position = safePlayerPos,
                            targetPosition = safePlayerPos,
                            velocity = Vec2.ZERO,
                            deathAnimProgress = -1f,
                        )

                        val updatedScore = if (intent.resetScore) {
                            ScoreState(personalBest = baseState.score.personalBest)
                        } else {
                            baseState.score
                        }

                        // 1.8s grace period after stage jump so tester can observe
                        val gracePeriodUntilMs = targetStage.startAtMs + 1800L

                        baseState.copy(
                            phase = GamePhase.PLAYING,
                            levelId = targetLvlId,
                            devTargetLevelId = targetLvlId,
                            player = safePlayer,
                            splitSnake = splitSnake,
                            obstacles = newObstacles,
                            score = updatedScore,
                            devInvincibleUntilMs = gracePeriodUntilMs,
                            devTestedStages = current.devTestedStages + (intent.subStageIndex + 1),
                            hud = baseState.hud.copy(
                                currentSubStage = intent.subStageIndex,
                                subStageName = targetStage.name,
                                survivalTimeMs = targetStage.startAtMs,
                                stageToast = StageToast(
                                    title = "STAGE ${intent.subStageIndex + 1}: ${targetStage.name.uppercase()}",
                                    subtitle = targetStage.subtitle,
                                    bonus = com.snakechase.app.levels.ScoreBudget.stageBonus(level, intent.subStageIndex),
                                    remainingMs = 3000L,
                                    narrative = level.stageNarratives[intent.subStageIndex],
                                ),
                            ),
                        )
                    }
                }
            }
            is GameIntent.ForceWinLevel -> {
                audioManager.playSfx(SoundId.LEVEL_COMPLETE)
                completeLevel()
            }
            is GameIntent.ForceDieNow -> {
                audioManager.stopBgm()
                audioManager.playSfx(SoundId.DEATH)
                val currentLevel = _state.value.levelId
                val finalScore = _state.value.score.total
                val survivalTime = _state.value.hud.survivalTimeMs
                recordScore(currentLevel, finalScore, survivalTime)
                _state.update { it.copy(phase = GamePhase.GAME_OVER) }
            }
            is GameIntent.TriggerSpawn -> {
                val level = LevelRegistry.getById(_state.value.levelId)
                when (intent.type) {
                    "split" -> {
                        val currentPrimary = _state.value.primarySnake
                        if (currentPrimary != null && _state.value.splitSnake == null) {
                            val spawnPos = Vec2(_state.value.arenaSize.x - currentPrimary.headPosition.x, currentPrimary.headPosition.y)
                            val split = SnakeAI.createInitialSnake(SnakeRole.SPLIT, spawnPos, level.snakeConfig)
                            _state.update { it.copy(splitSnake = split) }
                        }
                    }
                    "walls" -> {
                        val current = _state.value
                        val walls = ObstacleController.initializeObstacles(level.obstacleConfig, 1).filterIsInstance<Obstacle.Wall>()
                        val newObs = current.obstacles + walls
                        _state.update { it.copy(obstacles = newObs.distinctBy { o -> if (o is Obstacle.Wall) "${o.rect.x},${o.rect.y}" else o.toString() }) }
                    }
                    "bar" -> {
                        val current = _state.value
                        val bars = ObstacleController.initializeObstacles(level.obstacleConfig, 3).filterIsInstance<Obstacle.RotatingBar>()
                        _state.update { it.copy(obstacles = current.obstacles + bars) }
                    }
                    "mover" -> {
                        val current = _state.value
                        val movers = ObstacleController.initializeObstacles(level.obstacleConfig, 5).filter { it is Obstacle.MovingBlock || it is Obstacle.DangerZone }
                        _state.update { it.copy(obstacles = current.obstacles + movers) }
                    }
                    "teleporter" -> {
                        val current = _state.value
                        val gates = ObstacleController.initializeObstacles(level.obstacleConfig, 7).filterIsInstance<Obstacle.Teleporter>()
                        _state.update { it.copy(obstacles = current.obstacles + gates) }
                    }
                }
            }
            is GameIntent.TriggerSplitSnake -> {
                val level = LevelRegistry.getById(_state.value.levelId)
                val currentPrimary = _state.value.primarySnake
                if (currentPrimary != null && _state.value.splitSnake == null) {
                    val spawnPos = Vec2(_state.value.arenaSize.x - currentPrimary.headPosition.x, currentPrimary.headPosition.y)
                    val split = SnakeAI.createInitialSnake(SnakeRole.SPLIT, spawnPos, level.snakeConfig)
                    _state.update { it.copy(splitSnake = split) }
                }
            }
            is GameIntent.UnlockAllLevels -> {
                settingsRepository.unlockAllLevels()
            }
        }
    }

    /**
     * A level was cleared: save score, unlock the next level, rate it 1-3 stars (saved, best kept),
     * award the level's skin on a first clear, and open the victory screen.
     * Used by real clears and by the judges' "Win level" shortcut alike.
     */
    private fun completeLevel() {
        audioManager.stopBgm()
        val currentLevel = _state.value.levelId
        val finalScore = _state.value.score.total
        val survivalTime = _state.value.hud.survivalTimeMs
        // The stored best, read BEFORE saving: the engine already copied this run's
        // score into state.bestScores, so that map would compare a new best against itself.
        val prevBest = settingsRepository.bestScores.value[currentLevel] ?: 0
        recordScore(currentLevel, finalScore, survivalTime)
        if (_state.value.isDailyChallenge) {
            _state.value.dailyChallengeInfo?.let {
                settingsRepository.saveDailyChallengeScore(it.epochDay, finalScore)
            }
        }
        if (currentLevel < 5) {
            settingsRepository.unlockLevel(currentLevel + 1)
        }
        // ── Compute Star Rating & Skin Unlock ─────────────────
        val level = LevelRegistry.getById(currentLevel)
        val maxScore = level.scoringConfig.levelScoreMax
        val stars = LevelStars.forClear(finalScore, maxScore)
        // Read before saving: a level with no stars yet has never been cleared.
        val firstClear = (settingsRepository.levelStars.value[currentLevel] ?: 0) == 0
        settingsRepository.saveLevelStars(currentLevel, stars)
        val starsNow = settingsRepository.levelStars.value
        val allLevelsCleared = firstClear && (1..5).all { (starsNow[it] ?: 0) > 0 }
        val isNewBest = finalScore > prevBest

        // Unlock level skin if first time clearing this level
        val skinForLevel = com.snakechase.app.domain.model.SnakeSkin.entries
            .firstOrNull { it.unlockedByLevel == currentLevel }
        val alreadyOwned = skinForLevel?.name in profiles.profile.value.unlockedSkins
        val skinToAward = if (skinForLevel != null && !alreadyOwned) skinForLevel else null

        skinToAward?.let { profiles.unlockSkin(it.name) }
        // Star skins: three stars on a level (or all of them) earns a legendary look.
        val owned = profiles.profile.value.unlockedSkins
        val bonusSkins = SnakeSkin.earnedByStars(settingsRepository.levelStars.value)
            .filter { it.name !in owned && it != skinToAward }
        bonusSkins.forEach { profiles.unlockSkin(it.name) }
        payRun(finalScore, cleared = true, stars = stars)

        val levelResult = com.snakechase.app.domain.model.LevelResult(
            levelId = currentLevel,
            finalScore = finalScore,
            levelScoreMax = maxScore,
            stars = stars,
            skinUnlocked = skinToAward,
            bonusSkins = bonusSkins,
            isNewPersonalBest = isNewBest,
            stagesCleared = _state.value.hud.currentSubStage + 1,
            totalStages = level.subStages.size,
            firstClear = firstClear,
            allLevelsCleared = allLevelsCleared,
            totalStars = (1..5).sumOf { starsNow[it] ?: 0 },
        )
        val newStarRatings = _state.value.levelStarRatings.toMutableMap()
        val prevStars = newStarRatings[currentLevel] ?: 0
        if (stars > prevStars) newStarRatings[currentLevel] = stars

        _state.update {
            it.copy(
                phase = com.snakechase.app.domain.model.GamePhase.LEVEL_VICTORY,
                levelResult = levelResult,
                levelStarRatings = newStarRatings,
            )
        }
    }
}

/** Arena units per second at full joystick tilt (was 520: too twitchy on phones). */
private const val JOYSTICK_TOP_SPEED = 430f

/** A revive's protection: a short shield, and the snakes stunned a little longer than that. */
private const val REVIVE_SHIELD_MS = 3_000L
private const val REVIVE_STUN_MS = 2_000L

/** Pause between the catch replay ending and the roast, so the crunch lands first. */
private const val ROAST_DELAY_MS = 350L

/** A meme roast plays on every third failed run. */
private const val MEME_EVERY_LOSSES = 3

/** A catch to replay: the last [FRAMES] frames of the run and where the bite happened. */
data class DeathReplay(val frames: List<GameState>, val catchAt: Vec2) {
    companion object {
        /** About half a second of play at 60 fps. */
        const val FRAMES = 32

        /** That half second, played back in slow motion. */
        const val PLAYBACK_MS = 1_250L
    }
}
