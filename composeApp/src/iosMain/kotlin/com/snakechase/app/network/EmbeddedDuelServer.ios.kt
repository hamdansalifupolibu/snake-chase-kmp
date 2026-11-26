package com.snakechase.app.network

/** iOS joins duels as a guest; hosting would need a Network.framework listener. */
actual class EmbeddedDuelServer actual constructor() {
    actual val canHost: Boolean = false
    actual fun start(port: Int, listener: DuelServerListener): Boolean = false
    actual fun send(clientId: Int, message: String) {}
    actual fun broadcast(message: String) {}
    actual fun disconnect(clientId: Int) {}
    actual fun stop() {}
    actual fun getLocalIpAddress(): String = "127.0.0.1"
}
