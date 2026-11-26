package com.snakechase.app.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.share.ShareCardData
import com.snakechase.app.share.ShareKind
import com.snakechase.app.share.ShareStat
import com.snakechase.app.ui.canvas.OrbPainter
import com.snakechase.app.ui.canvas.SkinPainter
import com.snakechase.app.ui.components.AvatarBadge
import com.snakechase.app.ui.components.levelArt
import com.snakechase.app.ui.components.levelLook
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.RatingStar
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle
import org.jetbrains.compose.resources.painterResource
import kotlin.math.atan2
import kotlin.math.sin

/** The card is always laid out at this size; the PNG is the same drawing scaled to 1080 x 1350. */
val CARD_W = 360.dp
val CARD_H = 450.dp

/**
 * The share card: the level's own art, a sticker headline, stars, the score and the player
 * with their equipped snake and orb. Fully static (no animation), so a capture is always crisp.
 */
@Composable
fun ShareCardView(card: ShareCardData, modifier: Modifier = Modifier) {
    val look = levelLook(card.levelId)
    val shape = RoundedCornerShape(28.dp)
    val headColor = when (card.kind) {
        ShareKind.LEVEL_CLEAR, ShareKind.DUEL_WON -> Banana
        ShareKind.RUN -> look.accent
        ShareKind.DUEL_LOST -> Sky
        ShareKind.PROFILE -> Banana
    }
    // The card must look identical whatever the device's text size setting is.
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
        Box(modifier.clip(shape).background(Ink).border(4.dp, Ink, shape)) {
            Image(painterResource(levelArt(card.levelId)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Ink.copy(alpha = 0.35f),
                        0.3f to look.shade.copy(alpha = 0.25f),
                        0.62f to look.shade.copy(alpha = 0.88f),
                        1f to Ink.copy(alpha = 0.97f),
                    ),
                ),
            )
            Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedText("Snake Chase", displayStyle(22.sp), fill = Lime, outlineWidth = 4.dp, shadow = 2.dp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        card.worldName,
                        style = displayStyle(12.sp, Ink),
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(look.accent).border(2.dp, Ink, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                // Cards with a stars row need the room more than the art does.
                Spacer(Modifier.height(if (card.stars > 0) 30.dp else 54.dp))
                card.badge?.let { badge ->
                    Text(
                        badge,
                        style = displayStyle(13.sp, Ink),
                        modifier = Modifier.rotate(-4f).clip(RoundedCornerShape(8.dp)).background(Berry).border(2.dp, Ink, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedText(card.headline, displayStyle(31.sp), fill = headColor, outlineWidth = 6.dp, shadow = 4.dp, halo = Cream, haloWidth = 11.dp)
                Spacer(Modifier.height(10.dp))
                if (card.stars > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        repeat(3) { i -> RatingStar(filled = i < card.stars, size = if (i == 1) 50.dp else 40.dp, empty = Color(0x55FFFFFF)) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedText(card.bigNumber, displayStyle(56.sp), fill = Cream, outlineWidth = 6.dp, shadow = 4.dp)
                Text(card.bigLabel, style = displayStyle(13.sp, Moss))
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.stats.forEach { StatTile(it, Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
                PlayerStrip(card)
                Spacer(Modifier.height(8.dp))
                Text("Can you beat me?  ·  Android · iOS · Desktop · Web", color = Moss, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun StatTile(stat: ShareStat, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(Cream).border(2.dp, Ink, shape).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stat.value, style = displayStyle(17.sp, Ink), maxLines = 1)
        Text(stat.label, color = Ink.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun PlayerStrip(card: ShareCardData) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Ink.copy(alpha = 0.85f)).border(2.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBadge(card.player.name, card.player.avatar, size = 34.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(card.player.name.ifBlank { "Player" }, style = displayStyle(16.sp, Cream), maxLines = 1)
            Text("${card.player.skin.displayName} + ${card.player.orb.displayName}", color = Moss, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SnakeAndOrb(card.player.skin, card.player.orb, Modifier.size(width = 120.dp, height = 36.dp))
    }
}

/** A frozen moment of the chase in the player's own skins: the snake lunging at their orb. */
@Composable
private fun SnakeAndOrb(skin: SnakeSkin, orb: OrbSkin, modifier: Modifier) {
    Canvas(modifier.clipToBounds()) {
        val h = size.height
        val r = h * 0.17f
        val orbC = Offset(size.width - h * 0.42f, h * 0.45f)
        val headX = orbC.x - h * 1.05f
        val points = List(11) { i -> Offset(headX - i * r * 1.05f, h * 0.55f + h * 0.16f * sin(1.3f - i * 0.6f)) }.filter { it.x > r }
        val radii = List(points.size) { i -> if (i == 0) r * 1.25f else r * (1f - i * 0.04f).coerceAtLeast(0.55f) }
        val angle = atan2(points[0].y - points[1].y, points[0].x - points[1].x)
        with(SkinPainter) { drawSkinnedSnake(points, radii, angle, skin, 1.3f) }
        val trail = List(10) { k -> Offset(orbC.x + k * r * 0.5f, orbC.y - k * r * 0.12f) }
        with(OrbPainter) {
            drawOrbTrail(trail, r * 1.3f, orb, 1.3f)
            drawOrbCore(orbC, r * 1.3f, orb, 1.3f)
        }
    }
}
