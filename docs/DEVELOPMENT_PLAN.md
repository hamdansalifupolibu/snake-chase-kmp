# Development Plan — Snake Chase KMP

**Deadline:** December 7, 2026
**Available:** ~10 weeks from planning completion
**Developer Environment:** Windows laptop, Android Studio, JDK 17

---

## Development Principles

1. **Android first** — every feature is first built and tested on Android. Desktop comes second, iOS third (via CI).
2. **Shared code first** — if a feature can live in `commonMain`, it must. Platform-specific code is the last resort.
3. **Playable over perfect** — each week should end with a runnable game, even if incomplete.
4. **Port, don't rebuild** — the JS game logic is the reference. Port its behavior faithfully into Kotlin.
5. **No pre-optimizing** — get it working first, optimize rendering/performance in Week 7–8.

---

## Phase Breakdown

### Phase 1 — Foundation (Week 1)
**Goal: Compilable project, Level 1 snake chasing the cursor on Android.**

#### Tasks
- [ ] Create Gradle project (`settings.gradle.kts`, `build.gradle.kts`, `libs.versions.toml`)
- [ ] Configure `:shared` module (Android + iOS + Desktop targets, Cocoapods)
- [ ] Configure `:composeApp` module (Android + iOS + Desktop targets)
- [ ] Set up Koin DI (SharedModule, AndroidSharedModule, DesktopSharedModule)
- [ ] Write all domain model data classes (`GameModels.kt`)
- [ ] Write `LevelConfig.kt` sealed class hierarchy
- [ ] Write `Level1NeonDrift.kt` (port LEVELS[0] from JS)
- [ ] Write `GameEngine.kt` stub (tick loop, player position update)
- [ ] Write `SnakeAI.kt` — basic chase (straight-line pursuit, no personality yet)
- [ ] Write `CollisionDetector.kt` — snake head vs player orb (circle collision only)
- [ ] Write `GameViewModel.kt` — MVI skeleton, `StateFlow<GameState>`
- [ ] Write `GameScreen.kt` — `withFrameMillis` loop + basic `Canvas` draw
- [ ] Draw: filled circle for player orb, filled circles for snake segments
- [ ] Wire `PlayerMoved` intent from `pointerInput` (touch + mouse)
- [ ] `MainActivity.kt` — runs the app full-screen on Android
- [ ] **Milestone: Run the app on Android. Snake chases the cursor. Touch to move.**

#### Deliverable
A minimal but real game: one snake, no obstacles, no levels beyond 1, no HUD. But it's playable and demonstrates the core KMP architecture.

---

### Phase 2 — Game Engine Core (Week 2)
**Goal: Level 1 fully playable with sub-stages, split snake, and all obstacles.**

#### Tasks
- [ ] Improve `SnakeAI.kt` — full personality system (warm-up, aggression ramp, predictive lookahead, flank mode, drift)
- [ ] `ScoringEngine.kt` — survival points/sec, near-miss detection + bonus, combo tiers (1×→5×), stage bonuses
- [ ] `ObstacleController.kt` — wall (static), rotating bar (angular velocity), moving block (patrol path), danger zone (kill on enter), teleporter (pair of portals)
- [ ] Wire obstacle unlock at correct Level 1 sub-stages (Stage 6 walls, Stage 7 bar, Stage 8 mover+danger, Stage 9 teleporters)
- [ ] `ParticleSystem.kt` — near-miss ring, PERFECT near-miss amber ring, death implosion
- [ ] Sub-stage progression logic (advance on time, fire `StageAdvanced` effect)
- [ ] Split snake spawn at Level 1 Stage 4 — second `SnakeEntity` with `SPLIT` role
- [ ] Death detection → transition to `GAME_OVER` phase
- [ ] Level completion detection (survive `completionTimeMs`) → `LEVEL_COMPLETE`
- [ ] `GameIntent.Paused` / `Resumed` — freeze survival timer accurately
- [ ] **Milestone: Level 1 is fully playable end-to-end on Android.**

---

### Phase 3 — Levels 2–5 Port (Week 3)
**Goal: All 5 levels are playable.**

#### Tasks
- [ ] `Level2NeonGrove.kt` — 9 sub-stages, emerald/honey-gold snake, Neon Grove ambience, Twin Bloom split, obstacle ladder
- [ ] `Level3AbyssalChase.kt` — 7 sub-stages, aquatic AI (jellyfish, sharks, octopus, urchins, siren mine), cyclone hazards, Dual Current split
- [ ] `Level4CrystalTundra.kt` — 6 sub-stages, isopods patrol, snowfall from sub-stage 4, Ice Drift speed band, finale arena brain (open arena for twins), rift teleporters
- [ ] `Level5CrucibleCore.kt` — 5 sub-stages, crucible forge (floor cracks, ember impacts, rage dashes, eruption), escape door mechanism
- [ ] `LevelRegistry.kt` — `val ALL_LEVELS = listOf(L1, L2, L3, L4, L5)`
- [ ] Level unlock system (beat level N to unlock N+1, stored in SQLDelight + Settings)
- [ ] Level 3 special: `AquaticController.kt` — creatures + cyclone patrol
- [ ] Level 4 special: `IsopodController.kt` + `SnowfallSystem.kt`
- [ ] Level 5 special: `CrucibleController.kt` — forge scripted acts
- [ ] **Milestone: All 5 levels playable on Android.**

