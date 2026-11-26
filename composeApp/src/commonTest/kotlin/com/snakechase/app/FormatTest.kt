package com.snakechase.app

import com.snakechase.app.util.formatSeconds
import com.snakechase.app.util.toFixed
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun fixedDecimals_areStable() {
        assertEquals("1.1", 1.1f.toFixed(1)) // Float that is 1.100000023841858 in binary
        assertEquals("45.0", 45f.toFixed(1))
        assertEquals("0.05", 0.05f.toFixed(2))
        assertEquals("-2.5", (-2.5).toFixed(1))
        assertEquals("3", 2.6.toFixed(0))
    }

    @Test
    fun milliseconds_toSeconds() {
        assertEquals("12.3", formatSeconds(12_345))
        assertEquals("0.0", formatSeconds(0))
        assertEquals("45.0", formatSeconds(45_000))
    }
}
