package com.snakechase.app

import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.Rect
import com.snakechase.app.domain.model.SnakeRole
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.SnakeAI
import com.snakechase.app.engine.Solid
import com.snakechase.app.levels.LevelRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

/** Snakes must go around walls and bars, never through them. */
class SnakeSolidsTest {
    private val cfg = LevelRegistry.getById(2).snakeConfig

    private fun chase(solids: List<Solid>, start: Vec2, prey: Vec2, frames: Int = 600): Float {
        var snake = SnakeAI.createInitialSnake(SnakeRole.PRIMARY, start, cfg)
        val orb = PlayerOrb(position = prey, targetPosition = prey)
        var minGap = Float.MAX_VALUE
        var closest = Float.MAX_VALUE
        var t = 0L
        repeat(frames) {
            snake = SnakeAI.updateSnake(snake, orb, cfg.personality, cfg.segmentSpacing, t, 0.016f, solids = solids)
            t += 16L
            closest = minOf(closest, snake.headPosition.distanceTo(prey))
            solids.forEach { minGap = minOf(minGap, it.gap(snake.headPosition, snake.headRadius)) }
        }
        assertTrue(minGap > -1.5f, "head went into a solid (gap $minGap)")
        return closest
    }

    @Test
    fun aWallBetweenSnakeAndOrb_isWalkedAround() {
        val wall = Solid.Box(Rect(360f, 150f, 40f, 300f))
        val closest = chase(listOf(wall), start = Vec2(150f, 300f), prey = Vec2(600f, 300f))
        assertTrue(closest < 60f, "snake should reach the orb around the wall, got within $closest")
    }

    @Test
    fun aBarAcrossThePath_isNotCrossed() {
        val bar = Solid.Capsule(Vec2(400f, 160f), Vec2(400f, 440f), 7f)
        val closest = chase(listOf(bar), start = Vec2(150f, 300f), prey = Vec2(600f, 300f))
        assertTrue(closest < 60f, "snake should get around the bar, got within $closest")
    }

    @Test
    fun pushOut_movesACircleClearOfABox() {
        val box = Solid.Box(Rect(100f, 100f, 100f, 100f))
        val inside = Vec2(110f, 150f)
        val push = box.pushOut(inside, 10f)!!
        assertTrue(box.gap(inside + push, 10f) >= 0f)
        assertTrue(push.x < 0f, "leaves through the nearest (left) side")
    }
}
