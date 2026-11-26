package com.snakechase.app.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snakechase.app.network.firebase.RealtimeDb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Live matchmaking with strangers, pool-game style: wait in a tier queue, get paired, both
 * players ACCEPT within the countdown, then a live Firebase room opens automatically.
 *
 * Realtime Database layout:
 *   queue/{tier}/{uid}        = { name, ts }                         (ts refreshed every 5 s)
 *   queue/{tier}/{uid}/offer  = { from, fromName, hostAccepted, guestAccepted, room? }
 *
 * Pairing rule: only the player with the *higher* uid sends an offer. Every pair therefore has
 * exactly one initiator, so two searchers can never cross-offer each other; the offer slot is
 * written with putIfAbsent, so a waiting player can only ever hold one offer.
 *
 * All state changes run on [uiScope] (the composition's main thread), like [DuelSession].
 */
class QuickMatch(
    private val db: RealtimeDb,
    private val uid: suspend () -> String,
    private val uiScope: CoroutineScope,
) {
    sealed interface Status {
        data object Idle : Status
        data class Searching(val waitedSec: Int, val nobodyOnline: Boolean) : Status
        data class Offer(
            val opponentName: String,
            val iAmHost: Boolean,
            val iAccepted: Boolean,
            val theyAccepted: Boolean,
            val secondsLeft: Int,
        ) : Status

        /** Both accepted. Host: open a room (see [start]'s `openRoom`). Guest: join [roomCode]. */
        data class Matched(val opponentName: String, val iAmHost: Boolean, val roomCode: String?) : Status
        data class Failed(val message: String) : Status
    }

    var status by mutableStateOf<Status>(Status.Idle)
        private set

    private var job: Job? = null
    private var tier = 0
    private var me = ""

    /** Path of the offer currently in play (on my entry when I'm the guest, on theirs when I host). */
    private var offerPath: String? = null

    /**
     * Joins the [tierIndex] queue as [name]. When matched as host, [openRoom] must open a live
     * room and return its code; the guest receives that code and joins it.
     */
    fun start(tierIndex: Int, name: String, openRoom: suspend () -> String?) {
        cancel()
        tier = tierIndex
        status = Status.Searching(0, nobodyOnline = false)
        job = uiScope.launch {
            try {
                me = uid()
                val myEntry = "queue/$tier/$me"
                db.put(
                    myEntry,
                    buildJsonObject {
                        put("name", name.take(24))
                        putJsonObject("ts") { put(".sv", "timestamp") }
                    },
                )
                launch {
                    // keep my entry fresh so others know I'm still here
                    while (isActive) {
                        delay(5_000)
                        if (status is Status.Searching) runCatching { db.put("$myEntry/ts", buildJsonObject { put(".sv", "timestamp") }) }
                    }
                }
                launch {
                    // incoming offers (someone with a higher uid picked me)
                    db.listen("$myEntry/offer").collect { e ->
                        if (e.path == "/" && e.data is JsonObject && status is Status.Searching) {
                            offerPath = "$myEntry/offer"
                            runOffer(e.data as JsonObject, iAmHost = false, openRoom)
                        }
                    }
                }
                searchLoop(myEntry, name, openRoom)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = Status.Failed(e.message ?: "Quick match is unavailable right now.")
            }
        }
    }

    private suspend fun searchLoop(myEntry: String, name: String, openRoom: suspend () -> String?) {
        var waited = 0
        while (true) {
            if (status is Status.Searching) {
                val serverNow = (db.get("$myEntry/ts") as? JsonPrimitive)?.longOrNull ?: 0L
                val queue = db.get("queue/$tier") as? JsonObject
                // Players who closed the app mid-search never removed themselves: clear the long-silent ones.
                queue.orEmpty().forEach { (otherUid, entry) ->
                    val ts = ((entry as? JsonObject)?.get("ts") as? JsonPrimitive)?.longOrNull ?: return@forEach
                    if (otherUid != me && serverNow - ts > ABANDONED_MS) {
                        uiScope.launch { runCatching { db.delete("queue/$tier/$otherUid") } }
                    }
                }
                val candidates = queue.orEmpty().filter { (otherUid, entry) ->
                    val e = entry as? JsonObject ?: return@filter false
                    val ts = (e["ts"] as? JsonPrimitive)?.longOrNull ?: 0L
                    otherUid < me && "offer" !in e && serverNow - ts < STALE_MS
                }.keys.shuffled()
                for (other in candidates) {
                    val path = "queue/$tier/$other/offer"
                    val offer = buildJsonObject {
                        put("from", me)
                        put("fromName", name.take(24))
                        put("hostAccepted", false)
                        put("guestAccepted", false)
                    }
                    if (db.putIfAbsent(path, offer)) {
                        offerPath = path
                        val otherName = ((queue!![other] as JsonObject)["name"] as? JsonPrimitive)?.content ?: "Rival"
                        runOffer(buildJsonObject { put("fromName", otherName) }, iAmHost = true, openRoom)
                        break
                    }
                }
                if (status is Status.Searching) {
                    waited += POLL_SEC
                    status = Status.Searching(waited, nobodyOnline = waited >= NOBODY_AFTER_SEC)
                }
            }
            if (status is Status.Matched) return
            delay(POLL_SEC * 1_000L)
        }
    }

    /** Set by [accept]; sticky for the life of one offer, so a lost write or stale event can't undo it. */
    private var acceptedLocally = false

    /**
     * Drives one offer to MATCHED, or back to SEARCHING on decline/timeout. Both sides watch the
     * same offer node; each writes only its own acceptance flag.
     *
     * Events are only a *nudge*: every check re-reads the offer node itself (a few bytes), so an
     * out-of-order or replayed stream event can never leave one side with a wrong picture. The
     * countdown re-checks once a second too, covering a stream that silently drops an event.
     */
    private suspend fun runOffer(initial: JsonObject, iAmHost: Boolean, openRoom: suspend () -> String?) {
        val path = offerPath ?: return
        val opponentName = (initial["fromName"] as? JsonPrimitive)?.content ?: "Rival"
        val myFlag = if (iAmHost) "hostAccepted" else "guestAccepted"
        acceptedLocally = false
        status = Status.Offer(opponentName, iAmHost, iAccepted = false, theyAccepted = false, secondsLeft = OFFER_SEC)
        val gate = Mutex()
        var ended = false

        /** Reads the offer and advances the state. Returns true once this offer is over. */
        suspend fun evaluate(): Boolean = gate.withLock {
            if (ended) return@withLock true
            val node = try {
                db.get(path)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@withLock false // network blip: try again on the next nudge
            }
            val offer = node as? JsonObject
            if (offer == null) { // the other side declined or it expired
                if (status is Status.Offer) backToSearching()
                ended = true
                return@withLock true
            }
            val hostOk = offer["hostAccepted"]?.jsonPrimitive?.booleanOrNull == true
            val guestOk = offer["guestAccepted"]?.jsonPrimitive?.booleanOrNull == true
            val room = (offer["room"] as? JsonPrimitive)?.content
            val mineInDb = if (iAmHost) hostOk else guestOk
            if (acceptedLocally && !mineInDb) runCatching { db.put("$path/$myFlag", JsonPrimitive(true)) }
            val iOk = mineInDb || acceptedLocally
            val theyOk = if (iAmHost) guestOk else hostOk
            (status as? Status.Offer)?.let { status = it.copy(iAccepted = iOk, theyAccepted = theyOk) }
            if (!(iOk && theyOk)) return@withLock false
            if (iAmHost) {
                status = Status.Matched(opponentName, iAmHost = true, roomCode = null)
                val code = openRoom()
                if (code == null) {
                    status = Status.Failed("Could not open a room.")
                } else {
                    db.put("$path/room", JsonPrimitive(code))
                    status = Status.Matched(opponentName, iAmHost = true, roomCode = code)
                    leaveQueueQuietly()
                }
                ended = true
            } else if (room != null) {
                status = Status.Matched(opponentName, iAmHost = false, roomCode = room)
                leaveQueueQuietly()
                ended = true
            }
            ended
        }

        coroutineScope {
            val listener = launch {
                try {
                    db.listen(path).collect { if (evaluate()) throw OfferEnded() }
                } catch (_: OfferEnded) {
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Stream dropped: the countdown below keeps polling.
                }
            }
            for (left in OFFER_SEC downTo 0) {
                (status as? Status.Offer)?.let { status = it.copy(secondsLeft = left) }
                if (evaluate()) break
                if (left == 0) {
                    // Timed out. Re-check under the gate so a match that just completed isn't torn down.
                    val timedOut = gate.withLock {
                        if (!ended && status is Status.Offer) {
                            ended = true
                            true
                        } else {
                            false
                        }
                    }
                    if (timedOut) dropOffer()
                    break
                }
                delay(1_000)
            }
            listener.cancel()
        }
    }

    fun accept() {
        val path = offerPath ?: return
        val offer = status as? Status.Offer ?: return
        acceptedLocally = true
        status = offer.copy(iAccepted = true)
        uiScope.launch {
            runCatching { db.put("$path/${if (offer.iAmHost) "hostAccepted" else "guestAccepted"}", JsonPrimitive(true)) }
        }
    }

    fun decline() {
        if (status !is Status.Offer) return
        uiScope.launch { dropOffer() }
    }

    private suspend fun dropOffer() {
        offerPath?.let { p -> runCatching { db.delete(p) } }
        backToSearching()
    }

    private fun backToSearching() {
        offerPath = null
        acceptedLocally = false
        status = Status.Searching(0, nobodyOnline = false)
    }

    /** Leaves the queue (and any pending offer). Safe to call any time. */
    fun cancel() {
        job?.cancel()
        job = null
        if (me.isNotEmpty() && status !is Status.Idle) leaveQueueQuietly()
        offerPath = null
        acceptedLocally = false
        status = Status.Idle
    }

    private fun leaveQueueQuietly() {
        val entry = "queue/$tier/$me"
        val pendingOffer = offerPath
        uiScope.launch {
            withContext(NonCancellable) {
                runCatching { db.delete(entry) }
                // An offer I sent lives on the other player's entry — remove it unless the match started.
                if (pendingOffer != null && !pendingOffer.startsWith(entry) && status !is Status.Matched) {
                    runCatching { db.delete(pendingOffer) }
                }
            }
        }
    }

    /** Ends an offer's listener: the match started, or the offer was declined/expired. */
    private class OfferEnded : RuntimeException()

    companion object {
        const val POLL_SEC = 2
        const val NOBODY_AFTER_SEC = 20
        const val OFFER_SEC = 12
        const val STALE_MS = 20_000L

        /** A queue entry silent this long belongs to a closed app; anyone may delete it (mirrors the rules). */
        const val ABANDONED_MS = 5 * 60_000L
    }
}

private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
