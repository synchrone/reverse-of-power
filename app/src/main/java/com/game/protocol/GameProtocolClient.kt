@file:OptIn(InternalSerializationApi::class)

package com.game.protocol

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

// ==================== Message Models ====================

@Serializable
abstract class GameMessage {
    abstract val TypeString: String
}

@Serializable
data class InterfaceVersionMessage(
    override val TypeString: String = "InterfaceVersionMessage",
    val InterfaceVersion: String
) : GameMessage()

@Serializable
data class SessionStateMessage(
    override val TypeString: String = "SessionStateMessage",
    val SessionID: Long
) : GameMessage()

@Serializable
data class ClientRequestPlayerIDMessage(
    override val TypeString: String = "ClientRequestPlayerIDMessage",
    val UID: String
) : GameMessage()

@Serializable
data class AssignPlayerIDAndSlotMessage(
    override val TypeString: String = "AssignPlayerIDAndSlotMessage",
    val PlayerID: Int,
    val SlotID: Int,
    val UDPPortOffset: Int,
    val DisplayName: String,
    val PSNID: String
) : GameMessage()

@Serializable
data class ResourceRequirement(
    val GUID: String,
    val Rqrmnt: Int
)

@Serializable
data class ResourceRequirementsMessage(
    override val TypeString: String = "ResourceRequirementsMessage",
    val Requirements: List<ResourceRequirement>
) : GameMessage()

@Serializable
data class AllResourcesReceivedMessage(
    override val TypeString: String = "AllResourcesReceivedMessage",
    val Requirements: List<ResourceRequirement>
) : GameMessage()

@Serializable
data class ClientQuizCommandMessage(
    override val TypeString: String = "ClientQuizCommandMessage",
    val action: Int, // 14 = show ready button; 31 = go back to name selection screen; 29, 30, 15 = something around game being exited
    val time: Double
) : GameMessage()

@Serializable // 1
data class ClientRequestAvatarStatusMessage(
    override val TypeString: String = "KnowledgeIsPower.ClientRequestAvatarStatusMessage"
) : GameMessage()

@Serializable // 2
data class ServerAvatarStatusMessage(
    override val TypeString: String = "KnowledgeIsPower.ServerAvatarStatusMessage",
    val AvatarID: String,
    val Available: Boolean
) : GameMessage()

@Serializable // 3
data class ClientRequestAvatarMessage(
    override val TypeString: String = "KnowledgeIsPower.ClientRequestAvatarMessage",
    val RequestID: String,
    val AvatarID: String,
    val Request: Boolean
) : GameMessage()

@Serializable // 4
data class ServerAvatarRequestResponseMessage(
    override val TypeString: String = "KnowledgeIsPower.ServerAvatarRequestResponseMessage",
    val RequestID: String,
    val AvatarID: String,
    val Available: Boolean
) : GameMessage()


@Serializable
data class ClientPlayerProfileMessage(
    override val TypeString: String = "ClientPlayerProfileMessage",
    val playerName: String,
    val uppercasePlayerName: String,
    val deviceCultureName: String,
    val playerCardId: String
) : GameMessage()

@Serializable
data class DeviceInfoMessage(
    override val TypeString: String = "DeviceInfoMessage",
    val Response: Int,
    val DeviceSize: Int,
    val DeviceOS: Int,
    val DeviceModel: String,
    val DeviceType: String,
    val DeviceUID: String,
    val DeviceOperatingSystem: String
) : GameMessage()

@Serializable
data class ClientImageResourceContentTransferMessage(
    override val TypeString: String = "ClientImageResourceContentTransferMessage",
    val TransferID: Int,
    val ImageGUID: String,
    val ImgType: Int
) : GameMessage()

@Serializable
data class ImageResourceContentTransferMessage(
    override val TypeString: String = "ImageResourceContentTransferMessage",
    val TransferID: Int,
    val ImageGUID: String,
    val Acknowledge: Boolean,
    @Transient
    var image: ByteArray? = null
) : GameMessage()

