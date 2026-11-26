package com.snakechase.app

import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.SnakeRole
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.SnakeAI
import com.snakechase.app.levels.Level1NeonDrift
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SnakeAITest {

    @Test
    fun testSnakeCreation_hasCorrectSegments() {
        val config = Level1NeonDrift.snakeConfig
        val spawn = Vec2(400f, 300f)
        val snake = SnakeAI.createInitialSnake(SnakeRole.PRIMARY, spawn, config)

        assertEquals(SnakeRole.PRIMARY, snake.role)
        assertEquals(spawn, snake.headPosition)
        assertEquals(config.initialSegments, snake.segments.size)

        val fullSnake = SnakeAI.createInitialSnake(SnakeRole.PRIMARY, spawn, config, customSegmentCount = config.bodySegments)
        assertEquals(config.bodySegments, fullSnake.segments.size)
    }

    @Test
    fun testSnakeUpdate_movesTowardsPlayer() {
        val config = Level1NeonDrift.snakeConfig
        val spawn = Vec2(100f, 100f)
        val snake = SnakeAI.createInitialSnake(SnakeRole.PRIMARY, spawn, config)
        val player = PlayerOrb(position = Vec2(300f, 100f))

        val updated = SnakeAI.updateSnake(
            snake = snake,
            player = player,
            config = config.personality,
            segmentSpacing = config.segmentSpacing,
            elapsedSurvivalTimeMs = 10_000L,
            deltaTimeSec = 0.5f,
        )

        // Head should have moved to the right (x increased towards 300)
        assertTrue(updated.headPosition.x > spawn.x)
        assertNotNull(updated.segments.firstOrNull())
    }

    @Test
    fun testSnakeNeverLeavesArena_whenPlayerFleesAlongWall() {
        val config = Level1NeonDrift.snakeConfig
        val arena = Vec2(800f, 600f)
        var snake = SnakeAI.createInitialSnake(SnakeRole.PRIMARY, Vec2(400f, 300f), config)
        // Player pinned to the left wall moving fast outward: lookahead aims far off-arena.
        val player = PlayerOrb(position = Vec2(10f, 300f), velocity = Vec2(-900f, 0f))

        repeat(600) {
            // 10 seconds at 60 FPS
            snake = SnakeAI.updateSnake(
                snake = snake,
                player = player,
                config = config.personality,
                segmentSpacing = config.segmentSpacing,
                elapsedSurvivalTimeMs = 30_000L,
                deltaTimeSec = 1f / 60f,
                arenaSize = arena,
            )
            val h = snake.headPosition
            assertTrue(h.x >= snake.headRadius && h.x <= arena.x - snake.headRadius, "head x out of arena: $h")
            assertTrue(h.y >= snake.headRadius && h.y <= arena.y - snake.headRadius, "head y out of arena: $h")
            snake.segments.forEach { seg ->
                assertTrue(seg.position.x in 0f..arena.x && seg.position.y in 0f..arena.y, "segment out: ${seg.position}")
            }
        }
    }
}
