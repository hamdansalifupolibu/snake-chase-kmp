package com.snakechase.app.platform

import com.snakechase.app.domain.model.HapticStyle

expect class HapticsManager {
    fun trigger(style: HapticStyle)
    fun setEnabled(enabled: Boolean)
}
