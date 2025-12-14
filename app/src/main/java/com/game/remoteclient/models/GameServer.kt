package com.game.remoteclient.models

data class GameServer(
    val ipAddress: String,
    val port: Int = 8080,
    val name: String? = null,
    val playerCount: Int = 0
) {
    val fullAddress: String
        get() = "$ipAddress:$port"
}
