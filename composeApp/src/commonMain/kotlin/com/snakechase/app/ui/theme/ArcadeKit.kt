package com.snakechase.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Panel fill for dialogs and cards. Inside a level it becomes that level's own dark tone. */
val LocalPanelFill = staticCompositionLocalOf { Jungle }

/** Default title-plate colour. Inside a level it becomes that level's signature colour. */
val LocalPanelAccent = staticCompositionLocalOf { Banana }

/**
 * Dimmed backdrop that swallows clicks; tapping it calls [onDismiss] when given. On desktop and
 * web the Escape key calls [onEscape] (by default the same as tapping the backdrop).
 */
@Composable
fun ArcadeScrim(onDismiss: (() -> Unit)? = null, onEscape: (() -> Unit)? = onDismiss, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(onEscape != null) { if (onEscape != null) runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (onEscape == null) {
                    Modifier
                } else {
                    Modifier.focusRequester(focus).focusable().onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                            onEscape()
                            true
                        } else {
                            false
                        }
                    }
                },
            )
            .background(Ink.copy(alpha = 0.78f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The one dialog shell used by every overlay: a jungle-green sticker panel that pops in,
 * a coloured title plate hanging over its top edge, and a round close button.
 * The body scrolls when the screen is short (landscape phones).
 */
@Composable
fun ArcadeDialog(
    title: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ArcadeIcon? = null,
    width: Dp = 540.dp,
    accent: Color = LocalPanelAccent.current,
    dismissOnScrim: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ArcadeScrim(onDismiss = if (dismissOnScrim) onClose else null, onEscape = onClose) {
        ArcadeCard(title = title, onClose = onClose, modifier = modifier, icon = icon, width = width, accent = accent, content = content)
    }
}

/**
 * The same sticker panel as [ArcadeDialog] but placed in a screen rather than over it
 * (lobbies, pickers). Pops in, hangs a title plate over its top edge, scrolls when short.
 */
@Composable
fun ArcadeCard(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    icon: ArcadeIcon? = null,
    width: Dp = 540.dp,
    accent: Color = LocalPanelAccent.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pop = remember { Animatable(0.82f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f)) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 480.dp
        val bodyMax = maxHeight - (if (compact) 30.dp else 40.dp)
        Box(
            modifier
                .widthIn(max = width)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    alpha = ((pop.value - 0.82f) / 0.18f).coerceIn(0f, 1f)
                }
                // Swallow clicks so tapping the panel never reaches what's behind it.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            ArcadePanel(
                modifier = Modifier.padding(top = if (compact) 16.dp else 22.dp),
                fill = LocalPanelFill.current,
                shape = RoundedCornerShape(26.dp),
                shadow = 7.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = bodyMax)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = if (compact) 14.dp else 22.dp,
                            end = if (compact) 14.dp else 22.dp,
                            top = if (compact) 26.dp else 36.dp,
                            bottom = if (compact) 12.dp else 20.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    content = content,
                )
            }
            // Title plate
            ArcadePanel(modifier = Modifier.align(Alignment.TopCenter), fill = accent, shape = RoundedCornerShape(14.dp), shadow = 4.dp) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = if (compact) 4.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (icon != null) ArcadeIconView(icon, size = if (compact) 18.dp else 22.dp)
                    Text(title, style = displayStyle(if (compact) 17.sp else 22.sp))
                }
            }
            if (onClose != null) {
                ArcadeIconButton(
                    ArcadeIcon.Close,
                    onClick = onClose,
                    fill = Berry,
                    size = if (compact) 34.dp else 40.dp,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = if (compact) 6.dp else 10.dp),
                )
            }
        }
    }
}

/** Small chunky heading used inside panels. */
@Composable
fun ArcadeSectionTitle(text: String, modifier: Modifier = Modifier, color: Color = Lime, icon: ArcadeIcon? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) ArcadeIconView(icon, tint = color, size = 16.dp)
        Text(text, style = displayStyle(15.sp, color))
    }
}

