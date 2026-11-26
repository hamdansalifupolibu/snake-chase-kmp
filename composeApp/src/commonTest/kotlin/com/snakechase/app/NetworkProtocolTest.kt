package com.snakechase.app

import com.snakechase.app.domain.model.DuelMatchResult
import com.snakechase.app.domain.model.DuelNetworkMessage
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.network.DuelSession
import com.snakechase.app.network.WebSocketFrames
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkProtocolTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun roundTrip(msg: DuelNetworkMessage): DuelNetworkMessage = json.decodeFromString(DuelNetworkMessage.serializer(), json.encodeToString(DuelNetworkMessage.serializer(), msg))

    @Test
    fun everyMessageType_survivesJsonRoundTrip() {
        val messages = listOf(
            DuelNetworkMessage.Join("4821", "Android Player"),
            DuelNetworkMessage.RoomInfo("4821", "Host", "Guest", 2),
            DuelNetworkMessage.JoinRejected("Room is full"),
            DuelNetworkMessage.StartCountdown(round = 2, hostIsSnake = false),
            DuelNetworkMessage.ClientInput(1, Vec2(12.5f, 300f), isDashing = true),
            DuelNetworkMessage.ServerStateSync(1, 1234L, Vec2(1f, 2f), 0.5f, listOf(Vec2(3f, 4f)), Vec2(5f, 6f), true, false),
            DuelNetworkMessage.RoundTransition(1, 9000L, 1, 0, 2, false),
            DuelNetworkMessage.MatchFinished(DuelMatchResult("Host", true, 1000L, 2000L, player1Name = "Host", player2Name = "Guest")),
            DuelNetworkMessage.QuickChat("Host", "GG", "👑"),
            DuelNetworkMessage.Leave("Guest"),
            DuelNetworkMessage.Ping(42L),
            DuelNetworkMessage.Pong(42L),
        )
        messages.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun wireFormat_usesStableShortTypeNames() {
        // @SerialName keeps the "type" discriminator independent of class names / R8 renaming.
        val text = json.encodeToString(DuelNetworkMessage.serializer(), DuelNetworkMessage.Ping(7L))
        assertTrue("\"type\":\"Ping\"" in text, text)
    }

    @Test
    fun smallFrame_usesSevenBitLength() {
        val frame = WebSocketFrames.encodeText("hi")
        assertContentEquals(byteArrayOf(0x81.toByte(), 2, 'h'.code.toByte(), 'i'.code.toByte()), frame)
    }

    @Test
    fun mediumFrame_usesSixteenBitLength() {
        val payload = ByteArray(300) { 'a'.code.toByte() }
        val frame = WebSocketFrames.encodeServerFrame(WebSocketFrames.OPCODE_TEXT, payload)
        assertEquals(126, frame[1].toInt() and 0xFF)
        assertEquals(300, ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF))
        assertEquals(4 + 300, frame.size)
    }

    @Test
    fun largeFrame_usesSixtyFourBitLength() {
        val frame = WebSocketFrames.encodeServerFrame(WebSocketFrames.OPCODE_TEXT, ByteArray(70_000))
        assertEquals(127, frame[1].toInt() and 0xFF)
        var len = 0L
        for (i in 2 until 10) len = (len shl 8) or (frame[i].toLong() and 0xFF)
        assertEquals(70_000L, len)
    }

    @Test
    fun unmask_isItsOwnInverse() {
        val original = "Snake".encodeToByteArray()
        val data = original.copyOf()
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        WebSocketFrames.unmask(data, mask)
        WebSocketFrames.unmask(data, mask)
        assertContentEquals(original, data)
    }

    @Test
    fun roomCodes_matchIgnoringFormatting() {
        assertEquals("4821", DuelSession.normalizeCode(" chase-4821 "))
        assertEquals(DuelSession.normalizeCode("4821"), DuelSession.normalizeCode("CHASE-4821"))
    }
}
