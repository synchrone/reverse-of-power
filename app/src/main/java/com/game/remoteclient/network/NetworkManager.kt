package com.game.remoteclient.network

import android.util.Log
import com.game.remoteclient.models.GameServer
import com.game.remoteclient.models.GameState
import com.game.remoteclient.models.Player
import com.game.remoteclient.models.QuizQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class NetworkManager private constructor() {

    private val TAG = "NetworkManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var currentServer: GameServer? = null

    private val _gameState = MutableStateFlow(GameState.DISCONNECTED)
    val gameState: StateFlow<GameState> = _gameState

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players

    private val _currentQuestion = MutableStateFlow<QuizQuestion?>(null)
    val currentQuestion: StateFlow<QuizQuestion?> = _currentQuestion

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    companion object {
        @Volatile
        private var instance: NetworkManager? = null

        fun getInstance(): NetworkManager {
            return instance ?: synchronized(this) {
                instance ?: NetworkManager().also { instance = it }
            }
        }
    }

    suspend fun scanForServers(subnet: String = "192.168.1"): List<GameServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<GameServer>()
        val port = 8080

        servers
    }

    suspend fun connectToServer(server: GameServer, player: Player): Boolean = withContext(Dispatchers.IO) {
        try {
            _gameState.value = GameState.CONNECTING
            currentServer = server

            val request = Request.Builder()
                .url("ws://${server.fullAddress}/game")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")
                    _gameState.value = GameState.LOBBY
                    // Send player info to server
                    sendPlayerInfo(player)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received message: $text")
                    handleMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error", t)
                    _connectionError.value = t.message ?: "Connection failed"
                    _gameState.value = GameState.DISCONNECTED
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $reason")
                    _gameState.value = GameState.DISCONNECTED
                }
            })

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            _connectionError.value = e.message ?: "Failed to connect"
            _gameState.value = GameState.DISCONNECTED
            false
        }
    }

    private fun sendPlayerInfo(player: Player) {
        // TODO: Implement JSON serialization and send player info
        val message = """{"type":"join","name":"${player.name}"}"""
        webSocket?.send(message)
    }

    fun sendAnswer(answerIndex: Int) {
        val message = """{"type":"answer","answer":$answerIndex}"""
        webSocket?.send(message)
    }

    fun startGame() {
        val message = """{"type":"start_game"}"""
        webSocket?.send(message)
    }

    private fun handleMessage(message: String) {
        // TODO: Parse JSON messages and update state
        // This is a placeholder for message handling
        Log.d(TAG, "Handling message: $message")
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        currentServer = null
        _gameState.value = GameState.DISCONNECTED
    }
}
