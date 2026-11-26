# Architecture Document — Snake Chase KMP

## 1. Overview

Following the architectural patterns observed across all three 2026 Kotlin Multiplatform Contest winners (**HuntIt**, **Clarity**, and **ClipWhisper**), Snake Chase is structured around a streamlined, high-performance Compose Multiplatform architecture.

The core module is **`:composeApp`**, where 100% of game physics, AI intelligence, level configurations, networking, state management, and UI rendering reside in **`commonMain`**.

---

## 2. High-Level Architecture Diagram

```
┌───────────────────────────────────────────────────────────────────┐
│                    :composeApp  (commonMain)                      │
│                  100% Shared Kotlin Multiplatform                 │
│                                                                   │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  │
│  │ MenuScreen │  │ GameScreen │  │GameOverScr.│  │Leaderboard │  │
│  └────────────┘  └─────┬──────┘  └────────────┘  └────────────┘  │
│                        │                                          │
│              ┌─────────▼──────────┐                              │
│              │    GameCanvas      │   ← Compose Canvas()          │
│              │  (draws at 60fps)  │     pure hardware rendering   │
│              │    HudOverlay      │   ← glass HUD & Judge Panel   │
│              └─────────┬──────────┘                              │
│                        │                                          │
│                        │  collectAsState()                        │
│                        │  StateFlow<GameState>                    │
│  ┌─────────────────────▼────────────────────────────────────┐    │
│  │                  GameViewModel (MVI)                      │    │
│  │   GameIntent ──► handleIntent() ──► GameEngine.tick()   │    │
│  │                                          │               │    │
│  │                               GameState (immutable)      │    │
│  └──────────────────────────────────────────┬───────────────┘    │
│                                             │                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────▼────────────────┐   │
│  │  GameEngine  │  │    Levels    │  │      Data Layer       │   │
│  │  SnakeAI     │  │  LevelConfig │  │  LeaderboardApi(Ktor) │   │
│  │  Collision   │  │  Level1..5   │  │  MultiplayerClient    │   │
│  │  Scoring     │  │  (sealed     │  │  ScoreRepository      │   │
│  │  Particles   │  │   hierarchy) │  │  (SQLDelight)         │   │
│  │  Obstacles   │  │              │  │  Settings             │   │
│  └──────────────┘  └──────────────┘  └───────────────────────┘   │
│                                                                   │
│  expect AudioManager          expect HapticsManager               │
│  (only 2 lightweight platform interfaces)                         │
└───────────────────────────────────────────────────────────────────┘
         │                    │                      │
   actual (Android)    actual (iOS)          actual (Desktop)
   SoundPool           AVAudioPlayer         javax.sound.sampled
   VibrationEffect     UIImpactFeedback      no-op haptics
```

---

## 3. Package Structure (composeApp/src/commonMain)

