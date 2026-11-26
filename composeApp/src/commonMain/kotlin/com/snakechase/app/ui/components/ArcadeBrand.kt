package com.snakechase.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Jungle
import com.snakechase.app.ui.theme.JungleDeep
import com.snakechase.app.ui.theme.JungleNight
import com.snakechase.app.ui.theme.Leaf
import com.snakechase.app.ui.theme.LeafLight
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.Tangerine
import com.snakechase.app.ui.theme.displayStyle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/**
 * The game's wordmark: a sticker-style title that bounces in, with a little snake
 * forever chasing a prey orb underneath it — the whole game in one line.
 */
@Composable
fun SnakeChaseLogo(compact: Boolean, modifier: Modifier = Modifier) {
    val entrance = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Column(
        modifier = modifier.graphicsLayer {
            scaleX = entrance.value
            scaleY = entrance.value
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedText(
            text = "Snake Chase",
            style = displayStyle(if (compact) 38.sp else 66.sp),
            fill = Lime,
            outlineWidth = if (compact) 5.dp else 8.dp,
            halo = Cream,
            haloWidth = if (compact) 11.dp else 17.dp,
            shadow = if (compact) 4.dp else 7.dp,
            shadowColor = Leaf,
        )
        ChasingSnake(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(if (compact) 210.dp else 380.dp)
                .height(if (compact) 26.dp else 46.dp),
        )
    }
}

/** A short snake slithering after a bobbing prey orb. Blinks, and flicks its tongue. */
@Composable
fun ChasingSnake(modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "chase")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "slither")
    val blink by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3700, easing = LinearEasing)), label = "blink")
    val tongue by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2900, easing = LinearEasing)), label = "tongue")

    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val phase = t * TAU
        val r = h * 0.2f
        val outline = h * 0.07f
        val spacing = r * 1.05f
        val segments = 14
        val mid = h * 0.5f
        val amp = h * 0.18f

        val headX = w * 0.6f + w * 0.05f * sin(phase)
        val body = List(segments) { i ->
            val x = headX - i * spacing
            Offset(x, mid + amp * sin(phase * 2f - i * 0.55f))
        }
        val prey = Offset(headX + w * 0.13f + w * 0.04f * sin(phase + 1.3f), mid + amp * 1.2f * cos(phase * 2f))

        // Outline pass, then fill pass, tail first so the head sits on top.
        for (i in segments - 1 downTo 0) {
            drawCircle(Ink, segRadius(i, segments, r) + outline, body[i])
        }
        for (i in segments - 1 downTo 1) {
            drawCircle(if (i % 3 == 0) Banana else Tangerine, segRadius(i, segments, r), body[i])
        }

        val head = body[0]
        val toPrey = atan2(prey.y - head.y, prey.x - head.x)
        if (tongue > 0.85f) {
            val dir = Offset(cos(toPrey), sin(toPrey))
            val tip = head + dir * (r * 2.3f)
            drawLine(Berry, head, tip, strokeWidth = h * 0.05f, cap = StrokeCap.Round)
            rotate(25f, tip) { drawLine(Berry, tip, tip + dir * (r * 0.5f), h * 0.045f, StrokeCap.Round) }
            rotate(-25f, tip) { drawLine(Berry, tip, tip + dir * (r * 0.5f), h * 0.045f, StrokeCap.Round) }
        }
        drawCircle(Ink, r * 1.35f + outline, head)
        drawCircle(Tangerine, r * 1.35f, head)
        drawEye(head + Offset(r * 0.35f, -r * 0.45f), r * 0.42f, toPrey, closed = blink > 0.95f)

        // The prey: worried, and right to be.
        drawCircle(Ink, r * 1.05f + outline, prey)
        drawCircle(Sky, r * 1.05f, prey)
        drawCircle(Cream, r * 0.3f, prey + Offset(-r * 0.35f, -r * 0.35f))
        drawCircle(Ink, r * 0.14f, prey + Offset(r * 0.05f, 0f))
        drawCircle(Ink, r * 0.14f, prey + Offset(r * 0.45f, 0f))
    }
}

