package com.snakechase.app.engine

import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.SnakeEntity
import com.snakechase.app.domain.model.SnakeMode
import com.snakechase.app.domain.model.SnakeRole
import com.snakechase.app.domain.model.SnakeSegment
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.levels.SnakePersonalityConfig
import com.snakechase.app.levels.SnakeSetupConfig
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

object SnakeAI {

    private fun rotateTo(current: Float, target: Float, maxStep: Float): Float {
        var diff = target - current
        while (diff > kotlin.math.PI) diff -= (2 * kotlin.math.PI).toFloat()
        while (diff < -kotlin.math.PI) diff += (2 * kotlin.math.PI).toFloat()
        return if (kotlin.math.abs(diff) <= maxStep) {
            target
        } else {
            current + kotlin.math.sign(diff) * maxStep
        }
    }

    /**
     * Initializes a fresh SnakeEntity.
     */
    fun createInitialSnake(
        role: SnakeRole,
        spawnPos: Vec2,
        config: SnakeSetupConfig,
        customSegmentCount: Int? = null,
    ): SnakeEntity {
        val personality = if (role == SnakeRole.PRIMARY) config.personality else config.splitPersonality
        val initialHistory = List(150) { spawnPos }
        val spacing = config.segmentSpacing.toInt().coerceAtLeast(1)
        val segmentCount = customSegmentCount ?: config.initialSegments
        val segments = (1..segmentCount).map { i ->
            SnakeSegment(
                position = spawnPos - Vec2(i * config.segmentSpacing, 0f),
                angle = 0f,
            )
        }
        val initialSpeed = personality.baseSpeed * personality.warmUpSpeedFactor
        return SnakeEntity(
            role = role,
            headPosition = spawnPos,
            segments = segments,
            headAngle = 0f,
            speed = initialSpeed,
            targetSpeed = personality.baseSpeed,
            mode = if (role == SnakeRole.PRIMARY) SnakeMode.CHASE else SnakeMode.FLANK,
            alertLevel = 0f,
            headRadius = config.headRadius,
            bodyRadius = config.headRadius * 0.88f,
            history = initialHistory,
            isVisible = true,
        )
    }

