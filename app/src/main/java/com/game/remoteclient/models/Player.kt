package com.game.remoteclient.models

import android.graphics.Bitmap

data class Player(
    val id: String = "",
    val name: String,
    val avatarUrl: String? = null,
    val avatarBitmap: Bitmap? = null,
    val isReady: Boolean = false,
    val isHost: Boolean = false
)
