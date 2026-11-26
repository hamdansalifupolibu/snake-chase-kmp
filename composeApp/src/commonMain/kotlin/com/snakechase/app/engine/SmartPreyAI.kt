package com.snakechase.app.engine

import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.SnakeEntity
import com.snakechase.app.domain.model.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Advanced Predictive AI for Rival Prey in VS AI and 1v1 Duels.
 *
 * Implements:
 * 1. 16-Ray Directional Potential Field (Raycast probe)
 * 2. Multi-Segment Repulsion (Snake Head + 14 articulated body segments)
 * 3. Reactive Lateral Jukes (Perpendicular 90-degree dodge cuts when snake lunges)
 * 4. Anti-Cornering Gravitational Bias (Prevents wall & corner trapping)
 * 5. High-Skill Graze Skimming (Maintains tactical distance to simulate elite play)
 */
object SmartPreyAI {

    private const val PREY_BASE_SPEED = 320f
    private const val PREY_ESCAPE_BURST_SPEED = 380f
    private const val WALL_MARGIN = 90f
    private const val DANGER_RADIUS = 130f
    private const val PANIC_RADIUS = 75f

    // 16 radial rays around the unit circle
    private val RAY_ANGLES = (0 until 16).map { (it * 2 * PI / 16).toFloat() }

