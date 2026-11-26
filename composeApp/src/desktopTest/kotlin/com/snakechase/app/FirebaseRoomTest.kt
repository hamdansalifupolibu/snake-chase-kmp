package com.snakechase.app

import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.network.DirectionReader
import com.snakechase.app.network.DuelSession
import com.snakechase.app.network.FirebaseGuestTransport
import com.snakechase.app.network.FirebaseHostTransport
import com.snakechase.app.network.FirebaseRooms
import com.snakechase.app.network.firebase.RealtimeDb
import com.snakechase.app.network.firebase.RtdbEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * In-memory Realtime Database: a flat map of leaf paths, with live listeners that get the
 * current value first and then every change below their path — like the real SSE stream.
 */
class FakeRealtimeDb : RealtimeDb {
    private val leaves = sortedMapOf<String, JsonElement>()
    private val lock = Mutex()
    private val changes = MutableSharedFlow<Pair<String, JsonElement>>(extraBufferCapacity = 10_000)
    private var pushCounter = 0

    private fun norm(p: String) = p.trim('/')
    private fun under(p: String, prefix: String) = prefix.isEmpty() || p == prefix || p.startsWith("$prefix/")

    private fun flatten(prefix: String, v: JsonElement, out: MutableMap<String, JsonElement>) {
        // Like the real database, writing null deletes: nothing is stored.
        when (v) {
            is JsonObject -> v.forEach { (k, child) -> flatten("$prefix/$k", child, out) }
            is JsonNull -> Unit
            else -> out[prefix] = v
        }
    }

    private fun snapshot(path: String): JsonElement {
        val p = norm(path)
        leaves[p]?.let { return it }
        val children = leaves.filterKeys { it.startsWith("$p/") }
        if (children.isEmpty()) return JsonNull
        val root = mutableMapOf<String, Any>()
        children.forEach { (k, v) ->
            var node = root
            val parts = k.removePrefix("$p/").split('/')
            parts.dropLast(1).forEach { part ->
                @Suppress("UNCHECKED_CAST")
                node = node.getOrPut(part) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
            }
            node[parts.last()] = v
        }
        fun toJson(m: Map<String, Any>): JsonObject = JsonObject(
            m.mapValues { (_, v) ->
                @Suppress("UNCHECKED_CAST")
                if (v is Map<*, *>) toJson(v as Map<String, Any>) else v as JsonElement
            },
        )
        return toJson(root)
    }

    private suspend fun write(path: String, value: JsonElement) {
        val p = norm(path)
        lock.withLock {
            leaves.keys.filter { under(it, p) }.forEach { leaves.remove(it) }
            val flat = mutableMapOf<String, JsonElement>()
            flatten(p, value, flat)
            leaves.putAll(flat)
        }
        changes.emit(p to value)
    }

    override suspend fun put(path: String, value: JsonElement) = write(path, value)

    override suspend fun push(path: String, value: JsonElement): String {
        val key = lock.withLock { "k%08d".format(++pushCounter) }
        write("${norm(path)}/$key", value)
        return key
    }

    override suspend fun putIfAbsent(path: String, value: JsonElement): Boolean {
        val taken = lock.withLock { leaves.keys.any { under(it, norm(path)) } }
        if (taken) return false
        write(path, value)
        return true
    }

    override suspend fun get(path: String): JsonElement? = lock.withLock { snapshot(path) }.takeUnless { it is JsonNull }

    override suspend fun delete(path: String) = write(path, JsonNull)

    override fun listen(path: String): Flow<RtdbEvent> = channelFlow {
        val p = norm(path)
        val job = launch {
            changes.collect { (changed, value) ->
                when {
                    under(changed, p) -> send(RtdbEvent("/" + changed.removePrefix(p).trim('/'), value))
                    under(p, changed) -> send(RtdbEvent("/", lock.withLock { snapshot(p) }))
                }
            }
        }
        send(RtdbEvent("/", lock.withLock { snapshot(p) }))
        job.join()
    }
}

/** Rooms whose host vanished (app closed or killed) are cleared by the next player who hosts. */
class StaleRoomSweepTest {
    @Test
    fun sweep_removesDeadRooms_andKeepsLiveOnes() = runBlocking {
        val db = FakeRealtimeDb()
        val now = 10_000_000L
        fun room(code: String, lastSeen: Long) = runBlocking {
            db.put("rooms/$code/meta/hostName", JsonPrimitive("h"))
            db.put("roomIndex/$code", JsonPrimitive(lastSeen))
        }
        room("DEAD01", lastSeen = now - FirebaseRooms.STALE_MS - 1)
        room("DEAD02", lastSeen = now - 3 * FirebaseRooms.STALE_MS)
        room("LIVE01", lastSeen = now - 30_000) // heartbeat 30 s ago: a game in progress

        assertEquals(2, FirebaseRooms.sweepStale(db, nowMs = now))

        assertEquals(null, db.get("rooms/DEAD01"))
        assertEquals(null, db.get("roomIndex/DEAD02"))
        assertTrue(db.get("rooms/LIVE01") != null, "a live room must survive the sweep")
        assertTrue(db.get("roomIndex/LIVE01") != null)
    }
}

