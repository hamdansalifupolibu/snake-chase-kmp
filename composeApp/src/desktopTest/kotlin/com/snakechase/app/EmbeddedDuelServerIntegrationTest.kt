package com.snakechase.app

import com.snakechase.app.network.DuelServerListener
import com.snakechase.app.network.EmbeddedDuelServer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real sockets, no mocks: our hand-written RFC 6455 server must interoperate with
 * Ktor's production WebSocket client (the same client the guest uses in the app).
 */
class EmbeddedDuelServerIntegrationTest {
    private val server = EmbeddedDuelServer()

    @AfterTest
    fun tearDown() = server.stop()

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun ktorClient_handshakes_sends_and_receives() = runBlocking {
        val port = freePort()
        val received = CompletableDeferred<Pair<Int, String>>()
        val disconnected = CompletableDeferred<Int>()
        assertTrue(
            server.start(
                port,
                object : DuelServerListener {
                    override fun onClientMessage(clientId: Int, text: String) {
                        received.complete(clientId to text)
                        server.send(clientId, "echo:$text")
                    }
                    override fun onClientDisconnected(clientId: Int) {
                        disconnected.complete(clientId)
                    }
                },
            ),
        )

        val client = HttpClient { install(WebSockets) }
        withTimeout(5_000) {
            client.webSocket("ws://127.0.0.1:$port") {
                // > 125 bytes forces the 16-bit length path on both sides.
                val big = "x".repeat(400)
                send(Frame.Text(big))
                val reply = incoming.receive() as Frame.Text
                assertEquals("echo:$big", reply.readText())
            }
            val (id, text) = received.await()
            assertEquals(400, text.length)
            // Client closed the session → server must report that same client as gone.
            assertEquals(id, disconnected.await())
        }
        client.close()
    }
}