    /**
     * Updates snake head position, dynamic difficulty curves, and sinuous trailing body segments.
     */
    fun updateSnake(
        snake: SnakeEntity,
        player: PlayerOrb,
        config: SnakePersonalityConfig,
        segmentSpacing: Float,
        elapsedSurvivalTimeMs: Long,
        deltaTimeSec: Float,
        subStageSpeedMultiplier: Float = 1.0f,
        arenaSize: Vec2 = Vec2(800f, 600f),
        solids: List<Solid> = emptyList(),
    ): SnakeEntity {
        val survivalSec = elapsedSurvivalTimeMs / 1000f

        // 0. Handle Concussion Stun
        val deltaMs = (deltaTimeSec * 1000f).toLong()
        val newStunMs = max(0L, snake.stunRemainingMs - deltaMs)
        if (newStunMs > 0L) {
            return snake.copy(
                stunRemainingMs = newStunMs,
                mode = SnakeMode.STUNNED,
                speed = 0f,
                alertLevel = 0.5f,
            )
        }

        // 1. Dynamic Difficulty Speed Curve (Exact formula from main.js getSpeed(t))
        val warmUpSec = (config.warmUpDurationMs / 1000f).coerceAtLeast(0.1f)
        val warmupProgress = (survivalSec / warmUpSec).coerceIn(0f, 1f)
        val warmupFactor = config.warmUpSpeedFactor + (1f - config.warmUpSpeedFactor) * warmupProgress

        val baseCalculatedSpeed = min(config.maxSpeed, config.baseSpeed + survivalSec * config.speedScaling)
        val effectiveSpeed = baseCalculatedSpeed * warmupFactor * subStageSpeedMultiplier

        // 2. Dynamic Turn Rate Curve (Exact formula from main.js getTurnRate(t))
        val effectiveTurnRate = min(config.maxTurnRate, config.baseTurnRate + survivalSec * config.turnScaling)

        // 3. Determine Predictive Target Point
        val lookaheadSec = (config.lookaheadMs / 1000f).coerceIn(0f, 1.5f)
        val predictedPlayerPos = player.position + (player.velocity * lookaheadSec)

        // 4. Apply Flanking / Intercept Offset
        val rawTarget = if (config.flankOffsetPx != 0f) {
            val toPlayer = (predictedPlayerPos - snake.headPosition).normalized()
            val perp = Vec2(-toPlayer.y, toPlayer.x)
            predictedPlayerPos + (perp * config.flankOffsetPx)
        } else {
            predictedPlayerPos
        }
        // Lookahead and flank offsets can project the aim point past a wall when the
        // player hugs an edge; aiming at an in-bounds point stops the snake overshooting.
        val wallMargin = snake.headRadius
        val finalTarget = Vec2(
            rawTarget.x.coerceIn(wallMargin, arenaSize.x - wallMargin),
            rawTarget.y.coerceIn(wallMargin, arenaSize.y - wallMargin),
        )

        // 5. Steer Head towards Target with angular interpolation
        val desiredDir = finalTarget - snake.headPosition
        val probe = min(90f, max(snake.headRadius * 2f, desiredDir.length()))
        val desiredAngle = Solid.steer(
            snake.headPosition,
            snake.headRadius,
            atan2(desiredDir.y, desiredDir.x),
            snake.headAngle,
            probe,
            solids,
        )
        val maxTurn = effectiveTurnRate * deltaTimeSec
        val newAngle = rotateTo(snake.headAngle, desiredAngle, maxTurn)

        // 6. Advance Head Position
        val moveDir = Vec2.fromAngle(newAngle, 1f)
        val freeHeadPos = snake.headPosition + (moveDir * effectiveSpeed * deltaTimeSec)
        // 6a. Walls, bars and blocks are solid: push back out and glide along the surface.
        val (unboundedHeadPos, slideAngle) = Solid.resolve(snake.headPosition, freeHeadPos, snake.headRadius, solids)

        // 6b. Arena Wall Handling — clamp + slide.
        // The head never leaves the arena; if it hits a wall, the velocity component into
        // that wall is dropped so the snake glides along it (body follows via history).
        val minX = wallMargin
        val maxX = arenaSize.x - wallMargin
        val minY = wallMargin
        val maxY = arenaSize.y - wallMargin
        val newHeadPos = Vec2(
            unboundedHeadPos.x.coerceIn(minX, maxX),
            unboundedHeadPos.y.coerceIn(minY, maxY),
        )
        val hitX = newHeadPos.x != unboundedHeadPos.x
        val hitY = newHeadPos.y != unboundedHeadPos.y
        val finalAngle = when {
            hitX && hitY -> newAngle // Corner: keep turning toward target, it frees itself.
            hitX && moveDir.y != 0f -> atan2(moveDir.y, 0f) // Slide vertically.
            hitY && moveDir.x != 0f -> atan2(0f, moveDir.x) // Slide horizontally.
            slideAngle != null -> slideAngle
            else -> newAngle
        }

        // 7. Sinuous Breadcrumb History Queue (100% Matching Phaser history.unshift)
        val currentHistory = if (snake.history.isEmpty()) {
            List(150) { newHeadPos }
        } else {
            listOf(newHeadPos) + snake.history
        }.take(150)

        val spacing = segmentSpacing.toInt().coerceAtLeast(1)
        val updatedSegments = (0 until snake.segments.size).map { i ->
            val histIdx = minOf((i + 1) * spacing, currentHistory.size - 1)
            val segPos = currentHistory[histIdx]
            val prevPos = if (histIdx > 0) currentHistory[histIdx - 1] else newHeadPos
            val segAngle = atan2(prevPos.y - segPos.y, prevPos.x - segPos.x)
            SnakeSegment(segPos, segAngle)
        }

        // 8. Dynamic Alert Level (< 80px shifts head glow)
        val distToPlayer = newHeadPos.distanceTo(player.position)
        val alert = if (distToPlayer < 80f) (80f - distToPlayer) / 80f else 0f

        return snake.copy(
            headPosition = newHeadPos,
            segments = updatedSegments,
            headAngle = finalAngle,
            speed = effectiveSpeed,
            alertLevel = alert,
            history = currentHistory,
        )
    }
}
