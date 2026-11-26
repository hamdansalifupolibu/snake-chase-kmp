package com.snakechase.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The game's icon set, drawn in code on a 24×24 grid. Replaces emoji, which render
 * differently on every platform (and as empty boxes on the web).
 */
enum class ArcadeIcon {
    Gear,
    Info,
    Trophy,
    Book,
    Palette,
    Scale,
    Coin,
    Lock,
    Star,
    Swords,
    Calendar,
    Target,
    Play,
    User,
    Back,
    Close,
    Pause,
    Flame,
    Shield,
    Bolt,
    Home,
    Refresh,
    Share,
    Chat,
    Globe,
    Users,
    Robot,
    Link,
    Crown,
    Eye,
    Check,
    Wifi,
    Heart,
}

@Composable
fun ArcadeIconView(icon: ArcadeIcon, tint: Color = Ink, size: Dp, modifier: Modifier = Modifier) {
    // Offscreen so BlendMode.Clear punches real holes (gear hub, keyhole…) whatever the background.
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) { drawArcadeIcon(icon, tint) }
    }
}

private fun p(x: Float, y: Float) = Offset(x, y)

private fun DrawScope.hole(center: Offset, radius: Float) = drawCircle(Color.Black, radius, center, blendMode = BlendMode.Clear)

