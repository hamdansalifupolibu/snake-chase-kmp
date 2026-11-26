package com.snakechase.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.HapticStyle
import com.snakechase.app.domain.model.MultiplayerState
import com.snakechase.app.domain.model.SoundId
import com.snakechase.app.domain.model.SteeringMode
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.MultiplayerEngine
import com.snakechase.app.hunt.HuntProgress
import com.snakechase.app.hunt.HuntStage
import com.snakechase.app.hunt.SnakeHunt
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.profile.ProfileRepository
import com.snakechase.app.ui.components.JungleBackdrop
import com.snakechase.app.ui.components.LevelBackdrop
import com.snakechase.app.ui.components.levelLook
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeChip
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadePressable
import com.snakechase.app.ui.theme.ArcadeScreenHeader
import com.snakechase.app.ui.theme.ArcadeSize
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.CreamShade
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.RatingStar
import com.snakechase.app.ui.theme.displayStyle
import com.snakechase.app.util.toFixed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The outcome of one hunt, shown on the result card. */
private data class HuntResult(val stage: HuntStage, val caughtAtMs: Long?, val stars: Int, val coins: Int)

/**
 * Snake Hunt: you are the snake. Pick a hunt, catch the AI orb before its clock runs out,
 * earn stars for speed. Reuses the duel arena and engine; only the rules around it are new.
 */
@Composable
fun SnakeHuntScreen(
    playerName: String,
    steeringMode: SteeringMode,
    progress: HuntProgress,
    audioManager: AudioManager,
    hapticsManager: HapticsManager,
    onExit: () -> Unit,
    profiles: ProfileRepository? = null,
) {
    var stage by remember { mutableStateOf<HuntStage?>(null) }
    var game by remember { mutableStateOf<MultiplayerState?>(null) }
    var result by remember { mutableStateOf<HuntResult?>(null) }
    var aim by remember { mutableStateOf<Vec2?>(null) }
    var progressVersion by remember { mutableStateOf(0) } // re-reads stars after a hunt

    fun begin(s: HuntStage) {
        stage = s
        result = null
        aim = null
        // Each hunt plays in its own level world.
        game = SnakeHunt.startState(s, playerName.ifBlank { "You" }).copy(selectedTierIndex = huntTierFor(s.number))
    }

    val huntFrames = remember { ArrayDeque<com.snakechase.app.domain.model.GameState>() }
    var huntReplay by remember { mutableStateOf<com.snakechase.app.presentation.DeathReplay?>(null) }

    fun finish(s: HuntStage, caughtAtMs: Long?) {
        // A catch replays in slow motion (zooming in on the bite) before the result card.
        if (caughtAtMs != null && huntFrames.size >= 2) {
            huntReplay = com.snakechase.app.presentation.DeathReplay(huntFrames.toList(), huntFrames.last().player.position)
        }
        huntFrames.clear()
        val stars = SnakeHunt.starsFor(s, caughtAtMs)
        val coins = progress.record(s.number, stars)
        if (coins > 0) profiles?.addCoins(coins)
        progressVersion++
        if (caughtAtMs != null) {
            audioManager.playSfx(SoundId.BULLET_TIME, 0.6f)
            hapticsManager.trigger(HapticStyle.HEAVY)
        } else {
            audioManager.playSfx(SoundId.DEATH)
            hapticsManager.trigger(HapticStyle.MEDIUM)
        }
        result = HuntResult(s, caughtAtMs, stars, coins)
        game = null
    }

    // 3-2-1 countdown, then the hunt runs on the frame clock until a catch or the timeout.
    val current = game
    LaunchedEffect(current?.phase, stage) {
        val s = stage ?: return@LaunchedEffect
        when (game?.phase) {
            DuelPhase.COUNTDOWN -> {
                for (n in 3 downTo 1) {
                    game = game?.copy(countdownCount = n)
                    audioManager.playSfx(SoundId.COUNTDOWN_TICK)
                    delay(700)
                }
                game = game?.copy(countdownCount = 0)
                audioManager.playSfx(SoundId.COUNTDOWN_GO)
                delay(350)
                game = game?.copy(phase = DuelPhase.ROUND_PLAYING)
            }
            DuelPhase.ROUND_PLAYING -> {
                var last = 0L
                while (isActive) {
                    val ended = withFrameMillis { now ->
                        val g = game ?: return@withFrameMillis true
                        if (last == 0L) {
                            last = now
                            return@withFrameMillis false
                        }
                        // Hunting: my worn snake skin's perks (speed, agility, reach) apply.
                        val p = profiles?.profile?.value
                        val perk = com.snakechase.app.domain.model.SkinPerks.of(com.snakechase.app.config.Edition.unlocks.wornSkin(p?.activeSkin.orEmpty(), p?.unlockedSkins.orEmpty()))
                        val tick = MultiplayerEngine.tick(g.copy(localSnakePerk = perk), localInputPos = aim, rivalInputPos = null, deltaMs = (now - last).coerceIn(1L, 50L))
                        last = now
                        if (tick.roundCompleted) {
                            finish(s, if (tick.contactOccurred) tick.newState.survivalTimeMs else null)
                            true
                        } else {
                            game = tick.newState
                            false
                        }
                    }
                    if (ended) break
                }
            }
            else -> Unit
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Ink)) {
        val isCompact = maxHeight < 500.dp
        val g = game
        val r = result
        when {
            g != null -> {
                DuelArenaView(
                    showCountdown = false,
                    onFrame = { f ->
                        huntFrames.addLast(f)
                        while (huntFrames.size > com.snakechase.app.presentation.DeathReplay.FRAMES) huntFrames.removeFirst()
                    },
                    mySkin = profiles?.profile?.value.let { p -> com.snakechase.app.config.Edition.unlocks.wornSkin(p?.activeSkin.orEmpty(), p?.unlockedSkins.orEmpty()) },
                    myOrb = profiles?.profile?.value.let { p -> com.snakechase.app.config.Edition.unlocks.wornOrb(p?.activeOrb.orEmpty(), p?.unlockedOrbs.orEmpty()) },
                    state = g,
                    isCompact = isCompact,
                    localInputPos = aim,
                    showQuickChat = false,
                    onToggleQuickChat = {},
                    onSendQuickChat = { _, _ -> },
                    onLocalMove = { aim = it },
                    onRivalMove = {},
                    onQuit = {
                        game = null
                        stage = null
                    },
                    keyboardOnly = steeringMode == SteeringMode.KEYBOARD,
                    useJoystick = steeringMode == SteeringMode.VIRTUAL_STICK,
                    allowQuickChat = false,
                )
                HuntClockOverlay(g, huntWorld(g), Modifier.align(Alignment.TopCenter).padding(top = if (isCompact) 66.dp else 82.dp))
            }
            r != null -> {
                LevelBackdrop(huntWorld(r.stage.number), Modifier.fillMaxSize())
                HuntResultCard(
                    result = r,
                    canGoNext = r.caughtAtMs != null && SnakeHunt.stages.any { it.number == r.stage.number + 1 },
                    onRetry = { begin(r.stage) },
                    onNext = { SnakeHunt.stages.firstOrNull { it.number == r.stage.number + 1 }?.let(::begin) },
                    onBack = {
                        result = null
                        stage = null
                    },
                )
            }
            else -> HuntSelect(
                progress = progress,
                progressVersion = progressVersion,
                isCompact = isCompact,
                onPick = ::begin,
                onBack = onExit,
            )
        }
        huntReplay?.let { r ->
            DeathReplayView(r) {
                huntReplay = null
                audioManager.playSfx(SoundId.STAR_3) // the hunter's triumph lands with the bite
            }
        }
    }
}

