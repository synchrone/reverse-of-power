@file:OptIn(InternalSerializationApi::class)

package com.game.protocol

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
    val action: Int,
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

class GameProtocolClient(
    serverHostStr: String,
    private val serverPort: Int = 9066,
    listenHostStr: String = "0.0.0.0",
    listenPort: Int = 9060
    ) {

    private val serverHost: InetAddress
    private val serverSocket: DatagramSocket
    private val clientSocket: DatagramSocket

    init {
        // Now create sockets with IPv4
        serverHost = InetAddress.getByName(serverHostStr)
        serverSocket = DatagramSocket(0, Inet4Address.getByName("0.0.0.0") as InetAddress)
        clientSocket = DatagramSocket(listenPort, Inet4Address.getByName(listenHostStr) as InetAddress)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var messageCounter: Short = 0
    private val fragmentBuffer = mutableMapOf<Byte, MutableMap<Int, ByteArray>>()

    var onMessageReceived: ((GameMessage) -> Unit)? = null
    var onAvatarListReceived: ((List<ServerAvatarStatusMessage>) -> Unit)? = null

    // ==================== Connection ====================

    fun connect(deviceUID: String) {
        startListening()
        sendConnectionRequest()
        sendDeviceUID(deviceUID)
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

    private fun sendDeviceUID(uid: String) {
        val uidBytes = uid.toByteArray(Charsets.UTF_8)
        val data = ByteArray(44)
        data[0] = 0x0c.toByte()
        data[1] = 0x89.toByte()
        data[2] = 0xe8.toByte()
        data[3] = 0x84.toByte()
        data[4] = 0x61.toByte()
        data[5] = 0x03.toByte()
        data[6] = 0xf4.toByte()
        data[7] = 0x69.toByte()
        data[8] = 0x20.toByte()

        System.arraycopy(uidBytes, 0, data, 12, uidBytes.size.coerceAtMost(32))
        sendRawUDP(data)
    }

    // ==================== Session Management ====================

    fun requestPlayerID(deviceUID: String) {
        val msg = ClientRequestPlayerIDMessage(UID = deviceUID)
        sendMessage(msg)
    }

    fun sendAllResourcesReceived() {
        val msg = AllResourcesReceivedMessage(Requirements = emptyList())
        sendMessage(msg)
    }

    fun sendDeviceInfo(deviceUID: String, model: String, os: String) {
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

    // ==================== Image Transfer ====================

    fun sendImage(imageData: ByteArray, imageGuid: String, transferId: Int, imgType: Int = 2) {
        // Send image transfer message first
        val msg = ClientImageResourceContentTransferMessage(
            TransferID = transferId,
            ImageGUID = imageGuid,
            ImgType = imgType
        )
        sendMessage(msg)

        // Fragment and send image data
        val fragmentSize = 1024
        val totalFragments = ((imageData.size + fragmentSize - 1) / fragmentSize).toShort()

        for (i in 0 until totalFragments) {
            val start = i * fragmentSize
            val end = minOf(start + fragmentSize, imageData.size)
            val fragment = imageData.sliceArray(start until end)

            sendFragmentedData(i.toShort(), totalFragments, fragment)
            Thread.sleep(10) // Small delay between fragments
        }
    }

    private fun sendFragmentedData(packetNum: Short, totalPackets: Short, data: ByteArray) {
        val buffer = ByteBuffer.allocate(1024 + 42).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0xae.toByte())
        buffer.put(0x7f.toByte())
        buffer.putShort(messageCounter++)
        buffer.putShort(0x0011) // Fragment flag
        buffer.putShort(packetNum)
        buffer.putShort(0x0000)
        buffer.putLong(0x00000000000000ea.toLong())
        buffer.putLong(0x0300000000000000.toLong())
        buffer.put(0x33.toByte())
        buffer.put(0x29.toByte())

        // Add data
        buffer.put(data)

        val packet = buffer.array().sliceArray(0 until (data.size + 42))
        sendRawUDP(packet)
    }

    // ==================== Core Messaging ====================

    private inline fun <reified T : GameMessage> sendMessage(msg: T) {
        val jsonStr = json.encodeToString(msg)
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(jsonBytes.size + 42).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0xae.toByte())
        buffer.put(0x7f.toByte())
        buffer.putShort(messageCounter++)
        buffer.putShort(0x0001) // Single packet
        buffer.putShort(0x0000)
        buffer.putLong(0)
        buffer.putLong((jsonBytes.size + 16).toLong())
        buffer.putLong(0)
        buffer.put(0x33.toByte())
        buffer.put(0x29.toByte())
        buffer.put(0xb1.toByte())
        buffer.put(0xe2.toByte())
        buffer.put(0xff.toByte())
        buffer.put(0xff.toByte())
        buffer.putInt(jsonBytes.size)
        buffer.put(jsonBytes)

        sendRawUDP(buffer.array())
    }

    private fun sendAck(messageId: Byte) {
        val ack = AckPacket(messageId = messageId)
        val buffer = ByteBuffer.allocate(38).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(ack.header)
        buffer.put(ack.secondaryHeader)
        buffer.put(ack.messageId)
        buffer.put(ack.padding)

        sendRawUDP(buffer.array())
    }

    private fun sendRawUDP(data: ByteArray) {
        println("sending ${data.size}b to ${serverHost}:${serverPort} hex: ${data.joinToString("") { "%02x".format(it) }}")
        try {
            val packet = DatagramPacket(
                data,
                data.size,
                serverHost,
                serverPort
            )
            serverSocket.send(packet)
        } catch (e: Exception) {
            println("  -> ERROR sending: ${e.message}")
            e.printStackTrace()
        }
    }

    // ==================== Receiving ====================

    private fun startListening() {
        Thread {
            val buffer = ByteArray(2048)
            while (true) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    clientSocket.receive(packet)
                    handleReceivedPacket(packet.data.sliceArray(0 until packet.length))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    public fun handleReceivedPacket(data: ByteArray) {
        if (data.size < 2) return

        val header = data[0]
        val secondaryHeader = data[1]

        when {
            header == 0xae.toByte() && secondaryHeader == 0x7f.toByte() -> {
                handleDataPacket(data)
            }
            header == 0x8a.toByte() && secondaryHeader == 0x33.toByte() -> {
                // ACK packet - can be logged or ignored
            }
            else -> {
                // Unknown p8a,et type
                println("Unknown packet type: ${data.toHex()}")
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
        val flags = buffer.get() // 16th byte

        sendAck(messageId)

        if(flags == 3.toByte() || packetIdx == packetNum - 1){ // multipacket or last packet
            if(packetIdx == 0){ // buffer first packet with 2nd header
                handleFragmentedPacket(messageId, packetIdx, packetNum, data);
            }else{
                // push the rest after without the header for gluing after the first packet
                handleFragmentedPacket(messageId, packetIdx, packetNum, data.sliceArray(17 until data.size));
            }
            return
        }

        val h1 = buffer.int // always zero
        val h2 = buffer.int // always 0x00, 0x00, 0x33, 0x29
        val payloadType = buffer.int
        val payloadLength = buffer.order(ByteOrder.LITTLE_ENDIAN).int

        println("RPC ${messageId}/${packetNum}pieces, ${flags}: type=${payloadType.toHexString()}: ${payloadLength}b")

        if (flags == 0.toByte()){ // simple payload
            val fragment = data.sliceArray(32 until data.size)
            if(payloadType == -1310523393 ) { // 0xB1, 0xE2, 0xFF, 0xFF = json
                return parseJsonPayload(fragment)
            }else if (payloadType < 10) { // 5,6,7,8 made sense with 0x03 packet flag = jpeg
                throw NotImplementedError();
            }else{
                throw NotImplementedError();
            }
        }else if(flags == 1.toByte()){ // multi-payload
            val datatype = buffer.int
            if(datatype == -297790844){ //0x84, 0x12, 0x40, 0xEE = multijson
                var processed = 4; // advance for read datatype var
                while (processed < payloadLength-3) { // TODO: there's some byte counting error here ...
                    var doclen = buffer.order(ByteOrder.LITTLE_ENDIAN).int;
                    var doctype = buffer.int; // 0xB1, 0xE2, 0xFF, 0xFF for json
                    parseJsonPayload(data.sliceArray(buffer.position() until buffer.position() + doclen))
                    processed += doclen + 8;
                    buffer.position(buffer.position() + doclen)
                }
                buffer.get() // 0x32 finish
            }else{
                throw NotImplementedError();
            }
        }
    }

    private fun handleFragmentedPacket(messageId: Byte, packetNum: Int, totalPackets: Int, fragment: ByteArray) {
        if (!fragmentBuffer.containsKey(messageId)) {
            fragmentBuffer[messageId] = mutableMapOf()
        }

        fragmentBuffer[messageId]!![packetNum] = fragment

        // Check if all fragments received
        if (fragmentBuffer[messageId]!!.size == totalPackets.toInt()) {
            val completeData = reassembleFragments(messageId, totalPackets.toInt())
            fragmentBuffer.remove(messageId)

            // This is image data, not JSON
            println("Received complete image data: ${completeData.size} bytes")
            handleDataPacket(completeData);
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

    private fun parseJsonPayload(data: ByteArray) {
        // Skip protocol headers to find JSON
        var jsonStart = -1
        for (i in data.indices) {
            if (data[i] == '{'.code.toByte()) {
                jsonStart = i
                break
            }
        }

        if (jsonStart == -1) return

        try {
            val jsonStr = String(data.sliceArray(jsonStart until data.size), Charsets.UTF_8)
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
                else -> null
            }

            message?.let { onMessageReceived?.invoke(it) }

            // Handle avatar list (multiple messages in one packet)
            if (typeString.contains("ServerAvatarStatusMessage")) {
                parseMultipleAvatarMessages(data)
            }

        } catch (e: Exception) {
            println("Error parsing JSON: ${e.message}")
        }
    }

    private fun parseMultipleAvatarMessages(data: ByteArray) {
        val avatars = mutableListOf<ServerAvatarStatusMessage>()
        var offset = 0

        while (offset < data.size) {
            val jsonStart = data.indexOf('{'.code.toByte(), offset)
            if (jsonStart == -1) break

            val jsonEnd = data.indexOf('}'.code.toByte(), jsonStart)
            if (jsonEnd == -1) break

            try {
                val jsonStr = String(data.sliceArray(jsonStart..jsonEnd), Charsets.UTF_8)
                val msg = json.decodeFromString<ServerAvatarStatusMessage>(jsonStr)
                avatars.add(msg)
                offset = jsonEnd + 1
            } catch (e: Exception) {
                break
            }
        }

        if (avatars.isNotEmpty()) {
            onAvatarListReceived?.invoke(avatars)
        }
    }

    fun close() {
        serverSocket.close()
    }

    // ==================== Utilities ====================

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun ByteArray.indexOf(byte: Byte, startIndex: Int = 0): Int {
        for (i in startIndex until size) {
            if (this[i] == byte) return i
        }
        return -1
    }
}

// ==================== Usage Example ====================

fun main() {
    val deviceUID = "b2f3f8eb0cf4ef4b2359871d35495225"
    val client = GameProtocolClient("192.168.0.14")
    println("Game protocol started")

    client.onMessageReceived = { message ->
        when (message) {
            is InterfaceVersionMessage -> {
                println("Connected to server version: ${message.InterfaceVersion}")
//                client.requestPlayerID(deviceUID)
            }
            is SessionStateMessage -> {
                println("Session ID: ${message.SessionID}")
            }
            is AssignPlayerIDAndSlotMessage -> {
                println("Assigned Player ID: ${message.PlayerID}, Slot: ${message.SlotID}")
                println("Display Name: ${message.DisplayName}")
//                client.sendAllResourcesReceived()
//                client.sendDeviceInfo(
//                    deviceUID,
//                    "Genymobile Pixel 9",
//                    "Android OS 11 / API-30 (RQ1A.210105.003/857)"
//                )
//                client.requestAvatarStatus()
            }
            is ServerAvatarRequestResponseMessage -> {
                println("Avatar ${message.AvatarID} - Available: ${message.Available}")
                if (message.Available) {
                    // Avatar acquired, send profile
//                    client.sendPlayerProfile("test", message.AvatarID)
                }
            }
            else -> {
                println("Received: $message")
            }
        }
    }

    client.onAvatarListReceived = { avatars ->
        println("Available avatars:")
        avatars.forEach { avatar ->
            println("  - ${avatar.AvatarID}: ${if (avatar.Available) "Available" else "Taken"}")
        }

        // Request first available avatar
        val available = avatars.firstOrNull { it.Available }
        available?.let {
            println("Requesting avatar: ${it.AvatarID}")
            client.requestAvatar(it.AvatarID)
        }
    }

    // Connect to server
    client.connect(deviceUID)

    // Keep running
    Thread.sleep(30000)
    client.close()
}