package com.snakechase.app.ui.share

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.platform.ShareOutcome
import com.snakechase.app.platform.ShareService
import com.snakechase.app.platform.encodePng
import com.snakechase.app.share.ShareCardData
import com.snakechase.app.share.ShareIdentity
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconButton
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeScrim
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.JungleDeep
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.displayStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** The player as the share card shows them: name, avatar and what they're wearing. */
fun GameState.shareIdentity(): ShareIdentity = ShareIdentity(profile.name, profile.avatar, activeSnakeSkin, activeOrbSkin)

/** Pixel size of the shared PNG: 4:5, the shape Instagram, WhatsApp and X all show uncropped. */
private const val OUT_W = 1080
private const val OUT_H = 1350

/**
 * "Show it off!": the card big on the left, sharing on the right. The card is drawn once on
 * screen and, in the same pass, recorded scaled up to [OUT_W] x [OUT_H] into a graphics layer,
 * so the PNG is a sharp re-render of exactly what you see, not a blurry screenshot.
 */
@Composable
fun SharePage(card: ShareCardData, onClose: () -> Unit) {
    val sharer = koinInject<ShareService>()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val capture = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, isError

    val pop = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }

    fun shareNow() {
        if (busy) return
        busy = true
        status = null
        scope.launch {
            val outcome = runCatching {
                val bitmap = capture.toImageBitmap()
                val png = withContext(Dispatchers.Default) { bitmap.encodePng() }
                sharer.shareImage(png, card.fileName, card.message)
            }.getOrElse { ShareOutcome.Failed(it.message ?: "Couldn't make the card") }
            status = when (outcome) {
                ShareOutcome.Shared -> "Shared! Let them try to beat that." to false
                ShareOutcome.Cancelled -> null
                is ShareOutcome.Saved -> outcome.note to false
                is ShareOutcome.Failed -> outcome.reason to true
            }
            busy = false
        }
    }

    ArcadeScrim(onDismiss = onClose) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val fit = minOf(maxHeight / CARD_H, (maxWidth - 320.dp) / CARD_W, 1.25f).coerceAtLeast(0.4f)
            Row(
                Modifier
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    // Taps on the page itself shouldn't fall through to the scrim and close it.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.size(CARD_W * fit, CARD_H * fit), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .requiredSize(CARD_W, CARD_H)
                            .graphicsLayer {
                                scaleX = fit
                                scaleY = fit
                            }
                            .drawWithContent {
                                val k = OUT_W / size.width
                                capture.record(size = IntSize(OUT_W, OUT_H)) {
                                    // Solid corners: transparent ones show as black or white bars in chat apps.
                                    drawRect(Ink)
                                    scale(k, k, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
                                }
                                drawContent()
                            },
                    ) { ShareCardView(card, Modifier.fillMaxSize()) }
                }
                Column(
                    Modifier.width(280.dp).clip(RoundedCornerShape(22.dp)).background(JungleDeep).border(4.dp, Ink, RoundedCornerShape(22.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedText("Show it off!", displayStyle(30.sp), fill = Banana, outlineWidth = 6.dp, shadow = 3.dp, modifier = Modifier.weight(1f))
                        ArcadeIconButton(ArcadeIcon.Close, onClick = onClose, fill = Berry, size = 36.dp)
                    }
                    ArcadeInset(Modifier.fillMaxWidth()) {
                        Text(card.message, color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    ArcadeButton(
                        if (busy) "Making card..." else sharer.actionLabel,
                        icon = ArcadeIcon.Share,
                        fill = Lime,
                        enabled = !busy,
                        onClick = ::shareNow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ArcadeButton(
                        "Copy text",
                        icon = ArcadeIcon.Link,
                        fill = Cream,
                        onClick = {
                            clipboard.setText(AnnotatedString(card.message))
                            status = "Message copied." to false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    status?.let { (text, isError) ->
                        Text(text, color = if (isError) Berry else Moss, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
