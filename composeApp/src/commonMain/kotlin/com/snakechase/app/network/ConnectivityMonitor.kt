package com.snakechase.app.network

import com.snakechase.app.network.firebase.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers "can this device play online right now?" by actually reaching Firebase instead of
 * trusting the OS flag (Wi-Fi with a captive portal or no uplink still says "connected").
 *
 * Any HTTP answer, even 401/404, proves the internet path works; only a timeout or a network
 * error counts as offline. It checks often while offline (to notice the return quickly) and
 * rarely while online, and other code can ask for an immediate re-check via [recheck].
 */
class ConnectivityMonitor(
    private val probe: suspend () -> Boolean,
    private val onlineIntervalMs: Long = 30_000,
    private val offlineIntervalMs: Long = 4_000,
) {
    private val _online = MutableStateFlow<Boolean?>(null)

    /** null until the first check finishes, then true/false. */
    val online: StateFlow<Boolean?> = _online.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                _online.value = runCatching { probe() }.getOrDefault(false)
                val wait = if (_online.value == true) onlineIntervalMs else offlineIntervalMs
                withTimeoutOrNull(wait) { wake.receive() }
            }
        }
    }

    /** Ask for a fresh check now, e.g. after a request failed or the player tapped Retry. */
    fun recheck() {
        wake.trySend(Unit)
    }

    /** Probes right now and returns the answer (used to tell "they left" from "I'm offline"). */
    suspend fun checkNow(): Boolean {
        val ok = runCatching { probe() }.getOrDefault(false)
        _online.value = ok
        return ok
    }

    /** A real request just failed with a network error: flip to offline at once and re-check. */
    fun reportFailure() {
        _online.value = false
        recheck()
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        /**
         * Cheap reachability probe: `/ping` is the one publicly readable path (see database.rules.json),
         * so it answers 200 `null` in a few bytes instead of a 401 that browsers log as an error.
         * Any HTTP answer still counts as online.
         */
        fun firebaseProbe(http: HttpClient): suspend () -> Boolean = {
            http.get("${FirebaseConfig.RTDB_URL}/ping.json") {
                timeout {
                    requestTimeoutMillis = 5_000
                    connectTimeoutMillis = 5_000
                }
            }
            true
        }
    }
}

/** Waits until [ConnectivityMonitor.online] is true (used by the upload queue). */
suspend fun ConnectivityMonitor.awaitOnline() {
    online.first { it == true }
}
