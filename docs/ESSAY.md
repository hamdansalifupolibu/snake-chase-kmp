# Snake Chase

Snake Chase started on my dad's "yam phone", the Nokia feature phone we call that in Ghana. I loved
steering that pixel snake around its tiny screen. I lost my dad when I was 13, and that game is one
of the memories I still carry of him. For millions of people, Snake was the first game they ever
played on a phone. So I asked what it would feel like on the devices we use today.

I'm a Computer Science and Engineering student at the University of Mines and Technology, Ghana. I
had built software with React, React Native and Python, and I wrote my first Kotlin for this contest
last year. Snake Chase is where I took Kotlin Multiplatform further.

I didn't just rebuild Snake. I turned it around. You are a glowing orb, and the snake is hunting
you. You survive, graze past it for points, and clear five hand-painted worlds. In a 1v1 duel the
roles flip every round, even when one player is on a phone and the other is in a browser.

About 95% of the code lives in commonMain and runs on Android, desktop and the web through
Kotlin/Wasm. The game engine is a pure function from state and input to the next state, so the same
rules run, and are tested, on every platform. Live duels need no game server: the host simulates
the match, and state streams through Firebase over REST and server-sent events with Ktor.

Last year I learned what Kotlin Multiplatform is. This year I learned what it can do.

Snake once lived on one small phone. Now it runs on every screen, and anyone who grew up with it can
challenge a friend through a single link. Snake Chase brings a game from my dad's phone back, turned
inside out, for everyone.