private fun segRadius(i: Int, n: Int, r: Float) = r * (0.55f + 0.45f * (1f - i.toFloat() / n))

private fun DrawScope.drawEye(at: Offset, radius: Float, look: Float, closed: Boolean) {
    if (closed) {
        drawLine(Ink, at - Offset(radius, 0f), at + Offset(radius, 0f), strokeWidth = radius * 0.5f, cap = StrokeCap.Round)
        return
    }
    drawCircle(Cream, radius, at)
    drawCircle(Ink, radius * 0.55f, at + Offset(cos(look), sin(look)) * (radius * 0.35f))
}

/**
 * The menu backdrop: a night jungle drawn in flat layers — swaying leaves at the
 * edges, rolling hills, and fireflies drifting through the middle.
 *
 * With [framingOnly] it draws just the leaf frame, reskinned for [levelId]: neon-edged in
 * Neon Drift, lush in Neon Grove, sea fronds in Abyssal Chase, frosted in Crystal Tundra,
 * charred with lava rims in Crucible Core. The colours glide from one world to the next.
 */
@Composable
fun JungleBackdrop(modifier: Modifier = Modifier, framingOnly: Boolean = false, levelId: Int? = null) {
    val loop = rememberInfiniteTransition(label = "jungle")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "t")
    val sway by loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(4200), RepeatMode.Reverse), label = "sway")
    val pulse by loop.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse")

    val skin = leafSkin(levelId)
    val glide = tween<Color>(700)
    val dark by animateColorAsState(skin.dark, glide, label = "dark")
    val mid by animateColorAsState(skin.mid, glide, label = "mid")
    val light by animateColorAsState(skin.light, glide, label = "light")
    val rim by animateColorAsState(skin.rim ?: Color.Transparent, glide, label = "rim")

    Canvas(modifier.fillMaxSize()) {
        val m = size.minDimension
        if (!framingOnly) {
            drawRect(JungleNight)
            drawOval(JungleDeep, Offset(-size.width * 0.15f, size.height * 0.72f), Size(size.width * 0.75f, size.height * 0.6f))
            drawOval(JungleDeep, Offset(size.width * 0.45f, size.height * 0.78f), Size(size.width * 0.75f, size.height * 0.55f))
            drawOval(Jungle, Offset(size.width * 0.12f, size.height * 0.86f), Size(size.width * 0.8f, size.height * 0.5f))
        }

        for (leaf in LEAVES) {
            val anchor = Offset(leaf.x * size.width, leaf.y * size.height)
            val fill = when (leaf.tone) {
                0 -> dark
                1 -> mid
                else -> light
            }
            translate(anchor.x, anchor.y) {
                rotate(leaf.angle + sway * leaf.swayDeg, pivot = Offset.Zero) {
                    drawLeaf(leaf.length * m, fill, rim.copy(alpha = rim.alpha * pulse), skin.frosted)
                }
            }
        }

        // Fireflies only on the plain jungle; each level brings its own particles.
        if (!framingOnly) {
            for (i in 0 until 18) {
                val seedX = fract(sin(i * 12.9898f) * 43758.547f)
                val seedY = fract(sin(i * 78.233f) * 12543.12f)
                val ph = t * TAU + i
                val x = size.width * (0.12f + 0.76f * seedX) + m * 0.03f * sin(ph)
                val y = size.height * (0.15f + 0.65f * seedY) + m * 0.025f * cos(ph * 1.3f)
                val glow = 0.45f + 0.55f * (0.5f + 0.5f * sin(ph * 2f))
                drawCircle(Lime.copy(alpha = 0.12f * glow), m * 0.016f, Offset(x, y))
                drawCircle(Lime.copy(alpha = glow), m * 0.005f, Offset(x, y))
            }
        }
    }
}

private fun fract(v: Float) = v - kotlin.math.floor(v)

/** How the leaf frame looks in each world. [rim] glows along the leaf edges. */
private class LeafSkin(val dark: Color, val mid: Color, val light: Color, val rim: Color? = null, val frosted: Boolean = false)

