package com.snakechase.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.ARENA_TIERS
import com.snakechase.app.domain.model.MultiplayerState
import com.snakechase.app.network.QuickMatch
import com.snakechase.app.online.InboxResult
import com.snakechase.app.online.OnlineOpponent
import com.snakechase.app.online.OnlineRun
import com.snakechase.app.online.OnlineVerdict
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeCard
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeSectionTitle
import com.snakechase.app.ui.theme.ArcadeSize
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Berry
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle
import com.snakechase.app.util.formatSeconds

/** UI state of the online arena for one search → match → result cycle. */
data class OnlineUi(
    val searching: Boolean = false,
    val status: String? = null,
    /** The claimed challenge, or null when playing the house benchmark. */
    val opponent: OnlineOpponent? = null,
    val myRun: OnlineRun? = null,
    val verdict: OnlineVerdict? = null,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val submitError: String? = null,
    /** Run saved on the device; it uploads automatically when the internet returns. */
    val queued: Boolean = false,
    /** Coins won (+) or lost (−) on this match's stake, shown on the result card. */
    val coinsDelta: Int? = null,
    val inbox: List<InboxResult> = emptyList(),
)

/** The rival ghost to draw this frame: their prey path in round 1, their snake head in round 2. */
internal fun onlineGhostFor(state: MultiplayerState, online: OnlineUi): GhostOverlay {
    val t = state.survivalTimeMs
    val opponent = online.opponent
    val fleeing = state.round == 1
    if (opponent == null) {
        val target = if (fleeing) "survive past ${secs(OnlineRun.HOUSE.fleeMs)}" else "catch it under ${secs(OnlineRun.HOUSE.huntMs)}"
        return GhostOverlay(position = null, isSnake = !fleeing, label = "${OnlineRun.HOUSE_NAME}: $target")
    }
    val track = if (fleeing) opponent.run.ghostFlee else opponent.run.ghostHunt
    val theirMs = if (fleeing) opponent.run.fleeMs else opponent.run.huntMs
    val done = t >= theirMs
    val label = when {
        !done -> "${opponent.name}'s ghost"
        fleeing -> "${opponent.name} was caught at ${secs(theirMs)}. Keep going!"
        else -> "${opponent.name} caught theirs at ${secs(theirMs)}"
    }
    return GhostOverlay(position = if (done) null else track.positionAt(t), isSnake = !fleeing, label = label)
}

internal fun secs(ms: Long): String = "${formatSeconds(ms)}s"