/** Each hunt plays in its own world: hunt 1 in Neon Drift … hunt 5 in Crucible Core. */
private fun huntWorld(huntNumber: Int): Int = huntNumber.coerceIn(1, 5)

private fun huntWorld(state: MultiplayerState): Int = TIER_TO_LEVEL[state.selectedTierIndex] ?: 1

/** The duel arena picks its world from the tier index; these map a world to that index and back. */
private val LEVEL_TO_TIER = mapOf(1 to 0, 2 to 1, 3 to 4, 4 to 2, 5 to 3)
private val TIER_TO_LEVEL = LEVEL_TO_TIER.entries.associate { (level, tier) -> tier to level }

internal fun huntTierFor(huntNumber: Int): Int = LEVEL_TO_TIER[huntWorld(huntNumber)] ?: 0

/** Countdown before the hunt, then the time left: the hunt's own clock. */
@Composable
private fun HuntClockOverlay(state: MultiplayerState, levelId: Int, modifier: Modifier) {
    if (state.phase == DuelPhase.COUNTDOWN) {
        CountdownStamp(levelId, "Hunt starts", state.countdownCount)
        return
    }
    val leftMs = (state.maxRoundTimeMs - state.survivalTimeMs).coerceAtLeast(0)
    val urgent = leftMs < 5_000
    ArcadeChip(
        "Catch it · ${(leftMs / 1000f).toFixed(1)}s left",
        fill = if (urgent) Berry else Banana,
        icon = ArcadeIcon.Target,
        modifier = modifier,
    )
}

