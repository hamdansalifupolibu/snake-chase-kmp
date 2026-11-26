package com.snakechase.app

import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.CollisionDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollisionDetectorTest {

    @Test
    fun testCircleOverlap_whenOverlapping_returnsTrue() {
        val a = Vec2(100f, 100f)
        val b = Vec2(120f, 100f)
        // distance is 20, sum of radii is 15 + 10 = 25 -> overlap
        assertTrue(CollisionDetector.checkCircleOverlap(a, 15f, b, 10f))
    }

    @Test
    fun testCircleOverlap_whenApart_returnsFalse() {
        val a = Vec2(100f, 100f)
        val b = Vec2(200f, 200f)
        assertFalse(CollisionDetector.checkCircleOverlap(a, 15f, b, 10f))
    }

    @Test
    fun testCircleRectOverlap_whenInside_returnsTrue() {
        val circle = Vec2(150f, 150f)
        val rect = Rect(100f, 100f, 100f, 100f)
        assertTrue(CollisionDetector.checkCircleRectOverlap(circle, 10f, rect))
    }

    @Test
    fun testCircleRectOverlap_whenTouchingEdge_returnsTrue() {
        val circle = Vec2(95f, 150f)
        val rect = Rect(100f, 100f, 100f, 100f)
        // Distance from edge x=100 is 5, radius is 10 -> overlap
        assertTrue(CollisionDetector.checkCircleRectOverlap(circle, 10f, rect))
    }

    @Test
    fun testDistancePointToSegment() {
        val p = Vec2(50f, 10f)
        val a = Vec2(0f, 0f)
        val b = Vec2(100f, 0f)
        val dist = CollisionDetector.distancePointToSegment(p, a, b)
        assertEquals(10f, dist, 0.001f)
    }

    @Test
    fun testCircleRotatingBarOverlap() {
        val center = Vec2(200f, 200f)
        val circleOnBar = Vec2(250f, 200f) // along 0 rad angle
        val overlaps = CollisionDetector.checkCircleBarOverlap(
            circlePos = circleOnBar,
            radius = 10f,
            center = center,
            length = 150f,
            angleRad = 0f,
            barThickness = 14f,
        )
        assertTrue(overlaps)
    }
}
