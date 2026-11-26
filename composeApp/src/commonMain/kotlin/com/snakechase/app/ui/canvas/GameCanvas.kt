package com.snakechase.app.ui.canvas

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.ui.components.LevelBackdrop
import com.snakechase.app.ui.components.levelLook
import com.snakechase.app.ui.theme.Ink

@Composable
fun GameCanvas(
    state: GameState,
    onPlayerDragDelta: ((Vec2) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Mouse/trackpad: called with the arena point under the cursor whenever it moves. */
    onPointerAim: ((Vec2) -> Unit)? = null,
) {
    val level = LevelRegistry.getById(state.levelId)

    val tapModifier = if (onDoubleTap != null) {
        Modifier.pointerInput(onDoubleTap) {
            detectTapGestures(
                onDoubleTap = { onDoubleTap() },
            )
        }
    } else {
        Modifier
    }

    val dragModifier = if (onPlayerDragDelta != null) {
        Modifier.pointerInput(onPlayerDragDelta) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                // A mouse already steers by pointing (see aimModifier); dragging it must not add on top.
                if (onPointerAim != null && change.type == PointerType.Mouse) return@detectDragGestures
                val scale = minOf(size.width.toFloat() / state.arenaSize.x, size.height.toFloat() / state.arenaSize.y)
                if (scale > 0.001f) {
                    val deltaX = dragAmount.x / scale
                    val deltaY = dragAmount.y / scale
                    onPlayerDragDelta(Vec2(deltaX, deltaY))
                }
            }
        }
    } else {
        Modifier
    }

    // Mouse and trackpad steer by pointing: the orb follows the cursor, no click or drag needed.
    // Touch keeps drag-to-steer, so a tap somewhere else can never move the orb there.
    val aimModifier = if (onPointerAim != null) {
        Modifier.pointerInput(onPointerAim) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.type != PointerType.Mouse) continue
                    val scale = minOf(size.width.toFloat() / state.arenaSize.x, size.height.toFloat() / state.arenaSize.y)
                    if (scale <= 0.001f) continue
                    val offsetX = (size.width - state.arenaSize.x * scale) / 2f
                    val offsetY = (size.height - state.arenaSize.y * scale) / 2f
                    onPointerAim(Vec2((change.position.x - offsetX) / scale, (change.position.y - offsetY) / scale))
                }
            }
        }
    } else {
        Modifier
    }

    val look = levelLook(state.levelId)
    // Free-running clock for animated skins (rainbow, lava, sparkles), even in duels and pauses.
    val skinClock by androidx.compose.animation.core.rememberInfiniteTransition(label = "skin").animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(600_000, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "skinClock",
    )
    Box(modifier = modifier.fillMaxSize().background(look.shade)) {
        // ─── Layer 0: the level's drawn scene (same art as its menu card) ──────
        LevelBackdrop(levelId = state.levelId, modifier = Modifier.fillMaxSize())

        // ─── Layer 2: Main Hardware Canvas ────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(tapModifier)
                .then(dragModifier)
                .then(aimModifier),
        ) {
            val scale = minOf(size.width / state.arenaSize.x, size.height / state.arenaSize.y)
            val baseOffsetX = (size.width - state.arenaSize.x * scale) / 2f
            val baseOffsetY = (size.height - state.arenaSize.y * scale) / 2f

            // Compute Screen Shake jitter when active
            val (shakeX, shakeY) = if (state.screenShakeRemainingMs > 0L && state.screenShakeIntensity > 0f) {
                val seed = (state.hud.survivalTimeMs % 100).toFloat()
                val angle = seed * 0.628f
                val mag = state.screenShakeIntensity
                Pair(kotlin.math.cos(angle) * mag, kotlin.math.sin(angle) * mag)
            } else {
                Pair(0f, 0f)
            }

            translate(left = baseOffsetX + shakeX, top = baseOffsetY + shakeY) {
                // Logical 800x600 Coordinate Rendering
                scale(scale, scale, pivot = Offset.Zero) {
                    // Arena floor: the scene shows through, darkened so the chase stays readable.
                    drawRoundRect(
                        color = look.shade.copy(alpha = 0.62f),
                        topLeft = Offset(0f, 0f),
                        size = Size(state.arenaSize.x, state.arenaSize.y),
                        cornerRadius = CornerRadius(20f, 20f),
                    )

                    // Subtle Arena Grid Lines
                    val gridColor = Color(level.palette.gridLineArgb).copy(alpha = 0.35f)
                    val step = 40f
                    var x = 0f
                    while (x <= state.arenaSize.x) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, state.arenaSize.y), strokeWidth = 1f)
                        x += step
                    }
                    var y = 0f
                    while (y <= state.arenaSize.y) {
                        drawLine(gridColor, Offset(0f, y), Offset(state.arenaSize.x, y), strokeWidth = 1f)
                        y += step
                    }

                    // Sticker frame: thick ink outline with the level's accent inside it.
                    drawRoundRect(
                        color = Ink,
                        topLeft = Offset(2f, 2f),
                        size = Size(state.arenaSize.x - 4f, state.arenaSize.y - 4f),
                        cornerRadius = CornerRadius(20f, 20f),
                        style = Stroke(width = 7f),
                    )
                    drawRoundRect(
                        color = look.accent.copy(alpha = 0.8f),
                        topLeft = Offset(6f, 6f),
                        size = Size(state.arenaSize.x - 12f, state.arenaSize.y - 12f),
                        cornerRadius = CornerRadius(16f, 16f),
                        style = Stroke(width = 3f),
                    )

                    // Draw Ambient World Particles (Phase 4)
                    if (state.ambientParticles.isNotEmpty()) {
                        ParticleRenderer.drawParticles(this, state.ambientParticles)
                    }

                    // Draw Obstacles
                    ObstacleRenderer.drawObstacles(this, state.obstacles, state.levelId)

                    // Draw Power-Up Orbs (Phase 5)
                    state.activePowerUpOrb?.let { orb ->
                        PlayerOrbRenderer.drawPowerUpOrb(this, orb)
                    } ?: state.shieldOrb?.let { orb ->
                        PlayerOrbRenderer.drawShieldOrb(this, orb)
                    }

                    // Draw Snakes: the main snake wears the equipped skin; split snakes keep the level's look.
                    state.primarySnake?.let {
                        SnakeRenderer.drawSnake(this, it, level.palette.primarySnakeTheme, state.activeSnakeSkin, skinClock)
                    }
                    state.splitSnake?.let {
                        SnakeRenderer.drawSnake(this, it, level.palette.splitSnakeTheme)
                    }
                    state.extraSnakes.forEach { extra ->
                        SnakeRenderer.drawSnake(this, extra, level.palette.splitSnakeTheme2 ?: level.palette.splitSnakeTheme)
                    }

                    // Particles & shockwaves go under the orb: in a long graze they pile up on the
                    // player's position, and you must always be able to see what you steer.
                    ParticleRenderer.drawParticles(this, state.particles)

                    // Draw Player Orb & Ribbon Trail (with active shield bubble if active)
                    PlayerOrbRenderer.drawPlayer(
                        this,
                        state.player,
                        level.palette.cursorTheme,
                        isShieldActive = state.shieldState.isActive,
                        shieldRemainingMs = state.shieldState.remainingMs,
                        comboMultiplier = state.comboMultiplier,
                        comboChargePct = state.comboChargePct,
                        orbSkin = state.activeOrbSkin,
                        timeSec = skinClock,
                    )

                    // ─── Stage Clear Flash & Transition Pulse ───
                    if (state.subStageClearAnimMs > 0L) {
                        val flashAlpha = (state.subStageClearAnimMs / 700f).coerceIn(0f, 1f)
                        drawRoundRect(
                            color = Color.White.copy(alpha = flashAlpha * 0.45f),
                            topLeft = Offset(0f, 0f),
                            size = Size(state.arenaSize.x, state.arenaSize.y),
                            cornerRadius = CornerRadius(20f, 20f),
                        )
                    }
                }
            }
        }
    }
}
