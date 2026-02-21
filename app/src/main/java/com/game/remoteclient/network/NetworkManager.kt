package com.game.remoteclient.network

import android.util.Log
import com.game.protocol.ClientCategorySelectChoice
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.ClientPlayerProfileMessage
import com.game.protocol.ClientTriviaAnswer
import com.game.protocol.ContinuePressedResponseMessage
import com.game.protocol.ClientQuizCommandMessage
import com.game.protocol.ClientRequestAvatarMessage
import com.game.protocol.GameMessage
import com.game.protocol.GameProtocolClient
import com.game.protocol.InterfaceVersionMessage
import com.game.protocol.ServerAvatarRequestResponseMessage
import com.game.protocol.ServerAvatarStatusMessage
import com.game.protocol.ServerBeginTriviaAnsweringPhase
import com.game.protocol.ServerCategorySelectChoices
import com.game.protocol.ServerColourMessage
import com.game.protocol.ServerRequestCategorySelectChoice
import com.game.protocol.StartGameButtonPressedResponseMessage
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

    val availableAvatars = mutableListOf<ServerAvatarStatusMessage>()

    var onAvatarsChanged: (() -> Unit)? = null
    var onAvatarRequestResponse: ((ServerAvatarRequestResponseMessage) -> Unit)? = null
    var onQuizCommand: ((ClientQuizCommandMessage) -> Unit)? = null
    var onHoldingScreenMessage: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    var onColourMessage: ((ServerColourMessage) -> Unit)? = null
    var onCategoryChoicesMessage: ((ServerCategorySelectChoices) -> Unit)? = null
    var onCategorySelectRequest: (() -> Unit)? = null
    var onTriviaMessage: ((ServerBeginTriviaAnsweringPhase) -> Unit)? = null

    // Avatar selection state
    var isAvatarConfirmed: Boolean = false
        private set
    var pendingAvatarRequest: String? = null
        private set

    // Pending category choices for when message arrives before fragment is ready
    var pendingCategoryChoices: ServerCategorySelectChoices? = null
    // Pending trivia for when message arrives before fragment is ready
    var pendingTrivia: ServerBeginTriviaAnsweringPhase? = null

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
    private fun handleUIEvents(message: GameMessage): Boolean {
        Log.i(TAG, "< Received message: $message")

        when (message) {
            is InterfaceVersionMessage -> {
                _gameState.value = GameState.CONNECTED
            }

            is ServerAvatarStatusMessage -> {
                val existingIndex = availableAvatars.indexOfFirst { it.AvatarID == message.AvatarID }
                if (existingIndex >= 0) {
                    availableAvatars[existingIndex] = message
                } else {
                    availableAvatars.add(message)
                }
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

            is ServerBeginTriviaAnsweringPhase -> {
                pendingTrivia = message
                onTriviaMessage?.invoke(message)
            }

            else -> return false
        }
        return true
    }

    fun sendPlayerProfile(playerName: String, culture: String = "en-US") {
        val avatarId = selectedAvatarId ?: return
        Log.d(TAG, "Sending player profile: $playerName with avatar $avatarId")
        scope.launch {
            protocolClient?.sendMessage(ClientPlayerProfileMessage(
                playerName = playerName,
                uppercasePlayerName = playerName.uppercase(),
                deviceCultureName = culture,
                playerCardId = avatarId
            ))
        }
    }

    fun sendStartGameButtonPressed() {
        Log.d(TAG, "Start game button pressed")
        scope.launch {
            protocolClient?.sendMessage(StartGameButtonPressedResponseMessage())
        }
    }

    fun sendContinueButtonPressed() {
        Log.d(TAG, "Continue button pressed")
        scope.launch {
            protocolClient?.sendMessage(ContinuePressedResponseMessage())
        }
    }

    fun sendTriviaAnswer(answerIndex: Int, answerTime: Double) {
        Log.d(TAG, "Trivia answer: index=$answerIndex time=$answerTime")
        scope.launch {
            protocolClient?.sendMessage(ClientTriviaAnswer(
                ChosenAnswerDisplayIndex = answerIndex,
                AnswerTime = answerTime
            ))
        }
    }

    fun sendCategorySelection(doorIndex: Int) {
        Log.d(TAG, "Category selected: door $doorIndex")
        scope.launch {
            protocolClient?.sendMessage(ClientCategorySelectChoice(ChosenCategoryIndex = doorIndex))
        }
    }

    fun requestAvatar(avatarId: String) {
        selectedAvatarId = avatarId
        pendingAvatarRequest = avatarId
        isAvatarConfirmed = false
        scope.launch {
            protocolClient?.sendMessage(ClientRequestAvatarMessage(
                RequestID = UUID.randomUUID().toString(),
                AvatarID = avatarId,
                Request = true
            ))
        }
    }

    fun sendImage(imageData: ByteArray, imageGuid: String, transferId: Int) {
        scope.launch {
            protocolClient?.sendImage(imageData, imageGuid, transferId)
        }
    }

    fun sendImageProfileImage(imageData: ByteArray, imageGuid: String, transferId: Int, playerName: String, culture: String = "en-US") {
        val avatarId = selectedAvatarId ?: return
        Log.d(TAG, "Sending image+profile+image: $playerName with avatar $avatarId")
        scope.launch {
            protocolClient?.sendImage(imageData, imageGuid, transferId)
            protocolClient?.sendMessage(ClientPlayerProfileMessage(
                playerName = playerName,
                uppercasePlayerName = playerName.uppercase(),
                deviceCultureName = culture,
                playerCardId = avatarId
            ))
            protocolClient?.sendImage(imageData, imageGuid, transferId + 1)
        }
    }

    fun disconnect() {
        protocolClient?.close()
        protocolClient = null
        currentServer = null
        selectedAvatarId = null
        availableAvatars.clear()
        _gameState.value = GameState.DISCONNECTED
        Log.d(TAG, "Disconnected from server")
    }
}
