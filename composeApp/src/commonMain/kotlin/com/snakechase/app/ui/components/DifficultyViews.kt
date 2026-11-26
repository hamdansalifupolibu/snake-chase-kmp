package com.snakechase.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.Difficulty
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeChip
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime

fun difficultyColor(d: Difficulty): Color = when (d) {
    Difficulty.EASY -> Lime
    Difficulty.MODERATE -> Banana
    Difficulty.HARD -> Berry
}

/** The small tag shown next to a score: which difficulty it was earned on. */
@Composable
fun DifficultyBadge(d: Difficulty, modifier: Modifier = Modifier) {
    ArcadeChip(d.label, modifier = modifier, fill = difficultyColor(d), textColor = if (d == Difficulty.HARD) Cream else Ink)
}

/** Three tappable chips; the current one is filled with its colour. */
@Composable
fun DifficultySwitch(selected: Difficulty, onPick: (Difficulty) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (d in Difficulty.entries) {
            val on = d == selected
            ArcadeChip(
                text = d.label,
                fill = if (on) difficultyColor(d) else Cream.copy(alpha = 0.55f),
                textColor = if (on && d == Difficulty.HARD) Cream else Ink,
                icon = if (on) ArcadeIcon.Check else null,
                onClick = { onPick(d) },
            )
        }
    }
}

/** Asked the first time the player presses Play. Moderate is the recommended pick. */
@Composable
fun DifficultyPrompt(onPick: (Difficulty) -> Unit, onClose: () -> Unit) {
    ArcadeDialog(title = "Pick your challenge", onClose = onClose, icon = ArcadeIcon.Play, width = 460.dp) {
        for (d in listOf(Difficulty.MODERATE, Difficulty.EASY, Difficulty.HARD)) {
            ArcadeButton(
                text = if (d == Difficulty.RECOMMENDED) "${d.label} (recommended)" else d.label,
                subtitle = d.blurb,
                fill = difficultyColor(d),
                contentColor = if (d == Difficulty.HARD) Cream else Ink,
                onClick = { onPick(d) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        Text(
            "Scoring is the same on every difficulty. The online leaderboard counts Moderate and Hard runs. " +
                "Change it any time under the Play button or in Settings.",
            color = Cream.copy(alpha = 0.85f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
