package com.snakechase.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.platform.canRequestFullscreen
import com.snakechase.app.platform.needsRotateHint
import com.snakechase.app.platform.requestLandscapeFullscreen
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadePanel
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Jungle
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.Tangerine
import com.snakechase.app.ui.theme.displayStyle

/**
 * On phone browsers held upright, covers the game with a "turn your phone" guide. It disappears
 * by itself the moment the phone is turned. The apps never show it: they're locked to landscape.
 */
@Composable
fun RotateDeviceGuard() {
    if (!needsRotateHint) return
    var dismissed by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (dismissed || maxWidth >= maxHeight) return@BoxWithConstraints
        Box(
            Modifier
                .fillMaxSize()
                // Swallow taps so nothing underneath reacts while the guide is up.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            JungleBackdrop()
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SnakeChaseLogo(compact = true)
                ArcadePanel(fill = Jungle, shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 360.dp)) {
                    Column(
                        Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TiltingPhone()
                        Text("Turn your phone sideways", style = displayStyle(24.sp, Banana), textAlign = TextAlign.Center)
                        Text(
                            "Snake Chase plays in landscape. Rotate your phone and the game appears.",
                            color = Cream,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        if (canRequestFullscreen) {
                            ArcadeButton("Play fullscreen", icon = ArcadeIcon.Play, fill = Banana, onClick = { requestLandscapeFullscreen() })
                        }
                        Text(
                            "Continue in portrait anyway",
                            color = Moss,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { dismissed = true }.padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A sticker phone that tips from upright to sideways, pauses, and repeats. */
@Composable
private fun TiltingPhone() {
    val transition = rememberInfiniteTransition(label = "rotate-hint")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                0f at 0
                0f at 500
                90f at 1300 using FastOutSlowInEasing
                90f at 2400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )
    Canvas(modifier = Modifier.size(120.dp)) {
        val w = size.width * 0.42f
        val h = size.height * 0.72f
        rotate(angle) {
            val topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f)
            val corner = CornerRadius(w * 0.2f)
            drawRoundRect(Ink, topLeft + Offset(4.dp.toPx(), 5.dp.toPx()), Size(w, h), corner)
            drawRoundRect(Cream, topLeft, Size(w, h), corner)
            drawRoundRect(Ink, topLeft, Size(w, h), corner, style = Stroke(4.dp.toPx()))
            val inset = 7.dp.toPx()
            drawRoundRect(Lime, topLeft + Offset(inset, inset * 1.6f), Size(w - inset * 2, h - inset * 3.2f), CornerRadius(4.dp.toPx()))
            // A tiny snake on the screen.
            val cy = size.height / 2f
            for (i in 0 until 5) {
                drawCircle(Ink, 4.6.dp.toPx(), Offset(size.width / 2f - 10.dp.toPx() + i * 5.dp.toPx(), cy + (if (i % 2 == 0) 2f else -2f) * density))
                drawCircle(if (i == 4) Tangerine else Banana, 3.2.dp.toPx(), Offset(size.width / 2f - 10.dp.toPx() + i * 5.dp.toPx(), cy + (if (i % 2 == 0) 2f else -2f) * density))
            }
        }
    }
}
