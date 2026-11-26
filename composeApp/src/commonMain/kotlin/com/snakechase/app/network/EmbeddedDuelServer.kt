package com.snakechase.app.network

/** Callbacks from the embedded server. Invoked on background I/O threads. */
interface DuelServerListener {
    fun onClientConnected(clientId: Int) {}
    fun onClientMessage(clientId: Int, text: String)
    fun onClientDisconnected(clientId: Int) {}
}

/**
 * Minimal RFC 6455 WebSocket server embedded in the app so a phone or laptop can
 * host a duel with no cloud backend. Implemented once for Android + desktop in
 * `jvmAndAndroidMain`; iOS acts as guest only for now.
 */
expect class EmbeddedDuelServer() {
    /** True if the platform can host (false on iOS). */
    val canHost: Boolean
    fun start(port: Int, listener: DuelServerListener): Boolean
    fun send(clientId: Int, message: String)
    fun broadcast(message: String)
    fun disconnect(clientId: Int)
    fun stop()
    fun getLocalIpAddress(): String
}
