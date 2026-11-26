package com.snakechase.app.domain.model

enum class ParticleType {
    NEAR_MISS_RING,
    PERFECT_NEAR_MISS_BURST,
    DEATH_IMPLOSION,
    STAGE_ADVANCE_SPARK,
    TELEPORT_WARP,
    TRAIL_DUST,
    CONFETTI,
    SCORE_POPUP,
    FLAME_TRAIL,
    AMBIENT_MOTE,
}

enum class AmbientType {
    NEON_MOTE,
    FOREST_LEAF,
    SEA_BUBBLE,
    SNOWFLAKE,
    EMBER_SPARK,
}

data class Particle(
    val id: Long,
    val type: ParticleType,
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val progress: Float = 0f, // 0..1, 1 = dead
    val maxLifeMs: Long = 600L,
    val initialRadius: Float = 10f,
    val targetRadius: Float = 40f,
    val colorArgb: Long = 0xFF00FFFF,
    val alpha: Float = 1f,
    val text: String? = null,
    val ambientType: AmbientType? = null,
)
