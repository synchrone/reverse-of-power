package com.game.protocol

import org.junit.Test
import java.io.File

data class CapturedPacket(
    val frameNumber: Int,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    val payload: ByteArray,
    val direction: Direction
)

enum class Direction(val label: String) {
    SERVER_TO_CLIENT("S\u2192C"),
    CLIENT_TO_SERVER("C\u2192S")
}

object PcapReplay {

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(":", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHexPreview(max: Int = 32): String {
        val preview = take(max).joinToString(" ") { "%02x".format(it) }
        return if (size > max) "$preview ..." else preview
    }

    private fun resolveFile(path: String): File {
        val file = File(path)
        if (file.exists()) return file
        // Gradle test working dir is app/, try project root
        val projectRoot = File(System.getProperty("user.dir")).parentFile
        val fromRoot = File(projectRoot, path)
        if (fromRoot.exists()) return fromRoot
        return file // let tshark report the error
    }

    fun extractPackets(pcapFile: String): List<CapturedPacket> {
        val absolutePath = resolveFile(pcapFile).absolutePath
        val process = ProcessBuilder(
            "tshark", "-r", absolutePath,
            "-T", "fields",
            "-e", "frame.number",
            "-e", "ip.src",
            "-e", "udp.srcport",
            "-e", "ip.dst",
            "-e", "udp.dstport",
            "-e", "data.data",
            "-Y", "udp",
            "-E", "separator=|"
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            System.err.println("tshark failed (exit $exitCode):")
            System.err.println(output)
            error("tshark failed with exit code $exitCode")
        }

        return output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 6 || parts[5].isBlank()) return@mapNotNull null
                val dstPort = parts[4].toInt()
                val direction = when (dstPort) {
                    9060 -> Direction.SERVER_TO_CLIENT
                    9066 -> Direction.CLIENT_TO_SERVER
                    else -> Direction.SERVER_TO_CLIENT // fallback
                }
                CapturedPacket(
                    frameNumber = parts[0].toInt(),
                    srcIp = parts[1],
                    srcPort = parts[2].toInt(),
                    dstIp = parts[3],
                    dstPort = dstPort,
                    payload = hexToBytes(parts[5]),
                    direction = direction
                )
            }
    }

    fun run(pcapFile: String) {
        println("=== PCAP Replay: $pcapFile ===")
        println()

        val packets = extractPackets(pcapFile)
        println("Extracted ${packets.size} UDP packets")
        println()

        val decoder = ProtocolDecoder()
        var serverCount = 0
        var clientCount = 0

        for (packet in packets) {
            val dir = packet.direction.label
            val frameStr = "#%-4d".format(packet.frameNumber)
            val sizeStr = "%4db".format(packet.payload.size)

            when (packet.direction) {
                Direction.SERVER_TO_CLIENT -> serverCount++
                Direction.CLIENT_TO_SERVER -> clientCount++
            }

            val decoded = decoder.decode(packet.payload)
            val description = formatDecoded(decoded, packet.payload)

            println("$frameStr $dir $sizeStr  $description")
        }

        println()
        println("=== Summary: $serverCount server packets, $clientCount client packets, ${packets.size} total ===")
    }

    private fun formatDecoded(decoded: DecodedPacket, raw: ByteArray): String {
        return when (decoded) {
            is DecodedPacket.Ack ->
                "ACK 0x%02x".format(decoded.messageId)
            is DecodedPacket.Nack -> {
                val nonZero = decoded.payload.toList().mapIndexedNotNull { i, b ->
                    if (b != 0.toByte()) "[%2d] 0x%02x (%d)".format(i + 3, b, b.toInt() and 0xff) else null
                }.joinToString("\n         ")
                "NACK 0x%02x\n         $nonZero".format(decoded.messageId)
            }
            is DecodedPacket.ConnectionInit ->
                "ConnectionInit"
            is DecodedPacket.DeviceUID ->
                "DeviceUID: ${decoded.uid}"
            is DecodedPacket.GameInProgress ->
                "GameInProgress"
            is DecodedPacket.DataMessage -> {
                val msgId = "0x%02x".format(decoded.messageId)
                if (decoded.messages.isEmpty()) {
                    "Message $msgId: (no parsed messages)"
                } else if (decoded.messages.size == 1) {
                    "Message $msgId: ${formatMessage(decoded.messages[0])}"
                } else {
                    val header = "Message $msgId: ${decoded.messages.size} messages"
                    val details = decoded.messages.joinToString("\n         ") { formatMessage(it) }
                    "$header\n         $details"
                }
            }
            is DecodedPacket.ImageData ->
                "ImageData msgId=0x%02x transferId=${decoded.transferId} jpeg=${decoded.jpegSize}b".format(decoded.messageId)
            is DecodedPacket.Fragment ->
                "FRAG 0x%02x (%d/%d) %db".format(decoded.messageId, decoded.idx, decoded.total, decoded.size)
            is DecodedPacket.Unknown ->
                "Unknown: ${raw.toHexPreview()}"
            is DecodedPacket.TooShort ->
                "TooShort (${raw.size}b): ${raw.toHexPreview()}"
            is DecodedPacket.Error ->
                "ERROR: ${decoded.message}"
        }
    }

    private fun formatMessage(msg: GameMessage): String {
        val typeName = msg.TypeString.substringAfterLast(".")
        return when (msg) {
            is InterfaceVersionMessage ->
                "$typeName(version=${msg.InterfaceVersion})"
            is SessionStateMessage ->
                "$typeName(sessionId=${msg.SessionID})"
            is AssignPlayerIDAndSlotMessage ->
                "$typeName(playerId=${msg.PlayerID}, slotId=${msg.SlotID}, name=\"${msg.DisplayName}\")"
            is ResourceRequirementsMessage ->
                "$typeName(${msg.Requirements.size} requirements)"
            is AllResourcesReceivedMessage ->
                "$typeName(${msg.Requirements.size} requirements)"
            is ServerAvatarStatusMessage ->
                "$typeName(id=${msg.AvatarID}, available=${msg.Available})"
            is ServerAvatarRequestResponseMessage ->
                "$typeName(requestId=${msg.RequestID}, id=${msg.AvatarID}, available=${msg.Available})"
            is ClientQuizCommandMessage ->
                "$typeName(action=${msg.action}, time=${msg.time})"
            is ClientGameIDMessage ->
                "$typeName(gameId=${msg.GameID})"
            is ClientHoldingScreenCommandMessage ->
                "$typeName(action=${msg.action}, type=${msg.HoldingScreenType}, text=\"${msg.HoldingScreenText}\")"
            is PlayerJoinedMessage ->
                "$typeName(currentId=${msg.CurrentPlayerID}, oldId=${msg.OldPlayerID}, slotId=${msg.SlotID})"
            is PlayerLeftMessage ->
                "$typeName(playerId=${msg.PlayerID})"
            is PlayerNameQuizStateMessage ->
                "$typeName(name=\"${msg.PlayerName}\", playerId=${msg.PlayerID})"
            is ServerColourMessage ->
                "$typeName(rainbow=${msg.Rainbow}, roundType=${msg.RoundType})"
            is ServerCategorySelectChoices ->
                "$typeName(${msg.CategoryChoices.size} choices: ${msg.CategoryChoices.joinToString { "\"${it.DisplayText}\"" }})"
            is ServerRequestCategorySelectChoice ->
                typeName
            is ServerBeginCategorySelectOverride ->
                "$typeName(duration=${msg.DurationSeconds}s, door=${msg.DoorIndex})"
            is ServerStopCategorySelectOverride ->
                typeName
            is ServerCategorySelectOverrideSuccess ->
                "$typeName(success=${msg.CategorySelectOverrideSuccess}, player=\"${msg.CategorySelectOverridePlayerName}\")"
            is ServerBeginTriviaAnsweringPhase ->
                "$typeName(q=\"${msg.QuestionText}\", ${msg.Answers.size} answers, duration=${msg.QuestionDuration}s)"
            is ImageResourceContentTransferMessage ->
                "$typeName(transferId=${msg.TransferID}, guid=${msg.ImageGUID}, ack=${msg.Acknowledge})"
            is ClientRequestPlayerIDMessage ->
                "$typeName(uid=${msg.UID})"
            is ClientRequestAvatarStatusMessage ->
                typeName
            is ClientRequestAvatarMessage ->
                "$typeName(requestId=${msg.RequestID}, avatarId=${msg.AvatarID})"
            is ClientPlayerProfileMessage ->
                "$typeName(name=\"${msg.playerName}\", card=${msg.playerCardId})"
            is DeviceInfoMessage ->
                "$typeName(model=\"${msg.DeviceModel}\", os=\"${msg.DeviceOperatingSystem}\")"
            is ClientImageResourceContentTransferMessage ->
                "$typeName(transferId=${msg.TransferID}, guid=${msg.ImageGUID}, type=${msg.ImgType})"
            is StartGameButtonPressedResponseMessage ->
                "$typeName(response=${msg.Response})"
            is ClientCategorySelectChoice ->
                "$typeName(doorIndex=${msg.ChosenCategoryIndex})"
            is ClientCategorySelectOverride ->
                "$typeName(duration=${msg.DurationSeconds}s)"
            is ClientStopCategorySelectOverrideResponse ->
                "$typeName(overrideSent=${msg.OverrideSent})"
            else ->
                "$typeName"
        }
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: PcapReplay <pcapng-file>")
        System.err.println("Example: PcapReplay docs/successful-game-post-start.pcapng")
        return
    }
    PcapReplay.run(args[0])
}

class PcapReplayTest {
    @Test
    fun replayCapture() {
        val pcapFile = System.getProperty("pcap.file")
        if (pcapFile.isNullOrBlank()) {
            println("No pcap.file system property set, skipping replay.")
            println("Run with: ./gradlew testDebugUnitTest --tests \"com.game.protocol.PcapReplayTest\" -Dpcap.file=<path>")
            return
        }
        PcapReplay.run(pcapFile)
    }
}