---

### Phase 4 — UI & Screens (Week 4)
**Goal: Complete UI flow — menus, HUD, game over, leaderboard.**

#### Tasks
- [ ] App theme — neon `Color.kt`, Orbitron + Space Grotesk `Type.kt`, `Theme.kt`
- [ ] `MenuScreen.kt` — level cards with level name, tagline, personal best score+time, lock state
- [ ] `CountdownScreen.kt` — 3-2-1-GO animation with level palette
- [ ] `HudOverlay.kt` — glass Level/Time chip, centered Score pill, Combo chip, sub-stage progress pills, Pause button
- [ ] `StageToast.kt` — sliding in/out toast with stage name + bonus
- [ ] `PauseOverlay.kt` — Resume / Restart / Quit to Menu + Calmer screen toggle + Cursor size toggle
- [ ] `GameOverScreen.kt` — score count-up animation, breakdown table (survival/stages/near-miss/completion), achievement badges, confetti for level complete
- [ ] `LeaderboardScreen.kt` — top 10 rows, name + country tag + score + level
- [ ] Name entry overlay (username + optional country)
- [ ] Navigation — `AppNavigation.kt` wiring all screens
- [ ] **Milestone: Full UI flow working on Android, looks premium.**

---

### Phase 5 — Visual Polish (Week 5)
**Goal: The game looks as good as the JS version.**

#### Tasks
- [ ] `SnakeRenderer.kt` — radial-gradient body segments, energy link lines, head glow, coral flash on near-miss, eye lights
- [ ] `PlayerOrbRenderer.kt` — glowing orb with breathing pulse, trailing ribbon, near-miss pulse rings
- [ ] `ObstacleRenderer.kt` — glass energy wall (traveling scanlines), violet→magenta energy beam (bar), amber→coral pill (mover), force-field grid (danger zone), concentric portals (teleporters)
- [ ] `ParticleRenderer.kt` — expanding rings, PERFECT amber rings, death implosion inward scatter
- [ ] Background rendering — gradient-mesh blobs, constellation particles, stage-aware palette morphs via `BgStage` config
- [ ] Split snake visual identity — cyan primary vs magenta-pink flanker
- [ ] Level 3 visual — aquatic hazard drawing (jellyfish, sharks, cyclones)
- [ ] Level 4 visual — snowfall particle system
- [ ] Level 5 visual — floor cracks, ember impacts, lava fill, escape door
- [ ] Death animation — slow-mo effect, implosion, player orb collapse to zero
- [ ] **Milestone: Visuals match the JS reference. Screenshots worthy of the README.**

---

### Phase 6 — Desktop Target (Week 6)
**Goal: Game runs and looks great on Windows desktop.**

#### Tasks
- [ ] `Main.kt` — `singleWindowApplication()`, minimum 900×600, centered on screen
- [ ] Mouse input — `pointerInput` `awaitPointerEvent` for continuous mouse position
- [ ] Keyboard input — Esc for pause, arrow keys / WASD optional steering in multiplayer
- [ ] `AudioManagerImpl.kt` (Desktop) — `javax.sound.sampled` for SFX, background music via `Clip`
- [ ] `HapticsManagerImpl.kt` (Desktop) — no-op implementation
- [ ] SQLDelight `SqliteDriver` wired in `DesktopSharedModule`
- [ ] Settings on Desktop — `java.util.prefs.Preferences`
- [ ] Test all 5 levels on Desktop
- [ ] Package Windows `.msi` installer — `./gradlew :composeApp:packageMsi`
- [ ] **Milestone: Ship a working Windows .msi of Snake Chase.**

---

### Phase 7 — iOS Target & CI (Week 7)
**Goal: iOS target compiles and CI is green.**

#### Tasks
- [ ] Configure Cocoapods in `shared/build.gradle.kts`
- [ ] Write `MainViewController.kt` (iosMain composeApp)
- [ ] Write `iOSApp.swift` + `ContentView.swift` (iosApp)
- [ ] `AudioManagerImpl.kt` (iosMain) — AVAudioPlayer via Kotlin/Native ObjC interop
- [ ] `HapticsManagerImpl.kt` (iosMain) — UIImpactFeedbackGenerator
- [ ] SQLDelight `NativeSqliteDriver` wired in `IosSharedModule`
- [ ] Set up `.github/workflows/ci.yml`:
  - Android build on `ubuntu-latest`
  - Desktop build on `ubuntu-latest`
  - iOS shared module compile on `macos-latest`
- [ ] Verify: GitHub Actions CI is green for all 3 platforms
- [ ] **Milestone: CI badge shows ✅ for Android, Desktop, iOS.**

---

