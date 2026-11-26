package com.snakechase.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.snakechase.app.domain.model.OrbSkin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Draws the orb skins: a trail (behind everything) and a core (where the classic plasma orb
 * would be). In game [OrbSkin.SPARK] keeps the classic renderer; its look here is for previews.
 * `trail[0]` is the newest point (the orb itself), older points follow.
 */
object OrbPainter {

    /** Main colour of each skin, for glows, previews and the shop. */
    fun glowColor(skin: OrbSkin): Color = when (skin) {
        OrbSkin.SPARK -> Color(0xFF35D7FF)
        OrbSkin.FIREFLY -> Color(0xFFD8FF5C)
        OrbSkin.BUBBLE -> Color(0xFF8FE3FF)
        OrbSkin.SNOWFLAKE -> Color(0xFFCFF4FF)
        OrbSkin.EMBER -> Color(0xFFFF8A3D)
        OrbSkin.PIXEL -> Color(0xFF6CFF8E)
        OrbSkin.MOON -> Color(0xFFE6E9F2)
        OrbSkin.HEARTBEAT -> Color(0xFFFF4F7B)
        OrbSkin.COMET -> Color(0xFF7FD8FF)
        OrbSkin.SUPERSTAR -> Color(0xFFFFD23F)
        OrbSkin.PRISM -> Color(0xFFFF7BE5)
    }

