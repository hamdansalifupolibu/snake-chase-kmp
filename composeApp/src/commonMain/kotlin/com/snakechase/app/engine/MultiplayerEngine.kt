package com.snakechase.app.engine

import com.snakechase.app.domain.model.DuelMatchResult
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.MultiplayerRole
import com.snakechase.app.domain.model.MultiplayerState
import com.snakechase.app.domain.model.OrbPerk
import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.SnakeEntity
import com.snakechase.app.domain.model.SnakePerk
import com.snakechase.app.domain.model.SnakeRole
import com.snakechase.app.domain.model.SnakeSegment
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.network.firebase.FirebaseConfig
import com.snakechase.app.util.formatSeconds
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class MultiplayerTickResult(
    val newState: MultiplayerState,
    val contactOccurred: Boolean = false,
    val roundCompleted: Boolean = false,
    val matchCompleted: Boolean = false,
)

object MultiplayerEngine {

    private const val SNAKE_SPEED = 260f
    private const val PREY_SPEED = 300f

    /** A human prey's speed against the AI: a comfortable edge on Easy, a thin one on Hard. */
    fun humanPreySpeed(d: com.snakechase.app.domain.model.AiDifficulty): Float = PREY_SPEED * when (d) {
        com.snakechase.app.domain.model.AiDifficulty.EASY -> 1.1f
        com.snakechase.app.domain.model.AiDifficulty.MODERATE -> 1.08f
        com.snakechase.app.domain.model.AiDifficulty.HARD -> 1.05f
    }

    /** The AI snake reaches full speed this long into a round. */
    const val AI_WARM_UP_MS = 1_500L

    const val HELPER_FIRST_MS = 6_000L
    const val HELPER_EVERY_MS = 12_000L
    const val HELPER_LIFETIME_MS = 8_000L
    const val HELPER_BOOST_MS = 3_000L
    const val HELPER_BOOST = 1.45f
    private const val HELPER_PICKUP = 14f + 22f
    private const val SEGMENT_COUNT = 14
    private const val SEGMENT_SPACING = 12f
    private const val CATCH_DISTANCE = 32f // head radius (16) + orb radius (14) + padding

    /** Head within this of the aim point = "arrived": stop steering so it can't orbit the cursor. */
    private const val AIM_ARRIVE_RADIUS = 24f

    fun startMatch(
        mode: DuelMode,
        player1Name: String,
        player2Name: String,
        arenaSize: Vec2 = Vec2(800f, 600f),
        roomCode: String = "",
        difficulty: com.snakechase.app.domain.model.AiDifficulty = com.snakechase.app.domain.model.AiDifficulty.MODERATE,
    ): MultiplayerState {
        val userIsSnake = mode == DuelMode.VS_AI_RIVAL
        val (initialSnake, initialPrey) = roundStart(arenaSize)

        return MultiplayerState(
            mode = mode,
            aiDifficulty = difficulty,
            phase = DuelPhase.COUNTDOWN,
            round = 1,
            maxRounds = 3,
            localRole = if (userIsSnake) MultiplayerRole.SNAKE else MultiplayerRole.PREY,
            player1Name = player1Name,
            player2Name = player2Name,
            round1TimeMs = 0L,
            round2TimeMs = 0L,
            round3TimeMs = 0L,
            survivalTimeMs = 0L,
            helperOrb = null,
            preyBoostMs = 0L,
            countdownCount = 3,
            roundAnnouncement = if (userIsSnake) "ROUND 1 — HUNT THE FOOD!" else "ROUND 1 — FIGHT!",
            roomCode = roomCode,
            snake = initialSnake,
            prey = initialPrey,
            obstacles = emptyList(),
            arenaSize = arenaSize,
        )
    }

