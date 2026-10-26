package com.game.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File


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

    @Before
    fun setup() {
        // Create client with dummy addresses - we won't be doing actual network I/O
        client = GameProtocolClient("5ca923a0193251c3b24c46546829519a", "192.168.0.14")

        // Set up message handlers to capture parsed messages
        client.onMessageReceived = { message ->
            println("Parsed message: ${message::class.simpleName} - $message")
            receivedMessages.add(message)
        }

        // Clear state
        receivedMessages.clear()
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
    fun retransmittedMessageIsAckedEachTimeButDispatchedOnce() {
        val sent = mutableListOf<ByteArray>()
        client.onPacketSend = { sent += it }

        client.handleReceivedPacket(UdpPacketFixtures.singlePacket6())
        val dispatched = receivedMessages.size
        client.handleReceivedPacket(UdpPacketFixtures.singlePacket6())

        assertTrue("fixture should carry at least one message", dispatched > 0)
        assertEquals("a retransmit must not be dispatched again", dispatched, receivedMessages.size)
        assertEquals("both deliveries must be ACKed", 2, sent.count { it.size == 38 && it[0] == 0x8A.toByte() && it[1] == 0x33.toByte() })
    }

    @Test
    fun resourceThatIsNotRequiredIsConfirmedWithoutRequestingIt() {
        val sent = captureSentMessages()
        val server = ProtocolEncoder()

        feed(server.encodeMessage(ResourceRequirementsMessage(Requirements = listOf(ResourceRequirement("question-image", 2)))))

        assertEquals(listOf(AllResourcesReceivedMessage(Requirements = emptyList())), sent)
    }

    @Test
    fun missingResourcesAreRequestedThenConfirmedTogetherOnceAllArrive() {
        val sent = captureSentMessages()
        val server = ProtocolEncoder()
        val required = listOf(ResourceRequirement("photo-a", 1), ResourceRequirement("photo-b", 1))

        feed(server.encodeMessage(ResourceRequirementsMessage(Requirements = required)))
        assertEquals(listOf(RequestResourceMessage(ResourceGUID = "photo-a"), RequestResourceMessage(ResourceGUID = "photo-b")), sent)
        sent.clear()

        feed(serverImage(server, "photo-a", 90, jfif))
        assertTrue("nothing is confirmed while photo-b is missing", sent.isEmpty())

        val exifImage = serverImage(server, "photo-b", 91, exif)
        feed(exifImage.drop(1))
        feed(exifImage.take(1))
        assertEquals(listOf(AllResourcesReceivedMessage(Requirements = required)), sent)
    }

    @Test
    fun ownUploadedPhotoCountsAsHeld() {
        val sent = captureSentMessages()
        val server = ProtocolEncoder()
        client.sendImage(jfif, "own-photo", 2)
        sent.clear()

        val required = listOf(ResourceRequirement("own-photo", 1))
        feed(server.encodeMessage(ResourceRequirementsMessage(Requirements = required)))

        assertEquals(listOf(AllResourcesReceivedMessage(Requirements = required)), sent)
    }

    @Test
    fun pushedPhotoIsConfirmedWithAnEmptyRequirementList() {
        val sent = captureSentMessages()

        feed(serverImage(ProtocolEncoder(), "other-player", 90, jfif))

        assertEquals(listOf(AllResourcesReceivedMessage(Requirements = emptyList())), sent)
    }

    private val jfif = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0xFF, 0xD9)
    private val exif = bytes(0xFF, 0xD8, 0xFF, 0xE1, 0x00, 0x10, 0x45, 0x78, 0x69, 0x66, 0x00, 0xFF, 0xD9)

    private fun serverImage(server: ProtocolEncoder, guid: String, transferId: Int, jpeg: ByteArray): List<ByteArray> =
        server.encodeMessage(ImageResourceContentTransferMessage(TransferID = transferId, ImageGUID = guid, Acknowledge = true)) +
            server.encodeImageTransfer(jpeg, guid, transferId).drop(1)

    private fun feed(packets: List<ByteArray>) = packets.forEach { client.handleReceivedPacket(it) }

    private fun captureSentMessages(): MutableList<GameMessage> {
        val decoder = ProtocolDecoder()
        val sent = mutableListOf<GameMessage>()
        client.testMode = true
        client.onPacketSend = { packet ->
            (decoder.decode(packet) as? DecodedPacket.DataMessage)?.let { sent += it.messages }
        }
        return sent
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
