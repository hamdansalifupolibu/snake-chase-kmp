package com.snakechase.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.presentation.GameIntent
import com.snakechase.app.presentation.GameViewModel
import com.snakechase.app.ui.components.DevTestingPanel
import com.snakechase.app.ui.components.DifficultyBadge
import com.snakechase.app.ui.share.shareIdentity
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconButton
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle

@Composable
fun GameOverScreen(
    state: GameState,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val isWin = state.phase == GamePhase.LEVEL_COMPLETE
    val level = LevelRegistry.getById(state.levelId)
    val maxScore = level.scoringConfig.levelScoreMax

    // The total counts up from zero: a small payoff for every run, even a short one.
    val shown = remember { Animatable(0f) }
    // Orb skins earned in the run are celebrated once the score has counted up.
    var orbsTurn by remember { mutableStateOf(false) }
    var shareCard by remember { mutableStateOf<com.snakechase.app.share.ShareCardData?>(null) }
    LaunchedEffect(state.score.total) {
        shown.animateTo(state.score.total.toFloat(), tween(900, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(300)
        orbsTurn = true
    }

    Box(modifier.fillMaxSize()) {
        com.snakechase.app.ui.components.LevelBackdrop(state.levelId, Modifier.fillMaxSize())
        ArcadeDialog(
            title = if (isWin) "Level cleared!" else "Caught!",
            icon = if (isWin) ArcadeIcon.Trophy else ArcadeIcon.Target,
            onClose = null,
            dismissOnScrim = false,
            width = 640.dp,
            accent = if (isWin) com.snakechase.app.ui.theme.LocalPanelAccent.current else Berry,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArcadeTag("Stage ${state.hud.currentSubStage + 1} of ${level.subStages.size} · ${state.hud.subStageName}", Sky)
                    ArcadeInset(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ScoreLine("Survival", "${state.hud.survivalTimeMs / 1000}s", state.score.breakdown.survivalPoints)
                            ScoreLine("Stage bonus", state.hud.subStageName, state.score.breakdown.stageBonusPoints)
                            ScoreLine("Near misses", "Risky grazes", state.score.breakdown.nearMissPoints)
                            if (isWin) ScoreLine("Victory", "Level mastered", state.score.breakdown.completionBonus)
                        }
                    }
                    val tip = when {
                        state.subStageDeaths >= 2 -> "Persistence is power: you're reading the attack patterns better."
                        state.hud.currentSubStage >= level.subStages.size - 2 -> "So close! You were right at the finish line."
                        else -> "Tip: sharp turns throw off the snake's prediction."
                    }
                    ArcadeInset(Modifier.fillMaxWidth(), highlight = if (state.lastRoastMeme != null) Berry else Banana) {
                        Column {
                            state.lastRoastMeme?.let { Text("Roast: $it", style = displayStyle(14.sp, Berry)) }
                            Text(tip, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Level ${state.levelId} score", style = displayStyle(14.sp, Moss))
                    OutlinedText("${shown.value.toInt()}", displayStyle(48.sp), fill = if (isWin) Lime else Cream, outlineWidth = 7.dp, shadow = 4.dp)
                    Text("of $maxScore", color = Moss, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    DifficultyBadge(state.runDifficulty)
                    if (state.lastRunCoins > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArcadeIconView(ArcadeIcon.Coin, size = 22.dp)
                            Text("  +${state.lastRunCoins}", style = displayStyle(20.sp, Banana))
                        }
                    }
                    // You win a level by surviving to the end of its clock, so say how close that was.
                    val leftSec = ((level.completionTimeMs - state.hud.survivalTimeMs) / 1000).coerceAtLeast(0)
                    if (!isWin && leftSec > 0) {
                        Text(if (leftSec <= 15) "Only ${leftSec}s from the finish!" else "${leftSec}s to go to clear the level", color = Banana, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                    val wide = Modifier.fillMaxWidth()
                    // Hearts: revive and carry on from just before the catch (Levels 1-5).
                    if (!isWin && state.reviveOffered) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(com.snakechase.app.profile.Lives.MAX) { i ->
                                com.snakechase.app.ui.theme.ArcadeIconView(
                                    com.snakechase.app.ui.theme.ArcadeIcon.Heart,
                                    tint = if (i < state.reviveHearts) Berry else Cream.copy(alpha = 0.25f),
                                    size = 20.dp,
                                )
                            }
                        }
                        if (state.reviveHearts > 0) {
                            ArcadeButton(
                                "Revive",
                                subtitle = "Use 1 of ${state.reviveHearts} hearts",
                                icon = com.snakechase.app.ui.theme.ArcadeIcon.Heart,
                                fill = Berry,
                                contentColor = Cream,
                                onClick = { viewModel.handleIntent(GameIntent.Revive) },
                                modifier = wide,
                            )
                        } else {
                            // Out of hearts: buy a full set with coins earned in play, or wait for a free one.
                            val coins = org.koin.compose.koinInject<com.snakechase.app.profile.ProfileRepository>().profile.collectAsState().value.coins
                            val price = com.snakechase.app.profile.Lives.REFILL_PRICE
                            ArcadeButton(
                                if (coins >= price) "Buy 3 hearts" else "Need $price coins",
                                subtitle = "$price coins",
                                icon = com.snakechase.app.ui.theme.ArcadeIcon.Coin,
                                fill = Banana,
                                enabled = coins >= price,
                                onClick = { viewModel.handleIntent(GameIntent.BuyHearts) },
                                modifier = wide,
                            )
                            state.nextHeartInMs?.let { ms ->
                                val min = ms / 60_000
                                val sec = (ms / 1000) % 60
                                Text("Next free heart in $min:${sec.toString().padStart(2, '0')}", color = Cream.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    ArcadeButton(if (isWin) "Play again" else "I'm not done", icon = ArcadeIcon.Refresh, fill = Banana, onClick = { viewModel.handleIntent(GameIntent.RestartLevel) }, modifier = wide)
                    if (state.judgeModeActive) {
                        ArcadeButton("Retry stage ${state.hud.currentSubStage + 1}", fill = Lime, onClick = { viewModel.handleIntent(GameIntent.ReplayCurrentSubStage) }, modifier = wide)
                    }
                    Row(wide, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArcadeButton("Take a breath", icon = ArcadeIcon.Home, fill = Cream, onClick = { viewModel.handleIntent(GameIntent.ReturnToMenu) }, modifier = Modifier.weight(1f))
                        ArcadeIconButton(
                            ArcadeIcon.Share,
                            fill = Lime,
                            size = 50.dp,
                            onClick = {
                                shareCard = com.snakechase.app.share.ShareCards.run(
                                    levelId = state.levelId,
                                    levelName = level.name,
                                    score = state.score.total,
                                    maxScore = maxScore,
                                    survivalMs = state.hud.survivalTimeMs,
                                    stageReached = state.hud.currentSubStage + 1,
                                    totalStages = level.subStages.size,
                                    player = state.shareIdentity(),
                                )
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.judgeModeActive,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            DevTestingPanel(state = state, viewModel = viewModel, onClose = { viewModel.handleIntent(GameIntent.ToggleJudgeMode) })
        }

        if (orbsTurn) {
            state.newOrbSkins.firstOrNull()?.let { orb ->
                com.snakechase.app.ui.components.OrbUnlockCelebration(orb) { viewModel.handleIntent(GameIntent.OrbCelebrationSeen) }
            }
        }
        shareCard?.let { com.snakechase.app.ui.share.SharePage(it) { shareCard = null } }
    }
}

@Composable
private fun ScoreLine(label: String, detail: String, points: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = displayStyle(14.sp, Cream))
            Text(detail, color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text("+$points", style = displayStyle(16.sp, Banana))
    }
}
