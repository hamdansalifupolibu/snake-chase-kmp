package com.snakechase.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.snakechase.app.network.DuelSession
import com.snakechase.app.network.LanHostTransport
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.ui.screens.MultiplayerScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlin.test.assertTrue

/**
 * Drives the real lobby UI exactly as a player would: Friend Invite → JOIN tab →
 * type host IP + room code → Connect, against a real hosting DuelSession.
 */
@OptIn(ExperimentalTestApi::class)
class DuelLobbyUiTest {

    @Test(timeout = 90_000)
    fun joinTab_staysSelected_andGuestJoinsRealHost() = runComposeUiTest {
        // A real host on a free port, on its own "UI" thread.
        val hostUi = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val hostScope = CoroutineScope(SupervisorJob() + hostUi)
        val port = ServerSocket(0).use { it.localPort }
        val host = DuelSession(hostScope, localName = "Host Tester", hostTransports = { LanHostTransport(port) })
        runBlocking { withContext(hostUi) { assertTrue(host.startHosting(online = false)) } }
        val code = runBlocking { withContext(hostUi) { host.state.roomCode } }

        try {
            setContent {
                MultiplayerScreen(
                    audioManager = AudioManager(),
                    hapticsManager = HapticsManager(),
                    onReturnToMenu = {},
                    playerName = "UI Guest",
                    joinPortOverride = port,
                )
            }

            // Mode select: open the Friend invite card.
            onNodeWithText("Friend invite").performClick()
            waitUntil(timeoutMillis = 3_000) { onAllNodesWithText("Join with a code").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Join with a code").performClick()
            waitForIdle()
            // The regression: the lobby used to bounce back to HOST and open a new room.
            onNodeWithText("Join duel").assertExistsSafely()
            mainClock.advanceTimeBy(1_000)
            onNodeWithText("Join duel").assertExistsSafely()

            val fields = onAllNodes(hasSetTextAction())
            fields[0].performTextInput("127.0.0.1")
            fields[1].performTextInput(code)
            // From here the guest runs a 1 s heartbeat loop on the composition scope. With the
            // test's virtual clock auto-advancing, delay(1000) would complete instantly and spin
            // the UI thread, so freeze virtual time and poll in real time instead.
            mainClock.autoAdvance = false
            onNodeWithText("Join duel").performClick()

            // Guest UI shows the host's name; host session sees the guest.
            val deadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < deadline &&
                onAllNodes(hasText("Host Tester", substring = true)).fetchSemanticsNodes().isEmpty()
            ) {
                Thread.sleep(50)
            }
            onNode(hasText("Host Tester", substring = true)).assertExists()
            val hostSawGuest = runBlocking { withContext(hostUi) { host.state.isRoomActivated && host.state.player2Name == "UI Guest" } }
            assertTrue(hostSawGuest, "host should see 'UI Guest' joined")
        } finally {
            runBlocking { withContext(hostUi) { host.dispose() } }
            hostScope.cancel()
            hostUi.close()
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsSafely() {
        assertExists()
    }
}
