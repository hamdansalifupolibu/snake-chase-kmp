package com.snakechase.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.levels.LevelConfig
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.presentation.GameIntent
import com.snakechase.app.presentation.GameViewModel
import com.snakechase.app.ui.components.AboutOverlay
import com.snakechase.app.ui.components.AvatarBadge
import com.snakechase.app.ui.components.DevTestingPanel
import com.snakechase.app.ui.components.DifficultyPrompt
import com.snakechase.app.ui.components.DifficultySwitch
import com.snakechase.app.ui.components.JungleBackdrop
import com.snakechase.app.ui.components.LeaderboardOverlay
import com.snakechase.app.ui.components.LevelBackdrop
import com.snakechase.app.ui.components.SettingsOverlay
import com.snakechase.app.ui.components.SnakeChaseLogo
import com.snakechase.app.ui.components.levelLook
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconButton
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadePanel
import com.snakechase.app.ui.theme.ArcadePressable
import com.snakechase.app.ui.theme.ArcadeSize
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.RatingStar
import com.snakechase.app.ui.theme.Tangerine
import com.snakechase.app.ui.theme.displayStyle

@Composable
fun MenuScreen(
    state: GameState,
    viewModel: GameViewModel,
    onOpenMultiplayer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactHeight = maxHeight < 500.dp
        var askDifficulty by remember { mutableStateOf(false) }

        // First Play ever (button or tapping the chosen card again): ask how hard levels should be.
        val play = {
            if (state.settings.difficultyChosen) {
                viewModel.handleIntent(GameIntent.StartSelectedLevel)
            } else {
                askDifficulty = true
            }
        }

        // The focused card (hovered, else selected) comes forward and its world fills the screen:
        // the menu becomes that level, cross-fading as you move between cards.
        var hoveredId by remember { mutableStateOf<Int?>(null) }
        val focusedId = hoveredId ?: state.levelId
        Crossfade(targetState = focusedId, animationSpec = tween(700), label = "world") { id ->
            LevelBackdrop(id, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.5f)))
        JungleBackdrop(framingOnly = true, levelId = focusedId)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = if (isCompactHeight) 8.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SnakeChaseLogo(compact = isCompactHeight, modifier = Modifier.padding(top = if (isCompactHeight) 0.dp else 8.dp))

            // Arena picker: tap a card to select it, tap the selected card again to play.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 12.dp else 22.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                // Room around the cards so the one popping forward in 3D isn't clipped by the row.
                contentPadding = PaddingValues(horizontal = if (isCompactHeight) 20.dp else 30.dp, vertical = if (isCompactHeight) 10.dp else 34.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(LevelRegistry.ALL_LEVELS) { level ->
                    OriginalLevelCard(
                        level = level,
                        isFocused = focusedId == level.id,
                        offsetFromFocus = level.id - focusedId,
                        onHoverChange = { inside ->
                            if (inside) {
                                hoveredId = level.id
                            } else if (hoveredId == level.id) {
                                hoveredId = null
                            }
                        },
                        isSelected = state.levelId == level.id,
                        isUnlocked = state.unlockedLevelIds.contains(level.id),
                        bestScore = state.bestScores[level.id] ?: 0,
                        stars = state.levelStarRatings[level.id] ?: 0,
                        isCompact = isCompactHeight,
                        onSelect = {
                            if (state.unlockedLevelIds.contains(level.id)) {
                                if (state.levelId == level.id) {
                                    play()
                                } else {
                                    viewModel.handleIntent(GameIntent.SelectLevel(level.id))
                                }
                            }
                        },
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val selected = LevelRegistry.ALL_LEVELS.firstOrNull { it.id == state.levelId }
                val side = ArcadeSize.Medium
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArcadeButton(
                        text = "Play",
                        subtitle = selected?.let { "Level ${it.id} · ${it.name}" },
                        icon = ArcadeIcon.Play,
                        fill = Banana,
                        size = if (isCompactHeight) ArcadeSize.Medium else ArcadeSize.Large,
                        onClick = { play() },
                    )

                    // Daily trial: one shared seed a day (Wordle-style retention).
                    val dailyInfo = state.dailyChallengeInfo
                    val isDailyDone = dailyInfo?.isCompletedToday == true
                    ArcadeButton(
                        text = if (isDailyDone) "Trial ${dailyInfo.todayScore} pts" else "Daily trial",
                        subtitle = if (isDailyDone) "Back tomorrow" else dailyInfo?.modifierName,
                        icon = ArcadeIcon.Calendar,
                        fill = if (isDailyDone) Cream else Tangerine,
                        size = side,
                        onClick = { viewModel.handleIntent(GameIntent.StartDailyChallenge) },
                    )
                    ArcadeButton(
                        text = "1v1 Duel",
                        icon = ArcadeIcon.Swords,
                        fill = Cream,
                        size = side,
                        onClick = { viewModel.handleIntent(GameIntent.OpenMultiplayer) },
                    )
                    ArcadeButton(
                        text = "Snake Hunt",
                        icon = ArcadeIcon.Target,
                        fill = Berry,
                        size = side,
                        onClick = { viewModel.handleIntent(GameIntent.OpenSnakeHunt) },
                    )
                }
                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))
                DifficultySwitch(
                    selected = state.settings.difficulty,
                    onPick = { d -> viewModel.handleIntent(GameIntent.UpdateSettings { it.copy(difficulty = d, difficultyChosen = true) }) },
                )

                // Credit line only where there's room; on landscape phones it lives in About.
                if (!isCompactHeight) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hamdan Salifu Polibu · Kotlin Multiplatform Contest 2027" +
                            if (state.judgeEdition) "  ·  Judge edition: everything is unlocked" else "",
                        color = Moss,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Top-left: settings, about, leaderboard, bestiary, skins.
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArcadeIconButton(ArcadeIcon.Gear, onClick = { viewModel.handleIntent(GameIntent.OpenSettings) })
            ArcadeIconButton(ArcadeIcon.Info, onClick = { viewModel.handleIntent(GameIntent.OpenAbout) })
            ArcadeIconButton(ArcadeIcon.Trophy, onClick = { viewModel.handleIntent(GameIntent.OpenLeaderboard) })
            ArcadeIconButton(ArcadeIcon.Book, onClick = { viewModel.handleIntent(GameIntent.OpenBestiary) })
            ArcadeIconButton(ArcadeIcon.Palette, onClick = { viewModel.handleIntent(GameIntent.OpenSkinSelect) })
        }

        // Top-right: profile + coin wallet, judge mode.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ArcadePressable(
                onClick = { viewModel.handleIntent(GameIntent.OpenProfile) },
                fill = Cream,
                shape = RoundedCornerShape(50),
                depth = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 5.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AvatarBadge(state.profile.name, state.profile.avatar, size = 26.dp)
                    ArcadeIconView(ArcadeIcon.Coin, size = 20.dp)
                    Text("${state.profile.coins}", style = displayStyle(16.sp))
                }
            }
            ArcadeIconButton(
                ArcadeIcon.Scale,
                fill = if (state.judgeModeActive) Lime else Cream,
                onClick = { viewModel.handleIntent(GameIntent.ToggleJudgeMode) },
            )
        }

        // Settings Overlay
        if (state.showSettingsOverlay) {
            SettingsOverlay(
                settings = state.settings,
                onUpdateSettings = { transform -> viewModel.handleIntent(GameIntent.UpdateSettings(transform)) },
                onResetProgress = { viewModel.handleIntent(GameIntent.ResetProgress) },
                onClose = { viewModel.handleIntent(GameIntent.CloseSettings) },
            )
        }

        // About & Credits Overlay
        if (state.showAboutOverlay) {
            AboutOverlay(
                onClose = { viewModel.handleIntent(GameIntent.CloseAbout) },
            )
        }

        // Leaderboard & Trophy Overlay
        if (askDifficulty) {
            DifficultyPrompt(
                onPick = { d ->
                    askDifficulty = false
                    viewModel.handleIntent(GameIntent.UpdateSettings { it.copy(difficulty = d, difficultyChosen = true) })
                    viewModel.handleIntent(GameIntent.StartSelectedLevel)
                },
                onClose = { askDifficulty = false },
            )
        }
        if (state.showLeaderboardOverlay) {
            LeaderboardOverlay(
                state = state,
                onClose = { viewModel.handleIntent(GameIntent.CloseLeaderboard) },
            )
        }

        // Snake Bestiary & Collection Overlay
        if (state.showBestiaryOverlay) {
            val cards = com.snakechase.app.domain.model.BestiaryCard.createCardList(
                unlockedLevels = state.unlockedLevelIds,
                bestTimes = state.bestTimes,
                completedLevels = state.unlockedLevelIds,
                bestScores = state.bestScores,
            )
            com.snakechase.app.ui.components.BestiaryOverlay(
                cards = cards,
                onClose = { viewModel.handleIntent(GameIntent.CloseBestiary) },
            )
        }

        // Snake Skin Vault Overlay
        if (state.showSkinSelectOverlay) {
            com.snakechase.app.ui.components.SkinSelectOverlay(
                unlockedSkins = state.unlockedSkins,
                activeSkin = state.activeSnakeSkin,
                onEquip = { viewModel.handleIntent(GameIntent.EquipSkin(it)) },
                coins = state.profile.coins,
                onBuy = { viewModel.handleIntent(GameIntent.BuySkin(it)) },
                levelStars = state.levelStarRatings,
                unlockedOrbs = state.profile.unlockedOrbs,
                everythingOpen = state.judgeEdition,
                activeOrb = state.activeOrbSkin,
                onEquipOrb = { viewModel.handleIntent(GameIntent.EquipOrb(it)) },
                onBuyOrb = { viewModel.handleIntent(GameIntent.BuyOrb(it)) },
                onClose = { viewModel.handleIntent(GameIntent.CloseSkinSelect) },
            )
        }

        // An orb earned in a run you quit before the result screen is celebrated here.
        state.newOrbSkins.firstOrNull()?.let { orb ->
            com.snakechase.app.ui.components.OrbUnlockCelebration(orb) { viewModel.handleIntent(GameIntent.OrbCelebrationSeen) }
        }

        // Dev Testing Panel (Right sidebar)
        AnimatedVisibility(
            // Steps aside while a dialog is open, so it never covers the dialog or its close button.
            visible = state.judgeModeActive &&
                !state.showSettingsOverlay &&
                !state.showAboutOverlay &&
                !state.showLeaderboardOverlay &&
                !state.showBestiaryOverlay &&
                !state.showSkinSelectOverlay &&
                !state.showProfileOverlay &&
                !askDifficulty,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            DevTestingPanel(
                state = state,
                viewModel = viewModel,
                onClose = { viewModel.handleIntent(GameIntent.ToggleJudgeMode) },
            )
        }
    }
}