    fun startRound2(state: MultiplayerState): MultiplayerState {
        val arenaSize = state.arenaSize
        val isVsAi = state.mode == DuelMode.VS_AI_RIVAL
        val nextRole = if (isVsAi) MultiplayerRole.PREY else MultiplayerRole.SNAKE
        val (initialSnake, initialPrey) = roundStart(arenaSize)

        return state.copy(
            phase = DuelPhase.COUNTDOWN,
            round = 2,
            localRole = nextRole,
            survivalTimeMs = 0L,
            helperOrb = null,
            preyBoostMs = 0L,
            countdownCount = 3,
            roundAnnouncement = if (isVsAi) "ROUND 2 — FLEE & SURVIVE!" else "ROUND 2 — ROLE REVERSAL!",
            snake = initialSnake,
            prey = initialPrey,
        )
    }

    fun startRound3(state: MultiplayerState): MultiplayerState {
        val arenaSize = state.arenaSize
        val isVsAi = state.mode == DuelMode.VS_AI_RIVAL
        val nextRole = if (isVsAi) MultiplayerRole.SNAKE else MultiplayerRole.PREY
        val (initialSnake, initialPrey) = roundStart(arenaSize)

        return state.copy(
            phase = DuelPhase.COUNTDOWN,
            round = 3,
            localRole = nextRole,
            survivalTimeMs = 0L,
            helperOrb = null,
            preyBoostMs = 0L,
            countdownCount = 3,
            roundAnnouncement = "FINAL ROUND — SUDDEN DEATH!",
            snake = initialSnake,
            prey = initialPrey,
        )
    }

    /**
     * Where every round begins: snake in the bottom-left facing up, prey in the top-right corner.
     * They start ~575 units apart and the snake isn't pointed at the prey, so it has to turn and
     * hunt; it can't win a round by simply running straight ahead for a second.
     */
    private fun roundStart(arena: Vec2): Pair<SnakeEntity, PlayerOrb> = createDuelSnake(Vec2(arena.x * 0.2f, arena.y * 0.78f), initialAngle = -1.5708f) to
        PlayerOrb(position = Vec2(arena.x * 0.8f, arena.y * 0.25f), radius = 14f)

    private fun createDuelSnake(spawnPos: Vec2, initialAngle: Float = 1.5708f): SnakeEntity {
        val segments = (0 until SEGMENT_COUNT).map { i ->
            val offsetDist = (i + 1) * SEGMENT_SPACING
            SnakeSegment(
                position = Vec2(
                    spawnPos.x - cos(initialAngle) * offsetDist,
                    spawnPos.y - sin(initialAngle) * offsetDist,
                ),
                angle = initialAngle,
            )
        }
        return SnakeEntity(
            role = SnakeRole.PRIMARY,
            headPosition = spawnPos,
            segments = segments,
            headAngle = initialAngle,
            speed = SNAKE_SPEED,
            targetSpeed = SNAKE_SPEED,
            headRadius = 16f,
            bodyRadius = 13f,
            history = listOf(spawnPos),
        )
    }

