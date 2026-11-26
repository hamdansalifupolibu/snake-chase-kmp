package com.snakechase.app.network

import com.snakechase.app.network.firebase.RealtimeDb
import com.snakechase.app.network.firebase.RtdbEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/**
 * Live duel rooms on Firebase Realtime Database — the internet path for invite codes and
 * quick match, identical on Android, desktop, iOS and web.
 *
 * Each direction has two channels:
 *  - `events/<pushKey>` — reliable and ordered (join, round start, chat, heartbeat…). Push keys
 *    are chronological, so the reader can skip anything it has already processed after a reconnect.
 *  - `state` / `input` — a single slot overwritten in place: only the newest snapshot matters,
 *    and a slow connection simply skips stale ones instead of falling behind.
 */
object FirebaseRooms {
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // no 0/O, 1/I/L
    const val GUEST_ID = 1

    fun newCode(): String {
        while (true) {
            val code = (1..6).map { CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
            if (!code.startsWith("CHASE")) return code
        }
    }

    fun room(code: String) = "rooms/$code"

    /**
     * `roomIndex/{code}` = when the host was last seen (server time). The REST API has no
     * "delete when I disconnect", so a host that closes or kills the app leaves its room behind;
     * this heartbeat is how everyone else can tell it's dead (see database.rules.json).
     */
    fun index(code: String) = "roomIndex/$code"
    const val HEARTBEAT_MS = 60_000L

    /** No heartbeat for this long = the host is gone; any player may delete the room. Mirrors the rules. */
    const val STALE_MS = 10 * 60_000L

    /**
     * Deletes up to [limit] dead rooms. Runs when someone hosts, so every new game tidies up after
     * old ones; no server needed. Best effort: a room revived meanwhile is protected by the rules.
     */
    suspend fun sweepStale(db: RealtimeDb, nowMs: Long, limit: Int = 20): Int {
        val dead = runCatching { db.keysWithValueAtMost("roomIndex", nowMs - STALE_MS, limit) }.getOrDefault(emptyList())
        var removed = 0
        dead.forEach { code ->
            // Room first: its rule checks that the index entry is still there and stale.
            if (runCatching { db.delete(room(code)) }.isSuccess) removed++
            runCatching { db.delete(index(code)) }
        }
        return removed
    }
}

/**
 * Ordered sender for `events` plus a conflated "latest wins" sender for the lossy slot.
 * It has its own scope so closing the room stops listening at once while the last messages
 * (e.g. "Leave") still get written — see [drain].
 */
private const val LOSSY_IN_FLIGHT = 3

private class DirectionWriter(
    private val db: RealtimeDb,
    private val eventsPath: String,
    private val latestPath: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val events = Channel<String>(Channel.UNLIMITED)
    private val latest = Channel<String>(Channel.CONFLATED)

    // Lossy updates go out LOSSY_IN_FLIGHT at a time: one write takes a full round trip to the
    // database (~0.2 s from Europe), so waiting for each one capped duels at ~4 updates a second.
    // Measured live: 1 in flight = 4.2/s, 3 in flight = 12.7/s, same ~220 ms delay. Receivers
    // drop anything older than what they already have (see DuelSession).
    private val jobs = listOf(scope.launch { for (m in events) retrying { db.push(eventsPath, JsonPrimitive(m)) } }) +
        List(LOSSY_IN_FLIGHT) { scope.launch { for (m in latest) runCatching { db.put(latestPath, JsonPrimitive(m)) } } }

    fun send(message: String, lossy: Boolean) {
        if (lossy) latest.trySend(message) else events.trySend(message)
    }

    /** Stop accepting messages, give the queued ones up to [timeoutMs] to reach Firebase, then stop. */
    suspend fun drain(timeoutMs: Long = 2_000) {
        events.close()
        latest.close()
        withTimeoutOrNull(timeoutMs) { jobs.joinAll() }
        scope.cancel()
    }

    private suspend fun retrying(block: suspend () -> Unit) {
        repeat(3) { attempt ->
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                delay(300L * (attempt + 1))
            }
        }
    }
}

/**
 * Reads one direction (`events` map + one lossy key) from a listened node, delivering each event
 * exactly once even across stream reconnects, and the lossy value whenever it changes.
 */
internal class DirectionReader(private val lossyKey: String, private val onMessage: (String) -> Unit) {
    private var lastEventKey = ""

