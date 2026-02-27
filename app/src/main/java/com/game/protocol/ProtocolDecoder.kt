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

    private fun ByteArray.magic16(): Int = (this[0].toInt() and 0xFF shl 8) or (this[1].toInt() and 0xFF)
    private fun ByteArray.magic32(): Int =
        (this[0].toInt() and 0xFF shl 24) or (this[1].toInt() and 0xFF shl 16) or
        (this[2].toInt() and 0xFF shl 8) or (this[3].toInt() and 0xFF)

    companion object {
        // 2-byte packet magics
        const val MAGIC_DATA_KIP = 0xAE7F
        const val MAGIC_DATA_DECADES = 0xC148
        const val MAGIC_ACK = 0x8A33

        // 4-byte DeviceUID magics
        const val MAGIC_UID_KIP = 0x0C89E884
        const val MAGIC_UID_DECADES = 0xAFE4873D.toInt()
    }

    fun decode(data: ByteArray): DecodedPacket {
        if (data.size < 8) return DecodedPacket.TooShort(data)

        if (data.size >= 12) {
            when (data.magic32()) {
                MAGIC_UID_KIP, MAGIC_UID_DECADES -> return decodeDeviceUID(data)
            }
        }

        return when (data.magic16()) {
            MAGIC_DATA_KIP, MAGIC_DATA_DECADES -> {
                try {
                    decodeDataPacket(data)
                } catch (e: Exception) {
                    DecodedPacket.Error("Error decoding data packet: ${e.message}", data)
                }
            }
            MAGIC_ACK -> {
                val messageId = ByteBuffer.wrap(data, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (messageId == -1) {
                    DecodedPacket.ConnectionInit(data)
                } else {
                    val payload = data.sliceArray(6 until data.size)
                    if (payload.any { it != 0.toByte() }) {
                        DecodedPacket.Nack(messageId, payload)
                    } else {
                        DecodedPacket.Ack(messageId)
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
                val jpeg = data.sliceArray(buffer.position()-4 until data.size)
                DecodedPacket.ImageData(messageId, transferId, jpeg.size, jpeg)
            }
            else -> {
                DecodedPacket.Error("Unknown payload type: 0x${payloadType.toUInt().toString(16)}", data)
            }
        }
    }

    private fun parseJsonPayload(data: ByteArray): List<GameMessage> {
        val jsonStr = data.decodeToString()
        return try {
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
                typeString == "RejoiningClientOwnProfileMessage" ->
                    json.decodeFromString<RejoiningClientOwnProfileMessage>(jsonStr)
                typeString == "RequestResourceMessage" ->
                    json.decodeFromString<RequestResourceMessage>(jsonStr)
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
                typeString.contains("ServerRoomMessage") ->
                    json.decodeFromString<ServerRoomMessage>(jsonStr)
                typeString.contains("ServerAwaitTriviaAnsweringPhaseMessage") ->
                    json.decodeFromString<ServerAwaitTriviaAnsweringPhaseMessage>(jsonStr)
                typeString.contains("ServerBeginTriviaAnsweringPhase") ->
                    json.decodeFromString<ServerBeginTriviaAnsweringPhase>(jsonStr)
                typeString.contains("ServerBeginLinkingAnsweringPhase") ->
                    json.decodeFromString<ServerBeginLinkingAnsweringPhase>(jsonStr)
                typeString.contains("ServerBeginSortingAnsweringPhase") ->
                    json.decodeFromString<ServerBeginSortingAnsweringPhase>(jsonStr)
                typeString.contains("ServerBeginMissingLetterAnsweringPhase") ->
                    json.decodeFromString<ServerBeginMissingLetterAnsweringPhase>(jsonStr)
                typeString.contains("PrototypeClientToServerMissingLetterAnswer") ->
                    json.decodeFromString<PrototypeClientToServerMissingLetterAnswer>(jsonStr)
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
                typeString.contains("ClientLinkingAnswer") ->
                    json.decodeFromString<ClientLinkingAnswer>(jsonStr)
                typeString.contains("ClientSortingAnswer") ->
                    json.decodeFromString<ClientSortingAnswer>(jsonStr)
                typeString.contains("ServerRequestEndOfGameFactCount") ->
                    json.decodeFromString<ServerRequestEndOfGameFactCount>(jsonStr)
                typeString.contains("ClientEndOfGameFactCount") ->
                    json.decodeFromString<ClientEndOfGameFactCount>(jsonStr)
                typeString == "ClientEndOfGameFactCommandMessage" ->
                    json.decodeFromString<ClientEndOfGameFactCommandMessage>(jsonStr)
                typeString.contains("ClientToServerOngoingChallengeMessage") ->
                    json.decodeFromString<ClientToServerOngoingChallengeMessage>(jsonStr)
                typeString.contains("ClientToServerTimeSyncMessage") ->
                    json.decodeFromString<ClientToServerTimeSyncMessage>(jsonStr)
                typeString.contains("ServerToClientTimeSyncMessage") ->
                    json.decodeFromString<ServerToClientTimeSyncMessage>(jsonStr)
                else -> null
            }

            if (message != null) listOf(message) else {
                Log.d(TAG, "Unknown TypeString: $typeString in $jsonStr")
                emptyList()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing JSON: ${e.message} in $jsonStr")
            emptyList()
        }
    }
}
