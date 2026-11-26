package com.snakechase.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.HapticStyle
import com.snakechase.app.domain.model.SoundId
import com.snakechase.app.levels.Cheers
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeScrim
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.RatingStar
import com.snakechase.app.ui.theme.displayStyle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The grand finale, once per player: the last world falls and the whole jungle celebrates.
 * The logo bounces in under a crown, the five worlds pop in one by one, fireworks never stop.
 */
@Composable
fun ChampionCelebration(totalStars: Int, onDone: () -> Unit) {
    val audio = koinInject<AudioManager>()
    val haptics = koinInject<HapticsManager>()
    val fx = rememberCelebrationState()
    val palette = remember { (1..5).map { levelLook(it).accent } + Banana }
    val crown = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val worlds = remember { List(5) { Animatable(0f) } }

    LaunchedEffect(Unit) {
        audio.playSfx(SoundId.LEVEL_COMPLETE, 1f)
        haptics.trigger(HapticStyle.HEAVY)
        crown.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
        title.snapTo(2.4f)
        title.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
        for (i in 0 until 5) {
            audio.playSfx(SoundId.STAGE_ADVANCE, 1f + i * 0.15f)
            haptics.trigger(HapticStyle.LIGHT)
            worlds[i].snapTo(1.8f)
            worlds[i].animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 520f))
            delay(90)
        }
    }

    ArcadeScrim(onDismiss = null) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val roomy = maxHeight > 520.dp
            // On top of the scrim: the card underneath must not show through the finale's text.
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.6f)))
            SunRays(Banana.copy(alpha = 0.12f), Modifier.fillMaxSize(), rays = 18)
            CelebrationLayer(fx, palette, Modifier.fillMaxSize(), confettiSeconds = 7f, pieces = 120, autoFireworks = true)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (roomy) 10.dp else 4.dp)) {
                Box(
                    Modifier.graphicsLayer {
                        scaleX = crown.value
                        scaleY = crown.value
                        rotationZ = (1f - crown.value) * -30f
                    },
                ) { ArcadeIconView(ArcadeIcon.Crown, tint = Banana, size = if (roomy) 64.dp else 40.dp) }
                if (roomy) SnakeChaseLogo(compact = true)
                OutlinedText(
                    "Jungle Champion!",
                    displayStyle(if (roomy) 52.sp else 36.sp),
                    fill = Banana,
                    outlineWidth = 8.dp,
                    shadow = 5.dp,
                    halo = Cream,
                    haloWidth = 15.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = title.value
                        scaleY = title.value
                        alpha = if (title.value > 0f) 1f else 0f
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (id in 1..5) {
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier.graphicsLayer {
                                scaleX = worlds[id - 1].value
                                scaleY = worlds[id - 1].value
                                rotationZ = (id - 3) * 4f
                            },
                        ) {
                            Image(
                                painterResource(levelArt(id)),
                                null,
                                Modifier.size(if (roomy) 84.dp else 60.dp, if (roomy) 56.dp else 40.dp).clip(shape).border(3.dp, levelLook(id).accent, shape),
                                contentScale = ContentScale.Crop,
                            )
                            Box(Modifier.align(Alignment.BottomEnd).border(2.dp, Ink, RoundedCornerShape(50))) {
                                RatingStar(filled = true, size = 22.dp)
                            }
                        }
                    }
                }
                Text("$totalStars / 15 stars", style = displayStyle(20.sp, Banana))
                Text(Cheers.champion(totalStars), color = Cream, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                ArcadeButton("Take a bow", icon = ArcadeIcon.Trophy, fill = Banana, onClick = onDone)
            }
        }
    }
}
