package com.snakechase.app.engine

import com.snakechase.app.domain.model.Particle
import com.snakechase.app.domain.model.ParticleType
import com.snakechase.app.domain.model.Vec2
import kotlin.random.Random

object ParticleSystem {

    private var nextParticleId = 1L

    fun spawnNearMissRing(pos: Vec2, isPerfect: Boolean, colorArgb: Long): Particle = Particle(
        id = nextParticleId++,
        type = if (isPerfect) ParticleType.PERFECT_NEAR_MISS_BURST else ParticleType.NEAR_MISS_RING,
        position = pos,
        progress = 0f,
        maxLifeMs = if (isPerfect) 450L else 320L,
        initialRadius = 14f,
        targetRadius = if (isPerfect) 58f else 42f,
        colorArgb = colorArgb,
        alpha = 1f,
    )

    fun spawnScorePopup(pos: Vec2, text: String, colorArgb: Long = 0xFF00E5FF): Particle = Particle(
        id = nextParticleId++,
        type = ParticleType.SCORE_POPUP,
        position = pos,
        velocity = Vec2(0f, -55f), // Drifts upward
        progress = 0f,
        maxLifeMs = 650L,
        initialRadius = 12f,
        targetRadius = 16f,
        colorArgb = colorArgb,
        alpha = 1f,
        text = text,
    )

    fun spawnFlameTrail(pos: Vec2): Particle {
        val angle = Random.nextFloat() * (2f * kotlin.math.PI.toFloat())
        val speed = Random.nextFloat() * 40f + 10f
        val colors = listOf(0xFFFF3D00, 0xFFFF9100, 0xFFFFEA00, 0xFFFF1744)
        return Particle(
            id = nextParticleId++,
            type = ParticleType.FLAME_TRAIL,
            position = pos + Vec2((Random.nextFloat() - 0.5f) * 16f, (Random.nextFloat() - 0.5f) * 16f),
            velocity = Vec2.fromAngle(angle, speed),
            progress = 0f,
            maxLifeMs = 380L,
            initialRadius = Random.nextFloat() * 5f + 4f,
            targetRadius = 1f,
            colorArgb = colors[Random.nextInt(colors.size)],
            alpha = 1f,
        )
    }

    fun spawnAmbientParticle(arenaSize: Vec2, levelId: Int): Particle {
        val ambientType = when (levelId) {
            2 -> com.snakechase.app.domain.model.AmbientType.FOREST_LEAF
            3 -> com.snakechase.app.domain.model.AmbientType.SEA_BUBBLE
            4 -> com.snakechase.app.domain.model.AmbientType.SNOWFLAKE
            5 -> com.snakechase.app.domain.model.AmbientType.EMBER_SPARK
            else -> com.snakechase.app.domain.model.AmbientType.NEON_MOTE
        }

        val startPos = when (ambientType) {
            com.snakechase.app.domain.model.AmbientType.SEA_BUBBLE,
            com.snakechase.app.domain.model.AmbientType.EMBER_SPARK,
            ->
                Vec2(Random.nextFloat() * arenaSize.x, arenaSize.y + 10f) // Spawns from bottom rising up
            com.snakechase.app.domain.model.AmbientType.FOREST_LEAF,
            com.snakechase.app.domain.model.AmbientType.SNOWFLAKE,
            ->
                Vec2(Random.nextFloat() * arenaSize.x, -10f) // Spawns from top falling down
            com.snakechase.app.domain.model.AmbientType.NEON_MOTE ->
                Vec2(Random.nextFloat() * arenaSize.x, Random.nextFloat() * arenaSize.y)
        }

        val vel = when (ambientType) {
            com.snakechase.app.domain.model.AmbientType.SEA_BUBBLE -> Vec2((Random.nextFloat() - 0.5f) * 15f, -Random.nextFloat() * 35f - 20f)
            com.snakechase.app.domain.model.AmbientType.EMBER_SPARK -> Vec2((Random.nextFloat() - 0.5f) * 30f, -Random.nextFloat() * 50f - 25f)
            com.snakechase.app.domain.model.AmbientType.FOREST_LEAF -> Vec2((Random.nextFloat() - 0.3f) * 25f, Random.nextFloat() * 30f + 15f)
            com.snakechase.app.domain.model.AmbientType.SNOWFLAKE -> Vec2((Random.nextFloat() - 0.5f) * 20f, Random.nextFloat() * 25f + 12f)
            com.snakechase.app.domain.model.AmbientType.NEON_MOTE -> Vec2((Random.nextFloat() - 0.5f) * 18f, (Random.nextFloat() - 0.5f) * 18f)
        }

        val color = when (ambientType) {
            com.snakechase.app.domain.model.AmbientType.NEON_MOTE -> if (Random.nextBoolean()) 0xFF00E5FF else 0xFFD500F9
            com.snakechase.app.domain.model.AmbientType.FOREST_LEAF -> if (Random.nextBoolean()) 0xFF76FF03 else 0xFFFFD600
            com.snakechase.app.domain.model.AmbientType.SEA_BUBBLE -> 0xFF80D8FF
            com.snakechase.app.domain.model.AmbientType.SNOWFLAKE -> 0xFFE0F7FA
            com.snakechase.app.domain.model.AmbientType.EMBER_SPARK -> if (Random.nextBoolean()) 0xFFFF6D00 else 0xFFFFD600
        }

        val radius = when (ambientType) {
            com.snakechase.app.domain.model.AmbientType.SEA_BUBBLE -> Random.nextFloat() * 3.5f + 2f
            com.snakechase.app.domain.model.AmbientType.FOREST_LEAF -> Random.nextFloat() * 3f + 2f
            com.snakechase.app.domain.model.AmbientType.SNOWFLAKE -> Random.nextFloat() * 2.5f + 1.5f
            com.snakechase.app.domain.model.AmbientType.EMBER_SPARK -> Random.nextFloat() * 2f + 1f
            com.snakechase.app.domain.model.AmbientType.NEON_MOTE -> Random.nextFloat() * 2f + 1f
        }

        return Particle(
            id = nextParticleId++,
            type = ParticleType.AMBIENT_MOTE,
            position = startPos,
            velocity = vel,
            progress = 0f,
            maxLifeMs = Random.nextLong(4000L, 8000L),
            initialRadius = radius,
            targetRadius = radius * 0.8f,
            colorArgb = color,
            alpha = Random.nextFloat() * 0.45f + 0.25f,
            ambientType = ambientType,
        )
    }

