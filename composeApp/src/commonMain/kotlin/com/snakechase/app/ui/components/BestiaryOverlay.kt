package com.snakechase.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.BestiaryCard
import com.snakechase.app.ui.theme.ArcadeChip
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeSectionTitle
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.JungleDeep
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.displayStyle
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun BestiaryOverlay(
    cards: List<BestiaryCard>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val card = cards.getOrNull(selectedIndex) ?: cards.firstOrNull()

    ArcadeDialog(title = "Bestiary", icon = ArcadeIcon.Book, onClose = onClose, width = 640.dp, modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.forEachIndexed { index, c ->
                val on = index == selectedIndex
                ArcadeChip(
                    text = c.snakeName,
                    fill = if (on) Color(c.primaryColorArgb) else JungleDeep,
                    textColor = if (on) Ink else Cream,
                    onClick = { selectedIndex = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (card != null) {
            val primary = Color(card.primaryColorArgb)
            val accent = Color(card.accentColorArgb)
            // Portrait: the snake slithering through its own arena.
            val shape = RoundedCornerShape(18.dp)
            Box(Modifier.fillMaxWidth().height(130.dp).clip(shape).border(3.dp, Ink, shape)) {
                LevelBackdrop(card.levelId, Modifier.fillMaxSize())
                SnakePortrait(primary, accent, Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp))
                Column(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    OutlinedText(card.snakeName, displayStyle(26.sp), fill = primary, outlineWidth = 5.dp)
                    Text("${card.title} · ${card.species}", style = displayStyle(13.sp, Cream))
                }
                ArcadeTag(card.threatLevel, accent, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            Text(card.description, color = Cream.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            ArcadeInset(Modifier.fillMaxWidth(), highlight = Banana) {
                ArcadeIconView(ArcadeIcon.Target, tint = Banana, size = 22.dp)
                Column {
                    Text("How to survive it", style = displayStyle(14.sp, Banana))
                    Text(card.tacticalAdvice, color = Cream, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            ArcadeSectionTitle("Badges ${card.totalBadgesEarned} / 3")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeTile("Survivor", "3+ near-misses", card.badgeSurvivor, primary, Modifier.weight(1f))
                BadgeTile("Tamer", "Evade 30 s", card.badgeTamer, Banana, Modifier.weight(1f))
                BadgeTile("Conqueror", "Clear the level", card.badgeConqueror, accent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeTile(title: String, subtitle: String, earned: Boolean, color: Color, modifier: Modifier) {
    ArcadeInset(modifier, highlight = if (earned) color else null) {
        ArcadeIconView(if (earned) ArcadeIcon.Star else ArcadeIcon.Lock, tint = if (earned) color else Moss, size = 22.dp)
        Column {
            Text(title, style = displayStyle(14.sp, if (earned) Cream else Moss))
            Text(subtitle, color = if (earned) Cream.copy(alpha = 0.8f) else Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The species drawn as a chunky sticker snake, slithering in place. */
@Composable
fun SnakePortrait(body: Color, stripe: Color, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "portrait").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "t",
    )
    Canvas(modifier) {
        val n = 16
        val r = size.height * 0.16f
        val outline = r * 0.3f
        val phase = t * 2f * PI.toFloat()
        val points = List(n) { i ->
            val x = size.width * (0.3f + 0.65f * (1f - i / n.toFloat()))
            Offset(x, size.height * 0.55f + size.height * 0.25f * sin(phase - i * 0.5f))
        }
        for (i in n - 1 downTo 0) drawCircle(Ink, r * (0.55f + 0.45f * (1f - i / n.toFloat())) + outline, points[i])
        for (i in n - 1 downTo 1) drawCircle(if (i % 3 == 0) stripe else body, r * (0.55f + 0.45f * (1f - i / n.toFloat())), points[i])
        val head = points[0]
        drawCircle(Ink, r * 1.4f + outline, head)
        drawCircle(body, r * 1.4f, head)
        drawCircle(Cream, r * 0.45f, head + Offset(r * 0.4f, -r * 0.4f))
        drawCircle(Ink, r * 0.25f, head + Offset(r * 0.55f, -r * 0.4f))
    }
}
