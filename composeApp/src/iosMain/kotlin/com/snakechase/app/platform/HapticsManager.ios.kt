package com.snakechase.app.platform

import com.snakechase.app.domain.model.HapticStyle

actual class HapticsManager {
    actual fun trigger(style: HapticStyle) {
        // iOS UIImpactFeedbackGenerator
    }
}
