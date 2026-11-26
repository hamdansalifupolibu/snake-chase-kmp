package com.snakechase.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeSectionTitle
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.Tangerine
import com.snakechase.app.ui.theme.displayStyle

@Composable
fun AboutOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArcadeDialog(title = "About", icon = ArcadeIcon.Info, onClose = onClose, width = 620.dp, accent = Lime, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            ArcadeTag("Android", Lime)
            ArcadeTag("iOS (compiles)", Cream)
            ArcadeTag("Desktop", Sky)
            ArcadeTag("Web", Tangerine)
        }
        ArcadeInset(Modifier.fillMaxWidth()) {
            Text(
                "Built for the Kotlin Multiplatform Contest 2027. One shared Kotlin codebase drives every platform: " +
                    "Compose Multiplatform UI, the game engine and AI, and online play through Firebase over Ktor.",
                color = Cream,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AboutSection("The game") {
            "You're a glowing orb in five living worlds, hunted by snakes that read your moves. Graze past them to build " +
                "combos, clear ${com.snakechase.app.levels.LevelRegistry.ALL_LEVELS.sumOf { it.subStages.size }} stages, duel friends online, or flip roles and hunt as the snake."
        }
        AboutSection("Made by") {
            "Hamdan Salifu Polibu: Computer Science and Engineering, University of Mines and Technology (UMaT). " +
                "Interests: game engines, AI and multiplatform systems."
        }
        AboutSection("Sound") {
            "Main soundtrack by Eclipse Alegend. Countdown, near-miss whoosh, snake hiss and game-over sounds, " +
                "plus a playful roast soundboard after a streak of losses."
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ArcadeSectionTitle("Get in touch", color = Banana)
            Text("hamdansalifupolibu@gmail.com", style = displayStyle(15.sp, Banana))
        }
        Text("© 2027 Snake Chase · Hamdan Salifu Polibu", color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        ArcadeButton(text = "Done", fill = Banana, onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun AboutSection(title: String, body: () -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ArcadeSectionTitle(title)
        Text(body(), color = Cream.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
