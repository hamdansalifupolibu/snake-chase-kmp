package com.snakechase.app.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.DuelNetworkMessage
import com.snakechase.app.domain.model.DuelPause
import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.MultiplayerRole
import com.snakechase.app.domain.model.MultiplayerState
import com.snakechase.app.domain.model.QuickChatMessage
import com.snakechase.app.domain.model.SnakeEntity
import com.snakechase.app.domain.model.SnakeRole
import com.snakechase.app.domain.model.SnakeSegment
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.MultiplayerEngine
import com.snakechase.app.engine.MultiplayerTickResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.reflect.KProperty
import kotlin.time.TimeSource

/**
 * Owns everything about a networked duel: the embedded host server, the guest client,
 * protocol rules (room code, single guest), heartbeat/latency and disconnects.
 *
 * Threading: socket callbacks arrive on I/O threads. Every one is re-posted onto
 * [uiScope] (the composition's main-thread scope), so [state] is only ever read and
 * written on one thread — no locks, no torn updates.
 *
 * The screen uses it as a property delegate: `var duelState by session`.
 */
class DuelSession(
    private val uiScope: CoroutineScope,
    val localName: String,
    /** Creates the host side of a room: `online = true` -> Firebase, `false` -> LAN. */
    private val hostTransports: (online: Boolean) -> HostTransport = { LanHostTransport() },
    /** Creates the guest side of a room: `online = true` -> Firebase, `false` -> LAN. */
    private val guestTransports: (online: Boolean) -> GuestTransport = { LanGuestTransport() },
    /** Whether the device can host a LAN room (false on iOS / web). */
    val canHostLan: Boolean = EmbeddedDuelServer().canHost,
    /** Creates a read-only connection for watching an online room; null where watching isn't available. */
    private val spectatorTransports: (() -> GuestTransport)? = null,
) : DuelServerListener {

    private var server: HostTransport? = null
    private var client: GuestTransport? = null

    /** True while the current room runs over the internet (Firebase) rather than LAN. */
    var isOnlineRoom by mutableStateOf(false)
        private set
    private var lastStateSentMs = 0L

    /** Newest input / state seen so far, to drop updates that arrive out of order. */
    private var lastInputSentAtMs = 0L
    private var lastSyncRound = -1
    private var lastSyncSurvivalMs = -1L

    /** Rematch votes after a match: null = not voted yet. A new match starts only when both are true. */
    var rematchLocal by mutableStateOf<Boolean?>(null)
        private set
    var rematchRemote by mutableStateOf<Boolean?>(null)
        private set

    /** Quick match: the host starts as soon as the paired stranger joins (both already accepted). */
    var autoStartWhenGuestJoins = false

    /** Guest-side smoothing of host snapshots (see [guestFrame]). */
    private val interpolator = SnapshotInterpolator()

    var state by mutableStateOf(MultiplayerState())
    var statusText by mutableStateOf<String?>(null)
        private set
    var localHostIp by mutableStateOf("127.0.0.1")
        private set

    /** Latest pointer position from the remote guest (host side only). */
    var rivalInputPos by mutableStateOf<Vec2?>(null)
        private set

    /** Hosting over Firebase works on every platform; LAN hosting needs a JVM device ([canHostLan]). */
    val canHost: Boolean get() = true

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val clock = TimeSource.Monotonic.markNow()
    private fun nowMs(): Long = clock.elapsedNow().inWholeMilliseconds

    private var guestClientId: Int? = null

    /** Set when the host refused us, so the socket-closed error that follows can't hide the reason. */
    private var joinRefused = false
    private var lastHeardMs = 0L
    private var heartbeatJob: Job? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): MultiplayerState = state
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: MultiplayerState) {
        state = value
    }

    // ─── Host ────────────────────────────────────────────────────────────────

    /**
     * Opens a room and shows its invite code. [online] = Firebase (internet, any platform);
     * otherwise the LAN fallback. Returns once the room is open or failed (see [statusText]).
     */
    suspend fun startHosting(online: Boolean = true): Boolean {
        leave(notifyPeer = false)
        isOnlineRoom = online
        state = state.copy(
            mode = DuelMode.LOCAL_INVITE_CHALLENGE,
            phase = DuelPhase.CHALLENGE_INVITE,
            roomCode = "",
            isWaitingForFriend = true,
            isRoomActivated = false,
            isHost = true,
            isNetworkMatch = true,
            player1Name = localName,
            opponentLeftReason = null,
            latencyMs = null,
            lobbyShowsJoin = false,
        )
        statusText = if (online) "Creating an online room..." else "Opening a local room..."
        // Online, try a few times before giving up: a blip in the connection (or a sign-in that
        // just expired) shouldn't cost the player their duel.
        var room: HostRoom? = null
        var failure: Throwable? = null
        val attempts = if (online) HOST_ATTEMPTS else 1
        for (attempt in 0 until attempts) {
            val transport = hostTransports(online)
            server = transport
            room = transport.open(this).getOrElse { e ->
                failure = e
                server = null
                null
            }
            if (room != null) break
            if (attempt < attempts - 1) delay(HOST_RETRY_MS * (attempt + 1))
        }
        if (room == null) {
            statusText = (failure?.message ?: "Could not open a room.") +
                if (online) "" else " Joining a friend? Use the JOIN tab."
            return false
        }
        localHostIp = room.address ?: ""
        state = state.copy(roomCode = room.code)
        statusText = if (online) {
            "Share code ${room.code} - your friend can join from any device."
        } else {
            "Waiting for a challenger on ${room.address}..."
        }
        return true
    }

    /** Player picked the Join tab: close our own room (frees the port) and show the join form. */
    fun showJoinTab() {
        if (state.isHost) leave()
        statusText = null
        state = state.copy(isHost = false, lobbyShowsJoin = true)
    }

    /** Vote on a rematch after a networked match. The host starts it once both said yes. */
    fun voteRematch(accept: Boolean) {
        rematchLocal = accept
        sendToPeer(DuelNetworkMessage.RematchVote(accept))
        if (!accept) {
            leave()
            return
        }
        startRematchIfAgreed()
    }

    private fun onRematchVote(accept: Boolean) {
        rematchRemote = accept
        if (!accept) {
            val who = if (state.isHost) state.player2Name else state.player1Name
            opponentLost("$who declined the rematch")
            return
        }
        startRematchIfAgreed()
    }

    private fun startRematchIfAgreed() {
        if (state.isHost && rematchLocal == true && rematchRemote == true) hostStartMatch()
    }

    fun hostStartMatch() {
        rematchLocal = null
        rematchRemote = null
        if (guestClientId == null) {
            statusText = "No challenger connected yet."
            return
        }
        val fresh = MultiplayerEngine.startMatch(
            mode = DuelMode.LOCAL_INVITE_CHALLENGE,
            player1Name = localName,
            player2Name = state.player2Name,
            roomCode = state.roomCode,
        ).copy(isHost = true, isNetworkMatch = true, selectedTierIndex = state.selectedTierIndex)
        state = fresh
        broadcastRoundStart(round = 1, hostIsSnake = fresh.localRole == MultiplayerRole.SNAKE)
    }

    /** My worn skins (set by the duel screen from the profile); shared with the rival. */
    var localLook: DuelNetworkMessage.Look = DuelNetworkMessage.Look()

    /** The guest's skins, as they told the host. */
    private var guestLook: DuelNetworkMessage.Look = DuelNetworkMessage.Look()

    fun broadcastRoundStart(round: Int, hostIsSnake: Boolean) {
        rivalInputPos = null // each round starts from the spawn points, not last round's pointer
        lastInputSentAtMs = 0L
        state = state.copy(p1Look = localLook, p2Look = guestLook)
        sendToPeer(DuelNetworkMessage.StartCountdown(round = round, hostIsSnake = hostIsSnake, hostLook = localLook, guestLook = guestLook))
    }

    /** Called by the host's simulation loop after every [MultiplayerEngine.tick]. */
    fun onHostTick(result: MultiplayerTickResult) {
        val s = state
        val snake = s.snake ?: return
        val roundEnded = result.roundCompleted || result.matchCompleted
        // ~20 snapshots/s is plenty: the guest interpolates between them. Round ends always go out.
        val now = nowMs()
        if (!roundEnded && now - lastStateSentMs < STATE_SYNC_INTERVAL_MS) return
        lastStateSentMs = now
        sendToPeer(
            lossy = !roundEnded,
            msg =
            DuelNetworkMessage.ServerStateSync(
                round = s.round,
                survivalTimeMs = s.survivalTimeMs,
                snakeHead = snake.headPosition,
                snakeAngle = snake.headAngle,
                snakeSegments = snake.segments.map { it.position },
                preyPos = s.prey.position,
                contactOccurred = result.contactOccurred,
                roundOver = roundEnded,
            ),
        )
        if (result.roundCompleted) {
            sendToPeer(
                DuelNetworkMessage.RoundTransition(
                    finishedRound = s.round,
                    roundDurationMs = s.survivalTimeMs,
                    p1RoundsWon = s.player1RoundsWon,
                    p2RoundsWon = s.player2RoundsWon,
                    nextRound = s.round + 1,
                    nextHostIsSnake = s.localRole != MultiplayerRole.SNAKE,
                ),
            )
        }
        if (result.matchCompleted) s.result?.let { sendToPeer(DuelNetworkMessage.MatchFinished(it)) }
    }

    override fun onClientConnected(clientId: Int) = onUi { lastHeardMs = nowMs() }

    override fun onClientMessage(clientId: Int, text: String) {
        val msg = decode(text) ?: return
        onUi { handleHostMessage(clientId, msg) }
    }

    override fun onClientDisconnected(clientId: Int) = onUi {
        if (clientId == guestClientId) {
            guestClientId = null
            opponentLost("${state.player2Name} disconnected")
        }
    }

    private fun handleHostMessage(clientId: Int, msg: DuelNetworkMessage) {
        lastHeardMs = nowMs()
        when (msg) {
            is DuelNetworkMessage.Join -> {
                val current = guestClientId
                val reason = when {
                    current != null && current != clientId -> "Room is full — a duel is already in progress."
                    normalizeCode(msg.roomCode) != normalizeCode(state.roomCode) -> "Wrong room code."
                    else -> null
                }
                if (reason != null) {
                    server?.send(clientId, encode(DuelNetworkMessage.JoinRejected(reason)))
                    server?.disconnect(clientId)
                    return
                }
                guestClientId = clientId
                state = state.copy(player2Name = msg.guestName, isRoomActivated = true, opponentLeftReason = null)
                statusText = "${msg.guestName} joined 🟢"
                server?.send(
                    clientId,
                    encode(DuelNetworkMessage.RoomInfo(state.roomCode, localName, msg.guestName, state.selectedTierIndex)),
                )
                startHeartbeat()
                if (autoStartWhenGuestJoins) {
                    autoStartWhenGuestJoins = false
                    hostStartMatch()
                }
            }
            is DuelNetworkMessage.RematchVote -> if (clientId == guestClientId) onRematchVote(msg.accept)
            // Ignore input from sockets that never passed the Join check.
            // Late input from the previous round must not steer the new one.
            is DuelNetworkMessage.ClientInput -> if (clientId == guestClientId && msg.round == state.round && msg.sentAtMs >= lastInputSentAtMs) {
                lastInputSentAtMs = msg.sentAtMs
                rivalInputPos = msg.inputPos
            }
            is DuelNetworkMessage.QuickChat -> if (clientId == guestClientId) addChat(msg.sender, msg.message, msg.emoji)
            is DuelNetworkMessage.Ping -> server?.send(clientId, encode(DuelNetworkMessage.Pong(msg.timestamp)))
            is DuelNetworkMessage.Pong -> updateLatency(msg.timestamp)
            is DuelNetworkMessage.Look -> if (clientId == guestClientId) guestLook = msg
            is DuelNetworkMessage.Pause -> if (clientId == guestClientId) rivalPaused(msg.playerName)
            is DuelNetworkMessage.Resume -> if (clientId == guestClientId) rivalResumed()
            is DuelNetworkMessage.Leave -> if (clientId == guestClientId) {
                guestClientId = null
                server?.disconnect(clientId)
                opponentLost("${msg.playerName} left the duel")
            }
            else -> Unit
        }
    }

    // ─── Guest ───────────────────────────────────────────────────────────────

    /**
     * Joins room [roomCode]. [online] = Firebase (just the code); otherwise LAN, where
     * [hostAddress] is the host's IP shown on their screen.
     */
    fun joinRoom(roomCode: String, online: Boolean = true, hostAddress: String? = null) {
        leave(notifyPeer = false)
        isOnlineRoom = online
        val code = normalizeCode(roomCode)
        if (code.isEmpty()) {
            statusText = "Enter your friend's room code."
            return
        }
        statusText = if (online) "Joining room $code..." else "Connecting to ${hostAddress.orEmpty()}..."
        joinRefused = false
        state = state.copy(isHost = false, isNetworkMatch = true, opponentLeftReason = null, latencyMs = null, lobbyShowsJoin = true)
        val transport = guestTransports(online)
        client = transport
        transport.connect(
            code,
            hostAddress,
            object : GuestListener {
                override fun onConnected() = onUi {
                    statusText = "Connected - joining..."
                    lastHeardMs = nowMs()
                    transport.send(encode(DuelNetworkMessage.Join(roomCode = code, guestName = localName)))
                }

                override fun onMessage(text: String) {
                    val msg = decode(text) ?: return
                    onUi { handleGuestMessage(msg) }
                }

                override fun onClosed(reason: String) = onUi {
                    if (joinRefused || client !== transport) return@onUi
                    if (state.isRoomActivated) {
                        opponentLost("Lost connection to host")
                    } else {
                        statusText = reason
                    }
                }
            },
        )
    }

    /** Whether this build can watch online duels. */
    val canSpectate: Boolean get() = spectatorTransports != null

    /**
     * Watches online room [roomCode] without taking part: the same view as the guest, fed by the
     * host's broadcast, but with no input, chat, rematch vote, stake or stats.
     */
    fun spectateRoom(roomCode: String) {
        val transports = spectatorTransports ?: return
        leave(notifyPeer = false)
        isOnlineRoom = true
        val code = normalizeCode(roomCode)
        if (code.isEmpty()) {
            statusText = "Enter the code of the duel you want to watch."
            return
        }
        statusText = "Opening room $code to watch..."
        state = state.copy(
            isHost = false,
            isNetworkMatch = true,
            isSpectator = true,
            opponentLeftReason = null,
            latencyMs = null,
            lobbyShowsJoin = true,
        )
        val transport = transports()
        client = transport
        transport.connect(
            code,
            null,
            object : GuestListener {
                override fun onConnected() = onUi {
                    lastHeardMs = nowMs()
                    statusText = "Watching room $code — waiting for the duel to start…"
                }

                override fun onMessage(text: String) {
                    val msg = decode(text) ?: return
                    onUi { handleGuestMessage(msg) }
                }

                override fun onClosed(reason: String) = onUi {
                    if (client !== transport) return@onUi
                    if (state.isRoomActivated) opponentLost("The duel ended") else statusText = reason
                }
            },
        )
    }

    /** Called by the guest every frame during play: draws the smoothed host world. */
    fun guestFrame() {
        if (state.isHost || !state.isNetworkMatch) return
        applySnapshot(interpolator.sample(nowMs()))
    }

    private fun applySnapshot(snap: DuelSnapshot?) {
        snap ?: return
        val segments = snap.snakeSegments.map { SnakeSegment(it, 0f) }
        val snake = state.snake?.copy(headPosition = snap.snakeHead, headAngle = snap.snakeAngle, segments = segments)
            ?: SnakeEntity(
                role = SnakeRole.PRIMARY,
                headPosition = snap.snakeHead,
                segments = segments,
                headAngle = snap.snakeAngle,
                speed = 260f,
                targetSpeed = 260f,
                headRadius = 16f,
                bodyRadius = 13f,
                history = listOf(snap.snakeHead),
            )
        state = state.copy(snake = snake, prey = state.prey.copy(position = snap.prey))
    }

    fun sendGuestInput(pos: Vec2) {
        if (state.isHost || state.isSpectator) return
        val now = nowMs()
        if (now - lastStateSentMs < STATE_SYNC_INTERVAL_MS) return
        lastStateSentMs = now
        client?.send(encode(DuelNetworkMessage.ClientInput(round = state.round, inputPos = pos, sentAtMs = nowMs())), lossy = true)
    }

    private fun handleGuestMessage(msg: DuelNetworkMessage) {
        lastHeardMs = nowMs()
        when (msg) {
            is DuelNetworkMessage.RoomInfo -> {
                // Tell the host what I'm wearing (spectators wear nothing in the match).
                if (!state.isSpectator) sendToPeer(localLook)
                state = state.copy(
                    roomCode = msg.roomCode,
                    player1Name = msg.hostName,
                    player2Name = msg.guestName,
                    isRoomActivated = true,
                    selectedTierIndex = msg.selectedTierIndex,
                )
                statusText = if (state.isSpectator) {
                    "Watching ${msg.hostName} vs ${msg.guestName} — waiting for the duel to start…"
                } else {
                    "Joined ${msg.hostName}'s room — waiting for them to start…"
                }
                startHeartbeat()
            }
            is DuelNetworkMessage.JoinRejected -> {
                joinRefused = true
                statusText = "Join refused: ${msg.reason}"
                client?.disconnect()
            }
            is DuelNetworkMessage.StartCountdown -> {
                interpolator.clear()
                lastSyncSurvivalMs = -1L // a new round (or a rematch's round 1) starts the clock again
                rematchLocal = null
                rematchRemote = null
                val guestRole = if (msg.hostIsSnake) MultiplayerRole.PREY else MultiplayerRole.SNAKE
                state = state.copy(
                    mode = DuelMode.LOCAL_INVITE_CHALLENGE,
                    phase = DuelPhase.COUNTDOWN,
                    round = msg.round,
                    p1Look = msg.hostLook,
                    p2Look = msg.guestLook,
                    localRole = guestRole,
                    isHost = false,
                    isNetworkMatch = true,
                    countdownCount = 3,
                    result = null,
                    roundAnnouncement = if (state.isSpectator) {
                        "ROUND ${msg.round} — ${state.player1Name} vs ${state.player2Name}"
                    } else if (guestRole == MultiplayerRole.SNAKE) {
                        "ROUND ${msg.round} — HUNT THE PREY!"
                    } else {
                        "ROUND ${msg.round} — FLEE & SURVIVE!"
                    },
                )
            }
            is DuelNetworkMessage.ServerStateSync -> {
                // Several state writes travel at once, so one can land after a newer one: skip it.
                if (msg.round == lastSyncRound && msg.survivalTimeMs < lastSyncSurvivalMs) return
                lastSyncRound = msg.round
                lastSyncSurvivalMs = msg.survivalTimeMs
                interpolator.add(
                    DuelSnapshot(nowMs(), msg.snakeHead, msg.snakeAngle, msg.snakeSegments, msg.preyPos),
                )
                state = state.copy(survivalTimeMs = msg.survivalTimeMs)
                // Outside live play (countdown, round end) show the exact final positions.
                if (msg.roundOver || state.phase != DuelPhase.ROUND_PLAYING) applySnapshot(interpolator.sample(Long.MAX_VALUE))
            }
            // Keep `round` = the round that just ended (the overlay says "ROUND n CONCLUDED");
            // the host's StartCountdown for the next round sets the new number.
            is DuelNetworkMessage.RoundTransition -> state = state.copy(
                phase = DuelPhase.ROUND_TRANSITION,
                round = msg.finishedRound,
                player1RoundsWon = msg.p1RoundsWon,
                player2RoundsWon = msg.p2RoundsWon,
                round1TimeMs = if (msg.finishedRound == 1) msg.roundDurationMs else state.round1TimeMs,
                round2TimeMs = if (msg.finishedRound == 2) msg.roundDurationMs else state.round2TimeMs,
                round3TimeMs = if (msg.finishedRound == 3) msg.roundDurationMs else state.round3TimeMs,
            )
            is DuelNetworkMessage.MatchFinished -> state = state.copy(
                phase = DuelPhase.MATCH_OVER,
                // The host computed "local winner" from its own side; flip it for the guest.
                // A spectator neither won nor lost: the result screen just names the winner.
                result = msg.result.copy(isLocalWinner = !msg.result.isLocalWinner && !state.isSpectator),
            )
            is DuelNetworkMessage.RematchVote -> onRematchVote(msg.accept)
            is DuelNetworkMessage.QuickChat -> addChat(msg.sender, msg.message, msg.emoji)
            is DuelNetworkMessage.Ping -> client?.send(encode(DuelNetworkMessage.Pong(msg.timestamp)))
            is DuelNetworkMessage.Pong -> updateLatency(msg.timestamp)
            is DuelNetworkMessage.Pause -> rivalPaused(msg.playerName)
            is DuelNetworkMessage.Resume -> rivalResumed()
            is DuelNetworkMessage.Leave -> {
                client?.disconnect()
                opponentLost("${msg.playerName} closed the room")
            }
            else -> Unit
        }
    }

    // ─── Shared ──────────────────────────────────────────────────────────────

    fun sendChat(message: String, emoji: String?) {
        addChat("You", message, emoji)
        if (state.isNetworkMatch) sendToPeer(DuelNetworkMessage.QuickChat(localName, message, emoji))
    }

    /** Pause the match. Online, the rival is told and the [DuelPause.WINDOW_MS] clock starts. */
    fun pause() {
        if (state.pausedBy != null) return
        val deadline = if (state.isNetworkMatch) nowMs() + DuelPause.WINDOW_MS else 0L
        state = state.copy(pausedBy = YOU, pauseDeadlineMs = deadline)
        if (state.isNetworkMatch) sendToPeer(DuelNetworkMessage.Pause(localName))
    }

    /** Milliseconds left on the current pause (null = not paused or no limit). */
    fun pauseMsLeft(): Long? = if (state.pausedBy == null || state.pauseDeadlineMs == 0L) null else (state.pauseDeadlineMs - nowMs()).coerceAtLeast(0L)

    fun resume() {
        if (state.pausedBy != YOU) return
        state = state.copy(pausedBy = null, pauseDeadlineMs = 0L)
        if (state.isNetworkMatch) sendToPeer(DuelNetworkMessage.Resume(localName))
    }

    /** The rival's pause ran out without them coming back: the match is mine. */
    fun rivalPauseExpired() {
        val who = state.pausedBy ?: return
        if (who == YOU || state.isSpectator) return
        state = state.copy(pausedBy = null, pauseDeadlineMs = 0L)
        opponentLost("$who didn't come back within ${DuelPause.WINDOW_MS / 1000} s")
    }

    private fun rivalPaused(name: String) {
        // Spectators just see the pause; the 10-second rule is between the two players.
        val deadline = if (state.isSpectator) 0L else nowMs() + DuelPause.WINDOW_MS + DuelPause.GRACE_MS
        state = state.copy(pausedBy = name.ifBlank { "Your rival" }, pauseDeadlineMs = deadline)
    }

    private fun rivalResumed() {
        if (state.pausedBy != null && state.pausedBy != YOU) state = state.copy(pausedBy = null, pauseDeadlineMs = 0L)
    }

    /** Leave the room/match. Tells the peer first so they see "left" rather than a timeout. */
    fun leave(notifyPeer: Boolean = true) {
        if (notifyPeer && state.isNetworkMatch) sendToPeer(DuelNetworkMessage.Leave(localName))
        heartbeatJob?.cancel()
        heartbeatJob = null
        guestClientId = null
        rivalInputPos = null
        server?.close()
        server = null
        client?.disconnect()
        client = null
        statusText = null
        state = state.copy(isNetworkMatch = false, isRoomActivated = false, latencyMs = null, isSpectator = false)
    }

    fun dispose() = leave()

    private fun sendToPeer(msg: DuelNetworkMessage, lossy: Boolean = false) {
        val text = encode(msg)
        if (state.isHost) guestClientId?.let { server?.send(it, text, lossy) } else client?.send(text, lossy)
    }

    /** When the unanswered ping went out (null = none in flight). See [startHeartbeat]. */
    private var pingInFlightSince: Long? = null

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        lastHeardMs = nowMs()
        pingInFlightSince = null
        heartbeatJob = uiScope.launch {
            while (isActive) {
                // One ping at a time: on a slow link, firing one per second regardless queues them
                // up in the ordered channel, and everything behind them (chat, rematch, leave) and
                // the latency read-out fall further and further behind.
                val inFlight = pingInFlightSince
                if (inFlight == null || nowMs() - inFlight > PING_RETRY_MS) {
                    pingInFlightSince = nowMs()
                    sendToPeer(DuelNetworkMessage.Ping(nowMs()))
                }
                delay(HEARTBEAT_INTERVAL_MS)
                if (nowMs() - lastHeardMs > HEARTBEAT_TIMEOUT_MS) {
                    opponentLost("Connection timed out")
                    break
                }
            }
        }
    }

    private fun updateLatency(sentAt: Long) {
        pingInFlightSince = null
        state = state.copy(latencyMs = (nowMs() - sentAt).toInt().coerceAtLeast(0))
    }

    private fun opponentLost(reason: String) {
        if (state.opponentLeftReason != null) return
        heartbeatJob?.cancel()
        statusText = reason
        state = state.copy(opponentLeftReason = reason, isRoomActivated = false, latencyMs = null)
    }

    private fun addChat(sender: String, text: String, emoji: String?) {
        val chat = QuickChatMessage(sender, text, emoji, nowMs())
        state = state.copy(activeChatBubbles = (state.activeChatBubbles + chat).takeLast(3))
    }

    private fun onUi(block: () -> Unit) {
        uiScope.launch { block() }
    }

    private fun encode(msg: DuelNetworkMessage): String = json.encodeToString(DuelNetworkMessage.serializer(), msg)
    private fun decode(text: String): DuelNetworkMessage? = runCatching { json.decodeFromString(DuelNetworkMessage.serializer(), text) }.getOrNull()

    companion object {
        const val DEFAULT_PORT = 9090
        const val HEARTBEAT_INTERVAL_MS = 1_000L

        /** Opening an online room: tries, and the pause before each retry grows by this much. */
        const val HOST_ATTEMPTS = 3
        const val HOST_RETRY_MS = 600L

        /** [MultiplayerState.pausedBy] value when this device paused. */
        const val YOU = "You"
        const val HEARTBEAT_TIMEOUT_MS = 6_000L

        /** A ping unanswered this long is assumed lost and another is sent. */
        const val PING_RETRY_MS = 5_000L
        const val STATE_SYNC_INTERVAL_MS = 50L

        /** Case/spacing-insensitive: "snk-4k7q", " SNK4K7Q " and "SNK4K7Q" all match; a legacy "CHASE-" prefix is ignored. */
        fun normalizeCode(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }.removePrefix("CHASE")
    }
}
