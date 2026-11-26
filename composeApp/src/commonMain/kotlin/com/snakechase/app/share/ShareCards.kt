package com.snakechase.app.share

import com.snakechase.app.domain.model.DuelMatchResult
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.profile.Badge
import com.snakechase.app.profile.PlayerCard

/** Who is bragging: shown on the card's player strip. */
data class ShareIdentity(
    val name: String,
    val avatar: Int,
    val skin: SnakeSkin = SnakeSkin.DEFAULT,
    val orb: OrbSkin = OrbSkin.SPARK,
)

data class ShareStat(val label: String, val value: String)

enum class ShareKind { LEVEL_CLEAR, RUN, DUEL_WON, DUEL_LOST, PROFILE }

/**
 * Everything a share card shows, plus the text sent with the image. Built from game results by
 * [ShareCards], drawn by ui/share/ShareCardView.kt; plain data so the rules are unit-tested.
 */
data class ShareCardData(
    val kind: ShareKind,
    /** Level whose art and colours the card uses (duels use their arena's level). */
    val levelId: Int,
    val headline: String,
    val worldName: String,
    /** 1..3 for a cleared level, 0 = no stars row. */
    val stars: Int,
    val bigNumber: String,
    val bigLabel: String,
    val stats: List<ShareStat>,
    val player: ShareIdentity,
    /** Small stamp on the card, e.g. "New best!" or the duel MVP title. */
    val badge: String?,
    val message: String,
    val fileName: String,
)

object ShareCards {
    private const val TAG = "#SnakeChase"

    /** Every shared message ends with a link that opens the game in the browser, no install. */
    private val PLAY = "Play free in your browser: ${com.snakechase.app.network.firebase.FirebaseConfig.WEB_URL}"

    fun levelClear(
        levelId: Int,
        levelName: String,
        stars: Int,
        score: Int,
        maxScore: Int,
        survivalMs: Long,
        stagesCleared: Int,
        totalStages: Int,
        newBest: Boolean,
        player: ShareIdentity,
    ): ShareCardData {
        val s = stars.coerceIn(1, 3)
        return ShareCardData(
            kind = ShareKind.LEVEL_CLEAR,
            levelId = levelId,
            headline = "Level $levelId conquered!",
            worldName = levelName,
            stars = s,
            bigNumber = "$score",
            bigLabel = "of $maxScore points",
            stats = listOf(
                ShareStat("Survived", formatTime(survivalMs)),
                ShareStat("Stages", "$stagesCleared/$totalStages"),
                ShareStat("Rating", "$s/3"),
            ),
            player = player,
            badge = when {
                s == 3 -> "Flawless"
                newBest -> "New best!"
                else -> null
            },
            message = "I conquered Level $levelId ($levelName) in Snake Chase with $s/3 stars and $score points. Can you beat me? $TAG\n$PLAY",
            fileName = "snakechase-level$levelId-$score.png",
        )
    }

    /** A run that ended in the snake's jaws: still worth showing off how far it went. */
    fun run(
        levelId: Int,
        levelName: String,
        score: Int,
        maxScore: Int,
        survivalMs: Long,
        stageReached: Int,
        totalStages: Int,
        player: ShareIdentity,
    ): ShareCardData = ShareCardData(
        kind = ShareKind.RUN,
        levelId = levelId,
        headline = "Survived ${formatTime(survivalMs)}!",
        worldName = levelName,
        stars = 0,
        bigNumber = "$score",
        bigLabel = "of $maxScore points",
        stats = listOf(
            ShareStat("Level", "$levelId"),
            ShareStat("Stage", "$stageReached/$totalStages"),
            ShareStat("Survived", formatTime(survivalMs)),
        ),
        player = player,
        badge = if (score >= maxScore * 3 / 4) "So close!" else null,
        message = "I survived ${formatTime(survivalMs)} on Level $levelId ($levelName) in Snake Chase and scored $score. Can you outrun the snake? $TAG\n$PLAY",
        fileName = "snakechase-run-level$levelId-$score.png",
    )

