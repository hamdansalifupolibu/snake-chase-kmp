package com.snakechase.app.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * World identity rule (see docs/WORLD_IDENTITY.md): every world has its own obstacles and
 * creatures. Mechanics may repeat between worlds (a wall, a spinning bar, a falling hazard),
 * but each world draws them in its own material, so nothing looks borrowed from another world.
 *
 * Level 1 (Neon Drift) owns the original neon look; the other worlds are drawn here.
 */
enum class WorldStyle {
    NEON,
    GROVE,
    ABYSS,
    TUNDRA,
    CRUCIBLE,
    ;

    companion object {
        fun forLevel(levelId: Int): WorldStyle = when (levelId) {
            2 -> GROVE
            3 -> ABYSS
            4 -> TUNDRA
            5 -> CRUCIBLE
            else -> NEON
        }
    }
}

private val Bark = Color(0xFF5A3A22)
private val BarkDark = Color(0xFF3A2414)
private val Moss = Color(0xFF5FA845)
private val Leaf = Color(0xFF7ED957)
private val Ice = Color(0xFFBFE9FF)
private val IceDeep = Color(0xFF5BB8E8)
private val Frost = Color(0xFFF2FBFF)
private val Steel = Color(0xFF3B4A5A)
private val Rust = Color(0xFF8A5A3A)
private val Iron = Color(0xFF2A1A16)
private val Molten = Color(0xFFFF7A1A)

private fun DrawScope.o(x: Float, y: Float) = Offset(x, y)

// ─── Walls ──────────────────────────────────────────────────────────────────

/** Grove: a fallen mossy log. */
internal fun DrawScope.drawLogWall(r: Rect) {
    val horizontal = r.width >= r.height
    val corner = CornerRadius(minOf(r.width, r.height) / 2f)
    drawRoundRect(Color(0x55000000), o(r.left + 4f, r.top + 5f), Size(r.width, r.height), corner)
    drawRoundRect(
        Brush.linearGradient(listOf(Bark, BarkDark), o(r.left, r.top), if (horizontal) o(r.left, r.bottom) else o(r.right, r.top)),
        o(r.left, r.top),
        Size(r.width, r.height),
        corner,
    )
    // Bark grain along the log.
    val lines = 3
    for (i in 1..lines) {
        val f = i / (lines + 1f)
        if (horizontal) {
            val y = r.top + r.height * f
            drawLine(BarkDark, o(r.left + 10f, y), o(r.right - 10f, y), strokeWidth = 1.6f)
        } else {
            val x = r.left + r.width * f
            drawLine(BarkDark, o(x, r.top + 10f), o(x, r.bottom - 10f), strokeWidth = 1.6f)
        }
    }
    // Moss along the top edge and a few leaves.
    if (horizontal) {
        drawRoundRect(Moss.copy(alpha = 0.85f), o(r.left + 6f, r.top - 2f), Size(r.width - 12f, r.height * 0.28f), CornerRadius(6f))
    } else {
        drawRoundRect(Moss.copy(alpha = 0.85f), o(r.left - 2f, r.top + 6f), Size(r.width * 0.28f, r.height - 12f), CornerRadius(6f))
    }
    val n = ((if (horizontal) r.width else r.height) / 46f).toInt().coerceAtLeast(1)
    for (i in 0 until n) {
        val t = (i + 0.5f) / n
        val c = if (horizontal) o(r.left + r.width * t, r.top) else o(r.left, r.top + r.height * t)
        drawCircle(Leaf, 4.5f, c)
    }
    // Cut rings at the ends.
    val ring = minOf(r.width, r.height) * 0.32f
    val ends = if (horizontal) listOf(o(r.left + ring + 2f, r.centerY), o(r.right - ring - 2f, r.centerY)) else listOf(o(r.centerX, r.top + ring + 2f), o(r.centerX, r.bottom - ring - 2f))
    for (e in ends) {
        drawCircle(Color(0xFFB88A5A), ring, e)
        drawCircle(BarkDark, ring * 0.6f, e, style = Stroke(1.4f))
        drawCircle(BarkDark, ring * 0.25f, e, style = Stroke(1.2f))
    }
}

