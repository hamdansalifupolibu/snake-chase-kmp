package com.snakechase.app.levels

import com.snakechase.app.domain.model.ObstacleTheme
import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.Vec2

/**
 * 🌲 Level 2: Neon Grove (Minimal Neon Grove / Forest)
 * Reconstructed 1:1 from original web engine specifications (`main.js` lines 310–690).
 *
 * Distinct mechanics:
 *  - 9 Sub-Stages (Dew -> Canopy Pulse -> Triad Bloom -> Thorn Path -> Root Maze -> Spire Spin -> Moss Run -> Spore Warp -> Heatwood)
 *  - Triad Bloom: Multi-predator hunt featuring Emerald leader, Amber trail-follower, and Violet circuit-patrol hunter.
 *  - Heatwood (Stage 9): Full-body lethality (head, body segments, and tail contact).
 *  - Root Maze: 3 charcoal-ember veins with magma accent.
 *  - Spire Spin: Spindle rotor at (596, 288).
 *  - Moss Run: 2D vertical ping-pong and horizontal traversing block.
 *  - Spore Warp: Spore A (110, 460) and Pollen B (690, 130) fungal rifts.
 */
val Level2NeonGrove: LevelConfig = LevelConfig(
    id = 2,
    name = "Neon Grove",
    tagline = "Bioluminescent Spore Canopy",
    completionTimeMs = 90_000L, // 1:30 — 7 sub-stages
    splitAtSubStage = 3, // "Flank" split at index 3
    wholeBodyLethalFromSubStage = 6, // Lethal at final "Grove Finale" stage (index 6)
    subStages = listOf(
        SubStage(0, "Awakening", "Calm emerald mist — jellyfish drift in", 0L, 100),
        SubStage(1, "Weave", "Moving block enters the field", 10_000L, 150),
        SubStage(2, "Territory", "Danger zone seals a corner", 21_000L, 200),
        SubStage(3, "Flank", "Emerald & Amber hunters emerge!", 33_000L, 260),
        SubStage(4, "Prowler", "Shadow Panther stalks the forest floor", 46_000L, 330),
        SubStage(5, "Locked In", "Spore warp rifts tear open", 60_000L, 410),
        SubStage(6, "Grove Finale", "Everything active — lethal contact!", 75_000L, 500),
    ),
    palette = LevelPalette(
        bgTopArgb = 0xFF050D0A,
        bgBottomArgb = 0xFF061812,
        gridLineArgb = 0x22104032,
        ambientGlowArgb = 0x3352E090,
        arenaBorderArgb = 0xFF52E090,
        primarySnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFECFFF6),
                ColorStop(0.40f, 0xFF30FF9E),
                ColorStop(0.72f, 0xFF16C36E),
                ColorStop(1.00f, 0x00063A2C),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFCFFF8),
                ColorStop(0.28f, 0xFF46FFA0),
                ColorStop(0.62f, 0xFF1CC876),
                ColorStop(1.00f, 0x00083E30),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x8842FF9A,
            nearMissAlertColorArgb = 0xFFFF7A54,
        ),
        splitSnakeTheme = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFF0),
                ColorStop(0.28f, 0xFFFFE566),
                ColorStop(0.65f, 0xFFFFB300),
                ColorStop(1.00f, 0x004D3300),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.28f, 0xFFFFE888),
                ColorStop(0.60f, 0xFFFFC000),
                ColorStop(1.00f, 0x004D3300),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x88FFE566,
            nearMissAlertColorArgb = 0xFFFF5522,
        ),
        splitSnakeTheme2 = SnakeThemeConfig(
            bodyGradient = listOf(
                ColorStop(0.00f, 0xFFFAF5FF),
                ColorStop(0.28f, 0xFFC9A8FF),
                ColorStop(0.65f, 0xFF8A5CFF),
                ColorStop(1.00f, 0x00331055),
            ),
            headGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.28f, 0xFFD8C0FF),
                ColorStop(0.60f, 0xFFA070FF),
                ColorStop(1.00f, 0x00331055),
            ),
            eyeColorArgb = 0xFFFFFFFF,
            energyLinkColorArgb = 0x88C9A8FF,
            nearMissAlertColorArgb = 0xFFFF5577,
        ),
        cursorTheme = CursorThemeConfig(
            orbGradient = listOf(
                ColorStop(0.00f, 0xFFFFFFFF),
                ColorStop(0.12f, 0xFFFAFFF6),
                ColorStop(0.42f, 0xFF96FFCD),
                ColorStop(0.78f, 0xFF46DC91),
                ColorStop(1.00f, 0x00083024),
            ),
            trailColorArgb = 0x8852E090,
            nearMissRingArgb = 0xFF6EFFC0,
            perfectRingArgb = 0xFFFFD766,
        ),
    ),
    snakeConfig = SnakeSetupConfig(
        headRadius = 14f,
        bodySegments = 8,
        segmentSpacing = 10f,
        splitCount = 2, // Triad Bloom: 2 additional split predators
        personality = SnakePersonalityConfig(
            warmUpDurationMs = 1350L,
            warmUpSpeedFactor = 0.64f,
            baseSpeed = 112f,
            maxSpeed = 332f,
            speedScaling = 3.58f,
            baseTurnRate = 1.85f,
            maxTurnRate = 4.1f,
            turnScaling = 0.036f,
            accelerationRampEndMs = 52_000L,
            lookaheadMs = 310L,
            flankOffsetPx = 148f,
        ),
        splitPersonality = SnakePersonalityConfig(
            warmUpDurationMs = 1000L,
            warmUpSpeedFactor = 0.85f,
            baseSpeed = 118f,
            maxSpeed = 348f,
            speedScaling = 3.75f,
            baseTurnRate = 1.94f,
            maxTurnRate = 4.3f,
            turnScaling = 0.038f,
            accelerationRampEndMs = 52_000L,
            lookaheadMs = 280L,
            flankOffsetPx = 120f,
            speedMultiplier = 1.05f,
        ),
        splitPersonality2 = SnakePersonalityConfig(
            warmUpDurationMs = 1000L,
            warmUpSpeedFactor = 0.80f,
            baseSpeed = 85f,
            maxSpeed = 252f,
            speedScaling = 2.72f,
            baseTurnRate = 1.72f,
            maxTurnRate = 3.8f,
            turnScaling = 0.033f,
            accelerationRampEndMs = 52_000L,
            lookaheadMs = 350L,
            flankOffsetPx = -140f,
            speedMultiplier = 0.76f,
        ),
    ),
    obstacleConfig = ObstacleSetupConfig(
        walls = listOf(
            WallPlacement(id = "root_upper", rect = Rect(286f, 143.5f, 228f, 17f), theme = ObstacleTheme.AMBER, unlockSubStage = 2),
            WallPlacement(id = "root_mid", rect = Rect(295f, 279.5f, 246f, 17f), theme = ObstacleTheme.AMBER, unlockSubStage = 2),
            WallPlacement(id = "root_taproot", rect = Rect(172.5f, 319f, 19f, 186f), theme = ObstacleTheme.AMBER, unlockSubStage = 2),
        ),
        rotatingBars = listOf(
            RotatingBarPlacement(id = "spire_spin", center = Vec2(596f, 288f), length = 164f, rpm = 10f, theme = ObstacleTheme.AMBER, unlockSubStage = 4),
        ),
        movingBlocks = listOf(
            MovingBlockPlacement(id = "moss_run", size = Vec2(68f, 14f), patrolA = Vec2(448f, 368f), patrolB = Vec2(448f, 488f), speed = 85f, theme = ObstacleTheme.AMBER, unlockSubStage = 1),
        ),
        teleporters = listOf(
            TeleporterPlacement(id = "spore_teleporter", posA = Vec2(110f, 460f), posB = Vec2(690f, 130f), radius = 26f, theme = ObstacleTheme.EMERALD, unlockSubStage = 5),
        ),
        forestProwlers = listOf(
            ForestProwlerPlacement(
                id = "shadow_panther_grove",
                initialPos = Vec2(104f, 468f),
                patrol = listOf(Vec2(104f, 468f), Vec2(268f, 496f), Vec2(468f, 488f), Vec2(652f, 464f), Vec2(708f, 404f)),
                speed = 34f,
                radius = 33f,
                unlockSubStage = 4,
            ),
        ),
    ),
    scoringConfig = ScoringConfig(
        levelScoreMax = 160,
        levelScoreBase = 101,
        survivalPointsPerSec = 0.33f, // 30 pts across 90s survival
        nearMissBasePoints = 1,
        completionBonus = 18, // ~160 pts total
    ),
    stageNarratives = mapOf(
        0 to "The forest watches.",
        1 to "Canopy roots shift underfoot.",
        2 to "Thorns claim the perimeter.",
        3 to "Shadows twist between the roots.",
        4 to "The predator stalks its domain.",
        5 to "Spores cloud your vision.",
        6 to "The jungle never forgets.",
    ),
)