@Serializable
data class ClientGameIDMessage(
    override val TypeString: String = "ClientGameIDMessage",
    val GameID: String
) : GameMessage()

@Serializable
data class ClientHoldingScreenCommandMessage(
    override val TypeString: String = "ClientHoldingScreenCommandMessage",
    val action: Int, // 5 = look at the TV;
    val time: Double,
    val HoldingScreenText: String,
    val HoldingScreenType: Int, // 4 = look at the tv;  9 = get ready
    val OtherPlayerIndex: Int,
    val ShowPortraitPhotoControls: Boolean
) : GameMessage()

@Serializable
data class PlayerJoinedMessage(
    override val TypeString: String = "PlayerJoinedMessage",
    val CurrentPlayerID: Int,
    val OldPlayerID: Int,
    val SlotID: Int
) : GameMessage()

@Serializable
data class PlayerLeftMessage(
    override val TypeString: String = "PlayerLeftMessage",
    val PlayerID: Int
) : GameMessage()

@Serializable
data class PlayerNameQuizStateMessage(
    override val TypeString: String = "PlayerNameQuizStateMessage",
    val PlayerName: String,
    val PlayerID: Int
) : GameMessage()

@Serializable
data class ColorTint(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float
)

@Serializable
data class ServerColourMessage(
    override val TypeString: String = "KnowledgeIsPower.ServerColourMessage",
    val BackgroundTint: ColorTint,
    val PrimaryTint: ColorTint,
    val SecondaryTint: ColorTint,
    val Rainbow: Boolean,
    val RoundType: Int = -1
) : GameMessage()

@Serializable
data class StartGameButtonPressedResponseMessage(
    override val TypeString: String = "StartGameButtonPressedResponseMessage",
    val Response: Int = 9
) : GameMessage()

@Serializable
data class CategoryChoice(
    val DisplayText: String,
    val Colour: ColorTint,
    val DoorIndex: Int,
    val ChoiceType: Int
)

@Serializable
data class ServerCategorySelectChoices(
    override val TypeString: String = "KnowledgeIsPower.ServerCategorySelectChoices",
    val CategoryChoices: List<CategoryChoice>,
    val BackgroundTint: ColorTint,
    val PrimaryTint: ColorTint,
    val SecondaryTint: ColorTint
) : GameMessage()

@Serializable
data class ServerRequestCategorySelectChoice(
    override val TypeString: String = "KnowledgeIsPower.ServerRequestCategorySelectChoice"
) : GameMessage()

@Serializable
data class ClientCategorySelectChoice(
    override val TypeString: String = "KnowledgeIsPower.ClientCategorySelectChoice",
    val ChosenCategoryIndex: Int
) : GameMessage()

@Serializable
data class ServerBeginCategorySelectOverride(
    override val TypeString: String = "KnowledgeIsPower.ServerBeginCategorySelectOverride",
    val DurationSeconds: Double,
    val InitialCategorySelectChoice: String,
    val DoorIndex: Int
) : GameMessage()

@Serializable
data class ClientCategorySelectOverride(
    override val TypeString: String = "KnowledgeIsPower.ClientCategorySelectOverride",
    val DurationSeconds: Double
) : GameMessage()

@Serializable
data class ServerStopCategorySelectOverride(
    override val TypeString: String = "KnowledgeIsPower.ServerStopCategorySelectOverride"
) : GameMessage()

@Serializable
data class ClientStopCategorySelectOverrideResponse(
    override val TypeString: String = "KnowledgeIsPower.ClientStopCategorySelectOverrideResponse",
    val OverrideSent: Boolean
) : GameMessage()

@Serializable
data class ServerCategorySelectOverrideSuccess(
    override val TypeString: String = "KnowledgeIsPower.ServerCategorySelectOverrideSuccess",
    val CategorySelectOverrideSuccess: Boolean,
    val CategorySelectOverridePlayerName: String
) : GameMessage()

