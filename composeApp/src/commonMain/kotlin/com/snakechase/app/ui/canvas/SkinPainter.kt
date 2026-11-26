package com.snakechase.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.snakechase.app.domain.model.SnakeSkin
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a snake in one of the patterned skins as a chunky sticker: an ink outline around a
 * continuous body, a pattern on every segment, and a cartoon head that looks where it's going.
 * Shared by the arena (SnakeRenderer) and every preview, so the shop shows exactly what you get.
 */
object SkinPainter {
    private val Ink = Color(0xFF0E1F17)

    /** The colour a skin is "known by": its head, its shop swatch. */
    fun headColor(skin: SnakeSkin): Color = when (skin) {
        SnakeSkin.DEFAULT -> Color(0xFF50D2FF)
        SnakeSkin.CANDY_POP -> Color(0xFFFF7EB6)
        SnakeSkin.CORAL_KRAIT -> Color(0xFFE8323A)
        SnakeSkin.NEON_GHOST -> Color(0xFF7FF6FF)
        SnakeSkin.NEON_TUBE -> Color(0xFFFF3FCB)
        SnakeSkin.FOREST_PHANTOM -> Color(0xFF3FA34D)
        SnakeSkin.ABYSS_SERPENT -> Color(0xFF2A3F9A)
        SnakeSkin.ICE_CRYSTAL -> Color(0xFFCDEBFF)
        SnakeSkin.FEARLESS -> Color(0xFFD7263D)
        SnakeSkin.CRUCIBLE_TITAN -> Color(0xFF4A2A22)
        SnakeSkin.PERFECT_DRIFT -> Color(0xFFFF6FD0)
        SnakeSkin.THE_ENDGAME -> Color(0xFF2A1660)
        SnakeSkin.GOLDEN_IDOL -> Color(0xFFFFC83D)
    }

    /**
     * [points]: head first, then each body segment. [radii]: matching radii (head first).
     * [timeSec] drives the animated skins (rainbow, lava, stars, sparkles, glints).
     */
    fun DrawScope.drawSkinnedSnake(
        points: List<Offset>,
        radii: List<Float>,
        headAngle: Float,
        skin: SnakeSkin,
        timeSec: Float,
        alert: Boolean = false,
        stunned: Boolean = false,
        stunSpin: Float = 0f,
    ) {
        if (points.isEmpty()) return
        val n = points.size
        val ghost = skin == SnakeSkin.NEON_GHOST
        val outlineColor = if (ghost) Color(0xFF7FF6FF).copy(alpha = 0.55f) else Ink

        // 0. Glowing skins breathe a soft light around the body (SkinAuras.glowColor).
        with(SkinAuras) { drawSnakeGlow(points, radii, skin, timeSec) }

        // 1. Outline pass: one continuous ink silhouette, tail to head.
        for (i in n - 1 downTo 1) {
            val o = outline(radii[i])
            drawLine(outlineColor, points[i], points[i - 1], strokeWidth = (radii[i] + o) * 2f, cap = StrokeCap.Round)
        }
        // 2. Body fill pass.
        for (i in n - 1 downTo 1) {
            drawLine(bodyFill(skin, i, timeSec), points[i], points[i - 1], strokeWidth = radii[i] * 2f, cap = StrokeCap.Round)
        }
        // 3. Pattern on every segment.
        for (i in n - 1 downTo 1) {
            val dir = atan2(points[i - 1].y - points[i].y, points[i - 1].x - points[i].x)
            drawPattern(skin, i, points[i], radii[i], dir, timeSec)
        }
        // 4. Head.
        drawHead(skin, points[0], radii[0], headAngle, timeSec, alert, stunned, stunSpin)
    }

    private fun outline(r: Float) = max(1.6f, r * 0.26f)

    private fun hue(i: Int, t: Float) = Color.hsv(((i * 28f + t * 140f) % 360f + 360f) % 360f, 0.72f, 1f)

