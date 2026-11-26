package com.snakechase.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TeleporterPortal { A, B }

@Serializable
enum class ObstacleTheme {
    CYAN,
    MAGENTA,
    AMBER,
    EMERALD,
    VIOLET,
    ICE_BLUE,
    MAGMA_FIRE,
    DEEP_OCEAN,
}

sealed class Obstacle {
    abstract val id: String
    abstract val isActive: Boolean

    data class Wall(
        override val id: String,
        val rect: Rect,
        val theme: ObstacleTheme = ObstacleTheme.CYAN,
        val scanlinePhase: Float = 0f,
        override val isActive: Boolean = true,
    ) : Obstacle()

    data class RotatingBar(
        override val id: String,
        val center: Vec2,
        val length: Float,
        val currentAngleRad: Float,
        val angularVelocityRad: Float,
        val barThickness: Float = 14f,
        val tipRadius: Float = 10f,
        val theme: ObstacleTheme = ObstacleTheme.VIOLET,
        override val isActive: Boolean = true,
    ) : Obstacle()

    data class MovingBlock(
        override val id: String,
        val position: Vec2,
        val size: Vec2,
        val patrolA: Vec2,
        val patrolB: Vec2,
        val velocity: Vec2,
        val theme: ObstacleTheme = ObstacleTheme.AMBER,
        override val isActive: Boolean = true,
    ) : Obstacle()

    data class DangerZone(
        override val id: String,
        val rect: Rect,
        val pulsePhase: Float = 0f,
        val theme: ObstacleTheme = ObstacleTheme.AMBER,
        override val isActive: Boolean = true,
    ) : Obstacle()

    data class Teleporter(
        override val id: String,
        val posA: Vec2,
        val posB: Vec2,
        val radius: Float = 32f,
        val rotationPhaseA: Float = 0f,
        val rotationPhaseB: Float = 0f,
        val cooldownMs: Long = 0L,
        val theme: ObstacleTheme = ObstacleTheme.CYAN,
        override val isActive: Boolean = true,
    ) : Obstacle()

    // ─── Level 3: Aquatic World Entities (Sharks, Cyclones, Jellyfish) ─────────

    data class Shark(
        override val id: String,
        val position: Vec2,
        val velocity: Vec2,
        val radius: Float = 22f,
        val angleRad: Float = 0f,
        val tailPhase: Float = 0f,
        val patrolPoints: List<Vec2> = emptyList(),
        val currentPatrolIdx: Int = 0,
        val speed: Float = 136f,
        override val isActive: Boolean = true,
    ) : Obstacle()

    data class Cyclone(
        override val id: String,
        val position: Vec2,
        val visualRadius: Float = 111f,
        val hitRadius: Float = 97f,
        val spinAngle: Float = 0f,
        val spinSpeed: Float = 1.05f,
        val spinDir: Float = 1f,
        val patrolPoints: List<Vec2> = emptyList(),
        val currentPatrolIdx: Int = 0,
        val speed: Float = 18f,
        override val isActive: Boolean = true,
    ) : Obstacle() {
        val radius: Float get() = visualRadius
    }

    data class Jellyfish(
        override val id: String,
        val position: Vec2,
        val radius: Float = 12f,
        val pulsePhase: Float = 0f,
        val patrolPoints: List<Vec2> = emptyList(),
        val currentPatrolIdx: Int = 0,
        val speed: Float = 40f,
        override val isActive: Boolean = true,
    ) : Obstacle()

    // ─── Level 2: Forest Wildlife (Shadow Panther / Forest Prowler) ─────────

    data class ForestProwler(
        override val id: String,
        val position: Vec2,
        val radius: Float = 33f,
        val angleRad: Float = 0f,
        val stridePhase: Float = 0f,
        val patrolPoints: List<Vec2> = emptyList(),
        val currentPatrolIdx: Int = 0,
        val speed: Float = 34f,
        override val isActive: Boolean = true,
    ) : Obstacle()

    // ─── Level 4: Antarctic Isopods ──────────────────────────────────────────

    data class Isopod(
        override val id: String,
        val position: Vec2,
        val radius: Float = 33f,
        val angleRad: Float = 0f,
        val legPhase: Float = 0f,
        val patrolPoints: List<Vec2> = emptyList(),
        val currentPatrolIdx: Int = 0,
        val speed: Float = 28f,
        override val isActive: Boolean = true,
    ) : Obstacle()

    // ─── Level 5: Crucible Core Entities (Magma Cracks, Embers, Escape Door) ──

    data class MagmaCrack(
        override val id: String,
        val center: Vec2,
        val arms: List<List<Vec2>> = emptyList(),
        val coreRx: Float = 24f,
        val coreRy: Float = 18f,
        val coreR: Float = 20f,
        val seed: Long = 0L,
        val ageMs: Long = 0L,
        val lethalFromMs: Long = 1800L,
        val lethalUntilMs: Long = 4400L,
        val stopMs: Long = 5160L,
        val phase: String = "warn", // "warn", "charge", "lethal", "fade"
        val startPos: Vec2 = center,
        val endPos: Vec2 = center,
        val width: Float = 16f,
        val pulsePhase: Float = 0f,
        override val isActive: Boolean = true,
    ) : Obstacle()

    data class FallingEmber(
        override val id: String,
        val targetPos: Vec2,
        val warnRadius: Float = 34f,
        val impactRadius: Float = 46f,
        val ageMs: Long = 0L,
        val lethalAtMs: Long = 860L,
        val lethalUntilMs: Long = 3140L,
        val stopMs: Long = 3660L,
        val timerMs: Long = 0L,
        val isLethal: Boolean = false,
        override val isActive: Boolean = true,
    ) : Obstacle()

    data class EscapeDoor(
        override val id: String,
        val position: Vec2 = Vec2(788f, 54f),
        val width: Float = 70f,
        val height: Float = 70f,
        val glowPhase: Float = 0f,
        override val isActive: Boolean = true,
    ) : Obstacle()
}
