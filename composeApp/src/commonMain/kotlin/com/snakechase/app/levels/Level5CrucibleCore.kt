package com.snakechase.app.levels

import com.snakechase.app.domain.model.ObstacleTheme
import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2

/**
 * 🔥 Level 5: Crucible Core (Molten Volcanic Forge)
 * Reconstructed 1:1 from original web engine specifications (`main.js` lines 1546–1892).
 *
 * Distinct mechanics:
 *  - 5 Sub-Stages (Ignition -> Cracked Ground -> Ember Storm -> Serpent Rage -> Final Eruption)
 *  - Ember Storm (Stage 3): Twin Magma serpents ignite (Magma Orange #FF8C42 & Electric Cyan #5CE8FF).
 *  - Serpent Rage (Stage 4): Aggressive speed burst and intercept AI.
 *  - Final Eruption (Stage 5): Molten arena caldera erupts, escape door at (788, 54) opens!
 *  - Completion at 115s.
 */
val Level5CrucibleCore: LevelConfig = LevelConfig(
    id = 5,
    name = "Crucible Core",
    tagline = "Molten Volcanic Forge",
    completionTimeMs = 180_000L, // 3:00 — 8 sub-stages
    splitAtSubStage = 2, // "Ember Storm" split at index 2
    wholeBodyLethalFromSubStage = -1,
    subStages = listOf(
        SubStage(0, "Ignition", "Forge temperature rising — all systems warm", 0L, 200),
        SubStage(1, "Cracked Ground", "Lava fractures spread across the forge floor", 18_000L, 340),
        SubStage(2, "Ember Storm", "Twin Magma serpents ignite!", 37_000L, 500),
        SubStage(3, "Serpent Rage", "High-velocity rage dashes — no hesitation", 58_000L, 670),
        SubStage(4, "The Burn", "Escape door unlocks — race to freedom!", 80_000L, 850),
        SubStage(5, "Crucible", "Rotating bars at maximum RPM", 104_000L, 1020),
        SubStage(6, "Inferno", "All danger zones activated", 129_000L, 1200),
        SubStage(7, "Core Melt", "Full chaos — survive or be consumed!", 154_000L, 1400),
    ),
    palette = LevelPalette(
        bgTopArgb = 0xFF120706,
        bgBottomArgb = 0xFF1A0806,
        gridLineArgb = 0x223A1610,
        ambientGlowArgb = 0x33FF7A32,
        arenaBorderArgb = 0xFFFF7A32,
        primarySnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFFFF5C8),
                ColorStop(0.22f, 0xFFFFA046),
                ColorStop(0.45f, 0xFFFF5A28),
                ColorStop(0.72f, 0xFFB4281E),
                ColorStop(1.00f, 0x00280F0C),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFF0),
                ColorStop(0.14f, 0xFFFFDC78),
                ColorStop(0.38f, 0xFFFF7832),
                ColorStop(0.65f, 0xFFC8371C),
                ColorStop(1.00f, 0x003C120C),
            ),
            eyeColorArgb = 0xFFFFE8A0,
            energyLinkColorArgb = 0x88FF8C42,
            nearMissAlertColorArgb = 0xFF5CE8FF,
        ),
        splitSnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFEEFFFF),
                ColorStop(0.22f, 0xFF5CE8FF),
                ColorStop(0.45f, 0xFF18B0E8),
                ColorStop(0.72f, 0xFF1060B0),
                ColorStop(1.00f, 0x00081C40),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.14f, 0xFFD0FAFF),
                ColorStop(0.38f, 0xFF40D8FF),
                ColorStop(0.65f, 0xFF1288D8),
                ColorStop(1.00f, 0x000A2450),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x885CE8FF,
            nearMissAlertColorArgb = 0xFFFF7A32,
        ),
        cursorTheme = CursorThemeConfig(
            orbGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.12f, 0xFFFFEED2),
                ColorStop(0.34f, 0xFFFFB478),
                ColorStop(0.56f, 0xFFFF6E3C),
                ColorStop(0.80f, 0xFFC8462D),
                ColorStop(1.00f, 0x0032140F),
            ),
            trailColorArgb = 0x88FFAA66,
            nearMissRingArgb = 0xFFFF9444,
            perfectRingArgb = 0xFFFFE8A0,
        ),
    ),
    snakeConfig = SnakeSetupConfig(
        headRadius = 14f,
        bodySegments = 8,
        segmentSpacing = 10f,
        personality = SnakePersonalityConfig(
            warmUpDurationMs = 1100L,
            warmUpSpeedFactor = 0.66f,
            baseSpeed = 112f,
            maxSpeed = 338f,
            speedScaling = 3.62f,
            baseTurnRate = 1.88f,
            maxTurnRate = 4.15f,
            turnScaling = 0.037f,
            accelerationRampEndMs = 48_000L,
            lookaheadMs = 300L,
            flankOffsetPx = 146f,
        ),
        splitPersonality = SnakePersonalityConfig(
            warmUpDurationMs = 900L,
            warmUpSpeedFactor = 0.88f,
            baseSpeed = 120f,
            maxSpeed = 350f,
            speedScaling = 3.80f,
            baseTurnRate = 1.95f,
            maxTurnRate = 4.30f,
            turnScaling = 0.038f,
            accelerationRampEndMs = 48_000L,
            lookaheadMs = 350L,
            flankOffsetPx = -140f,
        ),
    ),
    obstacleConfig = ObstacleSetupConfig(
        magmaCracks = listOf(
            MagmaCrackPlacement(id = "crack_north_west", center = Vec2(260f, 220f), seed = 101L, unlockSubStage = 1),
            MagmaCrackPlacement(id = "crack_south_east", center = Vec2(540f, 380f), seed = 202L, unlockSubStage = 1),
            MagmaCrackPlacement(id = "crack_south_west", center = Vec2(320f, 440f), seed = 303L, unlockSubStage = 2),
            MagmaCrackPlacement(id = "crack_north_east", center = Vec2(580f, 190f), seed = 404L, unlockSubStage = 2),
        ),
        fallingEmbers = listOf(
            FallingEmberPlacement(id = "ember_nw", targetPos = Vec2(240f, 280f), warnRadius = 34f, impactRadius = 46f, unlockSubStage = 2),
            FallingEmberPlacement(id = "ember_se", targetPos = Vec2(560f, 320f), warnRadius = 34f, impactRadius = 46f, unlockSubStage = 2),
            FallingEmberPlacement(id = "ember_north", targetPos = Vec2(400f, 180f), warnRadius = 34f, impactRadius = 46f, unlockSubStage = 3),
            FallingEmberPlacement(id = "ember_south", targetPos = Vec2(380f, 460f), warnRadius = 34f, impactRadius = 46f, unlockSubStage = 3),
        ),
        rotatingBars = listOf(
            RotatingBarPlacement(
                id = "crucible_bar_1",
                center = Vec2(300f, 250f),
                length = 160f,
                rpm = 16f,
                theme = com.snakechase.app.domain.model.ObstacleTheme.AMBER,
                unlockSubStage = 5,
            ),
            RotatingBarPlacement(
                id = "crucible_bar_2",
                center = Vec2(500f, 380f),
                length = 140f,
                rpm = 18f,
                theme = com.snakechase.app.domain.model.ObstacleTheme.AMBER,
                unlockSubStage = 6,
            ),
        ),
        dangerZones = listOf(
            DangerZonePlacement(
                id = "lava_zone_1",
                rect = Rect(150f, 150f, 120f, 100f),
                theme = com.snakechase.app.domain.model.ObstacleTheme.AMBER,
                unlockSubStage = 6,
            ),
            DangerZonePlacement(
                id = "lava_zone_2",
                rect = Rect(530f, 340f, 120f, 100f),
                theme = com.snakechase.app.domain.model.ObstacleTheme.AMBER,
                unlockSubStage = 6,
            ),
        ),
        escapeDoor = EscapeDoorPlacement(
            id = "crucible_escape_door",
            position = Vec2(738f, 56f),
            width = 80f,
            height = 88f,
            unlockSubStage = 4,
        ),
    ),
    scoringConfig = ScoringConfig(
        levelScoreMax = 400,
        levelScoreBase = 311,
        survivalPointsPerSec = 0.25f, // ~45 pts across 180s survival
        nearMissBasePoints = 1,
        completionBonus = 25,
    ),
    stageNarratives = mapOf(
        0 to "This is where legends are made.",
        1 to "The floor liquifies into searing slag.",
        2 to "Twin Magma serpents ignite.",
        3 to "The core is alive. And hungry.",
        4 to "The blast doors creak open. Run!",
        5 to "Centrifuges scream at terminal velocity.",
        6 to "No shelter remains.",
        7 to "Only one survives the crucible.",
    ),
)
