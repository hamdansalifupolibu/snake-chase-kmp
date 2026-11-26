package com.snakechase.app

import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.MultiplayerRole
import com.snakechase.app.domain.model.PlayerOrb
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.MultiplayerEngine
import com.snakechase.app.engine.SmartPreyAI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiplayerEngineTest {

    @Test
    fun testVsAiRival_userStartsAsSnake_snakeWaitsForThePointer() {
        val state = MultiplayerEngine.startMatch(
            mode = DuelMode.VS_AI_RIVAL,
            player1Name = "Player 1",
            player2Name = "AI Titan",
            arenaSize = Vec2(800f, 600f),
        )
        assertEquals(DuelMode.VS_AI_RIVAL, state.mode)
        assertEquals(MultiplayerRole.SNAKE, state.localRole)
        assertEquals(1, state.round)
        assertNotNull(state.snake)

        val playing = state.copy(phase = DuelPhase.ROUND_PLAYING)
        val head = playing.snake!!.headPosition
        // No pointer yet: the snake waits where it spawned.
        val idle = MultiplayerEngine.tick(playing, localInputPos = null, rivalInputPos = null, deltaMs = 100L).newState
        assertEquals(head, idle.snake!!.headPosition)
        // Point somewhere: the head heads straight there.
        val aim = Vec2(head.x + 200f, head.y)
        val moved = MultiplayerEngine.tick(playing, localInputPos = aim, rivalInputPos = null, deltaMs = 100L).newState
        assertTrue(moved.snake!!.headPosition.distanceTo(aim) < head.distanceTo(aim), "the head moves toward the pointer")
    }

    @Test
    fun testSnakeSteering_turnsTowardsAimTarget() {
        val state = MultiplayerEngine.startMatch(
            mode = DuelMode.VS_AI_RIVAL,
            player1Name = "Player 1",
            player2Name = "AI Titan",
        ).copy(phase = DuelPhase.ROUND_PLAYING)

        val initialAngle = state.snake!!.headAngle
        val headPos = state.snake!!.headPosition

        // Aim target far to the right
        val targetRight = Vec2(headPos.x + 300f, headPos.y)

        val tickResult = MultiplayerEngine.tick(
            state = state,
            localInputPos = targetRight,
            rivalInputPos = null,
            deltaMs = 100L,
        )

        val newAngle = tickResult.newState.snake!!.headAngle
        assertTrue(newAngle != initialAngle, "Snake heading angle should adjust towards aim target")
    }

    /** Regression (live test 2026-11-19): in online matches the guest's pointer was ignored and the AI played for them. */
    @Test
    fun onlineMatch_guestSteersTheSnake_notTheAi() {
        val hostIsPrey = MultiplayerEngine.startMatch(DuelMode.LOCAL_INVITE_CHALLENGE, "Host", "Guest")
            .copy(isNetworkMatch = true, isHost = true, localRole = MultiplayerRole.PREY, phase = DuelPhase.ROUND_PLAYING)
        val head = hostIsPrey.snake!!.headPosition
        // The guest aims hard right; the AI would chase the prey instead.
        val guestAim = Vec2(head.x + 300f, head.y)
        var s = hostIsPrey
        repeat(10) { s = MultiplayerEngine.tick(s, localInputPos = s.prey.position, rivalInputPos = guestAim, deltaMs = 16L).newState }
        assertTrue(s.snake!!.headPosition.x > head.x + 20f, "the snake must go where the guest points")
    }

    @Test
    fun onlineMatch_guestSteersThePrey_whenHostIsSnake() {
        val hostIsSnake = MultiplayerEngine.startMatch(DuelMode.LOCAL_INVITE_CHALLENGE, "Host", "Guest")
            .copy(isNetworkMatch = true, isHost = true, localRole = MultiplayerRole.SNAKE, phase = DuelPhase.ROUND_PLAYING)
        val prey = hostIsSnake.prey.position
        val guestAim = Vec2(prey.x - 200f, prey.y)
        val s = MultiplayerEngine.tick(hostIsSnake, localInputPos = null, rivalInputPos = guestAim, deltaMs = 100L).newState
        assertTrue(s.prey.position.x < prey.x - 10f, "the prey must go where the guest points")
    }

    /** Regression (live test): rounds spawned the snake aimed straight at the prey, ending them in ~1.2 s untouched. */
    @Test
    fun idleRound_isNotOverInTheFirstTwoSeconds() {
        var s = MultiplayerEngine.startMatch(DuelMode.LOCAL_INVITE_CHALLENGE, "Host", "Guest")
            .copy(isNetworkMatch = true, isHost = true, phase = DuelPhase.ROUND_PLAYING)
        repeat(125) { s = MultiplayerEngine.tick(s, localInputPos = null, rivalInputPos = null, deltaMs = 16L).newState }
        assertEquals(DuelPhase.ROUND_PLAYING, s.phase, "nobody moved, yet the round ended within 2 s")
    }

    @Test
    fun snakeInACorner_followsTheAimBackOut() {
        val base = MultiplayerEngine.startMatch(DuelMode.LOCAL_INVITE_CHALLENGE, "Host", "Guest")
            .copy(isNetworkMatch = true, isHost = true, phase = DuelPhase.ROUND_PLAYING)
        val corner = Vec2(base.arenaSize.x - 20f, base.arenaSize.y - 20f)
        var s = base.copy(snake = base.snake!!.copy(headPosition = corner, headAngle = 0.785f)) // facing into the corner
        val middle = Vec2(base.arenaSize.x / 2f, base.arenaSize.y / 2f)
        repeat(30) { s = MultiplayerEngine.tick(s, localInputPos = null, rivalInputPos = middle, deltaMs = 16L).newState }
        assertTrue(s.snake!!.headPosition.distanceTo(corner) > 40f, "the snake should leave the corner, not freeze in it")
    }

    /** A human snake *is* the pointer: it reaches a parked cursor and rests there (no circling). */
    @Test
    fun snakeReachingParkedCursor_restsOnIt_insteadOfCircling() {
        var s = MultiplayerEngine.startMatch(DuelMode.VS_AI_RIVAL, "P1", "AI")
            .copy(phase = DuelPhase.ROUND_PLAYING, localRole = MultiplayerRole.SNAKE)
        val head = s.snake!!.headPosition
        val cursor = Vec2(head.x, head.y - 120f)
        repeat(120) { s = MultiplayerEngine.tick(s, localInputPos = cursor, rivalInputPos = null, deltaMs = 16L).newState }
        if (s.phase != DuelPhase.ROUND_PLAYING) return // caught the AI prey first: nothing to check
        assertTrue(s.snake!!.headPosition.distanceTo(cursor) < 10f, "the snake should rest on the cursor")
        val rest = s.snake!!.headPosition
        repeat(30) { s = MultiplayerEngine.tick(s, localInputPos = cursor, rivalInputPos = null, deltaMs = 16L).newState }
        if (s.phase == DuelPhase.ROUND_PLAYING) assertTrue(s.snake!!.headPosition.distanceTo(rest) < 1f, "and stay there")
    }

    @Test
    fun testSmartPreyAI_evasionFromSnake() {
        val state = MultiplayerEngine.startMatch(
            mode = DuelMode.VS_AI_RIVAL,
            player1Name = "Player 1",
            player2Name = "AI Titan",
        )

        val prey = PlayerOrb(position = Vec2(400f, 300f), radius = 14f)
        val snake = state.snake!! // snake at Vec2(400f, 460f)

        val nextPreyPos = SmartPreyAI.computeNextPosition(
            prey = prey,
            snake = snake,
            arena = Vec2(800f, 600f),
            dt = 0.1f,
        )

        // Prey should move away from the approaching snake (moving further UP / away from Y=460)
        assertTrue(
            nextPreyPos.y < prey.position.y || abs(nextPreyPos.x - prey.position.x) > 1f,
            "AI Prey should actively evade the approaching snake",
        )
        assertTrue(nextPreyPos.x in 24f..776f, "Prey must stay within arena boundaries")
        assertTrue(nextPreyPos.y in 24f..576f, "Prey must stay within arena boundaries")
    }

    @Test
    fun testCatchCollision_completesRoundAndRecordsCatchTime() {
        val state = MultiplayerEngine.startMatch(
            mode = DuelMode.VS_AI_RIVAL,
            player1Name = "Player 1",
            player2Name = "AI Titan",
        ).copy(phase = DuelPhase.ROUND_PLAYING)

        // Position snake head right next to prey (distance < CATCH_DISTANCE = 30f)
        val closeSnake = state.snake!!.copy(
            headPosition = Vec2(state.prey.position.x + 10f, state.prey.position.y),
        )
        val testState = state.copy(snake = closeSnake)

        val tickResult = MultiplayerEngine.tick(
            state = testState,
            localInputPos = null,
            rivalInputPos = null,
            deltaMs = 50L,
        )

        assertTrue(tickResult.contactOccurred, "Contact should be detected on collision")
        assertTrue(tickResult.roundCompleted, "Round 1 should complete upon catch")
        assertEquals(DuelPhase.ROUND_TRANSITION, tickResult.newState.phase)
        assertTrue(tickResult.newState.round1TimeMs > 0L, "Round 1 catch time must be recorded")
    }

    @Test
    fun testRound2RoleReversal_swapsRoles() {
        val r1State = MultiplayerEngine.startMatch(
            mode = DuelMode.VS_AI_RIVAL,
            player1Name = "Player 1",
            player2Name = "AI Titan",
        ).copy(
            phase = DuelPhase.ROUND_TRANSITION,
            round1TimeMs = 6200L,
            round = 1,
        )

        val r2State = MultiplayerEngine.startRound2(r1State)

        assertEquals(2, r2State.round)
        assertEquals(MultiplayerRole.PREY, r2State.localRole, "Role must reverse to PREY in Round 2")
        assertEquals(DuelPhase.COUNTDOWN, r2State.phase)
        assertEquals("ROUND 2 — FLEE & SURVIVE!", r2State.roundAnnouncement)
    }

    @Test
    fun testMatchWinner_decidedAccuratelyForVsAiRival() {
        val r2Playing = MultiplayerEngine.startMatch(
            mode = DuelMode.VS_AI_RIVAL,
            player1Name = "Player 1",
            player2Name = "AI Titan",
        ).copy(
            phase = DuelPhase.ROUND_PLAYING,
            round = 2,
            localRole = MultiplayerRole.PREY,
            round1TimeMs = 5000L, // Player caught AI in 5.0s
            survivalTimeMs = 8000L, // Player survived 8.0s as Prey against AI
        )

        // Trigger catch in Round 2 where Player survived 8.0s (> 5.0s)
        val closeSnake = r2Playing.snake!!.copy(
            headPosition = Vec2(r2Playing.prey.position.x + 5f, r2Playing.prey.position.y),
        )
        val testState = r2Playing.copy(snake = closeSnake)

        val tickResult = MultiplayerEngine.tick(
            state = testState,
            localInputPos = null,
            rivalInputPos = null,
            deltaMs = 50L,
        )

        assertTrue(tickResult.matchCompleted, "Match should finish after Round 2")
        assertEquals(DuelPhase.MATCH_OVER, tickResult.newState.phase)
        assertNotNull(tickResult.newState.result)
        assertTrue(tickResult.newState.result!!.isLocalWinner, "Player 1 should win since survival time 8.0s > catch time 5.0s")
    }
}
