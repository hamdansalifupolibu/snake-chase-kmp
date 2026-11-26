# Technology Decisions — Snake Chase KMP

Every technology choice in this project is deliberate. This document explains the *why*
behind each decision, especially as it relates to winning the KMP contest.

---

## 1. Kotlin Multiplatform — The Foundation

**Decision:** Use KMP with Kotlin 2.1.0

**Why:**
- This is literally the contest requirement
- KMP 2.x has stable iOS/Native support — no longer experimental
- The `expect/actual` mechanism lets us isolate the only 2 platform-specific APIs we need
- Everything else compiles for all targets from `commonMain`

**What we get:** One game engine that runs on Android, iOS, and Desktop without modification.

---

## 2. Compose Multiplatform — UI & Game Rendering

**Decision:** Use Compose Multiplatform 1.7.x with the `Canvas` composable for ALL rendering.
Do NOT use any external game engine (LibGDX, Korge, Kubriko, Godot).

**Why not a game engine?**

The contest judges score 40% on **use of KMP technology**. Using LibGDX or Korge means:
- Those frameworks abstract away KMP — the judge sees the framework, not your KMP usage
- They are not idiomatic Compose Multiplatform
- They add large non-Kotlin dependencies that hurt your Kotlin %
- They complicate the "shared code" story

**Why Compose Canvas?**
- 100% idiomatic KMP — this is what JetBrains wants to see used
- The `Canvas` composable wraps Skia under the hood (same renderer as Flutter)
- Works identically on Android, iOS, Desktop via Compose Multiplatform
- Hardware-accelerated on all platforms
- Fits perfectly with the MVI pattern — `Canvas { drawGameState(state) }`
- Used in the contest by top entries

**How the game renders at 60fps:**
```kotlin
LaunchedEffect(Unit) {
    while (isActive) {
        withFrameMillis { ms -> viewModel.tick(ms) }  // runs every frame
    }
}
Canvas(modifier = Modifier.fillMaxSize()) {
    // draw everything from immutable GameState snapshot
}
```
This is exactly how Compose Multiplatform games are built idiomatically.

---

## 3. MVI (Model-View-Intent) Architecture

**Decision:** MVI pattern with `StateFlow<GameState>` and immutable state snapshots.

**Why MVI over MVVM or Redux?**

Games have extremely frequent state updates (60 per second). MVI shines here because:
- **Immutable state** — every frame produces a new `GameState` snapshot. No mutation bugs.
- **Single source of truth** — the `GameViewModel` is the only thing that mutates state
- **Predictable** — every `GameIntent` produces a deterministic new `GameState`
- **Testable** — pure functions: `(GameState, GameIntent) → GameState`
- **Compose-native** — `StateFlow` + `collectAsState()` integrates perfectly

**Why not just use mutable state?**
With 60 updates/second, mutable shared state leads to race conditions and hard-to-debug rendering glitches. Immutable snapshots eliminate this class of bug entirely.

---

## 4. Ktor Client — Networking

**Decision:** Use Ktor 3.0.x for both HTTP (leaderboard) and WebSocket (multiplayer).

**Why Ktor over Retrofit/OkHttp?**
- **KMP-native** — Ktor is designed for Kotlin Multiplatform from the ground up
- **Single API** for HTTP and WebSocket — leaderboard API and multiplayer both use Ktor
- **Pluggable engines** — `OkHttp` on Android, `Darwin` on iOS, `Java` on Desktop
- Zero Java-only dependencies in `commonMain`
- All 3 winners (HuntIt, Clarity, ClipWhisper) used Ktor

**Leaderboard:** Calls Firebase Firestore via its REST API. No Firebase SDK needed —
just `ktor-client-content-negotiation` + `ktor-serialization-kotlinx-json`.
This keeps the project clean and 100% Kotlin.

**Multiplayer:** Connects to the existing Colyseus WebSocket server via `ktor-client-websockets`.
The Colyseus server (Node.js) is separate from the KMP client — it does not affect Kotlin %.

---

## 5. Koin — Dependency Injection

**Decision:** Koin 4.0.x with `koin-compose` for ViewModel injection.

**Why Koin over Hilt/Dagger?**
- **KMP-native** — Hilt and Dagger are Android-only. Koin works in `commonMain`.
- **Compose-first** — `koinViewModel()` in any composable, no annotations
- **Simple** — no code generation, just Kotlin DSL module declarations
- Used by all 3 contest winners

---

## 6. SQLDelight — Local Storage

**Decision:** SQLDelight 2.0.x for local high scores and player profile.