/** Tundra: a slab of clear blue ice with facets. */
internal fun DrawScope.drawIceSlab(r: Rect, corner: Float = 6f) {
    drawRoundRect(Color(0x44000814), o(r.left + 3f, r.top + 5f), Size(r.width, r.height), CornerRadius(corner))
    drawRoundRect(
        Brush.linearGradient(listOf(Frost.copy(alpha = 0.95f), Ice, IceDeep), o(r.left, r.top), o(r.right, r.bottom)),
        o(r.left, r.top),
        Size(r.width, r.height),
        CornerRadius(corner),
    )
    // Facet lines.
    val facet = Color.White.copy(alpha = 0.55f)
    drawLine(facet, o(r.left + r.width * 0.15f, r.top), o(r.left + r.width * 0.45f, r.bottom), strokeWidth = 1.4f)
    drawLine(facet, o(r.left + r.width * 0.55f, r.top), o(r.left + r.width * 0.85f, r.bottom), strokeWidth = 1.1f)
    drawLine(facet, o(r.left, r.top + r.height * 0.6f), o(r.right, r.top + r.height * 0.3f), strokeWidth = 1f)
    drawRoundRect(Color.White.copy(alpha = 0.9f), o(r.left, r.top), Size(r.width, r.height), CornerRadius(corner), style = Stroke(1.8f))
    // Snow cap.
    drawRoundRect(Frost, o(r.left + 2f, r.top - 2f), Size(r.width - 4f, minOf(7f, r.height * 0.3f)), CornerRadius(4f))
}

// ─── Blocks ─────────────────────────────────────────────────────────────────

internal fun DrawScope.drawWorldBlock(style: WorldStyle, block: Obstacle.MovingBlock) {
    val r = Rect(block.position.x - block.size.x / 2f, block.position.y - block.size.y / 2f, block.size.x, block.size.y)
    when (style) {
        WorldStyle.TUNDRA -> drawIceSlab(r, corner = 10f)
        else -> {
            // Grove: a rolling mossy boulder.
            val c = o(r.centerX, r.centerY)
            val rad = minOf(r.width, r.height) / 2f
            drawCircle(Color(0x55000000), rad * 1.02f, o(c.x + 4f, c.y + 6f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF9A9A8A), Color(0xFF5C5C50)), o(c.x - rad * 0.3f, c.y - rad * 0.3f), rad * 1.4f), rad, c)
            drawCircle(Color(0xFF4A4A40), rad * 0.18f, o(c.x + rad * 0.3f, c.y + rad * 0.2f))
            drawCircle(Color(0xFF4A4A40), rad * 0.12f, o(c.x - rad * 0.35f, c.y + rad * 0.35f))
            // Moss cap.
            drawArc(Moss, 200f, 140f, true, o(c.x - rad, c.y - rad), Size(rad * 2f, rad * 2f))
            drawCircle(Leaf, 4f, o(c.x - rad * 0.4f, c.y - rad * 0.82f))
            drawCircle(Leaf, 3.5f, o(c.x + rad * 0.35f, c.y - rad * 0.86f))
        }
    }
}

// ─── Spinning bars ──────────────────────────────────────────────────────────

