package com.snakechase.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadePressable
import com.snakechase.app.ui.theme.ArcadeScreenHeader
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.CoinWallet
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle

// ─── 1. Mode Select View ─────────────────────────────────────────────────────

@Composable
internal fun DuelModeSelectView(
    isCompact: Boolean,
    coins: Int?,
    isOffline: Boolean,
    /** This device can host same-Wi-Fi rooms without internet (Android, desktop). */
    lanAvailable: Boolean = false,
    selectedTierIndex: Int,
    onSelectTier: (Int) -> Unit,
    onSelectMode: (DuelMode) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isCompact) 18.dp else 24.dp, vertical = if (isCompact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArcadeScreenHeader(
            title = "1v1 Duel",
            subtitle = "3 rounds · roles flip · best chase wins",
            onBack = onBack,
            compact = isCompact,
            trailing = { CoinWallet(coins, isOffline) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuelModeCard("Vs AI rival", "Smart AI", "Face a predictive AI that reads your moves.", ArcadeIcon.Robot, Lime, isCompact, Modifier.weight(1f)) {
                onSelectMode(DuelMode.VS_AI_RIVAL)
            }
            // Offline, only same-Wi-Fi rooms work, and only where the device can host one.
            val inviteUsable = !isOffline || lanAvailable
            DuelModeCard(
                "Friend invite",
                if (!inviteUsable) {
                    "Needs internet"
                } else if (isOffline) {
                    "Same Wi-Fi"
                } else {
                    "Room code"
                },
                when {
                    !inviteUsable -> "Connect to the internet to play a friend online."
                    isOffline -> "Offline: play a friend on the same Wi-Fi."
                    else -> "Make or join a room code. Play live across phone, PC and web."
                },
                ArcadeIcon.Link,
                if (inviteUsable) Banana else Cream,
                isCompact,
                Modifier.weight(1f),
            ) {
                if (inviteUsable) onSelectMode(DuelMode.LOCAL_INVITE_CHALLENGE)
            }
            DuelModeCard("Pass and play", "One screen", "Couch duel on one device: left half vs right half.", ArcadeIcon.Users, Berry, isCompact, Modifier.weight(1f)) {
                onSelectMode(DuelMode.LOCAL_PASS_AND_PLAY)
            }
            DuelModeCard(
                "Online arena",
                if (isOffline) "Needs internet" else "Worldwide",
                if (isOffline) "Connect to the internet to duel players worldwide." else "Quick match or ghost duels against real players.",
                ArcadeIcon.Globe,
                if (isOffline) Cream else Sky,
                isCompact,
                Modifier.weight(1f),
            ) {
                if (!isOffline) onSelectMode(DuelMode.ONLINE_SERVER)
            }
        }

        Text(
            "Hunt as the snake: it follows your pointer · flee as the prey to survive",
            color = Moss,
            fontSize = if (isCompact) 11.sp else 13.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** A duel mode as a sticker card: lifts and tilts toward you on hover. */
@Composable
internal fun DuelModeCard(
    title: String,
    badge: String,
    description: String,
    icon: ArcadeIcon,
    accentColor: Color,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 350f), label = "lift")
    ArcadePressable(
        onClick = onClick,
        fill = Cream,
        shape = RoundedCornerShape(22.dp),
        depth = 6.dp,
        modifier = modifier
            .hoverable(hover)
            .graphicsLayer {
                scaleX = 1f + lift * 0.06f
                scaleY = 1f + lift * 0.06f
                rotationX = lift * 6f
                translationY = -lift * 8f * density
                cameraDistance = 14f * density
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(if (isCompact) 170.dp else 230.dp)
                .padding(if (isCompact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(if (isCompact) 40.dp else 52.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .border(3.dp, Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ArcadeIconView(icon, size = if (isCompact) 22.dp else 28.dp)
                }
                Box(Modifier.weight(1f))
                ArcadeTag(badge, accentColor)
            }
            Column {
                Text(title, style = displayStyle(if (isCompact) 17.sp else 22.sp))
                Text(
                    description,
                    color = Ink.copy(alpha = 0.75f),
                    fontSize = if (isCompact) 11.sp else 13.sp,
                    lineHeight = if (isCompact) 14.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enter duel", style = displayStyle(if (isCompact) 13.sp else 15.sp, Ink))
                Box(Modifier.weight(1f))
                ArcadeIconView(ArcadeIcon.Play, size = 16.dp)
            }
        }
    }
}
