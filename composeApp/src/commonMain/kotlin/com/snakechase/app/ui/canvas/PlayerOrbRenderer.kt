package com.snakechase.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.levels.CursorThemeConfig

object PlayerOrbRenderer {

    /** Skinned orbs are drawn a bit larger than the hitbox so their detail survives on phones. */
    private const val SKIN_SCALE = 1.2f

    fun drawPlayer(
        drawScope: DrawScope,
        player: PlayerOrb,
        theme: CursorThemeConfig,
        isShieldActive: Boolean = false,
        shieldRemainingMs: Long = 0L,
        comboMultiplier: Float = 1.0f,
        comboChargePct: Float = 0.0f,
        orbSkin: OrbSkin = OrbSkin.SPARK,
        timeSec: Float = 0f,
    ) {
        if (player.deathAnimProgress >= 1f) return

        with(drawScope) {
            val centerOffset = Offset(player.position.x, player.position.y)
            val ringTint = Color(theme.trailColorArgb)

            // ─── -3. Skin trail (the classic Spark has none) ──────────────────
            if (orbSkin != OrbSkin.SPARK) {
                val trail = player.trailPoints.map { Offset(it.x, it.y) }
                with(OrbPainter) { drawOrbTrail(trail, player.radius, orbSkin, timeSec) }
            }

            // ─── -2. Dash Ghost Afterimages ───────────────────────────────────
            if (player.isDashing && player.dashTrailHistory.isNotEmpty()) {
                player.dashTrailHistory.forEachIndexed { idx, ghostPos ->
                    val alpha = (0.50f - (idx * 0.12f)).coerceAtLeast(0.08f)
                    drawCircle(
                        color = Color(0xFF00F5FF).copy(alpha = alpha),
                        radius = player.radius * 0.85f,
                        center = Offset(ghostPos.x, ghostPos.y),
                    )
                }
            }

            // ─── -1. Greed Multiplier Aura ────────────────────────────────────
            if (comboMultiplier > 1.0f) {
                val greedColor = when {
                    comboMultiplier >= 3.0f -> Color(0xFFFF2255) // MAX GREED Coral Red
                    comboMultiplier >= 2.0f -> Color(0xFFFFD700) // 2x Pure Gold
                    else -> Color(0xFF00E5FF) // 1.5x Electric Cyan
                }
                val pulse = 1f + kotlin.math.sin(player.pulsePhase * 2f) * 0.14f
                val auraRadius = player.radius * (1.75f * pulse)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            // Fainter behind a skinned orb, so the skin's own colours stay readable.
                            greedColor.copy(alpha = (if (orbSkin == OrbSkin.SPARK) 0.40f else 0.18f) * comboChargePct.coerceIn(0.2f, 1f)),
                            greedColor.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = centerOffset,
                        radius = auraRadius,
                    ),
                    radius = auraRadius,
                    center = centerOffset,
                )
                drawCircle(
                    color = greedColor.copy(alpha = 0.75f),
                    radius = auraRadius * 0.88f,
                    center = centerOffset,
                    style = Stroke(width = 1.8f),
                )
            }

