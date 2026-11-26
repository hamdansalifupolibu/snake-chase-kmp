package com.snakechase.app.network

/**
 * Pure RFC 6455 frame helpers, shared by every platform and unit-tested in commonTest.
 * The socket I/O lives in the platform server; everything byte-level lives here.
 */
object WebSocketFrames {
    const val OPCODE_CONTINUATION = 0x0
    const val OPCODE_TEXT = 0x1
    const val OPCODE_BINARY = 0x2
    const val OPCODE_CLOSE = 0x8
    const val OPCODE_PING = 0x9
    const val OPCODE_PONG = 0xA

    /** Magic GUID appended to Sec-WebSocket-Key before SHA-1 hashing (RFC 6455 §1.3). */
    const val HANDSHAKE_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    /** Largest client frame we accept; duel packets are a few hundred bytes. */
    const val MAX_PAYLOAD_BYTES = 64 * 1024

    /**
     * Builds an unmasked, final (FIN=1) server→client frame.
     * Servers must never mask (RFC 6455 §5.1); length uses the 7 / 16 / 64-bit encodings.
     */
    fun encodeServerFrame(opcode: Int, payload: ByteArray): ByteArray {
        val len = payload.size
        val header: ByteArray = when {
            len < 126 -> byteArrayOf((0x80 or opcode).toByte(), len.toByte())
            len <= 0xFFFF -> byteArrayOf(
                (0x80 or opcode).toByte(),
                126.toByte(),
                (len shr 8).toByte(),
                len.toByte(),
            )
            else -> ByteArray(10).also { h ->
                h[0] = (0x80 or opcode).toByte()
                h[1] = 127.toByte()
                for (i in 0 until 8) h[2 + i] = (len.toLong() shr (56 - i * 8)).toByte()
            }
        }
        return header + payload
    }

    fun encodeText(text: String): ByteArray = encodeServerFrame(OPCODE_TEXT, text.encodeToByteArray())

    /** XOR-unmasks a client payload in place with its 4-byte masking key. */
    fun unmask(payload: ByteArray, mask: ByteArray) {
        for (i in payload.indices) {
            payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        }
    }
}
