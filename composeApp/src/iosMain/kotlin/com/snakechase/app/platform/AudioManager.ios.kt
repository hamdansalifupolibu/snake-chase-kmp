package com.snakechase.app.platform

import com.snakechase.app.domain.model.SoundId

actual class AudioManager {
    actual fun playSfx(soundId: SoundId) {
        // iOS AVAudioPlayer
    }

    actual fun playSfx(soundId: SoundId, pitch: Float) {
        // iOS AVAudioPlayer with pitch
    }

    actual fun playBgm(levelId: Int) {
        // iOS BGM audio
    }

    actual fun stopBgm() {
        // Stop iOS BGM
    }

    actual fun setVolume(sfxVolume: Float, bgmVolume: Float) {
        // Volume setting
    }

    actual fun setMuted(muted: Boolean) {
        // Mute setting
    }

    actual fun playMemeRoast(): String? = null

    actual fun stopMemeRoast() {}

    actual fun preloadMusic(levelId: Int) {}
}
