package com.snakechase.app.engine

import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Walls, bars and blocks are solid to snakes: a snake steers around them and slides along
 * them instead of passing through, which gives the orb room to escape behind cover.
 * (A bar that swings *into* a snake still stuns it; see GameEngine's concussion check.)
 */
sealed class Solid {
    /** A rounded stick: every point within [radius] of the segment a-b. */
    data class Capsule(val a: Vec2, val b: Vec2, val radius: Float) : Solid()

    data class Box(val rect: Rect) : Solid()

    /** The point of this solid closest to [p] (p itself when p is inside a box). */
    fun closestPoint(p: Vec2): Vec2 = when (this) {
        is Capsule -> {
            val ab = b - a
            val len2 = ab.lengthSquared()
            val t = if (len2 <= 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
            a + ab * t
        }
        is Box -> Vec2(p.x.coerceIn(rect.left, rect.right), p.y.coerceIn(rect.top, rect.bottom))
    }

    /** Signed gap between a circle at [p] with radius [r] and this solid (< 0 = overlapping). */
    fun gap(p: Vec2, r: Float): Float = when (this) {
        is Capsule -> (p - closestPoint(p)).length() - radius - r
        is Box -> {
            val c = closestPoint(p)
            if (c == p) -insideDepth(p) - r else (p - c).length() - r
        }
    }

    private fun insideDepth(p: Vec2): Float = when (this) {
        is Box -> minOf(p.x - rect.left, rect.right - p.x, p.y - rect.top, rect.bottom - p.y)
        is Capsule -> 0f
    }

    /** How far, and which way, to move a circle at [p] (radius [r]) so it just touches the solid. */
    fun pushOut(p: Vec2, r: Float): Vec2? {
        val g = gap(p, r)
        if (g >= 0f) return null
        val c = closestPoint(p)
        val away = p - c
        val n = when {
            away.lengthSquared() > 1e-6f -> away.normalized()
            this is Box -> {
                // Centre inside the box: leave through the nearest side.
                val d = listOf(p.x - rect.left, rect.right - p.x, p.y - rect.top, rect.bottom - p.y)
                when (d.indexOf(d.min())) {
                    0 -> Vec2(-1f, 0f)
                    1 -> Vec2(1f, 0f)
                    2 -> Vec2(0f, -1f)
                    else -> Vec2(0f, 1f)
                }
            }
            else -> Vec2(0f, -1f)
        }
        return n * (-g + SKIN)
    }

    companion object {
        /** Extra clearance so a resting snake doesn't count as touching. */
        const val SKIN = 0.75f

        fun from(obstacles: List<Obstacle>): List<Solid> = obstacles.mapNotNull { obs ->
            if (!obs.isActive) return@mapNotNull null
            when (obs) {
                is Obstacle.Wall -> Box(obs.rect)
                is Obstacle.MovingBlock -> Box(
                    Rect(obs.position.x - obs.size.x / 2f, obs.position.y - obs.size.y / 2f, obs.size.x, obs.size.y),
                )
                is Obstacle.RotatingBar -> {
                    val dir = Vec2.fromAngle(obs.currentAngleRad, 1f)
                    Capsule(obs.center - dir * (obs.length / 2f), obs.center + dir * (obs.length / 2f), obs.barThickness / 2f)
                }
                else -> null
            }
        }

        private fun wrap(a: Float): Float {
            var d = a
            while (d > PI) d -= (2 * PI).toFloat()
            while (d < -PI) d += (2 * PI).toFloat()
            return d
        }

        /**
         * The heading closest to [desiredAngle] whose look-ahead path is clear of every solid.
         * Candidates fan out in 20 degree steps; a small bias toward [currentAngle] keeps the
         * snake committed to one side of an obstacle instead of dithering.
         */
        fun steer(head: Vec2, radius: Float, desiredAngle: Float, currentAngle: Float, probe: Float, solids: List<Solid>): Float {
            if (solids.isEmpty()) return desiredAngle
            val clearance = radius + 4f
            fun clear(angle: Float): Boolean {
                val dir = Vec2.fromAngle(angle, 1f)
                // Sample along the probe so thin bars can't hide between the head and the tip.
                for (k in 1..4) {
                    val pt = head + dir * (probe * k / 4f)
                    if (solids.any { it.gap(pt, clearance) < 0f }) return false
                }
                return true
            }
            if (clear(desiredAngle)) return desiredAngle
            val step = (PI / 9).toFloat()
            var best: Float? = null
            var bestCost = Float.MAX_VALUE
            for (k in 1..9) {
                for (sign in intArrayOf(1, -1)) {
                    val cand = desiredAngle + sign * k * step
                    if (!clear(cand)) continue
                    val cost = abs(wrap(cand - desiredAngle)) + 0.35f * abs(wrap(cand - currentAngle))
                    if (cost < bestCost) {
                        bestCost = cost
                        best = cand
                    }
                }
                if (best != null) break
            }
            return best ?: desiredAngle
        }

        /**
         * Moves a head from [from] towards [to] but never into a solid: overlaps are pushed back
         * out and the motion into the surface is dropped, so the snake glides along it.
         * Returns the resolved position and the heading of the actual motion (or null).
         */
        fun resolve(from: Vec2, to: Vec2, radius: Float, solids: List<Solid>): Pair<Vec2, Float?> {
            if (solids.isEmpty()) return to to null
            var p = to
            var slid = false
            repeat(3) {
                var pushed = false
                for (s in solids) {
                    val push = s.pushOut(p, radius) ?: continue
                    p += push
                    pushed = true
                    slid = true
                }
                if (!pushed) return@repeat
            }
            if (!slid) return p to null
            val moved = p - from
            val heading = if (moved.lengthSquared() > 1e-4f) atan2(moved.y, moved.x) else null
            return p to heading
        }
    }
}
