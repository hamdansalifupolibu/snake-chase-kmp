package com.snakechase.app

import com.snakechase.app.platform.SoundBank
import java.io.BufferedInputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every sound the game asks for must exist and decode (MP3 and WAV) through Java Sound. */
class SoundBankTest {
    private val dir = File("src/commonMain/composeResources/files/audio")

    @Test
    fun everyReferencedSound_existsAndDecodes() {
        val files = (SoundBank.sfxFiles + (0..5).map(SoundBank::musicFile)).distinct()
        for (name in files) {
            val f = File(dir, name)
            assertTrue(f.isFile, "missing sound file $name")
            val source = AudioSystem.getAudioInputStream(BufferedInputStream(f.inputStream()))
            val fmt = source.format
            val pcm = AudioSystem.getAudioInputStream(
                AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fmt.sampleRate, 16, fmt.channels, fmt.channels * 2, fmt.sampleRate, false),
                source,
            )
            // Read it all, like the desktop player does (OGG may hand back an empty first chunk).
            assertTrue(pcm.readAllBytes().size > 1000, "$name decoded to nothing")
            pcm.close()
        }
    }

    @Test
    fun everyWorldHasItsOwnMusic() {
        val tracks = (1..5).map(SoundBank::musicFile)
        assertTrue(tracks.distinct().size == 5, "$tracks")
    }
}
