# Development log

Snake Chase was built during the Kotlin Multiplatform Contest period, starting on 29 September 2026.
I only started using git on 26 November, so the first commit imports everything built until then.
This log was written afterwards from my notes and the files' creation dates, so the dates are
approximate. Everything after the first commit is in the git history.

## Before the first commit

**29-30 September: the foundation**
- Kotlin Multiplatform project with Compose Multiplatform for Android, desktop and the web
  (Kotlin/Wasm).
- The pure game engine (`GameEngine.tick`), collision detection, scoring and the first tests.
- The five worlds: Neon Drift, Neon Grove, Abyssal Chase, Crystal Tundra and Crucible Core.

**October: gameplay depth**
- Power-ups (shield, speed burst, magnet, ghost walk) and the dash.
- The daily trial and the bestiary of world creatures.

**19-20 November: duels and the online stack**
- A smarter snake AI, and the 1v1 duel engine: three rounds, roles flip every round.
- Live online duels through Firebase over REST and server-sent events, using Ktor on every platform.
- Nearby duels on the same Wi-Fi with an embedded Ktor WebSocket server (Android and desktop).
- Snapshot interpolation for smooth rival movement, and connectivity detection.
- Vs AI rivals, and continuous integration on GitHub Actions (including the iOS compile).

**21-22 November: the arcade look and the online world**
- The "Jungle Arcade" design kit, fonts, and share cards for every result.
- Easy / Moderate / Hard difficulty, spinning obstacles and whirlpools.
- The Hall of Fame with the world top 25, ghost duels and the online arena.

**24-25 November: polish for players and judges**
- App icon, splash and a new animated loading screen for the web.
- Per-world music, new sound effects and balanced volumes.
- Skin ratings and effects, hearts with revive, and the slow-motion catch replay in duels.
- Spectators, duel pause, rival skins online and the joystick in every mode.
- The welcome tour and first-run tips, offline handling and fixes for sign-in expiry.
- The essay, the README and screenshots.

## From the first commit onwards

See the [commit history](https://github.com/hamdansalifupolibu/snake-chase-kmp/commits/main).
