package com.game.remoteclient.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket

data class GameServer(
    val ipAddress: String,
    val port: Int = 8080,
    val name: String? = null,
    val playerCount: Int = 0
) {
    val fullAddress: String
        get() = "$ipAddress:$port"

    suspend fun handshake(): Boolean = withContext(Dispatchers.IO) {
        false
    }
}