    fun tick(
        state: MultiplayerState,
        localInputPos: Vec2?,
        rivalInputPos: Vec2?,
        deltaMs: Long,
    ): MultiplayerTickResult {
        if (state.phase != DuelPhase.ROUND_PLAYING) {
            return MultiplayerTickResult(state)
        }

        val dt = deltaMs / 1000f
        val newSurvivalMs = state.survivalTimeMs + deltaMs
        val currentSnake = state.snake ?: return MultiplayerTickResult(state)
        val currentPrey = state.prey
        val arena = state.arenaSize

        // Who steers what: the local player drives their role; the *other* role is a human too in
        // pass & play and in every online match (the rival's pointer arrives as rivalInputPos).
        // Only VS AI hands the other role to the AI.
        val rivalIsHuman = state.mode == DuelMode.LOCAL_PASS_AND_PLAY || state.isNetworkMatch

        // 1. Move Prey
        val preyTarget = when {
            state.localRole == MultiplayerRole.PREY -> {
                localInputPos ?: currentPrey.position
            }
            rivalIsHuman -> {
                rivalInputPos ?: currentPrey.position
            }
            else -> {
                // Advanced 16-Ray Potential Field AI Prey with reactive juke maneuvers
                SmartPreyAI.computeNextPosition(currentPrey, currentSnake, arena, dt, state.aiDifficulty, state.preySpeedScale)
            }
        }

        val newPreyPos = if (state.mode == DuelMode.VS_AI_RIVAL && state.localRole == MultiplayerRole.SNAKE) {
            preyTarget
        } else {
            val preyDiff = preyTarget - currentPrey.position
            val preyDist = preyDiff.length()
            val humanPreyVsAi = state.mode == DuelMode.VS_AI_RIVAL && state.localRole == MultiplayerRole.PREY
            val preySpeed = if (humanPreyVsAi) {
                humanPreySpeed(state.aiDifficulty) * (if (state.preyBoostMs > 0L) HELPER_BOOST else 1f) * perks(state).second.speedMul
            } else {
                PREY_SPEED
            }
            val preyMoveDist = min(preyDist, preySpeed * dt)
            if (preyDist > 1f) {
                val dir = preyDiff.normalized()
                Vec2(
                    x = (currentPrey.position.x + dir.x * preyMoveDist).coerceIn(24f, arena.x - 24f),
                    y = (currentPrey.position.y + dir.y * preyMoveDist).coerceIn(24f, arena.y - 24f),
                )
            } else {
                currentPrey.position
            }
        }

        // 2. Move Snake with Responsive Player Steering or Predictive Trajectory Intercept
        val currentAngle = currentSnake.headAngle
        var newAngle = currentAngle
        var aimReached = state.snakeAimReached
        // How far the head moves this frame: AI snakes always run; a human snake goes to the pointer.
        var stepLen = SNAKE_SPEED * dt

        /**
         * A human snake *is* the pointer: the head heads straight for it (turning smoothly, capped at
         * snake speed) and stops once it's there. No aim marker, no circling a parked cursor.
         */
        fun steerHuman(aim: Vec2?) {
            if (aim == null) {
                stepLen = 0f // no input yet: wait at the spawn point
                return
            }
            aimReached = null
            val snakeDiff = aim - currentSnake.headPosition
            val dist = snakeDiff.length()
            if (dist <= AIM_ARRIVE_RADIUS * 0.25f) {
                stepLen = 0f
                aimReached = aim
                return
            }
            val targetAngle = atan2(snakeDiff.y, snakeDiff.x)
            val angleDiff = normalizeAngle(targetAngle - currentAngle)
            val mine = state.localRole == MultiplayerRole.SNAKE
            val snakePerk = if (mine) perks(state).first else SnakePerk.NEUTRAL
            val turnRate = state.humanTurnRate * snakePerk.agilityMul * dt // near-instant in duels; slower ("heavier") in Snake Hunt
            newAngle = currentAngle + angleDiff.coerceIn(-turnRate, turnRate)
            // Slow down while still turning hard, so the body bends instead of sliding sideways.
            val facing = kotlin.math.cos(normalizeAngle(targetAngle - newAngle)).coerceAtLeast(0.25f)
            stepLen = min(dist, SNAKE_SPEED * snakePerk.speedMul * dt) * facing
        }

        if (state.localRole == MultiplayerRole.SNAKE) {
            steerHuman(localInputPos)
        } else if (rivalIsHuman) {
            // Pass & play's second finger, or the online rival's pointer.
            steerHuman(rivalInputPos)
        } else {
            // Predictive AI Snake (when AI is snake)
            val preyVel = (newPreyPos - currentPrey.position) * (1f / max(dt, 0.001f))
            val ai = aiSnakeTuning(state.aiDifficulty)
            // Eases in over the first moments of a round, so a swap to prey never starts with a lunge.
            val warmUp = (0.55f + 0.45f * (state.survivalTimeMs / AI_WARM_UP_MS.toFloat())).coerceAtMost(1f)
            stepLen = SNAKE_SPEED * ai.speed * warmUp * dt
            val lookaheadSec = ai.lookaheadSec
            val predictedPrey = newPreyPos + preyVel * lookaheadSec
            val snakeTarget = Vec2(
                x = predictedPrey.x.coerceIn(20f, arena.x - 20f),
                y = predictedPrey.y.coerceIn(20f, arena.y - 20f),
            )
            val snakeDiff = snakeTarget - currentSnake.headPosition
            val targetAngle = atan2(snakeDiff.y, snakeDiff.x)
            val angleDiff = normalizeAngle(targetAngle - currentAngle)
            val turnRate = ai.turnRate * dt
            newAngle = currentAngle + angleDiff.coerceIn(-turnRate, turnRate)
        }

        // Constant forward running movement
        val stepX = cos(newAngle) * stepLen
        val stepY = sin(newAngle) * stepLen
        val rawX = currentSnake.headPosition.x + stepX
        val rawY = currentSnake.headPosition.y + stepY

        // Arena boundary collision with smooth wall sliding
        val clampedX = rawX.coerceIn(20f, arena.x - 20f)
        val clampedY = rawY.coerceIn(20f, arena.y - 20f)

        if (rawX != clampedX || rawY != clampedY) {
            val adjustedDirX = if (rawX != clampedX) 0f else cos(newAngle)
            val adjustedDirY = if (rawY != clampedY) 0f else sin(newAngle)
            newAngle = if (adjustedDirX != 0f || adjustedDirY != 0f) {
                atan2(adjustedDirY, adjustedDirX)
            } else {
                // Wedged in a corner (both walls block): turn back toward the middle instead of freezing.
                atan2(arena.y / 2f - currentSnake.headPosition.y, arena.x / 2f - currentSnake.headPosition.x)
            }
        }

        val newHeadPos = Vec2(clampedX, clampedY)

        // Sinuous Segment Follow
        // A parked snake keeps its shape: only real movement adds to the trail the body follows.
        val moved = currentSnake.history.isEmpty() || newHeadPos.distanceTo(currentSnake.history.first()) > 0.5f
        val newHistory = if (moved) (listOf(newHeadPos) + currentSnake.history).take(150) else currentSnake.history
        val newSegments = currentSnake.segments.mapIndexed { index, _ ->
            val historyIndex = min((index + 1) * 3, newHistory.size - 1)
            val pos = newHistory[historyIndex]
            val prevPos = if (historyIndex > 0) newHistory[historyIndex - 1] else newHeadPos
            val segAngle = atan2(prevPos.y - pos.y, prevPos.x - pos.x)
            SnakeSegment(position = pos, angle = segAngle)
        }

        val updatedSnake = currentSnake.copy(
            headPosition = newHeadPos,
            headAngle = newAngle,
            segments = newSegments,
            history = newHistory,
        )

        // Keep the prey's real velocity: the AI prey blends its next move with it (momentum).
        // Without it the AI read a permanent zero and crawled at a fraction of its speed.
        val updatedPrey = currentPrey.copy(
            position = newPreyPos,
            velocity = (newPreyPos - currentPrey.position) * (1f / max(dt, 0.001f)),
        )

        @Suppress("NAME_SHADOWING")
        val state = state.copy(snakeAimReached = aimReached)

        // 3. Collision Check: Snake catches Prey!
        val distance = newHeadPos.distanceTo(newPreyPos)
        val reach = if (state.localRole == MultiplayerRole.SNAKE) perks(state).first.reachMul else 1f
        val contact = distance <= CATCH_DISTANCE * reach
        val timeExpired = newSurvivalMs >= state.maxRoundTimeMs

        if (contact || timeExpired) {
            val recordedTime = if (contact) newSurvivalMs else state.maxRoundTimeMs

            when (state.round) {
                1 -> {
                    val isVsAi = state.mode == DuelMode.VS_AI_RIVAL
                    val p1WinsRound = if (isVsAi) recordedTime < 15000L else recordedTime >= (state.maxRoundTimeMs / 2)
                    val p1Score = if (p1WinsRound) 1 else 0
                    val p2Score = if (!p1WinsRound) 1 else 0

                    val nextState = state.copy(
                        phase = DuelPhase.ROUND_TRANSITION,
                        round1TimeMs = recordedTime,
                        survivalTimeMs = recordedTime,
                        player1RoundsWon = p1Score,
                        player2RoundsWon = p2Score,
                        snake = updatedSnake,
                        prey = updatedPrey,
                    )
                    return MultiplayerTickResult(nextState, contactOccurred = contact, roundCompleted = true)
                }
                2 -> {
                    // Round 2 Complete
                    val r1Time = state.round1TimeMs
                    val r2Time = recordedTime
                    val isVsAi = state.mode == DuelMode.VS_AI_RIVAL
                    val player1Won = if (isVsAi) r2Time > r1Time else r1Time > r2Time

                    // Check if tied or match is decided
                    if (r1Time == r2Time) {
                        // Transition to Round 3 Sudden Death
                        val nextState = state.copy(
                            phase = DuelPhase.ROUND_TRANSITION,
                            round2TimeMs = recordedTime,
                            survivalTimeMs = recordedTime,
                            player1RoundsWon = 1,
                            player2RoundsWon = 1,
                            snake = updatedSnake,
                            prey = updatedPrey,
                        )
                        return MultiplayerTickResult(nextState, contactOccurred = contact, roundCompleted = true)
                    } else {
                        // Best-of-2 decided by survival time
                        val winner = if (player1Won) state.player1Name else state.player2Name
                        val mvp = if (player1Won) "Phantom Dodger" else "Apex Predator"

                        val share = "🏆 SNAKE CHASE DUEL VICTORY! 🏆\nWinner: $winner\nRound 1: ${formatSeconds(r1Time)}s | Round 2: ${formatSeconds(r2Time)}s\nPlay with me: ${FirebaseConfig.WEB_URL}"

                        val result = DuelMatchResult(
                            winnerName = winner,
                            isLocalWinner = player1Won,
                            round1TimeMs = r1Time,
                            round2TimeMs = r2Time,
                            player1Name = state.player1Name,
                            player2Name = state.player2Name,
                            player1RoundsWon = if (player1Won) 2 else 0,
                            player2RoundsWon = if (!player1Won) 2 else 0,
                            mvpTitle = mvp,
                            shareText = share,
                        )

                        val nextState = state.copy(
                            phase = DuelPhase.MATCH_OVER,
                            round2TimeMs = recordedTime,
                            survivalTimeMs = recordedTime,
                            player1RoundsWon = if (player1Won) 2 else 0,
                            player2RoundsWon = if (!player1Won) 2 else 0,
                            result = result,
                            snake = updatedSnake,
                            prey = updatedPrey,
                        )
                        return MultiplayerTickResult(nextState, contactOccurred = contact, matchCompleted = true)
                    }
                }
                else -> {
                    // Round 3 Complete (Sudden Death Decider)
                    val r1Time = state.round1TimeMs
                    val r2Time = state.round2TimeMs
                    val r3Time = recordedTime

                    val player1Won = (r1Time + r3Time) >= (r2Time + (state.maxRoundTimeMs / 2))
                    val winner = if (player1Won) state.player1Name else state.player2Name
                    val mvp = "Mortal Champion ⚡"

                    val share = "🏆 SNAKE CHASE 3-ROUND CHAMPION! 🏆\nWinner: $winner\nR1: ${formatSeconds(r1Time)}s | R2: ${formatSeconds(r2Time)}s | R3: ${formatSeconds(r3Time)}s\nPlay with me: ${FirebaseConfig.WEB_URL}"

                    val result = DuelMatchResult(
                        winnerName = winner,
                        isLocalWinner = player1Won,
                        round1TimeMs = r1Time,
                        round2TimeMs = r2Time,
                        round3TimeMs = r3Time,
                        player1Name = state.player1Name,
                        player2Name = state.player2Name,
                        player1RoundsWon = if (player1Won) 2 else 1,
                        player2RoundsWon = if (!player1Won) 2 else 1,
                        mvpTitle = mvp,
                        shareText = share,
                    )

                    val nextState = state.copy(
                        phase = DuelPhase.MATCH_OVER,
                        round3TimeMs = recordedTime,
                        survivalTimeMs = recordedTime,
                        player1RoundsWon = if (player1Won) 2 else 1,
                        player2RoundsWon = if (!player1Won) 2 else 1,
                        result = result,
                        snake = updatedSnake,
                        prey = updatedPrey,
                    )
                    return MultiplayerTickResult(nextState, contactOccurred = contact, matchCompleted = true)
                }
            }
        }

        // Helper speed orb (VS AI, human prey only): appears on a timer in an open spot, far from
        // the snake; touching it gives a short speed boost.
        var helper = state.helperOrb
        var helperAt = state.helperSpawnedAtMs
        var boost = (state.preyBoostMs - deltaMs).coerceAtLeast(0L)
        if (state.mode == DuelMode.VS_AI_RIVAL && state.localRole == MultiplayerRole.PREY) {
            if (helper != null && updatedPrey.position.distanceTo(helper) < HELPER_PICKUP) {
                helper = null
                boost = HELPER_BOOST_MS
            } else if (helper != null && newSurvivalMs - helperAt > HELPER_LIFETIME_MS) {
                helper = null
            }
            val due = newSurvivalMs >= HELPER_FIRST_MS && (newSurvivalMs - HELPER_FIRST_MS) % HELPER_EVERY_MS < deltaMs
            if (helper == null && boost == 0L && due) {
                helper = helperSpot(arena, updatedSnake.headPosition, updatedPrey.position)
                helperAt = newSurvivalMs
            }
        }

        val nextState = state.copy(
            survivalTimeMs = newSurvivalMs,
            snake = updatedSnake,
            prey = updatedPrey,
            helperOrb = helper,
            helperSpawnedAtMs = helperAt,
            preyBoostMs = boost,
        )
        return MultiplayerTickResult(nextState)
    }

