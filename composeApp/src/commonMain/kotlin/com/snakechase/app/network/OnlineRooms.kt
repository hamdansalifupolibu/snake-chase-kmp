package com.snakechase.app.network

import com.snakechase.app.network.firebase.RealtimeDb

/** Factory for internet duel rooms (Firebase Realtime Database), shared across the app via Koin. */
class OnlineRooms(
    private val db: RealtimeDb,
    private val uid: suspend () -> String,
) {
    fun host(playerName: String): HostTransport = FirebaseHostTransport(db, uid, playerName)
    fun guest(): GuestTransport = FirebaseGuestTransport(db, uid)
    fun spectator(): GuestTransport = FirebaseSpectatorTransport(db, uid)
    fun watchers(code: String): kotlinx.coroutines.flow.Flow<Int> = watcherCount(db, code)
    fun quickMatch(uiScope: kotlinx.coroutines.CoroutineScope): QuickMatch = QuickMatch(db, uid, uiScope)
}
