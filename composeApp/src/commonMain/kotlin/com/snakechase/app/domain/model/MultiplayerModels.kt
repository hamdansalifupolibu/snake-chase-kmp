package com.snakechase.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MultiplayerRole {
    SNAKE,
    PREY,
}

@Serializable
enum class DuelMode {
    VS_AI_RIVAL,
    LOCAL_INVITE_CHALLENGE,
    LOCAL_PASS_AND_PLAY,
    ONLINE_SERVER,
}

@Serializable
enum class DuelPhase {
    MODE_SELECT,
    CHALLENGE_INVITE,
    MATCHMAKING,
    COUNTDOWN,
    ROUND_PLAYING,
    ROUND_TRANSITION,
    MATCH_OVER,
}

@Serializable
data class QuickChatMessage(
    val senderName: String,
    val text: String,
    val emoji: String? = null,
    val timestampMs: Long = 0L,
)

@Serializable
data class DuelMatchResult(
    val winnerName: String,
    val isLocalWinner: Boolean,
    val round1TimeMs: Long,
    val round2TimeMs: Long,
    val round3TimeMs: Long = 0L,
    val player1Name: String,
    val player2Name: String,
    val player1RoundsWon: Int = 0,
    val player2RoundsWon: Int = 0,
    val mvpTitle: String = "Apex Predator",
    val shareText: String = "",
)

@Serializable
data class ArenaTier(
    val name: String,
    val entryFee: Int,
    val prizePool: Int,
    val minLevel: Int,
    val icon: String,
    val accentColorHex: Long,
    val description: String,
)

val ARENA_TIERS = listOf(
    ArenaTier("Neon Drift", 100, 200, 1, "🏙️", 0xFF00F5FF, "The classic neon grid"),
    ArenaTier("Neon Grove", 500, 1000, 2, "🌳", 0xFF00E676, "A glowing jungle grove"),
    ArenaTier("Crystal Tundra", 2500, 5000, 3, "❄️", 0xFF76FFFF, "Icy arena with frost hazards"),
    ArenaTier("Crucible Core", 10000, 20000, 4, "🌋", 0xFFFF5252, "Molten core, the fastest snakes"),
    ArenaTier("Abyssal Chase", 50000, 100000, 5, "🌊", 0xFFB388FF, "The deep trench: champion stakes"),
)

@Serializable
enum class AiDifficulty(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val speedMultiplier: Float,
    val accentColorHex: Long,
) {
    EASY(
        title = "EASY",
        subtitle = "The Wanderer · Slower prey, forgiving turns",
        emoji = "🟢",
        speedMultiplier = 0.55f,
        accentColorHex = 0xFF30FF9E,
    ),
    MODERATE(
        title = "MODERATE",
        subtitle = "The Hunter · Balanced speed, tactical jukes",
        emoji = "🟡",
        speedMultiplier = 0.78f,
        accentColorHex = 0xFFFFC857,
    ),
    HARD(
        title = "HARD",
        subtitle = "The Ghost · Fast and reads your escapes",
        emoji = "🔴",
        speedMultiplier = 1.0f,
        accentColorHex = 0xFFFF5252,
    ),
}

data class MultiplayerState(
    val mode: DuelMode = DuelMode.VS_AI_RIVAL,
    val aiDifficulty: AiDifficulty = AiDifficulty.MODERATE,
    val phase: DuelPhase = DuelPhase.MODE_SELECT,
    val round: Int = 1,
    val maxRounds: Int = 3,
    val localRole: MultiplayerRole = MultiplayerRole.PREY,
    val player1Name: String = "Player 1",
    val player2Name: String = "AI Rival",
    val player1RoundsWon: Int = 0,
    val player2RoundsWon: Int = 0,
    val round1TimeMs: Long = 0L,
    val round2TimeMs: Long = 0L,
    val round3TimeMs: Long = 0L,
    val survivalTimeMs: Long = 0L,
    val maxRoundTimeMs: Long = 45_000L, // 45 seconds per round
    val countdownCount: Int = 3,
    val roundAnnouncement: String? = "ROUND 1 — FIGHT!",
    val roomCode: String = "",
    val isWaitingForFriend: Boolean = false,
    val isRoomActivated: Boolean = false,
    val selectedTierIndex: Int = 0,
    val activeChatBubbles: List<QuickChatMessage> = emptyList(),
    val snake: SnakeEntity? = null,
    val prey: PlayerOrb = PlayerOrb(Vec2(400f, 400f)),
    val obstacles: List<Obstacle> = emptyList(),
    val result: DuelMatchResult? = null,
    val isConnecting: Boolean = false,
    val isNetworkMatch: Boolean = false,
    val isHost: Boolean = false,
    val arenaSize: Vec2 = Vec2(800f, 600f),
    /** Round-trip time to the opponent, from the heartbeat. Null when not networked. */
    val latencyMs: Int? = null,
    /** Set when the network opponent left or timed out; the UI shows a forfeit overlay. */
    val opponentLeftReason: String? = null,
    /** Lobby tab: true = Join, false = Host. Lives in state so it survives recomposition. */
    val lobbyShowsJoin: Boolean = false,
    /**
     * Aim point a human-steered snake has already reached. While the pointer stays there the snake
     * keeps its heading instead of circling the point; moving the pointer re-engages steering.
     */
    val snakeAimReached: Vec2? = null,
    /**
     * How fast a human-steered snake can turn (radians per second). Duels use a near-instant
     * turn; Snake Hunt lowers it to make a "heavier" snake that has to plan its approach.
     */
    val humanTurnRate: Float = 36f,
    /** Extra multiplier on the AI prey's speed. 1 in duels; Snake Hunt uses it to pace its stages. */
    val preySpeedScale: Float = 1f,

    /** VS AI, human as prey: a speed orb that shows up now and then to even out a fierce AI snake. */
    val helperOrb: Vec2? = null,

    /** My skins' perks; used only against the AI and in Snake Hunt (online duels stay cosmetic). */
    val localSnakePerk: SnakePerk = SnakePerk.NEUTRAL,
    val localOrbPerk: OrbPerk = OrbPerk.NEUTRAL,

    /** Worn skins in a live match: player 1 is the host, player 2 the guest. */
    val p1Look: DuelNetworkMessage.Look = DuelNetworkMessage.Look(),
    val p2Look: DuelNetworkMessage.Look = DuelNetworkMessage.Look(),
    val helperSpawnedAtMs: Long = 0L,
    /** Time left on the speed boost from the last helper orb. */
    val preyBoostMs: Long = 0L,

    /** Who paused the match (null = running). "You" when it was this device. */
    val pausedBy: String? = null,
    /** Local clock time the pause runs out (0 = no limit, e.g. against the AI). */
    val pauseDeadlineMs: Long = 0L,
    /** Watching someone else's online duel: read-only, no input, chat, rematch, stakes or stats. */
    val isSpectator: Boolean = false,
)