    /** The open spot farthest from the snake that the prey still has to travel a little to reach. */
    internal fun helperSpot(arena: Vec2, snakeHead: Vec2, prey: Vec2): Vec2 {
        val spots = listOf(0.2f, 0.5f, 0.8f).flatMap { fx -> listOf(0.25f, 0.5f, 0.75f).map { fy -> Vec2(arena.x * fx, arena.y * fy) } }
        return spots.filter { it.distanceTo(prey) > 110f }.maxByOrNull { it.distanceTo(snakeHead) } ?: spots.maxBy { it.distanceTo(snakeHead) }
    }

    /** How the AI hunts when it is the snake: Easy is slow, lazy and only chases where you are. */
    internal data class AiSnakeTuning(val speed: Float, val turnRate: Float, val lookaheadSec: Float)

    internal fun aiSnakeTuning(d: com.snakechase.app.domain.model.AiDifficulty) = when (d) {
        com.snakechase.app.domain.model.AiDifficulty.EASY -> AiSnakeTuning(speed = 0.6f, turnRate = 2.6f, lookaheadSec = 0f)
        com.snakechase.app.domain.model.AiDifficulty.MODERATE -> AiSnakeTuning(speed = 0.8f, turnRate = 3.6f, lookaheadSec = 0.12f)
        com.snakechase.app.domain.model.AiDifficulty.HARD -> AiSnakeTuning(speed = 1.02f, turnRate = 5.2f, lookaheadSec = 0.3f)
    }

    /** Skin perks apply only against the AI and in Snake Hunt; online and pass & play are cosmetic. */
    private fun perks(state: MultiplayerState): Pair<SnakePerk, OrbPerk> = if (state.mode == DuelMode.VS_AI_RIVAL && !state.isNetworkMatch) {
        state.localSnakePerk to state.localOrbPerk
    } else {
        SnakePerk.NEUTRAL to OrbPerk.NEUTRAL
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a > kotlin.math.PI) a -= (2 * kotlin.math.PI).toFloat()
        while (a < -kotlin.math.PI) a += (2 * kotlin.math.PI).toFloat()
        return a
    }
}
