package com.snakechase.app.platform

import com.snakechase.app.domain.model.HapticStyle
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/** iOS: the Taptic Engine through UIImpactFeedbackGenerator. */
actual class HapticsManager {
    private var isEnabled = true

    actual fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    actual fun trigger(style: HapticStyle) {
        if (!isEnabled) return
        val impact = when (style) {
            HapticStyle.LIGHT, HapticStyle.HEARTBEAT_CALM -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            HapticStyle.MEDIUM, HapticStyle.HEARTBEAT_URGENT, HapticStyle.DASH_BURST -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            HapticStyle.HEAVY, HapticStyle.ADRENALINE_SNAP, HapticStyle.CONCUSSION_RUMBLE, HapticStyle.STREAK_FIRE ->
                UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        }
        UIImpactFeedbackGenerator(style = impact).impactOccurred()
    }
}
