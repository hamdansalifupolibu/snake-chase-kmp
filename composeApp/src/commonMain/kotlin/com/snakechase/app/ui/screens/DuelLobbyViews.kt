package com.snakechase.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.ARENA_TIERS
import com.snakechase.app.domain.model.MultiplayerState
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeCard
import com.snakechase.app.ui.theme.ArcadeChip
import com.snakechase.app.ui.theme.ArcadeChoiceRow
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeSize
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.ArcadeTextField
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.JungleDeep
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle

// ─── 2. Challenge Invite View (Host & Join Room Tabs) ─────────────────────────

@Composable
internal fun DuelChallengeInviteView(
    state: MultiplayerState,
    isCompact: Boolean,
    localHostIp: String,
    connectionStatusText: String?,
    canHost: Boolean,
    online: Boolean,
    canGoOnline: Boolean,
    onSelectOnline: (Boolean) -> Unit,
    onStartHostServer: () -> Unit,
    onShowJoinTab: () -> Unit,
    onJoinWithIpAndCode: (String, String) -> Unit,
    onStartMatch: () -> Unit,
    onBack: () -> Unit,
    /** Watch an online room by code (null hides the WATCH button). */
    onWatch: ((String) -> Unit)? = null,
    /** Nearby (same Wi-Fi) rooms need a device that can host one: Android and desktop, not the web. */
    nearbyAvailable: Boolean = true,
) {
    // Tab comes from session state (survives recomposition). Network actions run in the
    // click handlers below — never as a LaunchedEffect side effect of a remembered index.
    val selectedTab = if (state.lobbyShowsJoin || !canHost) 1 else 0
    var inputCode by remember { mutableStateOf("") }
    var inputHostIp by remember { mutableStateOf("") }
    var copiedCode by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    ArcadeCard(title = "Duel lobby", icon = ArcadeIcon.Link, onClose = onBack, width = 560.dp) {
        // Host / join tabs
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArcadeChip(
                "Host a room",
                fill = if (selectedTab == 0) Banana else JungleDeep,
                textColor = if (selectedTab == 0) Ink else Cream,
                onClick = if (canHost && selectedTab != 0) onStartHostServer else null,
                modifier = Modifier.weight(1f),
            )
            ArcadeChip(
                "Join with a code",
                fill = if (selectedTab == 1) Lime else JungleDeep,
                textColor = if (selectedTab == 1) Ink else Cream,
                onClick = if (selectedTab != 1) onShowJoinTab else null,
                modifier = Modifier.weight(1f),
            )
        }
        if (canGoOnline && nearbyAvailable) {
            ArcadeChoiceRow(
                options = listOf(true, false),
                selected = online,
                label = { if (it) "Online · any device" else "Nearby · no internet" },
                onSelect = onSelectOnline,
                selectedFill = Sky,
            )
        }

        if (selectedTab == 0) {
            Text(
                if (online) "Share this code. Your friend joins from a phone, PC or browser." else "Same Wi-Fi: share the code and your IP ($localHostIp).",
                color = Cream,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // The code, big, as a cream sticker.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Cream)
                    .border(3.dp, Ink, RoundedCornerShape(18.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.roomCode.ifEmpty { "······" }, style = displayStyle(if (isCompact) 32.sp else 40.sp).copy(letterSpacing = 6.sp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArcadeButton(
                    if (copiedCode) "Copied!" else "Copy code",
                    icon = if (copiedCode) ArcadeIcon.Check else ArcadeIcon.Share,
                    fill = Lime,
                    size = ArcadeSize.Small,
                    enabled = state.roomCode.isNotEmpty(),
                    onClick = {
                        clipboard.setText(AnnotatedString(if (online) state.roomCode else "${state.roomCode} @ $localHostIp"))
                        copiedCode = true
                    },
                )
                ArcadeInset(Modifier.weight(1f), highlight = if (state.isRoomActivated) Lime else Banana) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.isRoomActivated) Lime else Banana).border(2.dp, Ink, CircleShape))
                    Text(
                        if (state.isRoomActivated) "${state.player2Name} is here!" else (connectionStatusText ?: "Waiting for your friend…"),
                        color = Cream,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            val creating = connectionStatusText?.startsWith("Creating") == true || connectionStatusText?.startsWith("Opening") == true
            if (state.roomCode.isEmpty() && !creating) {
                // The room couldn't be opened (already retried): offer another go, never a dead end.
                ArcadeButton(
                    "Try again",
                    icon = ArcadeIcon.Refresh,
                    fill = Banana,
                    onClick = onStartHostServer,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                ArcadeButton(
                    "Start duel",
                    icon = ArcadeIcon.Swords,
                    fill = Banana,
                    // Only once the room exists and a friend has joined it.
                    enabled = state.roomCode.isNotEmpty() && state.isRoomActivated,
                    onClick = onStartMatch,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        } else {
            Text(
                if (online) "Enter the code your friend shared. Works across phone, PC and browser." else "Same Wi-Fi, no internet: enter the host's IP and code.",
                color = Cream,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!online) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("10.0.2.2" to "Emulator to PC", "127.0.0.1" to "Same device").forEach { (ip, label) ->
                        ArcadeChip(
                            label,
                            fill = if (inputHostIp == ip) Lime else JungleDeep,
                            textColor = if (inputHostIp == ip) Ink else Cream,
                            onClick = { inputHostIp = ip },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!online) {
                    ArcadeTextField(
                        value = inputHostIp,
                        onValueChange = { v -> inputHostIp = v.filter { it.isDigit() || it == '.' }.take(15) },
                        label = "Host IP",
                        placeholder = "192.168.1.23",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1.6f),
                    )
                }
                ArcadeTextField(
                    value = inputCode,
                    onValueChange = { v -> inputCode = v.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                    label = "Room code",
                    placeholder = if (online) "K7Q4MZ" else "0000",
                    keyboardType = if (online) KeyboardType.Ascii else KeyboardType.NumberPassword,
                    modifier = Modifier.weight(1f),
                )
            }
            if (connectionStatusText != null) {
                ArcadeInset(Modifier.fillMaxWidth(), highlight = Sky) {
                    Text(connectionStatusText, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                ArcadeButton("Join duel", icon = ArcadeIcon.Play, fill = Banana, onClick = { onJoinWithIpAndCode(inputHostIp, inputCode) })
                // Watch someone else's online duel without taking part.
                if (online && onWatch != null) {
                    ArcadeButton("Watch", icon = ArcadeIcon.Eye, fill = Sky, onClick = { onWatch(inputCode) })
                }
            }
        }
    }
}

// ─── 3. Versus Matchmaking & Tier Selection ───────────────────────────────────

@Composable
internal fun DuelVersusCardView(
    state: MultiplayerState,
    isCompact: Boolean,
    onSelectTier: (Int) -> Unit,
    onCancel: () -> Unit,
    onStartMatch: () -> Unit,
) {
    val selectedTier = ARENA_TIERS.getOrElse(state.selectedTierIndex) { ARENA_TIERS[0] }
    val tierColor = Color(selectedTier.accentColorHex)

    ArcadeCard(title = "Pick your stakes", icon = ArcadeIcon.Coin, onClose = onCancel, width = 600.dp, accent = tierColor) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ARENA_TIERS.forEachIndexed { index, tier ->
                val on = state.selectedTierIndex == index
                val c = Color(tier.accentColorHex)
                ArcadeInset(highlight = if (on) c else null, onClick = { onSelectTier(index) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tier.name, style = displayStyle(14.sp, if (on) Cream else Moss))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArcadeIconView(ArcadeIcon.Coin, size = 14.dp)
                            Text(" ${tier.entryFee}", style = displayStyle(13.sp, c))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ArcadeIconView(ArcadeIcon.Crown, tint = Banana, size = 30.dp)
                Text(state.player1Name, style = displayStyle(16.sp, Cream))
                ArcadeTag("Entry ${selectedTier.entryFee}", Banana)
            }
            OutlinedText("VS", displayStyle(34.sp), fill = Berry, outlineWidth = 6.dp, shadow = 3.dp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ArcadeIconView(ArcadeIcon.User, tint = Moss, size = 30.dp)
                Text("Searching…", style = displayStyle(16.sp, Moss))
                ArcadeTag("Entry ${selectedTier.entryFee}", Banana)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Winner takes ", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            ArcadeIconView(ArcadeIcon.Coin, size = 18.dp)
            Text(" ${selectedTier.prizePool}", style = displayStyle(17.sp, Banana))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)) {
            ArcadeButton("Start duel", icon = ArcadeIcon.Swords, fill = Banana, onClick = onStartMatch)
            ArcadeButton("Cancel", fill = Cream, onClick = onCancel)
        }
    }
}