internal fun DrawScope.drawWorldBar(style: WorldStyle, bar: Obstacle.RotatingBar) {
    val c = o(bar.center.x, bar.center.y)
    val half = bar.length / 2f
    val t = bar.barThickness
    rotate(degrees = bar.currentAngleRad * 180f / PI.toFloat(), pivot = c) {
        val a = o(c.x - half, c.y)
        val b = o(c.x + half, c.y)
        drawLine(Color(0x55000000), o(a.x + 3f, a.y + 5f), o(b.x + 3f, b.y + 5f), strokeWidth = t + 2f, cap = StrokeCap.Round)
        when (style) {
            WorldStyle.GROVE -> {
                // A swinging jungle branch with leaves.
                drawLine(Bark, a, b, strokeWidth = t, cap = StrokeCap.Round)
                drawLine(BarkDark, o(a.x + 6f, c.y + t * 0.18f), o(b.x - 6f, c.y + t * 0.18f), strokeWidth = 1.5f)
                val n = (bar.length / 34f).toInt()
                for (i in 0..n) {
                    val x = a.x + bar.length * i / n.coerceAtLeast(1)
                    val side = if (i % 2 == 0) -1f else 1f
                    drawOval(Leaf, o(x - 7f, c.y + side * (t / 2f) - 4f + side * 3f), Size(14f, 8f))
                }
            }
            WorldStyle.ABYSS -> {
                // A sunken anchor chain swinging in the current.
                val links = (bar.length / (t * 1.3f)).toInt().coerceAtLeast(3)
                for (i in 0 until links) {
                    val x = a.x + bar.length * (i + 0.5f) / links
                    val w = bar.length / links * 1.15f
                    val h = if (i % 2 == 0) t * 1.15f else t * 0.55f
                    drawRoundRect(if (i % 2 == 0) Steel else Rust, o(x - w / 2f, c.y - h / 2f), Size(w, h), CornerRadius(h / 2f), style = Stroke(3.4f))
                }
                drawCircle(Color(0xFF8FD3FF).copy(alpha = 0.5f), 3f, o(b.x - 10f, c.y - t))
            }
            WorldStyle.TUNDRA -> {
                // A spinning icicle spear, sharp at both ends.
                val p = Path().apply {
                    moveTo(a.x - 6f, c.y)
                    lineTo(c.x, c.y - t / 2f - 2f)
                    lineTo(b.x + 6f, c.y)
                    lineTo(c.x, c.y + t / 2f + 2f)
                    close()
                }
                drawPath(p, Brush.linearGradient(listOf(Frost, Ice, IceDeep), o(c.x, c.y - t), o(c.x, c.y + t)))
                drawLine(Color.White.copy(alpha = 0.85f), o(a.x + 4f, c.y - 1f), o(b.x - 4f, c.y - 1f), strokeWidth = 1.2f)
            }
            else -> {
                // Crucible: a molten iron girder with glowing seams.
                drawLine(Iron, a, b, strokeWidth = t, cap = StrokeCap.Round)
                drawLine(Molten.copy(alpha = 0.35f), a, b, strokeWidth = t + 8f, cap = StrokeCap.Round)
                drawLine(Molten, o(a.x + 4f, c.y), o(b.x - 4f, c.y), strokeWidth = 2.2f, cap = StrokeCap.Round)
                val n = (bar.length / 28f).toInt()
                for (i in 1 until n) {
                    val x = a.x + bar.length * i / n
                    drawLine(Color(0xFFFFC040), o(x, c.y - t / 2f + 1f), o(x, c.y + t / 2f - 1f), strokeWidth = 1.4f)
                }
            }
        }
        val hub = when (style) {
            WorldStyle.TUNDRA -> Frost
            WorldStyle.GROVE -> BarkDark
            else -> Steel
        }
        drawCircle(hub, t * 0.55f, c)
    }
}

// ─── Tundra-only creatures and hazards ──────────────────────────────────────

