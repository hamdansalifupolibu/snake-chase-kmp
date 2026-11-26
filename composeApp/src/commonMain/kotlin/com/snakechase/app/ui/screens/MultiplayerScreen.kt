package com.snakechase.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.ARENA_TIERS
import com.snakechase.app.domain.model.DuelMatchResult
import com.snakechase.app.domain.model.DuelMode
import com.snakechase.app.domain.model.DuelPause
import com.snakechase.app.domain.model.DuelPhase
import com.snakechase.app.domain.model.MultiplayerRole
import com.snakechase.app.domain.model.SteeringMode
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.engine.MultiplayerEngine
import com.snakechase.app.network.ConnectivityMonitor
import com.snakechase.app.network.DuelSession
import com.snakechase.app.network.LanGuestTransport
import com.snakechase.app.network.LanHostTransport
import com.snakechase.app.network.OnlineRooms
import com.snakechase.app.network.QuickMatch
import com.snakechase.app.online.GhostRecorder
import com.snakechase.app.online.GhostTrack
import com.snakechase.app.online.OnlineDuelRepository
import com.snakechase.app.online.OnlineDuelRules
import com.snakechase.app.online.OnlineRun
import com.snakechase.app.online.PendingChallenge
import com.snakechase.app.online.PendingFinish
import com.snakechase.app.online.PendingRun
import com.snakechase.app.online.UploadQueue
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.platform.platformName
import com.snakechase.app.profile.Economy
import com.snakechase.app.profile.ProfileRepository
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.BaseNavy
import com.snakechase.app.ui.theme.Cream
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal val QUICK_CHAT_PHRASES = listOf(
    "Nice Dodge! ⚡",
    "Almost had you! 🐍",
    "Good Game! 👑",
    "Rematch? 🔥",
    "Watch this! 🎯",
)

internal val QUICK_CHAT_EMOJIS = listOf("🐍", "⚡", "🔥", "💀", "🎯", "👑", "😂", "🥶")

/** Keyboard steering aims this far ahead of the player (arena units) while a key is held. */
internal const val KEYBOARD_AIM_AHEAD = 120f

/** How long a quick-match host waits in the room for the accepted rival before refunding. */
internal const val QUICK_MATCH_JOIN_TIMEOUT_MS = 20_000L

