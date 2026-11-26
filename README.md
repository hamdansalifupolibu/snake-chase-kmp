# Snake Chase

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Desktop](https://img.shields.io/badge/Desktop-JVM-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)
![Web](https://img.shields.io/badge/Web-Kotlin%2FWasm-654FF0?style=for-the-badge&logo=webassembly&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)

**Snake, turned inside out.** You are the glowing orb, and the snake is hunting you. Survive, graze
past it for points, and clear five hand-painted worlds. Or duel a friend live:
**3 rounds · roles flip · best chase wins**, phone against browser against desktop.

### ▶ Play it now in your browser: **https://snake-chase01.web.app**

<p align="center">
  <img src="docs/media/play.jpg" width="49%" alt="Crystal Tundra: the orb weaves between snow whirls, frostlings and falling icicles">
  <img src="docs/media/duel.jpg" width="49%" alt="A 1v1 duel: one player is the snake, the other the orb">
</p>
<p align="center">
  <img src="docs/media/menu.jpg" width="32%" alt="Main menu with the five worlds">
  <img src="docs/media/shop.jpg" width="32%" alt="Skin shop: every skin has its own effect and ratings">
  <img src="docs/media/loader.jpg" width="32%" alt="Loading screen: the snake grows as the game loads">
</p>

🎥 **Video demo:** _link added at submission_

---

## The story

Snake Chase started on my dad's Nokia "yam phone", as we call it in Ghana. I lost my dad when I
was 13, and that game is one of the memories I still have of him. Snake was the first phone game
for millions of people, and I wanted to bring it back on the devices we use today. So I turned it around: you are the food. Built with Kotlin Multiplatform and Compose
Multiplatform by **Hamdan Salifu Polibu**, University of Mines and Technology, Ghana.
[Read the full essay →](docs/ESSAY.md) · [How it was built, step by step →](docs/DEVLOG.md)

## One codebase, three platforms

About **95% of the app's code is shared Kotlin** in `commonMain`: the game engine, the AI, every screen,
the online duels, scoring, skins, sound logic and the leaderboard. Each platform adds only a thin
layer:

| Platform | How it runs | Platform-specific code |
| --- | --- | --- |
| **Web** | Kotlin/Wasm in any modern browser | ~270 lines: Web Audio, Web Share |
| **Android** | Native app (API 26+) | ~300 lines: SoundPool, vibration, share sheet |
| **Desktop** | JVM app for Windows, macOS, Linux | ~300 lines: Java Sound, window, clipboard sharing |
| **iOS** | Shared code compiles for iOS in CI | not one of the submitted platforms; not run on a device |

---

## Judge's walkthrough (about 10 minutes)

This build is the **judge edition**: every level, skin and hunt is unlocked and the wallet is full,
on every platform.

1. **First look (web, 1 minute).** Open https://snake-chase01.web.app. A short welcome tour explains
   the game and points to the **judge tools** (the scales icon, top-right). Your first run pauses
   on one tip card, then the 3-2-1-GO starts.
   *KMP:* the same Compose UI and engine you will see on Android and desktop, compiled to WebAssembly.
2. **Play a world.** Pick a world, press **Play**. The orb simply follows your pointer: the mouse,
   your finger, the keyboard (WASD / arrows) or an on-screen thumbstick. Graze past the snake for
   points: the closer the call, the bigger the score. If it catches you, watch the slow-motion
   replay, then **Revive** with a heart.
   *KMP:* `GameEngine.tick(state, input, dt)` is one pure function shared by every platform.
3. **See all 36 stages fast.** Open the **judge tools**: turn on god mode, jump to any world and
   stage, or **Win level** to skip ahead. Try *Abyssal Chase* (whirlpools), *Crucible Core* (lava
   cracks) and *Crystal Tundra, stage 7* (frostlings, snow whirls, icicles, thin ice).
4. **Change your look.** Open the skin shop (palette icon), pick a snake on **Snakes** and an orb on
   **Orbs**, then start a stage to see them in play.
5. **Settings (gear icon).** Touch drag or joystick, left or right thumb, music and sound volume,
   vibration, difficulty, particle quality and meme roasts. Best tried on a phone.
6. **Duel across devices (the highlight).** On one device: **1v1 Duel → Friend invite → Host**. On
   another (phone, browser or desktop): **Join** with the room code. You get a synced 3-2-1, three
   rounds with roles flipping (the hunter becomes the hunted), pause (10 seconds to come back),
   quick chat and rematch. Afterwards, **Share** your result.
   *KMP:* one networking stack in common code (Ktor + Firebase over REST and server-sent events).
7. **Watch a duel.** With three or more people, two play and everyone else joins with the same code:
   **Friend invite → Join tab → Watch**. You see the match live; the players see "1 watching".
8. **Play against the AI.** **1v1 Duel → Vs AI rival**: you take turns as snake and orb against the
   Wanderer (Easy), the Hunter (Moderate) or the Ghost (Hard, faster and predicts your moves). Then
   try **Snake Hunt** from the home screen, where you are the snake. Both work offline.
9. **No internet? Still a duel.** On Android or desktop, with the same Wi-Fi and no internet:
   **Friend invite → Nearby**. One device hosts and shows its local IP address; the other joins with
   that address. It runs through an embedded Ktor WebSocket server.
10. **Profile and Hall of Fame.** The trophy icon shows your bests with each level's world record,
    and the world top 25 from Firebase, with real players.

---

## Features

**Single player**
- **5 worlds, 36 stages**: Neon Drift, Neon Grove, Abyssal Chase, Crystal Tundra and Crucible Core,
  each with its own hazards, creatures and music ([world identity rule](docs/WORLD_IDENTITY.md)).
- **Easy / Moderate / Hard**: Easy has slower, less predictive snakes, fewer obstacles, a shield at
  every stage and one rescue per stage. Scoring is identical; the world leaderboard counts Moderate
  and Hard.
- **Hearts**: 3 revives that continue a run from just before the catch. Empty hearts come back
  over time, or with coins earned in play.
- **Power-ups**: invincible shield, speed burst, score magnet, ghost walk, plus a dash.
- **Slow-motion catch replay**: the camera pushes in on the bite, with sound and vibration.
- **Daily trial**: one shared seed a day with a twist.
- **Snake Hunt**: you are the snake; catch an AI orb before its clock runs out. Works offline.

**1v1 Duels**: 3 rounds, roles flip every round, the longest survival wins

| Mode | How it connects |
| --- | --- |
| **Vs AI rival** | Offline, on your device. Three AI personalities (the Wanderer, the Hunter, the Ghost). |
| **Friend invite, online** | A room code, played across phone, browser and desktop through Firebase. |
| **Friend invite, Nearby** | Same Wi-Fi, no internet: an embedded Ktor server on Android or desktop. |
| **Pass and play** | One screen, two players: left half against right half. |
| **Online arena** | Quick match against a live stranger, or a ghost duel against a real player's recorded run, with coin stakes per world. |

Every live duel has a synced countdown, pause with a 10-second return window, quick chat, rematch,
**spectators** (watch any room by its code), both players' real skins, and share cards.

**Progress and polish**
- Stars per level, 13 snake skins and 11 orb skins (bought, or earned by feats), each with its own
  animated effect (for example fireflies around an orb, or a glowing snake) and 1-5 ratings: orbs
  for **Speed / Dash / Graze**, snakes for **Speed / Agility / Reach**. Skins give at most a small
  edge, and never in live online duels. Both players see each other's real skins online.
- Player card with ranks and badges. **Share cards** for every result: your score drawn over the
  world's painted background, with a link that opens the game in a browser so friends can play you
  back.
- **Welcome tour** on first open, and one tip card before your first run (it shows the controls for
  your device), so new players and judges know where everything is.
- Per-world music and original sound effects ([credits](docs/CREDITS.md)), haptics, screen shake,
  and optional meme roasts after every third loss.
- Controls for everyone: mouse, touch, keyboard, or an on-screen thumbstick on the left or right
  thumb, for left- and right-handed players, in every mode (levels, Vs AI, online and friend duels).
- Settings: music and sound volumes, vibration, difficulty, Ultra particles or battery saver, meme
  roasts on or off, and a full progress reset.
- Offline-aware: an offline banner, online modes clearly marked, and results queued for upload.

---

## How players connect

```mermaid
flowchart LR
  subgraph Shared["commonMain (shared Kotlin)"]
    E["GameEngine / MultiplayerEngine<br/>pure state + input → next state"]
    S["DuelSession<br/>host-authoritative match"]
    T["Transports: Firebase rooms · LAN WebSocket"]
    E --- S --- T
  end
  H["Host device<br/>(simulates the match)"] --> S
  T -- "REST writes + server-sent events (Ktor)" --> F[("Firebase Realtime Database")]
  F -- "state ~13 updates/s" --> G["Guest device"]
  F -- "read-only" --> W["Spectators"]
  T -. "same Wi-Fi, no internet" .-> L["Embedded Ktor server (Android / desktop)"]
```

- **Online:** no game server. The host simulates; game state and the guest's input travel through
  Firebase Realtime Database as plain REST writes and server-sent events, using Ktor on every
  platform. Several writes stay in flight at once (about 13 updates a second), and the receiver
  drops anything that arrives out of order.
- **Resilient:** sign-in is refreshed automatically when it expires, room creation retries, and a
  peer that disappears is detected by heartbeat.
- **Offline:** single-player always works. On Android and desktop, Nearby duels run over the local
  Wi-Fi through an embedded Ktor WebSocket server. Scores made offline upload later.

## Run it

Requirements: **JDK 21** and the Android SDK for Android. Everything else comes through the Gradle
wrapper.

| Platform | Command |
| --- | --- |
| Web (dev server, opens the browser) | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Desktop (Windows / macOS / Linux) | `./gradlew :composeApp:run` |
| Android (emulator or device) | `./gradlew :composeApp:installDebug` |
| Web (production bundle) | `./gradlew :composeApp:wasmJsBrowserDistribution` → `composeApp/build/dist/wasmJs/productionExecutable` |
| Tests | `./gradlew :composeApp:desktopTest` and `./gradlew ktlintCheck` |

On Windows use `gradlew.bat`. Ready-made builds (Android APK, desktop app) are attached to the
[Releases](../../releases) page.

## How it is built

```
composeApp/src/
  commonMain/        the whole game, shared by every platform
    engine/          GameEngine (pure tick), SnakeAI, MultiplayerEngine, Vortex, Solid,
                     PowerUpSystem, ScoringEngine
    levels/          5 world definitions, scoring budget, stars
    presentation/    GameViewModel + GameIntent (unidirectional state flow)
    network/         DuelSession, Firebase Realtime Database + LAN transports, quick match
    online/          leaderboard, ghost duels, offline upload queue
    profile/         profile, hearts, player card
    ui/              Compose screens, canvas renderers, the "Jungle Arcade" design kit
    platform/        expect declarations: audio, haptics, sharing, sound bank
  androidMain/ desktopMain/ wasmJsMain/ iosMain/   small `actual` implementations
  jvmAndAndroidMain/                               the embedded LAN duel server (Ktor)
  commonTest/ desktopTest/                         139 tests
```

- **A pure engine.** The engine never mutates state; it returns the next immutable `GameState`. That
  makes whole runs, duels and the real ViewModel testable frame by frame on any platform.
- **`expect`/`actual` only at the edges:** audio, haptics, sharing and the LAN server.
- **Kotlin conventions** are enforced by ktlint (IntelliJ Kotlin style) in CI.

Tech: Kotlin 2.1 · Compose Multiplatform 1.7 · Kotlin/Wasm · Koin · Ktor · kotlinx.serialization ·
kotlinx.datetime · multiplatform-settings · Firebase (Auth, Firestore, Realtime Database, Hosting)
over REST · ktlint. More detail: [architecture](docs/ARCHITECTURE.md),
[technical decisions](docs/TECH_DECISIONS.md).

## Troubleshooting

- **The web page stays on the loader:** the game needs a browser with WebAssembly GC (current
  Chrome, Edge, Firefox, or Safari 18.2+).
- **A duel won't connect:** both devices need internet for online rooms. With no internet, use
  **Nearby** on Android or desktop, on the same Wi-Fi.
- **Android emulator says "offline" after your computer reconnects:** the emulator's network can
  get stuck after the host's connection drops; restart the emulator.

## Credits & licence

Created by **Hamdan Salifu Polibu** for the Kotlin Multiplatform Contest.
Music and sound credits (all CC0 third-party audio): [docs/CREDITS.md](docs/CREDITS.md).
Licensed under the [Apache License 2.0](LICENSE).
