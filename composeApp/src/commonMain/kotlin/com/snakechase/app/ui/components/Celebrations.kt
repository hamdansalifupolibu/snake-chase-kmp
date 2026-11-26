package com.snakechase.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One firework: where it pops (px, in the layer's own space), its colour and when. */
internal data class Burst(val center: Offset, val color: Color, val bornNs: Long)

/**
 * Drives a [CelebrationLayer]: confetti starts on its own, fireworks go off when [burst] is
 * called (e.g. as each star stamps in). Time comes from the frame clock, so effects pause and
 * resume with the app and never need game state.
 */
@Stable
class CelebrationState {
    internal var nowNs by mutableLongStateOf(0L)
    private val bursts = mutableStateListOf<Burst>()
    internal val active: List<Burst> get() = bursts

    /** A firework at [center] (layer px). Old ones are dropped once they have faded. */
    fun burst(center: Offset, color: Color) {
        bursts.removeAll { nowNs - it.bornNs > BURST_LIFE_NS }
        bursts += Burst(center, color, nowNs)
    }

    internal companion object {
        const val BURST_LIFE_NS = 1_300_000_000L
    }
}

@Composable
fun rememberCelebrationState(): CelebrationState = remember { CelebrationState() }

/**
 * Confetti raining for about [confettiSeconds] (in [palette] colours, so each level celebrates
 * in its own colours) plus any fireworks fired on [state]. Draws only: taps go straight through
 * to whatever is underneath. [autoFireworks] keeps launching them on a timer (grand finale).
 */
@Composable
fun CelebrationLayer(
    state: CelebrationState,
    palette: List<Color>,
    modifier: Modifier = Modifier,
    confettiSeconds: Float = 4.5f,
    pieces: Int = 70,
    autoFireworks: Boolean = false,
) {
    LaunchedEffect(state) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { state.nowNs = it - start }
    }
    Canvas(modifier) {
        val t = state.nowNs / 1e9f
        drawConfetti(t, palette, confettiSeconds, pieces)
        if (autoFireworks) {
            // A firework every 0.45 s at a spot that walks around the upper screen.
            val k = (t / 0.45f).toInt()
            val bornAt = k * 0.45f
            val age = t - bornAt
            for (back in 0..2) {
                val idx = k - back
                if (idx < 0) continue
                val a = age + back * 0.45f
                val p = Offset(size.width * (0.15f + 0.7f * hash(idx * 3)), size.height * (0.12f + 0.35f * hash(idx * 7 + 1)))
                drawBurst(p, palette[idx.mod(palette.size)], a)
            }
        }
        state.active.forEach { b -> drawBurst(b.center, b.color, (state.nowNs - b.bornNs) / 1e9f) }
    }
}

/** Slowly turning sunburst, the classic "you won something" backdrop. */
@Composable
fun SunRays(color: Color, modifier: Modifier = Modifier, rays: Int = 14, periodMs: Int = 16_000) {
    val spin by rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "spin")
    Canvas(modifier) {
        val c = center
        val len = size.maxDimension
        val half = (PI / rays / 2).toFloat()
        // Rays fade out with distance, so they glow around the centre instead of striping everything.
        val fade = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), c, len * 0.6f)
        rotate(spin, c) {
            for (k in 0 until rays) {
                val a = (2 * PI * k / rays).toFloat()
                drawPath(
                    Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(c.x + cos(a - half) * len, c.y + sin(a - half) * len)
                        lineTo(c.x + cos(a + half) * len, c.y + sin(a + half) * len)
                        close()
                    },
                    fade,
                )
            }
        }
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = color.alpha * 2f), Color.Transparent), c, size.minDimension * 0.5f), size.minDimension * 0.5f, c)
    }
}

/** Cheap stable pseudo-random in 0..1, so every piece keeps its own path frame to frame. */
private fun hash(i: Int): Float {
    val x = sin(i * 12.9898f + 78.233f) * 43758.547f
    return x - kotlin.math.floor(x)
}

private fun DrawScope.drawConfetti(t: Float, palette: List<Color>, seconds: Float, pieces: Int) {
    if (palette.isEmpty()) return
    for (i in 0 until pieces) {
        // Pieces are released over the first 60% of the window and fall about 1.3 screens.
        val delay = hash(i) * seconds * 0.6f
        val fall = 0.32f + hash(i + 400) * 0.25f // screens per second
        val age = t - delay
        if (age < 0f) continue
        val yFrac = -0.05f + age * fall
        if (yFrac > 1.1f) continue
        val x = size.width * hash(i + 900) + sin(age * (2f + hash(i + 50) * 3f) + i) * 22f
        val y = size.height * yFrac
        val w = 7f + hash(i + 7) * 7f
        val flip = cos(age * (6f + hash(i + 3) * 6f)) // fakes a 3D tumble by squashing the piece
        rotate(age * 200f * (if (i % 2 == 0) 1 else -1) + i * 37f, Offset(x, y)) {
            drawRect(palette[i % palette.size], Offset(x - w / 2f, y - w * 0.3f * flip), Size(w, (w * 0.6f * flip).let { if (it < 0) -it else it } + 1f))
        }
    }
}

private fun DrawScope.drawBurst(c: Offset, color: Color, ageSec: Float) {
    val life = CelebrationState.BURST_LIFE_NS / 1e9f
    if (ageSec < 0f || ageSec > life) return
    val f = ageSec / life
    val fade = 1f - f
    // The flash: a quick bright ring at the start.
    if (f < 0.25f) {
        val rf = f / 0.25f
        drawCircle(Color.White.copy(alpha = (1f - rf) * 0.8f), 10f + rf * 60f, c)
    }
    val sparks = 26
    for (k in 0 until sparks) {
        val a = (2 * PI * k / sparks).toFloat() + hash(k) * 0.2f
        val speed = 210f + hash(k + 11) * 120f
        // Sparks fly out, slow down and droop with gravity.
        val dist = speed * (1f - (1f - f) * (1f - f)) * life * 0.55f
        val droop = 140f * f * f
        val p = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist + droop)
        val tail = Offset(c.x + cos(a) * dist * 0.82f, c.y + sin(a) * dist * 0.82f + droop * 0.8f)
        val col = if (k % 3 == 0) Color.White else color
        drawLine(col.copy(alpha = fade * 0.9f), tail, p, strokeWidth = 3.2f * fade + 0.8f, cap = StrokeCap.Round)
    }
}