@Serializable
data class TriviaAnswer(
    val DisplayIndex: Int,
    val DisplayText: String,
    val IsCorrect: Boolean
)

@Serializable
data class PowerPlayPlayer(
    val SlotIndex: Int,
    val Name: String,
    val ImageGUID: String,
    val Colour: ColorTint,
    val Self: Boolean,
    val Away: Boolean
)

@Serializable
data class ServerBeginTriviaAnsweringPhase(
    override val TypeString: String = "KnowledgeIsPower.ServerBeginTriviaAnsweringPhase",
    val QuestionID: String,
    val QuestionText: String,
    val QuestionDuration: Double,
    val Answers: List<TriviaAnswer>,
    val PowerPlays: List<String>,
    val PowerPlayPlayers: List<PowerPlayPlayer>,
    val RoundType: Int,
    val BackgroundTint: ColorTint,
    val PrimaryTint: ColorTint,
    val SecondaryTint: ColorTint
) : GameMessage()

// ==================== Protocol Packet Classes ====================

data class ProtocolPacket(
    val header: Byte,
    val secondaryHeader: Byte,
    val messageId: Short,
    val packetNumber: Short,
    val totalPackets: Short,
    val dataLength: Long,
    val payload: ByteArray
)

data class AckPacket(
    val header: Byte = 0x8a.toByte(),
    val secondaryHeader: Byte = 0x33.toByte(),
    val messageId: Byte,
    val padding: ByteArray = ByteArray(34)
)

// ==================== Protocol Client ====================
public fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