            // ─── -0.5. Phase Dash Cooldown Arc / Ready Pips ───────────────────
            val dashRadius = player.radius * 2.3f
            if (player.dashCooldownMs > 0L) {
                val progress = 1f - (player.dashCooldownMs / 7000f).coerceIn(0f, 1f)
                val sweep = 360f * progress
                drawArc(
                    color = Color(0x6600F5FF),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - dashRadius, centerOffset.y - dashRadius),
                    size = androidx.compose.ui.geometry.Size(dashRadius * 2f, dashRadius * 2f),
                    style = Stroke(width = 2.0f),
                )
            } else {
                // 4 Subtle Ready Pips at Cardinal Points
                val pipDist = dashRadius
                val pipColor = Color(0xAA00F5FF)
                drawCircle(pipColor, 1.8f, Offset(centerOffset.x, centerOffset.y - pipDist))
                drawCircle(pipColor, 1.8f, Offset(centerOffset.x, centerOffset.y + pipDist))
                drawCircle(pipColor, 1.8f, Offset(centerOffset.x - pipDist, centerOffset.y))
                drawCircle(pipColor, 1.8f, Offset(centerOffset.x + pipDist, centerOffset.y))
            }

            // ─── 0. Draw Active Invulnerability Shield Bubble ─────────────────
            if (isShieldActive) {
                val isExpiringSoon = shieldRemainingMs in 1L..1500L
                val shieldColor = if (isExpiringSoon && ((shieldRemainingMs / 180L) % 2L == 0L)) {
                    Color(0xFFFFC857) // Flickering amber warning in last 1.5s
                } else {
                    Color(0xFF00F5FF) // Electric cyan invulnerability aura
                }

                val shieldRadius = player.radius * 2.1f

                // Outer soft shield bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            shieldColor.copy(alpha = 0.45f),
                            shieldColor.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                        center = centerOffset,
                        radius = shieldRadius * 1.35f,
                    ),
                    radius = shieldRadius * 1.35f,
                    center = centerOffset,
                )

                // Outer protective barrier ring
                drawCircle(
                    color = shieldColor.copy(alpha = 0.90f),
                    radius = shieldRadius,
                    center = centerOffset,
                    style = Stroke(width = 3.5f),
                )

                // Secondary inner energy ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.70f),
                    radius = shieldRadius * 0.82f,
                    center = centerOffset,
                    style = Stroke(width = 1.8f),
                )
            }

            // 1. Draw Expanding Near-Miss Shockwave Rings (Golden/Cyan Concentric Explosions)
            if (player.nearMissActive) {
                val ringColor = if (player.nearMissPerfect) Color(theme.perfectRingArgb) else Color(theme.nearMissRingArgb)
                val pulseRadius = player.radius * (1.55f + player.nearMissIntensity * 1.3f)

                // Primary near-miss shockwave ring
                drawCircle(
                    color = ringColor.copy(alpha = 0.92f),
                    radius = pulseRadius,
                    center = centerOffset,
                    style = Stroke(width = if (player.nearMissPerfect) 4.5f else 3.0f),
                )
                // Secondary concentric shockwave ring
                drawCircle(
                    color = ringColor.copy(alpha = 0.52f),
                    radius = pulseRadius * 1.38f,
                    center = centerOffset,
                    style = Stroke(width = 2.0f),
                )
                // Tertiary faint shockwave ring
                if (player.nearMissPerfect) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.28f),
                        radius = pulseRadius * 1.75f,
                        center = centerOffset,
                        style = Stroke(width = 1.4f),
                    )
                }
            }

            if (orbSkin != OrbSkin.SPARK) {
                // A dark sticker disc behind the skin: it reads on bright auras and busy backgrounds alike.
                val skinR = player.radius * SKIN_SCALE
                // The skin's living effect (fireflies, bubbles, orbiting moon...) sits behind it.
                with(SkinAuras) { drawOrbAura(centerOffset, skinR, orbSkin, timeSec) }
                drawCircle(Color(0xFF0E1F17).copy(alpha = 0.55f), skinR * 1.2f, centerOffset)
                with(OrbPainter) { drawOrbCore(centerOffset, skinR, orbSkin, timeSec) }
            } else {
                // 2. Draw Breathing Outer Halo (Phaser haloSize = 52, radius = 26)
                val haloRadius = player.radius * 1.72f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ringTint.copy(alpha = 0.50f),
                            ringTint.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = centerOffset,
                        radius = haloRadius,
                    ),
                    radius = haloRadius,
                    center = centerOffset,
                )

                // 3. Draw Main Cursor Radial Plasma Orb (Phaser orbSize = 32, radius = 16)
                val orbColorStops = arrayOf(
                    0.00f to Color.White,
                    0.08f to Color(0xFFF0FAFF).copy(alpha = 0.98f),
                    0.28f to Color(0xFF78E6FF).copy(alpha = 0.90f),
                    0.52f to Color(0xFF2DB4FF).copy(alpha = 0.68f),
                    0.75f to Color(0xFF5F4BFF).copy(alpha = 0.32f),
                    0.92f to Color(0xFFC85AFF).copy(alpha = 0.14f),
                    1.00f to Color.Transparent,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = orbColorStops,
                        center = centerOffset,
                        radius = player.radius * 1.12f,
                    ),
                    radius = player.radius * 1.12f,
                    center = centerOffset,
                )

                // 4. Draw Signature Cyber Concentric Rings (Original Web Game Art)
                // Outer Ring (Radius ~12px, alpha 0.40)
                drawCircle(
                    color = Color(0xFF35D7FF).copy(alpha = 0.45f),
                    radius = 12f,
                    center = centerOffset,
                    style = Stroke(width = 1.2f),
                )
                // Inner Sharp Ring (Radius ~7.5px, alpha 0.88)
                drawCircle(
                    color = Color(0xFF35D7FF).copy(alpha = 0.90f),
                    radius = 7.5f,
                    center = centerOffset,
                    style = Stroke(width = 1.4f),
                )

                // 5. Solid Pure White Center Core Dot (Phaser coreRadius = 4.5px)
                drawCircle(
                    color = Color.White,
                    radius = 4.5f,
                    center = centerOffset,
                )
            }
        }
    }

    /**
     * Renders a glowing pulsing circular Shield Power-Up Orb on the arena floor.
     */
    fun drawShieldOrb(drawScope: DrawScope, orb: com.snakechase.app.domain.model.ShieldOrb) {
        with(drawScope) {
            val centerOffset = Offset(orb.position.x, orb.position.y)
            val pulse = 1f + kotlin.math.sin(orb.pulsePhase) * 0.12f
            val baseRadius = orb.radius * pulse

            // Outer soft glow bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00F5FF).copy(alpha = 0.40f),
                        Color(0xFF0077FF).copy(alpha = 0.15f),
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = baseRadius * 1.8f,
                ),
                radius = baseRadius * 1.8f,
                center = centerOffset,
            )

            // Outer rotating energy ring
            drawCircle(
                color = Color(0xFF00F5FF).copy(alpha = 0.85f),
                radius = baseRadius,
                center = centerOffset,
                style = Stroke(width = 2.5f),
            )

            // Inner core gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFF80FAFF),
                        Color(0xFF00C0FF),
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = baseRadius * 0.75f,
                ),
                radius = baseRadius * 0.75f,
                center = centerOffset,
            )

            // Pure white center spark
            drawCircle(
                color = Color.White,
                radius = 4.0f,
                center = centerOffset,
            )
        }
    }

    fun drawPowerUpOrb(
        drawScope: DrawScope,
        orb: com.snakechase.app.domain.model.PowerUpOrb,
    ) {
        if (orb.isCollected) return

        with(drawScope) {
            val centerOffset = Offset(orb.position.x, orb.position.y)
            val pulse = 1f + kotlin.math.sin(orb.pulsePhase) * 0.16f
            val baseRadius = orb.radius * pulse
            val color = Color(orb.type.colorArgb)

            // Outer soft ambient aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.45f),
                        color.copy(alpha = 0.15f),
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = baseRadius * 1.8f,
                ),
                radius = baseRadius * 1.8f,
                center = centerOffset,
            )

            // Outer energy ring
            drawCircle(
                color = color.copy(alpha = 0.90f),
                radius = baseRadius,
                center = centerOffset,
                style = Stroke(width = 2.5f),
            )

            // Inner core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        color,
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = baseRadius * 0.70f,
                ),
                radius = baseRadius * 0.70f,
                center = centerOffset,
            )

            // Center spark
            drawCircle(
                color = Color.White,
                radius = 4.5f,
                center = centerOffset,
            )
        }
    }
}