/** A sunken row inside a panel: the list item / settings row container. */
@Composable
fun ArcadeInset(
    modifier: Modifier = Modifier,
    highlight: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonSound = LocalButtonSound.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .background(highlight?.copy(alpha = 0.22f) ?: Ink.copy(alpha = 0.32f))
            .border(2.dp, highlight ?: Ink, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        buttonSound()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Pill on/off switch: lime track and a cream knob that slides across. */
@Composable
fun ArcadeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "knob")
    Box(
        modifier
            .width(50.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) Lime else Ink.copy(alpha = 0.55f))
            .border(2.5.dp, Ink, RoundedCornerShape(50))
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(20.dp)
                .clip(CircleShape)
                .background(Cream)
                .border(2.dp, Ink, CircleShape),
        )
    }
}

/** A row of chunky choice chips; the selected one is banana-yellow. */
@Composable
fun <T> ArcadeChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selectedFill: Color = Banana,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == selected
            ArcadeChip(
                text = label(option),
                fill = if (on) selectedFill else JungleDeep,
                textColor = if (on) Ink else Cream,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Small outlined pill; clickable when [onClick] is given. */
@Composable
fun ArcadeChip(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = Cream,
    textColor: Color = Ink,
    icon: ArcadeIcon? = null,
    onClick: (() -> Unit)? = null,
) {
    val buttonSound = LocalButtonSound.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(fill)
            .border(2.5.dp, Ink, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        buttonSound()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) ArcadeIconView(icon, tint = textColor, size = 14.dp)
        Text(text, style = displayStyle(13.sp, textColor), maxLines = 1)
    }
}

/** Tiny static tag (status, rarity, "NEW"). */
@Composable
fun ArcadeTag(text: String, fill: Color, modifier: Modifier = Modifier, textColor: Color = Ink) {
    Text(
        text,
        style = displayStyle(11.sp, textColor),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .border(2.dp, Ink, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Cream text field with an ink outline and a lime label. */
@Composable
fun ArcadeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier) {
        if (label != null) {
            Text(label, style = displayStyle(13.sp, Lime))
            Spacer(Modifier.size(4.dp))
        }
        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Cream)
                .border(2.5.dp, Ink, shape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = Ink.copy(alpha = 0.4f), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = bodyFamily(), letterSpacing = 1.sp),
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Chunky 0..1 slider: ink-outlined track, lime fill, cream knob. Drag or tap anywhere on it. */
@Composable
fun ArcadeSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var widthPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val fill = if (enabled) Lime else Moss
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onValueChange((it.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ -> onValueChange((change.position.x / widthPx).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val knob = 24.dp
        val track = RoundedCornerShape(50)
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(track)
                .background(Ink.copy(alpha = 0.5f))
                .border(2.5.dp, Ink, track),
        ) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(14.dp).clip(track).background(fill).border(2.5.dp, Ink, track))
        }
        Box(
            Modifier
                .offset(x = (maxWidth - knob) * value.coerceIn(0f, 1f))
                .size(knob)
                .clip(CircleShape)
                .background(if (enabled) Cream else CreamShade)
                .border(2.5.dp, Ink, CircleShape),
        )
    }
}

/** Top bar for every sub-screen: back button, sticker title (+ subtitle), and a trailing slot. */
@Composable
fun ArcadeScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    compact: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxWidth()) {
        ArcadeButton("Back", icon = ArcadeIcon.Back, fill = Cream, size = ArcadeSize.Small, onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText(title, displayStyle(if (compact) 24.sp else 34.sp), fill = Lime, outlineWidth = if (compact) 5.dp else 6.dp, halo = Cream, haloWidth = if (compact) 9.dp else 12.dp, shadowColor = Leaf, shadow = 3.dp)
            if (subtitle != null) Text(subtitle, color = Cream, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** Coin balance pill (or an "offline" pill when the wallet can't be trusted). */
@Composable
fun CoinWallet(coins: Int?, offline: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(if (offline) Berry else Cream)
            .border(2.5.dp, Ink, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArcadeIconView(if (offline) ArcadeIcon.Wifi else ArcadeIcon.Coin, size = 18.dp)
        Text(if (offline) "Offline" else "${coins ?: 0}", style = displayStyle(15.sp))
    }
}

/** Big number + caption, for stat grids. */
@Composable
fun ArcadeStat(value: String, caption: String, modifier: Modifier = Modifier, valueColor: Color = Banana) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.copy(alpha = 0.32f))
            .border(2.dp, Ink, RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = displayStyle(20.sp, valueColor), maxLines = 1)
        Text(caption, color = Cream.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