@Composable
internal fun OnlineArenaLobby(
    selectedTierIndex: Int,
    playerName: String,
    online: OnlineUi,
    isCompact: Boolean,
    onSelectTier: (Int) -> Unit,
    onFindOpponent: () -> Unit,
    onBack: () -> Unit,
    quickMatchStatus: QuickMatch.Status? = null,
    onQuickMatch: () -> Unit = {},
    onAcceptMatch: () -> Unit = {},
    onDeclineMatch: () -> Unit = {},
    onCancelQuickMatch: () -> Unit = {},
    coins: Int = Int.MAX_VALUE,
    isOffline: Boolean = false,
) {
    val tier = ARENA_TIERS[selectedTierIndex]
    val canAfford = coins >= tier.entryFee
    ArcadeCard(title = "Online arena", icon = ArcadeIcon.Globe, onClose = onBack, width = 640.dp, accent = Sky) {
        ArcadeSectionTitle("Stakes")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ARENA_TIERS.forEachIndexed { i, t ->
                val on = i == selectedTierIndex
                val c = Color(t.accentColorHex)
                ArcadeInset(highlight = if (on) c else null, onClick = if (online.searching) null else ({ onSelectTier(i) })) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.name, style = displayStyle(14.sp, if (on) Cream else Moss))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArcadeIconView(ArcadeIcon.Coin, size = 13.dp)
                            Text(" ${t.entryFee} → ${t.prizePool}", style = displayStyle(12.sp, if (coins >= t.entryFee) Banana else Moss))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Playing as ", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(playerName, style = displayStyle(14.sp, Lime))
            if (coins != Int.MAX_VALUE) {
                Text("  ·  ", color = Moss, fontSize = 13.sp)
                ArcadeIconView(ArcadeIcon.Coin, size = 15.dp)
                Text(" $coins", style = displayStyle(14.sp, Banana))
            }
        }

        when {
            isOffline -> Warning("You're offline. Online duels need internet; VS AI, pass and play and nearby still work.")
            !canAfford -> Warning("Not enough coins for this arena. Pick a cheaper tier, earn more in single player, or grab tomorrow's daily reward.")
        }
        online.status?.let { if (online.searching) Info(it) else Warning(it) }

        QuickMatchPanel(quickMatchStatus, onAcceptMatch, onDeclineMatch, onCancelQuickMatch)

        val qmBusy = quickMatchStatus != null &&
            quickMatchStatus !is QuickMatch.Status.Idle &&
            quickMatchStatus !is QuickMatch.Status.Failed
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            if (quickMatchStatus != null) {
                ArcadeButton(
                    "Quick match",
                    subtitle = "A live player, right now",
                    icon = ArcadeIcon.Bolt,
                    fill = Banana,
                    enabled = !qmBusy && !online.searching && !isOffline && canAfford,
                    onClick = onQuickMatch,
                )
            }
            ArcadeButton(
                if (online.searching) "Searching…" else "Ghost duel",
                subtitle = "A real player's recorded run",
                icon = ArcadeIcon.Eye,
                fill = Sky,
                enabled = !online.searching && !qmBusy && !isOffline && canAfford,
                onClick = onFindOpponent,
            )
        }

        if (online.inbox.isNotEmpty()) {
            ArcadeSectionTitle("Your challenges", icon = ArcadeIcon.Trophy, color = Banana)
            online.inbox.take(5).forEach { r ->
                ArcadeInset(Modifier.fillMaxWidth(), highlight = if (r.iWon) Banana else null) {
                    ArcadeTag(if (r.iWon) "Won" else "Lost", if (r.iWon) Banana else Berry)
                    Column(Modifier.weight(1f)) {
                        Text("vs ${r.opponentName}", style = displayStyle(14.sp, Cream))
                        Text(
                            "Flee ${secs(r.myRun.fleeMs)} vs ${secs(r.theirRun.fleeMs)} · hunt ${secs(r.myRun.huntMs)} vs ${secs(r.theirRun.huntMs)}",
                            color = Moss,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Warning(text: String) {
    ArcadeInset(Modifier.fillMaxWidth(), highlight = Berry) {
        ArcadeIconView(ArcadeIcon.Wifi, tint = Berry, size = 18.dp)
        Text(text, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Info(text: String) {
    ArcadeInset(Modifier.fillMaxWidth(), highlight = Sky) {
        Text(text, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Live quick-match progress: searching, the accept/decline offer, or a failure. */
@Composable
private fun QuickMatchPanel(
    status: QuickMatch.Status?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
) {
    when (status) {
        null, QuickMatch.Status.Idle -> Unit
        is QuickMatch.Status.Searching -> ArcadeInset(Modifier.fillMaxWidth(), highlight = Sky) {
            Column(Modifier.weight(1f)) {
                Text("Looking for a live opponent… ${status.waitedSec}s", style = displayStyle(15.sp, Cream))
                if (status.nobodyOnline) {
                    Text("Quiet right now. Try a ghost duel; we'll keep searching.", color = Moss, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            ArcadeButton("Cancel", fill = Cream, size = ArcadeSize.Small, onClick = onCancel)
        }
        // The offer pops up over everything: inside the scrolling arena card it could sit below
        // the bottom of a phone screen, where nobody scrolls to it within the 5 s to accept.
        is QuickMatch.Status.Offer -> androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            ArcadeCard(title = "Match found", icon = ArcadeIcon.Swords, width = 420.dp, accent = Banana) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedText("${status.opponentName} found!", displayStyle(22.sp), fill = Banana, outlineWidth = 5.dp)
                    Text(
                        when {
                            status.iAccepted && !status.theyAccepted -> "Waiting for ${status.opponentName}… ${status.secondsLeft}s"
                            status.theyAccepted && !status.iAccepted -> "${status.opponentName} is ready! Accept? ${status.secondsLeft}s"
                            else -> "Accept the duel? ${status.secondsLeft}s"
                        },
                        color = Cream,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!status.iAccepted) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ArcadeButton("Accept", icon = ArcadeIcon.Check, fill = Lime, onClick = onAccept)
                            ArcadeButton("Decline", icon = ArcadeIcon.Close, fill = Berry, onClick = onDecline)
                        }
                    }
                }
            }
        }
        is QuickMatch.Status.Matched -> Info("Connecting to ${status.opponentName}…")
        is QuickMatch.Status.Failed -> Warning(status.message)
    }
}

@Composable
internal fun OnlineResultView(
    online: OnlineUi,
    myName: String,
    isCompact: Boolean,
    onRetryUpload: () -> Unit,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    val verdict = online.verdict
    val mine = online.myRun
    val theirs = online.opponent?.run ?: OnlineRun.HOUSE
    val rivalName = online.opponent?.name ?: OnlineRun.HOUSE_NAME
    val won = verdict?.iWon == true
    ArcadeCard(
        title = if (won) "You win!" else "So close",
        icon = if (won) ArcadeIcon.Trophy else ArcadeIcon.Swords,
        accent = if (won) Banana else Sky,
        width = 520.dp,
    ) {
        OutlinedText("$myName vs $rivalName", displayStyle(24.sp), fill = Cream, outlineWidth = 5.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        online.coinsDelta?.let { d ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                ArcadeIconView(ArcadeIcon.Coin, size = 24.dp)
                Text(if (d >= 0) "  +$d" else "  $d", style = displayStyle(24.sp, if (d >= 0) Banana else Berry))
            }
        }
        if (mine != null && verdict != null) {
            ArcadeInset(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ResultLine("Flee", "${secs(mine.fleeMs)} vs ${secs(theirs.fleeMs)}", verdict.wonFlee)
                    ResultLine("Hunt", "${secs(mine.huntMs)} vs ${secs(theirs.huntMs)}", verdict.wonHunt)
                }
            }
        }
        val failed = online.submitError != null
        Text(
            when {
                online.submitting -> "Uploading your run…"
                online.queued -> "Saved on this device. It uploads automatically when you're back online."
                failed -> online.submitError.orEmpty()
                online.opponent == null -> "Your ghost is live! The next player in this tier races it, and the result lands in your inbox."
                else -> "Result saved. $rivalName will see it in their inbox."
            },
            color = if (failed) Berry else Cream,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (failed) ArcadeButton("Retry upload", icon = ArcadeIcon.Refresh, fill = Lime, onClick = onRetryUpload, modifier = Modifier.weight(1f))
            ArcadeButton("Play again", icon = ArcadeIcon.Refresh, fill = Banana, enabled = !online.submitting, onClick = onPlayAgain, modifier = Modifier.weight(1f))
            ArcadeButton("Menu", icon = ArcadeIcon.Home, fill = Cream, enabled = !online.submitting, onClick = onMenu, modifier = Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun ResultLine(label: String, times: String, won: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = displayStyle(15.sp, Cream), modifier = Modifier.weight(0.6f))
        Text(times, color = Cream.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        ArcadeTag(if (won) "Won" else "Lost", if (won) Lime else Berry)
    }
}
