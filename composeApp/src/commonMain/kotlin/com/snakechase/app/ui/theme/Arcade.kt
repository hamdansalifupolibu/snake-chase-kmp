package com.snakechase.app.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import snakechase.composeapp.generated.resources.Res
import snakechase.composeapp.generated.resources.lilita_one
import snakechase.composeapp.generated.resources.nunito_black
import snakechase.composeapp.generated.resources.nunito_bold
import snakechase.composeapp.generated.resources.nunito_extrabold
import snakechase.composeapp.generated.resources.nunito_regular
import snakechase.composeapp.generated.resources.nunito_semibold

// ─── Jungle Arcade palette ────────────────────────────────────────────────────
// Sticker-style: flat fills, one ink colour for every outline and hard shadow.

/** Outlines, hard shadows and text on light fills. Never pure black. */
val Ink = Color(0xFF0E1F17)
val JungleNight = Color(0xFF10231A)
val JungleDeep = Color(0xFF163126)
val Jungle = Color(0xFF1E3B2F)
val Leaf = Color(0xFF2B5A3F)
val LeafLight = Color(0xFF3E7A52)
val Lime = Color(0xFFC8F560)
val Tangerine = Color(0xFFFF8A3D)
val Banana = Color(0xFFFFD23F)
val Cream = Color(0xFFFFF4DC)
val CreamShade = Color(0xFFEBDDBF)
val Berry = Color(0xFFFF4F7B)
val Sky = Color(0xFF5BC8FF)
val Moss = Color(0xFF8FA89A)

/** Hard (unblurred) drop shadow depth used by every arcade surface. */
val ArcadeShadow = 5.dp
val ArcadeOutline = 3.dp

// ─── Type ────────────────────────────────────────────────────────────────────

/** Chunky display face for titles, buttons and numbers. */
@Composable
fun displayFamily(): FontFamily = FontFamily(Font(Res.font.lilita_one))

/**
 * Rounded body face. One static file per weight: desktop and web ignore the weight axis of a
 * variable font, so these were cut from Nunito's variable font with fontTools' instancer.
 * Weights in between (Medium) fall back to the nearest file.
 */
@Composable
fun bodyFamily(): FontFamily = FontFamily(
    Font(Res.font.nunito_regular, FontWeight.Normal),
    Font(Res.font.nunito_semibold, FontWeight.SemiBold),
    Font(Res.font.nunito_bold, FontWeight.Bold),
    Font(Res.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(Res.font.nunito_black, FontWeight.Black),
)

@Composable
fun displayStyle(size: TextUnit, color: Color = Ink): TextStyle = TextStyle(
    fontFamily = displayFamily(),
    fontSize = size,
    color = color,
    letterSpacing = 0.5.sp,
)

/**
 * Text with a thick ink outline drawn behind the fill, plus an optional hard shadow:
 * the "sticker" look of every title in the game.
 */
@Composable
fun OutlinedText(
    text: String,
    style: TextStyle,
    fill: Color,
    modifier: Modifier = Modifier,
    outline: Color = Ink,
    outlineWidth: Dp = 4.dp,
    shadow: Dp = 0.dp,
    halo: Color? = null,
    haloWidth: Dp = outlineWidth * 2.2f,
    shadowColor: Color = outline,
) {
    val density = LocalDensity.current
    val stroke = remember(outlineWidth, density) { Stroke(width = with(density) { outlineWidth.toPx() }, join = StrokeJoin.Round) }
    // The halo is a wider stroke behind the ink one: the white die-cut edge of a real sticker.
    val haloStroke = remember(haloWidth, density) { Stroke(width = with(density) { haloWidth.toPx() }, join = StrokeJoin.Round) }
    val edge = if (halo != null) haloStroke else stroke
    Box(modifier) {
        if (shadow > 0.dp) {
            Text(text, style = style.copy(color = shadowColor, drawStyle = edge), modifier = Modifier.offset(y = shadow))
        }
        if (halo != null) Text(text, style = style.copy(color = halo, drawStyle = haloStroke))
        Text(text, style = style.copy(color = outline, drawStyle = stroke))
        Text(text, style = style.copy(color = fill))
    }
}

// ─── Surfaces ────────────────────────────────────────────────────────────────

/** A flat panel with an ink outline and a hard shadow. */
@Composable
fun ArcadePanel(
    modifier: Modifier = Modifier,
    fill: Color = Cream,
    shape: Shape = RoundedCornerShape(18.dp),
    shadow: Dp = ArcadeShadow,
    content: @Composable () -> Unit,
) {
    Box(modifier, propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().padding(top = shadow, start = shadow / 2).clip(shape).background(Ink))
        Box(
            Modifier
                .padding(bottom = shadow, end = shadow / 2)
                .clip(shape)
                .background(fill)
                .border(ArcadeOutline, Ink, shape),
        ) { content() }
    }
}

enum class ArcadeSize(val textSize: TextUnit, val padding: PaddingValues, val icon: Dp) {
    Large(22.sp, PaddingValues(horizontal = 30.dp, vertical = 12.dp), 24.dp),
    Medium(17.sp, PaddingValues(horizontal = 20.dp, vertical = 10.dp), 20.dp),
    Small(13.sp, PaddingValues(horizontal = 14.dp, vertical = 7.dp), 16.dp),
}

/**
 * The chunky arcade button. Pressing it pushes the face down onto its shadow,
 * so it feels like a physical key.
 */
@Composable
fun ArcadeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Banana,
    contentColor: Color = Ink,
    icon: ArcadeIcon? = null,
    subtitle: String? = null,
    size: ArcadeSize = ArcadeSize.Medium,
    shape: Shape = RoundedCornerShape(16.dp),
    enabled: Boolean = true,
) {
    ArcadePressable(onClick = onClick, modifier = modifier, fill = fill, shape = shape, enabled = enabled) {
        Row(
            modifier = Modifier.padding(size.padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) ArcadeIconView(icon, tint = contentColor, size = size.icon)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = displayStyle(size.textSize, contentColor))
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = contentColor.copy(alpha = 0.8f),
                        fontSize = (size.textSize.value * 0.5f).coerceAtLeast(10f).sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

/** Round icon-only arcade button (top bars, close buttons). */
@Composable
fun ArcadeIconButton(
    icon: ArcadeIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Cream,
    tint: Color = Ink,
    size: Dp = 40.dp,
) {
    ArcadePressable(onClick = onClick, modifier = modifier, fill = fill, shape = CircleShape, depth = 4.dp) {
        Box(Modifier.size(size - ArcadeOutline), contentAlignment = Alignment.Center) {
            ArcadeIconView(icon, tint = tint, size = size * 0.5f)
        }
    }
}

/** Played by every arcade button press (App.kt provides the game's button sound). */
val LocalButtonSound = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/** Shared press mechanics: a face sitting on a hard shadow that it sinks into when pressed. */
@Composable
fun ArcadePressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Cream,
    shape: Shape = RoundedCornerShape(16.dp),
    depth: Dp = ArcadeShadow,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonSound = LocalButtonSound.current
    val sink by animateDpAsState(if (pressed) depth - 1.dp else 0.dp, spring(stiffness = 2200f), label = "press")
    // propagateMinConstraints: a full-width button stretches its face, not just its shadow.
    Box(modifier, propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().padding(top = depth).clip(shape).background(Ink))
        Box(
            Modifier
                .padding(bottom = depth)
                .offset(y = sink)
                .clip(shape)
                .background(if (enabled) fill else CreamShade)
                .border(ArcadeOutline, Ink, shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                    buttonSound()
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
