package com.snakechase.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardEntry(
    val id: String = "",
    val playerName: String,
    val country: String = "",
    val score: Int,
    val survivalTimeMs: Long,
    val levelId: Int,
    val timestamp: Long = 0L,
)

@Serializable
data class PlayerProfile(
    val playerId: String,
    val name: String,
    val country: String = "",
    val totalScore: Int = 0,
    val totalGames: Int = 0,
    val bestScores: Map<String, Int> = emptyMap(),
)
