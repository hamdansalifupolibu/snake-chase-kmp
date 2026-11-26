package com.snakechase.app.hunt

import com.russhwolf.settings.Settings
import com.snakechase.app.domain.model.AiDifficulty
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.MultiplayerRole
import com.snakechase.app.domain.model.MultiplayerState
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.MultiplayerEngine

/**
 * Snake Hunt: the main game reversed — you are the snake, an AI orb is the prey, and you must
 * catch it before the clock runs out. Five hunts of rising difficulty, starred by speed.
 */
data class HuntStage(
    val number: Int,
    val name: String,
    val blurb: String,
    /** The prey's brain: how smart its dodges are. */
    val prey: AiDifficulty,
    /** Multiplies the prey's speed (see MultiplayerState.preySpeedScale). */
    val preySpeedScale: Float,
    val timeLimitMs: Long,
)

object SnakeHunt {
    /**
     * Paced from bot simulations (SnakeHuntTest): every hunt is catchable, and the catch rate
     * falls stage by stage. Times are generous on purpose; stars reward speed.
     */
    val stages: List<HuntStage> = listOf(
        HuntStage(1, "HATCHLING", "A slow, careless orb. Learn to turn.", AiDifficulty.EASY, 0.6f, 20_000),
        HuntStage(2, "STALKER", "Quicker now. Cut it off instead of chasing.", AiDifficulty.EASY, 0.8f, 20_000),
        HuntStage(3, "AMBUSH", "It dodges at the last moment. Trap it against a wall.", AiDifficulty.MODERATE, 0.6f, 25_000),
        HuntStage(4, "PREDATOR", "Fast and tricky. Herd it into a corner.", AiDifficulty.MODERATE, 0.8f, 30_000),
        HuntStage(5, "APEX", "The ghost orb reads your every turn.", AiDifficulty.HARD, 0.6f, 35_000),
    )

    /** 3 stars with half the clock left, 2 with a quarter, 1 for any catch, 0 if it escaped. */
    fun starsFor(stage: HuntStage, caughtAtMs: Long?): Int {
        if (caughtAtMs == null || caughtAtMs > stage.timeLimitMs) return 0
        val left = (stage.timeLimitMs - caughtAtMs).toFloat() / stage.timeLimitMs
        return when {
            left >= 0.5f -> 3
            left >= 0.25f -> 2
            else -> 1
        }
    }

    /** Coins for each newly earned star; replaying for the same stars pays nothing (no farming). */
    const val COINS_PER_NEW_STAR = 40

    /**
     * A hunt is one duel round with you as the snake and the AI as prey, on the stage's clock.
     * The prey starts in the middle, not trapped in a corner, so it gets a fair chance to run.
     */
    fun startState(stage: HuntStage, playerName: String): MultiplayerState {
        val base = MultiplayerEngine.startMatch(
            mode = DuelMode.VS_AI_RIVAL,
            player1Name = playerName,
            player2Name = "${stage.name} ORB",
            difficulty = stage.prey,
        )
        return base.copy(
            localRole = MultiplayerRole.SNAKE,
            maxRoundTimeMs = stage.timeLimitMs,
            preySpeedScale = stage.preySpeedScale,
            roundAnnouncement = "HUNT ${stage.number} — ${stage.name}",
            prey = base.prey.copy(position = Vec2(base.arenaSize.x / 2f, base.arenaSize.y / 2f)),
        )
    }
}

/** Best stars per hunt, kept on the device. */
class HuntProgress(
    private val storage: Settings,
    /** Judge edition: every hunt is open from the start (see config/Edition.kt). */
    private val openAll: Boolean = false,
) {
    fun stars(stage: Int): Int = storage.getInt(key(stage), 0)

    /** Hunt 1 is always open; each later hunt opens once the one before it has been won. */
    fun isUnlocked(stage: Int): Boolean = openAll || stage <= 1 || stars(stage - 1) > 0

    /** Saves a better result and returns the coins it earned (0 when it didn't beat the best). */
    fun record(stage: Int, stars: Int): Int {
        val old = stars(stage)
        if (stars <= old) return 0
        storage.putInt(key(stage), stars)
        return (stars - old) * SnakeHunt.COINS_PER_NEW_STAR
    }

    fun clearAll() = SnakeHunt.stages.forEach { storage.remove(key(it.number)) }

    private fun key(stage: Int) = "sc_hunt_stars_$stage"
}
