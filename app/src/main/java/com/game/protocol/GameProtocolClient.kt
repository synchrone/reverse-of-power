@file:OptIn(InternalSerializationApi::class)

package com.game.protocol

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
data class ResourceRequirementsMessage(
    override val TypeString: String = "ResourceRequirementsMessage",
    val Requirements: List<String>
) : GameMessage()

@Serializable
data class AllResourcesReceivedMessage(
    override val TypeString: String = "AllResourcesReceivedMessage",
    val Requirements: List<String>
) : GameMessage()

@Serializable
data class ClientQuizCommandMessage(
    override val TypeString: String = "ClientQuizCommandMessage",
    val action: Int, // 14 = show ready button; 31 = go back to name selection screen; 29, 30 = something around game being exited
    val time: Double
) : GameMessage()

@Serializable
data class ServerAvatarStatusMessage(
    override val TypeString: String = "KnowledgeIsPower.ServerAvatarStatusMessage",
    val AvatarID: String,
    val Available: Boolean
) : GameMessage()

@Serializable
data class ClientRequestAvatarMessage(
    override val TypeString: String = "KnowledgeIsPower.ClientRequestAvatarMessage",
    val RequestID: String,
    val AvatarID: String,
    val Request: Boolean
) : GameMessage()

@Serializable
data class ServerAvatarRequestResponseMessage(
    override val TypeString: String = "KnowledgeIsPower.ServerAvatarRequestResponseMessage",
    val RequestID: String,
    val AvatarID: String,
    val Available: Boolean
) : GameMessage()

