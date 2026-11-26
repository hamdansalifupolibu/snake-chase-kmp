package com.snakechase.app.engine

import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.Vec2
import kotlin.math.PI
import kotlin.random.Random

object PowerUpSystem {

    private var lastOrbDismissMs: Long = 0L

    fun getPowerUpDurationMs(type: com.snakechase.app.domain.model.PowerUpType, levelId: Int): Long = when (type) {
        com.snakechase.app.domain.model.PowerUpType.SHIELD -> when (levelId) {
            2 -> 6_000L
            3 -> 5_000L
            4 -> 4_500L
            5 -> 3_500L
            else -> 5_000L
        }
        com.snakechase.app.domain.model.PowerUpType.SPEED_BURST -> 4_000L
        com.snakechase.app.domain.model.PowerUpType.SCORE_MAGNET -> 6_000L
        com.snakechase.app.domain.model.PowerUpType.GHOST_WALK -> 3_500L
    }

    /**
     * Determines whether a PowerUp Orb should spawn in the arena.
     * Level 1: Only spawns Speed Burst or Score Magnet in sub-stages 2+.
     * Level 2+: Spawns on mercy assist (deaths >= 2), or periodically in sub-stages.
     */
    fun shouldSpawnOrb(state: GameState): Boolean {
        if (state.activePowerUpOrb != null) return false

        val currentSurvivalMs = state.hud.survivalTimeMs
        val difficulty = state.runDifficulty
        if (currentSurvivalMs - lastOrbDismissMs < difficulty.powerUpGapMs) return false

        // Mercy Assist: Player died 2 or more times in this sub-stage
        if (state.subStageDeaths >= 2 && currentSurvivalMs >= 3_000L) {
            return true
        }

        // Periodic spawn (every ~20s on Hard in sub-stages 1+; sooner and from the start on Easy)
        if (state.hud.currentSubStage >= 1 || difficulty == com.snakechase.app.domain.model.Difficulty.EASY) {
            val stageCycle = (currentSurvivalMs / 1000L) % difficulty.powerUpEverySec
            if (stageCycle in 1L..2L) {
                return true
            }
        }

        return false
    }

    /**
     * Spawns a new PowerUp Orb at a safe, open location away from the snake head.
     */
    fun spawnOrb(state: GameState): com.snakechase.app.domain.model.PowerUpOrb {
        val arena = state.arenaSize
        val margin = 70f
        val snakeHead = state.primarySnake?.headPosition ?: Vec2(arena.x / 2f, arena.y / 2f)

        // Try up to 8 random positions to find one safe from snake head
        var bestPos = Vec2(
            Random.nextFloat() * (arena.x - margin * 2) + margin,
            Random.nextFloat() * (arena.y - margin * 2) + margin,
        )

        for (i in 0 until 8) {
            val candidate = Vec2(
                Random.nextFloat() * (arena.x - margin * 2) + margin,
                Random.nextFloat() * (arena.y - margin * 2) + margin,
            )
            if (candidate.distanceTo(snakeHead) > 160f && candidate.distanceTo(state.player.position) > 80f) {
                bestPos = candidate
                break
            }
        }

        // Determine type based on level and sub-stage
        val availableTypes = if (state.runDifficulty.helpfulPowerUpsOnly) {
            listOf(
                com.snakechase.app.domain.model.PowerUpType.SHIELD,
                com.snakechase.app.domain.model.PowerUpType.GHOST_WALK,
                com.snakechase.app.domain.model.PowerUpType.SPEED_BURST,
            )
        } else if (state.levelId <= 1) {
            listOf(com.snakechase.app.domain.model.PowerUpType.SPEED_BURST, com.snakechase.app.domain.model.PowerUpType.SCORE_MAGNET)
        } else if (state.subStageDeaths >= 2) {
            listOf(com.snakechase.app.domain.model.PowerUpType.SHIELD, com.snakechase.app.domain.model.PowerUpType.GHOST_WALK)
        } else {
            com.snakechase.app.domain.model.PowerUpType.entries
        }

        val selectedType = availableTypes[Random.nextInt(availableTypes.size)]

        return com.snakechase.app.domain.model.PowerUpOrb(
            type = selectedType,
            position = bestPos,
            radius = 22f,
            spawnTimeMs = state.hud.survivalTimeMs,
            lifetimeMs = 14_000L,
            isCollected = false,
            pulsePhase = 0f,
        )
    }

    /**
     * Checks if the player touches the power-up orb.
     */
    fun checkCollection(player: PlayerOrb, orb: com.snakechase.app.domain.model.PowerUpOrb): Boolean {
        if (orb.isCollected) return false
        val dist = player.position.distanceTo(orb.position)
        return dist <= (player.radius + orb.radius + 6f)
    }

    /**
     * Ticks active power-ups and manages field orb lifetime.
     */
    fun tickPowerUps(
        orb: com.snakechase.app.domain.model.PowerUpOrb?,
        activeList: List<com.snakechase.app.domain.model.ActivePowerUp>,
        player: PlayerOrb,
        levelId: Int,
        deltaMs: Long,
    ): Triple<com.snakechase.app.domain.model.PowerUpOrb?, List<com.snakechase.app.domain.model.ActivePowerUp>, com.snakechase.app.domain.model.PowerUpType?> {
        var updatedOrb = orb
        var newlyCollectedType: com.snakechase.app.domain.model.PowerUpType? = null

        // 1. Decay active power-ups
        val updatedActive = activeList.mapNotNull { p ->
            val rem = p.remainingMs - deltaMs
            if (rem <= 0L) null else p.copy(remainingMs = rem)
        }.toMutableList()

        // 2. Check field orb collection
        if (updatedOrb != null && !updatedOrb.isCollected) {
            if (checkCollection(player, updatedOrb)) {
                newlyCollectedType = updatedOrb.type
                val duration = getPowerUpDurationMs(updatedOrb.type, levelId)
                // Replace or add active power-up
                val existingIdx = updatedActive.indexOfFirst { it.type == updatedOrb!!.type }
                val newActive = com.snakechase.app.domain.model.ActivePowerUp(
                    type = updatedOrb.type,
                    remainingMs = duration,
                    maxMs = duration,
                )
                if (existingIdx >= 0) {
                    updatedActive[existingIdx] = newActive
                } else {
                    updatedActive.add(newActive)
                }
                updatedOrb = null
                lastOrbDismissMs = lastOrbDismissMs.coerceAtLeast(0L)
            } else {
                val newLifetime = updatedOrb.lifetimeMs - deltaMs
                if (newLifetime <= 0L) {
                    updatedOrb = null
                } else {
                    val nextPhase = (updatedOrb.pulsePhase + (deltaMs / 1000f) * 4f) % (2f * PI.toFloat())
                    updatedOrb = updatedOrb.copy(
                        lifetimeMs = newLifetime,
                        pulsePhase = nextPhase,
                    )
                }
            }
        }

        return Triple(updatedOrb, updatedActive, newlyCollectedType)
    }

    fun onLevelReset() {
        lastOrbDismissMs = 0L
    }
}
