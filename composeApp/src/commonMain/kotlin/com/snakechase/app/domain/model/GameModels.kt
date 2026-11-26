package com.snakechase.app.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─── Core 2D Math ─────────────────────────────────────────────────────────────

@Serializable
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)
    operator fun div(scalar: Float): Vec2 = if (scalar != 0f) Vec2(x / scalar, y / scalar) else ZERO

    fun distanceTo(other: Vec2): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun length(): Float = sqrt(x * x + y * y)

    fun lengthSquared(): Float = x * x + y * y

    fun normalized(): Vec2 {
        val len = length()
        return if (len < 0.0001f) ZERO else Vec2(x / len, y / len)
    }

    fun dot(other: Vec2): Float = x * other.x + y * other.y

    companion object {
        val ZERO = Vec2(0f, 0f)
        fun fromAngle(angleRad: Float, length: Float = 1f): Vec2 = Vec2(cos(angleRad) * length, sin(angleRad) * length)
    }
}

@Serializable
data class Rect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val left: Float get() = x
    val top: Float get() = y
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
    val center: Vec2 get() = Vec2(centerX, centerY)

    fun contains(point: Vec2): Boolean = point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
}

// ─── Game Phase ───────────────────────────────────────────────────────────────

enum class GamePhase {
    MENU,
    COUNTDOWN,
    PLAYING,
    PAUSED,
    LEVEL_COMPLETE,
    LEVEL_VICTORY, // Full victory celebration screen with reward
    GAME_OVER,
    MULTIPLAYER_QUEUE,
    MULTIPLAYER_PLAYING,
    SNAKE_HUNT, // Phase C: play as the snake
}

// ─── Player Entity ────────────────────────────────────────────────────────────

data class PlayerOrb(
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val targetPosition: Vec2 = position,
    val trailPoints: List<Vec2> = emptyList(),
    val radius: Float = 18f,
    val nearMissActive: Boolean = false,
    val nearMissPerfect: Boolean = false,
    val nearMissIntensity: Float = 0f,
    val pulsePhase: Float = 0f,
    val deathAnimProgress: Float = -1f, // -1 = alive, 0..1 = dying animation
    val scale: Float = 1f,
    val isDashing: Boolean = false,
    val dashCooldownMs: Long = 0L,
    val dashRemainingMs: Long = 0L,
    val dashTrailHistory: List<Vec2> = emptyList(),
)

// ─── Snake Entity & AI ────────────────────────────────────────────────────────

enum class SnakeRole { PRIMARY, SPLIT }
enum class SnakeMode { CHASE, FLANK, INTERCEPT, WANDER, STUNNED }

data class SnakeSegment(
    val position: Vec2,
    val angle: Float = 0f,
)

data class SnakeEntity(
    val role: SnakeRole,
    val headPosition: Vec2,
    val segments: List<SnakeSegment>,
    val headAngle: Float = 0f,
    val speed: Float = 160f,
    val targetSpeed: Float = 160f,
    val mode: SnakeMode = SnakeMode.CHASE,
    val alertLevel: Float = 0f, // 0..1 for visual alert FX
    val headRadius: Float = 16f,
    val bodyRadius: Float = 14f,
    val history: List<Vec2> = emptyList(),
    val isVisible: Boolean = true,
    val stunRemainingMs: Long = 0L,
) {
    val isStunned: Boolean get() = stunRemainingMs > 0L
}

// ─── Scoring & Combo ──────────────────────────────────────────────────────────

data class ComboState(
    val multiplier: Float = 1f,
    val tier: Int = 0,
    val count: Int = 0,
    val remainingTimeMs: Long = 0L,
    val maxDurationMs: Long = 4000L,
)

data class ScoreBreakdown(
    val survivalPoints: Int = 0,
    val stageBonusPoints: Int = 0,
    val nearMissPoints: Int = 0,
    val completionBonus: Int = 0,
)

data class ScoreState(
    val total: Int = 0,
    val breakdown: ScoreBreakdown = ScoreBreakdown(),
    val combo: ComboState = ComboState(),
    val personalBest: Int = 0,
)

// ─── HUD & Feedback ───────────────────────────────────────────────────────────

data class StageToast(
    val title: String,
    val subtitle: String,
    val bonus: Int,
    val remainingMs: Long,
    val narrative: String? = null,
)

data class ArenaTelegraph(
    val message: String,
    val active: Boolean = false,
    val intensity: Float = 0f,
)

