package com.snakechase.app.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.snakechase.app.domain.model.SoundId

/** Android: SoundPool for effects and roasts, MediaPlayer for music, all from [SoundBank]'s files. */
actual class AudioManager(private val context: Context) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_GAME)
                .build(),
        )
        .build()
    private val loaded = mutableMapOf<String, Int>()
    private val main = Handler(Looper.getMainLooper())
    private var bgmPlayer: MediaPlayer? = null
    private var bgmFile: String? = null
    private var sfxVol = 1.0f
    private var bgmVol = 0.75f
    private var isMuted = false
    private var lastMeme: SoundBank.Meme? = null
    private var roastStream = 0

    init {
        SoundBank.sfxFiles.forEach { file -> runCatching { loaded[file] = soundPool.load(context.assets.openFd(assetPath(file)), 1) } }
    }

    /** Compose resources are packed as Android assets; the URI says where. */
    private fun assetPath(file: String) = SoundBank.uri(file).substringAfter("android_asset/")

    actual fun playSfx(soundId: SoundId, pitch: Float) {
        if (isMuted || sfxVol <= 0f) return
        val id = loaded[SoundBank.sfxFile(soundId)] ?: return
        val v = sfxVol * SoundBank.gain(SoundBank.sfxFile(soundId))
        runCatching { soundPool.play(id, v, v, 1, 0, pitch.coerceIn(0.5f, 2.0f)) }
    }

    actual fun playSfx(soundId: SoundId) = playSfx(soundId, 1.0f)

    actual fun playMemeRoast(): String? {
        if (isMuted || sfxVol <= 0f) return null
        val meme = SoundBank.nextMeme(lastMeme)
        val id = loaded[meme.file] ?: return null
        lastMeme = meme
        val v = sfxVol * SoundBank.gain(meme.file)
        roastStream = soundPool.play(id, v, v, 2, 0, 1f)
        return meme.caption
    }

    actual fun stopMemeRoast() {
        val stream = roastStream.takeIf { it != 0 } ?: return
        roastStream = 0
        // Short fade instead of a hard cut.
        val steps = 6
        for (i in 1..steps) {
            main.postDelayed({
                val v = sfxVol * (1f - i / steps.toFloat())
                soundPool.setVolume(stream, v, v)
                if (i == steps) soundPool.stop(stream)
            }, i * 50L)
        }
    }

    actual fun playBgm(levelId: Int) {
        if (isMuted || bgmVol <= 0f) return
        val file = SoundBank.musicFile(levelId)
        if (bgmPlayer?.isPlaying == true && bgmFile == file) return
        stopBgm()
        runCatching {
            val afd = context.assets.openFd(assetPath(file))
            bgmPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                val v = bgmVol * SoundBank.gain(file)
                setVolume(v, v)
                prepare()
                seekTo(SoundBank.musicStartMs(file))
                start()
            }
            bgmFile = file
        }
    }

    actual fun stopBgm() {
        runCatching {
            bgmPlayer?.stop()
            bgmPlayer?.release()
        }
        bgmPlayer = null
        bgmFile = null
    }

    actual fun setVolume(sfxVolume: Float, bgmVolume: Float) {
        sfxVol = sfxVolume
        bgmVol = bgmVolume
        val v = if (isMuted) 0f else bgmVol * (bgmFile?.let(SoundBank::gain) ?: 1f)
        bgmPlayer?.setVolume(v, v)
    }

    /** MediaPlayer prepares a track from the APK in milliseconds, so nothing to warm up. */
    actual fun preloadMusic(levelId: Int) {}

    actual fun setMuted(muted: Boolean) {
        isMuted = muted
        if (isMuted) stopBgm()
    }
}