@Composable
private fun HuntSelect(
    progress: HuntProgress,
    progressVersion: Int,
    isCompact: Boolean,
    onPick: (HuntStage) -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        JungleBackdrop()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = if (isCompact) 10.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ArcadeScreenHeader(
                title = "Snake Hunt",
                subtitle = "You are the snake · catch the orb before its clock runs out",
                onBack = onBack,
                compact = isCompact,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SnakeHunt.stages.forEach { s ->
                    // progressVersion is read so the cards refresh after a hunt.
                    val stars = progressVersion.let { progress.stars(s.number) }
                    HuntCard(s, stars, progress.isUnlocked(s.number), isCompact, Modifier.weight(1f)) { onPick(s) }
                }
            }
            ArcadeChip(
                (if (com.snakechase.app.config.Edition.JUDGE_EDITION) "Judge edition: every hunt is open" else "Win a hunt to open the next") +
                    " · stars for speed · each new star pays ${SnakeHunt.COINS_PER_NEW_STAR}",
                fill = Cream,
                icon = ArcadeIcon.Coin,
            )
        }
    }
}

@Composable
private fun HuntCard(s: HuntStage, stars: Int, open: Boolean, isCompact: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val accent = levelLook(huntWorld(s.number)).accent
    ArcadePressable(onClick = onPick, enabled = open, fill = if (open) Cream else CreamShade, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            val art = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 70.dp else 110.dp)
                    .padding(6.dp)
                    .clip(art)
                    .border(2.5.dp, Ink, art),
            ) {
                LevelBackdrop(huntWorld(s.number), Modifier.fillMaxSize().graphicsLayer { alpha = if (open) 1f else 0.35f })
                Box(
                    Modifier.align(Alignment.TopStart).padding(5.dp).size(26.dp).clip(CircleShape).background(accent).border(2.5.dp, Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${s.number}", style = displayStyle(14.sp)) }
                if (!open) {
                    Box(
                        Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape).background(Cream).border(3.dp, Ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { ArcadeIconView(ArcadeIcon.Lock, size = 20.dp) }
                }
            }
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(s.name, style = displayStyle(if (isCompact) 15.sp else 18.sp))
                if (!isCompact) Text(s.blurb, color = Ink.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArcadeTag("${s.timeLimitMs / 1000}s", accent)
                    Box(Modifier.weight(1f))
                    repeat(3) { RatingStar(filled = it < stars, size = if (isCompact) 14.dp else 17.dp, empty = Ink.copy(alpha = 0.15f)) }
                }
            }
        }
    }
}

@Composable
private fun HuntResultCard(
    result: HuntResult,
    canGoNext: Boolean,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val caught = result.caughtAtMs != null
    val look = levelLook(huntWorld(result.stage.number))
    // Stars stamp in one after another.
    val scales = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(result) {
        for (i in 0 until result.stars) {
            scales[i].snapTo(2.2f)
            scales[i].animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    ArcadeDialog(
        title = if (caught) "Caught it!" else "It got away",
        icon = if (caught) ArcadeIcon.Trophy else ArcadeIcon.Target,
        onClose = null,
        dismissOnScrim = false,
        width = 460.dp,
        accent = if (caught) look.accent else Berry,
    ) {
        Text("Hunt ${result.stage.number} · ${result.stage.name}", style = displayStyle(16.sp, Cream), modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            for (i in 0 until 3) {
                val earned = i < result.stars
                RatingStar(
                    filled = earned,
                    size = if (i == 1) 56.dp else 46.dp,
                    modifier = Modifier.graphicsLayer {
                        val k = if (earned) scales[i].value else 1f
                        scaleX = k
                        scaleY = k
                    },
                )
            }
        }
        Text(
            if (caught) "Caught in ${(result.caughtAtMs!! / 1000f).toFixed(1)}s of ${result.stage.timeLimitMs / 1000}s" else "The clock ran out. Cut it off, don't just chase.",
            color = Cream,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (result.coins > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                ArcadeIconView(ArcadeIcon.Coin, size = 22.dp)
                Text("  New best · +${result.coins}", style = displayStyle(18.sp, Banana))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            ArcadeButton("Hunts", icon = ArcadeIcon.Back, fill = Cream, size = ArcadeSize.Small, onClick = onBack)
            ArcadeButton("Retry", icon = ArcadeIcon.Refresh, fill = if (canGoNext) Cream else Banana, size = ArcadeSize.Small, onClick = onRetry)
            if (canGoNext) ArcadeButton("Next hunt", icon = ArcadeIcon.Play, fill = Banana, size = ArcadeSize.Small, onClick = onNext)
        }
    }
}
