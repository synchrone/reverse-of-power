@file:OptIn(InternalSerializationApi::class)

package com.game.protocol

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProtocolEncoder {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    var messageCounter: Int = 0

    // ==================== Raw Packets (no 0xAE7F header) ====================

    fun encodeConnectionRequest(): ByteArray {
        val data = ByteArray(38)
        data[0] = 0x8a.toByte()
        data[1] = 0x33.toByte()
        data[2] = 0xff.toByte()
        data[3] = 0xff.toByte()
        data[4] = 0xff.toByte()
        data[5] = 0xff.toByte()
        return data
    }

    fun encodeDeviceUID(uid: String, theByte: Byte = 0x63): ByteArray {
        val uidBytes = uid.toByteArray(Charsets.UTF_8)
        val data = ByteBuffer.allocate(12 + uidBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        data.put(bytes(0xc, 0x89, 0xe8, 0x84))
        data.put(bytes(0x61, 0x3, 0xf4))
        data.put(theByte)
        data.putInt(uidBytes.size)
        data.put(uidBytes)
        return data.array()
    }

    fun encodeAck(messageId: Int): ByteArray {
        val buffer = ByteBuffer.allocate(38).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x8a.toByte())
        buffer.put(0x33.toByte())
        buffer.putInt(messageId) // ACK uses low byte only
        buffer.put(ByteArray(32))
        return buffer.array()
    }

    // ==================== Framed Packets (0xAE7F header) ====================

    /**
     * Encode a single GameMessage as one or more UDP packets (fragmented if needed).
     */
    fun encodeMessage(msg: GameMessage): List<ByteArray> {
        val jsonBytes = encodeJson(msg)
        val body = ByteBuffer.allocate(jsonBytes.size + 10).order(ByteOrder.LITTLE_ENDIAN)
        body.put(bytes(0x33, 0x29)) // magic
        body.put(bytes(0xB1, 0xE2, 0xFF, 0xFF)) // json type
        body.putInt(jsonBytes.size)
        body.put(jsonBytes)
        return wrapAndChunk(body.array())
    }

    /**
     * Encode multiple GameMessages as one multi-JSON UDP packet.
     */
    fun encodeMessageMulti(msgs: List<GameMessage>, transferId: Int): ByteArray {
        val jsonBytesList = msgs.map { encodeJson(it) }
        val entriesSize = jsonBytesList.sumOf { 8 + it.size }
        val bodyLength = entriesSize + 6

        val buffer = ByteBuffer.allocate(10 + bodyLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(bytes(0x33, 0x29)) // magic
        buffer.putInt(transferId)
        buffer.putInt(bodyLength)
        buffer.put(bytes(0x84, 0x12, 0x40, 0xEE)) // multijson type
        for (jsonBytes in jsonBytesList) {
            buffer.putInt(jsonBytes.size)
            buffer.put(bytes(0xB1, 0xE2, 0xFF, 0xFF)) // json type
            buffer.put(jsonBytes)
        }
        buffer.put(bytes(0x32, 0x90)) // footer
        return wrapPayload(buffer.array())
    }

    /**
     * Encode an image transfer as a list of UDP packets:
     * first the control JSON message, then the chunked JPEG data.
     */
    fun encodeImageTransfer(imageData: ByteArray, imageGuid: String, transferId: Int, imgType: Int = 2): List<ByteArray> {
        val controlMsg = ClientImageResourceContentTransferMessage(
            TransferID = transferId,
            ImageGUID = imageGuid,
            ImgType = imgType
        )
        val controlPackets = encodeMessage(controlMsg)

        val payloadBuffer = ByteBuffer.allocate(imageData.size + 10).order(ByteOrder.LITTLE_ENDIAN)
        payloadBuffer.put(bytes(0x33, 0x29))  // magic
        payloadBuffer.putInt(transferId)
        payloadBuffer.putInt(imageData.size)
        payloadBuffer.put(imageData)

        val imagePackets = wrapAndChunk(payloadBuffer.array())
        return controlPackets + imagePackets
    }

    // ==================== Internal ====================

    @Suppress("UNCHECKED_CAST")
    fun encodeJson(msg: GameMessage): ByteArray {
        val serializer = msg::class.serializer() as KSerializer<GameMessage>
        return json.encodeToString(serializer, msg).toByteArray(Charsets.UTF_8)
    }

    /**
     * Wrap a payload with the 22-byte protocol header as a single packet.
     */
    private fun wrapPayload(payload: ByteArray, messageId: Int = ++messageCounter): ByteArray {
        return buildPacket(payload, packetNum = 1, packetIdx = 0, messageId = messageId, byteOffset = 0)
    }

    /**
     * Fragment a payload into multiple packets if needed, each with a 22-byte header.
     */
    private fun wrapAndChunk(payload: ByteArray): List<ByteArray> {
        val fragmentSize = 1002  // Max payload per chunk (1024 total - 22 byte header)
        val totalFragments = (payload.size + fragmentSize - 1) / fragmentSize
        val messageId = ++messageCounter

        val packets = mutableListOf<ByteArray>()
        var byteOffset = 0
        for (i in 0 until totalFragments) {
            val start = i * fragmentSize
            val end = minOf(start + fragmentSize, payload.size)
            val fragment = payload.sliceArray(start until end)
            packets.add(buildPacket(fragment, totalFragments, i, messageId, byteOffset))
            byteOffset += fragment.size
        }
        return packets
    }

    /**
     * Build a single protocol packet: 22-byte header + payload.
     */
    private fun buildPacket(payload: ByteArray, packetNum: Int, packetIdx: Int, messageId: Int, byteOffset: Int): ByteArray {
        val buffer = ByteBuffer.allocate(payload.size + 22).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(bytes(0xAE, 0x7F))
        buffer.putInt(messageId)
        buffer.putInt(packetNum)
        buffer.putInt(packetIdx)
        buffer.putInt(payload.size)
        buffer.putInt(byteOffset)
        buffer.put(payload)
        return buffer.array()
    }
}
