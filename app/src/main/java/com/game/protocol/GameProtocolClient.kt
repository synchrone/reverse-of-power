package com.game.protocol

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.net.*
import java.util.*

// ==================== Protocol Client ====================
fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

class GameProtocolClient(
    private val deviceUID: String,
    private val serverHostStr: String,
    private val serverPort: Int = 9066,
    private val listenHostStr: String = "0.0.0.0",
    private val listenPort: Int = 9060
    ) {
    private val TAG = "GameProtocolClient"
    private var isConnected: Boolean = false
    private var serverHost: InetAddress? = null
    private var serverSocket: DatagramSocket? = null
    private var clientSocket: DatagramSocket? = null

    private val encoder = ProtocolEncoder()
    private val decoder = ProtocolDecoder()
    private var lastRcvMessageId: Byte? = null
    private var connectionDeferred: CompletableDeferred<Unit>? = null
    var connectionError: String? = null

    var onMessageReceived: ((GameMessage) -> Unit)? = null
    var onAvatarListReceived: ((List<ServerAvatarStatusMessage>) -> Unit)? = null
    var onImageReceived: ((imageGuid: String, imageData: ByteArray) -> Unit)? = null
    var onPacketSend: ((ByteArray) -> Unit)? = null
    var testMode: Boolean = false

    // Protocol state
    var assignedPlayerId: Int? = null
        private set
    var assignedSlotId: Int? = null
        private set
    val availableAvatars = mutableListOf<ServerAvatarStatusMessage>()

    // Temporary storage for incoming JPEG payloads, keyed by TransferID
    private val pendingImages = mutableMapOf<Int, ByteArray>()
    // Pending control messages when they arrive before the JPEG
    private val pendingImageControls = mutableMapOf<Int, String>() // TransferID -> ImageGUID

    // Lock to prevent interleaved UDP packets when sending chunked data
    private val sendLock = Any()

    // ==================== Connection ====================

    suspend fun connect(timeoutMs: Long = 5000): Boolean {
        connectionDeferred = CompletableDeferred()
        connectionError = null
        startListening()
        Thread {
            while (testMode || serverSocket?.isClosed == false) {
                if (lastRcvMessageId == null && connectionError == null) {
                    sendConnectionRequest()
                    sendDeviceUID(deviceUID)
                } else if (lastRcvMessageId != null) {
                    sendAck(lastRcvMessageId!!)
                }
                Thread.sleep(1000)
            }
        }.start()

        return awaitConnection(timeoutMs)
    }

    suspend fun awaitConnection(timeoutMs: Long = 5000): Boolean {
        if (connectionError != null) return false
        if (lastRcvMessageId != null) return true
        val completed = withTimeoutOrNull(timeoutMs) {
            connectionDeferred?.await()
        } != null
        // Check if we completed due to an error
        return completed && connectionError == null
    }

    private fun sendConnectionRequest() = synchronized(sendLock){
        sendRawUDP(encoder.encodeConnectionRequest())
    }

    fun sendDeviceUID(uid: String, theByte: Byte = 0x63) = synchronized(sendLock){
        sendRawUDP(encoder.encodeDeviceUID(uid, theByte))
    }

    // ==================== Session Management ====================

    fun requestPlayerID() {
        val msg = ClientRequestPlayerIDMessage(UID = deviceUID)
        sendMessage(msg)
    }

    fun sendAllResourcesReceived(requirements: List<ResourceRequirement> = listOf()) {
        val msg = AllResourcesReceivedMessage(Requirements = requirements)
        sendMessage(msg)
    }

    fun sendDeviceInfo(model: String, os: String) {
        val msg = DeviceInfoMessage(
            Response = 10,
            DeviceSize = 2,
            DeviceOS = 1,
            DeviceModel = model,
            DeviceType = "Handheld",
            DeviceUID = deviceUID,
            DeviceOperatingSystem = os
        )
        sendMessage(msg)
    }

    // ==================== Avatar Management ====================

    fun requestAvatarStatus() {
        val msg = ClientRequestAvatarStatusMessage()
        sendMessage(msg)
    }

    fun requestAvatar(avatarId: String): String {
        val requestId = UUID.randomUUID().toString() // e.g "35858af1-bc80-4870-9f4e-189d4a0f38f8"
        val msg = ClientRequestAvatarMessage(
            RequestID = requestId,
            AvatarID = avatarId,
            Request = true
        )
        sendMessage(msg)
        return requestId
    }

    // ==================== Player Profile ====================

    fun sendPlayerProfile(name: String, avatarId: String, culture: String = "en-US") {
        val msg = ClientPlayerProfileMessage(
            playerName = name,
            uppercasePlayerName = name.uppercase(),
            deviceCultureName = culture,
            playerCardId = avatarId
        )
        sendMessage(msg)
    }

    // ==================== Game Control ====================

    fun sendStartGameButtonPressed() {
        val msg = StartGameButtonPressedResponseMessage()
        sendMessage(msg)
    }

    fun sendCategorySelection(doorIndex: Int) {
        val msg = ClientCategorySelectChoice(ChosenCategoryIndex = doorIndex)
        sendMessage(msg)
    }

    // ==================== Image Transfer ====================

    fun sendImage(imageData: ByteArray, imageGuid: String, transferId: Int, imgType: Int = 2) = synchronized(sendLock) {
        Log.i(TAG, "> Sending image transfer ID: $transferId (${imageData.size}b)")
        val packets = encoder.encodeImageTransfer(imageData, imageGuid, transferId, imgType)
        packets.forEach { sendRawUDP(it) }
    }

    // ==================== Core Messaging ====================

    private fun sendMessage(msg: GameMessage) {
        Log.i(TAG, "> Sending message: ${msg.TypeString}")
        sendRawUDP(encoder.encodeMessage(msg))
    }

    private fun sendAck(messageId: Byte) = synchronized(sendLock) {
        Log.d(TAG, "> ACK [t${Thread.currentThread().id} ${System.currentTimeMillis()}] 0x${messageId.toHexString()}")
        sendRawUDP(encoder.encodeAck(messageId))
    }

    private fun sendRawUDP(data: ByteArray) {
        onPacketSend?.invoke(data)
        if (testMode) return
        val packet = DatagramPacket(
            data,
            data.size,
            serverHost,
            serverPort
        )
        serverSocket?.send(packet)
    }

    // ==================== Receiving ====================

    private fun startListening() {
        if(testMode){
            return // will be fed packets via handleReceivedPacket()
        }
        // Force Java to prefer IPv4 stack to avoid IPv6 binding issues
        System.setProperty("java.net.preferIPv4Stack", "true")

        serverHost = InetAddress.getByName(serverHostStr)
        serverSocket = DatagramSocket(0, Inet4Address.getByName("0.0.0.0") as InetAddress)
        clientSocket = DatagramSocket(listenPort, Inet4Address.getByName(listenHostStr) as InetAddress)

        Thread {
            val buffer = ByteArray(2048)
            while (clientSocket?.isClosed == false) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    clientSocket?.receive(packet)
                    handleReceivedPacket(packet.data.sliceArray(0 until packet.length))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    fun handleReceivedPacket(data: ByteArray) {
        val decoded = decoder.decode(data)

        when (decoded) {
            is DecodedPacket.GameInProgress -> {
                Log.e(TAG, "< Game in progress - cannot join")
                connectionError = "Game in progress"
                connectionDeferred?.complete(Unit)
            }

            is DecodedPacket.ConnectionInit -> {
                Log.d(TAG, "< 0x8a, 0x33, 0xFF (4)")
                isConnected = true
                connectionDeferred?.complete(Unit)
            }

            is DecodedPacket.Ack -> {
                Log.e(TAG, "< [t${Thread.currentThread().id} ${System.currentTimeMillis()}] ACK 0x${decoded.messageId.toHexString()}")
            }

            is DecodedPacket.Nack -> {
                Log.e(TAG, "< [t${Thread.currentThread().id} ${System.currentTimeMillis()}] NACK 0x${decoded.messageId.toHexString()}: ${decoded.payload.toHex()}")
            }

            is DecodedPacket.Fragment -> {
                Log.d(TAG, "< FRAG 0x${decoded.messageId.toHexString()} (${decoded.idx}/${decoded.total}) l=${decoded.size}")
            }

            is DecodedPacket.DataMessage -> {
                lastRcvMessageId = decoded.messageId
                for (message in decoded.messages) {
                    if (message is ImageResourceContentTransferMessage) {
                        handleImageControl(message)
                    } else {
                        handleProtocolMessage(message)
                        onMessageReceived?.invoke(message)
                    }
                }
            }

            is DecodedPacket.ImageData -> {
                lastRcvMessageId = decoded.messageId
                handleImageData(decoded.transferId, decoded.jpeg)
            }

            is DecodedPacket.TooShort -> {}
            is DecodedPacket.Unknown -> {
                Log.d(TAG, "Unknown packet type: ${data.toHexString()}")
            }
            is DecodedPacket.Error -> {
                Log.e(TAG, "Error decoding packet: ${decoded.message}")
            }
            is DecodedPacket.DeviceUID -> {}
        }
    }

    // ==================== Image Matching ====================

    private fun handleImageData(transferId: Int, jpeg: ByteArray) {
        val pendingGuid = pendingImageControls.remove(transferId)
        if (pendingGuid != null) {
            Log.d(TAG, "^ matched JPEG for transferId=$transferId with waiting control (${jpeg.size} bytes)")
            onImageReceived?.invoke(pendingGuid, jpeg)
        } else {
            pendingImages[transferId] = jpeg
            Log.d(TAG, "^ stored JPEG for transferId=$transferId (${jpeg.size} bytes)")
        }
    }

    private fun handleImageControl(message: ImageResourceContentTransferMessage) {
        val pendingJpeg = pendingImages.remove(message.TransferID)
        if (pendingJpeg != null) {
            Log.d(TAG, "Matched JPEG for transferId=${message.TransferID}, imageGuid=${message.ImageGUID}")
            onImageReceived?.invoke(message.ImageGUID, pendingJpeg)
        } else {
            Log.d(TAG, "No pending JPEG for transferId=${message.TransferID}, storing control to wait for JPEG")
            pendingImageControls[message.TransferID] = message.ImageGUID
        }
    }

    // ==================== Protocol Responses ====================

    private fun handleProtocolMessage(message: GameMessage) {
        when (message) {
            is InterfaceVersionMessage -> {
                Log.d(TAG, "Server version: ${message.InterfaceVersion}")
            }

            is SessionStateMessage -> {
                Log.d(TAG, "Session ID: ${message.SessionID}")
                requestPlayerID()
            }

            is AssignPlayerIDAndSlotMessage -> {
                assignedPlayerId = message.PlayerID
                assignedSlotId = message.SlotID
                Log.d(TAG, "Assigned Player ID: ${message.PlayerID}, Slot: ${message.SlotID}")
                Log.d(TAG, "Display Name: ${message.DisplayName}")

                sendDeviceInfo(
                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    "Android OS ${android.os.Build.VERSION.RELEASE} / API-${android.os.Build.VERSION.SDK_INT} (${android.os.Build.ID})"
                )
                requestAvatarStatus()
            }

            is ServerAvatarStatusMessage -> {
                val existingIndex = availableAvatars.indexOfFirst { it.AvatarID == message.AvatarID }
                if (existingIndex >= 0) {
                    availableAvatars[existingIndex] = message
                } else {
                    availableAvatars.add(message)
                }
            }

            is ServerAvatarRequestResponseMessage -> {}

            is ResourceRequirementsMessage -> {
                sendAllResourcesReceived(message.Requirements)
            }
        }
    }

    fun close() {
        serverSocket?.close()
        serverSocket = null
        clientSocket?.close()
        clientSocket = null
    }

    // ==================== Utilities ====================

    private fun ByteArray.toHex(): String =
        joinToString(",") { "0x%x".format(it) }

}