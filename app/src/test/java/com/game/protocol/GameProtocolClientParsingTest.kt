package com.game.protocol

import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Focused unit test for GameProtocolClient packet parsing using UdpPacketFixtures.
 *
 * This test directly exercises the handleReceivedPacket() method without requiring
 * network I/O, making it fast and easy to iterate on protocol parsing bugs.
 *
 * The fixtures come from actual PCAP captures and represent correct protocol data.
 */
class GameProtocolClientParsingTest {

    private lateinit var client: GameProtocolClient
    private val receivedMessages = mutableListOf<GameMessage>()
    private val receivedAvatarLists = mutableListOf<List<ServerAvatarStatusMessage>>()

    @Before
    fun setup() {
        // Create client with dummy addresses - we won't be doing actual network I/O
        client = GameProtocolClient("127.0.0.1", serverPort = 9066, listenPort = 9060)

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
        client.sendDeviceUID("b2f3f8eb0cf4ef4b2359871d35495225")
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
    fun testValidChunkedPackets(){
        // Load and process all chunked fixture files sequentially
        for (i in 1..17) {
            val resourcePath = "fixtures/0f_chunked_$i.bin"
            val inputStream = javaClass.classLoader?.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException("Could not find fixture file: $resourcePath")

            val packet = inputStream.readBytes()
            client.handleReceivedPacket(packet)
        }
        for (i in 73..89) {
            val resourcePath = "fixtures/11_chunked_$i.bin"
            val inputStream = javaClass.classLoader?.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException("Could not find fixture file: $resourcePath")

            val packet = inputStream.readBytes()
            client.handleReceivedPacket(packet)
        }
        for (i in 1..2) {
            val resourcePath = "fixtures/2a_chunked_$i.bin"
            val inputStream = javaClass.classLoader?.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException("Could not find fixture file: $resourcePath")

            val packet = inputStream.readBytes()
            client.handleReceivedPacket(packet)
        }
    }

    // ==================== Helper for debugging ====================

    /**
     * Helper method to print packet bytes in hex format
     */
    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02x".format(it) }
    }

    /**
     * Helper method to find JSON start in packet
     */
    private fun findJsonStart(data: ByteArray): Int {
        for (i in data.indices) {
            if (data[i] == '{'.code.toByte()) {
                return i
            }
        }
        return -1
    }
}
