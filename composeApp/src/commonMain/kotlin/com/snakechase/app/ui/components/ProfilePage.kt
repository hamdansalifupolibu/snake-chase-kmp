package com.snakechase.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.profile.Badge
import com.snakechase.app.profile.PlayerCard
import com.snakechase.app.profile.Rank
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeSectionTitle
import com.snakechase.app.ui.theme.ArcadeStat
import com.snakechase.app.ui.theme.ArcadeTag
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

/** Each rank has its own colour: the avatar ring, the title and the progress bar wear it. */
fun rankColor(rank: Rank): Color = when (rank) {
    Rank.HATCHLING -> Cream
    Rank.RUNNER -> Lime
    Rank.DODGER -> Sky
    Rank.GHOST -> Color(0xFFB98CFF)
    Rank.LEGEND -> Banana
}

private fun badgeIcon(badge: Badge): ArcadeIcon = when (badge) {
    Badge.FIRST_CLEAR -> ArcadeIcon.Check
    Badge.FLAWLESS -> ArcadeIcon.Star
    Badge.CHAMPION -> ArcadeIcon.Crown
    Badge.STAR_COLLECTOR -> ArcadeIcon.Trophy
    Badge.DUELIST -> ArcadeIcon.Swords
    Badge.UNSTOPPABLE -> ArcadeIcon.Flame
    Badge.HUNTER -> ArcadeIcon.Target
    Badge.COLLECTOR -> ArcadeIcon.Palette
    Badge.ORB_HOARDER -> ArcadeIcon.Bolt
    Badge.LOYAL -> ArcadeIcon.Calendar
}

/**
 * The player card: who you are (avatar, name, rank), what you wear (your snake chasing your
 * orb, live), what you've done (stats, stars per world) and what you've collected (badges).
 */
