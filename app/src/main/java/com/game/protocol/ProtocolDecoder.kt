package com.game.protocol

import android.util.Log
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ==================== Decoded Packet Types ====================

sealed class DecodedPacket {
    data class Ack(val messageId: Int) : DecodedPacket()
    data class Nack(val messageId: Int, val payload: ByteArray) : DecodedPacket()
    data class ConnectionInit(val raw: ByteArray) : DecodedPacket()
    data class DeviceUID(val uid: String, val raw: ByteArray) : DecodedPacket()
    data class GameInProgress(val raw: ByteArray) : DecodedPacket()
    data class DataMessage(val messageId: Int, val messages: List<GameMessage>) : DecodedPacket()
    data class ImageData(val messageId: Int, val transferId: Int, val jpegSize: Int, val jpeg: ByteArray) : DecodedPacket()
    data class Fragment(val messageId: Int, val idx: Int, val total: Int, val size: Int) : DecodedPacket()
    data class Unknown(val raw: ByteArray) : DecodedPacket()
    data class TooShort(val raw: ByteArray) : DecodedPacket()
    data class Error(val message: String, val raw: ByteArray) : DecodedPacket()
}

// ==================== Protocol Decoder ====================

@OptIn(InternalSerializationApi::class)
class ProtocolDecoder {
    private val TAG = "ProtocolDecoder"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val fragmentBuffer = mutableMapOf<Int, MutableMap<Int, ByteArray>>()

    fun decode(data: ByteArray): DecodedPacket {
        if (data.size < 8) return DecodedPacket.TooShort(data)

        // Check for "game in progress" magic: e558fc895c8df001
        if (data.sliceArray(0..7).contentEquals(
                bytes(0xe5, 0x58, 0xfc, 0x89, 0x5c, 0x8d, 0xf0, 0x01)
            )
        ) {
            return DecodedPacket.GameInProgress(data)
        }

        // Check for device UID packet: 0c89e884
        if (data.size >= 12 &&
            data[0] == 0x0c.toByte() && data[1] == 0x89.toByte() &&
            data[2] == 0xe8.toByte() && data[3] == 0x84.toByte()
        ) {
            return decodeDeviceUID(data)
        }

        val header = data[0]
        val secondaryHeader = data[1]

        return when {
            header == 0xae.toByte() && secondaryHeader == 0x7f.toByte() -> {
                try {
                    decodeDataPacket(data)
                } catch (e: Exception) {
                    DecodedPacket.Error("Error decoding data packet: ${e.message}", data)
                }
            }
            header == 0x8a.toByte() && secondaryHeader == 0x33.toByte() -> {
                if (data.sliceArray(2..5).contentEquals(bytes(0xff, 0xff, 0xff, 0xff))) {
                    DecodedPacket.ConnectionInit(data)
                } else {
                    val payload = data.sliceArray(3 until data.size)
                    if (payload.any { it != 0.toByte() }) {
                        DecodedPacket.Nack(data[2].toInt(), payload)
                    } else {
                        DecodedPacket.Ack(data[2].toInt())
                    }
                }
            }
            else -> DecodedPacket.Unknown(data)
        }
    }

