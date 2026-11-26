package com.snakechase.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.PowerUpType
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconButton
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadePanel
import com.snakechase.app.ui.theme.ArcadePressable
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.CreamShade
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.Tangerine
import com.snakechase.app.ui.theme.displayStyle
import com.snakechase.app.util.toFixed

/** In-run heads-up display: cream sticker chips that stay out of the arena's middle. */
@Composable
fun HudOverlay(
    state: GameState,
    onPauseClick: () -> Unit,
    onJudgeToggle: () -> Unit,
    onDashClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val level = LevelRegistry.getById(state.levelId)
    val accent = levelLook(state.levelId).accent
    Box(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // Left: level, stage name, time survived and stage pips.
            HudChip {
                Row(Modifier.width(190.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(accent).border(2.dp, Ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${state.levelId}", style = displayStyle(12.sp)) }
                    Text(
                        "  ${state.hud.subStageName}",
                        style = displayStyle(14.sp),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(state.hud.survivalTimeMs / 1000f).toFixed(1)}s", color = Ink.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Text(state.runDifficulty.label, color = Ink.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
                    for (i in 0 until state.hud.totalSubStages) {
                        val active = i == state.hud.currentSubStage
                        val passed = i < state.hud.currentSubStage
                        Box(
                            Modifier
                                .height(7.dp)
                                .width(if (active) 18.dp else 12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        active -> accent
                                        passed -> Lime
                                        else -> CreamShade
                                    },
                                )
                                .border(1.5.dp, Ink, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }

            // Centre: score, plus streak / multiplier / power-up tags under it.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HudChip(padH = 18.dp) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${state.score.total}", style = displayStyle(26.sp))
                        Text(" / ${level.scoringConfig.levelScoreMax}", color = Ink.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                if (state.comboMultiplier > 1.0f) {
                    val greed = when {
                        state.comboMultiplier >= 3f -> Berry
                        state.comboMultiplier >= 2f -> Banana
                        else -> Lime
                    }
                    HudTag(ArcadeIcon.Bolt, "Greed ×${state.comboMultiplier.toFixed(1)}", greed)
                }
                if (state.isOnFire || state.nearMissStreakCount >= 2) {
                    HudTag(
                        ArcadeIcon.Flame,
                        if (state.isOnFire) "On fire! Streak ${state.nearMissStreakCount}" else "Streak ${state.nearMissStreakCount}/3",
                        if (state.isOnFire) Tangerine else Banana,
                        pulse = state.isOnFire,
                    )
                }
                state.activePowerUps.forEach { p ->
                    HudTag(
                        if (p.type == PowerUpType.SHIELD) ArcadeIcon.Shield else ArcadeIcon.Bolt,
                        "${p.type.displayName} ${(p.remainingMs / 1000f).toFixed(1)}s",
                        Color(p.type.colorArgb),
                    )
                }
                if (state.shieldState.isActive && state.activePowerUps.none { it.type == PowerUpType.SHIELD }) {
                    HudTag(ArcadeIcon.Shield, "Shield ${(state.shieldState.remainingMs / 1000f).toFixed(1)}s", Sky)
                }
            }

            // Right: combo, time left, pause and judge tools.
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudChip {
                    val combo = state.score.combo
                    Text("×${combo.multiplier.toFixed(1)}", style = displayStyle(17.sp, if (combo.multiplier > 1f) Tangerine else Ink.copy(alpha = 0.6f)))
                    Text(if (combo.multiplier > 1f) "Combo ${combo.count}" else "Graze to combo", color = Ink.copy(alpha = 0.65f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                val secRemaining = state.hud.timeRemainingMs / 1000L
                val clock = "${secRemaining / 60}:${(secRemaining % 60).toString().padStart(2, '0')}"
                HudChip(fill = if (secRemaining <= 10) Berry else Banana) {
                    Text(clock, style = displayStyle(18.sp))
                }
                ArcadeIconButton(ArcadeIcon.Pause, onClick = onPauseClick, size = 38.dp)
                ArcadeIconButton(ArcadeIcon.Scale, onClick = onJudgeToggle, size = 38.dp, fill = if (state.judgeModeActive) Lime else Cream)
            }
        }

        // Stage banner: a slim ribbon that drops in, holds ~2 s, then floats away. It keys on the
        // stage (not the toast object, which the engine re-copies every frame with a new timer).
        state.hud.stageToast?.let { toast ->
            val shown = remember(toast.title, toast.subtitle) { Animatable(0f) }
            LaunchedEffect(toast.title, toast.subtitle) {
                shown.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
                kotlinx.coroutines.delay(STAGE_BANNER_HOLD_MS)
                shown.animateTo(0f, androidx.compose.animation.core.tween(380))
            }
            // Never hide the action: when the orb is up near the banner, it turns see-through.
            val p = state.player.position
            val orbBehind = p.y < state.arenaSize.y * 0.4f && kotlin.math.abs(p.x - state.arenaSize.x / 2f) < state.arenaSize.x * 0.3f
            val see by androidx.compose.animation.core.animateFloatAsState(if (orbBehind) 0.28f else 1f, label = "see")
            if (shown.value > 0.01f) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 132.dp)
                        .graphicsLayer {
                            alpha = shown.value.coerceIn(0f, 1f) * see
                            translationY = (1f - shown.value) * -24f * density
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink.copy(alpha = 0.82f))
                        .border(2.5.dp, accent, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(accent).border(2.dp, Ink, CircleShape), contentAlignment = Alignment.Center) {
                        ArcadeIconView(ArcadeIcon.Check, size = 13.dp)
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(toast.title, style = displayStyle(16.sp, Cream))
                            if (toast.bonus > 0) Text("  +${toast.bonus}", style = displayStyle(15.sp, Banana))
                        }
                        Text(
                            toast.narrative?.let { "“$it”" } ?: toast.subtitle,
                            color = Cream.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontStyle = if (toast.narrative != null) FontStyle.Italic else FontStyle.Normal,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Bottom-right: dash.
        if (onDashClick != null) {
            val ready = state.player.dashCooldownMs <= 0L
            ArcadePressable(
                onClick = onDashClick,
                fill = if (ready) Sky else CreamShade,
                enabled = ready,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArcadeIconView(ArcadeIcon.Bolt, size = 18.dp)
                    Text(if (ready) "Dash" else "${(state.player.dashCooldownMs / 1000f).toFixed(1)}s", style = displayStyle(16.sp))
                }
            }
        }
    }
}

@Composable
private fun HudChip(fill: Color = Cream, padH: androidx.compose.ui.unit.Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    ArcadePanel(fill = fill, shape = RoundedCornerShape(14.dp), shadow = 3.dp) {
        Column(Modifier.padding(horizontal = padH, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun HudTag(icon: ArcadeIcon, text: String, fill: Color, pulse: Boolean = false) {
    val scale = if (pulse) remember { Animatable(1f) } else null
    if (scale != null) {
        LaunchedEffect(Unit) {
            while (true) {
                scale.animateTo(1.08f, spring(stiffness = 300f))
                scale.animateTo(1f, spring(stiffness = 300f))
            }
        }
    }
    Row(
        Modifier
            .graphicsLayer {
                val s = scale?.value ?: 1f
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .border(2.dp, Ink, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ArcadeIconView(icon, size = 13.dp)
        Text(text, style = displayStyle(12.sp))
    }
}

/** How long a stage banner stays fully visible before floating away. */
private const val STAGE_BANNER_HOLD_MS = 2_000L
