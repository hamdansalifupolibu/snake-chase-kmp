package com.snakechase.app.network

import com.snakechase.app.domain.model.Vec2

/** One host snapshot as the guest received it. */
data class DuelSnapshot(
    val receivedAtMs: Long,
    val snakeHead: Vec2,
    val snakeAngle: Float,
    val snakeSegments: List<Vec2>,
    val prey: Vec2,
)

/**
 * Smooths the guest's view of a host-authoritative match. Snapshots arrive ~20×/s with network
 * jitter; rendering them raw looks choppy. Instead the guest draws the world [delayMs] in the
 * past and blends between the two snapshots around that moment — the standard "entity
 * interpolation" technique used by online action games. Pure logic, unit-tested.
 */
class SnapshotInterpolator(private val delayMs: Long = 100L, private val capacity: Int = 32) {
    private val buffer = ArrayDeque<DuelSnapshot>()

    fun add(snapshot: DuelSnapshot) {
        // Out-of-order arrivals (rare over HTTP) are dropped rather than corrupting the timeline.
        if (buffer.isNotEmpty() && snapshot.receivedAtMs < buffer.last().receivedAtMs) return
        buffer.addLast(snapshot)
        while (buffer.size > capacity) buffer.removeFirst()
    }

    fun clear() = buffer.clear()

    /** The blended snapshot to draw at local time [nowMs], or null before anything arrived. */
    fun sample(nowMs: Long): DuelSnapshot? {
        if (buffer.isEmpty()) return null
        val renderAt = nowMs - delayMs
        if (renderAt <= buffer.first().receivedAtMs) return buffer.first()
        if (renderAt >= buffer.last().receivedAtMs) return buffer.last() // hold, never extrapolate
        // Drop snapshots that are entirely in the past, keeping the one just before renderAt.
        while (buffer.size >= 2 && buffer[1].receivedAtMs <= renderAt) buffer.removeFirst()
        val a = buffer[0]
        val b = buffer[1]
        val span = (b.receivedAtMs - a.receivedAtMs).coerceAtLeast(1)
        val t = ((renderAt - a.receivedAtMs).toFloat() / span).coerceIn(0f, 1f)
        return DuelSnapshot(
            receivedAtMs = renderAt,
            snakeHead = lerp(a.snakeHead, b.snakeHead, t),
            snakeAngle = b.snakeAngle,
            snakeSegments = if (a.snakeSegments.size == b.snakeSegments.size) {
                a.snakeSegments.indices.map { i -> lerp(a.snakeSegments[i], b.snakeSegments[i], t) }
            } else {
                b.snakeSegments
            },
            prey = lerp(a.prey, b.prey, t),
        )
    }

    private fun lerp(a: Vec2, b: Vec2, t: Float) = Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}