    fun spawnDeathImplosion(pos: Vec2): List<Particle> {
        val count = 24
        val particles = mutableListOf<Particle>()
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * (2f * kotlin.math.PI.toFloat())
            val speed = Random.nextFloat() * 180f + 60f
            val vel = Vec2.fromAngle(angle, speed)
            particles.add(
                Particle(
                    id = nextParticleId++,
                    type = ParticleType.DEATH_IMPLOSION,
                    position = pos,
                    velocity = vel,
                    progress = 0f,
                    maxLifeMs = 700L,
                    initialRadius = 6f,
                    targetRadius = 1f,
                    colorArgb = if (i % 2 == 0) 0xFFFF1744 else 0xFFFFEA00,
                    alpha = 1f,
                ),
            )
        }
        return particles
    }

    fun spawnStageSparks(center: Vec2, colorArgb: Long): List<Particle> {
        val particles = mutableListOf<Particle>()
        for (i in 0..16) {
            val angle = Random.nextFloat() * (2f * kotlin.math.PI.toFloat())
            val speed = Random.nextFloat() * 220f + 80f
            particles.add(
                Particle(
                    id = nextParticleId++,
                    type = ParticleType.STAGE_ADVANCE_SPARK,
                    position = center,
                    velocity = Vec2.fromAngle(angle, speed),
                    progress = 0f,
                    maxLifeMs = 600L,
                    initialRadius = 5f,
                    targetRadius = 0f,
                    colorArgb = colorArgb,
                    alpha = 1f,
                ),
            )
        }
        return particles
    }

    fun spawnTeleportWarp(pos: Vec2, colorArgb: Long = 0xFF00E5FF): List<Particle> {
        val particles = mutableListOf<Particle>()
        for (i in 0..12) {
            val angle = (i.toFloat() / 12f) * (2f * kotlin.math.PI.toFloat())
            val speed = Random.nextFloat() * 120f + 40f
            particles.add(
                Particle(
                    id = nextParticleId++,
                    type = ParticleType.TELEPORT_WARP,
                    position = pos,
                    velocity = Vec2.fromAngle(angle, speed),
                    progress = 0f,
                    maxLifeMs = 450L,
                    initialRadius = 8f,
                    targetRadius = 1f,
                    colorArgb = colorArgb,
                    alpha = 1f,
                ),
            )
        }
        return particles
    }

    fun updateParticles(particles: List<Particle>, deltaTimeMs: Long): List<Particle> {
        val dtSec = deltaTimeMs / 1000f
        return particles.mapNotNull { p ->
            val newProgress = p.progress + (deltaTimeMs.toFloat() / p.maxLifeMs)
            if (newProgress >= 1f) {
                null
            } else {
                val newPos = p.position + (p.velocity * dtSec)
                val newAlpha = 1f - newProgress
                p.copy(
                    position = newPos,
                    progress = newProgress,
                    alpha = newAlpha,
                )
            }
        }
    }
}