data class HudState(
    val currentSubStage: Int = 0,
    val subStageName: String = "Warm Up",
    val totalSubStages: Int = 10,
    val stageToast: StageToast? = null,
    val arenaTelegraph: ArenaTelegraph = ArenaTelegraph(""),
    val survivalTimeMs: Long = 0L,
    val timeRemainingMs: Long = 240_000L,
)

// ─── Master Game State (Immutable Snapshot) ───────────────────────────────────

data class GameState(
    val phase: GamePhase = GamePhase.MENU,
    val levelId: Int = 1,
    /** The difficulty this run started on (locked in, so changing Settings mid-run can't game it). */
    val runDifficulty: Difficulty = Difficulty.RECOMMENDED,
    /** Easy only: shield orbs handed out in the current stage, and when the next may appear. */
    val easyShieldsThisStage: Int = 0,
    val easyShieldNextMs: Long = 0L,
    /** Easy only: the stage whose one rescue was used (-1 = none yet). */
    val easyRescueStage: Int = -1,
    /** Hearts left at game over. */
    val reviveHearts: Int = 0,
    /** This run can be revived (not a daily trial); with 0 hearts the panel offers to buy more. */
    val reviveOffered: Boolean = false,
    /** Time until the next free heart (null = hearts are full). */
    val nextHeartInMs: Long? = null,
    val player: PlayerOrb = PlayerOrb(Vec2(400f, 300f)),
    val primarySnake: SnakeEntity? = null,
    val splitSnake: SnakeEntity? = null,
    val extraSnakes: List<SnakeEntity> = emptyList(),
    val obstacles: List<Obstacle> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val hud: HudState = HudState(),
    val score: ScoreState = ScoreState(),
    val arenaSize: Vec2 = Vec2(800f, 600f),
    val countdownNumber: Int = 3,
    val unlockedLevelIds: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** Contest build: everything is open for judges (config/Edition.kt). */
    val judgeEdition: Boolean = false,
    val bestScores: Map<Int, Int> = emptyMap(),
    val bestTimes: Map<Int, Long> = emptyMap(),
    val judgeModeActive: Boolean = false,
    val godModeEnabled: Boolean = false,
    val devTargetLevelId: Int = 1,
    val devTestedStages: Set<Int> = emptySet(),
    val devResetScoreOnJump: Boolean = true,
    val devSoloStageMode: Boolean = false,
    val devInvincibleUntilMs: Long = 0L,
    val devPanelCollapsed: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val showSettingsOverlay: Boolean = false,
    val showAboutOverlay: Boolean = false,
    val showLeaderboardOverlay: Boolean = false,
    val showBestiaryOverlay: Boolean = false,
    val showSkinSelectOverlay: Boolean = false,
    val lastRoastMeme: String? = null,
    val deathsSinceLastRoast: Int = 0,
    // ── Phase 1: Victory & Retention ──────────────────────────────────────────
    /** Result populated when phase = LEVEL_VICTORY. */
    val levelResult: LevelResult? = null,
    /** Deaths accumulated in current sub-stage — drives progressive retry states. */
    val subStageDeaths: Int = 0,
    /** Star ratings per level (1–3), persisted across sessions via SettingsRepository. */
    val levelStarRatings: Map<Int, Int> = emptyMap(),
    /** Set of SnakeSkin enum names that have been unlocked. */
    val unlockedSkins: Set<String> = setOf(SnakeSkin.DEFAULT.name),
    /** Currently equipped snake skin. */
    val activeSnakeSkin: SnakeSkin = SnakeSkin.DEFAULT,
    /** Currently equipped orb skin. */
    val activeOrbSkin: OrbSkin = OrbSkin.SPARK,
    /** Orb skins this run just earned, waiting to be celebrated on the result screen. */
    val newOrbSkins: List<OrbSkin> = emptyList(),
    // ── Player profile & wallet ───────────────────────────────────────────────
    val profile: com.snakechase.app.profile.PlayerProfile = com.snakechase.app.profile.PlayerProfile(),
    /** Coins earned by the run that just ended (shown on the result screens). */
    val lastRunCoins: Int = 0,
    /** Today's daily reward, shown once until dismissed. */
    val dailyClaim: com.snakechase.app.profile.DailyClaim? = null,
    val showProfileOverlay: Boolean = false,
    // ── Phase 2: Shield Power-Up Orb ──────────────────────────────────────────
    val shieldOrb: ShieldOrb? = null,
    val shieldState: ShieldState = ShieldState(),
    val shieldEverUsed: Boolean = false,
    // ── Phase 3: Dynamic Haptics, Adrenaline Bullet Time & Greed Multiplier ──
    val heartbeatAccumulatorMs: Long = 0L,
    val comboMultiplier: Float = 1.0f,
    val comboChargePct: Float = 0.0f,
    val bulletTimeRemainingMs: Long = 0L,
    // ── Juice Layer: Screen Shake, Stage Clear Flash & Death Slow-Mo ──
    val screenShakeRemainingMs: Long = 0L,
    val screenShakeIntensity: Float = 0f,
    val subStageClearAnimMs: Long = 0L,
    val subStageClearIndex: Int = 0,
    val deathSlowMoRemainingMs: Long = 0L,
    // ── Phase 3: ON FIRE Streak System ──
    val nearMissStreakCount: Int = 0,
    /** Counts down after a scored graze; a new graze only scores once it reaches 0 (one per pass). */
    val grazeCooldownMs: Long = 0L,
    val streakWindowRemainingMs: Long = 0L,
    val isOnFire: Boolean = false,
    // ── Phase 4: Ambient World Particles ──
    val ambientParticles: List<Particle> = emptyList(),
    // ── Phase 5: Power-Up Variety ──
    val activePowerUpOrb: PowerUpOrb? = null,
    val activePowerUps: List<ActivePowerUp> = emptyList(),
    // ── Phase 9: Daily Challenge Mode ──
    val isDailyChallenge: Boolean = false,
    val dailyChallengeInfo: DailyChallengeInfo? = null,
) {
    val allSnakes: List<SnakeEntity>
        get() = listOfNotNull(primarySnake, splitSnake) + extraSnakes
}

