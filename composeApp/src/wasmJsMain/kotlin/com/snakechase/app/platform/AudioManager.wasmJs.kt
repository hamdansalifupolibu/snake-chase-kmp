package com.snakechase.app.platform

import com.snakechase.app.domain.model.SoundId

// Web Audio player. Files are fetched once by URL (Compose serves them next to the page) and
// decoded into AudioBuffers; browsers only allow sound after a tap/click, so the context resumes
// on the first play after the player interacts.

private fun jsLoad(name: String, url: String): Unit = js(
    """{
    const a = globalThis.__sc || (globalThis.__sc = { buffers: {}, loading: {} });
    if (a.buffers[name] || a.loading[name]) return;
    a.ctx = a.ctx || new (globalThis.AudioContext || globalThis.webkitAudioContext)();
    a.loading[name] = fetch(url).then(r => r.arrayBuffer()).then(b => a.ctx.decodeAudioData(b))
        .then(buf => { a.buffers[name] = buf; return buf; }).catch(() => null);
    }""",
)

/** Plays a decoded sound; kind 0 = effect, 1 = music (looped, replaces the old one), 2 = roast. */
private fun jsPlay(name: String, url: String, rate: Double, volume: Double, kind: Int, offsetSec: Double): Unit = js(
    """{
    const a = globalThis.__sc || (globalThis.__sc = { buffers: {}, loading: {} });
    a.ctx = a.ctx || new (globalThis.AudioContext || globalThis.webkitAudioContext)();
    const ctx = a.ctx;
    if (ctx.state === 'suspended') ctx.resume();
    const start = (buf) => {
        if (!buf) return;
        if (kind === 1 && a.bgmName !== name) return;   // music changed while loading
        const src = ctx.createBufferSource();
        const gain = ctx.createGain();
        src.buffer = buf;
        src.playbackRate.value = rate;
        src.loop = kind === 1;
        gain.gain.value = volume;
        src.connect(gain); gain.connect(ctx.destination);
        if (kind === 1) { src.loopStart = offsetSec; src.loopEnd = buf.duration; }
        src.start(0, offsetSec);
        if (kind === 1) { a.bgm = { src, gain }; }
        if (kind === 2) { a.roast = { src, gain }; }
    };
    if (kind === 1) {
        if (a.bgm && a.bgmName === name) return;
        if (a.bgm) { try { a.bgm.src.stop(); } catch (e) {} a.bgm = null; }
        a.bgmName = name;
    }
    if (a.buffers[name]) { start(a.buffers[name]); return; }
    const load = (n, u) => a.loading[n] || (a.loading[n] = fetch(u).then(r => r.arrayBuffer()).then(b => ctx.decodeAudioData(b))
            .then(buf => { a.buffers[n] = buf; return buf; }).catch(() => null));
    load(name, url).then(start);
    }""",
)

/** Fades out and stops the music (kind 1) or the roast (kind 2). */
private fun jsStop(kind: Int, fadeMs: Int): Unit = js(
    """{
    const a = globalThis.__sc; if (!a || !a.ctx) return;
    const node = kind === 1 ? a.bgm : a.roast;
    if (kind === 1) { a.bgm = null; a.bgmName = null; } else { a.roast = null; }
    if (!node) return;
    const t = a.ctx.currentTime;
    try {
        node.gain.gain.setValueAtTime(node.gain.gain.value, t);
        node.gain.gain.linearRampToValueAtTime(0, t + fadeMs / 1000);
        node.src.stop(t + fadeMs / 1000 + 0.02);
    } catch (e) {}
    }""",
)

private fun jsMusicVolume(volume: Double): Unit = js(
    """{ const a = globalThis.__sc; if (a && a.bgm) a.bgm.gain.gain.value = volume; }""",
)

actual class AudioManager {
    private var sfxVolume = 0.8f
    private var bgmVolume = 0.6f
    private var muted = false
    private var lastMeme: SoundBank.Meme? = null

    init {
        // Fetch and decode the short effects up front so the first near miss isn't silent.
        SoundBank.sfxFiles.forEach { runCatching { jsLoad(it, SoundBank.uri(it)) } }
    }

    actual fun playSfx(soundId: SoundId) = playSfx(soundId, 1f)

    actual fun playSfx(soundId: SoundId, pitch: Float) {
        if (muted || sfxVolume <= 0f) return
        val file = SoundBank.sfxFile(soundId)
        jsPlay(file, SoundBank.uri(file), pitch.coerceIn(0.5f, 2f).toDouble(), (sfxVolume * SoundBank.gain(file)).toDouble(), 0, 0.0)
    }

    actual fun playBgm(levelId: Int) {
        if (muted || bgmVolume <= 0f) return
        val file = SoundBank.musicFile(levelId)
        jsPlay(file, SoundBank.uri(file), 1.0, (bgmVolume * SoundBank.gain(file)).toDouble(), 1, SoundBank.musicStartMs(file) / 1000.0)
    }

    actual fun stopBgm() = jsStop(1, 250)

    actual fun setVolume(sfxVolume: Float, bgmVolume: Float) {
        this.sfxVolume = sfxVolume
        this.bgmVolume = bgmVolume
        jsMusicVolume(if (muted) 0.0 else bgmVolume.toDouble())
    }

    actual fun setMuted(muted: Boolean) {
        this.muted = muted
        if (muted) stopBgm()
    }

    actual fun playMemeRoast(): String? {
        if (muted || sfxVolume <= 0f) return null
        val meme = SoundBank.nextMeme(lastMeme)
        lastMeme = meme
        jsPlay(meme.file, SoundBank.uri(meme.file), 1.0, (sfxVolume * SoundBank.gain(meme.file)).toDouble(), 2, 0.0)
        return meme.caption
    }

    actual fun stopMemeRoast() = jsStop(2, 300)

    /** Fetch and decode a level's music while the player is still in the menu or countdown. */
    actual fun preloadMusic(levelId: Int) {
        val file = SoundBank.musicFile(levelId)
        jsLoad(file, SoundBank.uri(file))
    }
}
