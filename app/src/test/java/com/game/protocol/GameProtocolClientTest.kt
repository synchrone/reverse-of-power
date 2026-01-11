package com.game.protocol

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


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
        val client = GameProtocolClient("5ca923a0193251c3b24c46546829519a", "192.168.1.152")
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

    @Test
    fun testClientMsg(){
        client.handleReceivedPacket(bytes(
            0xae, 0x7f, 0x12,
            0x0, 0x0, 0x0, 0x1,
            0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x0, 0x66,
            0x0,
            0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x33, 0x29,
            0xb1, 0xe2, 0xff, 0xff,
            0x56, 0x0, 0x0, 0x0,
            0x7b, 0x22, 0x54, 0x79, 0x70, 0x65, 0x53, 0x74, 0x72, 0x69, 0x6e, 0x67, 0x22, 0x3a, 0x22, 0x43, 0x6c, 0x69, 0x65, 0x6e, 0x74, 0x52, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x50, 0x6c, 0x61, 0x79, 0x65, 0x72, 0x49, 0x44, 0x4d, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65, 0x22, 0x2c, 0x22, 0x55, 0x49, 0x44, 0x22, 0x3a, 0x22, 0x35, 0x63, 0x61, 0x39, 0x32, 0x33, 0x61, 0x30, 0x31, 0x39, 0x33, 0x32, 0x35, 0x31, 0x63, 0x33, 0x62, 0x32, 0x34, 0x63, 0x34, 0x36, 0x35, 0x34, 0x36, 0x38, 0x32, 0x39, 0x35, 0x31, 0x39, 0x61, 0x22, 0x7d
        ))
        client.handleReceivedPacket(bytes(
            0xae, 0x7f, 0xa,
            0x0, 0x0, 0x0, 0x1,
            0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x0, 0x60,
            0x0,

            0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x33, 0x29,
            0xb1, 0xe2, 0xff, 0xff,
            0x56, 0x0, 0x0, 0x0,
            0x7b, 0x22, 0x54, 0x79, 0x70, 0x65, 0x53, 0x74, 0x72, 0x69, 0x6e, 0x67, 0x22, 0x3a, 0x22, 0x43, 0x6c, 0x69, 0x65, 0x6e, 0x74, 0x52, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x50, 0x6c, 0x61, 0x79, 0x65, 0x72, 0x49, 0x44, 0x4d, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65, 0x22, 0x2c, 0x22, 0x55, 0x49, 0x44, 0x22, 0x3a, 0x22, 0x62, 0x32, 0x66, 0x33, 0x66, 0x38, 0x65, 0x62, 0x30, 0x63, 0x66, 0x34, 0x65, 0x66, 0x34, 0x62, 0x32, 0x33, 0x35, 0x39, 0x38, 0x37, 0x31, 0x64, 0x33, 0x35, 0x34, 0x39, 0x35, 0x32, 0x32, 0x35, 0x22, 0x7d
        ))
    }
}
