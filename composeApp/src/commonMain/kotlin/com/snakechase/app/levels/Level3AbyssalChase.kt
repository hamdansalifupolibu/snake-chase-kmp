package com.snakechase.app.levels

import com.snakechase.app.domain.model.ObstacleTheme
import com.snakechase.app.domain.model.Vec2

/**
 * 🌊 Level 3: Abyssal Chase (Deep Ocean Trench)
 * Reconstructed 1:1 from original web engine specifications (`main.js` lines 691–1093).
 *
 * Distinct mechanics:
 *  - 7 Sub-Stages (Surface Drift -> Undertow -> Shark Curtain -> Dual Current -> Tangled Deep -> Cyclone -> Frenzy)
 *  - Dual Current (Stage 4): Twin deep-sea bioluminescent serpents (Cyan #5CF0FF & Coral Magenta #FF7AB8).
 *  - Lethal Roaming Cyclones: Giant whirlpool vortices patrolling the abyss floor.
 *  - Trench barriers: Deep ocean walls, rotating vortex bar, horizontal seabed mover, and dual vortex gates.
 *  - Completion at 132s.
 */
val Level3AbyssalChase: LevelConfig = LevelConfig(
    id = 3,
    name = "Abyssal Chase",
    tagline = "Deep Ocean Trench",
    completionTimeMs = 120_000L, // 2:00 — 7 sub-stages
    splitAtSubStage = 2, // "Split" — dual hunters active at index 2 (earlier than before)
    wholeBodyLethalFromSubStage = -1,
    subStages = listOf(
        SubStage(0, "Descent", "Descending into the deep abyss", 0L, 150),
        SubStage(1, "Pressure", "Bio-electric jellyfish & sharks emerge", 14_000L, 240),
        SubStage(2, "Split", "Twin deep-sea serpents split — dodge fast!", 29_000L, 340),
        SubStage(3, "Current", "A whirlpool drifts in: it drags you and the snakes around", 45_000L, 460),
        SubStage(4, "Storm", "Second shark + tighter patrol lines", 62_000L, 590),
        SubStage(5, "Abyss", "An anchor chain swings beside the whirlpool", 80_000L, 730),
        SubStage(6, "Void", "The deep wakes up: maximum predation pace!", 99_000L, 900),
    ),
    palette = LevelPalette(
        bgTopArgb = 0xFF040A14,
        bgBottomArgb = 0xFF05182A,
        gridLineArgb = 0x220C2842,
        ambientGlowArgb = 0x334AE0FF,
        arenaBorderArgb = 0xFF4AE0FF,
        primarySnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFEEFFFC),
                ColorStop(0.24f, 0xFF5ADCEF),
                ColorStop(0.46f, 0xFF23A0FF),
                ColorStop(0.72f, 0xFF325FFF),
                ColorStop(1.00f, 0x0018285A),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.14f, 0xFFC8FAFF),
                ColorStop(0.36f, 0xFF46D2FF),
                ColorStop(0.62f, 0xFF2A78FF),
                ColorStop(1.00f, 0x001E3278),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x885CF0FF,
            nearMissAlertColorArgb = 0xFFFF5A9E,
        ),
        splitSnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFFFF0F7),
                ColorStop(0.24f, 0xFFFF7AB8),
                ColorStop(0.46f, 0xFFD8308C),
                ColorStop(0.72f, 0xFF991866),
                ColorStop(1.00f, 0x00400828),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.14f, 0xFFFFE4F0),
                ColorStop(0.36f, 0xFFFF68AE),
                ColorStop(0.62f, 0xFFC8207E),
                ColorStop(1.00f, 0x004A0A30),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x88FF7AB8,
            nearMissAlertColorArgb = 0xFF5CF0FF,
        ),
        cursorTheme = CursorThemeConfig(
            orbGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.10f, 0xFFF0FCFF),
                ColorStop(0.32f, 0xFF8CE6FF),
                ColorStop(0.56f, 0xFF37B4FF),
                ColorStop(0.82f, 0xFF7896FF),
                ColorStop(1.00f, 0x00142850),
            ),
            trailColorArgb = 0x886EE8FF,
            nearMissRingArgb = 0xFF5AE8FF,
            perfectRingArgb = 0xFFFFE8A8,
        ),
    ),
    snakeConfig = SnakeSetupConfig(
        headRadius = 14f,
        bodySegments = 8,
        segmentSpacing = 10f,
        personality = SnakePersonalityConfig(
            warmUpDurationMs = 1450L,
            warmUpSpeedFactor = 0.76f,
            baseSpeed = 92f,
            maxSpeed = 300f,
            speedScaling = 2.95f,
            baseTurnRate = 1.78f,
            maxTurnRate = 4.05f,
            turnScaling = 0.034f,
            accelerationRampEndMs = 58_000L,
            lookaheadMs = 290L,
            flankOffsetPx = 146f,
        ),
        splitPersonality = SnakePersonalityConfig(
            warmUpDurationMs = 1000L,
            warmUpSpeedFactor = 0.88f,
            baseSpeed = 105f,
            maxSpeed = 320f,
            speedScaling = 3.20f,
            baseTurnRate = 1.90f,
            maxTurnRate = 4.2f,
            turnScaling = 0.036f,
            accelerationRampEndMs = 58_000L,
            lookaheadMs = 340L,
            flankOffsetPx = -135f,
        ),
    ),
    obstacleConfig = ObstacleSetupConfig(
        jellyfish = listOf(
            JellyfishPlacement(
                id = "jelly_1",
                initialPos = Vec2(132f, 128f),
                patrol = listOf(Vec2(132f, 128f), Vec2(288f, 198f), Vec2(210f, 342f)),
                speed = 44f,
                radius = 12f,
                unlockSubStage = 1,
            ),
            JellyfishPlacement(
                id = "jelly_2",
                initialPos = Vec2(668f, 136f),
                patrol = listOf(Vec2(668f, 136f), Vec2(530f, 250f), Vec2(648f, 388f)),
                speed = 40f,
                radius = 11f,
                unlockSubStage = 1,
            ),
            JellyfishPlacement(
                id = "jelly_3",
                initialPos = Vec2(390f, 102f),
                patrol = listOf(Vec2(390f, 102f), Vec2(490f, 195f), Vec2(310f, 188f)),
                speed = 38f,
                radius = 10f,
                unlockSubStage = 1,
            ),
        ),
        sharks = listOf(
            SharkPlacement(
                id = "shark_west",
                initialPos = Vec2(156f, 332f),
                patrol = listOf(Vec2(118f, 420f), Vec2(330f, 488f), Vec2(412f, 330f), Vec2(196f, 250f)),
                speed = 132f,
                radius = 22f,
                unlockSubStage = 1,
            ),
            SharkPlacement(
                id = "shark_east",
                initialPos = Vec2(642f, 294f),
                patrol = listOf(Vec2(690f, 198f), Vec2(512f, 170f), Vec2(490f, 412f), Vec2(712f, 456f)),
                speed = 136f,
                radius = 22f,
                unlockSubStage = 1,
            ),
            SharkPlacement(
                id = "shark_mid_hunter",
                initialPos = Vec2(400f, 298f),
                patrol = listOf(Vec2(392f, 112f), Vec2(696f, 308f), Vec2(412f, 488f), Vec2(124f, 304f)),
                speed = 140f,
                radius = 22f,
                unlockSubStage = 4,
            ),
        ),
        cyclones = listOf(
            CyclonePlacement(
                id = "cyclone_riptide",
                initialPos = Vec2(96f, 360f),
                patrol = listOf(Vec2(96f, 360f), Vec2(688f, 320f), Vec2(714f, 512f), Vec2(128f, 512f)),
                speed = 18f, radius = 111f, hitRadius = 97f, spinSpeed = 1.05f, spinDir = 1f, unlockSubStage = 3,
            ),
        ),
        rotatingBars = listOf(
            RotatingBarPlacement(
                id = "abyss_bar",
                center = Vec2(400f, 300f),
                length = 160f,
                rpm = 11f,
                theme = com.snakechase.app.domain.model.ObstacleTheme.CYAN,
                unlockSubStage = 5,
            ),
        ),
    ),
    scoringConfig = ScoringConfig(
        levelScoreMax = 230,
        levelScoreBase = 161,
        survivalPointsPerSec = 0.30f, // ~36 pts across 120s survival
        nearMissBasePoints = 1,
        completionBonus = 20,
    ),
    stageNarratives = mapOf(
        0 to "Deeper. The pressure builds.",
        1 to "Bioluminescent predators sense your warmth.",
        2 to "The twin tides divide.",
        3 to "The abyss stares back.",
        4 to "Crushing currents narrow the passage.",
        5 to "No light reaches this depth.",
        6 to "Survive the deep. Become legend.",
    ),
)
