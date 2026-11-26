package com.snakechase.app.levels

import com.snakechase.app.domain.model.ObstacleTheme
import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2

data class SubStage(
    val index: Int,
    val name: String,
    val subtitle: String = "",
    val startAtMs: Long,
    val bonus: Int = 500,
)

data class ColorStop(val position: Float, val colorArgb: Long)

data class SnakeThemeConfig(
    val bodyGradient: List<ColorStop>,
    val headGradient: List<ColorStop>,
    val eyeColorArgb: Long = 0xFFFFFFFF,
    val energyLinkColorArgb: Long = 0x9900FFFF,
    val nearMissAlertColorArgb: Long = 0xFFFF3366,
)

data class CursorThemeConfig(
    val orbGradient: List<ColorStop>,
    val trailColorArgb: Long = 0x8800FFFF,
    val nearMissRingArgb: Long = 0xFF00FFFF,
    val perfectRingArgb: Long = 0xFFFFCC00,
)

data class LevelPalette(
    val bgTopArgb: Long,
    val bgBottomArgb: Long,
    val gridLineArgb: Long,
    val ambientGlowArgb: Long,
    val arenaBorderArgb: Long,
    val primarySnakeTheme: SnakeThemeConfig,
    val splitSnakeTheme: SnakeThemeConfig,
    val cursorTheme: CursorThemeConfig,
    val splitSnakeTheme2: SnakeThemeConfig? = null,
)

data class SnakePersonalityConfig(
    val warmUpDurationMs: Long = 1500L,
    val warmUpSpeedFactor: Float = 0.60f,
    val baseSpeed: Float = 100f,
    val maxSpeed: Float = 320f,
    val speedScaling: Float = 3.5f,
    val baseTurnRate: Float = 1.8f,
    val maxTurnRate: Float = 4.0f,
    val turnScaling: Float = 0.035f,
    val accelerationRampEndMs: Long = 63_000L,
    val lookaheadMs: Long = 300L,
    val flankOffsetPx: Float = 0f,
    val flankDurationMs: Long = 4000L,
    val turnRateRadPerSec: Float = 4.0f,
    val speedMultiplier: Float = 1.0f,
)

data class SnakeSetupConfig(
    val headRadius: Float = 14f,
    val bodySegments: Int = 8,
    val segmentSpacing: Float = 10f,
    val personality: SnakePersonalityConfig = SnakePersonalityConfig(),
    val splitPersonality: SnakePersonalityConfig = SnakePersonalityConfig(flankOffsetPx = -80f),
    val splitPersonality2: SnakePersonalityConfig? = null,
    val splitCount: Int = 1,
    val initialSegments: Int = 3,
    val segmentsPerSubStage: Int = 2,
)

data class WallPlacement(
    val id: String,
    val rect: Rect,
    val theme: ObstacleTheme = ObstacleTheme.CYAN,
    val unlockSubStage: Int = 0,
)

data class RotatingBarPlacement(
    val id: String,
    val center: Vec2,
    val length: Float = 180f,
    val rpm: Float = 12f,
    val theme: ObstacleTheme = ObstacleTheme.VIOLET,
    val unlockSubStage: Int = 0,
)

data class MovingBlockPlacement(
    val id: String,
    val size: Vec2 = Vec2(48f, 48f),
    val patrolA: Vec2,
    val patrolB: Vec2,
    val speed: Float = 110f,
    val theme: ObstacleTheme = ObstacleTheme.AMBER,
    val unlockSubStage: Int = 0,
)

data class DangerZonePlacement(
    val id: String,
    val rect: Rect,
    val theme: ObstacleTheme = ObstacleTheme.AMBER,
    val unlockSubStage: Int = 0,
)

data class TeleporterPlacement(
    val id: String,
    val posA: Vec2,
    val posB: Vec2,
    val radius: Float = 32f,
    val theme: ObstacleTheme = ObstacleTheme.CYAN,
    val unlockSubStage: Int = 0,
)

data class SharkPlacement(
    val id: String,
    val initialPos: Vec2,
    val patrol: List<Vec2>,
    val speed: Float = 136f,
    val radius: Float = 22f,
    val unlockSubStage: Int = 2,
)

data class CyclonePlacement(
    val id: String,
    val initialPos: Vec2,
    val patrol: List<Vec2>,
    val speed: Float = 18f,
    val radius: Float = 111f,
    val hitRadius: Float = 97f,
    val spinSpeed: Float = 1.05f,
    val spinDir: Float = 1f,
    val unlockSubStage: Int = 5,
)

