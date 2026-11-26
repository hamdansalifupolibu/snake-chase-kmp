package com.snakechase.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

private val DarkColorScheme = darkColorScheme(
    primary = Lime,
    secondary = Tangerine,
    tertiary = Banana,
    background = JungleNight,
    surface = Jungle,
    onPrimary = Ink,
    onSecondary = Ink,
    onBackground = Cream,
    onSurface = Cream,
)

/** Every text style uses the rounded body face, so screens not yet restyled still match. */
private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@Composable
fun SnakeChaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography().withFamily(bodyFamily()),
        content = content,
    )
}
