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
public fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

public class GameProtocolClient(
    private val deviceUID: String,
    private val serverHostStr: String,
    private val serverPort: Int = 9066,
    private val listenHostStr: String = "0.0.0.0",
    private val listenPort: Int = 9060
    ) {
    private var isConnected: Boolean = false
    private var serverHost: InetAddress? = null
    private var serverSocket: DatagramSocket? = null
    private var clientSocket: DatagramSocket? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var messageCounter: Byte = 0
    private var lastRcvMessageId: Byte? = null
    private val fragmentBuffer = mutableMapOf<Byte, MutableMap<Int, ByteArray>>()

    var onMessageReceived: ((GameMessage) -> Unit)? = null
    var onAvatarListReceived: ((List<ServerAvatarStatusMessage>) -> Unit)? = null

    // ==================== Connection ====================

    fun connect() {
        startListening()
        Thread {
            while (true) {
                if (lastRcvMessageId == null) {
                    sendConnectionRequest()
                    sendDeviceUID(deviceUID)
                } else {
                    sendAck(lastRcvMessageId!!)
                }
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

    // ==================== Image Transfer ====================

    fun sendImage(imageData: ByteArray, imageGuid: String, transferId: Int, imgType: Int = 2) {
        // Send image transfer message first
        val msg = ClientImageResourceContentTransferMessage(
            TransferID = transferId,
            ImageGUID = imageGuid,
            ImgType = imgType
        )
        sendMessage(msg)

        val payloadBuffer = ByteBuffer.allocate(imageData.size + 16).order(ByteOrder.LITTLE_ENDIAN)
        payloadBuffer.putInt(0)
        payloadBuffer.put(bytes(0x00, 0x00, 0x33, 0x29))
        payloadBuffer.putInt(transferId)
        payloadBuffer.putInt(imageData.size)
        payloadBuffer.put(imageData)
        sendChunked(payloadBuffer.array())
    }

    private fun sendChunked(payload: ByteArray) {
        // Fragment and send image data
        val fragmentSize = 1024
        val totalFragments = ((payload.size + fragmentSize - 1) / fragmentSize)

        for (i in 0 until totalFragments) {
            val start = i * fragmentSize
            val end = minOf(start + fragmentSize, payload.size)
            val fragment = payload.sliceArray(start until end)

            if (i < totalFragments-1){
                send(fragment, totalFragments, i, 0x03)
            } else {
                send(fragment, totalFragments, i, 0x00)
            }
            Thread.sleep(10) // Small delay between fragments
        }
    }

    // ==================== Core Messaging ====================
    private fun send(payload: ByteArray, packetNum: Int = 1, packetIdx: Int = 0, flags: Byte = 0x0, length: Int = 0){
        val buffer = ByteBuffer.allocate(length+16).order(ByteOrder.BIG_ENDIAN)
        buffer.put(0xae.toByte())
        buffer.put(0x7f.toByte())
        buffer.put(messageCounter++)
        buffer.putInt(packetNum)
        buffer.putInt(packetIdx)
        buffer.putInt(if (flags == 3.toByte()) 234 else if (length > 0) length else payload.size) // 234 is 0xEA which seems to be a placeholder for flags=3
        buffer.put(flags)
        buffer.put(payload)
        sendRawUDP(buffer.array())
    }

    private inline fun <reified T : GameMessage> sendMessage(msg: T) {
        val jsonStr = json.encodeToString(msg)
        println("> Sending message: $jsonStr")
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(jsonBytes.size + 16).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0) // zeros
        buffer.put(bytes(0x00, 0x00, 0x33, 0x29)) // ???
        buffer.put(bytes(0xB1, 0xE2, 0xFF, 0xFF)) // json type
        buffer.order(ByteOrder.LITTLE_ENDIAN).putInt(jsonBytes.size)
        buffer.put(jsonBytes)
        sendRawUDP(buffer.array()) // send a simple packet, non chunked
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
//        println("> sending ${data.size}b: ${data.toHex()}")
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
                if(data.sliceArray(2 .. 5).contentEquals(bytes(0xff, 0xff, 0xff, 0xff))){
                    isConnected = true;
                }else {
                    println("< ACK for ${data[3].toHexString()}")
                }
            }
            else -> {
                // Unknown p8a,et type
                println("Unknown packet type: ${data.toHexString()}")
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

        lastRcvMessageId = messageId

        if (packetNum>1) { //chunked transmission
            val h1 = buffer.short
            val h2 = buffer.int
            println("< fragmented packet ${messageId.toHexString()} (${packetIdx+1}/$packetNum) l=$totalLength: h1=$h1, h2=${h2.toHexString()}")
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
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.get().toInt()
        val h1 = buffer.int // always zeros
        val h2 = buffer.int // always 0x00, 0x00, 0x33, 0x29
        var payloadType = buffer.int
        var payloadLength = buffer.int
        println("RCV ${messageId.toHexString()}, flags=${flags}: h1=$h1, h2=$h2, type=${payloadType.toHexString()}, len=${payloadLength}")

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
            val imageId = payloadType;
            val jpeg = data.sliceArray(buffer.position() until data.size)
            val file = File("$messageId.jpeg")
            file.writeBytes(jpeg)
            println("^ wrote ${file.absolutePath}")
        }else{
            throw NotImplementedError("flags ${flags.toHexString()}, payloadType ${payloadType.toHexString()} not implemented");
        }
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
//            println(jsonStr)

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
        joinToString(", ") { "0x%x".format(it) }

}