    private fun bodyFill(skin: SnakeSkin, i: Int, t: Float): Color = when (skin) {
        SnakeSkin.DEFAULT -> if (i % 2 == 0) Color(0xFF30C8FF) else Color(0xFF2878FF)
        SnakeSkin.CANDY_POP -> listOf(Color(0xFFFF7EB6), Color(0xFFFFF4DC), Color(0xFF7EF5C8))[i % 3]
        SnakeSkin.CORAL_KRAIT -> listOf(Color(0xFFE8323A), Color(0xFF1A1A1A), Color(0xFFFFD23F), Color(0xFF1A1A1A))[i % 4]
        SnakeSkin.NEON_GHOST -> Color(0xFF7FF6FF).copy(alpha = 0.28f)
        SnakeSkin.NEON_TUBE -> Color(0xFF2A0A2E)
        SnakeSkin.FOREST_PHANTOM -> if (i % 2 == 0) Color(0xFF2E7D3C) else Color(0xFF3FA34D)
        SnakeSkin.ABYSS_SERPENT -> if (i % 2 == 0) Color(0xFF0E1B4A) else Color(0xFF1C2E7A)
        SnakeSkin.ICE_CRYSTAL -> if (i % 2 == 0) Color(0xFFBFE6FF) else Color(0xFFDDF2FF)
        SnakeSkin.FEARLESS -> Color(0xFFD7263D)
        SnakeSkin.CRUCIBLE_TITAN -> if (i % 2 == 0) Color(0xFF3A2420) else Color(0xFF4A2A22)
        SnakeSkin.PERFECT_DRIFT -> hue(i, t)
        SnakeSkin.THE_ENDGAME -> if (i % 2 == 0) Color(0xFF1B0F3A) else Color(0xFF2A1660)
        SnakeSkin.GOLDEN_IDOL -> if (i % 2 == 0) Color(0xFFFFC83D) else Color(0xFFF2B228)
    }

    private fun twinkle(t: Float, seed: Int, speed: Float = 3f) = (0.5f + 0.5f * sin(t * speed + seed * 2.39f)).let { it * it }

