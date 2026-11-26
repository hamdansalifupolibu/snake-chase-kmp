package com.snakechase.app.platform

actual val isKeyboardPrimary: Boolean = true
actual val platformName: String = "Desktop"
actual val needsRotateHint: Boolean = false
actual val canRequestFullscreen: Boolean = false
actual fun requestLandscapeFullscreen() {}
