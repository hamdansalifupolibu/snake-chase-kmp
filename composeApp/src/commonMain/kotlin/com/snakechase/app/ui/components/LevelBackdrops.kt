package com.snakechase.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import snakechase.composeapp.generated.resources.Res
import snakechase.composeapp.generated.resources.bg_landing
import snakechase.composeapp.generated.resources.bg_level2
import snakechase.composeapp.generated.resources.bg_level3
import snakechase.composeapp.generated.resources.bg_level4
import snakechase.composeapp.generated.resources.bg_level5
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** Each level's painted artwork. */
fun levelArt(levelId: Int): DrawableResource = when (levelId) {
    2 -> Res.drawable.bg_level2
    3 -> Res.drawable.bg_level3
    4 -> Res.drawable.bg_level4
    5 -> Res.drawable.bg_level5
    else -> Res.drawable.bg_landing
}

/**
 * A level's world: its painted artwork brought to life with an animated layer made for it
 * (neon rain, glowing spores, bubbles and jellyfish, snowfall and aurora, rising embers).
 * The same backdrop paints the level card, the menu behind it and the arena itself.
 * A [parallax] offset (the card's 3D tilt) moves the near effect layers more than the art.
 */
@Composable
fun LevelBackdrop(levelId: Int, modifier: Modifier = Modifier, parallax: Offset = Offset.Zero, effects: Boolean = true) {
    Box(modifier) {
        Image(
            painter = painterResource(levelArt(levelId)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Slightly oversized so the parallax shift never shows an edge.
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationX = parallax.x * 0.35f
                    translationY = parallax.y * 0.35f
                },
        )
        if (effects) {
            val loop = rememberInfiniteTransition(label = "fx")
            val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "t")
            Canvas(Modifier.fillMaxSize()) { drawLevelEffects(levelId, t, parallax) }
        }
    }
}

/** Per-level colours the UI adapts to while you're in that level. */
data class LevelLook(
    /** Dark tone sitting under the arena floor. */
    val shade: Color,
    /** Panel fill for in-level dialogs and cards. */
    val panel: Color,
    /** Signature colour: title plates, frames, stage pips. */
    val accent: Color,
    /** Second highlight for effects and tags. */
    val glow: Color,
)

fun levelLook(levelId: Int): LevelLook = when (levelId) {
    2 -> LevelLook(Color(0xFF061509), Color(0xFF123320), Color(0xFF8CFF5A), Color(0xFFD7FF7A))
    3 -> LevelLook(Color(0xFF031A2A), Color(0xFF0C2E45), Color(0xFF4FE0F0), Color(0xFFFF6FD0))
    4 -> LevelLook(Color(0xFF0D1C33), Color(0xFF1B3352), Color(0xFFBFE6FF), Color(0xFFFFFFFF))
    5 -> LevelLook(Color(0xFF170605), Color(0xFF3A1610), Color(0xFFFF8A3D), Color(0xFFFFD23F))
    else -> LevelLook(Color(0xFF0B0A1F), Color(0xFF1C1A40), Color(0xFF55E6FF), Color(0xFFFF6FB5))
}

fun DrawScope.drawLevelEffects(levelId: Int, t: Float, parallax: Offset = Offset.Zero) {
    val fx = Fx(this, t, parallax, levelLook(levelId))
    when (levelId) {
        2 -> fx.neonGrove()
        3 -> fx.abyssalChase()
        4 -> fx.crystalTundra()
        5 -> fx.crucibleCore()
        else -> fx.neonDrift()
    }
    fx.vignette()
}

private const val TAU = (2 * PI).toFloat()

private fun fract(v: Float) = v - floor(v)

/** Stable pseudo-random 0..1 for element [i]: the same layout every frame and every run. */
private fun rnd(i: Int, salt: Float = 0f) = fract(sin(i * 12.9898f + salt * 78.233f) * 43758.547f)

private class Fx(val d: DrawScope, val t: Float, val parallax: Offset, val look: LevelLook) {
    val w = d.size.width
    val h = d.size.height
    val m = d.size.minDimension
    val phase = t * TAU

    /** Near layers follow the tilt more than far ones. */
    fun layer(depth: Float, block: DrawScope.() -> Unit) = d.translate(parallax.x * depth, parallax.y * depth) { block() }

    fun glowDot(c: Offset, r: Float, color: Color, alpha: Float) {
        d.drawCircle(color.copy(alpha = 0.16f * alpha), r * 3.2f, c)
        d.drawCircle(color.copy(alpha = 0.35f * alpha), r * 1.8f, c)
        d.drawCircle(color.copy(alpha = alpha), r, c)
    }

