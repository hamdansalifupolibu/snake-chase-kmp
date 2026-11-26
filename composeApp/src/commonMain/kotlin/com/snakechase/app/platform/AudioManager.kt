package com.snakechase.app.platform

import com.snakechase.app.domain.model.SoundId

/**
 * Plays the game's sounds on each platform. Which file plays for what is decided in [SoundBank],
 * so every platform sounds the same.
 */
expect class AudioManager {
    fun playSfx(soundId: SoundId)
    fun playSfx(soundId: SoundId, pitch: Float)
    fun playBgm(levelId: Int)
    fun stopBgm()
    fun setVolume(sfxVolume: Float, bgmVolume: Float)
    fun setMuted(muted: Boolean)

    /** Plays a roast clip after a failed run; returns its caption (null when muted). */
    fun playMemeRoast(): String?

    /** Fades out a roast that is still playing (the player started again). */
    fun stopMemeRoast()

    /** Gets a level's music ready ahead of time, so it starts the moment the level does. */
    fun preloadMusic(levelId: Int)
}
