package com.game.remoteclient.models

enum class GameState {
    DISCONNECTED,
    CONNECTING,

    CONNECTED,

    LOBBY,
    GET_READY,
    PLAYING,
    WAITING,
    GAME_OVER
}
