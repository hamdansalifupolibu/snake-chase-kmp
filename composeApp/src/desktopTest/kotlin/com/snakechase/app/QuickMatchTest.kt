package com.snakechase.app

import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.network.DuelSession
import com.snakechase.app.network.FirebaseGuestTransport
import com.snakechase.app.network.FirebaseHostTransport
import com.snakechase.app.network.QuickMatch
import com.snakechase.app.network.firebase.RealtimeDb
import com.snakechase.app.network.firebase.RtdbEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuickMatchTest {
    private val db = FakeRealtimeDb()
    private val uiA = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val uiB = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scopeA = CoroutineScope(SupervisorJob() + uiA)
    private val scopeB = CoroutineScope(SupervisorJob() + uiB)

    // "uid-b" > "uid-a", so B is the one who sends the offer (and hosts).
    private val qmA = QuickMatch(db, { "uid-a" }, scopeA)
    private val qmB = QuickMatch(db, { "uid-b" }, scopeB)

    @AfterTest
    fun tearDown() = runBlocking {
        withContext(uiA) { qmA.cancel() }
        withContext(uiB) { qmB.cancel() }
        scopeA.cancel()
        scopeB.cancel()
        uiA.close()
        uiB.close()
    }

    private suspend fun awaitTrue(what: String, timeoutMs: Long = 10_000, check: suspend () -> Boolean) {
        try {
            withTimeout(timeoutMs) { while (!check()) delay(25) }
        } catch (e: Exception) {
            throw AssertionError("Timed out: $what", e)
        }
    }

    @Test
    fun twoStrangers_getPaired_bothAccept_guestReceivesRoomCode(): Unit = runBlocking {
        withContext(uiA) { qmA.start(0, "Ama") { error("A must not host") } }
        withContext(uiB) { qmB.start(0, "Kofi") { "ROOM42" } }

        awaitTrue("both see the offer") {
            withContext(uiA) { qmA.status is QuickMatch.Status.Offer } && withContext(uiB) { qmB.status is QuickMatch.Status.Offer }
        }
        val offerA = withContext(uiA) { qmA.status as QuickMatch.Status.Offer }
        assertEquals("Kofi", offerA.opponentName)
        assertTrue(!offerA.iAmHost)

        withContext(uiA) { qmA.accept() }
        withContext(uiB) { qmB.accept() }

        awaitTrue("guest gets the room code") {
            (withContext(uiA) { qmA.status } as? QuickMatch.Status.Matched)?.roomCode == "ROOM42"
        }
        val hostSide = withContext(uiB) { qmB.status as QuickMatch.Status.Matched }
        assertTrue(hostSide.iAmHost)
    }

    /**
     * Regression (live test 2026-11-19): over a real connection the host's event stream delivered
     * an out-of-date picture of the offer, so the host "forgot" its own accept and both players sat
     * on "waiting" until the offer expired. Here every listener sees a STALE full snapshot right
     * after each real change; the match must still start.
     */
    @Test
    fun bothAccept_evenWhenTheEventStreamReplaysStaleSnapshots(): Unit = runBlocking {
        val flaky = StaleEchoDb(db)
        val a = QuickMatch(flaky, { "uid-a" }, scopeA)
        val b = QuickMatch(flaky, { "uid-b" }, scopeB)
        try {
            withContext(uiA) { a.start(2, "Ama") { error("A must not host") } }
            withContext(uiB) { b.start(2, "Kofi") { "ROOM77" } }
            awaitTrue("both see the offer") {
                withContext(uiA) { a.status is QuickMatch.Status.Offer } && withContext(uiB) { b.status is QuickMatch.Status.Offer }
            }
            withContext(uiB) { b.accept() } // host first, exactly as on the desktop
            delay(300)
            withContext(uiA) { a.accept() }
            awaitTrue("guest gets the room code") {
                (withContext(uiA) { a.status } as? QuickMatch.Status.Matched)?.roomCode == "ROOM77"
            }
        } finally {
            withContext(uiA) { a.cancel() }
            withContext(uiB) { b.cancel() }
        }
    }

    @Test
    fun declinedOffer_sendsBothBackToSearching(): Unit = runBlocking {
        withContext(uiA) { qmA.start(1, "Ama") { null } }
        withContext(uiB) { qmB.start(1, "Kofi") { "X" } }
        awaitTrue("offer shown") { withContext(uiA) { qmA.status is QuickMatch.Status.Offer } }
        withContext(uiA) { qmA.decline() }
        awaitTrue("host back to searching (or re-offered)") {
            withContext(uiB) { qmB.status is QuickMatch.Status.Searching || qmB.status is QuickMatch.Status.Offer }
        }
        awaitTrue("guest back to searching") { withContext(uiA) { qmA.status !is QuickMatch.Status.Matched } }
    }
}

/**
 * Wraps a [RealtimeDb] so each listener, after every real event, also receives the full snapshot
 * it saw when it first connected — an old picture arriving late, like a stream that reconnects
 * or reorders. Reads and writes are untouched.
 */
private class StaleEchoDb(private val real: RealtimeDb) : RealtimeDb by real {
    override fun listen(path: String): Flow<RtdbEvent> = flow {
        var first: RtdbEvent? = null
        real.listen(path).collect { e ->
            if (first == null) first = e
            emit(e)
            first?.let { emit(it) }
        }
    }
}

class RematchTest {
    private val db = FakeRealtimeDb()
    private val hostUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val guestUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
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

    @AfterTest
    fun tearDown() = runBlocking {
        withContext(hostUi) { host.dispose() }
        withContext(guestUi) { guest.dispose() }
        hostUi.close()
        guestUi.close()
    }

    private suspend fun awaitTrue(what: String, check: suspend () -> Boolean) {
        try {
            withTimeout(6_000) { while (!check()) delay(20) }
        } catch (e: Exception) {
            throw AssertionError("Timed out: $what", e)
        }
    }

    @Test
    fun rematchStartsOnlyWhenBothPlayersVoteYes(): Unit = runBlocking {
        withContext(hostUi) { host.startHosting(online = true) }
        val code = withContext(hostUi) { host.state.roomCode }
        withContext(guestUi) { guest.joinRoom(code, online = true) }
        awaitTrue("joined") { withContext(guestUi) { guest.state.isRoomActivated } }

        withContext(guestUi) { guest.voteRematch(true) }
        awaitTrue("host sees the request") { withContext(hostUi) { host.rematchRemote == true } }
        assertTrue(withContext(guestUi) { guest.state.phase != DuelPhase.COUNTDOWN }, "one vote is not enough")

        withContext(hostUi) { host.voteRematch(true) }
        awaitTrue("both agreed → new match") { withContext(guestUi) { guest.state.phase == DuelPhase.COUNTDOWN } }
    }
}
