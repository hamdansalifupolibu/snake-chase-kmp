package com.snakechase.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream

@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onOpenSettings: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit,
) {
    ArcadeDialog(title = "Paused", icon = ArcadeIcon.Pause, onClose = onResume, width = 340.dp) {
        val wide = Modifier.fillMaxWidth()
        ArcadeButton("Resume", icon = ArcadeIcon.Play, fill = Banana, onClick = onResume, modifier = wide)
        ArcadeButton("Settings", icon = ArcadeIcon.Gear, fill = Cream, onClick = onOpenSettings, modifier = wide)
        ArcadeButton("Restart level", icon = ArcadeIcon.Refresh, fill = Cream, onClick = onRestart, modifier = wide)
        ArcadeButton("Quit to menu", icon = ArcadeIcon.Home, fill = Berry, onClick = onQuit, modifier = wide)
    }
}
