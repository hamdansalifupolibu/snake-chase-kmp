package com.snakechase.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.platform.isKeyboardPrimary
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadePanel
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Jungle
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.displayStyle

/** How to steer on this device, in one line. */
val steerHint: String get() = if (isKeyboardPrimary) "Point with your mouse (no clicking), or use WASD / arrow keys." else "Drag your finger anywhere, or switch to the thumbstick in Settings."

private data class TourCard(val icon: ArcadeIcon, val title: String, val body: String)

private val tour = listOf(
    TourCard(ArcadeIcon.Target, "You're the food", "In Snake Chase you are the glowing orb, and the snake is hunting you. Survive each world's clock to clear it."),
    TourCard(
        ArcadeIcon.Play,
        "Steer",
        "Mouse or trackpad: just point, no clicking.\nTouch: drag anywhere on the arena.\n" +
            "Keyboard: WASD or the arrow keys, Space to dash.\nPrefer a thumbstick? Switch to it in Settings." +
            if (isKeyboardPrimary) "\n(On this device, try the mouse.)" else "\n(On this device, try dragging.)",
    ),
    TourCard(ArcadeIcon.Bolt, "Live dangerously", "Graze past the snake: the closer the call, the bigger the score. Grab power-ups, dash out of trouble, and keep your streak alive."),
    TourCard(ArcadeIcon.Scale, "Judges, start here", "Tap the scales button (top right) for Judge tools: god mode, jump to any level or stage, spawn hazards. Then try 1v1 Duel: roles flip every round."),
)

/**
 * A short welcome tour (about 30 seconds, skippable) shown once after the player picks a name.
 * The last card points an arrow at the judge button in the menu's top-right corner.
 */
@Composable
fun BoxScope.WelcomeTour(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val card = tour[step]
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.72f)))
    if (step == tour.lastIndex) PointingArrow(Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 30.dp))
    ArcadePanel(fill = Jungle, modifier = Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 460.dp)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Banana), contentAlignment = Alignment.Center) {
                ArcadeIconView(card.icon, tint = Ink, size = 30.dp)
            }
            OutlinedText(card.title, displayStyle(28.sp), fill = Banana, outlineWidth = 5.dp, shadow = 3.dp)
            Text(card.body, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tour.indices.forEach { i ->
                    Box(Modifier.size(if (i == step) 10.dp else 7.dp).clip(CircleShape).background(if (i == step) Lime else Cream.copy(alpha = 0.3f)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeButton("Skip", fill = Cream, onClick = onDone, modifier = Modifier.weight(1f))
                ArcadeButton(
                    if (step == tour.lastIndex) "Let's play" else "Next",
                    fill = Lime,
                    onClick = { if (step == tour.lastIndex) onDone() else step++ },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A bouncing arrow pointing up-right, at the judge button. */
@Composable
private fun PointingArrow(modifier: Modifier) {
    val bob by rememberInfiniteTransition(label = "arrow").animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "bob")
    Canvas(modifier.size(70.dp).offset(x = (bob * 6).dp, y = (-bob * 6).dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.85f)
            lineTo(size.width * 0.8f, size.height * 0.2f)
        }
        drawPath(path, Ink, style = Stroke(width = 14f))
        drawPath(path, Banana, style = Stroke(width = 8f))
        val tip = Offset(size.width * 0.86f, size.height * 0.14f)
        val head = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - size.width * 0.34f, tip.y + size.height * 0.04f)
            lineTo(tip.x - size.width * 0.04f, tip.y + size.height * 0.34f)
            close()
        }
        drawPath(head, Ink, style = Stroke(width = 6f))
        drawPath(head, Banana)
    }
}

/**
 * Before the very first run, the level holds while the player reads one card: you are the food,
 * this is how you steer. The countdown starts when they tap Start.
 */
@Composable
fun BoxScope.FirstRunTips(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.55f)))
    ArcadePanel(fill = Jungle, modifier = Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 440.dp)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedText("You're the food: run!", displayStyle(26.sp), fill = Banana, outlineWidth = 5.dp, shadow = 3.dp)
            Text("Keep away from the snake's head until the clock runs out.", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(steerHint, color = Lime, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Close calls score big. Glowing orbs are power-ups.", color = Cream.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            ArcadeButton("Start", icon = ArcadeIcon.Play, fill = Lime, onClick = onStart, modifier = Modifier.fillMaxWidth())
        }
    }
}
