package com.game.remoteclient.network

import android.util.Log
import com.game.protocol.AssignPlayerIDAndSlotMessage
import com.game.protocol.ClientQuizCommandMessage
import com.game.protocol.GameMessage
import com.game.protocol.GameProtocolClient
import com.game.protocol.InterfaceVersionMessage
import com.game.protocol.ResourceRequirementsMessage
import com.game.protocol.ServerAvatarRequestResponseMessage
import com.game.protocol.ServerAvatarStatusMessage
import com.game.protocol.SessionStateMessage
import com.game.remoteclient.models.GameServer
import com.game.remoteclient.models.GameState
import com.game.remoteclient.models.Player
import com.game.remoteclient.models.QuizQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class NetworkManager private constructor() {

    private val TAG = "NetworkManager"

    private var protocolClient: GameProtocolClient? = null
    private var currentServer: GameServer? = null
    private var currentPlayer: Player? = null
    private var assignedPlayerId: Int? = null
    private var assignedSlotId: Int? = null
    private var deviceUID: String = UUID.randomUUID().toString().replace("-", "")
    private var selectedAvatarId: String? = null

    private val _gameState = MutableStateFlow(GameState.DISCONNECTED)
    val gameState: StateFlow<GameState> = _gameState

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players

    private val _currentQuestion = MutableStateFlow<QuizQuestion?>(null)
    val currentQuestion: StateFlow<QuizQuestion?> = _currentQuestion

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    private val _availableAvatars = MutableStateFlow<List<ServerAvatarStatusMessage>>(emptyList())
    val availableAvatars: StateFlow<List<ServerAvatarStatusMessage>> = _availableAvatars

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
            currentPlayer = player

            // Create protocol client
            protocolClient = GameProtocolClient(server.ipAddress, server.port)

            // Set up message handlers
            setupMessageHandlers()

            // Connect to server
            protocolClient?.connect(deviceUID)

            Log.d(TAG, "Connected to ${server.ipAddress}:${server.port} with UID: $deviceUID")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            _connectionError.value = e.message ?: "Failed to connect"
            _gameState.value = GameState.DISCONNECTED
            false
        }
    }

    private fun setupMessageHandlers() {
        protocolClient?.onMessageReceived = { message ->
            handleProtocolMessage(message)
        }

        protocolClient?.onAvatarListReceived = { avatars ->
            _availableAvatars.value = avatars
            Log.d(TAG, "Received ${avatars.size} avatars")

            // Auto-select first available avatar
            val available = avatars.firstOrNull { it.Available }
            available?.let {
                Log.d(TAG, "Auto-requesting avatar: ${it.AvatarID}")
                protocolClient?.requestAvatar(it.AvatarID)
                selectedAvatarId = it.AvatarID
            }
        }
    }

    private fun handleProtocolMessage(message: GameMessage) {
        Log.d(TAG, "Received message: ${message.TypeString}")

        when (message) {
            is InterfaceVersionMessage -> {
                Log.d(TAG, "Server version: ${message.InterfaceVersion}")
                _gameState.value = GameState.CONNECTED
                protocolClient?.requestPlayerID(deviceUID)
            }

            is SessionStateMessage -> {
                Log.d(TAG, "Session ID: ${message.SessionID}")
            }

            is AssignPlayerIDAndSlotMessage -> {
                assignedPlayerId = message.PlayerID
                assignedSlotId = message.SlotID
                Log.d(TAG, "Assigned Player ID: ${message.PlayerID}, Slot: ${message.SlotID}")
                Log.d(TAG, "Display Name: ${message.DisplayName}")

                // Send required responses
                protocolClient?.sendAllResourcesReceived()
                protocolClient?.sendDeviceInfo(
                    deviceUID,
                    android.os.Build.MODEL,
                    "Android OS ${android.os.Build.VERSION.RELEASE} / API-${android.os.Build.VERSION.SDK_INT}"
                )
                protocolClient?.requestAvatarStatus()
            }

            is ResourceRequirementsMessage -> {
                Log.d(TAG, "Resources required: ${message.Requirements}")
            }

            is ServerAvatarStatusMessage -> {
                Log.d(TAG, "Avatar ${message.AvatarID}: ${message.Available}")
            }

            is ServerAvatarRequestResponseMessage -> {
                Log.d(TAG, "Avatar ${message.AvatarID} request response - Available: ${message.Available}")
                if (message.Available && message.AvatarID == selectedAvatarId) {
                    // Avatar acquired, send player profile
                    currentPlayer?.let { player ->
                        sendPlayerProfile(player)
                        _gameState.value = GameState.LOBBY
                    }
                }
            }

            else -> {
                Log.d(TAG, "Unhandled message type: ${message.TypeString}")
            }
        }
    }

    private fun sendPlayerProfile(player: Player) {
        selectedAvatarId?.let { avatarId ->
            protocolClient?.sendPlayerProfile(
                name = player.name,
                avatarId = avatarId,
                culture = "en-US"
            )
            Log.d(TAG, "Sent player profile: ${player.name}")
        }
    }

    fun sendAnswer(answerIndex: Int) {
        protocolClient?.let { client ->
            val msg = ClientQuizCommandMessage(
                action = answerIndex,
                time = System.currentTimeMillis() / 1000.0
            )
            // Note: ClientQuizCommandMessage would need to be sent via the client
            // This requires adding a generic sendMessage method to GameProtocolClient
            Log.d(TAG, "Sending answer: $answerIndex")
        }
    }

    fun startGame() {
        Log.d(TAG, "Start game requested")
        // Implementation depends on protocol requirements
    }

    fun requestAvatar(avatarId: String) {
        protocolClient?.requestAvatar(avatarId)
        selectedAvatarId = avatarId
    }

    fun sendImage(imageData: ByteArray, imageGuid: String) {
        val transferId = (Math.random() * 10000).toInt()
        protocolClient?.sendImage(imageData, imageGuid, transferId)
    }

    fun disconnect() {
        protocolClient?.close()
        protocolClient = null
        currentServer = null
        currentPlayer = null
        assignedPlayerId = null
        assignedSlotId = null
        selectedAvatarId = null
        _gameState.value = GameState.DISCONNECTED
        _availableAvatars.value = emptyList()
        Log.d(TAG, "Disconnected from server")
    }
}