    /** Each particle loops bottom to top at its own pace. */
    fun rising(count: Int, salt: Float, speed: Float, sway: Float, draw: (Offset, Float, Int) -> Unit) {
        for (i in 0 until count) {
            val progress = fract(rnd(i, salt) + t * speed * (0.6f + rnd(i, salt + 1f)))
            val x = w * rnd(i, salt + 2f) + sway * m * sin(phase * 3f + i)
            draw(Offset(x, h * (1.1f - progress * 1.2f)), progress, i)
        }
    }

    /** Each particle loops top to bottom, drifting sideways. */
    fun falling(count: Int, salt: Float, speed: Float, drift: Float, draw: (Offset, Int) -> Unit) {
        for (i in 0 until count) {
            val progress = fract(rnd(i, salt) + t * speed * (0.7f + rnd(i, salt + 1f)))
            val x = w * rnd(i, salt + 2f) + drift * m * sin(phase * 2f + i * 1.7f) + progress * drift * m * 2f
            draw(Offset(x, h * (progress * 1.15f - 0.08f)), i)
        }
    }

    fun vignette() {
        d.drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, look.shade.copy(alpha = 0.55f)),
                center = Offset(w / 2f, h / 2f),
                radius = max(w, h) * 0.75f,
            ),
        )
    }

    // ─── 1 · Neon Drift: neon rain over the night city lake ───────────────
    fun neonDrift() {
        layer(0.3f) {
            // Big soft neon bokeh drifting across the skyline.
            for (i in 0 until 9) {
                val x = w * fract(rnd(i, 1f) + t * 0.3f * (0.5f + rnd(i, 2f)))
                val y = h * (0.15f + 0.45f * rnd(i, 3f)) + sin(phase * 2f + i) * m * 0.02f
                val pulse = 0.5f + 0.5f * sin(phase * 4f + i * 2f)
                val c = if (i % 2 == 0) look.accent else look.glow
                drawCircle(c.copy(alpha = 0.12f + 0.12f * pulse), m * (0.03f + 0.03f * rnd(i, 4f)), Offset(x, y))
            }
        }
        layer(0.6f) {
            // Neon reflections shimmering on the water.
            for (k in 0 until 10) {
                val y = h * (0.66f + k * 0.032f)
                val x = w * rnd(k, 5f) + sin(phase * 3f + k) * w * 0.03f
                val len = w * (0.05f + 0.07f * rnd(k, 6f))
                val c = if (k % 3 == 0) look.glow else look.accent
                drawLine(c.copy(alpha = 0.35f + 0.3f * sin(phase * 6f + k)), Offset(x, y), Offset(x + len, y), strokeWidth = m * 0.006f, cap = StrokeCap.Round)
            }
        }
        layer(1f) {
            // Fine slanted rain.
            for (i in 0 until 46) {
                val progress = fract(rnd(i, 7f) + t * 9f * (0.7f + rnd(i, 8f)))
                val x = w * rnd(i, 9f) + progress * w * 0.06f
                val y = h * (progress * 1.2f - 0.1f)
                drawLine(Color(0x88CFEFFF), Offset(x, y), Offset(x + m * 0.012f, y + m * 0.045f), strokeWidth = max(1f, m * 0.0022f), cap = StrokeCap.Round)
            }
        }
    }

    // ─── 2 · Neon Grove: spores and fireflies around the glowing tree ─────
    fun neonGrove() {
        layer(0.4f) {
            for (i in 0 until 12) {
                // Fireflies wander in lazy loops and blink.
                val cx = w * (0.1f + 0.8f * rnd(i, 11f)) + sin(phase * 2f + i) * m * 0.06f
                val cy = h * (0.25f + 0.55f * rnd(i, 12f)) + sin(phase * 3f + i * 2f) * m * 0.04f
                val blink = (0.5f + 0.5f * sin(phase * 7f + i * 3f)).let { it * it }
                glowDot(Offset(cx, cy), m * 0.006f, Color(0xFFF4FF8A), blink)
            }
        }
        layer(1f) {
            rising(24, 13f, 0.55f, 0.015f) { p, progress, _ ->
                glowDot(p, m * 0.004f, look.accent, (1f - progress * 0.7f) * (0.6f + 0.4f * sin(phase * 6f + p.x)))
            }
        }
    }

    // ─── 3 · Abyssal Chase: sunbeams, drifting jellyfish, bubbles ─────────
    fun abyssalChase() {
        layer(0.15f) {
            for (i in 0 until 4) {
                val x = w * (0.15f + 0.24f * i) + sin(phase + i) * w * 0.025f
                val beam = Path().apply {
                    moveTo(x - w * 0.03f, -h * 0.1f)
                    lineTo(x + w * 0.03f, -h * 0.1f)
                    lineTo(x + w * 0.12f, h * 0.95f)
                    lineTo(x - w * 0.02f, h * 0.95f)
                    close()
                }
                drawPath(beam, Color.White.copy(alpha = 0.05f + 0.03f * sin(phase * 2f + i)))
            }
        }
        layer(0.5f) {
            for (i in 0 until 3) {
                val progress = fract(rnd(i, 21f) + t * 0.6f)
                val pulse = 0.5f + 0.5f * sin(phase * 8f + i)
                val c = Offset(w * (0.2f + 0.3f * i) + sin(phase * 2f + i) * m * 0.04f, h * (1.05f - progress * 1.2f))
                val r = m * (0.035f + 0.01f * pulse)
                val color = if (i == 1) look.accent else look.glow
                drawCircle(color.copy(alpha = 0.12f), r * 2.2f, c)
                drawArc(color.copy(alpha = 0.55f), 180f, 180f, true, c - Offset(r, r * 0.8f), Size(r * 2f, r * 1.6f))
                for (k in 0 until 4) {
                    val x0 = c.x - r * 0.6f + k * r * 0.4f
                    val tentacle = Path().apply {
                        moveTo(x0, c.y)
                        for (s in 1..4) lineTo(x0 + sin(phase * 6f + k + s) * r * 0.25f, c.y + s * r * 0.45f)
                    }
                    drawPath(tentacle, color.copy(alpha = 0.4f), style = Stroke(max(1f, m * 0.003f), cap = StrokeCap.Round))
                }
            }
        }
        layer(1f) {
            rising(20, 23f, 0.8f, 0.01f) { p, _, i ->
                val r = m * (0.005f + 0.008f * rnd(i, 24f))
                drawCircle(Color(0xCCE6FBFF), r, p, style = Stroke(max(1f, m * 0.002f)))
                drawCircle(Color(0x99FFFFFF), r * 0.3f, p - Offset(r * 0.35f, r * 0.35f))
            }
        }
    }

    // ─── 4 · Crystal Tundra: aurora, snowfall and ice glints ──────────────
    fun crystalTundra() {
        layer(0.1f) {
            for (band in 0 until 3) {
                val p = Path()
                var x = -w * 0.1f
                p.moveTo(x, h * (0.12f + band * 0.05f))
                while (x <= w * 1.1f) {
                    p.lineTo(x, h * (0.12f + band * 0.05f) + h * 0.05f * sin(x / w * 2.5f * TAU + phase + band))
                    x += w / 40f
                }
                val c = if (band == 1) Color(0xFF7DF9C5) else Color(0xFF9FD8FF)
                drawPath(p, c.copy(alpha = 0.16f + 0.08f * sin(phase * 2f + band)), style = Stroke(h * 0.045f, cap = StrokeCap.Round))
            }
        }
        layer(0.5f) {
            for (i in 0 until 8) {
                val twinkle = (0.5f + 0.5f * sin(phase * 9f + i * 2.3f)).let { it * it * it }
                val c = Offset(w * rnd(i, 31f), h * (0.7f + 0.25f * rnd(i, 32f)))
                val r = m * 0.012f * twinkle
                drawLine(Color.White.copy(alpha = twinkle), c - Offset(r, 0f), c + Offset(r, 0f), strokeWidth = max(1f, m * 0.002f))
                drawLine(Color.White.copy(alpha = twinkle), c - Offset(0f, r), c + Offset(0f, r), strokeWidth = max(1f, m * 0.002f))
            }
        }
        layer(1f) {
            falling(40, 33f, 1.2f, 0.02f) { p, i ->
                drawCircle(Color.White.copy(alpha = 0.85f), m * (0.003f + 0.004f * rnd(i, 34f)), p)
            }
        }
    }

    // ─── 5 · Crucible Core: heat glow, ash and rising embers ──────────────
    fun crucibleCore() {
        layer(0.1f) {
            val pulse = 0.5f + 0.5f * sin(phase * 5f)
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, look.accent.copy(alpha = 0.10f + 0.10f * pulse)),
                    startY = h * 0.45f,
                    endY = h,
                ),
            )
        }
        layer(0.5f) {
            falling(14, 41f, 0.6f, 0.03f) { p, i ->
                drawCircle(Color(0x887A6A66), m * (0.003f + 0.003f * rnd(i, 42f)), p)
            }
        }
        layer(1f) {
            rising(26, 43f, 0.9f, 0.025f) { p, progress, i ->
                val a = (1f - progress).coerceIn(0f, 1f)
                glowDot(p, m * (0.003f + 0.003f * rnd(i, 44f)), if (i % 3 == 0) look.glow else look.accent, a)
            }
        }
    }
}
