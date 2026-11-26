package com.snakechase.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.snakechase.app.presentation.DeathReplay
import com.snakechase.app.ui.canvas.GameCanvas
import kotlinx.coroutines.launch

/**
 * The catch, as an instant replay: the last moments before the snake got you play back in slow
 * motion while the camera pushes in on the bite, the edges darken, then a white flash, and the
 * game-over panel follows ([onDone]). Pure playback of recorded frames: it never changes the result.
 */
@Composable
fun DeathReplayView(replay: DeathReplay, onDone: () -> Unit) {
    val progress = remember(replay) { Animatable(0f) }
    val flash = remember(replay) { Animatable(0f) }
    LaunchedEffect(replay) {
        progress.animateTo(1f, tween(DeathReplay.PLAYBACK_MS.toInt(), easing = LinearEasing))
        launch { flash.animateTo(1f, tween(70)) }.join()
        flash.animateTo(0f, tween(340))
        onDone()
    }
    val frames = replay.frames
    val frame = frames[((frames.size - 1) * progress.value).toInt().coerceIn(0, frames.size - 1)]
    // Slow push-in that speeds up toward the bite.
    val zoom = 1f + 0.8f * FastOutSlowInEasing.transform(progress.value)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val arena = frame.arenaSize
        val scale = minOf(w / arena.x, h / arena.y)
        val pivotX = ((w - arena.x * scale) / 2f + replay.catchAt.x * scale) / w
        val pivotY = ((h - arena.y * scale) / 2f + replay.catchAt.y * scale) / h
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(pivotX.coerceIn(0f, 1f), pivotY.coerceIn(0f, 1f))
            },
        ) {
            GameCanvas(state = frame)
        }
        // Tunnel vision: the edges close in as the snake strikes.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0.35f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.75f * progress.value),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.85f * flash.value)))
    }
}
