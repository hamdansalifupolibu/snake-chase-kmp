package com.snakechase.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.online.OnlineDuelRepository
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeChoiceRow
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle
import com.snakechase.app.util.toFixed
import org.koin.compose.koinInject

enum class LeaderboardTab {
    PERSONAL_BESTS,
    GLOBAL_RANKINGS,
}

private data class GlobalRankingItem(
    val rank: Int,
    val playerName: String,
    val worldName: String,
    val levelId: Int,
    val score: Int,
    val timeSec: Float,
    val difficulty: com.snakechase.app.domain.model.Difficulty? = null,
)

private val Silver = Color(0xFFDDE3E8)
private val Bronze = Color(0xFFE0A06A)

@Composable
fun LeaderboardOverlay(
    state: GameState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(LeaderboardTab.PERSONAL_BESTS) }

    // Real global leaderboard from Firestore (top personal bests, all worlds).
    val onlineRepository = koinInject<OnlineDuelRepository>()
    var globalRows by remember { mutableStateOf<List<GlobalRankingItem>?>(null) }
    var globalError by remember { mutableStateOf<String?>(null) }
    // Loaded once for both tabs: "Your bests" shows each level's world record next to yours.
    LaunchedEffect(Unit) {
        if (globalRows != null) return@LaunchedEffect
        runCatching {
            com.snakechase.app.online.LeaderboardRules.clean(onlineRepository.topScores(limit = 200, maxScore = LevelRegistry.ALL_LEVELS.maxOf { it.scoringConfig.levelScoreMax }.toLong()), { LevelRegistry.getById(it).scoringConfig.levelScoreMax })
        }
            .onSuccess { rows ->
                globalError = null
                globalRows = rows.mapIndexed { i, r ->
                    GlobalRankingItem(
                        rank = i + 1,
                        playerName = r.name,
                        worldName = LevelRegistry.ALL_LEVELS.firstOrNull { it.id == r.levelId }?.name ?: "World ${r.levelId}",
                        levelId = r.levelId,
                        score = r.score.toInt(),
                        timeSec = ((r.survivalMs / 100) / 10f),
                        difficulty = r.difficulty?.let { d -> com.snakechase.app.domain.model.Difficulty.entries.firstOrNull { it.name == d } },
                    )
                }
            }
            .onFailure { e -> globalError = e.message ?: "Couldn't reach the online leaderboard." }
    }

    ArcadeDialog(title = "Hall of fame", icon = ArcadeIcon.Trophy, onClose = onClose, width = 600.dp, modifier = modifier) {
        ArcadeChoiceRow(
            options = LeaderboardTab.entries.toList(),
            selected = selectedTab,
            label = { if (it == LeaderboardTab.PERSONAL_BESTS) "Your bests" else "World top 25" },
            onSelect = { selectedTab = it },
            selectedFill = Lime,
        )

        if (selectedTab == LeaderboardTab.PERSONAL_BESTS) {
            LevelRegistry.ALL_LEVELS.forEach { level ->
                val score = state.bestScores[level.id] ?: 0
                val timeMs = state.bestTimes[level.id] ?: 0L
                ArcadeInset(Modifier.fillMaxWidth(), highlight = if (score > 0) levelLook(level.id).accent else null) {
                    LevelThumb(level.id)
                    Column(Modifier.weight(1f)) {
                        Text(level.name, style = displayStyle(16.sp, Cream))
                        val stars = state.levelStarRatings[level.id] ?: 0
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(3) { com.snakechase.app.ui.theme.RatingStar(filled = it < stars, size = 14.dp) }
                            Text("  ${level.subStages.size} stages", color = Moss, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (score > 0) "$score pts" else "Not played", style = displayStyle(16.sp, if (score > 0) Banana else Moss))
                        if (timeMs > 0L) {
                            Text("${(timeMs / 1000f).toFixed(1)} s survived", color = Cream.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        // The level's world record, so there's always someone to chase.
                        globalRows?.firstOrNull { it.levelId == level.id }?.let { top ->
                            Text("World record: ${top.playerName} · ${top.score}", color = Sky, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val rows = globalRows
            if (rows.isNullOrEmpty()) {
                Text(
                    text = when {
                        globalError != null -> "$globalError Your personal bests are still saved on this device."
                        rows == null -> "Loading world rankings…"
                        else -> "No world scores yet. Set the first record!"
                    },
                    color = Cream.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
            rows.orEmpty().forEach { item ->
                val medal = when (item.rank) {
                    1 -> Banana
                    2 -> Silver
                    3 -> Bronze
                    else -> null
                }
                ArcadeInset(Modifier.fillMaxWidth(), highlight = medal) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(medal ?: Ink.copy(alpha = 0.5f))
                            .border(2.5.dp, Ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${item.rank}", style = displayStyle(14.sp, if (medal != null) Ink else Cream))
                    }
                    LevelThumb(item.levelId, small = true)
                    Column(Modifier.weight(1f)) {
                        Text(item.playerName, style = displayStyle(15.sp, Cream), maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.worldName, color = Moss, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            item.difficulty?.let { d ->
                                Text("  ${d.label}", color = difficultyColor(d), fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${item.score} pts", style = displayStyle(15.sp, medal ?: Banana))
                        Text("${item.timeSec.toFixed(1)} s", color = Cream.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        ArcadeButton(text = "Done", fill = Banana, onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** A tiny live window into a level's scene: used anywhere a list names a level. */
@Composable
fun LevelThumb(levelId: Int, small: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    LevelBackdrop(
        levelId = levelId,
        modifier = Modifier
            .size(width = if (small) 44.dp else 58.dp, height = if (small) 32.dp else 42.dp)
            .clip(shape)
            .border(2.5.dp, Ink, shape),
    )
}
