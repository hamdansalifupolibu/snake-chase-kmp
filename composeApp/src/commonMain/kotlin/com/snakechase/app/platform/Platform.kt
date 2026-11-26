package com.snakechase.app.platform

/**
 * True when the platform's primary input is a physical keyboard (desktop, web),
 * so the UI should advertise WASD / Space / Esc shortcuts instead of touch controls.
 */
expect val isKeyboardPrimary: Boolean

/** Short device label used as a fallback duel name, e.g. "Android", "Desktop". */
expect val platformName: String

/**
 * True on phone/tablet browsers. The apps lock themselves to landscape; a web page can't, so a
 * player holding the phone upright gets a "turn your phone" guide instead.
 */
expect val needsRotateHint: Boolean

/** Whether [requestLandscapeFullscreen] can do anything here (Android Chrome yes, iPhone Safari no). */
expect val canRequestFullscreen: Boolean

/** Best effort: go fullscreen and lock to landscape. Must be called from a tap. No-op off the web. */
expect fun requestLandscapeFullscreen()
