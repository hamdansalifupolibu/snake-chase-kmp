package com.snakechase.app.platform

actual val isKeyboardPrimary: Boolean = false
actual val platformName: String = "iOS"
actual val needsRotateHint: Boolean = false
actual val canRequestFullscreen: Boolean = false
actual fun requestLandscapeFullscreen() {}