    fun DrawScope.drawOrbTrail(trail: List<Offset>, r: Float, skin: OrbSkin, t: Float) {
        if (trail.size < 2) return
        val n = trail.size
        fun fade(i: Int) = 1f - i / n.toFloat()
        when (skin) {
            OrbSkin.SPARK -> Unit
            OrbSkin.FIREFLY -> trail.forEachIndexed { i, p ->
                if (i == 0 || i % 2 == 1) return@forEachIndexed
                val blink = 0.55f + 0.45f * sin(t * 9f + i * 1.7f)
                drawCircle(Color(0xFFD8FF5C).copy(alpha = 0.25f * fade(i) * blink), r * 0.7f, p)
                drawCircle(Color(0xFFF6FFB0).copy(alpha = 0.9f * fade(i) * blink), r * 0.16f, p)
            }
            OrbSkin.BUBBLE -> trail.forEachIndexed { i, p ->
                if (i == 0 || i % 3 != 0) return@forEachIndexed
                val wob = sin(t * 4f + i) * r * 0.3f
                val c = Offset(p.x + wob, p.y - i * r * 0.08f)
                val br = r * (0.18f + 0.12f * (i % 2))
                drawCircle(Color.White.copy(alpha = 0.55f * fade(i)), br, c, style = Stroke(1.4f))
                drawCircle(Color.White.copy(alpha = 0.7f * fade(i)), br * 0.3f, Offset(c.x - br * 0.35f, c.y - br * 0.35f))
            }
            OrbSkin.SNOWFLAKE -> trail.forEachIndexed { i, p ->
                if (i == 0) return@forEachIndexed
                val a = fade(i)
                drawCircle(Color(0xFFCFF4FF).copy(alpha = 0.18f * a), r * 0.75f * a, p)
                if (i % 3 == 0) drawSparkle(p, r * 0.3f * a, Color.White.copy(alpha = 0.85f * a), t * 2f + i)
            }
            OrbSkin.EMBER -> trail.forEachIndexed { i, p ->
                if (i == 0) return@forEachIndexed
                val a = fade(i)
                // Sparks rise and scatter as they age.
                val jitter = sin(i * 12.9898f + t * 7f) * r * 0.25f * i / n
                val c = Offset(p.x + jitter, p.y - i * r * 0.12f)
                drawCircle(lerp(Color(0xFFFFE07A), Color(0xFFFF3B1F), 1f - a).copy(alpha = 0.85f * a), r * 0.32f * a + 0.8f, c)
            }
            OrbSkin.PIXEL -> {
                val cell = r * 0.42f
                trail.forEachIndexed { i, p ->
                    if (i == 0 || i % 2 == 1) return@forEachIndexed
                    val gx = floor(p.x / cell) * cell
                    val gy = floor(p.y / cell) * cell
                    drawRect(Color(0xFF6CFF8E).copy(alpha = 0.75f * fade(i)), Offset(gx, gy), Size(cell, cell))
                }
            }
            OrbSkin.MOON -> trail.forEachIndexed { i, p ->
                if (i == 0) return@forEachIndexed
                drawCircle(Color(0xFFE6E9F2).copy(alpha = 0.12f * fade(i)), r * (1f - i * 0.04f), p)
            }
            OrbSkin.HEARTBEAT -> trail.forEachIndexed { i, p ->
                if (i == 0 || i % 4 != 0) return@forEachIndexed
                val s = r * 0.5f * fade(i)
                drawPath(heartPath(Offset(p.x, p.y - i * r * 0.06f), s), Color(0xFFFF7FA0).copy(alpha = 0.8f * fade(i)))
            }
            OrbSkin.COMET -> {
                // A smooth tapered tail: interpolate between trail points so it never looks dotted.
                for (i in n - 1 downTo 1) {
                    val a = trail[i]
                    val b = trail[i - 1]
                    for (k in 0 until 3) {
                        val f = k / 3f
                        val p = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                        val age = (i - f) / n
                        val rad = r * (1.05f - age) * 1.1f
                        drawCircle(Color(0xFF2E7BFF).copy(alpha = 0.10f * (1f - age)), rad * 1.7f, p)
                        drawCircle(lerp(Color.White, Color(0xFF7FD8FF), age).copy(alpha = 0.55f * (1f - age)), rad * 0.7f, p)
                    }
                }
            }
            OrbSkin.SUPERSTAR -> trail.forEachIndexed { i, p ->
                if (i == 0 || i % 2 == 1) return@forEachIndexed
                val a = fade(i)
                val off = Offset(sin(i * 2.3f) * r * 0.5f, cos(i * 1.9f) * r * 0.5f)
                drawSparkle(p + off, r * 0.42f * a, Color(0xFFFFE680).copy(alpha = a), t * 3f + i)
            }
            OrbSkin.PRISM -> for (i in 1 until n) {
                val a = fade(i)
                val hue = ((t * 120f + i * 28f) % 360f + 360f) % 360f
                drawLine(
                    Color.hsv(hue, 0.85f, 1f, alpha = 0.85f * a),
                    trail[i - 1],
                    trail[i],
                    strokeWidth = r * 1.1f * a + 1f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    fun DrawScope.drawOrbCore(c: Offset, r: Float, skin: OrbSkin, t: Float) {
        val glow = glowColor(skin)
        // Shared soft halo, so every skin reads as "the thing you steer" on any background.
        drawCircle(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), glow.copy(alpha = 0.12f), Color.Transparent), c, r * 1.8f),
            r * 1.8f,
            c,
        )
        when (skin) {
            OrbSkin.SPARK -> {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White, Color(0xFF78E6FF), Color(0xFF2DB4FF), Color(0x555F4BFF), Color.Transparent), c, r * 1.12f),
                    r * 1.12f,
                    c,
                )
                drawCircle(Color(0xFF35D7FF).copy(alpha = 0.9f), r * 0.47f, c, style = Stroke(1.4f))
                drawCircle(Color.White, r * 0.28f, c)
            }
            OrbSkin.FIREFLY -> {
                val blink = 0.6f + 0.4f * sin(t * 6f)
                drawCircle(Color(0xFFD8FF5C).copy(alpha = 0.35f * blink), r * 1.25f, c)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFE0), Color(0xFFD8FF5C), Color(0xFF7FB800)), c, r * 0.8f), r * 0.8f, c)
                // Little wings flicking.
                val flap = sin(t * 30f) * 0.25f + 0.75f
                drawOval(Color.White.copy(alpha = 0.45f), Offset(c.x - r * 1.05f, c.y - r * 0.9f * flap), Size(r * 0.8f, r * 0.5f * flap))
                drawOval(Color.White.copy(alpha = 0.45f), Offset(c.x + r * 0.25f, c.y - r * 0.9f * flap), Size(r * 0.8f, r * 0.5f * flap))
            }
            OrbSkin.BUBBLE -> {
                val wx = 1f + sin(t * 5f) * 0.07f
                val wy = 1f - sin(t * 5f) * 0.07f
                val tl = Offset(c.x - r * wx, c.y - r * wy)
                val sz = Size(r * 2f * wx, r * 2f * wy)
                drawOval(Brush.radialGradient(listOf(Color(0x228FE3FF), Color(0x558FE3FF), Color(0xAAE0F8FF)), c, r * 1.05f), tl, sz)
                drawOval(Color.White.copy(alpha = 0.85f), tl, sz, style = Stroke(2f))
                drawOval(Color.White.copy(alpha = 0.85f), Offset(c.x - r * 0.6f, c.y - r * 0.62f), Size(r * 0.5f, r * 0.3f))
                drawCircle(Color(0xFFFF9BE5).copy(alpha = 0.35f), r * 0.16f, Offset(c.x + r * 0.45f, c.y + r * 0.4f))
            }
            OrbSkin.SNOWFLAKE -> {
                drawCircle(Color(0xFF1E6FA8).copy(alpha = 0.55f), r * 0.95f, c)
                rotate(t * 40f, c) {
                    for (k in 0 until 6) {
                        val a = k * PI.toFloat() / 3f
                        val tip = Offset(c.x + cos(a) * r * 1.05f, c.y + sin(a) * r * 1.05f)
                        drawLine(Color.White, c, tip, strokeWidth = 2.4f, cap = StrokeCap.Round)
                        for (side in listOf(-1f, 1f)) {
                            val mid = Offset(c.x + cos(a) * r * 0.6f, c.y + sin(a) * r * 0.6f)
                            val b = a + side * 0.7f
                            drawLine(Color.White, mid, Offset(mid.x + cos(b) * r * 0.35f, mid.y + sin(b) * r * 0.35f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                        }
                    }
                }
                drawCircle(Color.White, r * 0.2f, c)
            }
            OrbSkin.EMBER -> {
                val flick = 1f + sin(t * 17f) * 0.06f + sin(t * 29f) * 0.04f
                // A flame: a teardrop pointing up, layered hot-to-cool.
                drawPath(flamePath(c, r * 1.25f * flick), Color(0xFFFF3B1F))
                drawPath(flamePath(Offset(c.x, c.y + r * 0.15f), r * 0.9f * flick), Color(0xFFFF8A3D))
                drawPath(flamePath(Offset(c.x, c.y + r * 0.3f), r * 0.55f * flick), Color(0xFFFFE07A))
            }
            OrbSkin.PIXEL -> {
                val px = r * 0.4f
                // A 5x5 rounded pixel blob with a highlight pixel and a shade pixel.
                val mask = listOf("01110", "11111", "11111", "11111", "01110")
                val origin = Offset(c.x - px * 2.5f, c.y - px * 2.5f)
                mask.forEachIndexed { y, row ->
                    row.forEachIndexed { x, ch ->
                        if (ch == '1') {
                            val col = when {
                                x == 1 && y == 1 -> Color(0xFFE8FFE8)
                                x >= 3 && y >= 3 -> Color(0xFF1F9E4A)
                                else -> Color(0xFF6CFF8E)
                            }
                            drawRect(col, Offset(origin.x + x * px, origin.y + y * px), Size(px + 0.5f, px + 0.5f))
                        }
                    }
                }
            }
            OrbSkin.MOON -> {
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFD5D9E4), Color(0xFF9AA0B4)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.3f), r, c)
                rotate(t * 12f, c) {
                    drawCircle(Color(0xFF8A90A6).copy(alpha = 0.7f), r * 0.22f, Offset(c.x + r * 0.35f, c.y - r * 0.2f))
                    drawCircle(Color(0xFF8A90A6).copy(alpha = 0.6f), r * 0.14f, Offset(c.x - r * 0.4f, c.y + r * 0.35f))
                    drawCircle(Color(0xFF8A90A6).copy(alpha = 0.5f), r * 0.1f, Offset(c.x + r * 0.15f, c.y + r * 0.5f))
                }
            }
            OrbSkin.HEARTBEAT -> {
                // Lub-dub: two quick beats, then a rest.
                val phase = (t * 1.2f) % 1f
                val beat = 1f + 0.22f * pulse(phase, 0.08f) + 0.14f * pulse(phase, 0.28f)
                val s = r * 1.1f * beat
                drawPath(heartPath(Offset(c.x, c.y + s * 0.08f), s * 1.08f), Color(0xFF7A0E2E))
                drawPath(heartPath(c, s), Color(0xFFFF4F7B))
                drawCircle(Color.White.copy(alpha = 0.7f), s * 0.15f, Offset(c.x - s * 0.35f, c.y - s * 0.3f))
            }
            OrbSkin.COMET -> {
                drawCircle(Color(0xFF2E7BFF).copy(alpha = 0.35f), r * 1.2f, c)
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFBDEBFF), Color(0xFF3FA9FF)), c, r * 0.85f), r * 0.85f, c)
            }
            OrbSkin.SUPERSTAR -> {
                val spin = t * 90f
                drawPath(starPath(c, r * 1.35f, r * 0.62f, spin), Color(0xFF8A5A00))
                drawPath(starPath(c, r * 1.2f, r * 0.55f, spin), Brush.radialGradient(listOf(Color(0xFFFFF6B0), Color(0xFFFFD23F), Color(0xFFFF9F1C)), c, r * 1.2f))
                drawSparkle(Offset(c.x + r * 1.1f, c.y - r * 1.0f), r * 0.35f * (0.6f + 0.4f * sin(t * 8f)), Color.White, 0f)
            }
            OrbSkin.PRISM -> {
                val spin = t * 120f
                rotate(spin, c) {
                    drawCircle(
                        Brush.sweepGradient(List(7) { Color.hsv(it * 60f % 360f, 0.85f, 1f) }, c),
                        r,
                        c,
                    )
                }
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), c, r * 0.75f), r * 0.75f, c)
                drawCircle(Color.White.copy(alpha = 0.9f), r, c, style = Stroke(1.6f))
            }
        }
    }

    // ── Shapes ─────────────────────────────────────────────────────────────────

    /** A quick bump around [at] in a 0..1 cycle: 1 at the peak, 0 away from it. */
    private fun pulse(phase: Float, at: Float): Float = (1f - abs(phase - at) / 0.08f).coerceAtLeast(0f)

    private fun lerp(a: Color, b: Color, f: Float): Color {
        val k = f.coerceIn(0f, 1f)
        return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, a.alpha + (b.alpha - a.alpha) * k)
    }

    private fun heartPath(c: Offset, s: Float) = Path().apply {
        moveTo(c.x, c.y + s * 0.75f)
        cubicTo(c.x - s * 1.2f, c.y + s * 0.05f, c.x - s * 0.7f, c.y - s * 0.95f, c.x, c.y - s * 0.35f)
        cubicTo(c.x + s * 0.7f, c.y - s * 0.95f, c.x + s * 1.2f, c.y + s * 0.05f, c.x, c.y + s * 0.75f)
        close()
    }

    private fun flamePath(c: Offset, s: Float) = Path().apply {
        moveTo(c.x, c.y - s * 1.1f)
        cubicTo(c.x + s * 0.35f, c.y - s * 0.5f, c.x + s * 0.85f, c.y - s * 0.1f, c.x + s * 0.7f, c.y + s * 0.35f)
        cubicTo(c.x + s * 0.55f, c.y + s * 0.8f, c.x - s * 0.55f, c.y + s * 0.8f, c.x - s * 0.7f, c.y + s * 0.35f)
        cubicTo(c.x - s * 0.85f, c.y - s * 0.1f, c.x - s * 0.35f, c.y - s * 0.5f, c.x, c.y - s * 1.1f)
        close()
    }

    private fun starPath(c: Offset, outer: Float, inner: Float, rotationDeg: Float) = Path().apply {
        val rot = rotationDeg * PI.toFloat() / 180f - PI.toFloat() / 2f
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) outer else inner
            val a = rot + k * PI.toFloat() / 5f
            val x = c.x + cos(a) * rad
            val y = c.y + sin(a) * rad
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** A four-point twinkle. */
    private fun DrawScope.drawSparkle(c: Offset, s: Float, color: Color, spin: Float) {
        if (s <= 0.3f) return
        val path = Path().apply {
            for (k in 0 until 8) {
                val rad = if (k % 2 == 0) s else s * 0.28f
                val a = spin + k * PI.toFloat() / 4f
                val x = c.x + cos(a) * rad
                val y = c.y + sin(a) * rad
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, color)
    }
}
