package com.snakechase.app.online

import com.russhwolf.settings.Settings
import com.snakechase.app.network.ConnectivityMonitor
import com.snakechase.app.network.awaitOnline
import com.snakechase.app.profile.PlayerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Something the player earned that still has to reach Firebase. Stored on the device until it does. */
@Serializable
sealed interface PendingUpload

@Serializable
@SerialName("score")
data class PendingScore(
    val name: String,
    val levelId: Int,
    val score: Long,
    val survivalMs: Long,
    val difficulty: String = "MODERATE",
) : PendingUpload

@Serializable
@SerialName("challenge")
data class PendingChallenge(val tier: Int, val name: String, val run: PendingRun) : PendingUpload

@Serializable
@SerialName("finish")
data class PendingFinish(
    val matchId: String,
    val opponentUid: String,
    val opponentName: String,
    val iWon: Boolean,
    val run: PendingRun,
) : PendingUpload

@Serializable
data class PendingRun(val fleeMs: Long, val huntMs: Long, val ghostFlee: String, val ghostHunt: String) {
    fun toRun() = OnlineRun(fleeMs, huntMs, GhostTrack.decode(ghostFlee), GhostTrack.decode(ghostHunt))

    companion object {
        fun of(run: OnlineRun) = PendingRun(run.fleeMs, run.huntMs, run.ghostFlee.encode(), run.ghostHunt.encode())
    }
}

/**
 * Store-and-forward for online results: scores, ghost runs and the profile are saved on the
 * device first and sent whenever the internet is there. All of them are append-only (or, for
 * the profile, last-write-wins), so sending late can never conflict with newer data.
 */
class UploadQueue(
    private val storage: Settings,
    private val repo: OnlineDuelRepository,
    private val monitor: ConnectivityMonitor,
    private val profile: () -> PlayerProfile,
) {
    private val lock = Mutex()
    private val items = load().toMutableList()
    private var profileDirty = storage.getBoolean(KEY_PROFILE_DIRTY, false)

    private val _pending = MutableStateFlow(items.size)

    /** Results still waiting to upload (shown in the offline banner). */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun enqueue(item: PendingUpload) {
        items += item
        save()
        wake.trySend(Unit)
    }

    fun markProfileDirty() {
        profileDirty = true
        storage.putBoolean(KEY_PROFILE_DIRTY, true)
        wake.trySend(Unit)
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            var failures = 0
            while (isActive) {
                if (items.isEmpty() && !profileDirty) wake.receive()
                monitor.awaitOnline()
                val ok = runCatching { lock.withLock { sendNext() } }.isSuccess
                if (ok) {
                    failures = 0
                } else {
                    failures++
                    monitor.recheck()
                    if (failures >= MAX_ATTEMPTS && items.isEmpty()) {
                        // Only the profile is left: never drop it, just retry slowly.
                        delay(60_000)
                        failures = 0
                    } else if (failures >= MAX_ATTEMPTS) {
                        // Online yet still rejected (e.g. an expired challenge): drop it rather than
                        // retrying forever and blocking everything queued behind it.
                        lock.withLock { dropHead() }
                        failures = 0
                    } else {
                        delay(2_000L * failures)
                    }
                }
            }
        }
    }

    private suspend fun sendNext() {
        val head = items.firstOrNull()
        if (head == null) {
            if (profileDirty) {
                repo.saveProfile(profile())
                profileDirty = false
                storage.putBoolean(KEY_PROFILE_DIRTY, false)
            }
            return
        }
        when (head) {
            is PendingScore -> repo.submitScore(head.name, head.levelId, head.score, head.survivalMs, head.difficulty)
            is PendingChallenge -> repo.postChallenge(head.tier, head.name, head.run.toRun())
            is PendingFinish -> repo.finishMatch(
                OnlineOpponent(head.matchId, head.opponentName, head.opponentUid, OnlineRun.HOUSE),
                head.run.toRun(),
                head.iWon,
            )
        }
        items.removeAt(0)
        save()
    }

    private fun dropHead() {
        if (items.isNotEmpty()) {
            items.removeAt(0)
            save()
        } else {
            profileDirty = false
            storage.putBoolean(KEY_PROFILE_DIRTY, false)
        }
    }

    private fun load(): List<PendingUpload> = storage.getStringOrNull(KEY)?.let { raw ->
        runCatching { json.decodeFromString(ListSerializer(PendingUpload.serializer()), raw) }.getOrNull()
    }.orEmpty()

    private fun save() {
        storage.putString(KEY, json.encodeToString(ListSerializer(PendingUpload.serializer()), items))
        _pending.value = items.size
    }

    companion object {
        private const val KEY = "sc_upload_queue_v1"
        private const val KEY_PROFILE_DIRTY = "sc_profile_dirty"
        private const val MAX_ATTEMPTS = 5
        private val json = Json { ignoreUnknownKeys = true }
    }
}