// ─── Sound & Haptic Enums ─────────────────────────────────────────────────────

enum class SoundId {
    NEAR_MISS,
    PERFECT_NEAR_MISS,
    DEATH,
    STAGE_ADVANCE,
    LEVEL_COMPLETE,
    SPLIT_SPAWN,
    TELEPORT,
    BUTTON_CLICK,
    DASH,
    CONCUSSION,
    BULLET_TIME,
    ON_FIRE,
    STREAK_BROKEN,
    POWER_UP,
    COUNTDOWN_TICK,
    COUNTDOWN_GO,
    STAR_1,
    STAR_2,
    STAR_3,
    UNLOCK,
}

enum class HapticStyle {
    LIGHT,
    MEDIUM,
    HEAVY,
    HEARTBEAT_CALM,
    HEARTBEAT_URGENT,
    ADRENALINE_SNAP,
    CONCUSSION_RUMBLE,
    DASH_BURST,
    STREAK_FIRE,
}

// ─── Snake Skin Cosmetics ─────────────────────────────────────────────────────

/**
 * Cosmetic snake skins unlocked by completing levels.
 * Applied visually via SnakeRenderer — no gameplay effect.
 */
/** [SnakeSkin.threeStarGoal] value meaning "three stars on every level". */
const val ALL_LEVELS_GOAL = 6

/** How rare a skin is: sets its colour in the shop and how it's celebrated when unlocked. */
enum class SkinRarity(val label: String) { COMMON("Common"), RARE("Rare"), EPIC("Epic"), LEGENDARY("Legendary") }

/**
 * Snake skins. Each is drawn in code with its own pattern (see ui/canvas/SkinPainter.kt).
 * Names are saved in player profiles, so existing entries must keep their names.
 */
