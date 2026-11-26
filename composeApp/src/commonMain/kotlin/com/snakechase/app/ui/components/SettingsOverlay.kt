package com.snakechase.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.Difficulty
import com.snakechase.app.domain.model.JoystickSide
import com.snakechase.app.domain.model.ParticleQuality
import com.snakechase.app.domain.model.SteeringMode
import com.snakechase.app.domain.model.UserSettings
import com.snakechase.app.platform.isKeyboardPrimary
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeChoiceRow
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeSectionTitle
import com.snakechase.app.ui.theme.ArcadeSize
import com.snakechase.app.ui.theme.ArcadeSlider
import com.snakechase.app.ui.theme.ArcadeToggle
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.displayStyle
import kotlin.math.roundToInt

@Composable
fun SettingsOverlay(
    settings: UserSettings,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onResetProgress: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    ArcadeDialog(title = "Settings", icon = ArcadeIcon.Gear, onClose = onClose, width = 760.dp, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingsColumn {
                ArcadeSectionTitle("Sound")
                ToggleRow("Sound and effects", settings.soundEnabled) { on -> onUpdateSettings { it.copy(soundEnabled = on) } }
                ToggleRow("Meme roasts when you lose", settings.memeRoasts) { on -> onUpdateSettings { it.copy(memeRoasts = on) } }
                VolumeRow("Master volume", settings.masterVolume, settings.soundEnabled) { v -> onUpdateSettings { it.copy(masterVolume = v) } }
                VolumeRow("Music", settings.bgmVolume, settings.soundEnabled) { v -> onUpdateSettings { it.copy(bgmVolume = v) } }
                VolumeRow("Sound effects", settings.sfxVolume, settings.soundEnabled) { v -> onUpdateSettings { it.copy(sfxVolume = v) } }

                ArcadeSectionTitle("Visuals")
                ArcadeChoiceRow(
                    options = listOf(ParticleQuality.HIGH, ParticleQuality.LOW),
                    selected = settings.particleQuality,
                    label = { if (it == ParticleQuality.HIGH) "Ultra particles" else "Battery saver" },
                    onSelect = { q -> onUpdateSettings { it.copy(particleQuality = q) } },
                )
            }
            SettingsColumn {
                ArcadeSectionTitle("Controls")
                ControlsPicker(settings, onUpdateSettings)
                ArcadeSectionTitle("Difficulty")
                ArcadeChoiceRow(
                    options = Difficulty.entries,
                    selected = settings.difficulty,
                    label = { it.label },
                    onSelect = { d -> onUpdateSettings { it.copy(difficulty = d, difficultyChosen = true) } },
                )
                Text(
                    text = settings.difficulty.blurb + if (settings.difficulty.countsOnline) "" else " Easy runs stay off the online leaderboard.",
                    color = Moss,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                ToggleRow("Vibration on near-miss and hit", settings.hapticsEnabled) { on -> onUpdateSettings { it.copy(hapticsEnabled = on) } }

                ArcadeSectionTitle("Progress", color = Berry)
                if (!showResetConfirm) {
                    ArcadeButton(
                        text = "Reset progress",
                        fill = Berry,
                        size = ArcadeSize.Small,
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ArcadeInset(highlight = Berry) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Wipe every score, star and unlock? This can't be undone.", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ArcadeButton(
                                    text = "Reset",
                                    fill = Berry,
                                    size = ArcadeSize.Small,
                                    onClick = {
                                        onResetProgress()
                                        showResetConfirm = false
                                    },
                                )
                                ArcadeButton(text = "Keep it", fill = Cream, size = ArcadeSize.Small, onClick = { showResetConfirm = false })
                            }
                        }
                    }
                }
            }
        }
        ArcadeButton(
            text = "Done",
            icon = ArcadeIcon.Play,
            fill = Banana,
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

/**
 * How the player steers. "Keyboard" is only offered where there is one (desktop, laptop browser);
 * the thumbstick side picker only appears for the stick.
 */
@Composable
private fun ControlsPicker(settings: UserSettings, onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit) {
    val modes = buildList {
        add(SteeringMode.POINTER)
        if (isKeyboardPrimary) add(SteeringMode.KEYBOARD)
        add(SteeringMode.VIRTUAL_STICK)
    }
    ArcadeChoiceRow(
        options = modes,
        selected = settings.steeringMode,
        label = {
            when (it) {
                SteeringMode.POINTER -> if (isKeyboardPrimary) "Mouse" else "Touch"
                SteeringMode.KEYBOARD -> "Keyboard"
                SteeringMode.VIRTUAL_STICK -> "Joystick"
            }
        },
        onSelect = { mode -> onUpdateSettings { it.copy(steeringMode = mode) } },
    )
    Text(
        text = when (settings.steeringMode) {
            SteeringMode.POINTER -> if (isKeyboardPrimary) "Point to steer: the orb follows your cursor. WASD and arrow keys work too." else "Drag to steer."
            SteeringMode.KEYBOARD -> "WASD or arrow keys steer, Space dashes. Clicks in the arena are ignored."
            SteeringMode.VIRTUAL_STICK -> "An on-screen thumbstick in the corner. Touches in the arena are ignored."
        },
        color = Moss,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    if (settings.steeringMode == SteeringMode.VIRTUAL_STICK) {
        ArcadeChoiceRow(
            options = listOf(JoystickSide.LEFT, JoystickSide.RIGHT),
            selected = settings.joystickSide,
            label = { if (it == JoystickSide.LEFT) "Left thumb" else "Right thumb" },
            onSelect = { side -> onUpdateSettings { it.copy(joystickSide = side) } },
            selectedFill = Lime,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ArcadeInset(Modifier.fillMaxWidth(), onClick = { onChange(!checked) }) {
        Text(label, color = Cream, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        ArcadeToggle(checked, onChange)
    }
}

@Composable
private fun VolumeRow(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (enabled) Cream else Moss, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${(value * 100f).roundToInt()}%", style = displayStyle(15.sp, if (enabled) Banana else Moss))
        }
        ArcadeSlider(value = value, onValueChange = onChange, enabled = enabled)
    }
}
