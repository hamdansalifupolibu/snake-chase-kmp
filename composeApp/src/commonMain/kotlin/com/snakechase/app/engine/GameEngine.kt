package com.snakechase.app.engine

import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.HapticStyle
import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.ScoreState
import com.snakechase.app.domain.model.SnakeEntity
import com.snakechase.app.domain.model.SnakeMode
import com.snakechase.app.domain.model.SnakeRole
import com.snakechase.app.domain.model.SoundId
import com.snakechase.app.domain.model.StageToast
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.levels.ScoreBudget
import kotlin.math.max
import kotlin.math.min

object GameEngine {

    data class EngineEvents(
        val playSound: SoundId? = null,
        val soundPitch: Float = 1.0f,
        val playHaptic: HapticStyle? = null,
        val levelCompleted: Boolean = false,
        val playerDied: Boolean = false,
    )

    data class EngineTickResult(
        val newState: GameState,
        val events: EngineEvents = EngineEvents(),
    )

    /**
     * Initializes a fresh GameState for the given level.
     */
    /** The orb's top speed in arena units per second (snakes top out around 250-350). */
    const val ORB_MAX_SPEED = 700f

    const val EASY_SHIELDS_PER_STAGE = 2
    const val EASY_SHIELDS_LAST_STAGE = 3
    const val EASY_SHIELD_GAP_MS = 8_000L
    const val EASY_RESCUE_SHIELD_MS = 2_500L
    const val EASY_RESCUE_STUN_MS = 1_500L

    private fun easyOr(p: com.snakechase.app.levels.SnakePersonalityConfig, state: GameState) = if (state.runDifficulty == com.snakechase.app.domain.model.Difficulty.EASY) com.snakechase.app.domain.model.Difficulty.easySnake(p) else p

    fun startLevel(levelId: Int, currentState: GameState): GameState {
        val levelConfig = LevelRegistry.getById(levelId)
        val center = Vec2(currentState.arenaSize.x / 2f, currentState.arenaSize.y / 2f)
        val playerSpawn = Vec2(center.x, center.y + 120f)
        val snakeSpawn = Vec2(center.x, center.y - 140f)

        val player = PlayerOrb(position = playerSpawn, targetPosition = playerSpawn)
        val primarySnake = SnakeAI.createInitialSnake(SnakeRole.PRIMARY, snakeSpawn, levelConfig.snakeConfig)
        val obstacles = ObstacleController.initializeObstacles(levelConfig.obstacleConfig, currentSubStageIndex = 0, difficulty = currentState.settings.difficulty, levelId = levelId)

        val firstStage = levelConfig.subStages.firstOrNull()
        val hud = currentState.hud.copy(
            currentSubStage = 0,
            subStageName = firstStage?.name ?: "Stage 1",
            totalSubStages = levelConfig.subStages.size,
            stageToast = StageToast(
                title = firstStage?.name ?: "STAGE 1",
                subtitle = firstStage?.subtitle ?: "Survive",
                bonus = 0, // you start here: reaching later stages is what pays
                remainingMs = 3200L,
                narrative = levelConfig.stageNarratives[0],
            ),
            survivalTimeMs = 0L,
            timeRemainingMs = levelConfig.completionTimeMs,
        )

        PowerUpSystem.onLevelReset()

        return currentState.copy(
            phase = GamePhase.COUNTDOWN,
            levelId = levelId,
            runDifficulty = currentState.settings.difficulty,
            player = player,
            primarySnake = primarySnake,
            splitSnake = null,
            extraSnakes = emptyList(),
            obstacles = obstacles,
            particles = emptyList(),
            hud = hud,
            subStageDeaths = 0,
            shieldOrb = null,
            shieldState = com.snakechase.app.domain.model.ShieldState(),
            shieldEverUsed = false,
            activePowerUpOrb = null,
            activePowerUps = emptyList(),
            heartbeatAccumulatorMs = 0L,
            comboMultiplier = 1.0f,
            comboChargePct = 0.0f,
            bulletTimeRemainingMs = 0L,
            screenShakeRemainingMs = 0L,
            screenShakeIntensity = 0f,
            subStageClearAnimMs = 0L,
            subStageClearIndex = 0,
            deathSlowMoRemainingMs = 0L,
            nearMissStreakCount = 0,
            streakWindowRemainingMs = 0L,
            grazeCooldownMs = 0L,
            isOnFire = false,
            ambientParticles = (1..25).map { ParticleSystem.spawnAmbientParticle(currentState.arenaSize, levelId) },
            // A new run starts from zero: total, breakdown AND combo (the combo used to carry over,
            // so a fresh run could open on "×3.0, combo 1495"). Only the personal best is kept.
            score = ScoreState(personalBest = currentState.score.personalBest),
            levelResult = null,
            countdownNumber = 3,
        )
    }