    private fun DrawScope.drawPattern(skin: SnakeSkin, i: Int, c: Offset, r: Float, dir: Float, t: Float) {
        when (skin) {
            SnakeSkin.DEFAULT -> drawCircle(Color.White.copy(alpha = 0.7f), r * 0.38f, c)
            SnakeSkin.CANDY_POP, SnakeSkin.CORAL_KRAIT ->
                drawCircle(Color.White.copy(alpha = 0.35f), r * 0.3f, c - Offset(r * 0.3f, r * 0.35f))
            SnakeSkin.NEON_GHOST -> {
                drawCircle(Color(0xFF7FF6FF).copy(alpha = 0.18f), r * 1.5f, c)
                drawCircle(Color(0xFFE6FFFF).copy(alpha = 0.9f), r * 0.28f, c)
            }
            SnakeSkin.NEON_TUBE -> {
                val pink = Color(0xFFFF3FCB)
                drawCircle(pink.copy(alpha = 0.16f), r * 1.7f, c)
                drawCircle(pink, r * 0.8f, c, style = Stroke(max(1.4f, r * 0.32f)))
                drawCircle(Color(0xFFFFC2EE), r * 0.8f, c, style = Stroke(max(0.8f, r * 0.1f)))
            }
            SnakeSkin.FOREST_PHANTOM -> {
                drawArc(Color(0xFF1C5A2C), 200f, 140f, false, c - Offset(r * 0.75f, r * 0.75f), androidx.compose.ui.geometry.Size(r * 1.5f, r * 1.5f), style = Stroke(max(1f, r * 0.2f)))
                if (i % 3 == 0) {
                    val glow = twinkle(t, i)
                    drawCircle(Color(0xFFC8F560).copy(alpha = 0.25f * glow), r * 0.8f, c)
                    drawCircle(Color(0xFFD7FF7A).copy(alpha = 0.6f + 0.4f * glow), r * 0.25f, c)
                }
            }
            SnakeSkin.ABYSS_SERPENT -> if (i % 2 == 1) {
                val glow = twinkle(t, i, 2.4f)
                val spot = if (i % 4 == 1) Color(0xFFFF6FD0) else Color(0xFF4FE0F0)
                drawCircle(spot.copy(alpha = 0.25f * glow), r * 0.9f, c)
                drawCircle(spot.copy(alpha = 0.5f + 0.5f * glow), r * 0.3f, c)
            }
            SnakeSkin.ICE_CRYSTAL -> {
                rotate(45f + i * 20f, c) {
                    drawRect(Color.White.copy(alpha = 0.85f), c - Offset(r * 0.3f, r * 0.3f), androidx.compose.ui.geometry.Size(r * 0.6f, r * 0.6f))
                }
                val g = twinkle(t, i, 4f)
                if (g > 0.6f) {
                    val s = r * 0.9f * g
                    drawLine(Color.White, c - Offset(s, 0f), c + Offset(s, 0f), max(1f, r * 0.12f))
                    drawLine(Color.White, c - Offset(0f, s), c + Offset(0f, s), max(1f, r * 0.12f))
                }
            }
            SnakeSkin.FEARLESS -> if (i % 2 == 0) {
                // A black chevron pointing toward the head.
                val fx = cos(dir)
                val fy = sin(dir)
                val tip = c + Offset(fx, fy) * (r * 0.55f)
                val back = c - Offset(fx, fy) * (r * 0.25f)
                val side = Offset(-fy, fx) * (r * 0.7f)
                val chevron = Path().apply {
                    moveTo(back.x + side.x, back.y + side.y)
                    lineTo(tip.x, tip.y)
                    lineTo(back.x - side.x, back.y - side.y)
                }
                drawPath(chevron, Ink, style = Stroke(max(1.4f, r * 0.3f), cap = StrokeCap.Round))
            }
            SnakeSkin.CRUCIBLE_TITAN -> {
                val heat = 0.55f + 0.45f * sin(t * 5f + i * 0.9f)
                val a = dir + PI.toFloat() / 2f
                val p1 = c + Offset(cos(a), sin(a)) * (r * 0.7f)
                val p2 = c + Offset(cos(dir), sin(dir)) * (r * 0.2f)
                val p3 = c - Offset(cos(a), sin(a)) * (r * 0.6f)
                drawLine(Color(0xFFFF8A3D).copy(alpha = 0.35f * heat), p1, p3, max(2f, r * 0.45f), StrokeCap.Round)
                drawLine(Color(0xFFFFB347).copy(alpha = heat), p1, p2, max(1f, r * 0.16f), StrokeCap.Round)
                drawLine(Color(0xFFFFD23F).copy(alpha = heat), p2, p3, max(1f, r * 0.16f), StrokeCap.Round)
            }
            SnakeSkin.PERFECT_DRIFT -> drawCircle(Color.White.copy(alpha = 0.45f), r * 0.3f, c - Offset(r * 0.3f, r * 0.35f))
            SnakeSkin.THE_ENDGAME -> {
                drawCircle(Color(0xFFB07CFF).copy(alpha = 0.18f), r * 0.85f, c)
                for (k in 0 until 2) {
                    val ang = t * 0.8f + i * 1.7f + k * 3.1f
                    val p = c + Offset(cos(ang), sin(ang)) * (r * 0.5f)
                    drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * twinkle(t, i * 2 + k, 5f)), max(0.8f, r * 0.12f), p)
                }
            }
            SnakeSkin.GOLDEN_IDOL -> {
                drawCircle(Color(0xFFC99312), r * 0.95f, c, style = Stroke(max(1f, r * 0.14f)))
                drawCircle(Color(0xFFFFF0A0).copy(alpha = 0.8f), r * 0.3f, c - Offset(r * 0.3f, r * 0.35f))
                val g = twinkle(t, i, 3.5f)
                if (g > 0.75f) {
                    val s = r * 0.7f * g
                    val p = c + Offset(r * 0.35f, -r * 0.2f)
                    drawLine(Color.White, p - Offset(s, 0f), p + Offset(s, 0f), max(1f, r * 0.1f))
                    drawLine(Color.White, p - Offset(0f, s), p + Offset(0f, s), max(1f, r * 0.1f))
                }
            }
        }
    }

    private fun DrawScope.drawHead(
        skin: SnakeSkin,
        c: Offset,
        r: Float,
        angle: Float,
        t: Float,
        alert: Boolean,
        stunned: Boolean,
        stunSpin: Float,
    ) {
        val o = outline(r)
        val ghost = skin == SnakeSkin.NEON_GHOST
        if (alert) {
            val pulse = 0.5f + 0.5f * sin(t * 14f)
            drawCircle(Color(0xFFFF4F7B).copy(alpha = 0.25f + 0.35f * pulse), r * 1.9f, c)
        }
        if (skin == SnakeSkin.NEON_TUBE) drawCircle(Color(0xFFFF3FCB).copy(alpha = 0.22f), r * 1.9f, c)
        drawCircle(if (ghost) Color(0xFF7FF6FF).copy(alpha = 0.6f) else Ink, r + o, c)
        val head = when (skin) {
            SnakeSkin.PERFECT_DRIFT -> hue(0, t)
            SnakeSkin.NEON_GHOST -> Color(0xFF7FF6FF).copy(alpha = 0.45f)
            else -> headColor(skin)
        }
        drawCircle(head, r, c)
        if (skin == SnakeSkin.NEON_TUBE) drawCircle(Color(0xFFFF3FCB), r * 0.82f, c, style = Stroke(max(1.4f, r * 0.26f)))
        if (skin == SnakeSkin.CRUCIBLE_TITAN) drawCircle(Color(0xFFFF8A3D).copy(alpha = 0.5f + 0.3f * sin(t * 5f)), r * 0.5f, c)
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.32f, c - Offset(r * 0.35f, r * 0.4f))

        val fx = cos(angle)
        val fy = sin(angle)
        val side = Offset(-fy, fx)
        val ahead = Offset(fx, fy)
        val eyeL = c + side * (-r * 0.42f) + ahead * (r * 0.3f)
        val eyeR = c + side * (r * 0.42f) + ahead * (r * 0.3f)
        val eyeR0 = r * 0.34f
        if (stunned) {
            for (s in 0 until 3) {
                val a = stunSpin + s * 2.094f
                drawCircle(Color(0xFFFFE844), max(1.6f, r * 0.22f), c + Offset(cos(a) * r * 1.5f, sin(a) * r * 1.2f - r * 0.5f))
            }
            for (e in listOf(eyeL, eyeR)) {
                val x = eyeR0 * 0.8f
                drawLine(Ink, e - Offset(x, x), e + Offset(x, x), max(1.4f, r * 0.14f))
                drawLine(Ink, e - Offset(x, -x), e + Offset(x, -x), max(1.4f, r * 0.14f))
            }
            return
        }
        // Blink now and then: a slow random-ish cadence.
        val blink = floor(t * 0.6f + 0.3f) != floor(t * 0.6f + 0.27f)
        for (e in listOf(eyeL, eyeR)) {
            if (blink) {
                drawLine(Ink, e - side * eyeR0, e + side * eyeR0, max(1.4f, r * 0.14f), StrokeCap.Round)
            } else {
                drawCircle(Ink, eyeR0 + max(0.8f, r * 0.08f), e)
                drawCircle(Color.White, eyeR0, e)
                drawCircle(if (alert) Color(0xFFD7263D) else Ink, eyeR0 * 0.55f, e + ahead * (eyeR0 * 0.35f))
                drawCircle(Color.White, eyeR0 * 0.18f, e + ahead * (eyeR0 * 0.15f) - side * (eyeR0 * 0.2f))
            }
        }
    }
}
