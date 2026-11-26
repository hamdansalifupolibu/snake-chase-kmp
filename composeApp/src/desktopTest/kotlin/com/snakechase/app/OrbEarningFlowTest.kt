package com.snakechase.app

import com.russhwolf.settings.MapSettings
import com.snakechase.app.domain.model.GamePhase
import com.snakechase.app.domain.repository.SettingsRepositoryImpl
import com.snakechase.app.hunt.HuntProgress
import com.snakechase.app.network.ConnectivityMonitor
import com.snakechase.app.online.OnlineDuelRepository
import com.snakechase.app.online.UploadQueue
import com.snakechase.app.platform.AudioManager
import com.snakechase.app.platform.HapticsManager
import com.snakechase.app.presentation.GameIntent
import com.snakechase.app.presentation.GameViewModel
import com.snakechase.app.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drives the real GameViewModel frame by frame through the flows that can earn orb skins. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrbEarningFlowTest {
    private lateinit var profiles: ProfileRepository
    private lateinit var vm: GameViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val settings = MapSettings()
        profiles = ProfileRepository(settings)
        val monitor = ConnectivityMonitor(probe = { false })
        val repo = OnlineDuelRepository(FakeFirestore(Clock.System), currentUid = { "test" })
        vm = GameViewModel(
            audioManager = AudioManager(),
            hapticsManager = HapticsManager(),
            settingsRepository = SettingsRepositoryImpl(settings),
            profiles = profiles,
            uploads = UploadQueue(settings, repo, monitor) { profiles.profile.value },
            huntProgress = HuntProgress(settings),
            lives = com.snakechase.app.profile.Lives(settings, autoRefill = true, now = { 0L }),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun startLevel(id: Int) {
        vm.handleIntent(GameIntent.SelectLevel(id))
        vm.handleIntent(GameIntent.StartSelectedLevel)
        vm.handleIntent(GameIntent.CountdownFinished)
    }

    /** Plays [ms] of game time with no input (the orb sits still), or until the run ends. */
    private fun idle(ms: Long) {
        var t = 0L
        while (t < ms && vm.state.value.phase == GamePhase.PLAYING) {
            vm.handleIntent(GameIntent.FrameTick(16L))
            t += 16L
        }
    }

    /** Restarting must not pull the orb back to where the last run's pointer was. */
    @Test
    fun restart_leavesTheOrbAtItsSpawn_untilThePlayerMoves() {
        startLevel(1)
        vm.handleIntent(GameIntent.PlayerAimAt(com.snakechase.app.domain.model.Vec2(60f, 60f)))
        idle(300)
        vm.handleIntent(GameIntent.RestartLevel)
        vm.handleIntent(GameIntent.CountdownFinished)
        val spawn = vm.state.value.player.position
        idle(300)
        val drift = vm.state.value.player.position.distanceTo(spawn)
        assertTrue(drift < 1f, "orb drifted $drift toward the old aim after a restart")
    }

    /**
     * The judge flow used while testing on a phone: caught on Level 1, god mode on, "Win level",
     * then Levels 2-5 played hands-off for 50 s each. God mode keeps the snake on the orb, so
     * Combo and Streak race far past the Heartbeat/Superstar goals and the time passes Pixel's,
     * yet none of it may earn an orb skin.
     */
    @Test
    fun godModeRuns_neverEarnOrbSkins() {
        startLevel(1)
        idle(10_000)
        assertEquals(GamePhase.GAME_OVER, vm.state.value.phase, "a parked orb is caught on Level 1")
        vm.handleIntent(GameIntent.ToggleGodMode)
        vm.handleIntent(GameIntent.ForceWinLevel)
        for (level in 2..5) {
            startLevel(level)
            idle(50_000)
            assertTrue(vm.state.value.godModeEnabled)
            assertTrue(vm.state.value.nearMissStreakCount > 40 || vm.state.value.score.combo.count > 25 || vm.state.value.hud.survivalTimeMs > 45_000, "the run met some goal's numbers")
            vm.handleIntent(GameIntent.ForceWinLevel)
        }
        assertEquals(setOf("SPARK"), profiles.profile.value.unlockedOrbs)
        assertTrue(vm.state.value.newOrbSkins.isEmpty())
    }
}
