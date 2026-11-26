package com.snakechase.app.platform

actual val isKeyboardPrimary: Boolean = false
actual val platformName: String = "Android"
actual val needsRotateHint: Boolean = false // the activity is locked to landscape in the manifest
actual val canRequestFullscreen: Boolean = false
actual fun requestLandscapeFullscreen() {}
