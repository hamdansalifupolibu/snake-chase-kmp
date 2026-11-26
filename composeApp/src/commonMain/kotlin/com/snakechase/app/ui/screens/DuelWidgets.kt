package com.snakechase.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeChip
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle

// ─── Networked-duel helpers ───────────────────────────────────────────────────

/** "Spectating · 3 watching" for viewers; "3 watching" for the players of a watched duel. */
@Composable
internal fun WatchersBadge(spectating: Boolean, watchers: Int, modifier: Modifier = Modifier) {
    val text = when {
        spectating && watchers > 1 -> "Spectating · $watchers watching"
        spectating -> "Spectating"
        else -> "$watchers watching"
    }
    ArcadeChip(text, fill = Sky, icon = ArcadeIcon.Eye, modifier = modifier)
}

/** Shown on both sides when the network opponent leaves, closes the room, or times out. */
@Composable
internal fun OpponentLeftOverlay(reason: String, midMatch: Boolean, onContinue: () -> Unit, isSpectator: Boolean = false) {
    ArcadeDialog(
        title = when {
            isSpectator -> "The duel has ended"
            midMatch -> "Opponent left"
            else -> "Connection closed"
        },
        icon = ArcadeIcon.Wifi,
        onClose = null,
        dismissOnScrim = false,
        width = 400.dp,
        accent = Berry,
    ) {
        Text(reason, color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (midMatch && !isSpectator) {
            ArcadeInset(Modifier.fillMaxWidth(), highlight = Banana) {
                ArcadeIconView(ArcadeIcon.Trophy, tint = Banana, size = 22.dp)
                Text("You win by forfeit", style = displayStyle(17.sp, Banana))
            }
        }
        ArcadeButton("Back to duel menu", icon = ArcadeIcon.Back, fill = Banana, onClick = onContinue, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Live round-trip time from the heartbeat, colour-coded for how playable it is. */
@Composable
internal fun LatencyBadge(latencyMs: Int, modifier: Modifier = Modifier) {
    val color = when {
        latencyMs < 60 -> Lime
        latencyMs < 150 -> Banana
        else -> Berry
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Cream)
            .border(2.dp, Ink, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color).border(1.5.dp, Ink, CircleShape))
        Text("$latencyMs ms", style = displayStyle(12.sp))
    }
}

/** What the arena draws for an online rival's ghost this frame. */
data class GhostOverlay(
    /** Arena position, or null once their run has ended. */
    val position: Vec2?,
    val isSnake: Boolean,
    val label: String,
)