**Why SQLDelight over Room?**
- **KMP-native** — Room is Android-only. SQLDelight generates Kotlin code for all targets.
- **Type-safe SQL** — queries written in `.sq` files generate Kotlin extension functions
- **Driver-per-platform** — Android uses `AndroidSqliteDriver`, Desktop uses `SqliteDriver`, iOS uses `NativeSqliteDriver` — all configured in the platform `di/` modules

**What we store locally:**
- Player profile (name, country, playerId)
- Per-level best score + best time
- Unlocked level IDs
- Total games + total score

---

## 7. Multiplatform Settings — Preferences

**Decision:** `multiplatform-settings` 1.2.x for lightweight key-value preferences.

**Why separate from SQLDelight?**
SQLDelight is for relational data (scores, profiles). Some prefs are simpler:
- `sc_unlocked` — JSON array of unlocked level IDs
- `sc_cursor_scale` — cursor size preference (1 / 1.15 / 1.32)
- `sc_reduced_motion` — accessibility toggle
- `sc_player_id` — stable UUID (matches Firestore player doc)

`multiplatform-settings` maps to `SharedPreferences` (Android), `NSUserDefaults` (iOS),
and `java.util.prefs.Preferences` (Desktop). Zero `expect/actual` needed.

---

## 8. kotlinx.serialization — JSON

**Decision:** `kotlinx-serialization-json` 1.7.x

**Why not Gson/Moshi?**
- Gson and Moshi are JVM-only. `kotlinx.serialization` is KMP-native.
- Compile-time code generation — no reflection, works on Kotlin/Native (iOS)
- Integrates directly with Ktor's `ContentNegotiation` plugin
- `@Serializable` annotation — simple and idiomatic

Used for:
- Firebase REST API request/response DTOs
- Multiplayer WebSocket message protocol
- Level config serialization (if needed for remote config later)

---

## 9. Platform Strategy — No Mac, Windows Only

**Decision:** Android + Desktop JVM as primary shipping targets. iOS configured but not locally buildable.

**Context:** The developer has a Windows laptop only. Building and signing iOS apps requires macOS + Xcode.

**Solution in 3 parts:**

### Part 1: Full iOS source code written
The iOS target is configured in `shared/build.gradle.kts` (iosX64, iosArm64, iosSimulatorArm64).
All `iosMain` actual implementations are written. The code is 100% iOS-compatible.

### Part 2: GitHub Actions CI verifies iOS compilation
A `.github/workflows/ios-build.yml` workflow runs on every push using a `macos-latest` runner.
This proves to judges the iOS target compiles — even without a local Mac.

```yaml
# .github/workflows/ios-build.yml
- uses: actions/checkout@v4
- uses: actions/setup-java@v4
  with: { distribution: temurin, java-version: 17 }
- run: ./gradlew :shared:compileKotlinIosSimulatorArm64
- run: ./gradlew :shared:iosSimulatorArm64Test
```

### Part 3: README states clearly
```
| Platform        | Status      | How to build                          |
|----------------|-------------|---------------------------------------|
| Android         | ✅ Tested   | Android Studio or ./gradlew installDebug |
| Desktop         | ✅ Tested   | ./gradlew :composeApp:run             |
| iOS             | 🔧 Ready    | Requires Xcode on macOS — CI verified |
```

This is honest, professional, and shows engineering maturity. Judges appreciate this.

---

## 10. Multiplayer Server — Colyseus (Node.js, kept as-is)

**Decision:** Keep the existing Colyseus server. Do NOT port it to Ktor for the submission.

**Why:**
- Porting the server adds weeks of work with minimal contest impact
- The Colyseus server is a **separate repository/process** — judges evaluate the KMP client code
- The KMP client (`MultiplayerClient.kt` in `commonMain`) is 100% Kotlin and fully shared
- The contest scores the client KMP usage, not the server language
- Post-contest: the server can be ported to Ktor for a full Kotlin stack

**Future consideration:** A Ktor WebSocket server would be the ideal long-term solution.

---

## 11. What We Deliberately Avoided

| Rejected | Reason |
|---|---|
| LibGDX | JVM-only game engine, not idiomatic KMP, hurts Kotlin % story |
| Korge | Not Compose Multiplatform, separate ecosystem, risky maturity |
| Retrofit | Android/JVM only — Ktor is the KMP-native equivalent |
| Room | Android-only — SQLDelight is the KMP-native equivalent |
| Hilt/Dagger | Android-only — Koin is the KMP-native equivalent |
| Firebase Android SDK | Android-only — REST API via Ktor is the KMP solution |
| Kotlin/JS Web target | Beta WASM, StudySpark proved it doesn't win |
| Moshi/Gson | JVM-only — `kotlinx.serialization` is the KMP solution |
| DataStore | Android-only — `multiplatform-settings` is the KMP solution |
