package com.snakechase.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.snakechase.app.domain.model.SnakeEntity
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.levels.SnakeThemeConfig
import kotlin.math.cos
import kotlin.math.sin

object SnakeRenderer {

    /**
     * [skin] null or DEFAULT keeps the classic plasma look; any other skin is drawn as a
     * patterned sticker snake by [SkinPainter].
     */
    fun drawSnake(drawScope: DrawScope, snake: SnakeEntity, theme: SnakeThemeConfig, skin: SnakeSkin? = null, timeSec: Float = 0f) {
        if (!snake.isVisible) return
        if (skin != null && skin != SnakeSkin.DEFAULT) {
            val points = listOf(Offset(snake.headPosition.x, snake.headPosition.y)) + snake.segments.map { Offset(it.position.x, it.position.y) }
            val radii = listOf(snake.headRadius) + snake.segments.indices.map { maxOf(snake.headRadius - (it + 1) * 0.9f, 4.5f) * 0.92f }
            with(SkinPainter) {
                drawScope.drawSkinnedSnake(
                    points = points,
                    radii = radii,
                    headAngle = snake.headAngle,
                    skin = skin,
                    timeSec = timeSec,
                    alert = snake.alertLevel > 0.3f,
                    stunned = snake.isStunned,
                    stunSpin = snake.stunRemainingMs / 80f,
                )
            }
            return
        }

        with(drawScope) {
            val allPositions = listOf(snake.headPosition) + snake.segments.map { it.position }

            // 1. Draw Luminous Energy Spine Connecting Head & Segments (Phaser linkGfx)
            if (allPositions.size >= 2) {
                val spineColor = Color(theme.energyLinkColorArgb)
                for (i in 0 until allPositions.size - 1) {
                    val p1 = allPositions[i]
                    val p2 = allPositions[i + 1]
                    val tAge = i.toFloat() / allPositions.size
                    val lineWidth = 3.5f * (1f - tAge)
                    val alpha = 0.60f * (1f - tAge)
                    drawLine(
                        color = spineColor.copy(alpha = alpha),
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // 2. Draw 8 Glowing Plasma Body Beads (Drawn from tail to neck with radial gradient)
            for (i in snake.segments.indices.reversed()) {
                val seg = snake.segments[i]
                val r = maxOf(snake.headRadius - (i + 1) * 1.3f, 3.5f)
                val displayRadius = maxOf(r * 2.2f, 11f)
                val alpha = (0.95f - (i + 1) * 0.08f).coerceIn(0.25f, 0.95f)

                // Body stops with soft fading edge
                val bodyColorStops = arrayOf(
                    0.00f to Color(0xFFF8FCFF).copy(alpha = alpha),
                    0.22f to Color(0xFF96E6FF).copy(alpha = alpha * 0.92f),
                    0.42f to Color(0xFF30C8FF).copy(alpha = alpha * 0.82f),
                    0.68f to Color(0xFF2878FF).copy(alpha = alpha * 0.48f),
                    0.92f to Color(0xFF5A3CFF).copy(alpha = alpha * 0.16f),
                    1.00f to Color.Transparent,
                )

                // Radial plasma sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = bodyColorStops,
                        center = Offset(seg.position.x, seg.position.y),
                        radius = displayRadius,
                    ),
                    radius = displayRadius,
                    center = Offset(seg.position.x, seg.position.y),
                )

                // Inner bright bead
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f * alpha),
                    radius = r * 0.45f,
                    center = Offset(seg.position.x, seg.position.y),
                )
            }

            // 3. Draw Snake Head Radiant Plasma Aura (Phaser snakeHead 96x96 radial texture)
            val headAuraRadius = snake.headRadius * 2.6f

            // Check if alert proximity or split theme
            val isAlert = snake.alertLevel > 0.3f
            val headColorStops = if (isAlert) {
                arrayOf(
                    0.00f to Color.White,
                    0.15f to Color(0xFFFFE0D6),
                    0.38f to Color(0xFFFF6B4A).copy(alpha = 0.92f),
                    0.65f to Color(0xFFE03816).copy(alpha = 0.55f),
                    0.88f to Color(0xFF991400).copy(alpha = 0.20f),
                    1.00f to Color.Transparent,
                )
            } else {
                arrayOf(
                    0.00f to Color.White,
                    0.12f to Color(0xFFC8F5FF),
                    0.32f to Color(0xFF50D2FF).copy(alpha = 0.92f),
                    0.55f to Color(0xFF3796FF).copy(alpha = 0.68f),
                    0.80f to Color(0xFF8246FF).copy(alpha = 0.30f),
                    1.00f to Color.Transparent,
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = headColorStops,
                    center = Offset(snake.headPosition.x, snake.headPosition.y),
                    radius = headAuraRadius,
                ),
                radius = headAuraRadius,
                center = Offset(snake.headPosition.x, snake.headPosition.y),
            )

            // Specular highlight on top-left of head
            val highlightOffset = Offset(
                snake.headPosition.x - snake.headRadius * 0.25f,
                snake.headPosition.y - snake.headRadius * 0.25f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.65f), Color.Transparent),
                    center = highlightOffset,
                    radius = snake.headRadius * 0.65f,
                ),
                radius = snake.headRadius * 0.65f,
                center = highlightOffset,
            )

            // 4. White Cranium Inner Core Disc (Phaser headDot)
            drawCircle(
                color = Color(0xFFF4F8FF).copy(alpha = 0.95f),
                radius = snake.headRadius * 0.45f,
                center = Offset(snake.headPosition.x, snake.headPosition.y),
            )

            // 5. Black Googly Eyes & Crisp White Catchlights (Facing forward with head angle)
            val fwd = Vec2.fromAngle(snake.headAngle, 1f)
            val right = Vec2(-fwd.y, fwd.x)

            val leftEyePos = snake.headPosition + (right * -3.6f) + (fwd * 3.2f)
            val rightEyePos = snake.headPosition + (right * 3.6f) + (fwd * 3.2f)

            if (snake.isStunned) {
                // Dizzy spinning stars orbiting the head
                val spinAngle = (snake.stunRemainingMs / 80f)
                val starCount = 3
                for (s in 0 until starCount) {
                    val starOrbitAngle = spinAngle + (s * (2 * kotlin.math.PI.toFloat() / starCount))
                    val starOffset = Vec2(
                        kotlin.math.cos(starOrbitAngle) * (snake.headRadius + 8f),
                        kotlin.math.sin(starOrbitAngle) * (snake.headRadius + 6f) - 6f,
                    )
                    val starPos = snake.headPosition + starOffset
                    drawCircle(color = Color(0xFFFFE844), radius = 2.8f, center = Offset(starPos.x, starPos.y))
                }
                // Dizzy X eyes
                val xSize = 2.6f
                drawLine(Color(0xFFFFD700), Offset(leftEyePos.x - xSize, leftEyePos.y - xSize), Offset(leftEyePos.x + xSize, leftEyePos.y + xSize), strokeWidth = 1.8f)
                drawLine(Color(0xFFFFD700), Offset(leftEyePos.x - xSize, leftEyePos.y + xSize), Offset(leftEyePos.x + xSize, leftEyePos.y - xSize), strokeWidth = 1.8f)
                drawLine(Color(0xFFFFD700), Offset(rightEyePos.x - xSize, rightEyePos.y - xSize), Offset(rightEyePos.x + xSize, rightEyePos.y + xSize), strokeWidth = 1.8f)
                drawLine(Color(0xFFFFD700), Offset(rightEyePos.x - xSize, rightEyePos.y + xSize), Offset(rightEyePos.x + xSize, rightEyePos.y - xSize), strokeWidth = 1.8f)
            } else {
                // Dark Iris
                drawCircle(color = Color(0xFF05101F), radius = 2.4f, center = Offset(leftEyePos.x, leftEyePos.y))
                drawCircle(color = Color(0xFF05101F), radius = 2.4f, center = Offset(rightEyePos.x, rightEyePos.y))

                // White Catchlight Glint
                val leftGlintPos = snake.headPosition + (right * -3.6f) + (fwd * 3.6f)
                val rightGlintPos = snake.headPosition + (right * 3.6f) + (fwd * 3.6f)
                drawCircle(color = Color(0xFFF4F8FF), radius = 1.0f, center = Offset(leftGlintPos.x, leftGlintPos.y))
                drawCircle(color = Color(0xFFF4F8FF), radius = 1.0f, center = Offset(rightGlintPos.x, rightGlintPos.y))
            }
        }
    }
}
