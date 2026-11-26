package com.snakechase.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.presentation.GameIntent
import com.snakechase.app.presentation.GameViewModel
import com.snakechase.app.ui.theme.ArcadeChip
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconButton
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadePanel
import com.snakechase.app.ui.theme.ArcadeSectionTitle
import com.snakechase.app.ui.theme.ArcadeToggle
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Jungle
import com.snakechase.app.ui.theme.JungleDeep
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle
import com.snakechase.app.util.toFixed

/**
 * Judge tools: jump to any stage, toggle god mode, force wins or deaths, spawn hazards.
 * Lets a judge see all 36 stages in minutes instead of hours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevTestingPanel(
    state: GameState,
    viewModel: GameViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetLvl = LevelRegistry.getById(state.devTargetLevelId)
    val playingTarget = state.levelId == targetLvl.id && state.phase == GamePhase.PLAYING
    val currentStageIdx = if (playingTarget) state.hud.currentSubStage else 0
    val activeStage = targetLvl.subStages.getOrNull(currentStageIdx)
    fun send(intent: GameIntent) = viewModel.handleIntent(intent)

    ArcadePanel(modifier = modifier.width(290.dp).fillMaxHeight(0.96f), fill = Jungle, shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Judge tools", style = displayStyle(18.sp, Banana))
                    Text("Level ${targetLvl.id} · ${targetLvl.name}", color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                ArcadeIconButton(
                    if (state.devPanelCollapsed) ArcadeIcon.Play else ArcadeIcon.Back,
                    onClick = { send(GameIntent.ToggleDevPanelCollapsed) },
                    size = 32.dp,
                )
                ArcadeIconButton(ArcadeIcon.Close, onClick = onClose, fill = Berry, size = 32.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (lvl in 1..5) {
                    val on = lvl == state.devTargetLevelId
                    ArcadeChip(
                        "$lvl",
                        fill = if (on) levelLook(lvl).accent else JungleDeep,
                        textColor = if (on) com.snakechase.app.ui.theme.Ink else Cream,
                        onClick = { send(GameIntent.SetDevTargetLevel(lvl)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!state.devPanelCollapsed) {
                ArcadeSectionTitle("Stages")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ArcadeChip("Prev", fill = Cream, icon = ArcadeIcon.Back, onClick = { send(GameIntent.StepSubStage(-1)) }, modifier = Modifier.weight(1f))
                    ArcadeChip("Replay", fill = Lime, onClick = { send(GameIntent.ReplayCurrentSubStage) }, modifier = Modifier.weight(1f))
                    ArcadeChip("Next", fill = Cream, icon = ArcadeIcon.Play, onClick = { send(GameIntent.StepSubStage(1)) }, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    targetLvl.subStages.forEachIndexed { index, _ ->
                        val num = index + 1
                        val active = playingTarget && state.hud.currentSubStage == index
                        val tested = state.devTestedStages.contains(num)
                        ArcadeChip(
                            if (tested) "$num ✓" else "$num",
                            fill = when {
                                active -> Banana
                                tested -> Lime
                                else -> JungleDeep
                            },
                            textColor = if (active || tested) com.snakechase.app.ui.theme.Ink else Cream,
                            onClick = { send(GameIntent.JumpToSubStage(subStageIndex = index, targetLevelId = targetLvl.id, resetScore = state.devResetScoreOnJump)) },
                        )
                    }
                }
                if (activeStage != null) {
                    ArcadeInset(Modifier.fillMaxWidth(), highlight = Sky) {
                        Column {
                            Text("Stage ${currentStageIdx + 1}: ${activeStage.name}", style = displayStyle(14.sp, Cream))
                            Text("+${com.snakechase.app.levels.ScoreBudget.stageBonus(targetLvl, currentStageIdx)} pts · from ${activeStage.startAtMs / 1000} s", color = Banana, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(activeStage.subtitle, color = Moss, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                ArcadeSectionTitle("Options")
                DevToggle("Reset score on jump", state.devResetScoreOnJump) { send(GameIntent.ToggleDevResetScore) }
                DevToggle("Solo stage (isolate hazards)", state.devSoloStageMode) { send(GameIntent.ToggleDevSoloStageMode) }
                DevToggle("God mode", state.godModeEnabled) { send(GameIntent.ToggleGodMode) }

                ArcadeSectionTitle("Actions")
                val paused = state.phase == GamePhase.PAUSED
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 2) {
                    DevAction(if (paused) "Resume" else "Pause", Cream) { send(if (paused) GameIntent.ResumeGame else GameIntent.PauseGame) }
                    DevAction("Win level", Banana) { send(GameIntent.ForceWinLevel) }
                    DevAction("Die now", Berry) { send(GameIntent.ForceDieNow) }
                    DevAction("Restart", Cream) { send(GameIntent.RestartLevel) }
                    DevAction("Menu", Cream) { send(GameIntent.ReturnToMenu) }
                }

                ArcadeSectionTitle("Spawn")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 2) {
                    DevAction("Split snake", Tan) { send(GameIntent.TriggerSpawn("split")) }
                    DevAction("Walls", Tan) { send(GameIntent.TriggerSpawn("walls")) }
                    DevAction("Rotating bar", Tan) { send(GameIntent.TriggerSpawn("bar")) }
                    DevAction("Mover + zone", Tan) { send(GameIntent.TriggerSpawn("mover")) }
                    DevAction("Teleporters", Tan) { send(GameIntent.TriggerSpawn("teleporter")) }
                }

                ArcadeSectionTitle("Live")
                val mult = state.score.combo.multiplier.toFixed(1)
                val seconds = (state.hud.survivalTimeMs / 1000f).toFixed(1)
                ArcadeInset(Modifier.fillMaxWidth()) {
                    Column {
                        LiveLine("Score ${state.score.total} · combo ${state.score.combo.count} (×$mult)")
                        LiveLine("Survival ${state.score.breakdown.survivalPoints} · stages ${state.score.breakdown.stageBonusPoints} · near ${state.score.breakdown.nearMissPoints}")
                        LiveLine("${seconds}s · stage ${state.hud.currentSubStage + 1} (${state.hud.subStageName})")
                        LiveLine("Snakes ${state.allSnakes.size} · ${state.primarySnake?.mode?.name ?: "none"} · split ${if (state.splitSnake != null) "yes" else "no"}")
                    }
                }
            }
        }
    }
}

private val Tan = Color(0xFFFFE0B2)

@Composable
private fun DevToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    ArcadeInset(Modifier.fillMaxWidth(), onClick = onToggle) {
        Text(label, color = Cream, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        ArcadeToggle(checked, { onToggle() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.FlowRowScope.DevAction(text: String, fill: Color, onClick: () -> Unit) {
    ArcadeChip(text, fill = fill, onClick = onClick, modifier = Modifier.weight(1f))
}

@Composable
private fun LiveLine(text: String) {
    Text(text, color = Cream.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
}
