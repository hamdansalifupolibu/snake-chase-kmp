package com.snakechase.app.levels

import com.snakechase.app.domain.model.ObstacleTheme
import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2

/**
 * ❄️ Level 4: Crystal Tundra (Glacial Spire & Isopod Shallows)
 * Reconstructed 1:1 from original web engine specifications (`main.js` lines 1094–1545).
 *
 * Distinct mechanics:
 *  - 6 Sub-Stages (Polar Hush -> Isopod Shallows -> Ice Drift -> Gleam Field -> Glacier Twin -> Crevasse Maze)
 *  - Ice Drift (Stage 3): Predator speed surge multiplier 1.14x.
 *  - Glacier Twin (Stage 5): Twin frost serpents split (Cyan #7EE8FF & Rose Ice #FFB0D8).
 *  - Crevasse Maze (Stage 6): Open-arena twin predator hunt with sharpened predictive tracking.
 *  - Completion at 115s.
 */
val Level4CrystalTundra: LevelConfig = LevelConfig(
    id = 4,
    name = "Crystal Tundra",
    tagline = "Glacial Spire & Frostling Shallows",
    completionTimeMs = 150_000L, // 2:30 — 8 sub-stages
    splitAtSubStage = 4, // "Glacier Twin" split at index 4
    wholeBodyLethalFromSubStage = -1,
    subStages = listOf(
        SubStage(0, "Frost", "Sub-zero quiet frost — teleporters open", 0L, 140),
        SubStage(1, "Freeze", "Slow magma crack across the ice floor", 15_000L, 220),
        SubStage(2, "Shatter", "Rotating bar spins faster", 31_000L, 310),
        SubStage(3, "Split + Tele", "Dual hunters + teleporter disruption active", 48_000L, 410),
        SubStage(4, "Glacier Twin", "Twin frost serpents emerge!", 66_000L, 510),
        SubStage(5, "Crystal Trap", "Two snow whirls sweep the frozen tundra", 85_000L, 620),
        SubStage(6, "Ice Storm", "Icicles fall from the glacier — thin ice spreads", 105_000L, 750),
        SubStage(7, "Tundra Core", "All obstacles — max split aggression!", 127_000L, 900),
    ),
    palette = LevelPalette(
        bgTopArgb = 0xFF050A16,
        bgBottomArgb = 0xFF0A1728,
        gridLineArgb = 0x22143044,
        ambientGlowArgb = 0x337ED8FF,
        arenaBorderArgb = 0xFF7ED8FF,
        primarySnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFEEFFFC),
                ColorStop(0.22f, 0xFF78E6FF),
                ColorStop(0.42f, 0xFF23B4FF),
                ColorStop(0.68f, 0xFF3273FF),
                ColorStop(1.00f, 0x00182D5F),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.14f, 0xFFC8FAFF),
                ColorStop(0.38f, 0xFF4BD2FF),
                ColorStop(0.62f, 0xFF2378FF),
                ColorStop(1.00f, 0x00183778),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x887EE8FF,
            nearMissAlertColorArgb = 0xFFFF5A9E,
        ),
        splitSnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFFFF0F8),
                ColorStop(0.22f, 0xFFFFB0D8),
                ColorStop(0.42f, 0xFFE050A0),
                ColorStop(0.68f, 0xFFA02070),
                ColorStop(1.00f, 0x00400C30),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.14f, 0xFFFFE8F4),
                ColorStop(0.38f, 0xFFFF8CC0),
                ColorStop(0.62f, 0xFFD83088),
                ColorStop(1.00f, 0x00481036),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x88FFB0D8,
            nearMissAlertColorArgb = 0xFF7EE8FF,
        ),
        cursorTheme = CursorThemeConfig(
            orbGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.10f, 0xFFF0FCFF),
                ColorStop(0.32f, 0xFF91E6FF),
                ColorStop(0.56f, 0xFF34AAFF),
                ColorStop(0.82f, 0xFF87C3FF),
                ColorStop(1.00f, 0x0012285A),
            ),
            trailColorArgb = 0x888AE8FF,
            nearMissRingArgb = 0xFF72E8FF,
            perfectRingArgb = 0xFFFFE8B8,
        ),
    ),
    snakeConfig = SnakeSetupConfig(
        headRadius = 14f,
        bodySegments = 8,
        segmentSpacing = 10f,
        personality = SnakePersonalityConfig(
            warmUpDurationMs = 1420L,
            warmUpSpeedFactor = 0.62f,
            baseSpeed = 98f,
            maxSpeed = 312f,
            speedScaling = 3.45f,
            baseTurnRate = 1.82f,
            maxTurnRate = 4.05f,
            turnScaling = 0.035f,
            accelerationRampEndMs = 54_000L,
            lookaheadMs = 290L,
            flankOffsetPx = 148f,
        ),
        splitPersonality = SnakePersonalityConfig(
            warmUpDurationMs = 1000L,
            warmUpSpeedFactor = 0.86f,
            baseSpeed = 110f,
            maxSpeed = 330f,
            speedScaling = 3.65f,
            baseTurnRate = 1.92f,
            maxTurnRate = 4.25f,
            turnScaling = 0.037f,
            accelerationRampEndMs = 54_000L,
            lookaheadMs = 340L,
            flankOffsetPx = -140f,
        ),
    ),
    obstacleConfig = ObstacleSetupConfig(
        rotatingBars = listOf(
            RotatingBarPlacement(id = "ice_spindle", center = Vec2(400f, 300f), length = 152f, rpm = 13f, theme = ObstacleTheme.CYAN, unlockSubStage = 2),
        ),
        movingBlocks = listOf(
            MovingBlockPlacement(id = "ice_floe", size = Vec2(72f, 16f), patrolA = Vec2(140f, 430f), patrolB = Vec2(660f, 430f), speed = 135f, theme = ObstacleTheme.CYAN, unlockSubStage = 2),
        ),
        dangerZones = listOf(
            DangerZonePlacement(id = "brittle_ice", rect = Rect(548f, 196f, 128f, 112f), theme = ObstacleTheme.CYAN, unlockSubStage = 3),
        ),
        teleporters = listOf(
            TeleporterPlacement(id = "frost_rift_teleporter", posA = Vec2(118f, 128f), posB = Vec2(682f, 462f), radius = 26f, theme = ObstacleTheme.CYAN, unlockSubStage = 0),
        ),
        isopods = listOf(
            IsopodPlacement(
                id = "isopod_south",
                initialPos = Vec2(104f, 468f),
                patrol = listOf(Vec2(104f, 468f), Vec2(268f, 496f), Vec2(468f, 488f), Vec2(652f, 464f), Vec2(708f, 404f), Vec2(496f, 364f), Vec2(292f, 384f)),
                speed = 29f,
                radius = 33f,
                unlockSubStage = 1,
            ),
            IsopodPlacement(
                id = "isopod_ne",
                initialPos = Vec2(146f, 108f),
                patrol = listOf(Vec2(146f, 108f), Vec2(426f, 86f), Vec2(632f, 128f), Vec2(724f, 248f), Vec2(656f, 312f), Vec2(468f, 288f), Vec2(292f, 196f)),
                speed = 28f,
                radius = 33f,
                unlockSubStage = 1,
            ),
            IsopodPlacement(
                id = "isopod_west",
                initialPos = Vec2(128f, 168f),
                patrol = listOf(Vec2(128f, 168f), Vec2(132f, 330f), Vec2(152f, 468f), Vec2(268f, 404f), Vec2(232f, 250f)),
                speed = 28f,
                radius = 33f,
                unlockSubStage = 1,
            ),
        ),
        cyclones = listOf(
            CyclonePlacement(
                id = "tundra_cyclone_1", initialPos = Vec2(200f, 200f),
                patrol = listOf(Vec2(200f, 200f), Vec2(600f, 200f), Vec2(600f, 450f), Vec2(200f, 450f)),
                speed = 20f, radius = 100f, hitRadius = 88f, spinSpeed = 1.0f, spinDir = 1f, unlockSubStage = 5,
            ),
            CyclonePlacement(
                id = "tundra_cyclone_2", initialPos = Vec2(600f, 450f),
                patrol = listOf(Vec2(600f, 450f), Vec2(200f, 450f), Vec2(200f, 200f), Vec2(600f, 200f)),
                speed = 20f, radius = 100f, hitRadius = 88f, spinSpeed = 1.0f, spinDir = -1f, unlockSubStage = 5,
            ),
        ),
        fallingEmbers = listOf(
            FallingEmberPlacement(id = "ember_tundra_nw", targetPos = Vec2(200f, 250f), warnRadius = 34f, impactRadius = 46f, unlockSubStage = 6),
            FallingEmberPlacement(id = "ember_tundra_se", targetPos = Vec2(580f, 380f), warnRadius = 34f, impactRadius = 46f, unlockSubStage = 6),
            FallingEmberPlacement(id = "ember_tundra_n", targetPos = Vec2(400f, 160f), warnRadius = 34f, impactRadius = 46f, unlockSubStage = 7),
            FallingEmberPlacement(id = "ember_tundra_s", targetPos = Vec2(380f, 470f), warnRadius = 34f, impactRadius = 46f, unlockSubStage = 7),
        ),
    ),
    scoringConfig = ScoringConfig(
        levelScoreMax = 310,
        levelScoreBase = 231,
        survivalPointsPerSec = 0.28f, // ~42 pts across 150s survival
        nearMissBasePoints = 1,
        completionBonus = 22,
    ),
    stageNarratives = mapOf(
        0 to "Cold slows your mind. Don't let it.",
        1 to "Glacial veins fracture across the ice.",
        2 to "The rotor shears the frozen air.",
        3 to "The frostbite seeps in.",
        4 to "Twin specters rise from the drift.",
        5 to "Blizzard vortexes enclose the arena.",
        6 to "The glacier cracks: icicles rain on endless winter.",
        7 to "The tundra breaks the weak.",
    ),
)
