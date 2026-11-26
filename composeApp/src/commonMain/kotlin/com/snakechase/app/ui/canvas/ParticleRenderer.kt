package com.snakechase.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.snakechase.app.domain.model.Particle
import com.snakechase.app.domain.model.ParticleType

object ParticleRenderer {

    fun drawParticles(drawScope: DrawScope, particles: List<Particle>) {
        with(drawScope) {
            for (p in particles) {
                val color = Color(p.colorArgb).copy(alpha = p.alpha)
                val currentRadius = p.initialRadius + (p.targetRadius - p.initialRadius) * p.progress
                val center = Offset(p.position.x, p.position.y)

                when (p.type) {
                    ParticleType.NEAR_MISS_RING -> {
                        // Outer soft shockwave halo
                        drawCircle(
                            color = color.copy(alpha = p.alpha * 0.35f),
                            radius = currentRadius + 3f,
                            center = center,
                            style = Stroke(width = 4f),
                        )
                        // Crisp inner shockwave
                        drawCircle(
                            color = color,
                            radius = currentRadius,
                            center = center,
                            style = Stroke(width = 2f),
                        )
                    }
                    ParticleType.PERFECT_NEAR_MISS_BURST -> {
                        // Dual glowing shockwave with gold aura
                        drawCircle(
                            color = color.copy(alpha = p.alpha * 0.45f),
                            radius = currentRadius + 5f,
                            center = center,
                            style = Stroke(width = 6f),
                        )
                        drawCircle(
                            color = color,
                            radius = currentRadius,
                            center = center,
                            style = Stroke(width = 3.5f),
                        )
                        // Inner white flash
                        drawCircle(
                            color = Color.White.copy(alpha = p.alpha * 0.8f),
                            radius = (currentRadius - 4f).coerceAtLeast(1f),
                            center = center,
                            style = Stroke(width = 1.5f),
                        )
                    }
                    ParticleType.DEATH_IMPLOSION,
                    ParticleType.STAGE_ADVANCE_SPARK,
                    ParticleType.TELEPORT_WARP,
                    ParticleType.TRAIL_DUST,
                    ParticleType.CONFETTI,
                    -> {
                        // Particle soft aura
                        drawCircle(
                            color = color.copy(alpha = p.alpha * 0.40f),
                            radius = currentRadius * 1.8f,
                            center = center,
                        )
                        // Solid core
                        drawCircle(
                            color = color,
                            radius = currentRadius,
                            center = center,
                        )
                    }
                    ParticleType.SCORE_POPUP -> {
                        // Floating score burst indicator with glowing core
                        val glowRadius = currentRadius * 1.5f
                        drawCircle(
                            color = color.copy(alpha = p.alpha * 0.35f),
                            radius = glowRadius,
                            center = center,
                        )
                        drawCircle(
                            color = color.copy(alpha = p.alpha * 0.9f),
                            radius = currentRadius * 0.75f,
                            center = center,
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = p.alpha),
                            radius = currentRadius * 0.4f,
                            center = center,
                        )
                    }
                    ParticleType.FLAME_TRAIL -> {
                        // Sizzling flame motes around the player orb during ON FIRE streak
                        drawCircle(
                            color = color.copy(alpha = p.alpha * 0.45f),
                            radius = currentRadius * 1.6f,
                            center = center,
                        )
                        drawCircle(
                            color = color.copy(alpha = p.alpha),
                            radius = currentRadius,
                            center = center,
                        )
                    }
                    ParticleType.AMBIENT_MOTE -> {
                        // Soft ethereal ambient atmosphere motes
                        drawCircle(
                            color = color.copy(alpha = p.alpha * 0.30f),
                            radius = currentRadius * 2.2f,
                            center = center,
                        )
                        drawCircle(
                            color = color.copy(alpha = p.alpha * 0.85f),
                            radius = currentRadius,
                            center = center,
                        )
                    }
                }
            }
        }
    }
}
