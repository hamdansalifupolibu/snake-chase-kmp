package com.snakechase.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import snakechase.composeapp.generated.resources.Res
import snakechase.composeapp.generated.resources.bg_landing
import kotlin.math.PI
import kotlin.math.sin

private val Glow = Color(0xFF4FA8FF)
private val GlowCore = Color(0xFFE9F6FF)

/**
 * What you see the moment the app opens: the original Snake Chase painting, the logo, and a
 * loader of glowing beads that ripple like the painting's snake. Shown briefly, then it fades
 * into the menu (see App.kt).
 */
@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF0E1030))) {
        Image(
            painter = painterResource(Res.drawable.bg_landing),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Darken the bottom so the loader reads on any screen shape.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0xD9060818)),
            ),
        )
        Column(
            Modifier.fillMaxSize().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SnakeChaseLogo(compact = false, modifier = Modifier.padding(top = 12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BeadLoader()
                Text(
                    "Loading the jungle…",
                    color = GlowCore.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Seven glowing beads; a wave of light runs from tail to head, like the snake in the painting. */
@Composable
private fun BeadLoader() {
    val phase by rememberInfiniteTransition(label = "beads").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )
    Canvas(Modifier.size(width = 150.dp, height = 34.dp)) {
        val n = 7
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val x = size.width * (0.06f + 0.88f * t)
            val y = size.height / 2f + sin((t * 1.6f + phase) * 2f * PI.toFloat()) * size.height * 0.18f
            val pulse = 0.5f + 0.5f * sin((phase - t) * 2f * PI.toFloat())
            val r = (3.2f + 3.6f * t) * density / 2f
            drawCircle(Glow.copy(alpha = 0.18f + 0.3f * pulse), r * 2.6f, Offset(x, y))
            drawCircle(Glow, r, Offset(x, y))
            drawCircle(GlowCore.copy(alpha = 0.6f + 0.4f * pulse), r * 0.45f, Offset(x - r * 0.25f, y - r * 0.25f))
        }
    }
}
