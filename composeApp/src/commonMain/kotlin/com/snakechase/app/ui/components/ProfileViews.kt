package com.snakechase.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.profile.AVATAR_COLORS
import com.snakechase.app.profile.DailyClaim
import com.snakechase.app.profile.Economy
import com.snakechase.app.profile.PlayerProfile
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeTextField
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.displayStyle

/** The player's sticker avatar: their colour, their initial, an ink outline. No images to load. */
@Composable
fun AvatarBadge(name: String, avatar: Int, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val color = Color(AVATAR_COLORS[avatar.mod(AVATAR_COLORS.size)])
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border((size.value * 0.08f).coerceAtLeast(2f).dp, Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", style = displayStyle((size.value * 0.5f).sp))
    }
}

/**
 * Name + avatar editor. Shown on first launch (no way to skip: the name is what rivals and
 * the leaderboard see) and later from the profile card.
 */
@Composable
fun ProfileEditor(
    profile: PlayerProfile,
    firstTime: Boolean,
    onSave: (name: String, avatar: Int) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(profile.name) }
    var avatar by remember { mutableIntStateOf(profile.avatar) }
    val clean = Economy.cleanName(name)
    ArcadeDialog(
        title = if (firstTime) "Welcome, chaser" else "Edit profile",
        icon = ArcadeIcon.User,
        onClose = onClose,
        width = 460.dp,
        accent = Lime,
        dismissOnScrim = onClose != null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AvatarBadge(clean.ifEmpty { "?" }, avatar, size = 58.dp)
            Text(
                if (firstTime) "Pick a name and a colour. That's it: no account, no password." else "This is how rivals and the leaderboard see you.",
                color = Cream,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        ArcadeTextField(value = name, onValueChange = { name = it.take(Economy.MAX_NAME + 4) }, label = "Your name", placeholder = "Chaser")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            AVATAR_COLORS.indices.forEach { i ->
                val selected = i == avatar
                Box(
                    modifier = Modifier
                        .size(if (selected) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(Color(AVATAR_COLORS[i]))
                        .border(if (selected) 4.dp else 2.5.dp, if (selected) Cream else Ink, CircleShape)
                        .clickable { avatar = i },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            if (onClose != null) ArcadeButton("Cancel", fill = Cream, onClick = onClose)
            ArcadeButton(
                if (firstTime) "Let's play" else "Save",
                icon = if (firstTime) ArcadeIcon.Play else null,
                fill = Banana,
                enabled = clean.length >= 2,
                onClick = { onSave(clean, avatar) },
            )
        }
        if (clean.length < 2) {
            Text("At least 2 letters", color = Moss, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** The once-a-day coin gift, with the week's streak laid out as coins to collect. */
@Composable
fun DailyRewardDialog(claim: DailyClaim, onClose: () -> Unit) {
    ArcadeDialog(title = "Daily reward", icon = ArcadeIcon.Calendar, onClose = onClose, width = 460.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            ArcadeIconView(ArcadeIcon.Coin, size = 52.dp)
            OutlinedText("+${claim.coins}", displayStyle(54.sp), fill = Banana, outlineWidth = 7.dp, shadow = 4.dp)
        }
        Text("Day ${claim.streakDay} streak", style = displayStyle(18.sp, Cream), modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            val weekStart = ((claim.streakDay - 1) / 7) * 7
            for (d in 1..7) {
                val day = weekStart + d
                val done = day <= claim.streakDay
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (done) Banana else Ink.copy(alpha = 0.4f))
                        .border(2.5.dp, if (day == claim.streakDay) Cream else Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$day", style = displayStyle(12.sp, if (done) Ink else Moss))
                }
            }
        }
        val tomorrow = Economy.dailyReward(claim.streakDay + 1)
        Text(
            "Come back tomorrow for +$tomorrow. Miss a day and the streak restarts.",
            color = Cream.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        ArcadeButton("Collect", icon = ArcadeIcon.Coin, fill = Banana, onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Thin strip shown while the device can't reach the internet. */
@Composable
fun OfflineBanner(pendingUploads: Int, modifier: Modifier = Modifier) {
    Text(
        "You're offline. Levels 1-5 still work; connect to play online" + if (pendingUploads > 0) " · $pendingUploads result(s) will upload later" else "",
        style = displayStyle(13.sp, Ink),
        modifier = modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Berry)
            .border(2.5.dp, Ink, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}
