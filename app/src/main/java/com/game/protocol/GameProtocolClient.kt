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
    private val listenPort: Int = 9060,
    val decades: Boolean = false
    ) {
    private val TAG = "GameProtocolClient"
    private var isConnected: Boolean = false
    private var serverHost: InetAddress? = null
    private var serverSocket: DatagramSocket? = null
    private var clientSocket: DatagramSocket? = null

    private val encoder = ProtocolEncoder(decades = decades)
    private val decoder = ProtocolDecoder()
    private var lastRcvMessageId: Int? = null
    private var connectionDeferred: CompletableDeferred<Unit>? = null
    var connectionError: String? = null

    var onMessageReceived: ((GameMessage) -> Boolean)? = null
    var onImageReceived: ((imageGuid: String, imageData: ByteArray) -> Unit)? = null
    var onPacketSend: ((ByteArray) -> Unit)? = null
    var testMode: Boolean = false

    // Protocol state
    var assignedPlayerId: Int? = null
        private set
    var assignedSlotId: Int? = null
        private set
    // Temporary storage for incoming JPEG payloads, keyed by TransferID
    private val pendingImages = mutableMapOf<Int, ByteArray>()
    // Pending control messages when they arrive before the JPEG
    private val pendingImageControls = mutableMapOf<Int, String>() // TransferID -> ImageGUID

    // GUIDs of images we've already received this session
    private val receivedResourceGUIDs = mutableSetOf<String>()
    // Resource request tracking: GUIDs we've requested but haven't received yet
    private val pendingResourceGUIDs = mutableSetOf<String>()
    // Requirements to echo back once all resources are received
    private var pendingRequirements: List<ResourceRequirement>? = null

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
        return completed && connectionError == null
    }

    private fun sendConnectionRequest() = synchronized(sendLock){
        sendRawUDP(encoder.encodeConnectionRequest())
    }

    fun sendDeviceUID(uid: String, theByte: Byte = 0x63) = synchronized(sendLock){
        sendRawUDP(encoder.encodeDeviceUID(uid, theByte))
    }

    // ==================== Image Transfer ====================

    fun sendImage(imageData: ByteArray, imageGuid: String, transferId: Int, imgType: Int = 2) = synchronized(sendLock) {
        Log.i(TAG, "> Sending image transfer ID: $transferId (${imageData.size}b)")
        val packets = encoder.encodeImageTransfer(imageData, imageGuid, transferId, imgType)
        packets.forEach { sendRawUDP(it) }
    }

    // ==================== Core Messaging ====================

    fun sendMessage(msg: GameMessage) {
        Log.i(TAG, "> Sending message: $msg")
        encoder.encodeMessage(msg).forEach { sendRawUDP(it) }
    }

    private fun sendAck(messageId: Int) = synchronized(sendLock) {
//        Log.d(TAG, "> ACK $messageId")
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
            is DecodedPacket.ConnectionInit -> {
                Log.d(TAG, "< 0x8a, 0x33, 0xFF (4)")
                isConnected = true
                connectionDeferred?.complete(Unit)
            }

            is DecodedPacket.Ack -> {
//                Log.d(TAG, "< ACK ${decoded.messageId}")
            }

            is DecodedPacket.Nack -> {
                Log.e(TAG, "< NACK ${decoded.messageId}: ${decoded.payload.toHex()}")
            }

            is DecodedPacket.Fragment -> {
//                Log.d(TAG, "< FRAG ${decoded.messageId} (${decoded.idx+1}/${decoded.total}) l=${decoded.size}")
            }

            is DecodedPacket.DataMessage -> {
                Log.d(TAG, "< DataMessage ${decoded.messageId}: ${decoded.messages.size} messages")

                for (message in decoded.messages) {
                    if (message is ImageResourceContentTransferMessage) {
                        handleImageControl(message)
                    } else {
                        val handledByProtocol = handleProtocolMessage(message)
                        val handledByUI = onMessageReceived?.invoke(message) ?: false
                        if (!handledByProtocol && !handledByUI) {
                            Log.w(TAG, "< Unhandled message: ${message.TypeString}")
                        }
                    }
                }

                lastRcvMessageId = decoded.messageId
            }

            is DecodedPacket.ImageData -> {
                handleImageData(decoded.transferId, decoded.jpeg)
                lastRcvMessageId = decoded.messageId
            }

            is DecodedPacket.Error -> {
                Log.e(TAG, "Error decoding packet: ${decoded.message} ${data.toHexString()}")
            }
            is DecodedPacket.Unknown -> {
                Log.d(TAG, "Unknown packet type: ${data.toHexString()}")
            }
            else -> {
                Log.e(TAG, "Unhandled packet type: $decoded: ${data.toHexString()}")
            }
        }
    }

    // ==================== Image Matching ====================

    private fun handleImageData(transferId: Int, jpeg: ByteArray) {
        val pendingGuid = pendingImageControls.remove(transferId)
        if (pendingGuid != null) {
            Log.d(TAG, "^ matched JPEG for transferId=$transferId with waiting control $pendingGuid (${jpeg.size} bytes)")
            onImageReceived?.invoke(pendingGuid, jpeg)
            checkResourceComplete(pendingGuid)
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
            checkResourceComplete(message.ImageGUID)
        } else {
            Log.d(TAG, "No pending JPEG for transferId=${message.TransferID}, storing control to wait for JPEG")
            pendingImageControls[message.TransferID] = message.ImageGUID
        }
    }

    private fun checkResourceComplete(guid: String) {
        receivedResourceGUIDs.add(guid)
        pendingResourceGUIDs.remove(guid)

        Log.d(TAG, "Resource $guid received, sending AllResourcesReceivedMessage")
        val req = ResourceRequirement(GUID = guid, Rqrmnt = 0)
        sendMessage(AllResourcesReceivedMessage(Requirements = listOf(req)))

        if (pendingResourceGUIDs.isEmpty() && pendingRequirements != null) {
            pendingRequirements = null
        }
    }

    // ==================== Protocol Responses ====================

    private fun handleProtocolMessage(message: GameMessage): Boolean {
        when (message) {
            is InterfaceVersionMessage -> {
                Log.d(TAG, "Server version: ${message.InterfaceVersion}")
            }

            is SessionStateMessage -> {
                Log.d(TAG, "Session ID: ${message.SessionID}")
                sendMessage(ClientRequestPlayerIDMessage(UID = deviceUID))
            }

            is AssignPlayerIDAndSlotMessage -> {
                assignedPlayerId = message.PlayerID
                assignedSlotId = message.SlotID
                Log.d(TAG, "Assigned Player ID: ${message.PlayerID}, Slot: ${message.SlotID}")
                Log.d(TAG, "Display Name: ${message.DisplayName}")

                sendMessage(DeviceInfoMessage(
                    Response = 10,
                    DeviceSize = 2,
                    DeviceOS = 1,
                    DeviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    DeviceType = "Handheld",
                    DeviceUID = deviceUID,
                    DeviceOperatingSystem = "Android OS ${android.os.Build.VERSION.RELEASE} / API-${android.os.Build.VERSION.SDK_INT} (${android.os.Build.ID})"
                ))
                sendMessage(ClientRequestAvatarStatusMessage())
            }

            is ServerToClientTimeSyncMessage -> {
                Log.d(TAG, "Time sync: clientTick=${message.clientSentTick}, serverTick=${message.serverSentTick}")
            }

            is ServerRequestEndOfGameFactCount -> {
                sendMessage(ClientEndOfGameFactCount(FactCount = 63)) // random number, TODO: fix this somehow? is it a total answer counter?
            }

            is ResourceRequirementsMessage -> {
                val missing = message.Requirements.filter { it.GUID !in receivedResourceGUIDs }
                if (missing.isEmpty()) {
                    sendMessage(AllResourcesReceivedMessage(Requirements = message.Requirements))
                } else {
                    pendingRequirements = message.Requirements
                    for (req in missing) {
                        pendingResourceGUIDs.add(req.GUID)
                        sendMessage(RequestResourceMessage(ResourceGUID = req.GUID))
                    }
                }
            }

            else -> return false
        }
        return true
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