enum class SnakeSkin(
    val displayName: String,
    val description: String,
    val rarity: SkinRarity,
    /** Clearing this level unlocks it for free (0 = no level). */
    val unlockedByLevel: Int = 0,
    /** Shop price in coins; null = can only be earned. */
    val price: Int? = null,
    /** Earned with three stars on this level; [ALL_LEVELS_GOAL] = on every level; 0 = none. */
    val threeStarGoal: Int = 0,
) {
    DEFAULT("Neon Classic", "The original glowing plasma serpent.", SkinRarity.COMMON, price = 0),
    CANDY_POP("Candy Pop", "Bubblegum stripes. Deadly, but sweet.", SkinRarity.COMMON, price = 200),
    CORAL_KRAIT("Coral Krait", "Red, black and yellow bands: a warning to all prey.", SkinRarity.COMMON, price = 250),
    NEON_GHOST("Neon Ghost", "A see-through cyan body around a glowing spine.", SkinRarity.RARE, unlockedByLevel = 1, price = 300),
    NEON_TUBE("Neon Tube", "A hollow tube of hot pink neon.", SkinRarity.RARE, price = 450),
    FOREST_PHANTOM("Forest Phantom", "Moss-green scales speckled with glowing spores.", SkinRarity.RARE, unlockedByLevel = 2, price = 600),
    ABYSS_SERPENT("Abyss Serpent", "Ink-blue bands lit by deep-sea glow spots.", SkinRarity.EPIC, unlockedByLevel = 3, price = 900),
    ICE_CRYSTAL("Ice Crystal", "A frosted body set with ice shards that glint.", SkinRarity.EPIC, unlockedByLevel = 4, price = 1200),
    FEARLESS("Fearless", "Blood red, slashed with black zigzags.", SkinRarity.EPIC, threeStarGoal = 3),
    CRUCIBLE_TITAN("Crucible Titan", "Volcanic rock with molten cracks that pulse.", SkinRarity.LEGENDARY, unlockedByLevel = 5, price = 1500),
    PERFECT_DRIFT("Perfect Drift", "Every scale shifts through the rainbow.", SkinRarity.LEGENDARY, threeStarGoal = 1),
    THE_ENDGAME("The Endgame", "A slice of night sky, with stars drifting inside.", SkinRarity.LEGENDARY, threeStarGoal = 5),
    GOLDEN_IDOL("Golden Idol", "Solid, sparkling gold.", SkinRarity.LEGENDARY, threeStarGoal = ALL_LEVELS_GOAL),
    ;

    /** One line on how to get it, for the shop. */
    val unlockHint: String
        get() = when {
            this == DEFAULT -> "Yours from the start"
            threeStarGoal == ALL_LEVELS_GOAL -> "Earn 3 stars on every level"
            threeStarGoal > 0 -> "Earn 3 stars on Level $threeStarGoal"
            unlockedByLevel > 0 -> "Clear Level $unlockedByLevel, or buy it"
            else -> "Buy it in the shop"
        }

    companion object {
        /** Star skins whose goal is met by these best ratings (level id → stars). */
        fun earnedByStars(levelStars: Map<Int, Int>): List<SnakeSkin> = entries.filter { skin ->
            when (val goal = skin.threeStarGoal) {
                0 -> false
                ALL_LEVELS_GOAL -> (1..5).all { (levelStars[it] ?: 0) >= 3 }
                else -> (levelStars[goal] ?: 0) >= 3
            }
        }
    }
}

// ─── Level Result ─────────────────────────────────────────────────────────────

/**
 * Captures the result of a completed level run for the victory screen.
 * Stars: 1 = cleared, 2 = 70%+ score, 3 = 90%+ score AND no shield used.
 */
data class LevelResult(
    val levelId: Int,
    val finalScore: Int,
    val levelScoreMax: Int,
    val stars: Int, // 1, 2 or 3
    val skinUnlocked: SnakeSkin? = null, // null if already owned
    /** Star skins earned by this clear (e.g. three stars on Level 1). */
    val bonusSkins: List<SnakeSkin> = emptyList(),
    val isNewPersonalBest: Boolean = false,
    val stagesCleared: Int = 0,
    val totalStages: Int = 0,
    val shieldUsed: Boolean = false,
    /** The level had never been cleared before this run. */
    val firstClear: Boolean = false,
    /** This clear was the last world still uncleared: time for the grand finale. */
    val allLevelsCleared: Boolean = false,
    /** Best stars summed over all five levels after this clear (out of 15). */
    val totalStars: Int = 0,
)

// ─── Shield State & Orb (Phase 2 — Power-Up Orb) ──────────────────────────────

data class ShieldState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val maxMs: Long = 5000L,
)

data class ShieldOrb(
    val position: Vec2,
    val radius: Float = 22f,
    val spawnTimeMs: Long = 0L,
    val lifetimeMs: Long = 18_000L,
    val isCollected: Boolean = false,
    val pulsePhase: Float = 0f,
)

// ─── Phase 5: Power-Up Variety ───────────────────────────────────────────────

enum class PowerUpType(
    val displayName: String,
    val colorArgb: Long,
    val durationMs: Long,
    val emoji: String,
) {
    SHIELD("INVINCIBLE SHIELD", 0xFF00F5FF, 5_000L, "🛡️"),
    SPEED_BURST("SPEED BURST", 0xFF00FF88, 4_000L, "⚡"),
    SCORE_MAGNET("SCORE MAGNET", 0xFFFFD700, 6_000L, "🧲"),
    GHOST_WALK("GHOST WALK", 0xFFB388FF, 3_500L, "👻"),
}

data class PowerUpOrb(
    val type: PowerUpType,
    val position: Vec2,
    val radius: Float = 22f,
    val spawnTimeMs: Long = 0L,
    val lifetimeMs: Long = 14_000L,
    val isCollected: Boolean = false,
    val pulsePhase: Float = 0f,
)

data class ActivePowerUp(
    val type: PowerUpType,
    val remainingMs: Long,
    val maxMs: Long,
)
