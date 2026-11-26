package com.snakechase.app

import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.network.DuelSession
import com.snakechase.app.network.FirebaseGuestTransport
import com.snakechase.app.network.FirebaseHostTransport
import com.snakechase.app.network.FirebaseSpectatorTransport
import com.snakechase.app.network.watcherCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpectatorTest {
    private val db = FakeRealtimeDb()
    private val hostUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val guestUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val watchUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val host = DuelSession(
        CoroutineScope(SupervisorJob() + hostUi),
        "Host",
        hostTransports = { FirebaseHostTransport(db, { "h" }, "Host") },
        guestTransports = { FirebaseGuestTransport(db, { "h" }) },
    )
    private val guest = DuelSession(
        CoroutineScope(SupervisorJob() + guestUi),
        "Guest",
        hostTransports = { FirebaseHostTransport(db, { "g" }, "Guest") },
        guestTransports = { FirebaseGuestTransport(db, { "g" }) },
    )
    private val fan = DuelSession(
        CoroutineScope(SupervisorJob() + watchUi),
        "Fan",
        guestTransports = { FirebaseGuestTransport(db, { "s" }) },
        spectatorTransports = { FirebaseSpectatorTransport(db, { "s" }) },
    )

    @AfterTest
    fun tearDown() = runBlocking {
        withContext(watchUi) { fan.dispose() }
        withContext(guestUi) { guest.dispose() }
        withContext(hostUi) { host.dispose() }
        hostUi.close()
        guestUi.close()
        watchUi.close()
    }

    private suspend fun awaitTrue(what: String, check: suspend () -> Boolean) {
        try {
            withTimeout(8_000) { while (!check()) delay(20) }
        } catch (e: Exception) {
            throw AssertionError("Timed out: $what", e)
        }
    }

    @Test
    fun spectator_followsTheDuel_isCounted_andCannotTouchIt(): Unit = runBlocking {
        withContext(hostUi) { host.startHosting(online = true) }
        val code = withContext(hostUi) { host.state.roomCode }
        withContext(guestUi) { guest.joinRoom(code, online = true) }
        awaitTrue("guest joined") { withContext(guestUi) { guest.state.isRoomActivated } }

        withContext(watchUi) { fan.spectateRoom(code) }
        awaitTrue("watcher registered") { watcherCount(db, code).first() == 1 }
        assertEquals(JsonPrimitive("g"), db.get("rooms/$code/guestUid"), "the spectator must not take the guest seat")

        withContext(hostUi) { host.hostStartMatch() }
        awaitTrue("spectator sees the match start") {
            withContext(watchUi) { fan.state.phase == DuelPhase.COUNTDOWN && fan.state.isSpectator }
        }
        assertEquals("Host", withContext(watchUi) { fan.state.player1Name })
        assertEquals("Guest", withContext(watchUi) { fan.state.player2Name })

        // Steering from the spectator goes nowhere.
        withContext(watchUi) { fan.sendGuestInput(Vec2(10f, 10f)) }
        delay(300)
        assertNull(withContext(hostUi) { host.rivalInputPos }, "a spectator's input must never reach the host")

        // The host pauses: the guest gets the 10-second clock, the spectator only sees "paused"
        // and can never be handed the win when the pause runs out.
        withContext(hostUi) { host.pause() }
        awaitTrue("guest sees the pause") { withContext(guestUi) { guest.state.pausedBy == "Host" } }
        awaitTrue("spectator sees the pause") { withContext(watchUi) { fan.state.pausedBy == "Host" } }
        assertTrue(withContext(guestUi) { guest.pauseMsLeft() != null }, "the guest's 10-second clock runs")
        assertNull(withContext(watchUi) { fan.pauseMsLeft() }, "no clock for the spectator")
        withContext(watchUi) { fan.rivalPauseExpired() }
        assertNull(withContext(watchUi) { fan.state.opponentLeftReason }, "a spectator never 'wins' a pause timeout")
        withContext(hostUi) { host.resume() }
        awaitTrue("pause cleared for the spectator") { withContext(watchUi) { fan.state.pausedBy == null } }

        withContext(watchUi) { fan.leave() }
        awaitTrue("watcher removed on leave") { watcherCount(db, code).first() == 0 }
        assertTrue(withContext(guestUi) { guest.state.isRoomActivated }, "the players' duel is unaffected")
    }
}