class FirebaseRoomTest {
    private val db = FakeRealtimeDb()
    private val hostUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val guestUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val hostScope = CoroutineScope(SupervisorJob() + hostUi)
    private val guestScope = CoroutineScope(SupervisorJob() + guestUi)

    private fun session(scope: CoroutineScope, name: String, uid: String) = DuelSession(
        uiScope = scope,
        localName = name,
        hostTransports = { FirebaseHostTransport(db, { uid }, name) },
        guestTransports = { FirebaseGuestTransport(db, { uid }) },
    )

    private val host = session(hostScope, "Phone Host", "uid-host")
    private val guest = session(guestScope, "Web Guest", "uid-guest")

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
            withTimeout(5_000) { while (!check()) delay(20) }
        } catch (e: Exception) {
            throw AssertionError("Timed out: $what", e)
        }
    }

    @Test
    fun inviteCode_join_start_input_and_leave_overFirebase(): Unit = runBlocking {
        assertTrue(withContext(hostUi) { host.startHosting(online = true) })
        val code = withContext(hostUi) { host.state.roomCode }
        assertEquals(6, code.length, "online codes are 6 characters")

        // The guest types the code in lowercase with a dash — still matches.
        withContext(guestUi) { guest.joinRoom(code.lowercase().chunked(3).joinToString("-"), online = true) }
        awaitTrue("host sees guest") { withContext(hostUi) { host.state.isRoomActivated } }
        awaitTrue("guest gets RoomInfo") { withContext(guestUi) { guest.state.player1Name == "Phone Host" } }
        awaitTrue("heartbeat latency") { withContext(guestUi) { guest.state.latencyMs != null } }

        withContext(hostUi) { host.hostStartMatch() }
        awaitTrue("guest countdown") { withContext(guestUi) { guest.state.phase == DuelPhase.COUNTDOWN } }

        // Lossy input channel: only the latest pointer position needs to arrive.
        repeat(5) { i ->
            withContext(guestUi) { guest.sendGuestInput(Vec2(100f + i, 200f)) }
            delay(60)
        }
        awaitTrue("host gets guest input") { withContext(hostUi) { host.rivalInputPos?.x == 104f } }

        withContext(guestUi) { guest.leave() }
        awaitTrue("host notices leave") { withContext(hostUi) { host.state.opponentLeftReason != null } }
    }

    @Test
    fun secondGuest_isRefused_andUnknownCode_isReported(): Unit = runBlocking {
        assertTrue(withContext(hostUi) { host.startHosting(online = true) })
        val code = withContext(hostUi) { host.state.roomCode }
        withContext(guestUi) { guest.joinRoom(code, online = true) }
        awaitTrue("first guest in") { withContext(hostUi) { host.state.isRoomActivated } }

        val thirdUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val third = session(CoroutineScope(SupervisorJob() + thirdUi), "Late Player", "uid-late")
        withContext(thirdUi) { third.joinRoom(code, online = true) }
        awaitTrue("room full message") { withContext(thirdUi) { third.statusText?.contains("full") == true } }

        withContext(thirdUi) { third.joinRoom("ZZZZZZ", online = true) }
        awaitTrue("unknown code message") { withContext(thirdUi) { third.statusText?.contains("No room") == true } }
        withContext(thirdUi) { third.dispose() }
        thirdUi.close()
    }

    @Test
    fun reader_deliversEachEventOnce_acrossStreamReconnects() {
        val got = mutableListOf<String>()
        val reader = DirectionReader("state") { got += it }
        val snapshot = JsonObject(mapOf("events" to JsonObject(mapOf("k1" to JsonPrimitive("a"), "k2" to JsonPrimitive("b")))))
        reader.handle(RtdbEvent("/", snapshot))
        reader.handle(RtdbEvent("/events/k3", JsonPrimitive("c")))
        reader.handle(RtdbEvent("/", snapshot)) // reconnect replays the old events — must be skipped
        reader.handle(RtdbEvent("/state", JsonPrimitive("s1")))
        assertEquals(listOf("a", "b", "c", "s1"), got)
    }
}