    fun handle(e: RtdbEvent) {
        val segments = e.path.trim('/').split('/').filter { it.isNotEmpty() }
        when {
            segments.isEmpty() -> (e.data as? JsonObject)?.let { root ->
                (root["events"] as? JsonObject)?.let(::deliverEvents)
                if (!e.isPatch || lossyKey in root) root[lossyKey]?.let(::deliverValue)
            }
            segments[0] == "events" && segments.size == 1 -> (e.data as? JsonObject)?.let(::deliverEvents)
            segments[0] == "events" && segments.size == 2 -> deliverEvent(segments[1], e.data)
            segments[0] == lossyKey -> deliverValue(e.data)
        }
    }

    private fun deliverEvents(map: JsonObject) = map.keys.sorted().forEach { k -> deliverEvent(k, map.getValue(k)) }

    private fun deliverEvent(key: String, value: kotlinx.serialization.json.JsonElement) {
        if (key <= lastEventKey) return
        lastEventKey = key
        deliverValue(value)
    }

    private fun deliverValue(value: kotlinx.serialization.json.JsonElement) {
        if (value is JsonNull) return
        runCatching { value.jsonPrimitive.content }.getOrNull()?.let(onMessage)
    }
}

/** Keeps listening to [path], reconnecting with backoff (e.g. after a token refresh) until cancelled. */
private fun CoroutineScope.listenForever(db: RealtimeDb, path: String, onEvent: (RtdbEvent) -> Unit, onFatal: (String) -> Unit): Job = launch {
    var failures = 0
    while (isActive) {
        try {
            db.listen(path).collect {
                failures = 0
                onEvent(it)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (++failures >= 6) {
                onFatal(e.message ?: "Connection to the room was lost.")
                return@launch
            }
        }
        delay(minOf(500L * (1 shl failures), 5_000L))
    }
}

class FirebaseHostTransport(
    private val db: RealtimeDb,
    private val uid: suspend () -> String,
    private val hostName: String,
) : HostTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var code: String? = null
    private var writer: DirectionWriter? = null
    private var guestUid: String? = null

    override suspend fun open(listener: DuelServerListener): Result<HostRoom> = runCatching {
        val me = uid()
        var chosen: String? = null
        repeat(5) {
            if (chosen != null) return@repeat
            val candidate = FirebaseRooms.newCode()
            val meta = buildJsonObject {
                put("hostUid", me)
                put("hostName", hostName.take(24))
                putJsonObject("createdAt") { put(".sv", "timestamp") }
            }
            if (db.putIfAbsent("${FirebaseRooms.room(candidate)}/meta", meta)) chosen = candidate
        }
        val c = chosen ?: error("Could not reserve a room code, try again.")
        code = c
        val room = FirebaseRooms.room(c)

        // "I'm alive" heartbeat for this room, refreshed until the room closes.
        scope.launch {
            while (isActive) {
                runCatching { db.put(FirebaseRooms.index(c), buildJsonObject { put(".sv", "timestamp") }) }
                delay(FirebaseRooms.HEARTBEAT_MS)
            }
        }
        // Tidy up rooms whose hosts vanished, so the database doesn't fill with dead rooms.
        scope.launch { FirebaseRooms.sweepStale(db, nowMs = Clock.System.now().toEpochMilliseconds()) }
        writer = DirectionWriter(db, "$room/toGuest/events", "$room/toGuest/state")

        // A guest has joined when their uid appears in the guestUid slot.
        scope.listenForever(db, "$room/guestUid", onEvent = { e ->
            val g = (e.data as? JsonPrimitive)?.content
            if (g != null && g != guestUid) {
                guestUid = g
                listener.onClientConnected(FirebaseRooms.GUEST_ID)
            }
        }, onFatal = { listener.onClientDisconnected(FirebaseRooms.GUEST_ID) })

        val reader = DirectionReader("input") { listener.onClientMessage(FirebaseRooms.GUEST_ID, it) }
        scope.listenForever(
            db,
            "$room/toHost",
            onEvent = reader::handle,
            onFatal = { listener.onClientDisconnected(FirebaseRooms.GUEST_ID) },
        )
        HostRoom(code = c)
    }

    override fun send(clientId: Int, message: String, lossy: Boolean) {
        writer?.send(message, lossy)
    }

    /** Room-full / wrong-code can't happen here (the rules enforce one guest), so just drop them. */
    override fun disconnect(clientId: Int) {
        guestUid = null
    }

    override fun close() {
        val c = code
        val w = writer
        code = null
        writer = null
        scope.cancel()
        CoroutineScope(Dispatchers.Default).launch {
            withContext(NonCancellable) {
                // Let the last messages (e.g. "Leave") reach the guest before the room disappears.
                w?.drain()
                // Best-effort cleanup so finished rooms don't pile up in the database. The index goes
                // first: its rule needs the room (and its hostUid) to still exist.
                if (c != null) {
                    delay(1_500)
                    runCatching { db.delete(FirebaseRooms.index(c)) }
                    runCatching { db.delete(FirebaseRooms.room(c)) }
                }
            }
        }
    }
}

