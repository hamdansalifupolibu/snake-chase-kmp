package com.snakechase.app

import com.russhwolf.settings.MapSettings
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.network.ConnectivityMonitor
import com.snakechase.app.online.GhostTrack
import com.snakechase.app.online.OnlineDuelRepository
import com.snakechase.app.online.PendingScore
import com.snakechase.app.online.UploadQueue
import com.snakechase.app.profile.Economy
import com.snakechase.app.profile.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {

    @Test
    fun newPlayer_startsWithStarterCoins_andNoName() {
        val p = ProfileRepository(MapSettings()).profile.value
        assertFalse(p.isSetUp)
        assertEquals(Economy.STARTER_COINS, p.coins)
    }

    @Test
    fun legacyName_isMigrated_andIdentityPersists() {
        val storage = MapSettings()
        storage.putString("sc_player_name", "Hamdan")
        val repo = ProfileRepository(storage)
        assertEquals("Hamdan", repo.profile.value.name)

        repo.setIdentity("  Neon   Kid!! ", 3)
        val reloaded = ProfileRepository(storage).profile.value
        assertEquals("Neon Kid", reloaded.name)
        assertEquals(3, reloaded.avatar)
    }

    @Test
    fun spend_neverGoesNegative() {
        val repo = ProfileRepository(MapSettings())
        assertTrue(repo.spend(500))
        assertEquals(0, repo.profile.value.coins)
        assertFalse(repo.spend(1))
        assertEquals(0, repo.profile.value.coins)
    }

    @Test
    fun dailyStreak_growsOnConsecutiveDays_andResetsAfterAGap() {
        val repo = ProfileRepository(MapSettings())
        assertEquals(1, repo.claimDaily(100)!!.streakDay)
        assertNull(repo.claimDaily(100), "same day pays once")
        assertEquals(2, repo.claimDaily(101)!!.streakDay)
        val day3 = repo.claimDaily(102)!!
        assertEquals(3, day3.streakDay)
        assertEquals(Economy.dailyReward(3), day3.coins)
        assertEquals(1, repo.claimDaily(105)!!.streakDay, "missed days restart the streak")
        assertEquals(Economy.STARTER_COINS + 100 + 150 + 200 + 100, repo.profile.value.coins)
    }

    @Test
    fun duelStreaks_trackCurrentAndBest() {
        val repo = ProfileRepository(MapSettings())
        repeat(3) { repo.recordDuel(true) }
        repo.recordDuel(false)
        repo.recordDuel(true)
        val p = repo.profile.value
        assertEquals(4, p.duelsWon)
        assertEquals(1, p.duelsLost)
        assertEquals(1, p.winStreak)
        assertEquals(3, p.bestWinStreak)
        assertEquals(80, p.winRatePct)
    }

    @Test
    fun economy_rules() {
        assertEquals(50 + 75, Economy.runReward(100, cleared = true, stars = 3))
        assertEquals(15, Economy.runReward(30, cleared = false, stars = 0))
        assertEquals(600, Economy.dailyReward(30), "streak reward caps at day 7")
        assertEquals(900, Economy.skinPrice(SnakeSkin.ABYSS_SERPENT))
        assertNull(Economy.skinPrice(SnakeSkin.FEARLESS), "achievement skins can't be bought")
    }

    @Test
    fun outlastingTheGhost_happensOnlyAfterItsRunEnded() {
        val threeSeconds = GhostTrack(List(31) { Vec2(1f, it.toFloat()) }) // 31 samples x 100 ms = 3.0 s
        assertFalse(threeSeconds.isOutlastedAt(2_000L))
        assertFalse(threeSeconds.isOutlastedAt(3_000L), "level with it isn't outlasting it")
        assertTrue(threeSeconds.isOutlastedAt(3_016L))
        assertFalse(GhostTrack.EMPTY.isOutlastedAt(60_000L), "no ghost, nothing to outlast")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun uploadQueue_holdsResultsOffline_andSendsThemWhenBackOnline() = runTest {
        val storage = MapSettings()
        val db = FakeFirestore(Clock.System)
        val repo = OnlineDuelRepository(db, currentUid = { "me" })
        var internet = false
        val monitor = ConnectivityMonitor(probe = { internet }, onlineIntervalMs = 1_000, offlineIntervalMs = 1_000)
        val queue = UploadQueue(storage, repo, monitor, profile = { ProfileRepository(storage).profile.value })

        monitor.start(backgroundScope)
        queue.start(backgroundScope)
        queue.enqueue(PendingScore("Me", 1, 90, 30_000))
        runCurrent()
        assertEquals(1, queue.pending.value)
        assertTrue(db.docs["scores"].isNullOrEmpty(), "nothing is sent while offline")

        // Survives an app restart: a new queue on the same storage still has it.
        assertEquals(1, UploadQueue(storage, repo, monitor, profile = { error("unused") }).pending.value)

        internet = true
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(0, queue.pending.value)
        assertEquals(1, db.docs["scores"]?.size)
        coroutineContext.cancelChildren()
    }
}
