package com.snakechase.app

import com.snakechase.app.domain.model.Difficulty
import com.snakechase.app.domain.model.GameState
import com.snakechase.app.domain.model.UserSettings
import com.snakechase.app.engine.GameEngine
import com.snakechase.app.engine.ObstacleController
import com.snakechase.app.levels.LevelRegistry
import com.snakechase.app.online.LeaderboardRow
import com.snakechase.app.online.LeaderboardRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DifficultyTest {
    private fun allObstacles(levelId: Int, d: Difficulty) = ObstacleController.initializeObstacles(LevelRegistry.getById(levelId).obstacleConfig, Int.MAX_VALUE, difficulty = d)

    @Test
    fun easierModes_haveFewerObstacles_butEveryWorldKeepsItsWholeCast() {
        for (level in LevelRegistry.ALL_LEVELS) {
            val hard = allObstacles(level.id, Difficulty.HARD)
            val moderate = allObstacles(level.id, Difficulty.MODERATE)
            val easy = allObstacles(level.id, Difficulty.EASY)
            assertTrue(easy.size <= moderate.size && moderate.size <= hard.size, "level ${level.id}: ${easy.size}/${moderate.size}/${hard.size}")
            assertEquals(hard.map { it::class }.toSet(), easy.map { it::class }.toSet(), "level ${level.id} keeps one of every kind on Easy")
        }
        val crowded = LevelRegistry.ALL_LEVELS.count { allObstacles(it.id, Difficulty.EASY).size < allObstacles(it.id, Difficulty.HARD).size }
        assertTrue(crowded >= 3, "Easy should thin out most worlds ($crowded did)")
    }

    @Test
    fun theRunKeepsTheDifficultyItStartedOn() {
        val start = GameEngine.startLevel(2, GameState(settings = UserSettings(difficulty = Difficulty.EASY)))
        assertEquals(Difficulty.EASY, start.runDifficulty)
        val changedMidRun = start.copy(settings = start.settings.copy(difficulty = Difficulty.HARD))
        assertEquals(Difficulty.EASY, GameEngine.tick(changedMidRun, null, 16L).newState.runDifficulty)
    }

    @Test
    fun easy_handsOutAShieldAsEachStageBegins_otherModesDoNot() {
        fun firstOrb(d: Difficulty): com.snakechase.app.domain.model.PowerUpType? {
            var s = GameEngine.startLevel(1, GameState(settings = UserSettings(difficulty = d)))
                .copy(phase = com.snakechase.app.domain.model.GamePhase.PLAYING, godModeEnabled = true)
            repeat(30) { s = GameEngine.tick(s, null, 16L).newState }
            return s.activePowerUpOrb?.type
        }
        assertEquals(com.snakechase.app.domain.model.PowerUpType.SHIELD, firstOrb(Difficulty.EASY))
        assertEquals(null, firstOrb(Difficulty.HARD))
    }

    @Test
    fun easy_rescuesTheFirstCatchInAStage_butNotTheSecond() {
        var s = GameEngine.startLevel(1, GameState(settings = UserSettings(difficulty = Difficulty.EASY)))
            .copy(phase = com.snakechase.app.domain.model.GamePhase.PLAYING)
        fun caught(st: GameState): GameState {
            val onOrb = st.copy(
                primarySnake = st.primarySnake!!.copy(headPosition = st.player.position, stunRemainingMs = 0L),
                activePowerUps = emptyList(),
                activePowerUpOrb = null,
            )
            return GameEngine.tick(onOrb, st.player.position, 16L).newState
        }
        s = caught(s)
        assertEquals(com.snakechase.app.domain.model.GamePhase.PLAYING, s.phase, "first catch: rescued")
        assertTrue(s.shieldState.isActive, "rescue gives a short shield")
        s = caught(s.copy(shieldState = com.snakechase.app.domain.model.ShieldState()))
        assertEquals(com.snakechase.app.domain.model.GamePhase.GAME_OVER, s.phase, "second catch in the same stage ends the run")
    }

    @Test
    fun easy_isGentlestOnLevelOne_andRampsUp() {
        val speeds = (1..5).map { Difficulty.EASY.snakeSpeedFor(it) }
        assertEquals(speeds.sorted(), speeds)
        assertTrue(speeds.last() < Difficulty.MODERATE.snakeSpeedFor(5))
    }

    @Test
    fun easyRuns_neverReachTheWorldBoard() {
        val rows = listOf(
            LeaderboardRow("A", 1, 80, 30_000, "EASY"),
            LeaderboardRow("B", 1, 70, 30_000, "HARD"),
            LeaderboardRow("C", 1, 60, 30_000, null), // from before difficulties: kept
        )
        assertEquals(listOf("B", "C"), LeaderboardRules.clean(rows, { 100 }).map { it.name })
        assertTrue(!Difficulty.EASY.countsOnline && Difficulty.MODERATE.countsOnline && Difficulty.HARD.countsOnline)
    }
}
