package com.snakechase.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SteeringMode {
    POINTER, // Touch or mouse drag (the keyboard also works alongside it)
    KEYBOARD, // WASD / arrow keys only: clicks and drags in the arena are ignored
    VIRTUAL_STICK, // Floating on-screen thumb joystick
}

@Serializable
enum class JoystickSide {
    LEFT, // Left margin (outside arena) - Left thumb
    RIGHT, // Right margin (outside arena) - Right thumb
}

@Serializable
enum class ParticleQuality {
    HIGH, // Full 60fps glow, spark trails, explosion debris
    LOW, // Minimal particles for battery saving / lower end devices
}

@Serializable
data class UserSettings(
    val soundEnabled: Boolean = true,
    val masterVolume: Float = 1.0f, // 0.0f - 1.0f
    val bgmVolume: Float = 0.75f, // 0.0f - 1.0f
    val sfxVolume: Float = 1.0f, // 0.0f - 1.0f
    val hapticsEnabled: Boolean = true,
    val steeringMode: SteeringMode = SteeringMode.POINTER,
    val joystickSide: JoystickSide = JoystickSide.LEFT,
    val particleQuality: ParticleQuality = ParticleQuality.HIGH,
    val touchSensitivity: Float = 1.0f, // 0.5f - 2.0f
    val playerName: String = "Player 1",
    /** Levels 1-5 difficulty; [difficultyChosen] is false until the player picks one. */
    val difficulty: Difficulty = Difficulty.RECOMMENDED,
    val difficultyChosen: Boolean = false,
    /** Play a meme clip that roasts you when a run fails. */
    val memeRoasts: Boolean = true,
    /** The welcome tour and the first-run tips have been shown. */
    val tourSeen: Boolean = false,
    val firstRunTipsSeen: Boolean = false,
)