@Serializable
data class ClientRequestAvatarStatusMessage(
    override val TypeString: String = "KnowledgeIsPower.ClientRequestAvatarStatusMessage"
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
    @kotlinx.serialization.Transient
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
    val action: Int, // 5 = look at the TV
    val time: Double,
    val HoldingScreenText: String,
    val HoldingScreenType: Int,
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

    // Temporary storage for incoming JPEG payloads, keyed by TransferID
    private val pendingImages = mutableMapOf<Int, ByteArray>()
    // Pending control messages when they arrive before the JPEG
    private val pendingImageControls = mutableMapOf<Int, String>() // TransferID -> ImageGUID

    // ==================== Connection ====================

    suspend fun connect(timeoutMs: Long = 5000): Boolean {
        connectionDeferred = CompletableDeferred()
        connectionError = null
        startListening()
        Thread {
            while (serverSocket?.isClosed == false) {
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

    private fun sendConnectionRequest() {
        val data = ByteArray(38)
        data[0] = 0x8a.toByte()
        data[1] = 0x33.toByte()
        data[2] = 0xff.toByte()
        data[3] = 0xff.toByte()
        data[4] = 0xff.toByte()
        data[5] = 0xff.toByte()
        sendRawUDP(data)
    }

    public fun sendDeviceUID(uid: String, theByte: Byte = 0x00) {
        val uidBytes = uid.toByteArray(Charsets.UTF_8)
        val data = ByteBuffer.allocate(12+uidBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        data.put(bytes(0xc, 0x89, 0xe8, 0x84))
        data.put(bytes(0x61, 0x3, 0xf4))
        data.put(theByte)
        data.putInt(uidBytes.size)
        data.put(uidBytes)
        sendRawUDP(data.array())
    }

    // ==================== Session Management ====================

    fun requestPlayerID() {
        val msg = ClientRequestPlayerIDMessage(UID = deviceUID)
        sendMessage(msg)
    }

    fun sendAllResourcesReceived() {
        val msg = AllResourcesReceivedMessage(Requirements = emptyList())
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
            ImgType = imgType
        )
        sendMessage(msg)

        Log.i(TAG, "> Sending transfer ID: $transferId for $imageGuid (${imageData.size}b)")
        val payloadBuffer = ByteBuffer.allocate(imageData.size + 16).order(ByteOrder.LITTLE_ENDIAN)
        payloadBuffer.put(bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x33, 0x29))
        payloadBuffer.putInt(transferId)
        payloadBuffer.putInt(imageData.size)
        payloadBuffer.put(imageData)
        sendChunked(payloadBuffer.array())
    }

    private fun sendChunked(payload: ByteArray) {
        // Fragment and send image data
        val fragmentSize = 1008
        val totalFragments = ((payload.size + fragmentSize - 1) / fragmentSize)
        val messageId = messageCounter++

        for (i in 0 until totalFragments) {
            val start = i * fragmentSize
            val end = minOf(start + fragmentSize, payload.size)
            val fragment = payload.sliceArray(start until end)

            if (i < totalFragments-1){
                send(fragment, totalFragments, i, 0x03, messageId)
            } else {
                send(fragment, totalFragments, i, 0x00, messageId)
            }
            Thread.sleep(10) // Small delay between fragments
        }
    }

    // ==================== Core Messaging ====================
    private fun send(payload: ByteArray, packetNum: Int = 1, packetIdx: Int = 0, flags: Byte = 0x0, messageId: Byte? = null, length: Int = 0){
        val buffer = ByteBuffer.allocate(payload.size+16).order(ByteOrder.BIG_ENDIAN)
        buffer.put(bytes(0xAE, 0x7F))
        if(messageId != null){
            buffer.put(messageId)
        }else{
            buffer.put(++messageCounter)
        }
        buffer.putInt(packetNum)
        buffer.putInt(packetIdx)
        buffer.putInt(if (flags == 3.toByte()) 234 else if (length > 0) length else payload.size) // 234 is 0xEA which seems to be a placeholder when flags=3
        buffer.put(flags)
        buffer.put(payload)
        sendRawUDP(buffer.array())
    }

    private inline fun <reified T : GameMessage> sendMessage(msg: T) {
        val jsonStr = json.encodeToString(msg)
        Log.i(TAG, "> Sending message: $jsonStr")
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(jsonBytes.size + 16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x33, 0x29)) // ???
        buffer.put(bytes(0xB1, 0xE2, 0xFF, 0xFF)) // json type
        buffer.putInt(jsonBytes.size)
        buffer.put(jsonBytes)
        val payload = buffer.array()
        send(payload, length = payload.size - 6) // send a simple packet, non chunked, and don't count the zeros towards payload size
    }

    private fun sendAck(messageId: Byte) {
//        Log.d(TAG, "> ACK for $messageId")
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
            data.asIterable().chunked(800).forEachIndexed { i, chunk ->
                Log.d(TAG, "> ${data.size}b [$i]: ${chunk.toByteArray().toHex()}")
            }
        }
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
                handleDataPacket(data)
            }
            header == 0x8a.toByte() && secondaryHeader == 0x33.toByte() -> {
                if(data.sliceArray(2 .. 5).contentEquals(bytes(0xff, 0xff, 0xff, 0xff))){
                    Log.d(TAG, "< 0x8a, 0x33, 0xFF (4)")
                    isConnected = true;
                    connectionDeferred?.complete(Unit)
                }else {
                    Log.d(TAG, "< ACK for ${data[2].toHexString()}")
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
        val totalLength = buffer.int // length of this packet

        if (packetNum>1) { //chunked transmission
            Log.d(TAG, "< fragmented packet ${messageId.toHexString()} (${packetIdx+1}/$packetNum) l=$totalLength")
            if(packetIdx == 0){ // buffer first packet
                // drag Flags
                handleFragmentedPacket(messageId, packetIdx, packetNum, data.sliceArray(15 until data.size));
            }else{
                // push the rest after without the header for gluing after the first packet
                // continuation packets only have a 16-byte header and a 6-byte subheader, totaling 22b
                handleFragmentedPacket(messageId, packetIdx, packetNum, data.sliceArray(22 until data.size))
            }
            return
        }

        return handleData(messageId, data.sliceArray(15 until data.size))
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
        val flags = buffer.get().toInt()
        val h1 = buffer.int // always zeros
        val h2 = buffer.int // always 0x00, 0x00, 0x33, 0x29
        var payloadType = buffer.int
        var payloadLength = buffer.int
        Log.d(TAG, "RCV ${messageId.toHexString()}, flags=${flags}: h1=$h1, h2=${h2.toHexString()}, type=${payloadType.toHexString()}, len=${payloadLength}")

        val peek4 = data.sliceArray(buffer.position() until buffer.position() + 4);

        if(payloadType == -7503){ // 0xB1, 0xE2, 0xFF, 0xFF // JSON
            return parseJsonPayload(data.sliceArray(buffer.position() until data.size));
        }
        else if(peek4.contentEquals(bytes(0x84, 0x12, 0x40, 0xEE)))
        {
            val sh1 = buffer.int
            var processed = 4 // advance for read sh1
            while (processed < payloadLength-3) { // TODO: there's some byte counting error here ...
                var doclen = buffer.int;
                var doctype = buffer.int; // 0xB1, 0xE2, 0xFF, 0xFF for json
                if(doctype == -7503) {
                    parseJsonPayload(data.sliceArray(buffer.position() until buffer.position() + doclen))
                }else {
                    throw NotImplementedError("sh1=${sh1.toHexString()}, doctype ${doctype.toHexString()} is not implemented");
                }
                processed += doclen + 8;
                buffer.position(buffer.position() + doclen)
            }
            buffer.get() // 0x32 finish
        }
        else if(peek4.sliceArray(0..1).contentEquals(bytes(0xFF, 0xD8))) // JPEG
        {
            val transferId = payloadType
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
            throw NotImplementedError("flags ${flags.toHexString()}, payloadType ${payloadType.toHexString()} not implemented");
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
            }else{
                message?.let { onMessageReceived?.invoke(it) }
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