public class GameProtocolClient(
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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    public var messageCounter: Byte = 0
    private var lastRcvMessageId: Byte? = null
    private val fragmentBuffer = mutableMapOf<Byte, MutableMap<Int, ByteArray>>()
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
        val data = ByteArray(38)
        data[0] = 0x8a.toByte()
        data[1] = 0x33.toByte()
        data[2] = 0xff.toByte()
        data[3] = 0xff.toByte()
        data[4] = 0xff.toByte()
        data[5] = 0xff.toByte()
        sendRawUDP(data)
    }

    public fun sendDeviceUID(uid: String, theByte: Byte = 0x63) = synchronized(sendLock){
        val uidBytes = uid.toByteArray(Charsets.UTF_8)
        val data = ByteBuffer.allocate(12+uidBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        data.put(bytes(0xc, 0x89, 0xe8, 0x84))
        data.put(bytes(0x61, 0x3, 0xf4))
        data.put(theByte) // random is ok
        data.putInt(uidBytes.size)
        data.put(uidBytes)
        sendRawUDP(data.array())
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
        val requestId = UUID.randomUUID().toString()
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

    fun sendImage(imageData: ByteArray, imageGuid: String, transferId: Int, imgType: Int = 2) {
        // Send image transfer message first
        val msg = ClientImageResourceContentTransferMessage(
            TransferID = transferId,
            ImageGUID = imageGuid,
            ImgType = imgType // 2 = player photo
        )
        sendMessage(msg)


        val payloadBuffer = ByteBuffer.allocate(imageData.size + 10).order(ByteOrder.LITTLE_ENDIAN)
        payloadBuffer.put(bytes(0x33, 0x29))  // magic
        payloadBuffer.putInt(transferId - 1)  // zero-based transfer ID
        payloadBuffer.putInt(imageData.size)  // total JPEG length
        Log.i(TAG, "> Sending transfer ID: $transferId (${imageData.size}b): ${payloadBuffer.array().slice(0 until 10).toByteArray().toHex()}")
        payloadBuffer.put(imageData)
        sendChunked(payloadBuffer.array())
    }

    private fun sendChunked(payload: ByteArray) {
        // Fragment and send image data
        val fragmentSize = 1002  // Max payload per chunk (1024 total - 22 byte header)
        val totalFragments = ((payload.size + fragmentSize - 1) / fragmentSize)
        val messageId = ++messageCounter

        var byteOffset = 0
        for (i in 0 until totalFragments) {
            val start = i * fragmentSize
            val end = minOf(start + fragmentSize, payload.size)
            val fragment = payload.sliceArray(start until end)

            send(fragment, totalFragments, i, messageId = messageId, byteOffset = byteOffset)
            byteOffset += fragment.size
            Thread.sleep(30)
        }
    }

    // ==================== Core Messaging ====================

    /**
     * Send a protocol packet. Header layout:
     * - bytes 0-1: magic (0xAE, 0x7F)
     * - byte 2: messageId
     * - bytes 3-6: total packets (big-endian)
     * - bytes 7-10: packet index (big-endian)
     *
     * Standard (byteOffset == null): 16-byte header
     * - bytes 11-14: payload length (big-endian)
     * - byte 15: flags
     *
     * Chunked (byteOffset != null): 22-byte header
     * - bytes 11-13: static 0x00, 0x00, 0x00
     * - bytes 14-17: chunk length (little-endian)
     * - bytes 18-21: byte offset (little-endian)
     */
    private fun send(payload: ByteArray, packetNum: Int = 1, packetIdx: Int = 0, messageId: Byte? = null, byteOffset: Int = 0) = synchronized(sendLock){
        val buffer = ByteBuffer.allocate(payload.size + 22).order(ByteOrder.BIG_ENDIAN)
        buffer.put(bytes(0xAE, 0x7F))
        buffer.put(messageId ?: ++messageCounter)
        buffer.putInt(packetNum)
        buffer.putInt(packetIdx)
        buffer.put(bytes(0x00, 0x00, 0x00))
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(payload.size)
        buffer.putInt(byteOffset)
        buffer.put(payload)
        val packet = buffer.array()
        sendRawUDP(packet)
    }

    @Suppress("UNCHECKED_CAST")
    private fun encodeJson(msg: GameMessage): ByteArray {
        val serializer = msg::class.serializer() as KSerializer<GameMessage>
        val jsonStr = json.encodeToString(serializer, msg)
        Log.i(TAG, "> Sending message: $jsonStr")
        return jsonStr.toByteArray(Charsets.UTF_8)
    }

    private fun sendMessage(msg: GameMessage) {
        val jsonBytes = encodeJson(msg)

        val buffer = ByteBuffer.allocate(jsonBytes.size + 10).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(bytes(0x33, 0x29)) // magic
        buffer.put(bytes(0xB1, 0xE2, 0xFF, 0xFF)) // json type
        buffer.putInt(jsonBytes.size)
        buffer.put(jsonBytes)
        send(buffer.array())
    }

    private fun sendMessageMulti(vararg msgs: GameMessage) {
        val jsonBytesList = msgs.map { encodeJson(it) }
        val entriesSize = jsonBytesList.sumOf { 8 + it.size } // len 4b + type 4b each
        val bodyLength = entriesSize + 6 // multijson type 4b + footer 2b

        val buffer = ByteBuffer.allocate(10 + bodyLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(bytes(0x33, 0x29)) // magic
        buffer.putInt(nextTransferId++)
        buffer.putInt(bodyLength)
        buffer.put(bytes(0x84, 0x12, 0x40, 0xEE)) // multijson type

        for (jsonBytes in jsonBytesList) {
            buffer.putInt(jsonBytes.size)
            buffer.put(bytes(0xB1, 0xE2, 0xFF, 0xFF)) // json type
            buffer.put(jsonBytes)
        }
        buffer.put(bytes(0x32, 0x90)) // footer

        send(buffer.array())
    }

    private fun sendAck(messageId: Byte) = synchronized(sendLock) {
        Log.d(TAG, "> ACK 0x${messageId.toHexString()}")
        val ack = AckPacket(messageId = messageId)
        val buffer = ByteBuffer.allocate(38).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ack.header)
        buffer.put(ack.secondaryHeader)
        buffer.put(ack.messageId)
        buffer.put(ack.padding)
        sendRawUDP(buffer.array())
    }

    private fun sendRawUDP(data: ByteArray) {
        if(!(data[0]== 0x8a.toByte() && data[1] == 0x33.toByte())) {
            Log.d(TAG, "> ${data.size}b: ${data.slice(0 until 22).toByteArray().toHex()} ...")
//            data.asIterable().chunked(800).forEachIndexed { i, chunk ->
//                Log.d(TAG, "> ${data.size}b [$i]: ${chunk.toByteArray().toHex()}")
//            }
        }
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

    public fun handleReceivedPacket(data: ByteArray) {
        if (data.size < 8) return

        // 4edd96663b274f00 means "available"
        // Check for "game in progress" magic packet: e558fc895c8df001
        if (data.sliceArray(0..7).contentEquals(
                bytes(0xe5, 0x58, 0xfc, 0x89, 0x5c, 0x8d, 0xf0, 0x01)
            )
        ) {
            Log.e(TAG, "< Game in progress - cannot join")
            connectionError = "Game in progress"
            connectionDeferred?.complete(Unit)
            return
        }

        val header = data[0]
        val secondaryHeader = data[1]

        when {
            header == 0xae.toByte() && secondaryHeader == 0x7f.toByte() -> {
                try {
                    handleDataPacket(data)
                }catch (e: Exception){
                    Log.e(TAG, "Error handling data packet ${data.toHex()}", e)
                }
            }
            header == 0x8a.toByte() && secondaryHeader == 0x33.toByte() -> {
                if(data.sliceArray(2 .. 5).contentEquals(bytes(0xff, 0xff, 0xff, 0xff))){
                    Log.d(TAG, "< 0x8a, 0x33, 0xFF (4)")
                    isConnected = true;
                    connectionDeferred?.complete(Unit)
                }else {
                    val payload = data.sliceArray(3 until data.size)
                    if (payload.any { it != 0.toByte() }) {
                        Log.e(TAG, "< NACK 0x${data[2].toHexString()}: ${payload.toHexString()}")
                    }
                }
            }
            else -> {
                // Unknown packet type
                Log.d(TAG, "Unknown packet type: ${data.toHexString()}")
            }
        }
    }

    private fun handleDataPacket(data: ByteArray) {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        // read first header, 16 bytes
        buffer.get() // 0xae
        buffer.get() // 0x7f
        val messageId = buffer.get()
        val packetNum = buffer.int // packets for this message ID
        val packetIdx = buffer.int // packet idx
        buffer.get()
        buffer.get() // 3x zero
        buffer.get()
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val packetLen = buffer.int // length of this packet
        val offset = buffer.int

        if (packetNum>1) { //chunked transmission
            Log.d(TAG, "< FRAG 0x${messageId.toHexString()} (${packetIdx+1}/$packetNum) l=$packetLen, offset=$offset")
            handleFragmentedPacket(messageId, packetIdx, packetNum, data.sliceArray(22 until data.size))
            return
        }

        return handleData(messageId, data.sliceArray(22 until data.size))
    }

    private fun handleFragmentedPacket(messageId: Byte, packetNum: Int, totalPackets: Int, fragment: ByteArray) {
        if (!fragmentBuffer.containsKey(messageId)) {
            fragmentBuffer[messageId] = mutableMapOf()
        }

        fragmentBuffer[messageId]!![packetNum] = fragment

        // Check if all fragments received
        if (fragmentBuffer[messageId]!!.size == totalPackets) {
            val completeData = reassembleFragments(messageId, totalPackets)
            fragmentBuffer.remove(messageId)
            handleData(messageId, completeData);
        }
    }

    private fun reassembleFragments(messageId: Byte, totalPackets: Int): ByteArray {
        val fragments = fragmentBuffer[messageId]!!
        val totalSize = fragments.values.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0

        for (i in 0 until totalPackets) {
            val fragment = fragments[i]!!
            System.arraycopy(fragment, 0, result, offset, fragment.size)
            offset += fragment.size
        }

        return result
    }

    fun handleData(messageId: Byte, data: ByteArray){
        lastRcvMessageId = messageId

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.getShort() // static 0x33, 0x29
        var payloadType = buffer.int
        val payloadLength = buffer.int
        var transferId = -1
        if(payloadType > 0){
            transferId = payloadType
            payloadType = buffer.int
        }
        Log.d(TAG, "< RCV 0x${messageId.toHexString()}, type=${payloadType.toHexString()}, len=${payloadLength}")
        if(payloadType == -7503){ // 0xB1, 0xE2, 0xFF, 0xFF // JSON
            return parseJsonPayload(data.sliceArray(buffer.position() until data.size));
        }
        else if(payloadType == -297790844) // 0x84, 0x12, 0x40, 0xEE // multijson
        {
            var processed = 15;
            while (processed < payloadLength-2) {
                val doclen = buffer.int
                val doctype = buffer.int; // 0xB1, 0xE2, 0xFF, 0xFF for json
                if(doctype == -7503) {
                    parseJsonPayload(data.sliceArray(buffer.position() until buffer.position() + doclen))
                }else {
                    throw NotImplementedError("doctype ${doctype.toHexString()} (${doclen}b) is not implemented")
                }
                processed += doclen + 8;
                buffer.position(buffer.position() + doclen)
            }
            buffer.get() // 0x32 finish
            buffer.get() // 0x90 finish
        }
        else if(payloadType == -520103681) // 0xFF, 0xD8, 0xFF, 0xE0 // JPEG SOI marker + JFIF APP0
        {
            val jpeg = data.sliceArray(buffer.position() until data.size)

            // Check if we already have a control message waiting for this JPEG
            val pendingGuid = pendingImageControls.remove(transferId)
            if (pendingGuid != null) {
                Log.d(TAG, "^ matched JPEG for transferId=$transferId with waiting control (${jpeg.size} bytes)")
                for (i in jpeg.indices step 800) {
                    val chunk = jpeg.copyOfRange(i, minOf(i + 800, jpeg.size))
                    Log.d(TAG, "JPEG[$i]: ${Base64.getEncoder().encodeToString(chunk)}")
                }
                onImageReceived?.invoke(pendingGuid, jpeg)
            } else {
                pendingImages[transferId] = jpeg
                Log.d(TAG, "^ stored JPEG for transferId=$transferId (${jpeg.size} bytes)")
            }
        }else{
            throw NotImplementedError("payloadType ${payloadType.toHexString()} not implemented");
        }
    }

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
                Log.d(TAG, "Avatar ${message.AvatarID}: Available=${message.Available}")
                val existingIndex = availableAvatars.indexOfFirst { it.AvatarID == message.AvatarID }
                if (existingIndex >= 0) {
                    availableAvatars[existingIndex] = message
                } else {
                    availableAvatars.add(message)
                }
            }

            is ServerAvatarRequestResponseMessage -> {
                Log.d(TAG, "Avatar ${message.AvatarID} request response - Available: ${message.Available}")
            }

            is ResourceRequirementsMessage -> {
                Log.d(TAG, "Resources required: ${message.Requirements}")
                // report we have them all, since they're not implemented yet
                // they're typically used as faint background on frozen buttons, maybe other PowerPlay woes
                sendAllResourcesReceived(message.Requirements)
            }
        }
    }

    private fun parseJsonPayload(data: ByteArray) {
       try {
            val jsonStr = data.decodeToString()
            Log.d(TAG, " < parseJsonPayload: $jsonStr" )

            val jsonElement = json.parseToJsonElement(jsonStr).jsonObject
            val typeString = jsonElement["TypeString"]?.jsonPrimitive?.content ?: return

            val message = when {
                typeString == "InterfaceVersionMessage" ->
                    json.decodeFromString<InterfaceVersionMessage>(jsonStr)
                typeString == "SessionStateMessage" ->
                    json.decodeFromString<SessionStateMessage>(jsonStr)
                typeString == "AssignPlayerIDAndSlotMessage" ->
                    json.decodeFromString<AssignPlayerIDAndSlotMessage>(jsonStr)
                typeString == "ResourceRequirementsMessage" ->
                    json.decodeFromString<ResourceRequirementsMessage>(jsonStr)
                typeString.contains("ServerAvatarStatusMessage") ->
                    json.decodeFromString<ServerAvatarStatusMessage>(jsonStr)
                typeString.contains("ServerAvatarRequestResponseMessage") ->
                    json.decodeFromString<ServerAvatarRequestResponseMessage>(jsonStr)
                typeString.contains("ClientQuizCommandMessage") ->
                    json.decodeFromString<ClientQuizCommandMessage>(jsonStr)
                typeString == "ClientGameIDMessage" ->
                    json.decodeFromString<ClientGameIDMessage>(jsonStr)
                typeString == "ClientHoldingScreenCommandMessage" ->
                    json.decodeFromString<ClientHoldingScreenCommandMessage>(jsonStr)
                typeString == "PlayerJoinedMessage" ->
                    json.decodeFromString<PlayerJoinedMessage>(jsonStr)
                typeString == "PlayerLeftMessage" ->
                    json.decodeFromString<PlayerLeftMessage>(jsonStr)
                typeString == "PlayerNameQuizStateMessage" ->
                    json.decodeFromString<PlayerNameQuizStateMessage>(jsonStr)
                typeString.contains("ServerColourMessage") ->
                    json.decodeFromString<ServerColourMessage>(jsonStr)
                typeString.contains("ServerCategorySelectChoices") ->
                    json.decodeFromString<ServerCategorySelectChoices>(jsonStr)
                typeString.contains("ServerRequestCategorySelectChoice") ->
                    json.decodeFromString<ServerRequestCategorySelectChoice>(jsonStr)
                typeString.contains("ServerBeginCategorySelectOverride") ->
                    json.decodeFromString<ServerBeginCategorySelectOverride>(jsonStr)
                typeString.contains("ServerStopCategorySelectOverride") ->
                    json.decodeFromString<ServerStopCategorySelectOverride>(jsonStr)
                typeString.contains("ServerCategorySelectOverrideSuccess") ->
                    json.decodeFromString<ServerCategorySelectOverrideSuccess>(jsonStr)
                typeString.contains("ServerBeginTriviaAnsweringPhase") ->
                    json.decodeFromString<ServerBeginTriviaAnsweringPhase>(jsonStr)
                typeString == "ImageResourceContentTransferMessage" ->
                    json.decodeFromString<ImageResourceContentTransferMessage>(jsonStr)
                else -> null
            }

            // Handle image transfer matching - when control message arrives, check for pending JPEG
            if (message is ImageResourceContentTransferMessage) {
                val pendingJpeg = pendingImages.remove(message.TransferID)
                if (pendingJpeg != null) {
                    Log.d(TAG, "Matched JPEG for transferId=${message.TransferID}, imageGuid=${message.ImageGUID}")
                    for (i in pendingJpeg.indices step 800) {
                        val chunk = pendingJpeg.copyOfRange(i, minOf(i + 800, pendingJpeg.size))
                        Log.d(TAG, "JPEG[$i]: ${Base64.getEncoder().encodeToString(chunk)}")
                    }
                    onImageReceived?.invoke(message.ImageGUID, pendingJpeg)
                } else {
                    Log.d(TAG, "No pending JPEG for transferId=${message.TransferID}, storing control to wait for JPEG")
                    pendingImageControls[message.TransferID] = message.ImageGUID
                }
            } else {
                message?.let {
                    handleProtocolMessage(it)
                    onMessageReceived?.invoke(it)
                }
            }

            if(message == null){
                Log.d(TAG, "Unknown type: $typeString")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing JSON: ${data.toHexString()}: ${e.stackTraceToString()}")
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