package com.snakechase.app

import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.network.DuelSession
import com.snakechase.app.network.LanGuestTransport
import com.snakechase.app.network.LanHostTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Host and guest DuelSessions talking over real localhost sockets — the full lobby flow,
 * with each session confined to its own single "UI" thread exactly like in the app.
 */
class DuelSessionEndToEndTest {
    private val hostUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val guestUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val hostScope = CoroutineScope(SupervisorJob() + hostUi)
    private val guestScope = CoroutineScope(SupervisorJob() + guestUi)
    private val port = ServerSocket(0).use { it.localPort }
    private val host = DuelSession(
        hostScope,
        localName = "Host Tester",
        hostTransports = { LanHostTransport(port) },
        guestTransports = { LanGuestTransport(port) },
    )
    private val guest = DuelSession(
        guestScope,
        localName = "Guest Tester",
        hostTransports = { LanHostTransport(port) },
        guestTransports = { LanGuestTransport(port) },
    )

    @AfterTest
    fun tearDown() = runBlocking {
        withContext(hostUi) { host.dispose() }
        withContext(guestUi) { guest.dispose() }
        hostScope.cancel()
        guestScope.cancel()
        hostUi.close()
        guestUi.close()
    }

    private suspend fun awaitTrue(what: String, check: suspend () -> Boolean) {
        try {
            // Real sockets: noticing a dropped peer can take a few seconds on a busy machine.
            withTimeout(15_000) { while (!check()) delay(20) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $what", e)
        }
    }

    private suspend fun <T> onHost(block: () -> T) = withContext(hostUi) { block() }
    private suspend fun <T> onGuest(block: () -> T) = withContext(guestUi) { block() }

    @Test
    fun guestJoins_hostStarts_guestLeaves(): Unit = runBlocking {
        assertTrue(withContext(hostUi) { host.startHosting(online = false) })
        val code = onHost { host.state.roomCode }

        onGuest { guest.joinRoom(code, online = false, hostAddress = "127.0.0.1") }
        awaitTrue("host sees guest") { onHost { host.state.isRoomActivated } }
        awaitTrue("guest receives RoomInfo") { onGuest { guest.state.isRoomActivated } }
        assertEquals("Guest Tester", onHost { host.state.player2Name })
        assertEquals("Host Tester", onGuest { guest.state.player1Name })

        // Heartbeat produces a latency reading on both sides.
        awaitTrue("host latency") { onHost { host.state.latencyMs != null } }
        awaitTrue("guest latency") { onGuest { guest.state.latencyMs != null } }

        onHost { host.hostStartMatch() }
        awaitTrue("guest enters countdown") { onGuest { guest.state.phase == DuelPhase.COUNTDOWN } }
        val hostRole = onHost { host.state.localRole }
        assertTrue(onGuest { guest.state.localRole } != hostRole, "roles must be opposite")

        // Host ends round 1: guest's transition overlay must describe round 1, not round 2.
        onHost {
            host.onHostTick(com.snakechase.app.engine.MultiplayerTickResult(newState = host.state, roundCompleted = true))
        }
        awaitTrue("guest shows round transition") { onGuest { guest.state.phase == DuelPhase.ROUND_TRANSITION } }
        assertEquals(1, onGuest { guest.state.round }, "overlay must say ROUND 1 CONCLUDED")

        onGuest { guest.leave() }
        awaitTrue("host notices guest left") { onHost { host.state.opponentLeftReason != null } }
    }

    @Test
    fun wrongRoomCode_isRejected(): Unit = runBlocking {
        assertTrue(withContext(hostUi) { host.startHosting(online = false) })
        // Real codes are 1000..9999, so 0000 can never match.
        onGuest { guest.joinRoom("0000", online = false, hostAddress = "127.0.0.1") }
        awaitTrue("guest told join refused") { onGuest { guest.statusText?.contains("refused") == true } }
        assertTrue(!onHost { host.state.isRoomActivated })
        assertNotNull(onGuest { guest.statusText })
    }
}
