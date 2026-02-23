package com.game.remoteclient.models

data class GameServer(
    val ipAddress: String,
    val port: Int = 9066, // fixed server port
    val name: String? = null,
    val consoleType: String? = null
) {
    val fullAddress: String
        get() = "$ipAddress:$port"
}