### Phase 8 — Networking (Week 8)
**Goal: Global leaderboard works. Multiplayer Chase Battle works.**

#### Tasks
- [ ] Firebase project setup — enable Firestore, get REST API key
- [ ] `LeaderboardApi.kt` — `GET /scores` (top 10), `POST /scores` (submit), `GET/PUT /players/{id}` (profile)
- [ ] Wire leaderboard to `GameOverScreen` — submit score after game over
- [ ] Wire leaderboard to `LeaderboardScreen` — fetch + display top 10
- [ ] Player profile — name + country stored locally (Settings) and synced to Firestore
- [ ] `MultiplayerClient.kt` — Ktor WebSocket connecting to Colyseus `ChaseDuelRoom`
- [ ] Chase Battle flow — `MultiplayerScreen` → queue → paired → in-match → result
- [ ] Chase Battle UI — opponent name/country, role (snake vs survivor), round timer, MP points
- [ ] **Milestone: Submit a score and see it on the leaderboard. Chase Battle works.**

---

### Phase 9 — Sound & Haptics (Week 9)
**Goal: Audio and haptics complete on Android and Desktop.**

#### Tasks
- [ ] Source/create sound effects: near-miss whoosh, PERFECT chime, death implosion, stage advance, level complete, split spawn, teleport, button click
- [ ] Source/create BGM tracks: one per level (5 tracks total), loop points set
- [ ] Wire `AudioManager.loadSounds()` at app start
- [ ] Wire `AudioManager.playBgm(levelId)` on level start
- [ ] Wire `AudioManager.playSfx(...)` on all game events
- [ ] `SettingsRepository` — BGM volume, SFX volume, mute toggles
- [ ] Wire volume controls in `PauseOverlay`
- [ ] `HapticsManager` wired: near-miss → light, stage advance → medium, death → heavy
- [ ] Test audio on Android (phone, emulator)
- [ ] Test audio on Desktop (Windows speakers/headphones)

---

### Phase 10 — Polish, README, Demo Video (Week 10)
**Goal: Contest-ready submission.**

#### Tasks
- [ ] Screenshot pass — capture high-quality screenshots of all 5 levels on Android + Desktop
- [ ] README final version:
  - App icon + header graphic
  - Contest badge (Kotlin Multiplatform Contest 2027)
  - Platform support table with CI badge
  - Screenshots of all 5 levels (landscape grid)
  - How to Run (Android / Desktop / iOS)
  - Architecture section (link to `docs/ARCHITECTURE.md`)
  - Tech Stack table
  - Features checklist
  - **Judge's Walkthrough** section (step-by-step to experience all 5 levels + multiplayer)
  - Video demo link
- [ ] Demo video (3 min):
  - Minute 1: Android gameplay — Level 1 → 2 → highlights
  - Minute 2: Desktop gameplay — mouse controls, Level 3 cyclones, Level 5 eruption
  - Minute 3: Multiplayer Chase Battle (two windows side by side), leaderboard
- [ ] LICENSE file — Apache 2.0
- [ ] `.gitignore` — standard KMP gitignore
- [ ] Final Gradle cleanup — remove any debug/dev flags
- [ ] Tag release `v1.0.0` on GitHub
- [ ] **Submit to contest ✅**

---

## Weekly Summary

| Week | Theme | Key Deliverable |
|---|---|---|
| 1 | Foundation | KMP project + Level 1 snake chases cursor on Android |
| 2 | Game Engine | Level 1 fully playable (sub-stages, split, all obstacles) |
| 3 | All Levels | 5 levels playable on Android |
| 4 | UI & Screens | Full UI flow, HUD, menus, game over |
| 5 | Visual Polish | Visuals match JS reference |
| 6 | Desktop | Windows .msi ships, mouse controls |
| 7 | iOS & CI | GitHub CI green for all 3 platforms |
| 8 | Networking | Leaderboard + Multiplayer working |
| 9 | Sound & Haptics | Audio complete on Android + Desktop |
| 10 | Submit | README, demo video, contest submission |

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Compose Canvas performance below 60fps | Medium | High | Profile early (Week 2); reduce particle count; use `key()` to skip unnecessary recompositions |
| Level 5 crucible mechanics complex to port | Medium | Medium | Port config first, add visual acts incrementally |
| Colyseus WebSocket API changes | Low | High | Pin Colyseus server version; test WS connection in Week 8 before server changes |
| Firebase REST quota limits | Low | Low | Leaderboard traffic is minimal; implement client-side caching |
| Sound file licensing | Medium | Medium | Use royalty-free sources (freesound.org, ZapSplat) or generate procedurally |
| iOS CI macos-runner cost | Low | Low | GitHub free tier includes 2000 min/month macOS — sufficient for CI |

---

## Definition of "Done" (For Submission)

A feature is done when:
1. It works on **Android** (physical device or emulator)
2. It works on **Desktop** (Windows, `./gradlew :composeApp:run`)
3. The code lives in **`commonMain`** (or is a documented `expect/actual` with all 3 actuals written)
4. No compiler warnings on the feature's files
5. It is visible in the **demo video**