    /**
     * Executes one frame of simulation.
     */
    fun tick(
        state: GameState,
        playerInputPos: Vec2?,
        deltaTimeMs: Long,
    ): EngineTickResult {
        if (state.phase != GamePhase.PLAYING) {
            return EngineTickResult(state)
        }

        val isBulletTime = state.bulletTimeRemainingMs > 0L
        val timeDilation = if (isBulletTime) 0.45f else 1.0f
        val effectiveDtSec = (deltaTimeMs / 1000f) * timeDilation
        val effectiveDeltaTimeMs = (deltaTimeMs * timeDilation).toLong()
        val newBulletTimeMs = max(0L, state.bulletTimeRemainingMs - deltaTimeMs)

        val dtSec = effectiveDtSec
        val level = LevelRegistry.getById(state.levelId)
        var soundToPlay: SoundId? = null
        var soundPitch = 1.0f
        var hapticToPlay: HapticStyle? = null
        var playerDied = false
        var levelCompleted = false

        // Phase Dash Timers
        val newDashRemainingMs = max(0L, state.player.dashRemainingMs - deltaTimeMs)
        val newDashCooldownMs = max(0L, state.player.dashCooldownMs - deltaTimeMs)
        val isDashing = newDashRemainingMs > 0L

        // 1. Advance Survival Time
        val newSurvivalTimeMs = state.hud.survivalTimeMs + effectiveDeltaTimeMs
        val newTimeRemainingMs = max(0L, level.completionTimeMs - newSurvivalTimeMs)

        // 2. Check Level Completion
        if (newSurvivalTimeMs >= level.completionTimeMs) {
            val finalScore = (state.score.total + ScoreBudget.completionBonus(level)).coerceAtMost(level.scoringConfig.levelScoreMax)
            val updatedBestScores = state.bestScores.toMutableMap().apply {
                this[level.id] = max(this[level.id] ?: 0, finalScore)
            }
            val updatedUnlocked = state.unlockedLevelIds + min(5, level.id + 1)

            val completedState = state.copy(
                phase = GamePhase.LEVEL_COMPLETE,
                score = state.score.copy(total = finalScore),
                unlockedLevelIds = updatedUnlocked,
                bestScores = updatedBestScores,
            )
            return EngineTickResult(
                completedState,
                EngineEvents(playSound = SoundId.LEVEL_COMPLETE, soundPitch = 1.4f, playHaptic = HapticStyle.HEAVY, levelCompleted = true),
            )
        }

        // 3. Sub-stage Progression
        var currentSubStageIndex = state.hud.currentSubStage
        var stageToast = state.hud.stageToast
        var stageBonusToAdd = 0
        var subStageClearMs = max(0L, state.subStageClearAnimMs - deltaTimeMs)
        var subStageClearIdx = state.subStageClearIndex

        val nextStage = level.subStages.getOrNull(currentSubStageIndex + 1)
        if (nextStage != null && newSurvivalTimeMs >= nextStage.startAtMs) {
            currentSubStageIndex++
            stageBonusToAdd = ScoreBudget.stageBonus(level, currentSubStageIndex)
            soundToPlay = SoundId.STAGE_ADVANCE
            soundPitch = (1.0f + currentSubStageIndex * 0.12f).coerceIn(1.0f, 1.8f)
            hapticToPlay = HapticStyle.ADRENALINE_SNAP
            subStageClearMs = 700L
            subStageClearIdx = currentSubStageIndex
            val narrative = level.stageNarratives[currentSubStageIndex]
            stageToast = StageToast(
                title = "STAGE ${currentSubStageIndex + 1}: ${nextStage.name.uppercase()}",
                subtitle = nextStage.subtitle,
                bonus = stageBonusToAdd,
                remainingMs = if (narrative != null) 3400L else 2800L,
                narrative = narrative,
            )
        } else if (stageToast != null) {
            val rem = stageToast.remainingMs - deltaTimeMs
            stageToast = if (rem <= 0) null else stageToast.copy(remainingMs = rem)
        }

        // 4. Update Player Position
        val targetPos = playerInputPos ?: state.player.targetPosition
        // Clamp target to arena bounds
        val clampedTarget = Vec2(
            x = max(state.player.radius, min(state.arenaSize.x - state.player.radius, targetPos.x)),
            y = max(state.player.radius, min(state.arenaSize.y - state.player.radius, targetPos.y)),
        )

        var newPlayerPos: Vec2
        val playerVelocity: Vec2

        // Check Speed Burst power-up
        val hasSpeedBurst = state.activePowerUps.any { it.type == com.snakechase.app.domain.model.PowerUpType.SPEED_BURST }
        val speedMul = if (hasSpeedBurst) 1.40f else 1.0f
        // The worn orb skin's small edge (see SkinPerks).
        val orbPerk = com.snakechase.app.domain.model.SkinPerks.of(state.activeOrbSkin)

        if (state.levelId == 4) {
            // Crystal Tundra low-friction ice sliding drift inertia
            val iceFriction = 0.86f
            val desiredVel = ((clampedTarget - state.player.position) / max(0.001f, dtSec)) * speedMul
            var smoothedVel = (state.player.velocity * iceFriction) + (desiredVel * (1f - iceFriction))
            val iceTop = ORB_MAX_SPEED * speedMul * orbPerk.speedMul
            if (smoothedVel.length() > iceTop) smoothedVel = smoothedVel * (iceTop / smoothedVel.length())
            val posCandidate = state.player.position + (smoothedVel * dtSec)
            newPlayerPos = Vec2(
                x = max(state.player.radius, min(state.arenaSize.x - state.player.radius, posCandidate.x)),
                y = max(state.player.radius, min(state.arenaSize.y - state.player.radius, posCandidate.y)),
            )
            playerVelocity = smoothedVel
        } else {
            val lerpFactor = min(1f, 24f * speedMul * dtSec)
            var step = (clampedTarget - state.player.position) * lerpFactor
            // A top speed: the orb chases the cursor/finger fast, but can never jump across the
            // arena (no teleporting by flicking the mouse or tapping somewhere else).
            val maxStep = ORB_MAX_SPEED * speedMul * orbPerk.speedMul * dtSec
            val stepLen = step.length()
            if (stepLen > maxStep) step = step * (maxStep / stepLen)
            newPlayerPos = state.player.position + step
            playerVelocity = (newPlayerPos - state.player.position) / max(0.001f, dtSec)
        }

        if (isDashing) {
            val dashDir = if (playerVelocity.length() > 0.05f) playerVelocity.normalized() else Vec2(0f, -1f)
            val step = dashDir * 450f * speedMul * dtSec
            newPlayerPos = Vec2(
                x = max(state.player.radius, min(state.arenaSize.x - state.player.radius, newPlayerPos.x + step.x)),
                y = max(state.player.radius, min(state.arenaSize.y - state.player.radius, newPlayerPos.y + step.y)),
            )
        }

        // Whirlpools / snow whirls carry the orb around (they no longer kill it).
        val orbDrift = Vortex.drift(newPlayerPos, state.obstacles, dtSec)
        if (orbDrift != Vec2.ZERO) {
            newPlayerPos = Vec2(
                x = (newPlayerPos.x + orbDrift.x).coerceIn(state.player.radius, state.arenaSize.x - state.player.radius),
                y = (newPlayerPos.y + orbDrift.y).coerceIn(state.player.radius, state.arenaSize.y - state.player.radius),
            )
        }

        // Walls, bars and blocks are solid for the orb too. Without protection touching one is fatal
        // (checked below). A dash or the judges' invincibility used to let the orb ghost through
        // them; now it only saves you from dying, and the orb bumps and slides along them like the
        // snakes do. The one exception is the invincible shield orb: holding it, you pass through.
        val hasShieldNow = state.activePowerUps.any { it.type == com.snakechase.app.domain.model.PowerUpType.SHIELD }
        val safeButSolid = state.godModeEnabled || isDashing || state.hud.survivalTimeMs < state.devInvincibleUntilMs
        if (safeButSolid && !hasShieldNow) {
            newPlayerPos = Solid.resolve(state.player.position, newPlayerPos, state.player.radius, Solid.from(state.obstacles)).first
        }

        // Trailing ribbon history (keep last 12 points)
        val newTrail = (listOf(newPlayerPos) + state.player.trailPoints).take(12)
        val dashTrailHistory = if (isDashing) (listOf(newPlayerPos) + state.player.dashTrailHistory).take(6) else emptyList()

        // Sub-stage dynamic speed multiplier
        val subStageSpeedMul = when (state.levelId) {
            3 -> if (currentSubStageIndex >= 6) 1.25f else 1.0f
            4 -> if (currentSubStageIndex >= 2) 1.14f else 1.0f
            5 -> when {
                currentSubStageIndex >= 3 -> 1.35f
                currentSubStageIndex == 0 -> 1.24f
                else -> 1.15f
            }
            else -> 1.0f
        }

        // 5. Update Snake Predators (Primary & Split)
        val activePrimarySnake = state.primarySnake ?: SnakeAI.createInitialSnake(
            SnakeRole.PRIMARY,
            Vec2(state.arenaSize.x / 2f, 80f),
            level.snakeConfig,
        )

        // Dynamic Snake Body Length Growth per Sub-Stage
        val targetSegmentCount = min(
            level.snakeConfig.bodySegments,
            level.snakeConfig.initialSegments + (currentSubStageIndex * level.snakeConfig.segmentsPerSubStage),
        )
        val snakeWithScaledBody = if (activePrimarySnake.segments.size < targetSegmentCount) {
            val lastSeg = activePrimarySnake.segments.lastOrNull()
            val extraSegs = (activePrimarySnake.segments.size until targetSegmentCount).map { i ->
                com.snakechase.app.domain.model.SnakeSegment(
                    position = lastSeg?.position ?: activePrimarySnake.headPosition,
                    angle = lastSeg?.angle ?: 0f,
                )
            }
            activePrimarySnake.copy(segments = activePrimarySnake.segments + extraSegs)
        } else {
            activePrimarySnake
        }

        val snakeSolids = Solid.from(state.obstacles)
        val updatedPrimarySnake = SnakeAI.updateSnake(
            snakeWithScaledBody,
            state.player.copy(position = newPlayerPos, velocity = playerVelocity),
            easyOr(level.snakeConfig.personality, state),
            level.snakeConfig.segmentSpacing,
            newSurvivalTimeMs,
            dtSec,
            subStageSpeedMul * state.runDifficulty.snakeSpeedFor(state.levelId),
            arenaSize = state.arenaSize,
            solids = snakeSolids,
        )

        // Split Snake Handling
        var updatedSplitSnake = state.splitSnake
        var updatedExtraSnakes = state.extraSnakes.toMutableList()
        if (level.splitAtSubStage >= 0 && currentSubStageIndex >= level.splitAtSubStage) {
            if (updatedSplitSnake == null) {
                // Spawn split snake 1 (Amber / Gold)
                soundToPlay = SoundId.SPLIT_SPAWN
                hapticToPlay = HapticStyle.HEAVY
                val spawnPos = Vec2(state.arenaSize.x - updatedPrimarySnake.headPosition.x, updatedPrimarySnake.headPosition.y)
                updatedSplitSnake = SnakeAI.createInitialSnake(SnakeRole.SPLIT, spawnPos, level.snakeConfig)
            } else {
                updatedSplitSnake = SnakeAI.updateSnake(
                    updatedSplitSnake,
                    state.player.copy(position = newPlayerPos, velocity = playerVelocity),
                    easyOr(level.snakeConfig.splitPersonality, state),
                    level.snakeConfig.segmentSpacing,
                    newSurvivalTimeMs,
                    dtSec,
                    subStageSpeedMul * state.runDifficulty.snakeSpeedFor(state.levelId),
                    arenaSize = state.arenaSize,
                    solids = snakeSolids,
                )
            }

            // Triad Bloom: Spawn 3rd snake (Violet / Purple) if configured
            if (level.snakeConfig.splitCount >= 2 && state.runDifficulty != com.snakechase.app.domain.model.Difficulty.EASY) {
                if (updatedExtraSnakes.isEmpty()) {
                    val spawnPos3 = Vec2(state.arenaSize.x / 2f, state.arenaSize.y - 80f)
                    val extraSnake = SnakeAI.createInitialSnake(SnakeRole.SPLIT, spawnPos3, level.snakeConfig)
                    updatedExtraSnakes.add(extraSnake)
                } else {
                    val p2 = level.snakeConfig.splitPersonality2 ?: level.snakeConfig.splitPersonality
                    updatedExtraSnakes = updatedExtraSnakes.map { snake ->
                        SnakeAI.updateSnake(
                            snake,
                            state.player.copy(position = newPlayerPos, velocity = playerVelocity),
                            easyOr(p2, state),
                            level.snakeConfig.segmentSpacing,
                            newSurvivalTimeMs,
                            dtSec,
                            subStageSpeedMul * state.runDifficulty.snakeSpeedFor(state.levelId),
                            arenaSize = state.arenaSize,
                            solids = snakeSolids,
                        )
                    }.toMutableList()
                }
            }
        }

        // 6. Update Obstacles & Check Unlocks
        var obstacles = ObstacleController.updateObstacles(state.obstacles, dtSec, deltaTimeMs)
        // Refresh unlocked obstacles for current stage
        if (state.devSoloStageMode) {
            val soloConfigObstacles = ObstacleController.initializeObstacles(level.obstacleConfig, currentSubStageIndex, soloStage = true, difficulty = state.runDifficulty, levelId = state.levelId)
            val soloIds = soloConfigObstacles.map { it.id }.toSet()
            val existingMatching = obstacles.filter { it.id in soloIds }
            val existingIds = existingMatching.map { it.id }.toSet()
            val newSolo = soloConfigObstacles.filter { it.id !in existingIds }
            obstacles = existingMatching + newSolo
        } else {
            val fullConfigObstacles = ObstacleController.initializeObstacles(level.obstacleConfig, currentSubStageIndex, soloStage = false, difficulty = state.runDifficulty, levelId = state.levelId)
            val activeIds = obstacles.map { it.id }.toSet()
            val newlyUnlocked = fullConfigObstacles.filter { it.id !in activeIds }
            if (newlyUnlocked.isNotEmpty()) {
                obstacles = obstacles + newlyUnlocked
            }
        }

        // 6.5 Snake Hazard Concussion Trapping ("The Uno Reverse")
        val newParticles = ParticleSystem.updateParticles(state.particles, deltaTimeMs).toMutableList()
        var concussedPrimary = swirlSnake(updatedPrimarySnake, state, dtSec)
        var concussedSplit = updatedSplitSnake?.let { swirlSnake(it, state, dtSec) }
        var concussedExtras = updatedExtraSnakes.map { swirlSnake(it, state, dtSec) }.toMutableList()
        var screenShakeMs = max(0L, state.screenShakeRemainingMs - deltaTimeMs)
        var screenShakeInt = if (screenShakeMs > 0L) state.screenShakeIntensity * 0.92f else 0f

        for (obs in obstacles) {
            if (!concussedPrimary.isStunned && isObstacleHittingSnake(obs, concussedPrimary.headPosition, concussedPrimary.headRadius)) {
                concussedPrimary = concussedPrimary.copy(stunRemainingMs = 1800L, mode = SnakeMode.STUNNED)
                soundToPlay = SoundId.CONCUSSION
                hapticToPlay = HapticStyle.CONCUSSION_RUMBLE
                screenShakeMs = 300L
                screenShakeInt = 8f
                newParticles.add(ParticleSystem.spawnNearMissRing(concussedPrimary.headPosition, true, 0xFFFFE844))
            }
            concussedSplit?.let { s ->
                if (!s.isStunned && isObstacleHittingSnake(obs, s.headPosition, s.headRadius)) {
                    concussedSplit = s.copy(stunRemainingMs = 1800L, mode = SnakeMode.STUNNED)
                    soundToPlay = SoundId.CONCUSSION
                    hapticToPlay = HapticStyle.CONCUSSION_RUMBLE
                    screenShakeMs = 300L
                    screenShakeInt = 8f
                    newParticles.add(ParticleSystem.spawnNearMissRing(s.headPosition, true, 0xFFFFE844))
                }
            }
            for (idx in concussedExtras.indices) {
                val ex = concussedExtras[idx]
                if (!ex.isStunned && isObstacleHittingSnake(obs, ex.headPosition, ex.headRadius)) {
                    concussedExtras[idx] = ex.copy(stunRemainingMs = 1800L, mode = SnakeMode.STUNNED)
                    soundToPlay = SoundId.CONCUSSION
                    hapticToPlay = HapticStyle.CONCUSSION_RUMBLE
                    screenShakeMs = 300L
                    screenShakeInt = 8f
                    newParticles.add(ParticleSystem.spawnNearMissRing(ex.headPosition, true, 0xFFFFE844))
                }
            }
        }

        val allActiveSnakes = listOfNotNull(concussedPrimary, concussedSplit) + concussedExtras
        val minSnakeDist = allActiveSnakes.minOfOrNull { (it.headPosition - newPlayerPos).length() } ?: Float.MAX_VALUE

        // Dynamic Proximity Greed Multiplier (2x faster when ON FIRE)
        val greedChargeRate = if (state.isOnFire) 0.80f else 0.40f
        val newComboChargePct = if (minSnakeDist <= 160f) {
            min(1.0f, state.comboChargePct + (dtSec * greedChargeRate))
        } else if (minSnakeDist > 220f) {
            max(0.0f, state.comboChargePct - (dtSec * 0.25f))
        } else {
            state.comboChargePct
        }
        val newComboMultiplier = when {
            newComboChargePct >= 0.95f -> 3.0f
            newComboChargePct >= 0.60f -> 2.0f
            newComboChargePct >= 0.25f -> 1.5f
            else -> 1.0f
        }

        // Dynamic Heartbeat Cadence Haptics
        var newHeartbeatAcc = state.heartbeatAccumulatorMs + deltaTimeMs
        if (hapticToPlay == null) {
            if (minSnakeDist <= 110f) {
                if (newHeartbeatAcc >= 360L) {
                    hapticToPlay = HapticStyle.HEARTBEAT_URGENT
                    newHeartbeatAcc = 0L
                }
            } else if (minSnakeDist <= 200f) {
                if (newHeartbeatAcc >= 760L) {
                    hapticToPlay = HapticStyle.HEARTBEAT_CALM
                    newHeartbeatAcc = 0L
                }
            } else {
                newHeartbeatAcc = 0L
            }
        }

        // 7. Evaluate Near-Miss Grazing across all active snakes
        val scoring = ScoreBudget.config(level)
        var activeNearMiss = ScoringEngine.evaluateNearMiss(
            state.player.copy(position = newPlayerPos),
            concussedPrimary,
            scoring,
            state.score.combo.multiplier,
            reach = orbPerk.grazeMul,
        )
        for (snake in allActiveSnakes.drop(1)) {
            val candidate = ScoringEngine.evaluateNearMiss(
                state.player.copy(position = newPlayerPos),
                snake,
                scoring,
                state.score.combo.multiplier,
                reach = orbPerk.grazeMul,
            )
            if (candidate.isNearMiss && candidate.pointsAwarded > activeNearMiss.pointsAwarded) {
                activeNearMiss = candidate
            }
        }
        // One graze per pass: being close counts once, then not again until the cooldown has run
        // out. (Counting every frame made a 1 s graze worth ~60 grazes, 60 sounds and 60 buzzes.)
        // [activeNearMiss] keeps the live closeness for the orb's rings; [grazeScored] is what counts.
        val grazeCooldown = max(0L, state.grazeCooldownMs - deltaTimeMs)
        val grazeScored = activeNearMiss.isNearMiss && grazeCooldown == 0L
        val newGrazeCooldown = if (grazeScored) ScoreBudget.GRAZE_COOLDOWN_MS else grazeCooldown
        val scoredNearMiss = if (grazeScored) activeNearMiss else ScoringEngine.NearMissResult(false, false, 0f, 0)

        // 7.5 Phase 3: ON FIRE Streak Logic
        var newStreakCount = state.nearMissStreakCount
        var newStreakWindowMs = max(0L, state.streakWindowRemainingMs - deltaTimeMs)
        var newIsOnFire = state.isOnFire

        if (activeNearMiss.isNearMiss) {
            if (grazeScored) newStreakCount += if (activeNearMiss.isPerfect) 2 else 1
            newStreakWindowMs = 4000L // 4 second combo grazing window
            if (newStreakCount >= 3 && !newIsOnFire) {
                newIsOnFire = true
                soundToPlay = SoundId.ON_FIRE
                soundPitch = 1.45f
                hapticToPlay = HapticStyle.STREAK_FIRE
                newParticles.add(ParticleSystem.spawnScorePopup(newPlayerPos + Vec2(0f, -42f), "🔥 ON FIRE! 2X SPEED", 0xFFFF3D00))
            }
        } else if (newStreakWindowMs <= 0L && (newStreakCount > 0 || newIsOnFire)) {
            if (newIsOnFire) {
                soundToPlay = SoundId.STREAK_BROKEN
                soundPitch = 0.65f
            }
            newStreakCount = 0
            newIsOnFire = false
        }

        // If ON FIRE, emit trailing flame motes
        if (newIsOnFire) {
            newParticles.add(ParticleSystem.spawnFlameTrail(newPlayerPos))
        }

        // 7.6 Phase 4: Ambient World Particles Simulation
        var updatedAmbientParticles = ParticleSystem.updateParticles(state.ambientParticles, deltaTimeMs).toMutableList()
        if (updatedAmbientParticles.size < 25) {
            updatedAmbientParticles.add(ParticleSystem.spawnAmbientParticle(state.arenaSize, state.levelId))
        }

        // Adrenaline Bullet Time Trigger on Close Shave
        var finalBulletTimeMs = newBulletTimeMs
        if (scoredNearMiss.isPerfect && finalBulletTimeMs <= 0L) {
            finalBulletTimeMs = 280L
            soundToPlay = SoundId.BULLET_TIME
            soundPitch = 0.85f
            hapticToPlay = HapticStyle.ADRENALINE_SNAP
        }

        // 8. Near Miss Ring Particles & Score Popups (once per scored graze)
        if (scoredNearMiss.isNearMiss) {
            val ringColor = if (activeNearMiss.isPerfect) level.palette.cursorTheme.perfectRingArgb else level.palette.cursorTheme.nearMissRingArgb
            newParticles.add(ParticleSystem.spawnNearMissRing(newPlayerPos, activeNearMiss.isPerfect, ringColor))

            // Score popup particle
            val popupText = if (activeNearMiss.isPerfect) "+${activeNearMiss.pointsAwarded} PERFECT!" else "+${activeNearMiss.pointsAwarded}"
            val popupColor = if (activeNearMiss.isPerfect) 0xFFFFD700 else ringColor
            newParticles.add(ParticleSystem.spawnScorePopup(newPlayerPos + Vec2(0f, -20f), popupText, popupColor))

            // Screen shake you can feel, growing with the graze streak (capped so it never blurs play).
            val streakKick = newStreakCount.coerceAtMost(8) * 0.45f
            screenShakeMs = if (activeNearMiss.isPerfect) 280L else 170L
            screenShakeInt = (if (activeNearMiss.isPerfect) 8f else 4.5f) + streakKick

            if (soundToPlay == null) {
                soundToPlay = if (activeNearMiss.isPerfect) SoundId.PERFECT_NEAR_MISS else SoundId.NEAR_MISS
                val streakBoost = (newStreakCount.coerceAtMost(10) * 0.08f)
                val basePitch = 1.0f + streakBoost
                soundPitch = if (activeNearMiss.isPerfect) (basePitch * 1.15f).coerceIn(0.8f, 2.0f) else basePitch.coerceIn(0.8f, 2.0f)
                // Vibration matches the shake: a firm snap for a perfect graze, a tap otherwise.
                hapticToPlay = if (activeNearMiss.isPerfect) HapticStyle.ADRENALINE_SNAP else HapticStyle.MEDIUM
            }
        }

        // 9. Phase 5: Power-Up Orbs Spawning & Ticking
        var activeFieldOrb = state.activePowerUpOrb
        // Easy: shields come to you. One as every stage begins, another a little later, and a third
        // in the final stage, each at a random open spot (on top of the normal power-ups).
        val newStage = currentSubStageIndex != state.hud.currentSubStage
        var easyShields = if (newStage) 0 else state.easyShieldsThisStage
        var easyShieldNext = if (newStage) 0L else state.easyShieldNextMs
        if (state.runDifficulty == com.snakechase.app.domain.model.Difficulty.EASY) {
            val cap = if (currentSubStageIndex >= level.subStages.lastIndex) EASY_SHIELDS_LAST_STAGE else EASY_SHIELDS_PER_STAGE
            val shieldOn = state.activePowerUps.any { it.type == com.snakechase.app.domain.model.PowerUpType.SHIELD }
            val now = state.hud.survivalTimeMs
            if (activeFieldOrb == null && !shieldOn && easyShields < cap && now >= easyShieldNext) {
                activeFieldOrb = PowerUpSystem.spawnOrb(state).copy(type = com.snakechase.app.domain.model.PowerUpType.SHIELD)
                easyShields++
                easyShieldNext = now + EASY_SHIELD_GAP_MS
            }
        }
        if (activeFieldOrb == null && PowerUpSystem.shouldSpawnOrb(state)) {
            activeFieldOrb = PowerUpSystem.spawnOrb(state)
        }
        val (tickedOrb, tickedActivePowerUps, newlyCollectedType) = PowerUpSystem.tickPowerUps(
            activeFieldOrb,
            state.activePowerUps,
            state.player.copy(position = newPlayerPos),
            state.levelId,
            deltaTimeMs,
        )
        if (newlyCollectedType != null) {
            soundToPlay = SoundId.POWER_UP
            soundPitch = 1f
            hapticToPlay = HapticStyle.HEAVY
            screenShakeMs = 260L
            screenShakeInt = 6.5f
            val orbColor = newlyCollectedType.colorArgb
            newParticles.add(ParticleSystem.spawnNearMissRing(newPlayerPos, true, orbColor))
            newParticles.add(ParticleSystem.spawnScorePopup(newPlayerPos + Vec2(0f, -24f), "${newlyCollectedType.emoji} ${newlyCollectedType.displayName}!", orbColor))
        }

        // Active Shield Check (either from ActivePowerUp or legacy shieldState)
        // The shield lasts exactly as long as its power-up. (It used to also count "shielded last
        // frame" and reset itself to a fresh 5 s every frame, so once picked up it never ended.)
        val shieldPowerUp = tickedActivePowerUps.firstOrNull { it.type == com.snakechase.app.domain.model.PowerUpType.SHIELD }
        val hasActiveShield = shieldPowerUp != null
        val hasGhostWalk = tickedActivePowerUps.any { it.type == com.snakechase.app.domain.model.PowerUpType.GHOST_WALK }
        val hasScoreMagnet = tickedActivePowerUps.any { it.type == com.snakechase.app.domain.model.PowerUpType.SCORE_MAGNET }

        // 10. Update Score Engine (with Greed Multiplier & Score Magnet)
        val effectiveGreedMul = if (hasScoreMagnet) newComboMultiplier * 1.5f else newComboMultiplier
        val newScore = ScoringEngine.tickScore(
            state.score,
            scoredNearMiss,
            stageBonusToAdd,
            scoring,
            dtSec,
            deltaTimeMs,
            totalSurvivalTimeMs = newSurvivalTimeMs,
            greedMultiplier = effectiveGreedMul,
        )

        // 11. Check Lethal Collisions (Swept CCD for 100% reliability, Dash i-frames, Shield & Ghost Walk)
        val isInvincible = state.godModeEnabled || newSurvivalTimeMs < state.devInvincibleUntilMs || hasActiveShield || isDashing
        if (!isInvincible) {
            val isWholeBodyLethal = level.wholeBodyLethalFromSubStage >= 0 && currentSubStageIndex >= level.wholeBodyLethalFromSubStage
            for (snake in allActiveSnakes) {
                // Head collision is always lethal (continuous swept check)
                if (CollisionDetector.checkSweptCircleOverlap(
                        state.player.position,
                        newPlayerPos,
                        state.player.radius,
                        snake.headPosition,
                        snake.headPosition,
                        snake.headRadius,
                    )
                ) {
                    playerDied = true
                    break
                }
                // Body segment collision: Ghost Walk phases through body segments!
                if (isWholeBodyLethal && !hasGhostWalk) {
                    for (seg in snake.segments) {
                        if (CollisionDetector.checkSweptCircleOverlap(
                                state.player.position,
                                newPlayerPos,
                                state.player.radius,
                                seg.position,
                                seg.position,
                                snake.bodyRadius,
                            )
                        ) {
                            playerDied = true
                            break
                        }
                    }
                    if (playerDied) break
                }
            }
            // Check collision with obstacles
            if (!playerDied) {
                for (obs in obstacles) {
                    when (obs) {
                        is Obstacle.Wall -> {
                            if (CollisionDetector.checkSweptCircleRectOverlap(state.player.position, newPlayerPos, state.player.radius, obs.rect)) {
                                playerDied = true
                                break
                            }
                        }
                        is Obstacle.RotatingBar -> {
                            if (CollisionDetector.checkCircleBarOverlap(
                                    newPlayerPos,
                                    state.player.radius,
                                    obs.center,
                                    obs.length,
                                    obs.currentAngleRad,
                                    obs.barThickness,
                                )
                            ) {
                                playerDied = true
                                break
                            }
                        }
                        is Obstacle.MovingBlock -> {
                            val blockRect = com.snakechase.app.domain.model.Rect(
                                obs.position.x - obs.size.x / 2f,
                                obs.position.y - obs.size.y / 2f,
                                obs.size.x,
                                obs.size.y,
                            )
                            if (CollisionDetector.checkSweptCircleRectOverlap(state.player.position, newPlayerPos, state.player.radius, blockRect)) {
                                playerDied = true
                                break
                            }
                        }
                        is Obstacle.DangerZone -> {
                            if (obs.rect.contains(newPlayerPos) || CollisionDetector.checkCircleRectOverlap(newPlayerPos, state.player.radius, obs.rect)) {
                                playerDied = true
                                break
                            }
                        }
                        is Obstacle.Shark -> {
                            if (CollisionDetector.checkSweptCircleOverlap(
                                    state.player.position,
                                    newPlayerPos,
                                    state.player.radius,
                                    obs.position,
                                    obs.position,
                                    obs.radius,
                                )
                            ) {
                                playerDied = true
                                break
                            }
                        }
                        is Obstacle.ForestProwler -> {
                            if (CollisionDetector.checkSweptCircleOverlap(
                                    state.player.position,
                                    newPlayerPos,
                                    state.player.radius,
                                    obs.position,
                                    obs.position,
                                    obs.radius,
                                )
                            ) {
                                playerDied = true
                                break
                            }
                        }
                        is Obstacle.Isopod -> {
                            if (CollisionDetector.checkSweptCircleOverlap(
                                    state.player.position,
                                    newPlayerPos,
                                    state.player.radius,
                                    obs.position,
                                    obs.position,
                                    obs.radius,
                                )
                            ) {
                                playerDied = true
                                break
                            }
                        }
                        // Vortices are not lethal: see Vortex.drift, applied to the orb and the snakes.
                        is Obstacle.Cyclone -> Unit
                        is Obstacle.Jellyfish -> {
                            if (CollisionDetector.checkSweptCircleOverlap(
                                    state.player.position,
                                    newPlayerPos,
                                    state.player.radius,
                                    obs.position,
                                    obs.position,
                                    obs.radius,
                                )
                            ) {
                                playerDied = true
                                break
                            }
                        }
                        is Obstacle.MagmaCrack -> {
                            if (obs.phase == "lethal") {
                                val dist = if (obs.arms.isNotEmpty()) {
                                    CollisionDetector.minDistToPothole(newPlayerPos, obs.center, obs.coreRx, obs.coreRy, obs.arms)
                                } else {
                                    CollisionDetector.distancePointToSegment(newPlayerPos, obs.startPos, obs.endPos)
                                }
                                if (dist <= (state.player.radius + 6f)) {
                                    playerDied = true
                                    break
                                }
                            }
                        }
                        is Obstacle.FallingEmber -> {
                            if (obs.isLethal && CollisionDetector.checkCircleOverlap(newPlayerPos, state.player.radius, obs.targetPos, obs.impactRadius)) {
                                playerDied = true
                                break
                            }
                        }
                        is Obstacle.EscapeDoor -> {
                            val doorRect = com.snakechase.app.domain.model.Rect(
                                obs.position.x - obs.width / 2f,
                                obs.position.y - obs.height / 2f,
                                obs.width,
                                obs.height,
                            )
                            if (CollisionDetector.checkCircleRectOverlap(newPlayerPos, state.player.radius, doorRect)) {
                                val finalScore = (state.score.total + ScoreBudget.completionBonus(level)).coerceAtMost(level.scoringConfig.levelScoreMax)
                                val updatedBestScores = state.bestScores.toMutableMap().apply {
                                    this[level.id] = max(this[level.id] ?: 0, finalScore)
                                }
                                val updatedUnlocked = state.unlockedLevelIds + min(5, level.id + 1)
                                return EngineTickResult(
                                    state.copy(
                                        phase = GamePhase.LEVEL_COMPLETE,
                                        score = state.score.copy(total = finalScore),
                                        unlockedLevelIds = updatedUnlocked,
                                        bestScores = updatedBestScores,
                                    ),
                                    EngineEvents(playSound = SoundId.LEVEL_COMPLETE, playHaptic = HapticStyle.HEAVY, levelCompleted = true),
                                )
                            }
                        }
                        is Obstacle.Teleporter -> {} // handled separately below
                    }
                }
            }
        }

        // 11. Teleporter Interaction
        var teleportedPos = newPlayerPos
        for (i in obstacles.indices) {
            val obs = obstacles[i]
            if (obs is Obstacle.Teleporter && obs.cooldownMs <= 0L) {
                if (CollisionDetector.checkCircleOverlap(teleportedPos, state.player.radius, obs.posA, obs.radius)) {
                    teleportedPos = obs.posB
                    obstacles = obstacles.toMutableList().apply {
                        this[i] = obs.copy(cooldownMs = 1200L)
                    }
                    soundToPlay = SoundId.TELEPORT
                    break
                } else if (CollisionDetector.checkCircleOverlap(teleportedPos, state.player.radius, obs.posB, obs.radius)) {
                    teleportedPos = obs.posA
                    obstacles = obstacles.toMutableList().apply {
                        this[i] = obs.copy(cooldownMs = 1200L)
                    }
                    soundToPlay = SoundId.TELEPORT
                    break
                }
            }
        }

        // 11b. Easy: one rescue per stage (Mario Odyssey style). The first catch in a stage throws
        // you clear with a short shield and stuns the snakes, instead of ending the run.
        var rescued = false
        if (playerDied && state.runDifficulty == com.snakechase.app.domain.model.Difficulty.EASY && state.easyRescueStage != currentSubStageIndex) {
            playerDied = false
            rescued = true
            concussedPrimary = concussedPrimary.copy(stunRemainingMs = EASY_RESCUE_STUN_MS, mode = SnakeMode.STUNNED)
            concussedSplit = concussedSplit?.copy(stunRemainingMs = EASY_RESCUE_STUN_MS, mode = SnakeMode.STUNNED)
            concussedExtras = concussedExtras.map { it.copy(stunRemainingMs = EASY_RESCUE_STUN_MS, mode = SnakeMode.STUNNED) }.toMutableList()
            newParticles.add(ParticleSystem.spawnNearMissRing(newPlayerPos, true, 0xFF4FA8FF))
            stageToast = StageToast(title = "SAVED!", subtitle = "Easy mode caught you once this stage", bonus = 0, remainingMs = 1600L)
            soundToPlay = SoundId.CONCUSSION
            hapticToPlay = HapticStyle.HEAVY
            screenShakeMs = 260L
            screenShakeInt = 7f
        }
        val rescueShield = com.snakechase.app.domain.model.ActivePowerUp(com.snakechase.app.domain.model.PowerUpType.SHIELD, EASY_RESCUE_SHIELD_MS, EASY_RESCUE_SHIELD_MS)
        val finalPowerUps = if (rescued) tickedActivePowerUps.filter { it.type != com.snakechase.app.domain.model.PowerUpType.SHIELD } + rescueShield else tickedActivePowerUps
        val finalShield = if (rescued) rescueShield else shieldPowerUp

        // 12. Handle Death
        if (playerDied) {
            val deathParticles = ParticleSystem.spawnDeathImplosion(newPlayerPos)
            val updatedBestScores = state.bestScores.toMutableMap().apply {
                this[level.id] = max(this[level.id] ?: 0, newScore.total)
            }
            val gameOverState = state.copy(
                phase = GamePhase.GAME_OVER,
                player = state.player.copy(position = newPlayerPos, deathAnimProgress = 1f),
                particles = state.particles + deathParticles,
                score = newScore,
                bestScores = updatedBestScores,
            )
            return EngineTickResult(
                gameOverState,
                EngineEvents(playSound = SoundId.DEATH, playHaptic = HapticStyle.HEAVY, playerDied = true),
            )
        }

        // 13. Construct updated GameState
        val updatedPlayer = state.player.copy(
            position = teleportedPos,
            velocity = playerVelocity,
            targetPosition = clampedTarget,
            trailPoints = newTrail,
            isDashing = isDashing,
            dashRemainingMs = newDashRemainingMs,
            dashCooldownMs = newDashCooldownMs,
            dashTrailHistory = dashTrailHistory,
            nearMissActive = activeNearMiss.isNearMiss,
            nearMissPerfect = activeNearMiss.isPerfect,
            nearMissIntensity = activeNearMiss.intensity,
        )

        val currentStageObj = level.subStages.getOrNull(currentSubStageIndex)
        val updatedHud = state.hud.copy(
            currentSubStage = currentSubStageIndex,
            subStageName = currentStageObj?.name ?: "Stage",
            stageToast = stageToast,
            survivalTimeMs = newSurvivalTimeMs,
            timeRemainingMs = newTimeRemainingMs,
        )

        val updatedState = state.copy(
            player = updatedPlayer,
            primarySnake = concussedPrimary,
            splitSnake = concussedSplit,
            extraSnakes = concussedExtras,
            obstacles = obstacles,
            particles = newParticles,
            hud = updatedHud,
            subStageDeaths = if (currentSubStageIndex != state.hud.currentSubStage) 0 else state.subStageDeaths,
            easyShieldsThisStage = easyShields,
            easyShieldNextMs = easyShieldNext,
            easyRescueStage = if (rescued) currentSubStageIndex else state.easyRescueStage,
            shieldOrb = null,
            shieldState = finalShield?.let { com.snakechase.app.domain.model.ShieldState(isActive = true, remainingMs = it.remainingMs, maxMs = it.maxMs) }
                ?: com.snakechase.app.domain.model.ShieldState(),
            shieldEverUsed = state.shieldEverUsed || (newlyCollectedType == com.snakechase.app.domain.model.PowerUpType.SHIELD),
            activePowerUpOrb = tickedOrb,
            activePowerUps = finalPowerUps,
            score = newScore,
            heartbeatAccumulatorMs = newHeartbeatAcc,
            comboMultiplier = newComboMultiplier,
            comboChargePct = newComboChargePct,
            bulletTimeRemainingMs = finalBulletTimeMs,
            screenShakeRemainingMs = screenShakeMs,
            screenShakeIntensity = screenShakeInt,
            subStageClearAnimMs = subStageClearMs,
            subStageClearIndex = subStageClearIdx,
            nearMissStreakCount = newStreakCount,
            streakWindowRemainingMs = newStreakWindowMs,
            grazeCooldownMs = newGrazeCooldown,
            isOnFire = newIsOnFire,
            ambientParticles = updatedAmbientParticles,
        )

        return EngineTickResult(
            updatedState,
            EngineEvents(playSound = soundToPlay, soundPitch = soundPitch, playHaptic = hapticToPlay),
        )
    }