private fun DrawScope.line(c: Color, a: Offset, b: Offset, w: Float) = drawLine(c, a, b, strokeWidth = w, cap = StrokeCap.Round)

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float) = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawArcadeIcon(icon: ArcadeIcon, c: Color) {
    val center = p(12f, 12f)
    when (icon) {
        ArcadeIcon.Gear -> {
            for (i in 0 until 8) {
                rotate(i * 45f, pivot = center) {
                    drawRoundRect(c, p(10f, 1.5f), Size(4f, 5f), CornerRadius(1f))
                }
            }
            drawCircle(c, 8f, center)
            hole(center, 3.2f)
        }
        ArcadeIcon.Info -> {
            drawCircle(c, 10.5f, center)
            hole(p(12f, 7.2f), 1.8f)
            drawRoundRect(Color.Black, p(10.4f, 10.4f), Size(3.2f, 7.6f), CornerRadius(1.6f), blendMode = BlendMode.Clear)
        }
        ArcadeIcon.Trophy -> {
            val cup = Path().apply {
                moveTo(6f, 3f)
                lineTo(18f, 3f)
                lineTo(18f, 8f)
                cubicTo(18f, 12f, 15f, 14.5f, 12f, 14.5f)
                cubicTo(9f, 14.5f, 6f, 12f, 6f, 8f)
                close()
            }
            drawPath(cup, c)
            drawCircle(c, 3f, p(5f, 7.5f), style = Stroke(2f))
            drawCircle(c, 3f, p(19f, 7.5f), style = Stroke(2f))
            drawRect(c, p(10.8f, 14f), Size(2.4f, 4.5f))
            drawRoundRect(c, p(7f, 18f), Size(10f, 3.5f), CornerRadius(1.2f))
        }
        ArcadeIcon.Book -> {
            drawRoundRect(c, p(2f, 4f), Size(9.3f, 16f), CornerRadius(2f))
            drawRoundRect(c, p(12.7f, 4f), Size(9.3f, 16f), CornerRadius(2f))
            drawLine(Color.Black, p(4.5f, 8.5f), p(9f, 8.5f), 1.4f, StrokeCap.Round, blendMode = BlendMode.Clear)
            drawLine(Color.Black, p(4.5f, 12f), p(9f, 12f), 1.4f, StrokeCap.Round, blendMode = BlendMode.Clear)
            drawLine(Color.Black, p(15f, 8.5f), p(19.5f, 8.5f), 1.4f, StrokeCap.Round, blendMode = BlendMode.Clear)
        }
        ArcadeIcon.Palette -> {
            val blob = Path().apply {
                moveTo(12f, 2f)
                cubicTo(18f, 2f, 22.5f, 6f, 22.5f, 11f)
                cubicTo(22.5f, 15f, 19f, 16f, 16.5f, 15.5f)
                cubicTo(14f, 15f, 13f, 17f, 14f, 19f)
                cubicTo(15f, 21f, 13.5f, 22.5f, 11.5f, 22.5f)
                cubicTo(5.5f, 22.5f, 1.5f, 17.5f, 1.5f, 12f)
                cubicTo(1.5f, 6.5f, 6f, 2f, 12f, 2f)
                close()
            }
            drawPath(blob, c)
            hole(p(7f, 9f), 1.9f)
            hole(p(12f, 6.5f), 1.9f)
            hole(p(17f, 9f), 1.9f)
            hole(p(6.5f, 14.5f), 1.9f)
        }
        ArcadeIcon.Scale -> {
            drawRect(c, p(11f, 4f), Size(2f, 15f))
            drawRoundRect(c, p(6.5f, 19f), Size(11f, 2.8f), CornerRadius(1.2f))
            drawRoundRect(c, p(3f, 5.2f), Size(18f, 2.4f), CornerRadius(1.2f))
            drawCircle(c, 2f, p(12f, 3.5f))
            for (x in listOf(5.5f, 18.5f)) {
                line(c, p(x, 7f), p(x - 3f, 13f), 1.3f)
                line(c, p(x, 7f), p(x + 3f, 13f), 1.3f)
                drawArc(c, 0f, 180f, true, p(x - 4f, 10.5f), Size(8f, 6f))
            }
        }
        ArcadeIcon.Coin -> {
            // Always a gold coin, whatever the tint: currency must read instantly.
            drawCircle(Ink, 11.5f, center)
            drawCircle(Banana, 9.3f, center)
            drawCircle(Color(0xFFF0B429), 6.8f, center)
            val bolt = Path().apply {
                moveTo(13.2f, 6f)
                lineTo(8.5f, 12.8f)
                lineTo(11.6f, 12.8f)
                lineTo(10.6f, 18f)
                lineTo(15.4f, 11f)
                lineTo(12.3f, 11f)
                close()
            }
            drawPath(bolt, Ink)
        }
        ArcadeIcon.Lock -> {
            drawArc(c, 180f, 180f, false, p(7.2f, 3f), Size(9.6f, 10f), style = Stroke(2.8f))
            drawRect(c, p(5.8f, 8f), Size(2.8f, 3f))
            drawRect(c, p(15.4f, 8f), Size(2.8f, 3f))
            drawRoundRect(c, p(4.5f, 10f), Size(15f, 12f), CornerRadius(2.5f))
            hole(p(12f, 15f), 2f)
            drawRect(Color.Black, p(11.2f, 15f), Size(1.6f, 3.6f), blendMode = BlendMode.Clear)
        }
        ArcadeIcon.Star -> drawPath(starPath(12f, 12.8f, 11f, 4.8f), c)
        ArcadeIcon.Swords -> {
            for (angle in listOf(45f, -45f)) {
                rotate(angle, pivot = center) {
                    drawRoundRect(c, p(10.9f, 1f), Size(2.2f, 15f), CornerRadius(1.1f))
                    drawRoundRect(c, p(7.5f, 15f), Size(9f, 2.4f), CornerRadius(1.2f))
                    drawRoundRect(c, p(10.8f, 17f), Size(2.4f, 5f), CornerRadius(1f))
                }
            }
        }
        ArcadeIcon.Calendar -> {
            drawRoundRect(c, p(2.5f, 4.5f), Size(19f, 17f), CornerRadius(3f))
            drawRoundRect(Color.Black, p(4.8f, 10f), Size(14.4f, 9.2f), CornerRadius(1.5f), blendMode = BlendMode.Clear)
            drawRoundRect(c, p(6.5f, 11.8f), Size(4f, 3.6f), CornerRadius(0.8f))
            drawRoundRect(c, p(6.5f, 2f), Size(2.6f, 5f), CornerRadius(1.3f))
            drawRoundRect(c, p(14.9f, 2f), Size(2.6f, 5f), CornerRadius(1.3f))
        }
        ArcadeIcon.Target -> {
            drawCircle(c, 8f, center, style = Stroke(2.6f))
            drawCircle(c, 2.8f, center)
            line(c, p(12f, 1f), p(12f, 6f), 2.6f)
            line(c, p(12f, 18f), p(12f, 23f), 2.6f)
            line(c, p(1f, 12f), p(6f, 12f), 2.6f)
            line(c, p(18f, 12f), p(23f, 12f), 2.6f)
        }
        ArcadeIcon.Play -> {
            val tri = Path().apply {
                moveTo(7f, 4f)
                lineTo(20f, 12f)
                lineTo(7f, 20f)
                close()
            }
            drawPath(tri, c, style = Stroke(3f, join = StrokeJoin.Round))
            drawPath(tri, c)
        }
        ArcadeIcon.User -> {
            drawCircle(c, 4.8f, p(12f, 7.5f))
            drawArc(c, 180f, 180f, true, p(3.5f, 13.5f), Size(17f, 16f))
        }
        ArcadeIcon.Back -> {
            line(c, p(19f, 12f), p(6f, 12f), 3.2f)
            line(c, p(11f, 6f), p(5f, 12f), 3.2f)
            line(c, p(11f, 18f), p(5f, 12f), 3.2f)
        }
        ArcadeIcon.Close -> {
            line(c, p(6f, 6f), p(18f, 18f), 3.4f)
            line(c, p(18f, 6f), p(6f, 18f), 3.4f)
        }
        ArcadeIcon.Pause -> {
            drawRoundRect(c, p(6f, 4f), Size(4.2f, 16f), CornerRadius(1.6f))
            drawRoundRect(c, p(13.8f, 4f), Size(4.2f, 16f), CornerRadius(1.6f))
        }
        ArcadeIcon.Flame -> {
            val flame = Path().apply {
                moveTo(12f, 1.5f)
                cubicTo(14f, 6f, 20f, 9f, 20f, 15f)
                cubicTo(20f, 19.5f, 16.5f, 22.5f, 12f, 22.5f)
                cubicTo(7.5f, 22.5f, 4f, 19.5f, 4f, 15f)
                cubicTo(4f, 11f, 7f, 9f, 8f, 6f)
                cubicTo(9f, 8.5f, 10f, 9.5f, 11f, 10f)
                cubicTo(11.5f, 7f, 11f, 4f, 12f, 1.5f)
                close()
            }
            drawPath(flame, c)
            val core = Path().apply {
                moveTo(12f, 12f)
                cubicTo(14.5f, 14.5f, 15.5f, 16f, 15.5f, 17.8f)
                cubicTo(15.5f, 19.8f, 14f, 21f, 12f, 21f)
                cubicTo(10f, 21f, 8.5f, 19.8f, 8.5f, 17.8f)
                cubicTo(8.5f, 15.8f, 10.5f, 14.5f, 12f, 12f)
                close()
            }
            drawPath(core, Color.Black, blendMode = BlendMode.Clear)
        }
        ArcadeIcon.Heart -> {
            val heart = Path().apply {
                moveTo(12f, 21f)
                cubicTo(4f, 15.5f, 1.8f, 11.2f, 2.2f, 7.6f)
                cubicTo(2.6f, 4.3f, 5.2f, 2.6f, 7.7f, 2.8f)
                cubicTo(9.6f, 3f, 11.1f, 4.2f, 12f, 5.8f)
                cubicTo(12.9f, 4.2f, 14.4f, 3f, 16.3f, 2.8f)
                cubicTo(18.8f, 2.6f, 21.4f, 4.3f, 21.8f, 7.6f)
                cubicTo(22.2f, 11.2f, 20f, 15.5f, 12f, 21f)
                close()
            }
            drawPath(heart, c)
        }
        ArcadeIcon.Shield -> {
            val shield = Path().apply {
                moveTo(12f, 1.8f)
                lineTo(20.5f, 5f)
                cubicTo(20.5f, 13f, 17f, 19.5f, 12f, 22.3f)
                cubicTo(7f, 19.5f, 3.5f, 13f, 3.5f, 5f)
                close()
            }
            drawPath(shield, c)
            drawLine(Color.Black, p(8.5f, 12f), p(11f, 14.8f), 2.4f, StrokeCap.Round, blendMode = BlendMode.Clear)
            drawLine(Color.Black, p(11f, 14.8f), p(16f, 8.8f), 2.4f, StrokeCap.Round, blendMode = BlendMode.Clear)
        }
        ArcadeIcon.Bolt -> {
            val bolt = Path().apply {
                moveTo(14f, 1.5f)
                lineTo(4.5f, 13.5f)
                lineTo(11f, 13.5f)
                lineTo(9.5f, 22.5f)
                lineTo(19.5f, 10f)
                lineTo(13f, 10f)
                close()
            }
            drawPath(bolt, c, style = Stroke(1.6f, join = StrokeJoin.Round))
            drawPath(bolt, c)
        }
        ArcadeIcon.Home -> {
            val roof = Path().apply {
                moveTo(12f, 2.5f)
                lineTo(22f, 11.5f)
                lineTo(19f, 11.5f)
                lineTo(19f, 21f)
                lineTo(5f, 21f)
                lineTo(5f, 11.5f)
                lineTo(2f, 11.5f)
                close()
            }
            drawPath(roof, c, style = Stroke(1.6f, join = StrokeJoin.Round))
            drawPath(roof, c)
            drawRoundRect(Color.Black, p(9.8f, 14f), Size(4.4f, 7.2f), CornerRadius(1f), blendMode = BlendMode.Clear)
        }
        ArcadeIcon.Refresh -> {
            drawArc(c, -60f, 290f, false, p(4f, 4f), Size(16f, 16f), style = Stroke(3f, cap = StrokeCap.Round))
            val head = Path().apply {
                moveTo(15.5f, 1.8f)
                lineTo(21.5f, 6.2f)
                lineTo(14.6f, 9.4f)
                close()
            }
            drawPath(head, c)
        }
        ArcadeIcon.Share -> {
            drawCircle(c, 3.4f, p(18f, 5f))
            drawCircle(c, 3.4f, p(6f, 12f))
            drawCircle(c, 3.4f, p(18f, 19f))
            line(c, p(6f, 12f), p(18f, 5f), 2.2f)
            line(c, p(6f, 12f), p(18f, 19f), 2.2f)
        }
        ArcadeIcon.Chat -> {
            drawRoundRect(c, p(2.5f, 3.5f), Size(19f, 13.5f), CornerRadius(4f))
            val tail = Path().apply {
                moveTo(7f, 16f)
                lineTo(6f, 21.5f)
                lineTo(12.5f, 16f)
                close()
            }
            drawPath(tail, c)
            hole(p(8f, 10.2f), 1.4f)
            hole(p(12f, 10.2f), 1.4f)
            hole(p(16f, 10.2f), 1.4f)
        }
        ArcadeIcon.Globe -> {
            drawCircle(c, 10f, center)
            drawOval(Color.Black, p(8.4f, 2.6f), Size(7.2f, 18.8f), style = Stroke(1.6f), blendMode = BlendMode.Clear)
            drawLine(Color.Black, p(2f, 12f), p(22f, 12f), 1.6f, blendMode = BlendMode.Clear)
            drawLine(Color.Black, p(3.6f, 7.5f), p(20.4f, 7.5f), 1.4f, blendMode = BlendMode.Clear)
            drawLine(Color.Black, p(3.6f, 16.5f), p(20.4f, 16.5f), 1.4f, blendMode = BlendMode.Clear)
        }
        ArcadeIcon.Users -> {
            drawCircle(c, 3.6f, p(8.5f, 7.5f))
            drawArc(c, 180f, 180f, true, p(2f, 12.5f), Size(13f, 12f))
            drawCircle(c, 3.2f, p(16.5f, 8.5f))
            drawArc(c, 180f, 180f, true, p(11.5f, 13.5f), Size(11f, 10f))
        }
        ArcadeIcon.Robot -> {
            drawRoundRect(c, p(4f, 7f), Size(16f, 13f), CornerRadius(3.5f))
            drawRect(c, p(11f, 3f), Size(2f, 4.5f))
            drawCircle(c, 1.8f, p(12f, 2.8f))
            hole(p(9f, 12.5f), 1.9f)
            hole(p(15f, 12.5f), 1.9f)
            drawRoundRect(Color.Black, p(8.5f, 16f), Size(7f, 1.8f), CornerRadius(0.9f), blendMode = BlendMode.Clear)
        }
        ArcadeIcon.Link -> {
            rotate(-45f, pivot = center) {
                drawRoundRect(c, p(2.5f, 8.5f), Size(10f, 7f), CornerRadius(3.5f), style = Stroke(2.6f))
                drawRoundRect(c, p(11.5f, 8.5f), Size(10f, 7f), CornerRadius(3.5f), style = Stroke(2.6f))
            }
        }
        ArcadeIcon.Crown -> {
            val crown = Path().apply {
                moveTo(3f, 18f)
                lineTo(2.5f, 7f)
                lineTo(8f, 11.5f)
                lineTo(12f, 4f)
                lineTo(16f, 11.5f)
                lineTo(21.5f, 7f)
                lineTo(21f, 18f)
                close()
            }
            drawPath(crown, c, style = Stroke(1.6f, join = StrokeJoin.Round))
            drawPath(crown, c)
            drawRoundRect(c, p(3f, 19f), Size(18f, 2.8f), CornerRadius(1.2f))
        }
        ArcadeIcon.Eye -> {
            val eye = Path().apply {
                moveTo(1.5f, 12f)
                cubicTo(5f, 5f, 19f, 5f, 22.5f, 12f)
                cubicTo(19f, 19f, 5f, 19f, 1.5f, 12f)
                close()
            }
            drawPath(eye, c)
            hole(center, 4.2f)
            drawCircle(c, 2.4f, center)
        }
        ArcadeIcon.Check -> {
            line(c, p(4.5f, 12.5f), p(9.5f, 17.5f), 3.6f)
            line(c, p(9.5f, 17.5f), p(19.5f, 6.5f), 3.6f)
        }
        ArcadeIcon.Wifi -> {
            drawArc(c, 215f, 110f, false, p(2f, 6f), Size(20f, 20f), style = Stroke(2.8f, cap = StrokeCap.Round))
            drawArc(c, 215f, 110f, false, p(6f, 10f), Size(12f, 12f), style = Stroke(2.8f, cap = StrokeCap.Round))
            drawCircle(c, 2.2f, p(12f, 19f))
        }
    }
}

/** Gold star with an ink outline, for ratings. [filled] false draws an empty socket. */
@Composable
fun RatingStar(filled: Boolean, size: Dp, modifier: Modifier = Modifier, empty: Color = Color(0x33FFFFFF)) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            val star = starPath(12f, 12.8f, 10.5f, 4.6f)
            drawPath(star, Ink, style = Stroke(3.2f, join = StrokeJoin.Round))
            drawPath(star, if (filled) Banana else empty)
        }
    }
}
