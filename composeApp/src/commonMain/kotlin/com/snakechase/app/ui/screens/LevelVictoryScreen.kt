package com.snakechase.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.levels.Cheers
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.presentation.GameIntent
import com.snakechase.app.presentation.GameViewModel
import com.snakechase.app.ui.components.CelebrationLayer
import com.snakechase.app.ui.components.ChampionCelebration
import com.snakechase.app.ui.components.DifficultyBadge
import com.snakechase.app.ui.components.LevelBackdrop
import com.snakechase.app.ui.components.SnakeChaseLogo
import com.snakechase.app.ui.components.SunRays
import com.snakechase.app.ui.components.levelLook
import com.snakechase.app.ui.components.rememberCelebrationState
import com.snakechase.app.ui.share.shareIdentity
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.RatingStar
import com.snakechase.app.ui.theme.displayStyle

@Composable
fun LevelVictoryScreen(
    state: GameState,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val level = LevelRegistry.getById(state.levelId)
    val look = levelLook(state.levelId)
    val result = state.levelResult
    val stars = result?.stars ?: 1
    val finalScore = result?.finalScore ?: state.score.total
    val maxScore = result?.levelScoreMax ?: level.scoringConfig.levelScoreMax
    // Everything this clear unlocked: the level's skin and any three-star skins.
    val newSkins = listOfNotNull(result?.skinUnlocked) + result?.bonusSkins.orEmpty()
    var celebrating by remember { mutableStateOf<List<com.snakechase.app.domain.model.SnakeSkin>>(emptyList()) }
    // Orb skins earned in the run wait until the stars and snake skins have had their moment.
    var orbsTurn by remember { mutableStateOf(false) }
    var showChampion by remember { mutableStateOf(false) }
    var shareCard by remember { mutableStateOf<com.snakechase.app.share.ShareCardData?>(null) }
    val cheer = remember(result) {
        Cheers.forClear(state.levelId, stars, newBest = result?.isNewPersonalBest == true, firstClear = result?.firstClear == true, seed = finalScore)
    }

    // The level celebrates in its own colours.
    val palette = remember(state.levelId) { listOf(look.accent, look.glow, Banana, Lime, Cream, Berry) }
    val fx = rememberCelebrationState()
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    val starCenters = remember { Array(3) { Offset.Zero } }

    // Stars stamp in one after another, overshooting like a rubber stamp, each with a
    // rising chime, a buzz and a firework, so a three-star clear sounds and feels bigger.
    val audio = org.koin.compose.koinInject<com.snakechase.app.platform.AudioManager>()
    val haptics = org.koin.compose.koinInject<com.snakechase.app.platform.HapticsManager>()
    val starScales = remember { List(3) { Animatable(0f) } }
    val shownScore = remember { Animatable(0f) }
    val stampScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(450) // let the card pop in first
        for (i in 0 until stars.coerceIn(1, 3)) {
            starScales[i].snapTo(2.2f)
            // Each star has its own chime (rising C-E-G); the third adds a sparkle.
            audio.playSfx(listOf(com.snakechase.app.domain.model.SoundId.STAR_1, com.snakechase.app.domain.model.SoundId.STAR_2, com.snakechase.app.domain.model.SoundId.STAR_3)[i])
            haptics.trigger(if (i == 2) com.snakechase.app.domain.model.HapticStyle.HEAVY else com.snakechase.app.domain.model.HapticStyle.MEDIUM)
            fx.burst(starCenters[i] - layerOrigin, palette[i])
            starScales[i].animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
            kotlinx.coroutines.delay(120)
        }
        // The score rolls up to its total, then the stamp slams onto the card.
        shownScore.animateTo(finalScore.toFloat(), tween(900, easing = FastOutSlowInEasing))
        if (cheer.stamp != null) {
            stampScale.snapTo(2.6f)
            audio.playSfx(com.snakechase.app.domain.model.SoundId.LEVEL_COMPLETE, 1.15f)
            haptics.trigger(com.snakechase.app.domain.model.HapticStyle.HEAVY)
            stampScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
        }
        kotlinx.coroutines.delay(400)
        // Last world cleared: the grand finale comes before any new skins.
        if (result?.allLevelsCleared == true) {
            showChampion = true
        } else if (newSkins.isNotEmpty()) {
            celebrating = newSkins.sortedByDescending { it.rarity.ordinal }
        }
        orbsTurn = true
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The conquered level's own scene glows behind the card.
        LevelBackdrop(state.levelId, Modifier.fillMaxSize())
        ArcadeDialog(
            title = "Level ${state.levelId} conquered!",
            icon = ArcadeIcon.Trophy,
            onClose = null,
            dismissOnScrim = false,
            width = 560.dp,
        ) {
            OutlinedText(level.name, displayStyle(34.sp), fill = look.accent, outlineWidth = 6.dp, shadow = 4.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Box(Modifier.align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                SunRays(look.accent.copy(alpha = 0.10f), Modifier.size(width = 300.dp, height = 90.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until 3) {
                        val earned = i < stars
                        RatingStar(
                            filled = earned,
                            size = if (i == 1) 62.dp else 50.dp,
                            modifier = Modifier
                                .onGloballyPositioned { starCenters[i] = it.boundsInRoot().center }
                                .graphicsLayer {
                                    val s = if (earned) starScales[i].value else 1f
                                    scaleX = s
                                    scaleY = s
                                },
                        )
                    }
                }
            }
            Text("“${cheer.line}”", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Box(Modifier.fillMaxWidth()) {
                ArcadeInset(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${shownScore.value.toInt()}", style = displayStyle(30.sp, Banana))
                            Text(" / $maxScore  ", style = displayStyle(16.sp, Moss))
                            DifficultyBadge(state.runDifficulty)
                            Spacer(Modifier.weight(1f))
                            if (state.lastRunCoins > 0) {
                                ArcadeIconView(ArcadeIcon.Coin, size = 20.dp)
                                Text(" +${state.lastRunCoins}", style = displayStyle(18.sp, Banana))
                            }
                        }
                        val fraction = if (maxScore > 0) (shownScore.value / maxScore).coerceIn(0f, 1f) else 1f
                        val bar = RoundedCornerShape(50)
                        Box(Modifier.fillMaxWidth().height(14.dp).clip(bar).background(Ink.copy(alpha = 0.5f)).border(2.5.dp, Ink, bar)) {
                            Box(Modifier.fillMaxWidth(fraction).height(14.dp).clip(bar).background(Lime).border(2.5.dp, Ink, bar))
                        }
                        StatLine("Survived", "${state.hud.survivalTimeMs / 1000}s")
                        StatLine("Stages cleared", "${result?.stagesCleared ?: level.subStages.size} / ${level.subStages.size}")
                        StatLine("Near-miss points", "+${state.score.breakdown.nearMissPoints}")
                    }
                }
                cheer.stamp?.let { stamp ->
                    // A sticker slapped onto the corner of the score panel.
                    Text(
                        stamp,
                        style = displayStyle(17.sp, Ink),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-16).dp)
                            .graphicsLayer {
                                scaleX = stampScale.value
                                scaleY = stampScale.value
                                rotationZ = 8f
                                alpha = if (stampScale.value > 0f) 1f else 0f
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (stars == 3) Banana else Berry)
                            .border(3.dp, Ink, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            newSkins.forEach { skin ->
                val rarity = com.snakechase.app.ui.components.rarityColor(skin.rarity)
                ArcadeInset(Modifier.fillMaxWidth(), highlight = rarity) {
                    com.snakechase.app.ui.components.SkinPreview(skin, Modifier.size(width = 96.dp, height = 44.dp), segments = 8)
                    Column(Modifier.weight(1f)) {
                        Text("New skin unlocked!", style = displayStyle(14.sp, rarity))
                        Text(skin.displayName, style = displayStyle(17.sp, Cream))
                        Text(skin.description, color = Cream.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ArcadeTag(skin.rarity.label, rarity)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.levelId < 5) {
                    ArcadeButton(
                        "Next level",
                        icon = ArcadeIcon.Play,
                        fill = Banana,
                        onClick = {
                            viewModel.handleIntent(GameIntent.SelectLevel(state.levelId + 1))
                            viewModel.handleIntent(GameIntent.StartSelectedLevel)
                        },
                        modifier = Modifier.weight(1.3f),
                    )
                } else {
                    ArcadeButton("Replay", icon = ArcadeIcon.Refresh, fill = Banana, onClick = { viewModel.handleIntent(GameIntent.RestartLevel) }, modifier = Modifier.weight(1.3f))
                }
                ArcadeButton(
                    "Share",
                    icon = ArcadeIcon.Share,
                    fill = Lime,
                    onClick = {
                        shareCard = com.snakechase.app.share.ShareCards.levelClear(
                            levelId = state.levelId,
                            levelName = level.name,
                            stars = stars,
                            score = finalScore,
                            maxScore = maxScore,
                            survivalMs = state.hud.survivalTimeMs,
                            stagesCleared = result?.stagesCleared ?: level.subStages.size,
                            totalStages = result?.totalStages ?: level.subStages.size,
                            newBest = result?.isNewPersonalBest == true,
                            player = state.shareIdentity(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                ArcadeButton("Menu", icon = ArcadeIcon.Home, fill = Cream, onClick = { viewModel.handleIntent(GameIntent.ReturnToMenu) }, modifier = Modifier.weight(1f))
            }
        }
        // Confetti and fireworks over everything; they only draw, so taps go straight through.
        CelebrationLayer(fx, palette, Modifier.fillMaxSize().onGloballyPositioned { layerOrigin = it.positionInRoot() })
        if (maxHeight > 680.dp) {
            SnakeChaseLogo(compact = true, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
        }
        if (showChampion) {
            ChampionCelebration(totalStars = result?.totalStars ?: 15) {
                showChampion = false
                if (newSkins.isNotEmpty()) celebrating = newSkins.sortedByDescending { it.rarity.ordinal }
            }
        }
        celebrating.firstOrNull()?.let { skin ->
            com.snakechase.app.ui.components.SkinUnlockCelebration(skin) { celebrating = celebrating.drop(1) }
        }
        if (orbsTurn && celebrating.isEmpty() && !showChampion) {
            state.newOrbSkins.firstOrNull()?.let { orb ->
                com.snakechase.app.ui.components.OrbUnlockCelebration(orb) { viewModel.handleIntent(GameIntent.OrbCelebrationSeen) }
            }
        }
        shareCard?.let { com.snakechase.app.ui.share.SharePage(it) { shareCard = null } }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Cream.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, style = displayStyle(14.sp, Cream))
    }
}
