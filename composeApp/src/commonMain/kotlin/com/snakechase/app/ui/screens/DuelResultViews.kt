package com.snakechase.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.AiDifficulty
import com.snakechase.app.domain.model.DuelMatchResult
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.MultiplayerState
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeCard
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle
import com.snakechase.app.util.toFixed

// ─── 5. Match Over & Victory Screen ──────────────────────────────────────────

@Composable
internal fun DuelMatchOverView(
    result: DuelMatchResult,
    state: MultiplayerState,
    isCompact: Boolean,
    onShare: () -> Unit,
    onPlayAgain: () -> Unit,
    onReturnToMenu: () -> Unit,
) {
    val won = result.isLocalWinner
    ArcadeCard(
        title = when {
            state.isSpectator -> "Duel finished"
            won -> "Victory!"
            else -> "Good fight"
        },
        icon = if (won) ArcadeIcon.Trophy else ArcadeIcon.Swords,
        accent = if (won) Banana else Sky,
        width = 520.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            ArcadeIconView(ArcadeIcon.Crown, tint = Banana, size = 30.dp)
            OutlinedText("  ${result.winnerName} wins", displayStyle(30.sp), fill = if (won) Banana else Cream, outlineWidth = 6.dp, shadow = 3.dp)
        }
        ArcadeTag(result.mvpTitle, Lime, Modifier.align(Alignment.CenterHorizontally))

        val isVsAi = state.mode == DuelMode.VS_AI_RIVAL
        ArcadeInset(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RoundLine(if (isVsAi) "Round 1 · you hunt" else "Round 1 · ${result.player1Name} is prey", result.round1TimeMs)
                RoundLine(if (isVsAi) "Round 2 · you flee" else "Round 2 · ${result.player2Name} is prey", result.round2TimeMs)
            }
        }

        // Spectators don't vote or share; if the players rematch, the spectator follows them in.
        if (!state.isSpectator) {
            ArcadeButton(
                "Share result",
                icon = ArcadeIcon.Share,
                fill = Lime,
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.isSpectator) {
                ArcadeButton("Rematch", icon = ArcadeIcon.Refresh, fill = Banana, onClick = onPlayAgain, modifier = Modifier.weight(1.2f))
            }
            ArcadeButton("Menu", icon = ArcadeIcon.Home, fill = Cream, onClick = onReturnToMenu, modifier = Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun RoundLine(label: String, timeMs: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Text("${(timeMs / 1000f).format(1)}s", style = displayStyle(17.sp, Banana))
    }
}

internal fun Float.format(digits: Int): String = toFixed(digits)

/** Pick how clever the AI rival is. */
@Composable
internal fun AiDifficultySelectorModal(
    onSelectDifficulty: (AiDifficulty) -> Unit,
    onDismiss: () -> Unit,
) {
    ArcadeDialog(title = "Choose your rival", icon = ArcadeIcon.Robot, onClose = onDismiss, width = 460.dp, accent = Lime) {
        Text(
            "Harder rivals flee faster and read your moves further ahead.",
            color = Cream,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        AiDifficulty.entries.forEachIndexed { index, diff ->
            val accent = Color(diff.accentColorHex)
            ArcadeInset(Modifier.fillMaxWidth(), highlight = accent, onClick = { onSelectDifficulty(diff) }) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(accent).border(3.dp, Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = displayStyle(20.sp))
                }
                Column(Modifier.weight(1f)) {
                    Text(diff.title, style = displayStyle(18.sp, Cream))
                    Text(diff.subtitle, color = Cream.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                ArcadeTag("${(diff.speedMultiplier * 100).toInt()}% speed", accent)
            }
        }
    }
}
