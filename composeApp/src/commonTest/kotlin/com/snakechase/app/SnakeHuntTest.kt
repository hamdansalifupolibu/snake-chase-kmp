package com.snakechase.app

import com.russhwolf.settings.MapSettings
import com.snakechase.app.domain.model.AiDifficulty
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.MultiplayerRole
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.MultiplayerEngine
import com.snakechase.app.hunt.HuntProgress
import com.snakechase.app.hunt.HuntStage
import com.snakechase.app.hunt.SnakeHunt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnakeHuntTest {

    /**
     * A bot hunter that sees the prey [reactionMs] late (like a person) and aims [lookAheadMs]
     * ahead of its motion. Returns the catch time, or null if the prey survived the stage.
     */
    private fun botHunt(stage: HuntStage, reactionMs: Long, lookAheadMs: Long): Long? {
        var s = SnakeHunt.startState(stage, "Bot").copy(phase = DuelPhase.ROUND_PLAYING)
        val dt = 16L
        val seen = ArrayDeque<Vec2>()
        val delay = (reactionMs / dt).toInt()
        while (true) {
            seen.addLast(s.prey.position)
            while (seen.size > delay + 2) seen.removeFirst()
            val prey = seen[maxOf(0, seen.size - 1 - delay)]
            val before = seen[maxOf(0, seen.size - 2 - delay)]
            val aim = Vec2(prey.x + (prey.x - before.x) / dt * lookAheadMs, prey.y + (prey.y - before.y) / dt * lookAheadMs)
            val r = MultiplayerEngine.tick(s, localInputPos = aim, rivalInputPos = null, deltaMs = dt)
            if (r.roundCompleted) return if (r.contactOccurred) r.newState.survivalTimeMs else null
            s = r.newState
        }
    }

    @Test
    fun everyHunt_isWinnable_withinItsTimeLimit() {
        for (stage in SnakeHunt.stages) {
            val styles = listOf(150L, 250L, 350L).flatMap { react -> listOf(0L, 250L, 500L).map { look -> react to look } }
            val catches = styles.mapNotNull { (react, look) -> botHunt(stage, react, look) }
            assertTrue(catches.isNotEmpty(), "hunt ${stage.number} ${stage.name} must be catchable by at least one style")
            assertTrue(catches.all { it <= stage.timeLimitMs })
        }
    }

    /** Regression: the AI prey's momentum read a velocity that was never stored, so it crawled. */
    @Test
    fun aiPrey_fleesAtItsRealSpeed() {
        var s = MultiplayerEngine.startMatch(DuelMode.VS_AI_RIVAL, "P1", "AI", difficulty = AiDifficulty.MODERATE)
            .copy(phase = DuelPhase.ROUND_PLAYING, localRole = MultiplayerRole.SNAKE)
        val start = s.prey.position
        repeat(30) { s = MultiplayerEngine.tick(s, localInputPos = null, rivalInputPos = null, deltaMs = 16L).newState } // ~0.5 s
        val moved = s.prey.position.distanceTo(start)
        assertTrue(moved > 80f, "a Moderate prey (~300/s) should cover well over 80 units in 0.5 s, moved $moved")
    }

    @Test
    fun stars_rewardSpeed() {
        val stage = SnakeHunt.stages.first() // 20 s
        assertEquals(3, SnakeHunt.starsFor(stage, 5_000))
        assertEquals(3, SnakeHunt.starsFor(stage, 10_000), "exactly half the clock left still counts")
        assertEquals(2, SnakeHunt.starsFor(stage, 14_000))
        assertEquals(1, SnakeHunt.starsFor(stage, 19_000))
        assertEquals(0, SnakeHunt.starsFor(stage, null), "it escaped")
    }

    @Test
    fun progress_unlocksInOrder_andPaysOnlyForNewStars() {
        val progress = HuntProgress(MapSettings())
        assertTrue(progress.isUnlocked(1))
        assertFalse(progress.isUnlocked(2), "hunt 2 opens once hunt 1 is won")

        assertEquals(SnakeHunt.COINS_PER_NEW_STAR, progress.record(1, 1))
        assertTrue(progress.isUnlocked(2))
        assertEquals(0, progress.record(1, 1), "same result again pays nothing")
        assertEquals(2 * SnakeHunt.COINS_PER_NEW_STAR, progress.record(1, 3), "only the two new stars pay")
        assertEquals(0, progress.record(1, 2), "a worse run never lowers the best")
        assertEquals(3, progress.stars(1))
    }
}