    private fun decodeDeviceUID(data: ByteArray): DecodedPacket {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(8) // skip magic + flags
            val uidLen = buffer.int
            val uidBytes = ByteArray(uidLen)
            buffer.get(uidBytes)
            DecodedPacket.DeviceUID(String(uidBytes, Charsets.UTF_8), data)
        } catch (e: Exception) {
            DecodedPacket.Error("Error decoding DeviceUID: ${e.message}", data)
        }
    }

    private fun decodeDataPacket(data: ByteArray): DecodedPacket {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // 0xae
        buffer.get() // 0x7f
        val messageId = buffer.int
        val packetNum = buffer.int
        val packetIdx = buffer.int
        val packetLen = buffer.int
        val offset = buffer.int

        if (packetNum > 1) {
            val fragment = data.sliceArray(22 until data.size)
            return handleFragment(messageId, packetIdx, packetNum, fragment)
        }

        return decodePayload(messageId, data.sliceArray(22 until data.size))
    }

    private fun handleFragment(messageId: Int, packetIdx: Int, totalPackets: Int, fragment: ByteArray): DecodedPacket {
        if (!fragmentBuffer.containsKey(messageId)) {
            fragmentBuffer[messageId] = mutableMapOf()
        }

        fragmentBuffer[messageId]!![packetIdx] = fragment

        if (fragmentBuffer[messageId]!!.size == totalPackets) {
            val completeData = reassembleFragments(messageId, totalPackets)
            fragmentBuffer.remove(messageId)
            return decodePayload(messageId, completeData)
        }

        return DecodedPacket.Fragment(messageId, packetIdx, totalPackets, fragment.size)
    }

    private fun reassembleFragments(messageId: Int, totalPackets: Int): ByteArray {
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

    fun decodePayload(messageId: Int, data: ByteArray): DecodedPacket {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.getShort() // 0x33, 0x29
        var payloadType = buffer.int
        val payloadLength = buffer.int
        var transferId = -1
        if (payloadType > 0) {
            transferId = payloadType
            payloadType = buffer.int
        }

        return when (payloadType) {
            -7503 -> { // 0xB1E2FFFF - JSON
                val messages = parseJsonPayload(data.sliceArray(buffer.position() until data.size))
                DecodedPacket.DataMessage(messageId, messages)
            }
            -297790844 -> { // 0x841240EE - multi-JSON
                val messages = mutableListOf<GameMessage>()
                var processed = 15
                while (processed < payloadLength - 2) {
                    val doclen = buffer.int
                    val doctype = buffer.int
                    if (doctype == -7503) {
                        messages.addAll(parseJsonPayload(data.sliceArray(buffer.position() until buffer.position() + doclen)))
                    }
                    processed += doclen + 8
                    buffer.position(buffer.position() + doclen)
                }
                DecodedPacket.DataMessage(messageId, messages)
            }
            -520103681 -> { // 0xFFD8FFE0 - JPEG
                val jpeg = data.sliceArray(buffer.position() until data.size)
                DecodedPacket.ImageData(messageId, transferId, jpeg.size, jpeg)
            }
            else -> {
                DecodedPacket.Error("Unknown payload type: 0x${payloadType.toUInt().toString(16)}", data)
            }
        }
    }

    private fun parseJsonPayload(data: ByteArray): List<GameMessage> {
        return try {
            val jsonStr = data.decodeToString()
            val jsonElement = json.parseToJsonElement(jsonStr).jsonObject
            val typeString = jsonElement["TypeString"]?.jsonPrimitive?.content ?: return emptyList()

            val message = when {
                typeString == "InterfaceVersionMessage" ->
                    json.decodeFromString<InterfaceVersionMessage>(jsonStr)
                typeString == "SessionStateMessage" ->
                    json.decodeFromString<SessionStateMessage>(jsonStr)
                typeString == "AssignPlayerIDAndSlotMessage" ->
                    json.decodeFromString<AssignPlayerIDAndSlotMessage>(jsonStr)
                typeString == "ResourceRequirementsMessage" ->
                    json.decodeFromString<ResourceRequirementsMessage>(jsonStr)
                typeString == "AllResourcesReceivedMessage" ->
                    json.decodeFromString<AllResourcesReceivedMessage>(jsonStr)
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
                typeString.contains("ServerBeginPowerPlayPhase") ->
                    json.decodeFromString<ServerBeginPowerPlayPhase>(jsonStr)
                typeString.contains("ServerRequestPowerPlayChoice") ->
                    json.decodeFromString<ServerRequestPowerPlayChoice>(jsonStr)
                typeString == "ImageResourceContentTransferMessage" ->
                    json.decodeFromString<ImageResourceContentTransferMessage>(jsonStr)
                typeString == "ClientRequestPlayerIDMessage" ->
                    json.decodeFromString<ClientRequestPlayerIDMessage>(jsonStr)
                typeString.contains("ClientRequestAvatarStatusMessage") ->
                    json.decodeFromString<ClientRequestAvatarStatusMessage>(jsonStr)
                typeString.contains("ClientRequestAvatarMessage") ->
                    json.decodeFromString<ClientRequestAvatarMessage>(jsonStr)
                typeString == "ClientPlayerProfileMessage" ->
                    json.decodeFromString<ClientPlayerProfileMessage>(jsonStr)
                typeString == "DeviceInfoMessage" ->
                    json.decodeFromString<DeviceInfoMessage>(jsonStr)
                typeString == "ClientImageResourceContentTransferMessage" ->
                    json.decodeFromString<ClientImageResourceContentTransferMessage>(jsonStr)
                typeString == "StartGameButtonPressedResponseMessage" ->
                    json.decodeFromString<StartGameButtonPressedResponseMessage>(jsonStr)
                typeString == "ContinuePressedResponseMessage" ->
                    json.decodeFromString<ContinuePressedResponseMessage>(jsonStr)
                typeString.contains("ClientCategorySelectChoice") ->
                    json.decodeFromString<ClientCategorySelectChoice>(jsonStr)
                typeString.contains("ClientCategorySelectOverride") ->
                    json.decodeFromString<ClientCategorySelectOverride>(jsonStr)
                typeString.contains("ClientStopCategorySelectOverrideResponse") ->
                    json.decodeFromString<ClientStopCategorySelectOverrideResponse>(jsonStr)
                typeString.contains("ClientPowerPlayChoice") ->
                    json.decodeFromString<ClientPowerPlayChoice>(jsonStr)
                typeString.contains("ClientTriviaAnswer") ->
                    json.decodeFromString<ClientTriviaAnswer>(jsonStr)
                else -> null
            }

            if (message != null) listOf(message) else {
                Log.d(TAG, "Unknown TypeString: $typeString")
                emptyList()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing JSON: ${e.message}")
            emptyList()
        }
    }
}
