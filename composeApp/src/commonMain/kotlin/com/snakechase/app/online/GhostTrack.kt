package com.snakechase.app.online

import com.snakechase.app.domain.model.Vec2
import kotlin.math.roundToInt

/**
 * A recorded run: the player's position sampled every [SAMPLE_MS], replayed as a translucent
 * "ghost" for the opponent (like racing a friend's ghost). Stored as a compact string,
 * "x,y;x,y;…" in whole arena pixels — ~5 KB for a 45 s round, far below Firestore limits.
 */
class GhostTrack(val points: List<Vec2>) {

    val durationMs: Long get() = if (points.isEmpty()) 0L else (points.size - 1) * SAMPLE_MS

    /** Interpolated position at [timeMs]; null once the run has ended (or when empty). */
    fun positionAt(timeMs: Long): Vec2? {
        if (points.isEmpty() || timeMs < 0 || timeMs > durationMs) return null
        val exact = timeMs.toFloat() / SAMPLE_MS
        val i = exact.toInt().coerceAtMost(points.size - 1)
        if (i >= points.size - 1) return points.last()
        val t = exact - i
        val a = points[i]
        val b = points[i + 1]
        return Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    /** True once a run that has lasted [timeMs] has survived longer than this recorded one. */
    fun isOutlastedAt(timeMs: Long): Boolean = points.isNotEmpty() && timeMs > durationMs

    fun encode(): String = points.joinToString(";") { "${it.x.roundToInt()},${it.y.roundToInt()}" }

    companion object {
        const val SAMPLE_MS = 100L

        /** 45 s round at 10 samples/s, plus slack. */
        const val MAX_POINTS = 460

        val EMPTY = GhostTrack(emptyList())

        /** Tolerant decode: malformed entries are skipped, the length is capped at [maxPoints]. */
        fun decode(encoded: String?, maxPoints: Int = MAX_POINTS): GhostTrack {
            if (encoded.isNullOrBlank()) return EMPTY
            val pts = encoded.split(';').asSequence().take(maxPoints).mapNotNull { pair ->
                val comma = pair.indexOf(',')
                if (comma <= 0) return@mapNotNull null
                val x = pair.substring(0, comma).toIntOrNull() ?: return@mapNotNull null
                val y = pair.substring(comma + 1).toIntOrNull() ?: return@mapNotNull null
                Vec2(x.toFloat(), y.toFloat())
            }.toList()
            return GhostTrack(pts)
        }
    }
}

/**
 * Samples a moving position at a fixed rate regardless of the device's frame rate. [maxPoints]
 * defaults to a duel round; single-player runs are longer (see PersonalGhosts).
 */
class GhostRecorder(private val maxPoints: Int = GhostTrack.MAX_POINTS) {
    private val points = ArrayList<Vec2>(minOf(maxPoints, GhostTrack.MAX_POINTS))
    private var nextSampleMs = 0L

    fun reset() {
        points.clear()
        nextSampleMs = 0L
    }

    /** Call every frame with the round clock; stores a sample each time a 100 ms slot passes. */
    fun record(roundTimeMs: Long, position: Vec2) {
        while (roundTimeMs >= nextSampleMs && points.size < maxPoints) {
            points.add(position)
            nextSampleMs += GhostTrack.SAMPLE_MS
        }
    }

    fun finish(): GhostTrack = GhostTrack(points.toList())
}