```
composeApp/src/commonMain/kotlin/com/snakechase/app/
│
├── domain/
│   ├── model/
│   │   ├── GameModels.kt       ← GameState, PlayerOrb, SnakeEntity, Vec2, Rect
│   │   ├── Obstacle.kt         ← Wall, RotatingBar, MovingBlock, DangerZone, Teleporter
│   │   ├── Particle.kt         ← Near-miss rings, death implosions, stage FX
│   │   └── LeaderboardEntry.kt
│   └── usecase/
│       └── CalculateScoreUseCase.kt
│
├── engine/
│   ├── GameEngine.kt           ← Main frame-by-frame simulation tick
│   ├── SnakeAI.kt              ← Predictive intercept, warm-up curves, flanking AI
│   ├── CollisionDetector.kt    ← Circle-circle, circle-rect, circle-line checks
│   ├── ObstacleController.kt   ← Procedural obstacle mechanics & unlock timings
│   ├── ParticleSystem.kt       ← Dynamic particle generation & decay
│   └── ScoringEngine.kt        ← Combo multipliers, near-miss detection, stage bonuses
│
├── levels/
│   ├── LevelConfig.kt          ← Type-safe level data classes
│   ├── LevelRegistry.kt        ← Catalog of all 5 playable levels
│   ├── Level1NeonDrift.kt      ← 10 sub-stages, cyberpunk, split snake
│   ├── Level2NeonGrove.kt      ← 9 sub-stages, bioluminescent forest
│   ├── Level3AbyssalChase.kt   ← 7 sub-stages, procedural cyclones, aquatic predators
│   ├── Level4CrystalTundra.kt  ← 6 sub-stages, Antarctic isopods, snowfall
│   └── Level5CrucibleCore.kt   ← 5 sub-stages, rising lava forge, golden escape door
│
├── data/
│   ├── network/
│   │   ├── LeaderboardApi.kt   ← Ktor HTTP client -> Firebase Firestore REST
│   │   ├── MultiplayerClient.kt← Ktor WebSocket client -> 1v1 Chase Battle server
│   │   └── dto/                ← @Serializable network data transfer objects
│   └── local/
│       ├── ScoreRepository.kt  ← SQLDelight local high scores & statistics
│       └── SettingsRepository.kt ← Player profile, unlocked levels, motion prefs
│
├── presentation/
│   ├── GameViewModel.kt        ← MVI pattern: Intent -> Reducer -> StateFlow<GameState>
│   └── GameIntent.kt           ← Player inputs, lifecycle events, judge cheats
│
├── ui/
│   ├── canvas/
│   │   ├── GameCanvas.kt       ← Main Compose Canvas loop
│   │   ├── SnakeRenderer.kt    ← Radial gradients, glowing eye headlights, plasma body
│   │   ├── PlayerOrbRenderer.kt← Cursor orb, breathing glow, dynamic ribbon trail
│   │   ├── ObstacleRenderer.kt ← Scanline walls, energy beams, portal vortexes
│   │   └── ParticleRenderer.kt ← Near-miss shockwaves, explosive bursts
│   │
│   ├── components/
│   │   ├── HudOverlay.kt       ← Glassmorphism status pills (score, time, combo)
│   │   ├── StageToast.kt       ← Smooth stage advance notifications
│   │   ├── PauseOverlay.kt     ← In-game pause & accessibility toggles
│   │   └── JudgePanel.kt       ← Contest Judge Utilities (level skip, god mode, debug)
│   │
│   ├── screens/
│   │   ├── MenuScreen.kt       ← Interactive level selector & personal bests
│   │   ├── CountdownScreen.kt  ← 3-2-1-GO animated entry
│   │   ├── GameScreen.kt       ← Active gameplay viewport
│   │   ├── GameOverScreen.kt   ← Stat breakdown, medal badges, retry flow
│   │   ├── LeaderboardScreen.kt← Global top 10 rankings & country flags
│   │   └── MultiplayerScreen.kt← 1v1 Chase Battle matchmaking & arena
│   │
│   └── theme/
│       ├── Color.kt            ← Cyber neon, deep abyss, tundra frost, molten magma
│       ├── Type.kt             ← Modern gaming typography
│       └── Theme.kt            ← Compose Material3 theme wrapper
│
├── platform/
│   ├── AudioManager.kt         ← expect interface for low-latency SFX & BGM
│   └── HapticsManager.kt       ← expect interface for tactile vibration feedback
│
└── di/
    └── AppModule.kt            ← Koin dependency injection module
```

---

## 4. Why This Architecture Wins

1. **Maximum Code Reuse (~99% Kotlin)**: Unlike projects that split business logic into separate native apps, 100% of game simulation, AI, UI, and networking is written once in Kotlin `commonMain`.
2. **Zero Platform Baggage**: Pure Compose Canvas drawing means no third-party game engine dependencies to maintain or debug.
3. **Contest Judge Friendly**: Built-in Judge Utilities panel lets competition reviewers test high-tier features (e.g., Level 5 Crucible lava or Level 1 Split Snake) in seconds.
4. **Comprehensive Test Coverage (`commonTest`)**: Deterministic testing for all AI behaviors, collision math, and score accumulation directly in multiplatform tests.
