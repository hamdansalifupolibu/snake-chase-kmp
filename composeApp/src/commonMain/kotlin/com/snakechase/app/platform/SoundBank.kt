package com.snakechase.app.platform

import com.snakechase.app.domain.model.SoundId
import org.jetbrains.compose.resources.ExperimentalResourceApi
import snakechase.composeapp.generated.resources.Res

/**
 * Every sound in the game, in one place, shared by all platforms. Files live in
 * composeResources/files/audio; each platform's [AudioManager] plays them by URI.
 * Credits for every file are in docs/CREDITS.md.
 */
object SoundBank {
    data class Meme(val file: String, val caption: String)

    /** Which file plays for each game event. The 3-2-1-GO countdown keeps its own beep. */
    fun sfxFile(id: SoundId): String = when (id) {
        SoundId.NEAR_MISS -> "near_miss_whoosh.wav"
        SoundId.PERFECT_NEAR_MISS -> "near_miss_whoosh.wav"
        SoundId.DEATH -> "impact_gameover.wav"
        SoundId.STAGE_ADVANCE -> "countdown_go.wav"
        SoundId.LEVEL_COMPLETE -> "kn_level_clear.wav"
        SoundId.SPLIT_SPAWN -> "snake_hiss.wav"
        SoundId.TELEPORT -> "kn_teleport.wav"
        SoundId.BUTTON_CLICK -> "ui_pop.wav"
        SoundId.DASH -> "near_miss_whoosh.wav"
        SoundId.CONCUSSION -> "kn_stun.wav"
        SoundId.BULLET_TIME -> "kn_slowmo.wav"
        SoundId.ON_FIRE -> "kn_on_fire.wav"
        SoundId.STREAK_BROKEN -> "kn_streak_broken.wav"
        SoundId.POWER_UP -> "kn_powerup.wav"
        // The original 3-2-1-GO sound, cut into its beep and its long GO so each lands on its number.
        SoundId.COUNTDOWN_TICK -> "countdown_tick.wav"
        SoundId.COUNTDOWN_GO -> "countdown_go.wav"
        // Made for Snake Chase (tools/generate_sounds.py): a rising chime per victory star, the
        // third with a sparkle, and a soft arpeggio when a skin or orb is unlocked.
        SoundId.STAR_1 -> "star_1.wav"
        SoundId.STAR_2 -> "star_2.wav"
        SoundId.STAR_3 -> "star_3.wav"
        SoundId.UNLOCK -> "unlock.wav"
    }

    /** Every world has its own music (see docs/CREDITS.md); Eclipse scores the fire world and every duel and hunt. */
    fun musicFile(levelId: Int): String = when (levelId) {
        1 -> "l1_neon_synthwave_house.mp3"
        2 -> "l2_grove_joyful_jungle.mp3"
        3 -> "l3_abyss_underwater_pad.mp3"
        4 -> "l4_tundra_snow_wave.mp3"
        else -> "bgm_eclipse.mp3"
    }

    /**
     * Loudness balance, measured per file (RMS): every music track is brought to the same level
     * (about -20 dB) so no world is louder than another and music sits under the effects; the
     * loudest effects are pulled down to match the rest. 1 = as recorded.
     */
    fun gain(file: String): Float = when (file) {
        "l1_neon_synthwave_house.mp3" -> 0.42f
        "l2_grove_joyful_jungle.mp3" -> 0.42f
        "l3_abyss_underwater_pad.mp3" -> 0.94f
        "l4_tundra_snow_wave.mp3" -> 0.54f
        "bgm_eclipse.mp3" -> 0.40f
        "countdown_tick.wav", "countdown_go.wav" -> 0.6f
        "meme_vine_boom.mp3", "meme_vine_boom2.mp3" -> 0.55f
        else -> 1f
    }

    /** Where a music track really begins (skips the silence at the start of the file). */
    fun musicStartMs(file: String): Int = when (file) {
        "l4_tundra_snow_wave.mp3" -> 380
        "bgm_eclipse.mp3" -> 370
        else -> 0
    }

    /** Roasts for a failed run (Settings → Meme roasts). */
    val memes = listOf(
        Meme("meme_vine_boom.mp3", "VINE BOOM"),
        Meme("meme_oh_hell_nah.mp3", "OH HELL NAH MAN!"),
        Meme("meme_let_me_tell_you.mp3", "LET ME TELL YOU SOMETHING"),
        Meme("meme_keep_quiet.mp3", "WILL YOU KEEP QUIET?!"),
        Meme("meme_fahhh.mp3", "FAHHH!"),
        Meme("meme_core.mp3", "CORE OVERLOAD"),
        Meme("meme_vine_boom2.mp3", "VINE BOOM II"),
    )

    /** Every distinct effect file, for preloading. */
    val sfxFiles: List<String> = (SoundId.entries.map(::sfxFile) + memes.map { it.file }).distinct()

    /** Picks a roast, never the same one twice in a row. */
    fun nextMeme(previous: Meme?, random: kotlin.random.Random = kotlin.random.Random): Meme {
        val pool = memes.filter { it != previous }.ifEmpty { memes }
        return pool[random.nextInt(pool.size)]
    }

    /** A platform-readable URI for an audio file (web URL, Android asset, desktop jar entry). */
    @OptIn(ExperimentalResourceApi::class)
    fun uri(file: String): String = Res.getUri("files/audio/$file")
}
