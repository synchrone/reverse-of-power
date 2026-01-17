package com.game.remoteclient.network

import android.util.Log
import com.game.protocol.AssignPlayerIDAndSlotMessage
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.GameMessage
import com.game.protocol.GameProtocolClient
import com.game.protocol.InterfaceVersionMessage
import com.game.protocol.ResourceRequirementsMessage
import com.game.protocol.ServerAvatarRequestResponseMessage
import com.game.protocol.ServerAvatarStatusMessage
import com.game.protocol.ServerCategorySelectChoices
import com.game.protocol.ServerColourMessage
import com.game.protocol.ServerRequestCategorySelectChoice
import com.game.protocol.SessionStateMessage
import com.game.remoteclient.models.GameServer
import com.game.remoteclient.models.GameState
import com.game.remoteclient.models.Player
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
    private var currentPlayer: Player? = null
    private var assignedPlayerId: Int? = null
    private var assignedSlotId: Int? = null
    private var deviceUID: String = UUID.randomUUID().toString().replace("-", "")
    private var selectedAvatarId: String? = null

    private val _gameState = MutableStateFlow(GameState.DISCONNECTED)
    val gameState: StateFlow<GameState> = _gameState

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    val availableAvatars = mutableListOf<ServerAvatarStatusMessage>()
    var onAvatarsChanged: (() -> Unit)? = null
    var onHoldingScreenMessage: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    var onColourMessage: ((ServerColourMessage) -> Unit)? = null
    var onCategoryChoicesMessage: ((ServerCategorySelectChoices) -> Unit)? = null
    var onCategorySelectRequest: (() -> Unit)? = null

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
            handleProtocolMessage(message)
        }

        protocolClient?.onAvatarListReceived = { avatars ->
            availableAvatars.clear()
            availableAvatars.addAll(avatars)
            Log.d(TAG, "Received ${avatars.size} avatars")
            onAvatarsChanged?.invoke()
        }
    }

    private fun handleProtocolMessage(message: GameMessage) {
        when (message) {
            is InterfaceVersionMessage -> {
                Log.d(TAG, "Server version: ${message.InterfaceVersion}")
                _gameState.value = GameState.CONNECTED
                protocolClient?.requestPlayerID()
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
                    android.os.Build.MODEL,
                    "Android OS ${android.os.Build.VERSION.RELEASE} / API-${android.os.Build.VERSION.SDK_INT}"
                )
                protocolClient?.requestAvatarStatus()
            }

            is ResourceRequirementsMessage -> {
                Log.d(TAG, "Resources required: ${message.Requirements}")
                if(message.Requirements.isEmpty()){
                    protocolClient?.sendAllResourcesReceived()
                }
            }

            is ServerAvatarStatusMessage -> {
                Log.d(TAG, "Avatar ${message.AvatarID}: Available=${message.Available}")
                updateAvatarStatus(message)
            }

            is ServerAvatarRequestResponseMessage -> {
                Log.d(TAG, "Avatar ${message.AvatarID} request response - Available: ${message.Available}")
            }

            is ClientHoldingScreenCommandMessage -> {
                Log.d(TAG, "Holding screen command: action=${message.action}, text=${message.HoldingScreenText}")
                onHoldingScreenMessage?.invoke(message)
            }

            is ServerColourMessage -> {
                Log.d(TAG, "Colour message: bg=${message.BackgroundTint}, primary=${message.PrimaryTint}")
                onColourMessage?.invoke(message)
            }

            is ServerCategorySelectChoices -> {
                Log.d(TAG, "Category choices: ${message.CategoryChoices.size} options")
                pendingCategoryChoices = message
                onCategoryChoicesMessage?.invoke(message)
            }

            is ServerRequestCategorySelectChoice -> {
                Log.d(TAG, "Category select request received")
                onCategorySelectRequest?.invoke()
            }

            else -> {
                Log.w(TAG, "Unhandled message type: ${message.TypeString}")
            }
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

    private fun updateAvatarStatus(avatar: ServerAvatarStatusMessage) {
        val existingIndex = availableAvatars.indexOfFirst { it.AvatarID == avatar.AvatarID }

        if (existingIndex >= 0) {
            availableAvatars[existingIndex] = avatar
        } else {
            availableAvatars.add(avatar)
        }
        onAvatarsChanged?.invoke()
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
        // Mark previously selected avatar as available again
        selectedAvatarId?.let { previousId ->
            if (previousId != avatarId) {
                val prevIndex = availableAvatars.indexOfFirst { it.AvatarID == previousId }
                if (prevIndex >= 0) {
                    availableAvatars[prevIndex] = availableAvatars[prevIndex].copy(Available = true)
                }
            }
        }

        selectedAvatarId = avatarId
        onAvatarsChanged?.invoke()

        scope.launch {
            protocolClient?.requestAvatar(avatarId)
        }
    }

    fun sendImage(imageData: ByteArray, imageGuid: String) {
        val transferId = (Math.random() * 10000).toInt()
        scope.launch {
            protocolClient?.sendImage(imageData, imageGuid, transferId)
        }
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
        availableAvatars.clear()
        Log.d(TAG, "Disconnected from server")
    }
}
