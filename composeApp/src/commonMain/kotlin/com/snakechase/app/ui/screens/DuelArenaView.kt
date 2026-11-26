package com.snakechase.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.MultiplayerRole
import com.snakechase.app.domain.model.MultiplayerState
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.platform.isKeyboardPrimary
import com.snakechase.app.ui.canvas.GameCanvas
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeChip
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconButton
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadePanel
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Jungle
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle
import kotlinx.coroutines.isActive

/** The level world a duel arena tier is played in (its art, colours and backdrop). */
internal fun duelArenaLevel(tierIndex: Int): Int = when (tierIndex) {
    1 -> 2
    2 -> 4
    3 -> 5
    4 -> 3
    else -> 1
}

// ─── 4. Live Duel Arena View (0ms Instant Aim Reticle & Responsive Controls) ────

@Composable
internal fun DuelArenaView(
    state: MultiplayerState,
    isCompact: Boolean,
    localInputPos: Vec2?,
    showQuickChat: Boolean,
    onToggleQuickChat: () -> Unit,
    onSendQuickChat: (String, String?) -> Unit,
    onLocalMove: (Vec2) -> Unit,
    onRivalMove: (Vec2) -> Unit,
    onQuit: () -> Unit,
    keyboardOnly: Boolean = false,
    /** Steering by on-screen thumbstick (Settings → Joystick): arena touches are ignored. */
    useJoystick: Boolean = false,
    ghost: GhostOverlay? = null,
    /** Off in single-player modes (Snake Hunt): there's nobody to chat with. */
    allowQuickChat: Boolean = true,
    /** Snake Hunt draws its own "Hunt starts" countdown. */
    showCountdown: Boolean = true,
    /** Gets every drawn frame of live play (for the slow-motion catch replay). */
    onFrame: ((GameState) -> Unit)? = null,
    /** Null hides the pause button (spectators, Snake Hunt). */
    onPause: (() -> Unit)? = null,
    onResume: () -> Unit = {},
    /** Time left on a live online pause (null = no limit). */
    pauseMsLeft: Long? = null,
    /** My equipped skin: the snake wears it whenever I'm the one playing the snake. */
    mySkin: com.snakechase.app.domain.model.SnakeSkin = com.snakechase.app.domain.model.SnakeSkin.DEFAULT,
    /** My equipped orb skin: the prey wears it whenever I'm the one playing the orb. */
    myOrb: OrbSkin = OrbSkin.SPARK,
) {
    // Live matches: everyone sees both players' real skins (shared in the round start).
    // Against the AI / pass & play: my skins on my role, defaults on the other.
    val p1IsSnakeNow = (state.localRole == MultiplayerRole.SNAKE) == !(state.isNetworkMatch && !state.isHost)
    val snakeSkin = if (state.isNetworkMatch) {
        val look = if (p1IsSnakeNow) state.p1Look else state.p2Look
        com.snakechase.app.domain.model.SnakeSkin.entries.firstOrNull { it.name == look.snake } ?: com.snakechase.app.domain.model.SnakeSkin.DEFAULT
    } else if (state.localRole == MultiplayerRole.SNAKE && !state.isSpectator) {
        mySkin
    } else {
        com.snakechase.app.domain.model.SnakeSkin.DEFAULT
    }
    val orbSkin = if (state.isNetworkMatch) {
        OrbSkin.byName(if (p1IsSnakeNow) state.p2Look.orb else state.p1Look.orb)
    } else if (state.localRole == MultiplayerRole.PREY && !state.isSpectator) {
        myOrb
    } else {
        OrbSkin.SPARK
    }
    // The online engine sends positions only, so the orb's trail is kept here (newest first).
    val preyTrail = remember { mutableStateOf(emptyList<Vec2>()) }
    val preyPos = state.prey.position
    LaunchedEffect(preyPos) { preyTrail.value = (listOf(preyPos) + preyTrail.value).take(12) }
    val canvasGameState = remember(state.snake, state.prey, snakeSkin, orbSkin, preyTrail.value, state.helperOrb, state.preyBoostMs > 0L) {
        GameState(
            levelId = duelArenaLevel(state.selectedTierIndex),
            player = state.prey.copy(trailPoints = preyTrail.value),
            primarySnake = state.snake,
            activeSnakeSkin = snakeSkin,
            activeOrbSkin = orbSkin,
            obstacles = state.obstacles,
            arenaSize = state.arenaSize,
            activePowerUpOrb = state.helperOrb?.let { com.snakechase.app.domain.model.PowerUpOrb(com.snakechase.app.domain.model.PowerUpType.SPEED_BURST, it) },
            activePowerUps = if (state.preyBoostMs > 0L) {
                listOf(com.snakechase.app.domain.model.ActivePowerUp(com.snakechase.app.domain.model.PowerUpType.SPEED_BURST, state.preyBoostMs, com.snakechase.app.engine.MultiplayerEngine.HELPER_BOOST_MS))
            } else {
                emptyList()
            },
        )
    }
    LaunchedEffect(canvasGameState) {
        if (state.phase == DuelPhase.ROUND_PLAYING) onFrame?.invoke(canvasGameState)
    }

    val focusRequester = remember { FocusRequester() }
    val heldKeys = remember { mutableStateOf(setOf<Key>()) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Keyboard steering: while a direction is held, aim a point just ahead of what I control,
    // re-computed every frame so holding a key keeps me moving (not just one 160-unit hop).
    // Releasing the keys stops the prey; the snake always keeps running forward.
    val latestState by rememberUpdatedState(state)
    val latestOnLocalMove by rememberUpdatedState(onLocalMove)
    LaunchedEffect(Unit) {
        var steering = false
        while (isActive) {
            withFrameMillis { }
            val keys = heldKeys.value
            var dx = 0f
            var dy = 0f
            if (Key.W in keys || Key.DirectionUp in keys) dy -= 1f
            if (Key.S in keys || Key.DirectionDown in keys) dy += 1f
            if (Key.A in keys || Key.DirectionLeft in keys) dx -= 1f
            if (Key.D in keys || Key.DirectionRight in keys) dx += 1f
            val s = latestState
            if (dx != 0f || dy != 0f) {
                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                val origin = if (s.localRole == MultiplayerRole.SNAKE) s.snake?.headPosition ?: s.prey.position else s.prey.position
                latestOnLocalMove(
                    Vec2(
                        x = (origin.x + dx / len * KEYBOARD_AIM_AHEAD).coerceIn(24f, s.arenaSize.x - 24f),
                        y = (origin.y + dy / len * KEYBOARD_AIM_AHEAD).coerceIn(24f, s.arenaSize.y - 24f),
                    ),
                )
                steering = true
            } else if (steering) {
                steering = false
                if (s.localRole == MultiplayerRole.PREY) latestOnLocalMove(s.prey.position)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                val key = keyEvent.key
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        when (key) {
                            Key.W, Key.S, Key.A, Key.D,
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                            -> {
                                heldKeys.value = heldKeys.value + key
                                true
                            }
                            Key.Escape -> {
                                onQuit()
                                true
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        heldKeys.value = heldKeys.value - key
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Steering listens on the arena only (not the whole screen), so taps on the HUD's
        // buttons, like quit, reach those buttons instead of being turned into steering.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state.mode, state.localRole, keyboardOnly) {
                    // Keyboard-only players ignore the mouse (pass & play is touch by nature, so it keeps it).
                    val ignorePointer = (keyboardOnly || useJoystick) && state.mode != DuelMode.LOCAL_PASS_AND_PLAY
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes

                            // A mouse steers just by pointing (like the levels); touch steers while held.
                            fun steers(c: androidx.compose.ui.input.pointer.PointerInputChange) = c.pressed || (c.type == androidx.compose.ui.input.pointer.PointerType.Mouse && state.mode != DuelMode.LOCAL_PASS_AND_PLAY)
                            if (!ignorePointer && changes.any { steers(it) }) {
                                val arenaScale = minOf(size.width.toFloat() / state.arenaSize.x, size.height.toFloat() / state.arenaSize.y)
                                val offsetX = (size.width - state.arenaSize.x * arenaScale) / 2f
                                val offsetY = (size.height - state.arenaSize.y * arenaScale) / 2f

                                if (state.mode == DuelMode.LOCAL_PASS_AND_PLAY) {
                                    // Multi-touch: check each active pointer for left vs right half
                                    changes.filter { it.pressed }.forEach { change ->
                                        change.consume()
                                        val touchPos = change.position
                                        val logicalX = ((touchPos.x - offsetX) / arenaScale).coerceIn(20f, state.arenaSize.x - 20f)
                                        val logicalY = ((touchPos.y - offsetY) / arenaScale).coerceIn(20f, state.arenaSize.y - 20f)
                                        val targetPos = Vec2(logicalX, logicalY)

                                        if (touchPos.x < size.width / 2f) {
                                            // Left half: Player 1
                                            onLocalMove(targetPos)
                                        } else {
                                            // Right half: Player 2
                                            onRivalMove(targetPos)
                                        }
                                    }
                                } else {
                                    // Single player / VS AI / Online
                                    val primaryChange = changes.firstOrNull { steers(it) }
                                    if (primaryChange != null) {
                                        primaryChange.consume()
                                        val touchPos = primaryChange.position
                                        val logicalX = ((touchPos.x - offsetX) / arenaScale).coerceIn(20f, state.arenaSize.x - 20f)
                                        val logicalY = ((touchPos.y - offsetY) / arenaScale).coerceIn(20f, state.arenaSize.y - 20f)
                                        val targetPos = Vec2(logicalX, logicalY)
                                        onLocalMove(targetPos)
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            GameCanvas(
                state = canvasGameState,
                onPlayerDragDelta = null,
            )
        }

        // Overlay: online rival's ghost (their recorded run, replayed in sync with the round clock)
        if (ghost != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = minOf(size.width / state.arenaSize.x, size.height / state.arenaSize.y)
                val offsetX = (size.width - state.arenaSize.x * scale) / 2f
                val offsetY = (size.height - state.arenaSize.y * scale) / 2f
                ghost.position?.let { p ->
                    val c = Offset(offsetX + p.x * scale, offsetY + p.y * scale)
                    val r = (if (ghost.isSnake) 16f else 14f) * scale
                    drawCircle(color = GhostViolet.copy(alpha = 0.2f), radius = r * 2.2f, center = c)
                    drawCircle(color = Color.White.copy(alpha = 0.4f), radius = r, center = c)
                    drawCircle(
                        color = GhostViolet,
                        radius = r,
                        center = c,
                        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)),
                    )
                }
            }
            ArcadeChip(ghost.label, fill = GhostViolet, icon = ArcadeIcon.Eye, modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp))
        }

        // Overlay: pass and play split line and zone labels
        if (state.mode == DuelMode.LOCAL_PASS_AND_PLAY) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midX = size.width / 2f
                drawLine(Ink, Offset(midX, 0f), Offset(midX, size.height), strokeWidth = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f))
                drawLine(Cream, Offset(midX, 0f), Offset(midX, size.height), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 48.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val p1Prey = state.localRole == MultiplayerRole.PREY
                ArcadeChip("Player 1 · ${if (p1Prey) "prey" else "snake"}", fill = Lime, icon = ArcadeIcon.Back)
                ArcadeChip("Player 2 · ${if (p1Prey) "snake" else "prey"}", fill = Berry, icon = ArcadeIcon.Play)
            }
        }

        // Bottom hint
        ArcadeChip(
            text = when {
                state.isSpectator -> "You're watching live: ${state.player1Name} vs ${state.player2Name}"
                keyboardOnly && state.localRole == MultiplayerRole.SNAKE -> "WASD steers the snake · Esc quits"
                keyboardOnly -> "WASD to flee and survive · Esc quits"
                state.localRole == MultiplayerRole.SNAKE && isKeyboardPrimary -> "Point with the mouse to hunt: the snake follows it · Esc quits"
                state.localRole == MultiplayerRole.SNAKE -> "Hold and move your finger: the snake follows it"
                isKeyboardPrimary -> "Point with the mouse (or WASD) to flee · Esc quits"
                else -> "Drag to flee and survive the timer"
            },
            fill = if (state.isSpectator) {
                Sky
            } else if (state.localRole == MultiplayerRole.SNAKE) {
                Banana
            } else {
                Lime
            },
            icon = if (state.isSpectator) {
                ArcadeIcon.Eye
            } else if (state.localRole == MultiplayerRole.SNAKE) {
                ArcadeIcon.Target
            } else {
                ArcadeIcon.Bolt
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (isCompact) 8.dp else 14.dp),
        )

        // Top bar: player badges, round and clock, chat and quit.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // Player 1 is always the host / left player. `localRole` is *this* device's role,
            // so on a network guest it describes player 2 and must be inverted.
            val localIsPlayer1 = !(state.isNetworkMatch && !state.isHost)
            val p1IsSnake = (state.localRole == MultiplayerRole.SNAKE) == localIsPlayer1
            PlayerBadge(state.player1Name, p1IsSnake)

            ArcadePanel(fill = Cream, shape = RoundedCornerShape(16.dp), shadow = 3.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.roundAnnouncement ?: "Round ${state.round} of ${state.maxRounds}", style = displayStyle(12.sp, Ink.copy(alpha = 0.75f)))
                    Text("${(state.survivalTimeMs / 1000f).format(1)}s", style = displayStyle(22.sp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerBadge(state.player2Name, !p1IsSnake)
                // One shared screen in pass and play: nobody to banter with.
                if (allowQuickChat && state.mode != DuelMode.LOCAL_PASS_AND_PLAY) ArcadeIconButton(ArcadeIcon.Chat, onClick = onToggleQuickChat, fill = Sky, size = 36.dp)
                if (onPause != null && state.phase == DuelPhase.ROUND_PLAYING && state.pausedBy == null) {
                    ArcadeIconButton(ArcadeIcon.Pause, onClick = onPause, fill = Banana, size = 36.dp)
                }
                ArcadeIconButton(ArcadeIcon.Close, onClick = onQuit, fill = Berry, size = 36.dp)
            }
        }

        // Latest quick-chat message: a small sticker under the round timer, clear of the play area.
        state.activeChatBubbles.lastOrNull()?.let { chat ->
            ArcadePanel(fill = Cream, shape = RoundedCornerShape(14.dp), modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArcadeIconView(ArcadeIcon.Chat, tint = Ink, size = 16.dp)
                    Text(chat.text, style = displayStyle(14.sp))
                }
            }
        }

        // Quick chat picker: a compact card under the chat button, never a full-width sheet.
        if (showQuickChat && state.mode != DuelMode.LOCAL_PASS_AND_PLAY) {
            ArcadePanel(
                fill = Jungle,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 12.dp).width(220.dp),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Quick banter", style = displayStyle(13.sp, Banana))
                    QUICK_CHAT_PHRASES.forEach { phrase ->
                        ArcadeChip(phrase, fill = Cream, onClick = { onSendQuickChat(phrase, null) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // Between rounds
        if (state.phase == DuelPhase.ROUND_TRANSITION) {
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                ArcadePanel(fill = Jungle, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(horizontal = 30.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedText("Round ${state.round} done!", displayStyle(30.sp), fill = Banana, outlineWidth = 6.dp, shadow = 3.dp)
                        Text("Survived ${(state.survivalTimeMs / 1000f).format(1)}s", style = displayStyle(18.sp, Cream))
                        ArcadeTag("Swap roles · get ready!", Lime)
                    }
                }
            }
        }

        // Paused: frozen for both players; online the pauser has 10 s to come back.
        state.pausedBy?.let { who ->
            val mine = who == com.snakechase.app.network.DuelSession.YOU
            val secs = pauseMsLeft?.let { ((it + 999) / 1000).toInt() }
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                ArcadePanel(fill = Jungle, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(horizontal = 30.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedText(if (mine) "Paused" else "$who paused", displayStyle(30.sp), fill = Banana, outlineWidth = 6.dp, shadow = 3.dp)
                        if (secs != null) {
                            Text(
                                if (mine) "Come back within $secs s or your rival wins" else "They have $secs s to come back, or you win",
                                style = displayStyle(16.sp, Cream),
                            )
                        }
                        if (mine) ArcadeButton("Resume", icon = ArcadeIcon.Play, fill = Lime, onClick = onResume)
                    }
                }
            }
        }

        // Thumbstick: moves an aim point from my snake/orb, which my side then follows.
        if (useJoystick && !state.isSpectator && state.mode != DuelMode.LOCAL_PASS_AND_PLAY && state.phase == DuelPhase.ROUND_PLAYING) {
            var stick by remember { mutableStateOf(Vec2.ZERO) }
            var aim by remember(state.round) { mutableStateOf<Vec2?>(null) }
            LaunchedEffect(state.round) {
                var last = 0L
                while (true) {
                    androidx.compose.runtime.withFrameMillis { now ->
                        val dt = if (last == 0L) 0f else ((now - last).coerceIn(1L, 50L)) / 1000f
                        last = now
                        if (stick.length() > 0.05f) {
                            val mine = if (state.localRole == MultiplayerRole.SNAKE) state.snake?.headPosition else state.prey.position
                            val from = aim ?: mine ?: return@withFrameMillis
                            val next = from + stick * (DUEL_STICK_SPEED * dt)
                            val clamped = Vec2(next.x.coerceIn(20f, state.arenaSize.x - 20f), next.y.coerceIn(20f, state.arenaSize.y - 20f))
                            aim = clamped
                            onLocalMove(clamped)
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 24.dp)) {
                com.snakechase.app.ui.components.VirtualJoystick(onVectorChange = { stick = it }, size = 140.dp)
            }
        }

        // 3-2-1-Go before every round, on both players' screens (same stamp as the levels).
        if (showCountdown && state.phase == DuelPhase.COUNTDOWN) {
            val label = when {
                state.isSpectator -> "Round ${state.round} · ${state.player1Name} vs ${state.player2Name}"
                state.localRole == MultiplayerRole.SNAKE -> "Round ${state.round} · you're the snake"
                else -> "Round ${state.round} · you're the prey"
            }
            CountdownStamp(duelArenaLevel(state.selectedTierIndex), label, state.countdownCount)
        }
    }
}

private val GhostViolet = Color(0xFFC6A8FF)

/** A player's name with their current role, as a sticker badge. */
@Composable
private fun PlayerBadge(name: String, isSnake: Boolean) {
    ArcadeChip(
        text = "$name · ${if (isSnake) "snake" else "prey"}",
        fill = if (isSnake) Banana else Lime,
        icon = if (isSnake) ArcadeIcon.Target else ArcadeIcon.Bolt,
    )
}

/** How fast the thumbstick's aim point moves across the duel arena (arena units per second). */
private const val DUEL_STICK_SPEED = 420f