    /** A vortex sweeps a snake's head along too; the body follows through the head's history. */
    private fun swirlSnake(snake: SnakeEntity, state: GameState, dtSec: Float): SnakeEntity {
        val d = Vortex.drift(snake.headPosition, state.obstacles, dtSec)
        if (d == Vec2.ZERO) return snake
        val r = snake.headRadius
        val head = Vec2(
            (snake.headPosition.x + d.x).coerceIn(r, state.arenaSize.x - r),
            (snake.headPosition.y + d.y).coerceIn(r, state.arenaSize.y - r),
        )
        return snake.copy(headPosition = head, history = if (snake.history.isEmpty()) snake.history else listOf(head) + snake.history.drop(1))
    }

    private fun isObstacleHittingSnake(obs: Obstacle, headPos: Vec2, headRadius: Float): Boolean = when (obs) {
        is Obstacle.RotatingBar -> CollisionDetector.checkCircleBarOverlap(
            headPos,
            headRadius,
            obs.center,
            obs.length,
            obs.currentAngleRad,
            obs.barThickness,
        )
        is Obstacle.MovingBlock -> {
            val blockRect = com.snakechase.app.domain.model.Rect(
                obs.position.x - obs.size.x / 2f,
                obs.position.y - obs.size.y / 2f,
                obs.size.x,
                obs.size.y,
            )
            CollisionDetector.checkCircleRectOverlap(headPos, headRadius, blockRect)
        }
        is Obstacle.ForestProwler -> (obs.position - headPos).length() <= (obs.radius + headRadius)
        is Obstacle.Isopod -> (obs.position - headPos).length() <= (obs.radius + headRadius)
        is Obstacle.Shark -> (obs.position - headPos).length() <= (obs.radius + headRadius)
        else -> false
    }
}
