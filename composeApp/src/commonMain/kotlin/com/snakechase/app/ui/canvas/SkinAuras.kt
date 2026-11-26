package com.snakechase.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SnakeSkin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Living effects that give each skin its personality: fireflies that flutter around the Firefly
 * orb, bubbles that rise from Bubble, a little moon that orbits Moon, a rainbow halo on Prism,
 * and a soft glow under the brighter snake skins. Purely visual; the perks are in SkinPerks.
 */
object SkinAuras {
    private const val TAU = (2 * PI).toFloat()

    /** Drawn behind a skinned orb at [c] with core radius [r]. */
    fun DrawScope.drawOrbAura(c: Offset, r: Float, skin: OrbSkin, t: Float) {
        when (skin) {
            OrbSkin.FIREFLY -> repeat(5) { i ->
                // Fireflies flutter on wobbling loops and blink on and off.
                val a = t * (1.1f + i * 0.17f) + i * 1.9f
                val rad = r * (1.9f + 0.5f * sin(t * 1.7f + i))
                val p = Offset(c.x + cos(a) * rad, c.y + sin(a * 1.3f) * rad * 0.8f)
                val blink = (0.5f + 0.5f * sin(t * 6f + i * 2.3f)).coerceIn(0f, 1f)
                drawCircle(Color(0xFFFFF27A).copy(alpha = 0.35f * blink), r * 0.45f, p)
                drawCircle(Color(0xFFFFFBD0).copy(alpha = 0.95f * blink), r * 0.13f, p)
            }
            OrbSkin.BUBBLE -> repeat(5) { i ->
                // Bubbles rise from the orb, sway, and fade near the top.
                val life = ((t * 0.55f + i / 5f) % 1f)
                val p = Offset(c.x + sin(t * 2f + i * 1.7f) * r * 0.9f, c.y - r * 0.6f - life * r * 3.2f)
                drawCircle(Color(0xFFBFEFFF).copy(alpha = 0.7f * (1f - life)), r * (0.16f + 0.12f * (i % 2)), p, style = Stroke(r * 0.06f))
            }
            OrbSkin.SNOWFLAKE -> repeat(6) { i ->
                // Snow glitter drifting down around the crystal.
                val life = ((t * 0.4f + i / 6f) % 1f)
                val p = Offset(c.x + cos(i * 1.05f + t * 0.3f) * r * 1.8f, c.y - r * 1.4f + life * r * 3f)
                drawCircle(Color.White.copy(alpha = 0.85f * (1f - life)), r * 0.09f, p)
            }
            OrbSkin.EMBER -> repeat(6) { i ->
                // Sparks thrown upward, cooling from yellow to red.
                val life = ((t * 0.9f + i / 6f) % 1f)
                val ang = -PI.toFloat() / 2 + sin(i * 2.1f) * 0.9f
                val p = Offset(c.x + cos(ang) * r * (0.8f + life * 2.2f), c.y + sin(ang) * r * (0.8f + life * 2.2f))
                drawCircle(lerpColor(Color(0xFFFFE066), Color(0xFFFF3B1F), life).copy(alpha = 1f - life), r * 0.1f * (1f - life * 0.5f), p)
            }
            OrbSkin.PIXEL -> repeat(4) { i ->
                // Little pixels orbit in steps, like an 8-bit animation.
                val step = ((t * 6f).toInt() + i * 3) % 12
                val a = step / 12f * TAU
                val p = Offset(c.x + cos(a) * r * 1.8f, c.y + sin(a) * r * 1.8f)
                val s = r * 0.24f
                drawRect(listOf(Color(0xFF6BFF8A), Color(0xFF5FC8FF), Color(0xFFFF6BD6), Color(0xFFFFE066))[i], Offset(p.x - s / 2, p.y - s / 2), Size(s, s))
            }
            OrbSkin.MOON -> {
                // A tiny moonlet on an orbit ring.
                drawCircle(Color(0xFFD8E2F0).copy(alpha = 0.25f), r * 2.1f, c, style = Stroke(r * 0.05f))
                val a = t * 1.4f
                val p = Offset(c.x + cos(a) * r * 2.1f, c.y + sin(a) * r * 2.1f * 0.55f)
                drawCircle(Color(0xFFE6ECF5), r * 0.22f, p)
                drawCircle(Color(0xFFB5C0D0), r * 0.08f, Offset(p.x + r * 0.05f, p.y - r * 0.04f))
            }
            OrbSkin.HEARTBEAT -> {
                // A pulse ring on every beat.
                val beat = (t * 1.6f) % 1f
                drawCircle(Color(0xFFFF4F7B).copy(alpha = 0.6f * (1f - beat)), r * (1.3f + beat * 1.6f), c, style = Stroke(r * 0.12f * (1f - beat) + 0.5f))
            }
            OrbSkin.COMET -> {
                // A burning corona.
                drawCircle(Brush.radialGradient(listOf(Color(0x66FFB347), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
            }
            OrbSkin.SUPERSTAR -> repeat(3) { i ->
                // Three mini stars orbiting, twinkling.
                val a = t * 1.8f + i * TAU / 3
                val p = Offset(c.x + cos(a) * r * 2f, c.y + sin(a) * r * 2f)
                val tw = 0.6f + 0.4f * sin(t * 8f + i)
                drawStar(p, r * 0.3f * tw, Color(0xFFFFE066))
            }
            OrbSkin.PRISM -> {
                // A slowly turning rainbow halo.
                val colors = listOf(Color(0xFFFF4F7B), Color(0xFFFFB347), Color(0xFFFFE066), Color(0xFF6BFF8A), Color(0xFF5FC8FF), Color(0xFFB388FF), Color(0xFFFF4F7B))
                drawCircle(Brush.sweepGradient(colors, c), r * 1.9f, c, alpha = 0.55f, style = Stroke(r * 0.22f))
            }
            OrbSkin.SPARK -> Unit
        }
    }

    /** The colour a snake skin glows with (null = no glow). */
    fun glowColor(skin: SnakeSkin): Color? = when (skin) {
        SnakeSkin.NEON_GHOST -> Color(0xFF7FF6FF)
        SnakeSkin.NEON_TUBE -> Color(0xFFFF4FD8)
        SnakeSkin.FOREST_PHANTOM -> Color(0xFF8CFF6B)
        SnakeSkin.ABYSS_SERPENT -> Color(0xFF4FA8FF)
        SnakeSkin.ICE_CRYSTAL -> Color(0xFFBFEFFF)
        SnakeSkin.CRUCIBLE_TITAN -> Color(0xFFFF7A1A)
        SnakeSkin.PERFECT_DRIFT -> Color(0xFFFFFFFF)
        SnakeSkin.THE_ENDGAME -> Color(0xFFB388FF)
        SnakeSkin.GOLDEN_IDOL -> Color(0xFFFFD23F)
        else -> null
    }

    /** A soft breathing glow under the snake body (drawn before its outline). */
    fun DrawScope.drawSnakeGlow(points: List<Offset>, radii: List<Float>, skin: SnakeSkin, t: Float) {
        val color = glowColor(skin) ?: return
        val breathe = 0.75f + 0.25f * sin(t * 2.4f)
        for (i in points.size - 1 downTo 1) {
            val rainbow = if (skin == SnakeSkin.PERFECT_DRIFT) Color.hsv(((t * 60f + i * 18f) % 360f), 0.7f, 1f) else color
            drawLine(rainbow.copy(alpha = 0.16f * breathe), points[i], points[i - 1], strokeWidth = radii[i] * 4.2f, cap = StrokeCap.Round)
            drawLine(rainbow.copy(alpha = 0.22f * breathe), points[i], points[i - 1], strokeWidth = radii[i] * 2.9f, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.drawStar(c: Offset, s: Float, color: Color) {
        val path = Path()
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) s else s * 0.45f
            val a = -PI.toFloat() / 2 + k * PI.toFloat() / 5
            if (k == 0) path.moveTo(c.x + cos(a) * rad, c.y + sin(a) * rad) else path.lineTo(c.x + cos(a) * rad, c.y + sin(a) * rad)
        }
        path.close()
        drawPath(path, color)
    }

    private fun lerpColor(a: Color, b: Color, f: Float) = Color(
        a.red + (b.red - a.red) * f,
        a.green + (b.green - a.green) * f,
        a.blue + (b.blue - a.blue) * f,
        1f,
    )
}