class FirebaseGuestTransport(
    private val db: RealtimeDb,
    private val uid: suspend () -> String,
) : GuestTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var writer: DirectionWriter? = null

    override fun connect(code: String, address: String?, listener: GuestListener) {
        scope.launch {
            try {
                val me = uid()
                val room = FirebaseRooms.room(code)
                val meta = db.get("$room/meta")
                if (meta == null || meta !is JsonObject) {
                    listener.onClosed("No room with code $code — check the code with your friend.")
                    return@launch
                }
                if (meta["hostUid"]?.jsonPrimitive?.content == me) {
                    listener.onClosed("That's your own room — share the code with a friend.")
                    return@launch
                }
                if (!db.putIfAbsent("$room/guestUid", JsonPrimitive(me))) {
                    val current = (db.get("$room/guestUid") as? JsonPrimitive)?.content
                    if (current != me) {
                        listener.onClosed("Room $code is full — a duel is already in progress.")
                        return@launch
                    }
                }
                writer = DirectionWriter(db, "$room/toHost/events", "$room/toHost/input")
                listener.onConnected()
                val reader = DirectionReader("state", listener::onMessage)
                scope.listenForever(db, "$room/toGuest", onEvent = reader::handle, onFatal = listener::onClosed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listener.onClosed(e.message ?: "Could not join room $code.")
            }
        }
    }

    override fun send(message: String, lossy: Boolean) {
        writer?.send(message, lossy)
    }

    override fun disconnect() {
        val w = writer
        writer = null
        scope.cancel()
        if (w != null) CoroutineScope(Dispatchers.Default).launch { withContext(NonCancellable) { w.drain() } }
    }
}

/**
 * Watches someone else's online duel. Read-only by construction: it never claims the guest seat
 * and [send] does nothing, so a spectator can't affect the match. It reads the host's broadcast
 * (`toGuest`, which the rules let any signed-in player read) and registers in `watchers` so the
 * players can see how many people are watching.
 */
class FirebaseSpectatorTransport(
    private val db: RealtimeDb,
    private val uid: suspend () -> String,
) : GuestTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcherPath: String? = null

    override fun connect(code: String, address: String?, listener: GuestListener) {
        scope.launch {
            try {
                val me = uid()
                val room = FirebaseRooms.room(code)
                if (db.get("$room/meta") !is JsonObject) {
                    listener.onClosed("No live room with code $code.")
                    return@launch
                }
                val path = "$room/watchers/$me"
                runCatching { db.put(path, buildJsonObject { put(".sv", "timestamp") }) }
                watcherPath = path
                listener.onConnected()
                // The full history arrives first (names, current round…), so a late joiner catches up.
                val reader = DirectionReader("state", listener::onMessage)
                scope.listenForever(db, "$room/toGuest", onEvent = reader::handle, onFatal = listener::onClosed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listener.onClosed(e.message ?: "Could not watch room $code.")
            }
        }
    }

    /** Spectators never talk to the match. */
    override fun send(message: String, lossy: Boolean) = Unit

    override fun disconnect() {
        val path = watcherPath
        watcherPath = null
        scope.cancel()
        if (path != null) {
            CoroutineScope(Dispatchers.Default).launch { withContext(NonCancellable) { runCatching { db.delete(path) } } }
        }
    }
}

/** Live number of people watching room [code] (spectators register under `watchers/{uid}`). */
fun watcherCount(db: RealtimeDb, code: String): Flow<Int> = flow {
    val present = mutableSetOf<String>()
    db.listen("${FirebaseRooms.room(code)}/watchers").collect { e ->
        val key = e.path.trim('/')
        when {
            key.isEmpty() -> {
                present.clear()
                (e.data as? JsonObject)?.keys?.let(present::addAll)
            }
            e.data is JsonNull -> present.remove(key)
            else -> present.add(key.substringBefore('/'))
        }
        emit(present.size)
    }
}
