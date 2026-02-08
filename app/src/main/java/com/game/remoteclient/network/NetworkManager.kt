package com.game.remoteclient.network

import android.util.Log
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.ClientQuizCommandMessage
import com.game.protocol.GameMessage
import com.game.protocol.GameProtocolClient
import com.game.protocol.InterfaceVersionMessage
import com.game.protocol.ServerAvatarRequestResponseMessage
import com.game.protocol.ServerAvatarStatusMessage
import com.game.protocol.ServerCategorySelectChoices
import com.game.protocol.ServerColourMessage
import com.game.protocol.ServerRequestCategorySelectChoice
import com.game.remoteclient.models.GameServer
import com.game.remoteclient.models.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class NetworkManager private constructor() {

    private val TAG = "NetworkManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private var protocolClient: GameProtocolClient? = null
    private var currentServer: GameServer? = null
    private var deviceUID: String = UUID.randomUUID().toString().replace("-", "") // e.g: "b2f3f8eb0cf4ef4b2359871d35495225"
    private var selectedAvatarId: String? = null

    private val _gameState = MutableStateFlow(GameState.DISCONNECTED)
    val gameState: StateFlow<GameState> = _gameState

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    // Delegate to protocolClient for avatar state
    val availableAvatars: List<ServerAvatarStatusMessage>
        get() = protocolClient?.availableAvatars ?: emptyList()

    var onAvatarsChanged: (() -> Unit)? = null
    var onAvatarRequestResponse: ((ServerAvatarRequestResponseMessage) -> Unit)? = null
    var onQuizCommand: ((ClientQuizCommandMessage) -> Unit)? = null
    var onHoldingScreenMessage: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    var onColourMessage: ((ServerColourMessage) -> Unit)? = null
    var onCategoryChoicesMessage: ((ServerCategorySelectChoices) -> Unit)? = null
    var onCategorySelectRequest: (() -> Unit)? = null

    // Avatar selection state
    var isAvatarConfirmed: Boolean = false
        private set
    var pendingAvatarRequest: String? = null
        private set

    // Pending category choices for when message arrives before fragment is ready
    var pendingCategoryChoices: ServerCategorySelectChoices? = null

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
        // TODO: scan using UPnP
        servers
    }

    suspend fun connectToServer(server: GameServer): Boolean = withContext(Dispatchers.IO) {
        try {
            _gameState.value = GameState.CONNECTING
            currentServer = server

            // Create protocol client
            protocolClient = GameProtocolClient(deviceUID, server.ipAddress, server.port)

            // Set up message handlers
            setupMessageHandlers()

            // Connect to server and wait for response
            val connected = protocolClient?.connect() ?: false

            if (connected) {
                Log.d(TAG, "Connected to ${server.ipAddress}:${server.port} with UID: $deviceUID")
            } else {
                // Check if there's a specific error from the protocol client
                val errorMessage = protocolClient?.connectionError ?: "Connection timed out"
                Log.e(TAG, "Connection failed to ${server.ipAddress}:${server.port}: $errorMessage")
                _connectionError.value = errorMessage
                _gameState.value = GameState.DISCONNECTED
                protocolClient?.close()
                protocolClient = null
            }
            connected
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            _connectionError.value = e.message ?: "Failed to connect"
            _gameState.value = GameState.DISCONNECTED
            false
        }
    }

    private fun setupMessageHandlers() {
        protocolClient?.onMessageReceived = { message ->
            handleUIEvents(message)
        }
    }

    // UI event handling only - protocol responses are handled by GameProtocolClient
    private fun handleUIEvents(message: GameMessage) {
        when (message) {
            is InterfaceVersionMessage -> {
                _gameState.value = GameState.CONNECTED
            }

            is ServerAvatarStatusMessage -> {
                onAvatarsChanged?.invoke()
            }

            is ServerAvatarRequestResponseMessage -> {
                if (message.AvatarID == pendingAvatarRequest && message.Available) {
                    isAvatarConfirmed = true
                    pendingAvatarRequest = null
                }
                onAvatarRequestResponse?.invoke(message)
            }

            is ClientQuizCommandMessage -> {
                onQuizCommand?.invoke(message)
            }

            is ClientHoldingScreenCommandMessage -> {
                onHoldingScreenMessage?.invoke(message)
            }

            is ServerColourMessage -> {
                onColourMessage?.invoke(message)
            }

            is ServerCategorySelectChoices -> {
                pendingCategoryChoices = message
                onCategoryChoicesMessage?.invoke(message)
            }

            is ServerRequestCategorySelectChoice -> {
                onCategorySelectRequest?.invoke()
            }

            else -> { }
        }
    }

    fun sendPlayerProfile(playerName: String, culture: String = "en-US") {
        selectedAvatarId?.let { avatarId ->
            Log.d(TAG, "Sending player profile: $playerName with avatar $avatarId")
            scope.launch {
                protocolClient?.sendPlayerProfile(
                    name = playerName,
                    avatarId = avatarId,
                    culture = culture
                )
            }
        }
    }

    fun sendStartGameButtonPressed() {
        Log.d(TAG, "Start game button pressed")
        scope.launch {
            protocolClient?.sendStartGameButtonPressed()
        }
    }

    fun sendCategorySelection(doorIndex: Int) {
        Log.d(TAG, "Category selected: door $doorIndex")
        scope.launch {
            protocolClient?.sendCategorySelection(doorIndex)
        }
    }

    fun requestAvatar(avatarId: String) {
        selectedAvatarId = avatarId
        pendingAvatarRequest = avatarId
        isAvatarConfirmed = false
        scope.launch {
            protocolClient?.requestAvatar(avatarId)
        }
    }

    fun sendImage(imageData: ByteArray, imageGuid: String, transferId: Int) {
        scope.launch {
            protocolClient?.sendImage(imageData, imageGuid, transferId)
        }
    }

    fun disconnect() {
        protocolClient?.close()
        protocolClient = null
        currentServer = null
        selectedAvatarId = null
        _gameState.value = GameState.DISCONNECTED
        Log.d(TAG, "Disconnected from server")
    }
}