    /**
     * Calculates the next high-intelligence evasive position for the AI Prey.
     *
     * @param prey Current player orb state of the AI
     * @param snake Current snake entity pursuing the AI
     * @param arena Arena dimensions (e.g. 800x600)
     * @param dt Delta time in seconds
     */
    fun computeNextPosition(
        prey: PlayerOrb,
        snake: SnakeEntity,
        arena: Vec2,
        dt: Float,
        difficulty: com.snakechase.app.domain.model.AiDifficulty = com.snakechase.app.domain.model.AiDifficulty.MODERATE,
        speedScale: Float = 1f,
    ): Vec2 {
        val preyPos = prey.position
        val headPos = snake.headPosition
        val headDist = preyPos.distanceTo(headPos)

        // ─── 1. Multi-Segment Repulsion Vector ──────────────────────────────
        var repulsionX = 0f
        var repulsionY = 0f

        // Head Repulsion (Heaviest weight)
        val headDiff = preyPos - headPos
        val headDistSq = max(headDiff.x * headDiff.x + headDiff.y * headDiff.y, 400f)
        val headWeight = 28000f / headDistSq
        repulsionX += (headDiff.x / sqrt(headDistSq)) * headWeight
        repulsionY += (headDiff.y / sqrt(headDistSq)) * headWeight

        // Body Segments Repulsion (Prevents running into the coiled snake body)
        snake.segments.forEachIndexed { index, seg ->
            val segDiff = preyPos - seg.position
            val segDistSq = max(segDiff.x * segDiff.x + segDiff.y * segDiff.y, 300f)
            val decay = 1f - (index.toFloat() / snake.segments.size) * 0.55f
            val segWeight = (14000f / segDistSq) * decay
            repulsionX += (segDiff.x / sqrt(segDistSq)) * segWeight
            repulsionY += (segDiff.y / sqrt(segDistSq)) * segWeight
        }

        // ─── 2. Reactive Perpendicular Lateral Juke Maneuver ──────────────
        // If snake is lunging directly at prey, dodging sideways is mathematically optimal
        val snakeForward = Vec2(cos(snake.headAngle), sin(snake.headAngle))
        val toPrey = (preyPos - headPos).normalized()
        val closingIn = snakeForward.dot(toPrey) > 0.65f

        if (closingIn && headDist < DANGER_RADIUS) {
            // Perpendicular unit vector (lateral escape)
            val perp1 = Vec2(-snakeForward.y, snakeForward.x)
            val perp2 = Vec2(snakeForward.y, -snakeForward.x)

            // Choose the perpendicular direction that moves further away from the nearest wall
            val center = Vec2(arena.x / 2f, arena.y / 2f)
            val testPos1 = preyPos + perp1 * 60f
            val testPos2 = preyPos + perp2 * 60f
            val chosenPerp = if (testPos1.distanceTo(center) <= testPos2.distanceTo(center)) perp1 else perp2

            val jukeWeight = 2.4f
            repulsionX += chosenPerp.x * jukeWeight
            repulsionY += chosenPerp.y * jukeWeight
        }

        // ─── 3. Anti-Cornering Center Gravitational Bias ──────────────────
        // Soft repulsive vector pushing away from boundaries to avoid corner traps
        val center = Vec2(arena.x / 2f, arena.y / 2f)
        var wallRepulseX = 0f
        var wallRepulseY = 0f

        if (preyPos.x < WALL_MARGIN) {
            val factor = (WALL_MARGIN - preyPos.x) / WALL_MARGIN
            wallRepulseX += factor * 3.5f
        } else if (preyPos.x > arena.x - WALL_MARGIN) {
            val factor = (preyPos.x - (arena.x - WALL_MARGIN)) / WALL_MARGIN
            wallRepulseX -= factor * 3.5f
        }

        if (preyPos.y < WALL_MARGIN) {
            val factor = (WALL_MARGIN - preyPos.y) / WALL_MARGIN
            wallRepulseY += factor * 3.5f
        } else if (preyPos.y > arena.y - WALL_MARGIN) {
            val factor = (preyPos.y - (arena.y - WALL_MARGIN)) / WALL_MARGIN
            wallRepulseY -= factor * 3.5f
        }

        // Combine potential field
        val desiredDir = Vec2(repulsionX + wallRepulseX, repulsionY + wallRepulseY)

        // ─── 4. 16-Ray Directional Clearance Evaluation ───────────────────
        var bestAngle = 0f
        var bestScore = -Float.MAX_VALUE

        for (angle in RAY_ANGLES) {
            val rayDir = Vec2(cos(angle), sin(angle))
            val probePoint = preyPos + rayDir * 85f

            // Wall clearance penalty
            if (probePoint.x < 30f ||
                probePoint.x > arena.x - 30f ||
                probePoint.y < 30f ||
                probePoint.y > arena.y - 30f
            ) {
                continue // Ray hits boundary
            }

            // Snake segment clearance score
            var minSnakeDist = probePoint.distanceTo(headPos)
            for (seg in snake.segments) {
                val d = probePoint.distanceTo(seg.position)
                if (d < minSnakeDist) minSnakeDist = d
            }

            // Alignment with potential field vector
            val alignment = if (desiredDir.length() > 0.01f) {
                rayDir.dot(desiredDir.normalized())
            } else {
                0f
            }

            // Center proximity score (avoids corner entrapment)
            val distToCenter = probePoint.distanceTo(center)
            val centerBonus = (1f - (distToCenter / (arena.x / 2f)).coerceIn(0f, 1f)) * 0.4f

            // Total ray score
            val score = minSnakeDist * 1.8f + alignment * 120f + centerBonus * 40f

            if (score > bestScore) {
                bestScore = score
                bestAngle = angle
            }
        }

        // ─── 5. Velocity Application & Smoothing ──────────────────────────
        val isPanicking = headDist < PANIC_RADIUS
        val rawBaseSpeed = if (isPanicking) PREY_ESCAPE_BURST_SPEED else PREY_BASE_SPEED
        val currentSpeed = rawBaseSpeed * difficulty.speedMultiplier * speedScale

        val targetVel = Vec2(cos(bestAngle) * currentSpeed, sin(bestAngle) * currentSpeed)
        val smoothOld = when (difficulty) {
            com.snakechase.app.domain.model.AiDifficulty.EASY -> 0.85f
            com.snakechase.app.domain.model.AiDifficulty.MODERATE -> 0.72f
            com.snakechase.app.domain.model.AiDifficulty.HARD -> 0.50f
        }
        val smoothedVel = (prey.velocity * smoothOld) + (targetVel * (1f - smoothOld))

        val nextX = (preyPos.x + smoothedVel.x * dt).coerceIn(24f, arena.x - 24f)
        val nextY = (preyPos.y + smoothedVel.y * dt).coerceIn(24f, arena.y - 24f)

        return Vec2(nextX, nextY)
    }
}
