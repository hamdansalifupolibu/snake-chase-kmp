package com.snakechase.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.network.ConnectivityMonitor
import com.snakechase.app.network.OnlineRooms
import com.snakechase.app.online.OnlineDuelRepository
import com.snakechase.app.online.UploadQueue
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.presentation.GameIntent
import com.snakechase.app.presentation.GameViewModel
import com.snakechase.app.ui.components.DailyRewardDialog
import com.snakechase.app.ui.components.OfflineBanner
import com.snakechase.app.ui.components.ProfileEditor
import com.snakechase.app.ui.components.RotateDeviceGuard
import com.snakechase.app.ui.components.WelcomeTour
import com.snakechase.app.ui.screens.GameOverScreen
import com.snakechase.app.ui.screens.GameScreen
import com.snakechase.app.ui.screens.MenuScreen
import com.snakechase.app.ui.screens.MultiplayerScreen
import com.snakechase.app.ui.theme.BaseNavy
import com.snakechase.app.ui.theme.SnakeChaseTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    SnakeChaseTheme {
        // safeDrawing keeps UI clear of status bar, nav bar and camera cutouts on
        // Android/iOS; on desktop the insets are zero so this is a no-op.
        Surface(
            // Paint navy first (fills behind the system bars), then inset the content.
            modifier = Modifier
                .fillMaxSize()
                .background(BaseNavy)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = BaseNavy,
        ) {
            val viewModel = koinViewModel<GameViewModel>()
            val audioManager = koinInject<AudioManager>()
            val hapticsManager = koinInject<HapticsManager>()
            val state by viewModel.state.collectAsState()

            // App-wide online services: reachability checks and the offline upload queue.
            val connectivity = koinInject<ConnectivityMonitor>()
            val uploads = koinInject<UploadQueue>()
            val appScope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                connectivity.start(appScope)
                uploads.start(appScope)
            }
            val isOffline = connectivity.online.collectAsState().value == false
            val pendingUploads by uploads.pending.collectAsState()
            var editingProfile by remember { mutableStateOf(false) }
            var profileShare by remember { mutableStateOf<com.snakechase.app.share.ShareCardData?>(null) }

            androidx.compose.runtime.CompositionLocalProvider(
                com.snakechase.app.ui.theme.LocalButtonSound provides { audioManager.playSfx(com.snakechase.app.domain.model.SoundId.BUTTON_CLICK) },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (state.phase) {
                        GamePhase.MENU -> {
                            MenuScreen(state = state, viewModel = viewModel)
                        }
                        GamePhase.COUNTDOWN,
                        GamePhase.PLAYING,
                        GamePhase.PAUSED,
                        -> InLevelTheme(state.levelId) {
                            GameScreen(state = state, viewModel = viewModel)
                        }
                        GamePhase.LEVEL_VICTORY -> InLevelTheme(state.levelId) {
                            com.snakechase.app.ui.screens.LevelVictoryScreen(state = state, viewModel = viewModel)
                        }
                        GamePhase.GAME_OVER,
                        GamePhase.LEVEL_COMPLETE,
                        -> InLevelTheme(state.levelId) {
                            val replay by viewModel.deathReplay.collectAsState()
                            val r = replay
                            if (r != null && state.phase == GamePhase.GAME_OVER) {
                                com.snakechase.app.ui.screens.DeathReplayView(r) { viewModel.handleIntent(GameIntent.DeathReplayDone) }
                            } else {
                                GameOverScreen(state = state, viewModel = viewModel)
                            }
                        }
                        GamePhase.MULTIPLAYER_PLAYING,
                        GamePhase.MULTIPLAYER_QUEUE,
                        -> {
                            MultiplayerScreen(
                                audioManager = audioManager,
                                hapticsManager = hapticsManager,
                                playerName = state.settings.playerName,
                                onlineRepository = koinInject<OnlineDuelRepository>(),
                                onlineRooms = koinInject<OnlineRooms>(),
                                profiles = koinInject(),
                                connectivity = koinInject(),
                                uploads = koinInject(),
                                steeringMode = state.settings.steeringMode,
                                onReturnToMenu = { viewModel.handleIntent(GameIntent.ReturnToMenu) },
                            )
                        }
                        GamePhase.SNAKE_HUNT -> {
                            com.snakechase.app.ui.screens.SnakeHuntScreen(
                                playerName = state.profile.name,
                                steeringMode = state.settings.steeringMode,
                                progress = koinInject(),
                                audioManager = audioManager,
                                hapticsManager = hapticsManager,
                                profiles = koinInject(),
                                onExit = { viewModel.handleIntent(GameIntent.ReturnToMenu) },
                            )
                        }
                        else -> {
                            MenuScreen(state = state, viewModel = viewModel)
                        }
                    }

                    val inGame = state.phase in setOf(
                        GamePhase.COUNTDOWN,
                        GamePhase.PLAYING,
                        GamePhase.PAUSED,
                        GamePhase.MULTIPLAYER_PLAYING,
                        GamePhase.MULTIPLAYER_QUEUE,
                        GamePhase.SNAKE_HUNT,
                    )
                    if (isOffline && !inGame) {
                        OfflineBanner(pendingUploads, Modifier.align(Alignment.TopCenter))
                    }

                    // ─── Profile (first launch is mandatory: rivals and the leaderboard see this name) ───
                    when {
                        !state.profile.isSetUp -> ProfileEditor(
                            profile = state.profile,
                            firstTime = true,
                            onSave = { name, avatar -> viewModel.handleIntent(GameIntent.SaveProfile(name, avatar)) },
                        )
                        editingProfile -> ProfileEditor(
                            profile = state.profile,
                            firstTime = false,
                            onSave = { name, avatar ->
                                viewModel.handleIntent(GameIntent.SaveProfile(name, avatar))
                                editingProfile = false
                            },
                            onClose = { editingProfile = false },
                        )
                        profileShare != null -> com.snakechase.app.ui.share.SharePage(profileShare!!) { profileShare = null }
                        state.showProfileOverlay && !inGame -> {
                            val hunts = org.koin.compose.koinInject<com.snakechase.app.hunt.HuntProgress>()
                            val card = com.snakechase.app.profile.PlayerCard(
                                profile = state.profile,
                                levelStars = state.levelStarRatings,
                                huntStars = (1..5).associateWith { hunts.stars(it) },
                            )
                            com.snakechase.app.ui.components.ProfilePage(
                                card = card,
                                skin = state.activeSnakeSkin,
                                orb = state.activeOrbSkin,
                                isOffline = isOffline,
                                pendingUploads = pendingUploads,
                                onEdit = { editingProfile = true },
                                onShare = {
                                    profileShare = com.snakechase.app.share.ShareCards.profile(
                                        card,
                                        com.snakechase.app.share.ShareIdentity(state.profile.name, state.profile.avatar, state.activeSnakeSkin, state.activeOrbSkin),
                                    )
                                },
                                onClose = { viewModel.handleIntent(GameIntent.CloseProfile) },
                            )
                        }
                        state.dailyClaim != null && state.phase == GamePhase.MENU -> DailyRewardDialog(
                            claim = state.dailyClaim!!,
                            onClose = { viewModel.handleIntent(GameIntent.DismissDailyReward) },
                        )
                    }

                    // Welcome tour, once, after the name is set and any daily reward is collected.
                    if (state.phase == GamePhase.MENU && state.profile.isSetUp && !state.settings.tourSeen && state.dailyClaim == null) {
                        WelcomeTour(onDone = {
                            viewModel.handleIntent(GameIntent.UpdateSettings { it.copy(tourSeen = true) })
                        })
                    }

                    // Phone browsers held upright: guide the player to turn the phone (web only).
                    RotateDeviceGuard()

                    // Opening moment: the original painting, the logo and a bead loader, then fade in.
                    var showLaunch by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(LAUNCH_SCREEN_MS)
                        showLaunch = false
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showLaunch,
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(450)),
                    ) {
                        com.snakechase.app.ui.components.LaunchScreen()
                    }
                }
            }
        }
    }
}

/** Inside a level, every panel and title plate takes that level's own colours. */
@Composable
private fun InLevelTheme(levelId: Int, content: @Composable () -> Unit) {
    val look = com.snakechase.app.ui.components.levelLook(levelId)
    androidx.compose.runtime.CompositionLocalProvider(
        com.snakechase.app.ui.theme.LocalPanelFill provides look.panel,
        com.snakechase.app.ui.theme.LocalPanelAccent provides look.accent,
        content = content,
    )
}

/** How long the launch screen stays up before fading into the game. */
private const val LAUNCH_SCREEN_MS = 2_000L