/**
 * A level as a sticker card showing a live miniature of its arena. The focused card
 * (hovered, else selected) comes forward in 3D: bigger, raised, tilting toward the pointer,
 * with the scene's layers sliding at different depths. The others step back and dim.
 */
@Composable
fun OriginalLevelCard(
    level: LevelConfig,
    isSelected: Boolean,
    isUnlocked: Boolean,
    bestScore: Int,
    stars: Int = 0,
    isCompact: Boolean = false,
    isFocused: Boolean = isSelected,
    /** This card's position relative to the focused one (negative = left of it). */
    offsetFromFocus: Int = 0,
    onHoverChange: (Boolean) -> Unit = {},
    onSelect: () -> Unit,
) {
    val look = levelLook(level.id)
    // Phones: five cards plus the focused one's growth must fit a ~800dp-wide landscape screen.
    val cardWidth = if (isCompact) 136.dp else 196.dp
    // Compact = landscape phone (~390dp tall): leaves room for the logo and the button row.
    val cardHeight = if (isCompact) 140.dp else 236.dp
    val artShape = RoundedCornerShape(14.dp)

    // ── 3D pop: hover (mouse) tilts the card toward the pointer; a tap (touch) swings it in. ──
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val swing = remember { Animatable(0f) }
    LaunchedEffect(isSelected) {
        // Touch screens have no hover, so selecting a card plays a one-off 3D swing instead.
        if (isSelected && !hovered) {
            swing.snapTo(18f)
            swing.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 140f))
        }
    }
    LaunchedEffect(hovered) { onHoverChange(hovered) }
    val forward = isUnlocked && isFocused
    // Pointer position as -1..1 across the card; drives the tilt, the scene parallax and the glare.
    val aim = pointer?.takeIf { hovered && cardSize.width > 0 }?.let {
        Offset(it.x / cardSize.width * 2f - 1f, it.y / cardSize.height * 2f - 1f)
    }
    val bouncy = spring<Float>(dampingRatio = 0.48f, stiffness = 300f)
    val tiltSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 260f)
    // Coverflow: cards beside the focused one turn their faces toward it.
    val coverflow = when {
        forward || offsetFromFocus == 0 -> 0f
        offsetFromFocus > 0 -> COVERFLOW_DEG
        else -> -COVERFLOW_DEG
    }
    val rotY by animateFloatAsState((aim?.x ?: 0f) * 15f + coverflow, tiltSpring, label = "rotY")
    val rotX by animateFloatAsState(-(aim?.y ?: 0f) * 12f, tiltSpring, label = "rotX")
    // Focused card grows well past its neighbours, which step back to make room.
    val scale by animateFloatAsState(if (forward) (if (isCompact) 1.12f else 1.18f) else 0.9f, bouncy, label = "scale")
    val rise by animateDpAsState(if (forward) (-16).dp else 0.dp, spring(dampingRatio = 0.48f, stiffness = 300f), label = "rise")
    val depth by animateDpAsState(if (forward) 14.dp else 4.dp, label = "depth")
    val dim by animateFloatAsState(if (forward) 0f else 0.3f, label = "dim")
    val totalRotY = rotY + swing.value
    // The glare follows the mouse; during a touch swing it sweeps across with the turn.
    val glare = aim ?: if (swing.value != 0f) Offset(-swing.value / 14f, -0.4f) else null
    // A streak of light runs across the art each time the card comes into focus.
    val shine = remember { Animatable(-1f) }
    LaunchedEffect(forward) {
        if (forward) {
            shine.snapTo(-0.4f)
            shine.animateTo(1.4f, tween(750, easing = FastOutSlowInEasing))
        }
    }
    // While in focus the card floats gently, as if held up to the light.
    val bob = if (forward) {
        rememberInfiniteTransition(label = "bob").animateFloat(-1f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "y").value
    } else {
        0f
    }

    Box(
        Modifier
            .zIndex(if (forward) 1f else 0f)
            .onSizeChanged { cardSize = it }
            .hoverable(hoverSource, enabled = isUnlocked)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pointer = if (event.type == PointerEventType.Exit) null else event.changes.firstOrNull()?.position
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = rotX
                rotationY = totalRotY
                translationY = rise.toPx() + bob * 3.dp.toPx()
                cameraDistance = 14f * density
            },
    ) {
        // In focus, the frame takes the level's own signature colour.
        ArcadePanel(fill = if (forward) look.accent else Cream, shape = RoundedCornerShape(20.dp), shadow = depth) {
            Column(
                Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .clickable(enabled = isUnlocked, onClick = onSelect),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(6.dp)
                        .clip(artShape)
                        .background(Ink)
                        .border(2.5.dp, Ink, artShape),
                ) {
                    // A live miniature of the arena. Its layers slide against the tilt at
                    // different depths, so the card reads as a window into a 3D scene.
                    LevelBackdrop(
                        levelId = level.id,
                        parallax = Offset(-totalRotY * 1.4f, rotX * 1.4f) * LocalDensity.current.density,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (isUnlocked) 1f else 0.35f
                                scaleX = 1.1f
                                scaleY = 1.1f
                            },
                    )
                    if (shine.value > -0.4f && shine.value < 1.4f) {
                        Box(
                            Modifier.fillMaxSize().drawBehind {
                                val x = shine.value * size.width
                                val band = size.width * 0.22f
                                val streak = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(x - band, size.height)
                                    lineTo(x, size.height)
                                    lineTo(x + band * 1.6f, 0f)
                                    lineTo(x + band * 0.6f, 0f)
                                    close()
                                }
                                drawPath(streak, Color.White.copy(alpha = 0.32f))
                            },
                        )
                    }
                    if (glare != null) {
                        Box(
                            Modifier.fillMaxSize().drawBehind {
                                val center = Offset((glare.x + 1f) / 2f * size.width, (glare.y + 1f) / 2f * size.height)
                                drawRect(
                                    Brush.radialGradient(
                                        listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                                        center = center,
                                        radius = size.maxDimension * 0.65f,
                                    ),
                                )
                            },
                        )
                    }
                    // Level number badge
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(if (isCompact) 24.dp else 30.dp)
                            .clip(CircleShape)
                            .background(Banana)
                            .border(2.5.dp, Ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${level.id}", style = displayStyle(if (isCompact) 13.sp else 16.sp))
                    }
                    if (isUnlocked) {
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Ink.copy(alpha = 0.75f))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            repeat(3) { RatingStar(filled = it < stars, size = if (isCompact) 13.dp else 16.dp) }
                        }
                    } else {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(if (isCompact) 38.dp else 48.dp)
                                .clip(CircleShape)
                                .background(Cream)
                                .border(3.dp, Ink, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            ArcadeIconView(ArcadeIcon.Lock, size = if (isCompact) 20.dp else 24.dp)
                        }
                    }
                }
                Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = if (isCompact) 6.dp else 9.dp)) {
                    Text(
                        level.name,
                        style = displayStyle(if (isCompact) 15.sp else 19.sp, if (isUnlocked) Ink else Ink.copy(alpha = 0.5f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${level.subStages.size} stages",
                            color = Ink.copy(alpha = 0.65f),
                            fontSize = if (isCompact) 10.sp else 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        if (isUnlocked && bestScore > 0) {
                            Text(
                                "$bestScore / ${level.scoringConfig.levelScoreMax}",
                                color = Ink,
                                fontSize = if (isCompact) 10.sp else 12.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
        // Cards that aren't in focus sink into the dark a little, so the focused one owns the stage.
        if (dim > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(bottom = depth)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink.copy(alpha = dim)),
            )
        }
    }
}

/** How far neighbouring cards turn toward the focused one. */
private const val COVERFLOW_DEG = 14f
