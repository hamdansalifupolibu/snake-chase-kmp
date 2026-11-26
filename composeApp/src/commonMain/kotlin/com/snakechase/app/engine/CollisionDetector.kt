package com.snakechase.app.engine

import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2
import kotlin.math.max
import kotlin.math.min

object CollisionDetector {

    /**
     * Checks discrete overlap between two circles.
     */
    fun checkCircleOverlap(posA: Vec2, radiusA: Float, posB: Vec2, radiusB: Float): Boolean {
        val totalRadius = radiusA + radiusB
        val dx = posA.x - posB.x
        val dy = posA.y - posB.y
        return (dx * dx + dy * dy) <= (totalRadius * totalRadius)
    }

    /**
     * Continuous Collision Detection (CCD):
     * Checks if moving circle A (from oldA to newA) touches moving circle B (from oldB to newB).
     * Tests swept segment samples to prevent fast-moving tunneling.
     */
    fun checkSweptCircleOverlap(
        oldA: Vec2,
        newA: Vec2,
        radiusA: Float,
        oldB: Vec2,
        newB: Vec2,
        radiusB: Float,
    ): Boolean {
        // First test discrete endpoint
        if (checkCircleOverlap(newA, radiusA, newB, radiusB)) return true
        if (checkCircleOverlap(oldA, radiusA, oldB, radiusB)) return true

        val totalRadius = radiusA + radiusB
        val totalRadiusSq = totalRadius * totalRadius

        // Test 4 sub-steps along the trajectory
        for (i in 1..4) {
            val t = i / 4f
            val sampleA = oldA + (newA - oldA) * t
            val sampleB = oldB + (newB - oldB) * t
            val dx = sampleA.x - sampleB.x
            val dy = sampleA.y - sampleB.y
            if ((dx * dx + dy * dy) <= totalRadiusSq) return true
        }

        // Segment-to-segment distance check
        val dist = distancePointToSegment(newA, oldB, newB)
        if (dist <= totalRadius) return true

        val dist2 = distancePointToSegment(newB, oldA, newA)
        return dist2 <= totalRadius
    }

    /**
     * Calculates distance between two circle edges.
     * Negative value means overlapping.
     */
    fun getCircleEdgeDistance(posA: Vec2, radiusA: Float, posB: Vec2, radiusB: Float): Float = posA.distanceTo(posB) - (radiusA + radiusB)

    /**
     * Checks overlap between a circle and an axis-aligned bounding box (Rect).
     */
    fun checkCircleRectOverlap(circlePos: Vec2, radius: Float, rect: Rect): Boolean {
        val closestX = max(rect.left, min(circlePos.x, rect.right))
        val closestY = max(rect.top, min(circlePos.y, rect.bottom))

        val dx = circlePos.x - closestX
        val dy = circlePos.y - closestY
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    /**
     * Swept circle against Rect.
     */
    fun checkSweptCircleRectOverlap(oldPos: Vec2, newPos: Vec2, radius: Float, rect: Rect): Boolean {
        if (checkCircleRectOverlap(newPos, radius, rect)) return true
        if (checkCircleRectOverlap(oldPos, radius, rect)) return true

        for (i in 1..3) {
            val t = i / 3f
            val sample = oldPos + (newPos - oldPos) * t
            if (checkCircleRectOverlap(sample, radius, rect)) return true
        }
        return false
    }

    /**
     * Checks overlap between a circle and a rotating bar (capsule / line segment with thickness).
     */
    fun checkCircleBarOverlap(
        circlePos: Vec2,
        radius: Float,
        center: Vec2,
        length: Float,
        angleRad: Float,
        barThickness: Float,
    ): Boolean {
        val halfLen = length / 2f
        val dir = Vec2.fromAngle(angleRad, 1f)
        val p1 = center - (dir * halfLen)
        val p2 = center + (dir * halfLen)

        val dist = distancePointToSegment(circlePos, p1, p2)
        return dist <= (radius + barThickness / 2f)
    }

    /**
     * Calculates shortest distance from point P to line segment AB.
     */
    fun distancePointToSegment(p: Vec2, a: Vec2, b: Vec2): Float {
        val ab = b - a
        val ap = p - a
        val abLenSq = ab.lengthSquared()

        if (abLenSq < 0.0001f) return p.distanceTo(a)

        val t = max(0f, min(1f, (ap.x * ab.x + ap.y * ab.y) / abLenSq))
        val projection = a + (ab * t)
        return p.distanceTo(projection)
    }

    /**
     * Calculates shortest distance from point P to a polyline.
     */
    fun distancePointToPolyline(p: Vec2, poly: List<Vec2>): Float {
        if (poly.size < 2) return Float.MAX_VALUE
        var minDist = Float.MAX_VALUE
        for (i in 0 until poly.size - 1) {
            val d = distancePointToSegment(p, poly[i], poly[i + 1])
            if (d < minDist) minDist = d
        }
        return minDist
    }

    /**
     * Calculates shortest distance from point P to pothole (center oval + branching arms).
     */
    fun minDistToPothole(p: Vec2, center: Vec2, coreRx: Float, coreRy: Float, arms: List<List<Vec2>>): Float {
        val a = max(coreRx * 1.05f, 4f)
        val b = max(coreRy * 1.05f, 4f)
        val dx = p.x - center.x
        val dy = p.y - center.y
        val nx = dx / a
        val ny = dy / b
        if (nx * nx + ny * ny <= 1f) return 0f

        var dMin = Float.MAX_VALUE
        for (arm in arms) {
            val d = distancePointToPolyline(p, arm)
            if (d < dMin) dMin = d
        }
        return dMin
    }
}
