package com.game.protocol

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.game.protocol.GameProtocolClient

fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

/**
 * Focused unit test for GameProtocolClient packet parsing using UdpPacketFixtures.
 *
 * This test directly exercises the handleReceivedPacket() method without requiring
 * network I/O, making it fast and easy to iterate on protocol parsing bugs.
 *
 * The fixtures come from actual PCAP captures and represent correct protocol data.
 */
class GameProtocolClientTest {

    private lateinit var client: GameProtocolClient
    private val receivedMessages = mutableListOf<GameMessage>()
    private val receivedAvatarLists = mutableListOf<List<ServerAvatarStatusMessage>>()

    @Before
    fun setup() {
        // Create client with dummy addresses - we won't be doing actual network I/O
        client = GameProtocolClient("5ca923a0193251c3b24c46546829519a", "192.168.0.14")

        // Set up message handlers to capture parsed messages
        client.onMessageReceived = { message ->
            println("Parsed message: ${message::class.simpleName} - $message")
            receivedMessages.add(message)
        }

        client.onAvatarListReceived = { avatars ->
            println("Parsed avatar list with ${avatars.size} avatars")
            receivedAvatarLists.add(avatars)
        }

        // Clear state
        receivedMessages.clear()
        receivedAvatarLists.clear()
    }

    @Test
    fun testConnect(){
        return; // WIP
        val client = GameProtocolClient("5ca923a0193251c3b24c46546829519a", "192.168.0.14")
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
                    println("Assigned Player ID: ${message.PlayerID}, Slot: ${message.SlotID}, Name: ${message.DisplayName}")
                    client.sendAllResourcesReceived()
                    client.sendDeviceInfo(
                        "Genymobile Pixel 9",
                        "Android OS 11 / API-30 (RQ1A.210105.003/857)"
                    )
                    client.requestAvatarStatus()
                }
                is ServerAvatarStatusMessage -> {
                    if(message.AvatarID == "COWGIRL" && message.Available) {
                        client.requestAvatar("COWGIRL")
                    }
                }
                is ServerAvatarRequestResponseMessage -> {
                    println("< Avatar ${message.AvatarID} chosen: ${message.Available}")
                    if (message.Available) {
                        // Avatar acquired, send profile
                        client.sendPlayerProfile("test", message.AvatarID)
//                        client.sendImage()
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
        client.connect()
        println("Game protocol started")

        Thread.sleep(30000)
        client.close()
    }

    // ==================== Single Packet Tests ====================

    /**
     * Test parsing ACK packet (0x8a 0x33)
     * Fixture: singlePacket4 - ACK with message ID 0x0019
     */
    @Test
    fun testParseAckPacket() {
        val ackPacket = byteArrayOf(
            0x8A.toByte(), 0x33, 0x19, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        // ACK packets should be handled silently (no messages emitted)
        client.handleReceivedPacket(ackPacket)

        assertEquals("ACK packets should not generate messages", 0, receivedMessages.size)
    }

    @Test
    fun testValidSinglePackets() {
        client.handleReceivedPacket(UdpPacketFixtures.singlePacket6())
        client.handleReceivedPacket(UdpPacketFixtures.singlePacket9())
        client.handleReceivedPacket(UdpPacketFixtures.singlePacket18())
        client.handleReceivedPacket(UdpPacketFixtures.singlePacket21())
        client.handleReceivedPacket(UdpPacketFixtures.singlePacket22())
    }

    @Test
    fun testValidMultiPackets() {
        client.handleReceivedPacket(UdpPacketFixtures.multiPacket10())
        client.handleReceivedPacket(UdpPacketFixtures.multiPacket27())
    }

    @Test
    fun testValidChunkedMultiPackets() {
        client.handleReceivedPacket(UdpPacketFixtures.chunkedMutlipacket_chunk1());
        client.handleReceivedPacket(UdpPacketFixtures.chunkedMutlipacket_chunk2());
    }

    @Test
    fun testValidChunkedPackets(){
        // Load and process all chunked fixture files sequentially
        for (chunk in UdpPacketFixtures.chunkedDatagram1()) {
            client.handleReceivedPacket(chunk)
        }
        for (chunk in UdpPacketFixtures.chunkedDatagram2()) {
            client.handleReceivedPacket(chunk)
        }
    }
}