data class JellyfishPlacement(
    val id: String,
    val initialPos: Vec2,
    val patrol: List<Vec2>,
    val speed: Float = 40f,
    val radius: Float = 12f,
    val unlockSubStage: Int = 1,
)

data class ForestProwlerPlacement(
    val id: String,
    val initialPos: Vec2,
    val patrol: List<Vec2>,
    val speed: Float = 34f,
    val radius: Float = 33f,
    val unlockSubStage: Int = 4,
)

data class IsopodPlacement(
    val id: String,
    val initialPos: Vec2,
    val patrol: List<Vec2>,
    val speed: Float = 28f,
    val radius: Float = 33f,
    val unlockSubStage: Int = 1,
)

data class MagmaCrackPlacement(
    val id: String,
    val center: Vec2 = Vec2(400f, 300f),
    val startPos: Vec2 = center,
    val endPos: Vec2 = center,
    val width: Float = 16f,
    val seed: Long = 0L,
    val unlockSubStage: Int = 1,
)

data class FallingEmberPlacement(
    val id: String,
    val targetPos: Vec2,
    val warnRadius: Float = 34f,
    val impactRadius: Float = 46f,
    val unlockSubStage: Int = 2,
)

data class EscapeDoorPlacement(
    val id: String = "crucible_escape_door",
    val position: Vec2 = Vec2(788f, 54f),
    val width: Float = 70f,
    val height: Float = 70f,
    val unlockSubStage: Int = 4,
)

data class ObstacleSetupConfig(
    val walls: List<WallPlacement> = emptyList(),
    val rotatingBars: List<RotatingBarPlacement> = emptyList(),
    val movingBlocks: List<MovingBlockPlacement> = emptyList(),
    val dangerZones: List<DangerZonePlacement> = emptyList(),
    val teleporters: List<TeleporterPlacement> = emptyList(),
    val sharks: List<SharkPlacement> = emptyList(),
    val cyclones: List<CyclonePlacement> = emptyList(),
    val jellyfish: List<JellyfishPlacement> = emptyList(),
    val forestProwlers: List<ForestProwlerPlacement> = emptyList(),
    val isopods: List<IsopodPlacement> = emptyList(),
    val magmaCracks: List<MagmaCrackPlacement> = emptyList(),
    val fallingEmbers: List<FallingEmberPlacement> = emptyList(),
    val escapeDoor: EscapeDoorPlacement? = null,
)

data class ScoringConfig(
    /** Maximum score achievable for this level (e.g. 100 for Level 1, 160 for Level 2). */
    val levelScoreMax: Int = 100,
    /** Starting band for this level's score (e.g. 1 for Level 1, 101 for Level 2). */
    val levelScoreBase: Int = 1,
    /** Survival points accumulated per second of survival (scaled into the 50% survival band). */
    val survivalPointsPerSec: Float = 0.28f,
    /** Base points awarded for each near-miss graze event. */
    val nearMissBasePoints: Int = 1,
    /** Extra multiplier applied on perfect near-misses (<= 14px edge distance). */
    val perfectMultiplier: Float = 2.0f,
    /** Points awarded per sub-stage cleared (summed at end). */
    val completionBonus: Int = 20,
    /** Bonus applied at level completion if shield orb was never collected. */
    val shieldUnusedBonus: Int = 10,
    /** Combo decay window in ms before combo resets to x1. */
    val comboDecayMs: Long = 4000L,
    /** Most points grazing can add up to in one run (see ScoreBudget). */
    val nearMissCap: Int = Int.MAX_VALUE,
    /** Combo multiplier tiers: index = tier, value = multiplier. */
    val comboTiers: List<Float> = listOf(1.0f, 1.5f, 2.0f, 3.0f),
)

data class LevelConfig(
    val id: Int,
    val name: String,
    val tagline: String,
    val completionTimeMs: Long,
    val subStages: List<SubStage>,
    val palette: LevelPalette,
    val snakeConfig: SnakeSetupConfig,
    val obstacleConfig: ObstacleSetupConfig,
    val scoringConfig: ScoringConfig = ScoringConfig(),
    val splitAtSubStage: Int = -1,
    val wholeBodyLethalFromSubStage: Int = -1,
    val stageNarratives: Map<Int, String> = emptyMap(),
)