    /**
     * A finished duel, told from [player]'s side. Player 1/2 are matched by name; when neither
     * name is ours (e.g. a renamed profile) the result's own winner flag still decides the story.
     */
    fun duel(result: DuelMatchResult, arenaLevelId: Int, vsAi: Boolean, player: ShareIdentity): ShareCardData {
        val iAmP1 = when (player.name) {
            result.player1Name -> true
            result.player2Name -> false
            else -> result.isLocalWinner == (result.winnerName == result.player1Name)
        }
        val mine = if (iAmP1) result.player1RoundsWon else result.player2RoundsWon
        val theirs = if (iAmP1) result.player2RoundsWon else result.player1RoundsWon
        val rival = if (vsAi) "the AI rival" else (if (iAmP1) result.player2Name else result.player1Name).ifBlank { "my rival" }
        val won = result.isLocalWinner
        val best = listOf(result.round1TimeMs, result.round2TimeMs, result.round3TimeMs).maxOrNull() ?: 0L
        return ShareCardData(
            kind = if (won) ShareKind.DUEL_WON else ShareKind.DUEL_LOST,
            levelId = arenaLevelId,
            headline = if (won) "Duel won!" else "What a duel!",
            worldName = "vs $rival",
            stars = 0,
            bigNumber = "$mine - $theirs",
            bigLabel = "rounds",
            stats = listOf(
                ShareStat("Round 1", formatTime(result.round1TimeMs)),
                ShareStat("Round 2", formatTime(result.round2TimeMs)),
                ShareStat("Longest", formatTime(best)),
            ),
            player = player,
            badge = if (won) result.mvpTitle.ifBlank { null } else null,
            message = if (won) {
                "I beat $rival $mine-$theirs in a Snake Chase duel. Think you can take me? $TAG\n$PLAY"
            } else {
                "Close Snake Chase duel against $rival ($mine-$theirs). Rematch is on! $TAG\n$PLAY"
            },
            fileName = "snakechase-duel-$mine-$theirs.png",
        )
    }

    /** The player card from the profile page: rank, stars, badges and collection. */
    fun profile(card: PlayerCard, player: ShareIdentity): ShareCardData {
        val badges = card.earnedBadges
        val topLevel = (PlayerCard.LEVELS downTo 1).firstOrNull { (card.levelStars[it] ?: 0) > 0 } ?: 1
        return ShareCardData(
            kind = ShareKind.PROFILE,
            levelId = topLevel,
            headline = card.rank.title,
            worldName = "Player card",
            stars = 0,
            bigNumber = "${card.totalLevelStars}",
            bigLabel = "of 15 level stars",
            stats = listOf(
                ShareStat("Badges", "${badges.size}/${Badge.entries.size}"),
                ShareStat("Duels W-L", "${card.profile.duelsWon}-${card.profile.duelsLost}"),
                ShareStat("Skins", "${card.skinsOwned + card.orbsOwned}"),
            ),
            player = player,
            badge = when {
                Badge.STAR_COLLECTOR in badges -> "All stars!"
                Badge.CHAMPION in badges -> "Champion"
                else -> null
            },
            message = "I'm a ${card.rank.title} in Snake Chase: ${card.totalLevelStars}/15 stars and ${badges.size} badges. Come find me in the jungle! $TAG\n$PLAY",
            fileName = "snakechase-profile.png",
        )
    }

    /** 42.3 s under a minute, 1:05 above it. */
    fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        if (total < 59_950L) {
            val tenths = (total + 50) / 100 // rounded, like the rest of the game's timers
            return "${tenths / 10}.${tenths % 10}s"
        }
        val sec = total / 1000
        return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
    }

    fun starText(stars: Int): String = "★".repeat(stars.coerceIn(0, 3)) + "☆".repeat(3 - stars.coerceIn(0, 3))
}
