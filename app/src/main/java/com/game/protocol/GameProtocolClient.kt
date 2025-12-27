@file:OptIn(InternalSerializationApi::class)

package com.game.protocol

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
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
private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

class GameProtocolClient(
    private val deviceUID: String,
    private val serverHostStr: String,
    private val serverPort: Int = 9066,
    private val listenHostStr: String = "0.0.0.0",
    private val listenPort: Int = 9060
    ) {

    private val randomSeed: Byte = Random().nextInt(0, 255).toByte()
    private var isConnected: Boolean = false
    private var serverHost: InetAddress? = null
    private var serverSocket: DatagramSocket? = null
    private var clientSocket: DatagramSocket? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var messageCounter: Byte = 0
    private val fragmentBuffer = mutableMapOf<Byte, MutableMap<Int, ByteArray>>()

    var onMessageReceived: ((GameMessage) -> Unit)? = null
    var onAvatarListReceived: ((List<ServerAvatarStatusMessage>) -> Unit)? = null

    // ==================== Connection ====================

    fun connect() {
        startListening()
        Thread {
            while (!isConnected) {
                sendConnectionRequest()
                sendDeviceUID(deviceUID)
                Thread.sleep(1000)
            }
        }.start()
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
        throw NotImplementedError()
    }

    // ==================== Core Messaging ====================

    private inline fun <reified T : GameMessage> sendMessage(msg: T) {
        val jsonStr = json.encodeToString(msg)
        println("> Sending message: $jsonStr")
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(jsonBytes.size + 32).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0xae.toByte())
        buffer.put(0x7f.toByte())
        buffer.put(messageCounter++)
        buffer.putInt(1) // one message in packet
        buffer.putInt(0) // packet idx = 0
        buffer.putInt(jsonBytes.size + 16)
        buffer.put(0) // simple packet flag

        buffer.putInt(0) // zeros
        buffer.put(bytes(0x00, 0x00, 0x33, 0x29)) // ???
        buffer.put(bytes(0xB1, 0xE2, 0xFF, 0xFF)) // json type
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

        messageCounter = (messageId + 1).toByte()

        sendRawUDP(buffer.array())
    }

    private fun sendRawUDP(data: ByteArray) {
        println("> sending ${data.size}b: ${data.toHex()}")
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
            while (true) {
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
        if (data.size < 2) return

        val header = data[0]
        val secondaryHeader = data[1]

        when {
            header == 0xae.toByte() && secondaryHeader == 0x7f.toByte() -> {
                handleDataPacket(data)
            }
            header == 0x8a.toByte() && secondaryHeader == 0x33.toByte() -> {
                if(data.sliceArray(4 .. 6).equals(bytes(0xff, 0xff, 0xff, 0xff))){
                    isConnected = true;
                }else {
                    println("< ACK for ${data[3]}")
                }
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

        if (packetNum>1) { //chunked transmission
            val h1 = buffer.short
            val h2 = buffer.int
            println("< fragmented packet $messageId (${packetIdx+1}/$packetNum) l=$totalLength f=$flags: h1=$h1, h2=${h2.toHexString()}")
            if(packetIdx == 0){ // buffer first packet
                // TODO: the last packet arrives with flags = 0, so maybe that's the one that's triggering reassembly and processing.
                //  so we shuoldn't have to do the following hacks:
                byteArrayOf(0x00, 0x00, 0x00, 0x01).copyInto(data, 3) // begin reassembling buffer with packetNum=1
                data[15] = 0x00; //reset flags to trigger simple packet parsing
                handleFragmentedPacket(messageId, packetIdx, packetNum, data);
            }else{
                // push the rest after without the header for gluing after the first packet
                // continuation packets only have a 16-byte header and a 6-byte subheader, totaling 22b
                handleFragmentedPacket(messageId, packetIdx, packetNum, data.sliceArray(22 until data.size))
            }
            return
        }

        val h1 = buffer.int // always zero
        val h2 = buffer.int // always 0x00, 0x00, 0x33, 0x29
        val payloadType = buffer.order(ByteOrder.LITTLE_ENDIAN).int
        val payloadLength = buffer.order(ByteOrder.LITTLE_ENDIAN).int

        println("RPC ${messageId}, flags=${flags}: h1=$h1, h2=$h2, type=${payloadType.toHexString()}, len=${payloadLength}")

        if (flags == 0.toByte()) { // simple payload
            val fragment = data.sliceArray(32 until data.size)
            if(payloadType == -7503 ) { // 0xB1, 0xE2, 0xFF, 0xFF = json
                return parseJsonPayload(fragment)
            }else if (payloadType < 10){
                val jpeg = data.sliceArray(buffer.position() until data.size)
                val file = File("$messageId.jpeg")
                file.writeBytes(jpeg)
                println("^ wrote ${file.absolutePath}")
            }else{
                throw NotImplementedError();
            }
        } else if(flags == 1.toByte()) { // multi-payload
            val datatype = buffer.int
            if (datatype == -297790844) { //0x84, 0x12, 0x40, 0xEE = multijson
                var processed = 4 // advance for read datatype var
                while (processed < payloadLength-3) { // TODO: there's some byte counting error here ...
                    var doclen = buffer.order(ByteOrder.LITTLE_ENDIAN).int;
                    var doctype = buffer.int; // 0xB1, 0xE2, 0xFF, 0xFF for json
                    parseJsonPayload(data.sliceArray(buffer.position() until buffer.position() + doclen))
                    processed += doclen + 8;
                    buffer.position(buffer.position() + doclen)
                }
                buffer.get() // 0x32 finish
            } else {
                throw NotImplementedError();
            }
        } else if(flags == 3.toByte()) {
            // mostly jpegs here, which are always chunked, so the reassembled packets are handled under simple case
        }
    }

    private fun handleFragmentedPacket(messageId: Byte, packetNum: Int, totalPackets: Int, fragment: ByteArray, isFirstPacket: Boolean = false) {
        if (!fragmentBuffer.containsKey(messageId)) {
            fragmentBuffer[messageId] = mutableMapOf()
        }

        fragmentBuffer[messageId]!![packetNum] = fragment

        // Check if all fragments received
        if (fragmentBuffer[messageId]!!.size == totalPackets) {
            val completeData = reassembleFragments(messageId, totalPackets)
            fragmentBuffer.remove(messageId)
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
            println(jsonStr)

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
            if(message == null){
                println("Unknown type: $typeString")
            }
        } catch (e: Exception) {
            println("Error parsing JSON: ${e.message}")
        }
    }

    fun close() {
        serverSocket?.close()
    }

    // ==================== Utilities ====================

    private fun ByteArray.toHex(): String =
        HexFormat.ofDelimiter(",").formatHex(this)

}

// ==================== Usage Example ====================

fun main() {
//    val client = GameProtocolClient("b2f3f8eb0cf4ef4b2359871d35495225","192.168.0.14")
    val client = GameProtocolClient("5ca923a0193251c3b24c46546829519a","192.168.0.14")
    println("Game protocol started")

    client.onMessageReceived = { message ->
        when (message) {
            is InterfaceVersionMessage -> {
                println("Connected to server version: ${message.InterfaceVersion}")
            }
            is SessionStateMessage -> {
                println("Session ID: ${message.SessionID}")
                client.requestPlayerID()
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
                println("< Avatar ${message.AvatarID} - Available: ${message.Available}")
                if (message.Available) {
                    // Avatar acquired, send profile
//                    client.sendPlayerProfile("test", message.AvatarID)
                }
            }
            else -> {
                println("< Received: $message")
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
    client.connect()

    // Keep running
    Thread.sleep(30000)
    client.close()
}