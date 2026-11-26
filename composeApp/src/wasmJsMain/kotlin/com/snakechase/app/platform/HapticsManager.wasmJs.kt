package com.snakechase.app.platform

import com.snakechase.app.domain.model.HapticStyle

private fun webVibrate(ms: Int): Unit = js("{ try { if (navigator.vibrate) { navigator.vibrate(ms); } } catch (e) { } }")

/** Uses the Vibration API where available (Android browsers); silently ignored elsewhere. */
actual class HapticsManager {
    private var isEnabled = true

    actual fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    actual fun trigger(style: HapticStyle) {
        if (!isEnabled) return
        webVibrate(15 + style.ordinal * 15)
    }
}
