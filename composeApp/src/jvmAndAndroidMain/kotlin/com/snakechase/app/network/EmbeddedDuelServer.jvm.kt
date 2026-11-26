package com.snakechase.app.network

import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * One implementation for Android and desktop: both run on a JVM with java.net.
 *
 * Threads per client: one reader, one writer. Callers (usually the UI thread) only
 * enqueue frames — they never touch the socket. That matters on Android, where any
 * socket write on the main thread throws NetworkOnMainThreadException, and it means a
 * slow peer can never stall the 60 fps game loop. A single writer also keeps frame order.
 */
actual class EmbeddedDuelServer actual constructor() {
    actual val canHost: Boolean = true

    private inner class Client(val id: Int, val socket: Socket) {
        private val out: OutputStream = socket.getOutputStream()
        private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
            Thread(r, "DuelServer-writer-$id").apply { isDaemon = true }
        }

        /** Non-blocking: queues the frame for the writer thread. */
        fun enqueue(frame: ByteArray) {
            if (writer.isShutdown) return
            try {
                writer.execute {
                    try {
                        out.write(frame)
                        out.flush()
                    } catch (_: Throwable) {
                        disconnect(id)
                    }
                }
            } catch (_: RejectedExecutionException) {
                // Client is shutting down; drop the frame.
            }
        }

        /**
         * Closes after already-queued frames are written, so a final message such as
         * JoinRejected actually reaches the peer. The close runs on the writer thread,
         * behind everything enqueued before it.
         */
        fun close() {
            try {
                writer.execute { runCatching { socket.close() } }
            } catch (_: RejectedExecutionException) {
                runCatching { socket.close() }
            }
            writer.shutdown()
        }
    }

    @Volatile private var serverSocket: ServerSocket? = null

    @Volatile private var listener: DuelServerListener? = null
    private val clients = ConcurrentHashMap<Int, Client>()
    private val nextId = AtomicInteger(1)

    actual fun start(port: Int, listener: DuelServerListener): Boolean {
        this.listener = listener
        if (serverSocket?.isClosed == false) return true
        return try {
            val server = ServerSocket(port).apply { reuseAddress = true }
            serverSocket = server
            thread(isDaemon = true, name = "DuelServer-accept") {
                while (!server.isClosed) {
                    val socket = try {
                        server.accept()
                    } catch (_: Throwable) {
                        break
                    }
                    socket.tcpNoDelay = true
                    thread(isDaemon = true, name = "DuelServer-client") { serveClient(socket) }
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun serveClient(socket: Socket) {
        var client: Client? = null
        try {
            val input = socket.getInputStream()
            val key = readHandshakeKey(input) ?: return
            socket.getOutputStream().apply {
                write(
                    (
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n"
                        ).toByteArray(Charsets.ISO_8859_1),
                )
                flush()
            }
            val c = Client(nextId.getAndIncrement(), socket)
            client = c
            clients[c.id] = c
            listener?.onClientConnected(c.id)
            readFrames(c, input)
        } catch (_: Throwable) {
            // Peer vanished or sent garbage; fall through to cleanup.
        } finally {
            client?.close() ?: runCatching { socket.close() }
            client?.let {
                if (clients.remove(it.id) != null) listener?.onClientDisconnected(it.id)
            }
        }
    }

    private fun readFrames(client: Client, input: InputStream) {
        val textBuffer = java.io.ByteArrayOutputStream()
        while (true) {
            val b0 = input.read()
            if (b0 == -1) return
            val b1 = input.read()
            if (b1 == -1) return
            val fin = (b0 and 0x80) != 0
            val opcode = b0 and 0x0F
            val masked = (b1 and 0x80) != 0
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) {
                len = ((readByte(input) shl 8) or readByte(input)).toLong()
            } else if (len == 127L) {
                len = 0L
                repeat(8) { len = (len shl 8) or readByte(input).toLong() }
            }
            if (len > WebSocketFrames.MAX_PAYLOAD_BYTES) return // Refuse oversized frames.

            val mask = if (masked) readExactly(input, 4) else null
            val payload = readExactly(input, len.toInt())
            if (mask != null) WebSocketFrames.unmask(payload, mask)

            when (opcode) {
                WebSocketFrames.OPCODE_CLOSE -> {
                    client.enqueue(WebSocketFrames.encodeServerFrame(WebSocketFrames.OPCODE_CLOSE, ByteArray(0)))
                    return
                }
                WebSocketFrames.OPCODE_PING ->
                    client.enqueue(WebSocketFrames.encodeServerFrame(WebSocketFrames.OPCODE_PONG, payload))
                WebSocketFrames.OPCODE_PONG -> Unit
                WebSocketFrames.OPCODE_TEXT, WebSocketFrames.OPCODE_CONTINUATION -> {
                    textBuffer.write(payload)
                    if (textBuffer.size() > WebSocketFrames.MAX_PAYLOAD_BYTES) return
                    if (fin) {
                        val text = textBuffer.toByteArray().decodeToString()
                        textBuffer.reset()
                        listener?.onClientMessage(client.id, text)
                    }
                }
                else -> Unit // Binary frames are not part of the duel protocol.
            }
        }
    }

    private fun readByte(input: InputStream): Int {
        val b = input.read()
        if (b == -1) throw java.io.EOFException()
        return b
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(buf, read, n - read)
            if (r == -1) throw java.io.EOFException()
            read += r
        }
        return buf
    }

    private fun readHandshakeKey(input: InputStream): String? {
        val line = StringBuilder()
        var key: String? = null
        var headerBytes = 0
        while (true) {
            val c = input.read()
            if (c == -1 || ++headerBytes > 8192) return null
            when (c) {
                '\n'.code -> {
                    val l = line.toString().trim()
                    line.setLength(0)
                    if (l.isEmpty()) return key
                    if (l.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) key = l.substringAfter(":").trim()
                }
                '\r'.code -> Unit
                else -> line.append(c.toChar())
            }
        }
    }

    private fun acceptKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest((key + WebSocketFrames.HANDSHAKE_GUID).toByteArray(Charsets.ISO_8859_1))
        return Base64.getEncoder().encodeToString(digest)
    }

    actual fun send(clientId: Int, message: String) {
        val client = clients[clientId] ?: return
        try {
            client.enqueue(WebSocketFrames.encodeText(message))
        } catch (_: Throwable) {
            disconnect(clientId)
        }
    }

    actual fun broadcast(message: String) {
        val frame = WebSocketFrames.encodeText(message)
        for (client in clients.values) {
            client.enqueue(frame)
        }
    }

    actual fun disconnect(clientId: Int) {
        val client = clients.remove(clientId) ?: return
        client.close()
        listener?.onClientDisconnected(clientId)
    }

    actual fun stop() {
        clients.keys.toList().forEach { id -> clients.remove(id)?.close() }
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /**
     * The address a peer on the same Wi-Fi should type. Laptops often expose several IPv4
     * adapters (VMware/VirtualBox/Hyper-V/WSL/Docker bridges, Bluetooth PAN); those are
     * unreachable from a phone, so they are skipped and real Wi-Fi is ranked first.
     */
    actual fun getLocalIpAddress(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !isVirtualAdapter(it) }
            .sortedBy(::adapterRank)
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress ?: "127.0.0.1"
    } catch (_: Throwable) {
        "127.0.0.1"
    }

    private fun describe(iface: NetworkInterface) = "${iface.name} ${iface.displayName}".lowercase()

    private fun isVirtualAdapter(iface: NetworkInterface): Boolean = VIRTUAL_ADAPTER_HINTS.any { it in describe(iface) }

    private fun adapterRank(iface: NetworkInterface): Int {
        val d = describe(iface)
        return when {
            WIFI_HINTS.any { it in d } -> 0 // wlan0 on Android, "Wi-Fi"/"Wireless" on desktop
            ETHERNET_HINTS.any { it in d } -> 1
            else -> 2 // e.g. rmnet (mobile data), unknown
        }
    }

    private companion object {
        val VIRTUAL_ADAPTER_HINTS = listOf(
            "vmware", "vmnet", "virtualbox", "vboxnet", "hyper-v", "vethernet", "docker",
            "wsl", "bluetooth", "tap-", "tun", "utun", "virtual", "loopback", "zerotier", "tailscale",
        )
        val WIFI_HINTS = listOf("wlan", "wi-fi", "wifi", "wireless", "802.11")
        val ETHERNET_HINTS = listOf("eth", "ethernet", "en0", "en1")
    }
}
