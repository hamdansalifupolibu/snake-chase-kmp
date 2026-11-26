package com.snakechase.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.JoystickSide
import com.snakechase.app.domain.model.SteeringMode
import com.snakechase.app.presentation.GameIntent
import com.snakechase.app.presentation.GameViewModel
import com.snakechase.app.ui.canvas.GameCanvas
import com.snakechase.app.ui.components.DevTestingPanel
import com.snakechase.app.ui.components.FirstRunTips
import com.snakechase.app.ui.components.HudOverlay
import com.snakechase.app.ui.components.PauseOverlay
import com.snakechase.app.ui.components.SettingsOverlay
import com.snakechase.app.ui.components.VirtualJoystick
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun GameScreen(
    state: GameState,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val heldKeys = remember { mutableStateOf(setOf<Key>()) }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // 60fps / 120fps Hardware-synced Game Loop
    var lastFrameTimeMs by remember { mutableStateOf(0L) }

    LaunchedEffect(state.phase) {
        if (state.phase == GamePhase.PLAYING) {
            lastFrameTimeMs = 0L
            while (isActive) {
                withFrameMillis { frameTimeMs ->
                    if (lastFrameTimeMs != 0L) {
                        val deltaMs = (frameTimeMs - lastFrameTimeMs).coerceIn(1L, 50L)

                        // Smooth continuous keyboard steering (WASD & Arrow Keys)
                        val activeKeys = heldKeys.value
                        if (activeKeys.isNotEmpty()) {
                            var dx = 0f
                            var dy = 0f
                            if (Key.W in activeKeys || Key.DirectionUp in activeKeys) dy -= 1f
                            if (Key.S in activeKeys || Key.DirectionDown in activeKeys) dy += 1f
                            if (Key.A in activeKeys || Key.DirectionLeft in activeKeys) dx -= 1f
                            if (Key.D in activeKeys || Key.DirectionRight in activeKeys) dx += 1f

                            if (dx != 0f || dy != 0f) {
                                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                                val dtSec = deltaMs / 1000f
                                val speedPxPerSec = 520f * state.settings.touchSensitivity
                                val moveDist = speedPxPerSec * dtSec
                                viewModel.handleIntent(
                                    GameIntent.PlayerDragDelta(
                                        com.snakechase.app.domain.model.Vec2((dx / len) * moveDist, (dy / len) * moveDist),
                                    ),
                                )
                            }
                        }

                        viewModel.handleIntent(GameIntent.FrameTick(deltaMs))
                    }
                    lastFrameTimeMs = frameTimeMs
                }
            }
        } else {
            heldKeys.value = emptySet()
        }
    }

    // 3-2-1-GO Countdown Sequence (held on the very first run until the tips are read)
    LaunchedEffect(state.phase, state.settings.firstRunTipsSeen) {
        if (state.phase == GamePhase.COUNTDOWN && state.settings.firstRunTipsSeen) {
            viewModel.handleIntent(GameIntent.CountdownTick(3))
            delay(750)
            viewModel.handleIntent(GameIntent.CountdownTick(2))
            delay(750)
            viewModel.handleIntent(GameIntent.CountdownTick(1))
            delay(750)
            viewModel.handleIntent(GameIntent.CountdownTick(0)) // "GO!"
            delay(450)
            viewModel.handleIntent(GameIntent.CountdownFinished)
        }
    }

    // Resume countdown. The game stays paused (frozen) while it counts, then resumes on "Go!".
    // null = not counting; 3..1 = the number shown; 0 = "Go!".
    var resumeCount by remember { mutableStateOf<Int?>(null) }
    val startResume: () -> Unit = { if (resumeCount == null) resumeCount = 3 }
    LaunchedEffect(resumeCount) {
        val n = resumeCount ?: return@LaunchedEffect
        if (n > 0) {
            delay(800)
            resumeCount = n - 1
        } else {
            delay(400)
            resumeCount = null
            viewModel.handleIntent(GameIntent.ResumeGame)
        }
    }
    // Leaving the pause another way (quit, restart) cancels a countdown in progress.
    LaunchedEffect(state.phase) { if (state.phase != GamePhase.PAUSED) resumeCount = null }

    Box(
        modifier = modifier
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
                            Key.Spacebar -> {
                                viewModel.handleIntent(GameIntent.TriggerPhaseDash)
                                true
                            }
                            Key.Escape, Key.P -> {
                                when {
                                    state.phase == GamePhase.PLAYING -> viewModel.handleIntent(GameIntent.PauseGame)
                                    // Esc during the resume countdown goes back to the pause menu.
                                    state.phase == GamePhase.PAUSED && resumeCount != null -> resumeCount = null
                                    state.phase == GamePhase.PAUSED -> startResume()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        when (key) {
                            Key.W, Key.S, Key.A, Key.D,
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                            -> {
                                heldKeys.value = heldKeys.value - key
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            },
    ) {
        // 1. Core Hardware Canvas with Wallpaper & Shaders
        GameCanvas(
            state = state,
            // One steering mode at a time: keyboard and joystick players don't want a stray touch,
            // click or drag in the arena to move the orb.
            onPlayerDragDelta = if (state.settings.steeringMode != SteeringMode.POINTER) {
                null
            } else {
                { delta -> viewModel.handleIntent(GameIntent.PlayerDragDelta(delta)) }
            },
            onDoubleTap = { viewModel.handleIntent(GameIntent.TriggerPhaseDash) },
            onPointerAim = if (state.settings.steeringMode != SteeringMode.POINTER) {
                null
            } else {
                { pos -> viewModel.handleIntent(GameIntent.PlayerAimAt(pos)) }
            },
        )

        // 2. HUD Overlay
        if ((state.phase == GamePhase.PLAYING || state.phase == GamePhase.PAUSED)) {
            HudOverlay(
                state = state,
                onPauseClick = { viewModel.handleIntent(GameIntent.PauseGame) },
                onJudgeToggle = { viewModel.handleIntent(GameIntent.ToggleJudgeMode) },
                onDashClick = { viewModel.handleIntent(GameIntent.TriggerPhaseDash) },
            )
        }

        // 3. Start countdown
        if (state.phase == GamePhase.COUNTDOWN && !state.settings.firstRunTipsSeen) {
            // The very first run holds here: one card on what to do, then the countdown.
            FirstRunTips(onStart = {
                viewModel.handleIntent(GameIntent.UpdateSettings { it.copy(firstRunTipsSeen = true) })
            })
        } else if (state.phase == GamePhase.COUNTDOWN) {
            CountdownStamp(state.levelId, "Get ready", state.countdownNumber)
        }

        // 3b. Resume countdown: 3, 2, 1, Go! before a paused run continues.
        resumeCount?.let { CountdownStamp(state.levelId, "Resuming", it) }

        // 4. Pause Menu Modal
        if (state.phase == GamePhase.PAUSED && !state.showSettingsOverlay && resumeCount == null) {
            PauseOverlay(
                onResume = startResume,
                onOpenSettings = { viewModel.handleIntent(GameIntent.OpenSettings) },
                onRestart = { viewModel.handleIntent(GameIntent.RestartLevel) },
                onQuit = { viewModel.handleIntent(GameIntent.ReturnToMenu) },
            )
        }

        // 5. Settings Overlay (In-Game)
        if (state.showSettingsOverlay) {
            SettingsOverlay(
                settings = state.settings,
                onUpdateSettings = { transform -> viewModel.handleIntent(GameIntent.UpdateSettings(transform)) },
                onResetProgress = { viewModel.handleIntent(GameIntent.ResetProgress) },
                onClose = { viewModel.handleIntent(GameIntent.CloseSettings) },
            )
        }

        // 6. Console-Grade Virtual Thumbstick (Placed in Left or Right Letterbox Margin - Outside 800x600 Arena)
        if (state.phase == GamePhase.PLAYING && state.settings.steeringMode == SteeringMode.VIRTUAL_STICK) {
            // The judge panel covers the right side: while it is open the stick moves to the
            // left, so judges can still steer and the panel's buttons stay reachable.
            val isRightSide = state.settings.joystickSide == JoystickSide.RIGHT && !state.judgeModeActive
            Box(
                modifier = Modifier
                    .align(if (isRightSide) Alignment.BottomEnd else Alignment.BottomStart)
                    .padding(
                        start = if (isRightSide) 0.dp else 24.dp,
                        end = if (isRightSide) 24.dp else 0.dp,
                        bottom = 24.dp,
                    ),
            ) {
                VirtualJoystick(
                    onVectorChange = { vec ->
                        viewModel.handleIntent(GameIntent.JoystickVector(vec))
                    },
                    size = 145.dp,
                )
            }
        }

        // 7. Dev Testing Panel (right sidebar), drawn after the stick so it is always on top
        AnimatedVisibility(
            visible = state.judgeModeActive,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            DevTestingPanel(
                state = state,
                viewModel = viewModel,
                onClose = { viewModel.handleIntent(GameIntent.ToggleJudgeMode) },
            )
        }

        // 8. Desktop Hotkey HUD Overlay (Non-intrusive bottom margin badge)
        if (com.snakechase.app.platform.isKeyboardPrimary &&
            state.phase == GamePhase.PLAYING &&
            state.settings.steeringMode != SteeringMode.VIRTUAL_STICK
        ) {
            com.snakechase.app.ui.theme.ArcadeChip(
                "WASD move · Space dash · Esc pause",
                fill = com.snakechase.app.ui.theme.Cream,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 14.dp),
            )
        }
    }
}

/** "3, 2, 1, Go!" in big sticker numbers that stamp in, in the level's own colour. */
@Composable
internal fun CountdownStamp(levelId: Int, label: String, number: Int) {
    Box(
        modifier = Modifier.fillMaxSize().background(com.snakechase.app.ui.theme.Ink.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val look = com.snakechase.app.ui.components.levelLook(levelId)
            com.snakechase.app.ui.theme.ArcadeTag(label, look.accent)
            Spacer(modifier = Modifier.height(10.dp))
            // Each number slams in from big to normal, like a stamp.
            val stamp = remember(number) { androidx.compose.animation.core.Animatable(1.8f) }
            LaunchedEffect(number) {
                stamp.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f))
            }
            com.snakechase.app.ui.theme.OutlinedText(
                text = if (number in 1..3) "$number" else "Go!",
                style = com.snakechase.app.ui.theme.displayStyle(120.sp),
                fill = if (number in 1..3) look.accent else com.snakechase.app.ui.theme.Banana,
                outlineWidth = 12.dp,
                halo = com.snakechase.app.ui.theme.Cream,
                haloWidth = 22.dp,
                shadow = 8.dp,
                modifier = Modifier.graphicsLayer {
                    scaleX = stamp.value
                    scaleY = stamp.value
                },
            )
        }
    }
}