@Composable
fun ProfilePage(
    card: PlayerCard,
    skin: SnakeSkin,
    orb: OrbSkin,
    isOffline: Boolean,
    pendingUploads: Int,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val p = card.profile
    val rank = card.rank
    val color = rankColor(rank)
    ArcadeDialog(title = "Profile", icon = ArcadeIcon.User, onClose = onClose, width = 780.dp, accent = Lime) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ── Left: who you are ──────────────────────────────────────────
            Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RankedAvatar(p.name, p.avatar, color, card.rankProgress)
                OutlinedText(p.name.ifBlank { "Player" }, displayStyle(28.sp), fill = Cream, outlineWidth = 5.dp, shadow = 3.dp)
                ArcadeTag(rank.title, color)
                RankBar(card, color)
                Box(
                    Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B1A14)).border(3.dp, Ink, RoundedCornerShape(16.dp)),
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkinPreview(skin, Modifier.weight(1.6f).height(52.dp), segments = 10)
                        OrbPreview(orb, Modifier.weight(1f).height(60.dp), small = true)
                    }
                }
                Text("${skin.displayName} + ${orb.displayName}", color = Moss, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArcadeButton("Edit", icon = ArcadeIcon.User, fill = Cream, onClick = onEdit, modifier = Modifier.weight(1f))
                    ArcadeButton("Share", icon = ArcadeIcon.Share, fill = Lime, onClick = onShare, modifier = Modifier.weight(1f))
                }
            }
            // ── Right: what you've done ────────────────────────────────────
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArcadeStat("${p.coins}", "Coins", Modifier.weight(1f))
                    ArcadeStat("${p.runsPlayed}", "Runs", Modifier.weight(1f), valueColor = Cream)
                    ArcadeStat("${p.dailyStreak}", "Day streak", Modifier.weight(1f), valueColor = Lime)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArcadeStat("${p.duelsWon}-${p.duelsLost}", "Duels W-L", Modifier.weight(1f), valueColor = Cream)
                    ArcadeStat("${p.winRatePct}%", "Win rate", Modifier.weight(1f), valueColor = Lime)
                    ArcadeStat("${p.bestWinStreak}", "Best streak", Modifier.weight(1f))
                }
                ArcadeSectionTitle("Stars  ${card.totalLevelStars} / 15", icon = ArcadeIcon.Star, color = Banana)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in 1..PlayerCard.LEVELS) WorldChip(id, card.levelStars[id] ?: 0, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArcadeStat("${card.totalHuntStars}/15", "Hunt stars", Modifier.weight(1f), valueColor = Berry)
                    ArcadeStat("${card.skinsOwned}/${SnakeSkin.entries.size}", "Snakes", Modifier.weight(1f), valueColor = Cream)
                    ArcadeStat("${card.orbsOwned}/${OrbSkin.entries.size}", "Orbs", Modifier.weight(1f), valueColor = Sky)
                }
                Badges(card)
            }
        }
        Text(
            when {
                isOffline && pendingUploads > 0 -> "Offline: $pendingUploads result(s) will upload when you reconnect."
                isOffline -> "Offline: your profile syncs when you reconnect."
                pendingUploads > 0 -> "Uploading $pendingUploads result(s)…"
                else -> "Synced online"
            },
            color = if (isOffline) Berry else Moss,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The avatar inside a ring that fills up as you close in on the next rank. */
@Composable
private fun RankedAvatar(name: String, avatar: Int, color: Color, progress: Float) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(progress) { fill.animateTo(progress, tween(900, easing = FastOutSlowInEasing)) }
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 8.dp.toPx()
            val inset = w / 2f + 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(Ink.copy(alpha = 0.55f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * fill.value, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        AvatarBadge(name, avatar, size = 78.dp)
    }
}

@Composable
private fun RankBar(card: PlayerCard, color: Color) {
    val next = card.rank.next
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val bar = RoundedCornerShape(50)
        Box(Modifier.fillMaxWidth().height(12.dp).clip(bar).background(Ink.copy(alpha = 0.5f)).border(2.dp, Ink, bar)) {
            Box(Modifier.fillMaxWidth(card.rankProgress).height(12.dp).clip(bar).background(color))
        }
        Text(
            if (next == null) "Top rank reached: ${card.rankPoints} points" else "${card.rankPoints} / ${next.minPoints} points to ${next.title}",
            color = Moss,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** A world's thumbnail with its best stars; dimmed until it has been cleared. */
@Composable
private fun WorldChip(levelId: Int, stars: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(36.dp).clip(shape).border(2.5.dp, if (stars > 0) levelLook(levelId).accent else Ink, shape)) {
            Image(painterResource(levelArt(levelId)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (stars > 0) 1f else 0.35f)
        }
        Row(Modifier.padding(top = 2.dp)) { repeat(3) { RatingStar(filled = it < stars, size = 12.dp) } }
    }
}

/** Ten badges to collect; tap one to see what it's for (or how to earn it). */
@Composable
private fun Badges(card: PlayerCard) {
    val earned = card.earnedBadges
    var picked by remember { mutableStateOf<Badge?>(null) }
    ArcadeSectionTitle("Badges  ${earned.size} / ${Badge.entries.size}", icon = ArcadeIcon.Trophy, color = Banana)
    Badge.entries.chunked(5).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { badge ->
                val has = badge in earned
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (has) Banana else Ink.copy(alpha = 0.45f))
                        .border(if (badge == picked) 3.dp else 2.5.dp, if (badge == picked) Cream else Ink, CircleShape)
                        .clickable { picked = if (picked == badge) null else badge },
                    contentAlignment = Alignment.Center,
                ) {
                    ArcadeIconView(badgeIcon(badge), tint = if (has) Ink else Moss.copy(alpha = 0.7f), size = 22.dp)
                }
            }
        }
    }
    val shown = picked
    Text(
        when {
            shown == null -> "Tap a badge to see how to earn it."
            shown in earned -> "${shown.title}: earned! (${shown.hint.replaceFirstChar { it.lowercase() }})"
            else -> "${shown.title}: ${shown.hint.replaceFirstChar { it.lowercase() }} to earn it."
        },
        color = if (shown != null && shown in earned) Banana else Cream.copy(alpha = 0.85f),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.fillMaxWidth(),
    )
}