@Serializable
sealed class DuelNetworkMessage {
    @Serializable
    @SerialName("Join")
    data class Join(
        val roomCode: String,
        val guestName: String,
    ) : DuelNetworkMessage()

    @Serializable
    @SerialName("RoomInfo")
    data class RoomInfo(
        val roomCode: String,
        val hostName: String,
        val guestName: String,
        val selectedTierIndex: Int = 0,
    ) : DuelNetworkMessage()

    @Serializable
    @SerialName("StartCountdown")
    data class StartCountdown(
        val round: Int,
        val hostIsSnake: Boolean,
        /** Both players' worn skins, so everyone (rival and spectators) sees the real looks. */
        val hostLook: Look = Look(),
        val guestLook: Look = Look(),
    ) : DuelNetworkMessage()

    /** A player's worn snake and orb skins (enum names; blank = default). */
    @Serializable
    data class Look(val snake: String = "", val orb: String = "") : DuelNetworkMessage()

    @Serializable
    @SerialName("ClientInput")
    data class ClientInput(
        val round: Int,
        val inputPos: Vec2,
        val isDashing: Boolean = false,
        /** Sender's clock: several writes are in flight at once, so the host drops older ones. */
        val sentAtMs: Long = 0L,
    ) : DuelNetworkMessage()

    @Serializable
    @SerialName("ServerStateSync")
    data class ServerStateSync(
        val round: Int,
        val survivalTimeMs: Long,
        val snakeHead: Vec2,
        val snakeAngle: Float,
        val snakeSegments: List<Vec2>,
        val preyPos: Vec2,
        val contactOccurred: Boolean = false,
        val roundOver: Boolean = false,
    ) : DuelNetworkMessage()

    @Serializable
    @SerialName("RoundTransition")
    data class RoundTransition(
        val finishedRound: Int,
        val roundDurationMs: Long,
        val p1RoundsWon: Int,
        val p2RoundsWon: Int,
        val nextRound: Int,
        val nextHostIsSnake: Boolean,
    ) : DuelNetworkMessage()

    @Serializable
    @SerialName("MatchFinished")
    data class MatchFinished(
        val result: DuelMatchResult,
    ) : DuelNetworkMessage()

    @Serializable
    @SerialName("QuickChat")
    data class QuickChat(
        val sender: String,
        val message: String,
        val emoji: String? = null,
    ) : DuelNetworkMessage()

    /** Host → guest: join refused (wrong code, room full). The host closes the socket after. */
    @Serializable
    @SerialName("JoinRejected")
    data class JoinRejected(val reason: String) : DuelNetworkMessage()

    /** Either side: leaving on purpose, so the other shows "left" instead of "connection lost". */
    @Serializable
    @SerialName("Leave")
    data class Leave(val playerName: String) : DuelNetworkMessage()

    /** A player paused the live match; they have [DuelPause.WINDOW_MS] to come back. */
    @Serializable
    data class Pause(val playerName: String) : DuelNetworkMessage()

    @Serializable
    data class Resume(val playerName: String) : DuelNetworkMessage()

    /** After a match: "I want a rematch" (true) or "no thanks" (false). Both must say yes. */
    @Serializable
    @SerialName("RematchVote")
    data class RematchVote(val accept: Boolean) : DuelNetworkMessage()

    /** Heartbeat. `timestamp` is the sender's own monotonic clock; the peer echoes it in Pong. */
    @Serializable
    @SerialName("Ping")
    data class Ping(val timestamp: Long) : DuelNetworkMessage()

    @Serializable
    @SerialName("Pong")
    data class Pong(val timestamp: Long) : DuelNetworkMessage()
}

/** Live online matches: a pause may last this long before the player who paused forfeits. */
object DuelPause {
    const val WINDOW_MS = 10_000L

    /** The other side waits a little longer, so a resume already on its way isn't cut off. */
    const val GRACE_MS = 1_500L
}
