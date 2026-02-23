package com.game.remoteclient.models

data class PlayStationConsole(
    val hostName: String,
    val hostId: String,
    val hostType: String,
    val ipAddress: String,
    val statusCode: Int,
    val systemVersion: String? = null,
    val requestPort: Int? = null
) {
    val isAwake: Boolean get() = statusCode == 200
    val statusText: String get() = if (isAwake) "Awake" else "Standby"
}
