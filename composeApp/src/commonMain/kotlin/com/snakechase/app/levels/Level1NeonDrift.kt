package com.snakechase.app.levels

import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2

val Level1NeonDrift: LevelConfig = LevelConfig(
    id = 1,
    name = "Neon Drift",
    tagline = "Digital Cyberpunk Arena",
    completionTimeMs = 60_000L, // 1:00 — 6 sub-stages
    splitAtSubStage = 3, // "Flanked" split at index 3
    subStages = listOf(
        SubStage(0, "Warm Up", "Calibrate your movement", 0L, 80),
        SubStage(1, "Pressure", "Predator speed accelerating", 8_000L, 130),
        SubStage(2, "Drift", "Use open space wisely", 17_000L, 170),
        SubStage(3, "Flanked", "Warning: Dual Hunters active!", 27_000L, 220),
        SubStage(4, "Dodge", "Static defense walls unlocked", 38_000L, 270),
        SubStage(5, "Survive", "Final endurance push — hold on!", 49_000L, 330),
    ),
    palette = LevelPalette(
        bgTopArgb = 0xFF050B18,
        bgBottomArgb = 0xFF0A1E33,
        gridLineArgb = 0x220E2236,
        ambientGlowArgb = 0x4D35D7FF,
        arenaBorderArgb = 0xFF35D7FF,
        primarySnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFF8FCFF),
                ColorStop(0.22f, 0xEB96E6FF),
                ColorStop(0.42f, 0xD130C8FF),
                ColorStop(0.68f, 0x7A2878FF),
                ColorStop(0.92f, 0x295A3CFF),
                ColorStop(1.00f, 0x008A5CFF),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.12f, 0xFFC8F5FF),
                ColorStop(0.32f, 0xEB50D2FF),
                ColorStop(0.52f, 0xAD3796FF),
                ColorStop(0.78f, 0x528246FF),
                ColorStop(1.00f, 0x003C28B4),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x8035D7FF,
            nearMissAlertColorArgb = 0xFFFF6B4A,
        ),
        splitSnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFFFF0F8),
                ColorStop(0.22f, 0xFFFFB3E6),
                ColorStop(0.42f, 0xFFFF4DC9),
                ColorStop(0.68f, 0xCCBA007C),
                ColorStop(0.92f, 0x597A004B),
                ColorStop(1.00f, 0x0045002A),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.15f, 0xFFFFE0F2),
                ColorStop(0.35f, 0xFFFF66D4),
                ColorStop(0.55f, 0xFFFF1493),
                ColorStop(0.80f, 0x99B30066),
                ColorStop(1.00f, 0x00660033),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x80FF4DC9,
            nearMissAlertColorArgb = 0xFFFFC857,
        ),
        cursorTheme = CursorThemeConfig(
            orbGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.08f, 0xFAF0FAFF),
                ColorStop(0.28f, 0xE078E6FF),
                ColorStop(0.52f, 0x9E2DB4FF),
                ColorStop(0.75f, 0x475F4BFF),
                ColorStop(0.92f, 0x1FC85AFF),
                ColorStop(1.00f, 0x00281E78),
            ),
            trailColorArgb = 0x8035D7FF,
            nearMissRingArgb = 0xFF35D7FF,
            perfectRingArgb = 0xFFFFC857,
        ),
    ),
    snakeConfig = SnakeSetupConfig(
        headRadius = 14f,
        bodySegments = 8,
        segmentSpacing = 10f,
        personality = SnakePersonalityConfig(
            warmUpDurationMs = 1500L,
            warmUpSpeedFactor = 0.60f,
            baseSpeed = 100f,
            maxSpeed = 320f,
            speedScaling = 3.5f,
            baseTurnRate = 1.8f,
            maxTurnRate = 4.0f,
            turnScaling = 0.035f,
            lookaheadMs = 300L,
            flankOffsetPx = 0f,
        ),
        splitPersonality = SnakePersonalityConfig(
            warmUpDurationMs = 1500L,
            warmUpSpeedFactor = 0.85f,
            baseSpeed = 110f,
            maxSpeed = 320f,
            speedScaling = 3.5f,
            baseTurnRate = 2.0f,
            maxTurnRate = 4.2f,
            turnScaling = 0.035f,
            lookaheadMs = 350L,
            flankOffsetPx = -80f,
        ),
    ),
    obstacleConfig = ObstacleSetupConfig(
        walls = listOf(
            WallPlacement("wall_1", Rect(145f, 214f, 110f, 12f), unlockSubStage = 4),
            WallPlacement("wall_2", Rect(545f, 374f, 110f, 12f), unlockSubStage = 4),
        ),
        rotatingBars = listOf(
            RotatingBarPlacement("bar_center", center = Vec2(400f, 300f), length = 160f, rpm = 10f, unlockSubStage = 5),
        ),
        movingBlocks = listOf(
            MovingBlockPlacement(
                "block_mover",
                size = Vec2(80f, 16f),
                patrolA = Vec2(150f, 450f),
                patrolB = Vec2(650f, 450f),
                speed = 200f,
                unlockSubStage = 5,
            ),
        ),
        dangerZones = listOf(
            DangerZonePlacement("zone_core", Rect(485f, 165f, 130f, 110f), unlockSubStage = 5),
        ),
        teleporters = listOf(
            TeleporterPlacement(
                "portal_pair_1",
                posA = Vec2(120f, 130f),
                posB = Vec2(680f, 460f),
                radius = 28f,
                unlockSubStage = 5,
            ),
        ),
    ),
    scoringConfig = ScoringConfig(
        levelScoreMax = 100,
        levelScoreBase = 1,
        survivalPointsPerSec = 0.83f, // ~50 pts across 60s survival
        nearMissBasePoints = 1,
        completionBonus = 20, // spread across 6 stage clears
    ),
    stageNarratives = mapOf(
        0 to "The grid awakens. Stay sharp.",
        1 to "Predator algorithms accelerating.",
        2 to "The circuit hunter adapts.",
        3 to "Dual signatures detected. Flank incoming!",
        4 to "Static grid defense online.",
        5 to "Prove you belong in the neon.",
    ),
)