/** Tundra's falling hazard: an icicle drops from the glacier roof and shatters. */
internal fun DrawScope.drawIcicleDrop(ember: Obstacle.FallingEmber) {
    val c = o(ember.targetPos.x, ember.targetPos.y)
    val tWarn = (ember.ageMs.toFloat() / ember.lethalAtMs.toFloat()).coerceIn(0f, 1f)
    drawCircle(Color(0x33001A33), ember.impactRadius * 1.1f, c)
    if (!ember.isLethal) {
        // Growing frost ring plus the icicle's shadow, falling in.
        val ring = ember.warnRadius + tWarn * 10f
        drawCircle(Ice.copy(alpha = 0.25f + 0.7f * tWarn), ring, c, style = Stroke(2f + 2.4f * tWarn))
        drawCircle(IceDeep.copy(alpha = 0.18f * tWarn), ember.warnRadius * tWarn, c)
        val drop = (1f - tWarn) * 90f
        val tip = o(c.x, c.y - drop)
        val p = Path().apply {
            moveTo(tip.x - 7f, tip.y - 34f)
            lineTo(tip.x + 7f, tip.y - 34f)
            lineTo(tip.x, tip.y)
            close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Frost, Ice, IceDeep), tip.y - 34f, tip.y))
    } else {
        // Shattered: a star of ice shards and frost dust.
        val hot = ember.ageMs - ember.lethalAtMs
        drawCircle(Brush.radialGradient(listOf(Frost, Ice.copy(alpha = 0.8f), IceDeep.copy(alpha = 0.35f), Color.Transparent), c, ember.impactRadius * 1.2f), ember.impactRadius * 1.2f, c)
        for (i in 0 until 8) {
            val ang = i * PI.toFloat() / 4f + 0.3f
            val len = ember.impactRadius * (if (i % 2 == 0) 0.95f else 0.65f)
            val dir = o(cos(ang), sin(ang))
            val side = o(-dir.y, dir.x)
            val p = Path().apply {
                moveTo(c.x + side.x * 5f, c.y + side.y * 5f)
                lineTo(c.x + dir.x * len, c.y + dir.y * len)
                lineTo(c.x - side.x * 5f, c.y - side.y * 5f)
                close()
            }
            drawPath(p, Ice)
        }
        if (hot < 180L) {
            drawCircle(Color.White.copy(alpha = (1f - hot / 180f) * 0.9f), ember.warnRadius + 18f + hot / 180f * 8f, c, style = Stroke(2.4f))
        }
    }
}

/** Tundra's whirling hazard: a snow whirl, not a water vortex. */
internal fun DrawScope.drawSnowWhirl(cyclone: Obstacle.Cyclone) {
    val c = o(cyclone.position.x, cyclone.position.y)
    val r = cyclone.visualRadius
    val phi = cyclone.spinAngle * cyclone.spinDir
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Ice.copy(alpha = 0.18f), Color.Transparent), c, r), r, c)
    for (arm in 0 until 4) {
        val path = Path()
        for (s in 0..24) {
            val f = s / 24f
            val ang = phi + arm * PI.toFloat() / 2f + f * 3.4f * cyclone.spinDir
            val rad = r * (0.12f + 0.86f * f)
            val x = c.x + cos(ang) * rad
            val y = c.y + sin(ang) * rad
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Frost.copy(alpha = 0.7f), style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
    // Snowflakes riding the whirl.
    for (k in 0 until 14) {
        val f = ((k * 0.37f) % 1f)
        val ang = phi * (1.6f - f) + k * 1.9f
        val rad = r * (0.2f + 0.75f * f)
        drawCircle(Color.White, 2.2f + (k % 3), o(c.x + cos(ang) * rad, c.y + sin(ang) * rad))
    }
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.1f, c)
}

