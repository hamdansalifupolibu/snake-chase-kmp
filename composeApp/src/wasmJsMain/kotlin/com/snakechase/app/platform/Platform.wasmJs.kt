package com.snakechase.app.platform

private fun browserHasTouch(): Boolean = js("('ontouchstart' in window) || (navigator.maxTouchPoints > 0)")

/** A phone browser plays by touch; a laptop browser gets the keyboard hints. */
actual val isKeyboardPrimary: Boolean = !browserHasTouch()
actual val platformName: String = "Web"

actual val needsRotateHint: Boolean = browserHasTouch()

private fun fullscreenEnabled(): Boolean = js("document.fullscreenEnabled === true")
actual val canRequestFullscreen: Boolean = fullscreenEnabled()

private fun jsLandscapeFullscreen(): Unit = js(
    """{
    try {
        const lock = () => {
            try { if (screen.orientation && screen.orientation.lock) screen.orientation.lock('landscape').catch(() => {}); } catch (e) { }
        };
        const el = document.documentElement;
        if (el.requestFullscreen && !document.fullscreenElement) { el.requestFullscreen().then(lock).catch(lock); } else { lock(); }
    } catch (e) { }
    }""",
)

actual fun requestLandscapeFullscreen() = jsLandscapeFullscreen()
