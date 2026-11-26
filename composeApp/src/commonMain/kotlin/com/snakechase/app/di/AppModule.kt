package com.snakechase.app.di

import com.snakechase.app.domain.repository.SettingsRepository
import com.snakechase.app.domain.repository.SettingsRepositoryImpl
import com.snakechase.app.hunt.HuntProgress
import com.snakechase.app.network.ConnectivityMonitor
import com.snakechase.app.network.OnlineRooms
import com.snakechase.app.network.firebase.FirebaseAuthClient
import com.snakechase.app.network.firebase.FirestoreClient
import com.snakechase.app.network.firebase.FirestoreDocs
import com.snakechase.app.network.firebase.RealtimeDb
import com.snakechase.app.network.firebase.RealtimeDbClient
import com.snakechase.app.online.OnlineDuelRepository
import com.snakechase.app.online.UploadQueue
import com.snakechase.app.presentation.GameViewModel
import com.snakechase.app.profile.ProfileRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    viewModelOf(::GameViewModel)

    // ─── Online (Firebase over REST — same code on every platform) ───
    single {
        HttpClient {
            // Fail fast on bad networks instead of leaving the player on a spinner.
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 12_000
                socketTimeoutMillis = 12_000
            }
        }
    }
    single { FirebaseAuthClient(http = get(), settings = get()) }
    single<FirestoreDocs> { FirestoreClient(http = get(), auth = get()) }
    single<RealtimeDb> { RealtimeDbClient(http = get(), auth = get()) }
    single {
        val auth = get<FirebaseAuthClient>()
        OnlineDuelRepository(db = get(), currentUid = { auth.requireUid() })
    }
    single { ProfileRepository(get()) }
    // Revive hearts: when they run out, wait for the timed refill or buy more with coins.
    single { com.snakechase.app.profile.Lives(get(), autoRefill = false, now = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() }) }
    single { HuntProgress(get(), openAll = com.snakechase.app.config.Edition.JUDGE_EDITION) }
    single { ConnectivityMonitor(probe = ConnectivityMonitor.firebaseProbe(get())) }
    single {
        val profiles = get<ProfileRepository>()
        UploadQueue(storage = get(), repo = get(), monitor = get(), profile = { profiles.profile.value }).also { queue ->
            // Any profile change (name, avatar, duel stats) is mirrored online when possible.
            profiles.onChanged = { queue.markProfileDirty() }
        }
    }
    single {
        val auth = get<FirebaseAuthClient>()
        OnlineRooms(db = get(), uid = { auth.requireUid() })
    }
}

expect val platformModule: Module