@Composable
fun MultiplayerScreen(
    audioManager: AudioManager,
    hapticsManager: HapticsManager,
    onReturnToMenu: () -> Unit,
    playerName: String = "",
    /** Firebase-backed ghost duels; null (e.g. in UI tests) shows the arena as unavailable. */
    onlineRepository: OnlineDuelRepository? = null,
    modifier: Modifier = Modifier,
    /** Duel port; tests pass a free port so they never collide with a real game on 9090. */
    joinPortOverride: Int = DuelSession.DEFAULT_PORT,
    /** Internet rooms (Firebase). Null (tests, no Koin) leaves only the LAN "Nearby" mode. */
    onlineRooms: OnlineRooms? = null,
    /** Wallet + duel stats; null in UI tests (no stakes, no stats). */
    profiles: ProfileRepository? = null,
    connectivity: ConnectivityMonitor? = null,
    uploads: UploadQueue? = null,
    /** The player's Controls setting; KEYBOARD makes the arena ignore clicks and drags. */
    steeringMode: SteeringMode = SteeringMode.POINTER,
) {
    val scope = rememberCoroutineScope()
    val localName = remember(playerName) {
        playerName.trim().takeIf { it.isNotEmpty() && it != "Player 1" } ?: "$platformName Player"
    }
    // All networking (host server, guest client, heartbeat, protocol rules) lives in DuelSession;
    // this screen only renders its state and forwards user actions.
    val session = remember {
        DuelSession(
            uiScope = scope,
            localName = localName,
            hostTransports = { online ->
                if (online && onlineRooms != null) onlineRooms.host(localName) else LanHostTransport(joinPortOverride)
            },
            guestTransports = { online ->
                if (online && onlineRooms != null) onlineRooms.guest() else LanGuestTransport(joinPortOverride)
            },
            spectatorTransports = onlineRooms?.let { rooms -> { rooms.spectator() } },
        )
    }
    // How many people are watching this online room (players and spectators alike see it).
    var watchers by remember { mutableStateOf(0) }
    LaunchedEffect(session.state.roomCode, session.isOnlineRoom, session.state.isNetworkMatch) {
        watchers = 0
        val code = session.state.roomCode
        if (onlineRooms == null || !session.isOnlineRoom || !session.state.isNetworkMatch || code.isBlank()) return@LaunchedEffect
        runCatching { onlineRooms.watchers(code).collect { watchers = it } }
    }

    val quickMatch = remember { onlineRooms?.quickMatch(scope) }
    DisposableEffect(Unit) { onDispose { quickMatch?.cancel() } }

    // Friend rooms go over the internet by default; "Nearby" (LAN) is the no-internet fallback.
    var preferOnline by remember { mutableStateOf(onlineRooms != null) }
    var duelState by session

    var localInputPos by remember { mutableStateOf<Vec2?>(null) }
    var rivalInputPos by remember { mutableStateOf<Vec2?>(null) } // Pass & Play second finger
    var lastFrameTimeMs by remember { mutableStateOf(0L) }
    var showQuickChatPanel by remember { mutableStateOf(false) }
    var duelShare by remember { mutableStateOf<com.snakechase.app.share.ShareCardData?>(null) }
    var showAiDifficultyModal by remember { mutableStateOf(false) }

    // ─── Online ghost duel state ───
    var online by remember { mutableStateOf(OnlineUi()) }
    val ghostRecorder = remember { GhostRecorder() }
    var myFleeGhost by remember { mutableStateOf(GhostTrack.EMPTY) }

    // ─── Wallet: the arena tier whose entry fee is currently staked (null = friendly match) ───
    val profile = profiles?.profile?.collectAsState()?.value
    // What I wear, shared with my rival (and spectators) in live matches.
    session.localLook = com.snakechase.app.domain.model.DuelNetworkMessage.Look(
        snake = com.snakechase.app.config.Edition.unlocks.wornSkin(profile?.activeSkin.orEmpty(), profile?.unlockedSkins.orEmpty()).name,
        orb = com.snakechase.app.config.Edition.unlocks.wornOrb(profile?.activeOrb.orEmpty(), profile?.unlockedOrbs.orEmpty()).name,
    )
    val isOffline = connectivity?.online?.collectAsState()?.value == false
    var stakedTier by remember { mutableStateOf<Int?>(null) }
    var settledResult by remember { mutableStateOf<DuelMatchResult?>(null) }

    /** Takes the entry fee for [tier]; false if the player can't afford it. */
    fun stake(tier: Int): Boolean {
        val p = profiles ?: return true
        if (!p.spend(ARENA_TIERS[tier].entryFee)) return false
        stakedTier = tier
        return true
    }

    /** The match never started (no-show, cancelled in the lobby…): hand the entry fee back. */
    fun refundStake() {
        val tier = stakedTier ?: return
        stakedTier = null
        profiles?.addCoins(ARENA_TIERS[tier].entryFee)
    }

    // Both players accepted and the room exists. This is the ONE place a quick match is staked:
    // it runs once per match, on host and guest alike, right before the room is entered.
    LaunchedEffect(quickMatch?.status) {
        val matched = quickMatch?.status as? QuickMatch.Status.Matched ?: return@LaunchedEffect
        val code = matched.roomCode ?: return@LaunchedEffect
        if (stakedTier == null) stake(duelState.selectedTierIndex)
        quickMatch.cancel()
        if (!matched.iAmHost) session.joinRoom(code, online = true)
    }

    // A staked quick match that never actually starts (host: rival never joins; guest: the room
    // is gone or the host never starts): give up after a while and refund the entry fee.
    val waitingPhases = setOf(DuelPhase.CHALLENGE_INVITE, DuelPhase.MATCHMAKING)
    LaunchedEffect(duelState.phase, stakedTier) {
        if (duelState.phase !in waitingPhases || stakedTier == null) return@LaunchedEffect
        delay(QUICK_MATCH_JOIN_TIMEOUT_MS)
        if (duelState.phase in waitingPhases && stakedTier != null) {
            refundStake()
            session.leave()
            duelState = duelState.copy(phase = DuelPhase.MATCHMAKING, opponentLeftReason = null)
            online = online.copy(status = "Your opponent didn't join — entry fee refunded.")
        }
    }

    /**
     * Ends a *played* match: pays the prize (or not) and records the duel. Returns the coin change,
     * or null for an unstaked (friendly) match.
     */
    fun settleStake(won: Boolean): Int? {
        if (duelState.isSpectator) return null // watching isn't playing: no coins, no win/loss record
        val tier = stakedTier
        stakedTier = null
        profiles?.recordDuel(won)
        if (tier == null || profiles == null) return null
        val t = ARENA_TIERS[tier]
        return if (won) {
            profiles.addCoins(t.prizePool)
            t.prizePool - t.entryFee
        } else {
            -t.entryFee
        }
    }

    fun submitOnlineRun(run: OnlineRun) {
        val repo = onlineRepository ?: return
        val opponent = online.opponent
        online = online.copy(submitting = true, submitError = null)
        scope.launch {
            runCatching {
                if (opponent != null) {
                    repo.finishMatch(opponent, run, online.verdict?.iWon == true)
                } else {
                    repo.postChallenge(duelState.selectedTierIndex, localName, run)
                }
            }.onSuccess { online = online.copy(submitting = false, submitted = true) }
                .onFailure { e ->
                    val q = uploads
                    if (q != null && connectivity?.checkNow() != true) {
                        // Offline: keep the run on the device and send it when the internet is back.
                        q.enqueue(
                            if (opponent != null) {
                                PendingFinish(opponent.matchId, opponent.uid, opponent.name, online.verdict?.iWon == true, PendingRun.of(run))
                            } else {
                                PendingChallenge(duelState.selectedTierIndex, localName, PendingRun.of(run))
                            },
                        )
                        online = online.copy(submitting = false, queued = true, submitError = null)
                    } else {
                        online = online.copy(submitting = false, submitError = e.message ?: "Upload failed")
                    }
                }
        }
    }

    fun finishOnlineMatch(run: OnlineRun) {
        val theirs = online.opponent?.run ?: OnlineRun.HOUSE
        val verdict = OnlineDuelRules.decide(run, theirs)
        online = online.copy(myRun = run, verdict = verdict, coinsDelta = settleStake(verdict.iWon))
        audioManager.stopBgm()
        duelState = duelState.copy(phase = DuelPhase.MATCH_OVER)
        submitOnlineRun(run)
    }

    fun findOnlineOpponent(tier: Int) {
        val repo = onlineRepository
        if (repo == null) {
            online = online.copy(status = "Online arena is not available in this build.")
            return
        }
        if (isOffline) {
            online = online.copy(status = "📡 You're offline — connect to the internet to duel online.")
            return
        }
        if (!stake(tier)) {
            online = online.copy(status = "Not enough ⚡ for ${ARENA_TIERS[tier].name}.")
            return
        }
        online = OnlineUi(searching = true, status = "Searching for a rival in ${ARENA_TIERS[tier].name}…", inbox = online.inbox)
        scope.launch {
            val found = runCatching { withTimeout(12_000) { repo.findOpponent(tier, localName) } }
            found.onFailure { e ->
                // The duel never started: give the entry fee back.
                profiles?.addCoins(ARENA_TIERS[tier].entryFee)
                stakedTier = null
                connectivity?.recheck()
                online = online.copy(searching = false, status = "📡 ${e.message ?: "Could not reach the online arena."}")
                return@launch
            }
            val opponent = found.getOrNull()
            online = OnlineUi(opponent = opponent, inbox = online.inbox)
            myFleeGhost = GhostTrack.EMPTY
            duelState = MultiplayerEngine.startMatch(
                mode = DuelMode.ONLINE_SERVER,
                player1Name = localName,
                player2Name = opponent?.name ?: OnlineRun.HOUSE_NAME,
            ).copy(selectedTierIndex = tier, roundAnnouncement = "ROUND 1 — FLEE THE AI SNAKE!")
        }
    }

    DisposableEffect(Unit) {
        onDispose { session.dispose() }
    }

    fun leaveToModeSelect() {
        // Quitting a staked match mid-game forfeits it; leaving before it started is a refund.
        if (stakedTier != null) {
            if (duelState.phase in setOf(DuelPhase.COUNTDOWN, DuelPhase.ROUND_PLAYING, DuelPhase.ROUND_TRANSITION)) {
                settleStake(won = false)
            } else if (duelState.phase != DuelPhase.MATCH_OVER) {
                refundStake()
            }
        }
        stakedTier = null
        audioManager.stopBgm()
        session.leave()
        duelState = duelState.copy(phase = DuelPhase.MODE_SELECT, opponentLeftReason = null)
    }

    // Live / AI match finished: pay coins and record the duel exactly once per result.
    LaunchedEffect(duelState.phase, duelState.result) {
        val result = duelState.result ?: return@LaunchedEffect
        if (duelState.phase != DuelPhase.MATCH_OVER || duelState.mode == DuelMode.ONLINE_SERVER) return@LaunchedEffect
        if (settledResult === result) return@LaunchedEffect
        settledResult = result
        when {
            duelState.isNetworkMatch -> settleStake(result.isLocalWinner)
            duelState.mode == DuelMode.VS_AI_RIVAL && result.isLocalWinner -> profiles?.addCoins(Economy.VS_AI_WIN)
        }
    }

    // The link to the rival dropped. If *I* still have internet, they left: I win by forfeit.
    // If I'm the one offline, the staked match is lost and the overlay says so.
    var iLostConnection by remember { mutableStateOf(false) }
    LaunchedEffect(duelState.opponentLeftReason) {
        if (duelState.opponentLeftReason == null) {
            iLostConnection = false
            return@LaunchedEffect
        }
        val midMatch = duelState.phase in setOf(DuelPhase.COUNTDOWN, DuelPhase.ROUND_PLAYING, DuelPhase.ROUND_TRANSITION)
        val stillOnline = !duelState.isNetworkMatch || preferOnline.not() || (connectivity?.checkNow() ?: true)
        iLostConnection = !stillOnline
        when {
            midMatch && duelState.isNetworkMatch -> settleStake(won = stillOnline)
            duelState.phase != DuelPhase.MATCH_OVER -> refundStake() // gone before the match began
        }
    }

    // Pause clock: live online pauses last DuelPause.WINDOW_MS. If I paused and didn't come back,
    // I forfeit; if my rival did, the match is mine (their app may be gone, so I don't wait on them).
    var pauseLeftMs by remember { mutableStateOf<Long?>(null) }
    var forfeitedByPause by remember { mutableStateOf(false) }
    LaunchedEffect(duelState.pausedBy) {
        while (duelState.pausedBy != null) {
            val left = session.pauseMsLeft()
            pauseLeftMs = left
            if (left == null) return@LaunchedEffect // no time limit (e.g. against the AI)
            if (left <= 0L) {
                if (duelState.pausedBy == DuelSession.YOU) forfeitedByPause = true else session.rivalPauseExpired()
                return@LaunchedEffect
            }
            delay(200)
        }
        pauseLeftMs = null
    }

    // 60fps Duel Simulation Loop (Authoritative on Host, Synced on Guest)
    LaunchedEffect(duelState.phase) {
        if (duelState.phase == DuelPhase.ROUND_PLAYING) {
            lastFrameTimeMs = 0L
            while (isActive) {
                withFrameMillis { frameTimeMs ->
                    if (lastFrameTimeMs != 0L) {
                        val deltaMs = (frameTimeMs - lastFrameTimeMs).coerceIn(1L, 50L)
                        // Paused (by either player): the host stops simulating, so both screens freeze.
                        if (duelState.pausedBy == null && (!duelState.isNetworkMatch || duelState.isHost)) {
                            val rival = if (duelState.isNetworkMatch) session.rivalInputPos else rivalInputPos
                            val roundBefore = duelState.round
                            // My worn skins' perks (the engine only uses them against the AI).
                            val unlocks = com.snakechase.app.config.Edition.unlocks
                            val withPerks = duelState.copy(
                                localSnakePerk = com.snakechase.app.domain.model.SkinPerks.of(unlocks.wornSkin(profile?.activeSkin.orEmpty(), profile?.unlockedSkins.orEmpty())),
                                localOrbPerk = com.snakechase.app.domain.model.SkinPerks.of(unlocks.wornOrb(profile?.activeOrb.orEmpty(), profile?.unlockedOrbs.orEmpty())),
                            )
                            val result = MultiplayerEngine.tick(withPerks, localInputPos, rival, deltaMs)
                            duelState = result.newState
                            if (duelState.isNetworkMatch) session.onHostTick(result)
                            if (duelState.mode == DuelMode.ONLINE_SERVER) {
                                // Record what *I* control this round: the orb when fleeing, the head when hunting.
                                val me = if (duelState.localRole == MultiplayerRole.PREY) {
                                    duelState.prey.position
                                } else {
                                    duelState.snake?.headPosition
                                }
                                me?.let { ghostRecorder.record(duelState.survivalTimeMs, it) }
                                val ended = result.roundCompleted || result.matchCompleted
                                if (ended && roundBefore == 1) myFleeGhost = ghostRecorder.finish()
                                if (ended && roundBefore == 2) {
                                    finishOnlineMatch(
                                        OnlineRun(
                                            fleeMs = duelState.round1TimeMs,
                                            huntMs = duelState.round2TimeMs,
                                            ghostFlee = myFleeGhost,
                                            ghostHunt = ghostRecorder.finish(),
                                        ),
                                    )
                                }
                            }
                        } else {
                            session.guestFrame()
                            localInputPos?.let { session.sendGuestInput(it) }
                        }
                    }
                    lastFrameTimeMs = frameTimeMs
                }
            }
        }
    }

    // Countdown sequence before round start
    LaunchedEffect(duelState.phase, duelState.round) {
        if (duelState.phase == DuelPhase.COUNTDOWN) {
            // A new round starts from the spawn points: forget where the pointers were last round.
            localInputPos = null
            rivalInputPos = null
            ghostRecorder.reset()
            audioManager.playBgm(DUEL_MUSIC) // duels keep the Eclipse theme (SoundBank.musicFile)
            // Same rhythm as the levels' 3-2-1-Go, so a match never starts out of nowhere.
            for (n in 3 downTo 1) {
                duelState = duelState.copy(countdownCount = n)
                audioManager.playSfx(com.snakechase.app.domain.model.SoundId.COUNTDOWN_TICK)
                delay(COUNTDOWN_STEP_MS)
            }
            duelState = duelState.copy(countdownCount = 0) // "Go!"
            audioManager.playSfx(com.snakechase.app.domain.model.SoundId.COUNTDOWN_GO)
            delay(GO_HOLD_MS)
            duelState = duelState.copy(phase = DuelPhase.ROUND_PLAYING)
        }
    }

    // Round transition auto-advance (host decides; guest waits for StartCountdown)
    LaunchedEffect(duelState.phase, duelState.round) {
        if (duelState.phase == DuelPhase.ROUND_TRANSITION) {
            delay(2800)
            if (!duelState.isNetworkMatch || duelState.isHost) {
                val next = when {
                    duelState.round == 1 -> MultiplayerEngine.startRound2(duelState)
                    duelState.round == 2 && duelState.player1RoundsWon == 1 && duelState.player2RoundsWon == 1 ->
                        MultiplayerEngine.startRound3(duelState)
                    else -> null
                }
                if (next != null) {
                    duelState = if (next.mode == DuelMode.ONLINE_SERVER) {
                        next.copy(roundAnnouncement = "ROUND 2 — HUNT THE AI PREY!")
                    } else {
                        next
                    }
                    if (duelState.isNetworkMatch) {
                        session.broadcastRoundStart(next.round, hostIsSnake = next.localRole == MultiplayerRole.SNAKE)
                    }
                }
            }
        }
    }

    // Slow-motion catch replay (as in the levels): the arena's last half second is kept; when a
    // round ends with the snake on the prey, it plays back zooming in on the bite.
    val recentFrames = remember { ArrayDeque<com.snakechase.app.domain.model.GameState>() }
    var catchReplay by remember { mutableStateOf<com.snakechase.app.presentation.DeathReplay?>(null) }
    var wasPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(duelState.phase) {
        val playing = duelState.phase == DuelPhase.ROUND_PLAYING
        if (wasPlaying && !playing && duelState.phase != DuelPhase.MODE_SELECT && recentFrames.size >= 2) {
            val last = recentFrames.last()
            val head = last.primarySnake?.headPosition
            val caught = head != null && head.distanceTo(last.player.position) < CAUGHT_DISTANCE
            if (caught) {
                catchReplay = com.snakechase.app.presentation.DeathReplay(recentFrames.toList(), last.player.position)
                val iHunted = duelState.localRole == MultiplayerRole.SNAKE && !duelState.isSpectator
                audioManager.playSfx(com.snakechase.app.domain.model.SoundId.BULLET_TIME, 0.6f)
                delay(com.snakechase.app.presentation.DeathReplay.PLAYBACK_MS)
                audioManager.playSfx(if (iHunted) com.snakechase.app.domain.model.SoundId.STAR_3 else com.snakechase.app.domain.model.SoundId.DEATH)
            }
        }
        if (!playing) recentFrames.clear()
        wasPlaying = playing
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BaseNavy),
    ) {
        val isCompact = maxHeight < 500.dp

        // Same drawn jungle as the main menu.
        com.snakechase.app.ui.components.JungleBackdrop()

        when (duelState.phase) {
            DuelPhase.MODE_SELECT -> {
                DuelModeSelectView(
                    isCompact = isCompact,
                    coins = profile?.coins,
                    isOffline = isOffline,
                    lanAvailable = session.canHostLan,
                    selectedTierIndex = duelState.selectedTierIndex,
                    onSelectTier = { index -> duelState = duelState.copy(selectedTierIndex = index) },
                    onSelectMode = { mode ->
                        when (mode) {
                            DuelMode.VS_AI_RIVAL -> {
                                showAiDifficultyModal = true
                            }
                            DuelMode.LOCAL_INVITE_CHALLENGE -> {
                                // No internet: friend rooms fall back to Nearby (same Wi-Fi) automatically.
                                if (isOffline) preferOnline = false
                                if (!preferOnline && !session.canHostLan) {
                                    // iOS/web without internet rooms: can only join a LAN host.
                                    duelState = duelState.copy(mode = DuelMode.LOCAL_INVITE_CHALLENGE, phase = DuelPhase.CHALLENGE_INVITE)
                                    session.showJoinTab()
                                } else {
                                    scope.launch { session.startHosting(online = preferOnline) }
                                }
                            }
                            DuelMode.LOCAL_PASS_AND_PLAY -> {
                                duelState = MultiplayerEngine.startMatch(
                                    mode = DuelMode.LOCAL_PASS_AND_PLAY,
                                    player1Name = "Player 1 (Left)",
                                    player2Name = "Player 2 (Right)",
                                )
                            }
                            DuelMode.ONLINE_SERVER -> {
                                duelState = duelState.copy(phase = DuelPhase.MATCHMAKING, isConnecting = true)
                            }
                        }
                    },
                    onBack = onReturnToMenu,
                )
            }

            DuelPhase.CHALLENGE_INVITE -> {
                DuelChallengeInviteView(
                    state = duelState,
                    isCompact = isCompact,
                    localHostIp = session.localHostIp,
                    connectionStatusText = session.statusText,
                    canHost = preferOnline || session.canHostLan,
                    nearbyAvailable = session.canHostLan,
                    online = preferOnline,
                    canGoOnline = onlineRooms != null,
                    onSelectOnline = { online ->
                        if (online != preferOnline) {
                            preferOnline = online
                            if (duelState.lobbyShowsJoin || (!online && !session.canHostLan)) {
                                session.showJoinTab()
                            } else {
                                scope.launch { session.startHosting(online = online) }
                            }
                        }
                    },
                    // Tab clicks are explicit user choices: Host opens a fresh room (leaving any
                    // join in progress); Join closes our room so the port is free.
                    onStartHostServer = { scope.launch { session.startHosting(online = preferOnline) } },
                    onShowJoinTab = { session.showJoinTab() },
                    onJoinWithIpAndCode = { targetIp, code -> session.joinRoom(code, online = preferOnline, hostAddress = targetIp) },
                    onStartMatch = { session.hostStartMatch() },
                    onBack = { leaveToModeSelect() },
                    onWatch = if (session.canSpectate && !isOffline) { code -> session.spectateRoom(code) } else null,
                )
            }

            DuelPhase.MATCHMAKING -> {
                LaunchedEffect(Unit) {
                    // Results of challenges I posted earlier that someone has since played.
                    val repo = onlineRepository ?: return@LaunchedEffect
                    runCatching { repo.inbox() }.onSuccess { online = online.copy(inbox = it) }
                }
                OnlineArenaLobby(
                    selectedTierIndex = duelState.selectedTierIndex,
                    playerName = localName,
                    online = online,
                    isCompact = isCompact,
                    onSelectTier = { idx -> duelState = duelState.copy(selectedTierIndex = idx) },
                    onFindOpponent = { findOnlineOpponent(duelState.selectedTierIndex) },
                    quickMatchStatus = quickMatch?.status,
                    onQuickMatch = {
                        val tier = duelState.selectedTierIndex
                        quickMatch?.start(tier, localName) {
                            // Called on the host once both accepted: open a live room for the pair.
                            if (session.startHosting(online = true)) {
                                session.autoStartWhenGuestJoins = true
                                duelState = duelState.copy(selectedTierIndex = tier)
                                duelState.roomCode
                            } else {
                                null
                            }
                        }
                    },
                    onAcceptMatch = { quickMatch?.accept() },
                    onDeclineMatch = { quickMatch?.decline() },
                    onCancelQuickMatch = { quickMatch?.cancel() },
                    coins = profile?.coins ?: Int.MAX_VALUE,
                    isOffline = isOffline,
                    onBack = {
                        quickMatch?.cancel()
                        online = OnlineUi(inbox = online.inbox)
                        duelState = duelState.copy(phase = DuelPhase.MODE_SELECT, isConnecting = false)
                    },
                )
            }

            DuelPhase.COUNTDOWN,
            DuelPhase.ROUND_PLAYING,
            DuelPhase.ROUND_TRANSITION,
            -> {
                DuelArenaView(
                    mySkin = com.snakechase.app.config.Edition.unlocks.wornSkin(profile?.activeSkin.orEmpty(), profile?.unlockedSkins.orEmpty()),
                    myOrb = com.snakechase.app.config.Edition.unlocks.wornOrb(profile?.activeOrb.orEmpty(), profile?.unlockedOrbs.orEmpty()),
                    state = duelState,
                    isCompact = isCompact,
                    localInputPos = localInputPos,
                    showQuickChat = showQuickChatPanel,
                    onToggleQuickChat = { showQuickChatPanel = !showQuickChatPanel },
                    onSendQuickChat = { msg, emoji ->
                        session.sendChat(msg, emoji)
                        showQuickChatPanel = false
                    },
                    onLocalMove = { pos -> if (!duelState.isSpectator) localInputPos = pos },
                    onRivalMove = { pos -> rivalInputPos = pos },
                    onQuit = { leaveToModeSelect() },
                    keyboardOnly = steeringMode == SteeringMode.KEYBOARD,
                    useJoystick = steeringMode == SteeringMode.VIRTUAL_STICK,
                    ghost = if (duelState.mode == DuelMode.ONLINE_SERVER) onlineGhostFor(duelState, online) else null,
                    allowQuickChat = !duelState.isSpectator,
                    onPause = if (duelState.isSpectator) null else ({ session.pause() }),
                    onResume = { session.resume() },
                    pauseMsLeft = pauseLeftMs,
                    onFrame = { f ->
                        recentFrames.addLast(f)
                        while (recentFrames.size > com.snakechase.app.presentation.DeathReplay.FRAMES) recentFrames.removeFirst()
                    },
                )
                if (forfeitedByPause) {
                    ArcadeDialog(title = "Time's up", onClose = null, icon = ArcadeIcon.Pause, width = 420.dp, dismissOnScrim = false) {
                        Text(
                            "You didn't come back within ${DuelPause.WINDOW_MS / 1000} seconds, so your rival wins this match.",
                            color = Cream,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ArcadeButton("OK", onClick = {
                            forfeitedByPause = false
                            leaveToModeSelect()
                        }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                }
            }

            DuelPhase.MATCH_OVER -> if (duelState.mode == DuelMode.ONLINE_SERVER) {
                OnlineResultView(
                    online = online,
                    myName = localName,
                    isCompact = isCompact,
                    onRetryUpload = { online.myRun?.let(::submitOnlineRun) },
                    onPlayAgain = {
                        online = OnlineUi(inbox = online.inbox)
                        duelState = duelState.copy(phase = DuelPhase.MATCHMAKING)
                    },
                    onMenu = {
                        online = OnlineUi(inbox = online.inbox)
                        leaveToModeSelect()
                    },
                )
            } else {
                duelState.result?.let { result ->
                    DuelMatchOverView(
                        result = result,
                        state = duelState,
                        isCompact = isCompact,
                        onShare = {
                            duelShare = com.snakechase.app.share.ShareCards.duel(
                                result = result,
                                arenaLevelId = duelArenaLevel(duelState.selectedTierIndex),
                                vsAi = duelState.mode == DuelMode.VS_AI_RIVAL,
                                player = com.snakechase.app.share.ShareIdentity(
                                    // The name the match itself used, so the card tells it from my side.
                                    name = localName,
                                    avatar = profile?.avatar ?: 0,
                                    skin = com.snakechase.app.config.Edition.unlocks.wornSkin(profile?.activeSkin.orEmpty(), profile?.unlockedSkins.orEmpty()),
                                    orb = com.snakechase.app.config.Edition.unlocks.wornOrb(profile?.activeOrb.orEmpty(), profile?.unlockedOrbs.orEmpty()),
                                ),
                            )
                        },
                        onPlayAgain = {
                            when {
                                // Networked rematch: both players must agree; the host then restarts both sides.
                                duelState.isNetworkMatch -> session.voteRematch(true)
                                else -> duelState = MultiplayerEngine.startMatch(
                                    mode = duelState.mode,
                                    player1Name = duelState.player1Name,
                                    player2Name = duelState.player2Name,
                                    roomCode = duelState.roomCode,
                                )
                            }
                        },
                        onReturnToMenu = { leaveToModeSelect() },
                    )
                    // A win rains confetti over the result card (it only draws: buttons still work).
                    if (result.isLocalWinner && !duelState.isSpectator) {
                        val fx = com.snakechase.app.ui.components.rememberCelebrationState()
                        com.snakechase.app.ui.components.CelebrationLayer(
                            fx,
                            listOf(com.snakechase.app.ui.theme.Banana, com.snakechase.app.ui.theme.Lime, com.snakechase.app.ui.theme.Sky, com.snakechase.app.ui.theme.Cream, com.snakechase.app.ui.theme.Berry),
                            Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        duelShare?.let { com.snakechase.app.ui.share.SharePage(it) { duelShare = null } }

        if (showAiDifficultyModal) {
            AiDifficultySelectorModal(
                onSelectDifficulty = { diff ->
                    showAiDifficultyModal = false
                    duelState = MultiplayerEngine.startMatch(
                        mode = DuelMode.VS_AI_RIVAL,
                        player1Name = "Player 1 (Snake)",
                        player2Name = "AI ${diff.title.lowercase().replaceFirstChar { it.uppercase() }}",
                        difficulty = diff,
                    )
                },
                onDismiss = { showAiDifficultyModal = false },
            )
        }

        if (duelState.phase == DuelPhase.MATCH_OVER && duelState.isNetworkMatch && duelState.mode != DuelMode.ONLINE_SERVER) {
            val rival = if (duelState.isHost) duelState.player2Name else duelState.player1Name
            val text = when {
                duelState.isSpectator && session.rematchRemote == true -> "${duelState.player1Name} wants a rematch — stay to watch it"
                duelState.isSpectator -> null
                session.rematchLocal == true && session.rematchRemote == true -> "Starting the rematch…"
                session.rematchLocal == true -> "Waiting for $rival to accept the rematch…"
                session.rematchRemote == true -> "$rival wants a rematch. Tap Rematch to accept!"
                else -> null
            }
            if (text != null) {
                com.snakechase.app.ui.theme.ArcadeChip(
                    text,
                    fill = com.snakechase.app.ui.theme.Banana,
                    icon = com.snakechase.app.ui.theme.ArcadeIcon.Refresh,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                )
            }
        }

        duelState.opponentLeftReason?.let { reason ->
            OpponentLeftOverlay(
                reason = if (iLostConnection) "📡 You lost your internet connection." else reason,
                midMatch = duelState.phase in setOf(DuelPhase.COUNTDOWN, DuelPhase.ROUND_PLAYING, DuelPhase.ROUND_TRANSITION),
                onContinue = { leaveToModeSelect() },
                isSpectator = duelState.isSpectator,
            )
        }

        // Spectators: a "watching" tag; players: how many people are watching their duel.
        val inArena = duelState.phase in setOf(DuelPhase.COUNTDOWN, DuelPhase.ROUND_PLAYING, DuelPhase.ROUND_TRANSITION)
        if (inArena && duelState.isNetworkMatch && session.isOnlineRoom && (duelState.isSpectator || watchers > 0)) {
            WatchersBadge(
                spectating = duelState.isSpectator,
                watchers = watchers,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp),
            )
        }

        // A spectator has no link to the other player, so a ping figure would mean nothing.
        if (duelState.isNetworkMatch &&
            !duelState.isSpectator &&
            duelState.latencyMs != null &&
            duelState.phase in setOf(DuelPhase.COUNTDOWN, DuelPhase.ROUND_PLAYING, DuelPhase.ROUND_TRANSITION)
        ) {
            LatencyBadge(
                latencyMs = duelState.latencyMs!!,
                // Under the round clock in the top HUD, clear of the bottom steering hint.
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            )
        }

        catchReplay?.let { r ->
            com.snakechase.app.ui.screens.DeathReplayView(r) { catchReplay = null }
        }
    }
}

private const val COUNTDOWN_STEP_MS = 850L
private const val GO_HOLD_MS = 400L

/** Not a level id: SoundBank.musicFile plays the Eclipse theme for duels. */
private const val DUEL_MUSIC = 0

/** Snake head this close to the prey at the end of a round = it was a catch, not a timeout. */
private const val CAUGHT_DISTANCE = 48f
