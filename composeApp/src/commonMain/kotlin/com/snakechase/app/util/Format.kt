package com.snakechase.app.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Number formatting that prints identically on every target. Interpolating a Float directly
 * ("${x}") prints "1.1" on the JVM but "1.100000023841858" on Kotlin/Wasm, so the UI formats
 * with integer arithmetic instead.
 */
fun Float.toFixed(digits: Int): String = toDouble().toFixed(digits)

fun Double.toFixed(digits: Int): String {
    if (digits <= 0) return roundToLong().toString()
    var scale = 1L
    repeat(digits) { scale *= 10 }
    val scaled = (this * scale).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val whole = abs(scaled) / scale
    val frac = (abs(scaled) % scale).toString().padStart(digits, '0')
    return "$sign$whole.$frac"
}

/** Milliseconds as seconds with one decimal, e.g. 12345 → "12.3". */
fun formatSeconds(ms: Long): String = (ms / 1000.0).toFixed(1)
