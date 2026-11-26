package com.snakechase.app.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * How duel messages travel. [DuelSession] owns the protocol (join, heartbeat, rounds, rematch);
 * a transport only moves strings between host and guest. Implementations:
 *  - [LanHostTransport] / [LanGuestTransport] — embedded WebSocket server on local Wi-Fi (no internet).
 *  - FirebaseHostTransport / FirebaseGuestTransport — Realtime Database rooms over the internet.
 *
 * `lossy = true` marks high-frequency messages (state snapshots, pointer input) where only the
 * newest value matters; transports may drop superseded ones instead of queueing them.
 */
interface HostTransport {
    /** Opens a room and returns its code (and a LAN address when relevant), or an error message. */
    suspend fun open(listener: DuelServerListener): Result<HostRoom>
    fun send(clientId: Int, message: String, lossy: Boolean = false)
    fun disconnect(clientId: Int)
    fun close()
}

data class HostRoom(val code: String, val address: String? = null)

interface GuestListener {
    fun onConnected()
    fun onMessage(text: String)
    fun onClosed(reason: String)
}

interface GuestTransport {
    /** Connects to room [code]; [address] is only used by LAN. Reports through [listener]. */
    fun connect(code: String, address: String?, listener: GuestListener)
    fun send(message: String, lossy: Boolean = false)
    fun disconnect()
}

/** Host over the embedded RFC 6455 server (Android + desktop). */
class LanHostTransport(
    private val port: Int = DuelSession.DEFAULT_PORT,
    private val server: EmbeddedDuelServer = EmbeddedDuelServer(),
) : HostTransport {
    override suspend fun open(listener: DuelServerListener): Result<HostRoom> {
        if (!server.canHost) return Result.failure(IllegalStateException("This device can't host a local room."))
        if (!server.start(port, listener)) {
            return Result.failure(IllegalStateException("Port $port is busy (another copy of the game, or adb forward)."))
        }
        return Result.success(HostRoom(code = (1000..9999).random().toString(), address = "${server.getLocalIpAddress()}:$port"))
    }

    override fun send(clientId: Int, message: String, lossy: Boolean) = server.send(clientId, message)
    override fun disconnect(clientId: Int) = server.disconnect(clientId)
    override fun close() = server.stop()
}

/** Guest over a plain WebSocket to a LAN host (Ktor client — works on every target). */
class LanGuestTransport(
    private val port: Int = DuelSession.DEFAULT_PORT,
    private val http: HttpClient = HttpClient { install(WebSockets) },
) : GuestTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: DefaultClientWebSocketSession? = null
    private var job: Job? = null

    override fun connect(code: String, address: String?, listener: GuestListener) {
        disconnect()
        val host = address.orEmpty().trim()
            .removePrefix("ws://").removePrefix("http://")
            .substringBefore("/").substringBefore(":")
        if (host.isBlank()) {
            listener.onClosed("Enter the host's IP address.")
            return
        }
        job = scope.launch {
            try {
                http.webSocket(urlString = "ws://$host:$port") {
                    session = this
                    listener.onConnected()
                    for (frame in incoming) {
                        if (frame is Frame.Text) listener.onMessage(frame.readText())
                    }
                }
                listener.onClosed("Connection closed")
            } catch (e: Exception) {
                listener.onClosed(e.message ?: "Could not connect to $host")
            } finally {
                session = null
            }
        }
    }

    override fun send(message: String, lossy: Boolean) {
        val s = session ?: return
        scope.launch { runCatching { s.send(Frame.Text(message)) } }
    }

    override fun disconnect() {
        val s = session
        session = null
        job?.cancel()
        job = null
        if (s != null) scope.launch { runCatching { s.close() } }
    }
}
