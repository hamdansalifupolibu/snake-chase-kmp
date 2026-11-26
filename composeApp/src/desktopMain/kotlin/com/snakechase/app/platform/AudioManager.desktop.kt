package com.snakechase.app.platform

import com.snakechase.app.domain.model.SoundId
import java.io.BufferedInputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * Desktop: the same MP3 files as every other platform ([SoundBank]), decoded through Java Sound
 * with the mp3spi decoder. Effects are decoded once into PCM so they start instantly; music is
 * decoded off the UI thread and looped.
 */
actual class AudioManager {
    private class Pcm(val format: AudioFormat, val bytes: ByteArray)

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "snake-audio").apply { isDaemon = true } }
    private val sfx = ConcurrentHashMap<String, Pcm>()
    private var bgmClip: Clip? = null

    /** Decoded music for the levels played recently (each track is ~30 MB of PCM). */
    private val musicCache = object : LinkedHashMap<String, Pcm>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pcm>?) = size > 2
    }
    private var bgmFile: String? = null
    private var roastClip: Clip? = null
    private var lastMeme: SoundBank.Meme? = null
    private var sfxVol = 1.0f
    private var bgmVol = 0.75f
    private var muted = false

    init {
        worker.execute { SoundBank.sfxFiles.forEach { f -> runCatching { sfx[f] = decode(f) } } }
    }

    /** Decodes an MP3 into 16-bit signed PCM that any Clip can play. */
    private fun decode(file: String): Pcm {
        val source = AudioSystem.getAudioInputStream(BufferedInputStream(URI(SoundBank.uri(file)).toURL().openStream()))
        val base = source.format
        val pcmFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels, base.channels * 2, base.sampleRate, false)
        val pcm: AudioInputStream = AudioSystem.getAudioInputStream(pcmFormat, source)
        return Pcm(pcmFormat, pcm.readAllBytes())
    }

    private fun clipFor(p: Pcm, pitch: Float, volume: Float): Clip? = runCatching {
        // Pitch by playing the same samples at a different rate (higher = faster and higher).
        val f = p.format
        val rate = f.sampleRate * pitch.coerceIn(0.5f, 2.0f)
        val shifted = AudioFormat(f.encoding, rate, f.sampleSizeInBits, f.channels, f.frameSize, rate, f.isBigEndian)
        val clip = AudioSystem.getClip()
        runCatching { clip.open(shifted, p.bytes, 0, p.bytes.size) }.getOrElse { clip.open(f, p.bytes, 0, p.bytes.size) }
        setGain(clip, volume)
        clip
    }.getOrNull()

    private fun setGain(clip: Clip, volume: Float) {
        val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
        val db = if (volume <= 0.0001f) gain.minimum else (20f * log10(volume)).coerceIn(gain.minimum, gain.maximum)
        gain.value = db
    }

    private fun playOnce(file: String, pitch: Float, volume: Float): Clip? {
        val p = sfx[file] ?: return null
        val clip = clipFor(p, pitch, volume) ?: return null
        clip.addLineListener { e -> if (e.type == javax.sound.sampled.LineEvent.Type.STOP) clip.close() }
        clip.start()
        return clip
    }

    actual fun playSfx(soundId: SoundId) = playSfx(soundId, 1f)

    actual fun playSfx(soundId: SoundId, pitch: Float) {
        if (muted || sfxVol <= 0f) return
        val file = SoundBank.sfxFile(soundId)
        worker.execute { playOnce(file, pitch, sfxVol * SoundBank.gain(file)) }
    }

    actual fun playMemeRoast(): String? {
        if (muted || sfxVol <= 0f) return null
        val meme = SoundBank.nextMeme(lastMeme)
        lastMeme = meme
        worker.execute { roastClip = playOnce(meme.file, 1f, sfxVol * SoundBank.gain(meme.file)) }
        return meme.caption
    }

    actual fun stopMemeRoast() {
        worker.execute {
            val clip = roastClip ?: return@execute
            roastClip = null
            // Short fade instead of a hard cut.
            for (i in 1..6) {
                setGain(clip, sfxVol * (1f - i / 6f))
                Thread.sleep(45)
            }
            clip.stop()
        }
    }

    actual fun playBgm(levelId: Int) {
        if (muted || bgmVol <= 0f) return
        val file = SoundBank.musicFile(levelId)
        if (bgmClip?.isRunning == true && bgmFile == file) return
        stopBgm()
        bgmFile = file
        worker.execute {
            runCatching {
                val p = musicCache[file] ?: decode(file).also { musicCache[file] = it }
                if (bgmFile != file) return@execute // changed while decoding
                val clip = AudioSystem.getClip()
                clip.open(p.format, p.bytes, 0, p.bytes.size)
                setGain(clip, bgmVol * SoundBank.gain(file))
                // Start (and loop back) past the silence at the head of the file.
                val start = (SoundBank.musicStartMs(file) * p.format.frameRate / 1000f).toInt().coerceIn(0, clip.frameLength - 1)
                clip.framePosition = start
                clip.setLoopPoints(start, -1)
                clip.loop(Clip.LOOP_CONTINUOUSLY)
                bgmClip = clip
            }
        }
    }

    actual fun stopBgm() {
        bgmFile = null
        val clip = bgmClip
        bgmClip = null
        worker.execute {
            runCatching {
                clip?.stop()
                clip?.close()
            }
        }
    }

    actual fun setVolume(sfxVolume: Float, bgmVolume: Float) {
        sfxVol = sfxVolume
        bgmVol = bgmVolume
        bgmClip?.let { setGain(it, if (muted) 0f else bgmVol * (bgmFile?.let(SoundBank::gain) ?: 1f)) }
    }

    actual fun preloadMusic(levelId: Int) {
        val file = SoundBank.musicFile(levelId)
        worker.execute { if (file !in musicCache) runCatching { musicCache[file] = decode(file) } }
    }

    actual fun setMuted(muted: Boolean) {
        this.muted = muted
        if (muted) stopBgm()
    }
}
