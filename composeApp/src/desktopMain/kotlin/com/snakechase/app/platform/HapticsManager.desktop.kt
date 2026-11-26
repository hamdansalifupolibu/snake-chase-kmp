package com.snakechase.app.platform

import com.snakechase.app.domain.model.HapticStyle

actual class HapticsManager {
    private var isEnabled: Boolean = true

    actual fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    actual fun trigger(style: HapticStyle) {
        // Desktop is a no-op for haptics
    }
}