/** Tundra's creature: a frostling, a round snow critter that waddles across the ice. */
internal fun DrawScope.drawFrostling(critter: Obstacle.Isopod) {
    val c = o(critter.position.x, critter.position.y)
    val r = critter.radius
    val bob = sin(critter.legPhase) * 2f
    drawOval(Color(0x44001A33), o(c.x - r, c.y + r * 0.6f), Size(r * 2f, r * 0.6f))
    // Feet.
    val step = sin(critter.legPhase) * r * 0.18f
    drawOval(IceDeep, o(c.x - r * 0.6f + step, c.y + r * 0.62f), Size(r * 0.5f, r * 0.3f))
    drawOval(IceDeep, o(c.x + r * 0.1f - step, c.y + r * 0.62f), Size(r * 0.5f, r * 0.3f))
    // Fluffy body: overlapping puffs.
    val body = o(c.x, c.y + bob)
    for (i in 0 until 8) {
        val ang = i * PI.toFloat() / 4f
        drawCircle(Frost, r * 0.42f, o(body.x + cos(ang) * r * 0.55f, body.y + sin(ang) * r * 0.5f))
    }
    drawCircle(Brush.radialGradient(listOf(Color.White, Ice), o(body.x - r * 0.2f, body.y - r * 0.3f), r), r * 0.78f, body)
    // Ice horns, eyes facing the walking direction.
    val dir = o(cos(critter.angleRad), sin(critter.angleRad))
    val horn = Path().apply {
        moveTo(body.x - r * 0.45f, body.y - r * 0.55f)
        lineTo(body.x - r * 0.3f, body.y - r * 1.05f)
        lineTo(body.x - r * 0.15f, body.y - r * 0.6f)
        moveTo(body.x + r * 0.15f, body.y - r * 0.6f)
        lineTo(body.x + r * 0.3f, body.y - r * 1.05f)
        lineTo(body.x + r * 0.45f, body.y - r * 0.55f)
    }
    drawPath(horn, IceDeep)
    val eye = o(body.x + dir.x * r * 0.25f, body.y - r * 0.1f + dir.y * r * 0.15f)
    drawCircle(Color(0xFF0B2A44), r * 0.12f, o(eye.x - r * 0.22f, eye.y))
    drawCircle(Color(0xFF0B2A44), r * 0.12f, o(eye.x + r * 0.22f, eye.y))
    drawCircle(Color.White, r * 0.04f, o(eye.x - r * 0.19f, eye.y - r * 0.04f))
    drawCircle(Color.White, r * 0.04f, o(eye.x + r * 0.25f, eye.y - r * 0.04f))
    drawCircle(Color(0xFF7FC8F0), r * 0.08f, o(eye.x, eye.y + r * 0.2f))
}

/** Tundra's danger zone: thin, cracking ice over black water. */
internal fun DrawScope.drawThinIce(zone: Obstacle.DangerZone) {
    val r = zone.rect
    val pulse = 0.5f + 0.5f * sin(zone.pulsePhase)
    drawRoundRect(Color(0xFF06243A).copy(alpha = 0.55f), o(r.left, r.top), Size(r.width, r.height), CornerRadius(14f))
    drawRoundRect(Ice.copy(alpha = 0.25f + 0.15f * pulse), o(r.left, r.top), Size(r.width, r.height), CornerRadius(14f))
    val crack = Color.White.copy(alpha = 0.65f + 0.3f * pulse)
    val cx = r.centerX
    val cy = r.centerY
    for (i in 0 until 6) {
        val ang = i * PI.toFloat() / 3f + 0.4f
        val mid = o(cx + cos(ang) * r.width * 0.2f, cy + sin(ang) * r.height * 0.22f)
        val end = o(cx + cos(ang + 0.25f) * r.width * 0.45f, cy + sin(ang + 0.25f) * r.height * 0.45f)
        drawLine(crack, o(cx, cy), mid, strokeWidth = 1.6f)
        drawLine(crack, mid, end, strokeWidth = 1.1f)
    }
    drawRoundRect(Frost.copy(alpha = 0.8f), o(r.left, r.top), Size(r.width, r.height), CornerRadius(14f), style = Stroke(2f))
}

/** Portals keep their shared gate, framed in the world's own material (leaves or ice crystals). */
internal fun DrawScope.drawPortalTrim(style: WorldStyle, tele: Obstacle.Teleporter) {
    if (style != WorldStyle.GROVE && style != WorldStyle.TUNDRA) return
    for ((pos, phase) in listOf(tele.posA to tele.rotationPhaseA, tele.posB to tele.rotationPhaseB)) {
        val c = o(pos.x, pos.y)
        val n = 10
        for (i in 0 until n) {
            val ang = phase * 0.4f + i * 2f * PI.toFloat() / n
            val p = o(c.x + cos(ang) * (tele.radius + 6f), c.y + sin(ang) * (tele.radius + 6f))
            if (style == WorldStyle.GROVE) {
                drawCircle(if (i % 2 == 0) Leaf else Moss, 5f, p)
            } else {
                val d = Path().apply {
                    moveTo(p.x, p.y - 6f)
                    lineTo(p.x + 3.5f, p.y)
                    lineTo(p.x, p.y + 6f)
                    lineTo(p.x - 3.5f, p.y)
                    close()
                }
                drawPath(d, Frost)
            }
        }
    }
}