private fun leafSkin(levelId: Int?): LeafSkin = when (levelId) {
    1 -> LeafSkin(Color(0xFF14123A), Color(0xFF1F1B4C), Color(0xFF2B2563), rim = Color(0xFF55E6FF))
    2 -> LeafSkin(Color(0xFF1C5A2C), Color(0xFF2B7F3B), Color(0xFF45A548), rim = Color(0xFF8CFF5A))
    3 -> LeafSkin(Color(0xFF0B3E4E), Color(0xFF0F5867), Color(0xFF167A82), rim = Color(0xFF4FE0F0))
    4 -> LeafSkin(Color(0xFF8FA9C4), Color(0xFFB8CCE0), Color(0xFFDCE8F4), frosted = true)
    5 -> LeafSkin(Color(0xFF1C0D0A), Color(0xFF29130F), Color(0xFF381913), rim = Color(0xFFFF8A3D))
    else -> LeafSkin(Jungle, Leaf, LeafLight)
}

/** [tone]: 0 dark, 1 mid, 2 light. */
private class LeafSpec(val x: Float, val y: Float, val angle: Float, val length: Float, val tone: Int, val swayDeg: Float = 3f)

private val LEAVES = listOf(
    // top-left cluster
    LeafSpec(-0.02f, -0.04f, 35f, 0.42f, 0),
    LeafSpec(-0.03f, 0.1f, 12f, 0.36f, 1),
    LeafSpec(0.04f, -0.05f, 62f, 0.3f, 2, 4f),
    // top-right cluster
    LeafSpec(1.02f, -0.04f, 148f, 0.4f, 0),
    LeafSpec(1.03f, 0.14f, 172f, 0.32f, 1),
    LeafSpec(0.95f, -0.06f, 118f, 0.28f, 2, 4f),
    // bottom corners
    LeafSpec(-0.03f, 1.04f, -28f, 0.38f, 1, 2f),
    LeafSpec(0.03f, 1.06f, -58f, 0.3f, 2, 2.5f),
    LeafSpec(1.03f, 1.04f, 208f, 0.36f, 1, 2f),
    LeafSpec(0.97f, 1.06f, 238f, 0.28f, 2, 2.5f),
)

/** A leaf pointing along +x from the origin: ink outline, optional glowing rim, optional snow. */
private fun DrawScope.drawLeaf(length: Float, color: Color, rim: Color, frosted: Boolean) {
    val l = length
    val path = Path().apply {
        moveTo(0f, 0f)
        cubicTo(l * 0.3f, -l * 0.34f, l * 0.75f, -l * 0.28f, l, 0f)
        cubicTo(l * 0.75f, l * 0.28f, l * 0.3f, l * 0.34f, 0f, 0f)
        close()
    }
    if (rim.alpha > 0.01f) drawPath(path, rim.copy(alpha = rim.alpha * 0.3f), style = Stroke(width = l * 0.08f))
    drawPath(path, Ink, style = Stroke(width = l * 0.03f))
    drawPath(path, color)
    if (rim.alpha > 0.01f) drawPath(path, rim.copy(alpha = rim.alpha * 0.9f), style = Stroke(width = l * 0.012f))
    if (frosted) {
        // A cap of snow along the upper edge, and a few sparkles.
        val snow = Path().apply {
            moveTo(l * 0.18f, -l * 0.14f)
            cubicTo(l * 0.4f, -l * 0.3f, l * 0.7f, -l * 0.26f, l * 0.93f, -l * 0.04f)
            cubicTo(l * 0.7f, -l * 0.17f, l * 0.42f, -l * 0.19f, l * 0.18f, -l * 0.14f)
            close()
        }
        drawPath(snow, Color.White)
    }
    drawLine(
        if (rim.alpha > 0.01f) rim.copy(alpha = rim.alpha * 0.55f) else Ink.copy(alpha = 0.45f),
        Offset(l * 0.04f, 0f),
        Offset(l * 0.9f, 0f),
        strokeWidth = l * 0.018f,
        cap = StrokeCap.Round,
    )
}
