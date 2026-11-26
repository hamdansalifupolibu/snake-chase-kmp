package com.snakechase.app.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class DailyChallengeInfo(
    val epochDay: Long,
    val title: String,
    val levelId: Int,
    val modifierName: String,
    val modifierDescription: String,
    val isCompletedToday: Boolean,
    val todayScore: Int,
) {
    companion object {
        private val MODIFIERS = listOf(
            "Hyper-Reflex" to "Snake turns 15% sharper, but near-misses grant +50% points.",
            "Solar Flare" to "Lethal hazard zones expand, power-ups spawn more frequently.",
            "Overdrive" to "Player Phase Dash cooldown reduced by 50%.",
            "Twin Predation" to "Split hunters activate 10 seconds earlier.",
            "Iron Will" to "Shield orbs disabled — pure survival reflex test.",
        )

        fun getCurrentChallenge(
            lastCompletedDay: Long,
            lastScore: Int,
        ): DailyChallengeInfo {
            val now = Clock.System.now()
            val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val epochDay = (now.toEpochMilliseconds() / (1000L * 60 * 60 * 24))

            // Deterministic selection from epochDay
            val levelId = ((epochDay.toInt() % 5) + 1).coerceIn(1, 5)
            val modPair = MODIFIERS[(epochDay.toInt() % MODIFIERS.size).coerceAtLeast(0)]
            val isCompleted = lastCompletedDay == epochDay

            return DailyChallengeInfo(
                epochDay = epochDay,
                title = "DAILY TRIAL #${epochDay % 1000}",
                levelId = levelId,
                modifierName = modPair.first,
                modifierDescription = modPair.second,
                isCompletedToday = isCompleted,
                todayScore = if (isCompleted) lastScore else 0,
            )
        }
    }
}
