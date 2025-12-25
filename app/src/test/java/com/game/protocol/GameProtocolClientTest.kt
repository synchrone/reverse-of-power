package com.game.protocol

import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit test for GameProtocolClient based on docs/start-game.txt protocol capture.
 *
 * Protocol participants:
 * - Client: 192.168.1.98 (GameProtocolClient implementation)
 * - Server: 192.168.1.152 (Mock server fixture in this test)
 *
 * This test verifies the complete handshake and message exchange sequence.
 */
class GameProtocolClientTest {

    private lateinit var mockServerSocket: DatagramSocket
    private lateinit var mockServerThread: Thread
    private lateinit var client: GameProtocolClient

    private val deviceUID = "b2f3f8eb0cf4ef4b2359871d35495225"
    private val serverPort = 12345
    private val json = Json { ignoreUnknownKeys = true }

    // Track received messages
    private val clientSentPackets = mutableListOf<ByteArray>()
    private var mockServerRunning = false

    @Before
    fun setup() {
        // Create mock server socket that will receive client packets
        mockServerSocket = DatagramSocket(serverPort)
        mockServerRunning = true
        clientSentPackets.clear()
    }

    @After
    fun tearDown() {
        mockServerRunning = false
        if (::client.isInitialized) {
            client.close()
        }
        if (::mockServerSocket.isInitialized) {
            mockServerSocket.close()
        }
        if (::mockServerThread.isInitialized) {
            mockServerThread.interrupt()
            mockServerThread.join(1000)
        }
    }

    /**
     * Test the complete protocol handshake sequence from docs/start-game.txt
     * Frames 1-20 from the protocol capture
     */
    @Test
    fun testCompleteProtocolHandshake() {
        val messagesReceived = mutableListOf<GameMessage>()
        val avatarListLatch = CountDownLatch(1)
        val assignPlayerLatch = CountDownLatch(1)

        // Start mock server that responds according to protocol
        startMockServer()

        // Create client pointing to localhost mock server
        client = GameProtocolClient("127.0.0.1", serverPort)

        client.onMessageReceived = { message ->
            println("TEST: Received message: ${message::class.simpleName} - $message")
            messagesReceived.add(message)
            when (message) {
                is InterfaceVersionMessage -> {
                    println("TEST: Requesting player ID")
                    // When interface version is received, request player ID (Frame 10)
                    client.requestPlayerID(deviceUID)
                }
                is AssignPlayerIDAndSlotMessage -> {
                    println("TEST: Assigned player ID ${message.PlayerID}, sending resources/device info")
                    // When player is assigned, send resources received and device info
                    client.sendAllResourcesReceived()
                    client.sendDeviceInfo(deviceUID, "Genymobile Pixel 9", "Android OS 11 / API-30 (RQ1A.210105.003/857)")
                    client.requestAvatarStatus()
                    assignPlayerLatch.countDown()
                }
                else -> {
                    println("TEST: Other message type: ${message::class.simpleName}")
                }
            }
        }

        client.onAvatarListReceived = { avatars ->
            println("TEST: Received avatar list with ${avatars.size} avatars")
            avatarListLatch.countDown()
        }

        // Connect (sends connection requests and device UID)
        client.connect(deviceUID)

        // Wait for messages to be received
        assertTrue("Should receive AssignPlayerID message",
            assignPlayerLatch.await(15, TimeUnit.SECONDS))
        assertTrue("Should receive avatar list",
            avatarListLatch.await(15, TimeUnit.SECONDS))

        // Give time for all packets to be exchanged
        Thread.sleep(1000)

        // Verify client sent the correct initial packets
        assertTrue("Client should send at least 5 packets", clientSentPackets.size >= 5)

        // Verify Frame 1 & 2: Connection requests (sent twice)
        verifyConnectionRequest(clientSentPackets[0])
        verifyConnectionRequest(clientSentPackets[1])

        // Verify Frame 3: Device UID packet
        verifyDeviceUIDPacket(clientSentPackets[2])

        // Verify we received the expected messages
        val interfaceVersionMsg = messagesReceived.filterIsInstance<InterfaceVersionMessage>()
        assertTrue("Should receive InterfaceVersionMessage", interfaceVersionMsg.isNotEmpty())
        assertEquals("KIP_2016-11-14-1222", interfaceVersionMsg[0].InterfaceVersion)

        val sessionStateMsg = messagesReceived.filterIsInstance<SessionStateMessage>()
        assertTrue("Should receive SessionStateMessage", sessionStateMsg.isNotEmpty())
        assertEquals(1838788817L, sessionStateMsg[0].SessionID)

        val assignPlayerMsg = messagesReceived.filterIsInstance<AssignPlayerIDAndSlotMessage>()
        assertTrue("Should receive AssignPlayerIDAndSlotMessage", assignPlayerMsg.isNotEmpty())
        assertEquals(5, assignPlayerMsg[0].PlayerID)
        assertEquals(0, assignPlayerMsg[0].SlotID)
        assertEquals("Player 1", assignPlayerMsg[0].DisplayName)
    }

    /**
     * Test that ACK packets are sent correctly
     */
    @Test
    fun testAckPacketsFormat() {
        startMockServer()
        client = GameProtocolClient("127.0.0.1", serverPort)

        client.connect(deviceUID)
        Thread.sleep(1000)

        // Find ACK packets (header 0x8a, secondary 0x33, but not connection request)
        val ackPackets = clientSentPackets.filter { packet ->
            packet.size == 38 &&
            packet[0] == 0x8a.toByte() &&
            packet[1] == 0x33.toByte() &&
            // Not a connection request (has message ID)
            !(packet[2] == 0xff.toByte() && packet[3] == 0xff.toByte())
        }

        assertTrue("Should send ACK packets", ackPackets.isNotEmpty())

        // Verify ACK packet structure (Frame 7, 9, 13, 16 from docs)
        ackPackets.forEach { ack ->
            assertEquals("ACK header should be 0x8a", 0x8a.toByte(), ack[0])
            assertEquals("ACK secondary header should be 0x33", 0x33.toByte(), ack[1])
            // Message ID in bytes 2-3 (should reference server message)
            // Rest should be padding (zeros)
            for (i in 4 until 38) {
                assertEquals("ACK padding should be zero at byte $i", 0.toByte(), ack[i])
            }
        }
    }

    /**
     * Test ClientRequestPlayerIDMessage format (Frame 10)
     */
    @Test
    fun testClientRequestPlayerIDMessage() {
        startMockServer()
        client = GameProtocolClient("127.0.0.1", serverPort)

        client.connect(deviceUID)
        Thread.sleep(500)

        // Request player ID
        client.requestPlayerID(deviceUID)
        Thread.sleep(500)

        // Find the ClientRequestPlayerIDMessage packet
        val requestPacket = clientSentPackets.find { packet ->
            packet.size > 42 && String(packet).contains("ClientRequestPlayerIDMessage")
        }

        assertNotNull("Should find ClientRequestPlayerIDMessage packet", requestPacket)
        requestPacket?.let { packet ->
            // Verify packet structure
            assertEquals("Data header should be 0xae", 0xae.toByte(), packet[0])
            assertEquals("Data secondary header should be 0x7f", 0x7f.toByte(), packet[1])

            val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(2)
            val messageId = buffer.short
            val packetFlags = buffer.short

            assertEquals("Should be single packet", 0x0001.toShort(), packetFlags)

            // Extract JSON payload
            val jsonStr = extractJsonFromPacket(packet)
            assertTrue("Should contain ClientRequestPlayerIDMessage",
                jsonStr.contains("ClientRequestPlayerIDMessage"))
            assertTrue("Should contain device UID",
                jsonStr.contains(deviceUID))
        }
    }

    /**
     * Test AllResourcesReceivedMessage format (Frame 17)
     */
    @Test
    fun testAllResourcesReceivedMessage() {
        startMockServer()
        client = GameProtocolClient("127.0.0.1", serverPort)

        client.connect(deviceUID)
        Thread.sleep(500)

        // Send AllResourcesReceived
        client.sendAllResourcesReceived()
        Thread.sleep(500)

        val allResourcesPacket = clientSentPackets.find { packet ->
            packet.size > 42 && String(packet).contains("AllResourcesReceivedMessage")
        }

        assertNotNull("Should find AllResourcesReceivedMessage packet", allResourcesPacket)
        allResourcesPacket?.let { packet ->
            val jsonStr = extractJsonFromPacket(packet)
            assertTrue("Should contain AllResourcesReceivedMessage",
                jsonStr.contains("AllResourcesReceivedMessage"))
            assertTrue("Should contain empty Requirements array",
                jsonStr.contains("\"Requirements\":[]"))
        }
    }

    /**
     * Test DeviceInfoMessage format (Frame 19)
     */
    @Test
    fun testDeviceInfoMessage() {
        startMockServer()
        client = GameProtocolClient("127.0.0.1", serverPort)

        client.connect(deviceUID)
        Thread.sleep(500)

        // Send device info
        val deviceModel = "Genymobile Pixel 9"
        val deviceOS = "Android OS 11 / API-30 (RQ1A.210105.003/857)"
        client.sendDeviceInfo(deviceUID, deviceModel, deviceOS)
        Thread.sleep(500)

        val deviceInfoPacket = clientSentPackets.find { packet ->
            packet.size > 42 && String(packet).contains("DeviceInfoMessage")
        }

        assertNotNull("Should find DeviceInfoMessage packet", deviceInfoPacket)
        deviceInfoPacket?.let { packet ->
            val jsonStr = extractJsonFromPacket(packet)
            val deviceInfo = json.decodeFromString<DeviceInfoMessage>(jsonStr)

            assertEquals("Response should be 10", 10, deviceInfo.Response)
            assertEquals("DeviceSize should be 2", 2, deviceInfo.DeviceSize)
            assertEquals("DeviceOS should be 1", 1, deviceInfo.DeviceOS)
            assertEquals("DeviceModel should match", deviceModel, deviceInfo.DeviceModel)
            assertEquals("DeviceType should be Handheld", "Handheld", deviceInfo.DeviceType)
            assertEquals("DeviceUID should match", deviceUID, deviceInfo.DeviceUID)
            assertEquals("DeviceOperatingSystem should match", deviceOS, deviceInfo.DeviceOperatingSystem)
        }
    }

    /**
     * Test avatar request functionality
     */
    @Test
    fun testAvatarRequest() {
        startMockServer()
        client = GameProtocolClient("127.0.0.1", serverPort)

        client.connect(deviceUID)
        Thread.sleep(500)

        // Request avatar status
        client.requestAvatarStatus()
        Thread.sleep(500)

        val avatarStatusRequestPacket = clientSentPackets.find { packet ->
            packet.size > 42 && String(packet).contains("ClientRequestAvatarStatusMessage")
        }

        assertNotNull("Should find ClientRequestAvatarStatusMessage packet", avatarStatusRequestPacket)

        // Request specific avatar
        val avatarId = "COWGIRL"
        val requestId = client.requestAvatar(avatarId)
        Thread.sleep(500)

        val avatarRequestPacket = clientSentPackets.find { packet ->
            packet.size > 42 && String(packet).contains("ClientRequestAvatarMessage") &&
            String(packet).contains(avatarId)
        }

        assertNotNull("Should find ClientRequestAvatarMessage packet", avatarRequestPacket)
        avatarRequestPacket?.let { packet ->
            val jsonStr = extractJsonFromPacket(packet)
            assertTrue("Should contain request ID", jsonStr.contains(requestId))
            assertTrue("Should contain avatar ID", jsonStr.contains(avatarId))
            assertTrue("Should request avatar", jsonStr.contains("\"Request\":true"))
        }
    }

    /**
     * Test player profile message
     */
    @Test
    fun testPlayerProfileMessage() {
        startMockServer()
        client = GameProtocolClient("127.0.0.1", serverPort)

        client.connect(deviceUID)
        Thread.sleep(500)

        val playerName = "TestPlayer"
        val avatarId = "SPACEMAN"
        client.sendPlayerProfile(playerName, avatarId)
        Thread.sleep(500)

        val profilePacket = clientSentPackets.find { packet ->
            packet.size > 42 && String(packet).contains("ClientPlayerProfileMessage")
        }

        assertNotNull("Should find ClientPlayerProfileMessage packet", profilePacket)
        profilePacket?.let { packet ->
            val jsonStr = extractJsonFromPacket(packet)
            val profile = json.decodeFromString<ClientPlayerProfileMessage>(jsonStr)

            assertEquals("Player name should match", playerName, profile.playerName)
            assertEquals("Uppercase name should match", playerName.uppercase(), profile.uppercasePlayerName)
            assertEquals("Culture should be en-US", "en-US", profile.deviceCultureName)
            assertEquals("Avatar ID should match", avatarId, profile.playerCardId)
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Start mock server that responds according to protocol documentation
     */
    private fun startMockServer() {
        mockServerThread = Thread {
            val buffer = ByteArray(2048)
            var messageIdCounter: Short = 0x0019
            var connectionRequestCount = 0
            var deviceUIDReceived = false
            var savedClientAddress: InetAddress? = null
            var savedClientPort: Int = 0

            while (mockServerRunning && !Thread.interrupted()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    mockServerSocket.soTimeout = 100

                    try {
                        mockServerSocket.receive(packet)
                    } catch (e: Exception) {
                        continue
                    }

                    val receivedData = packet.data.copyOfRange(0, packet.length)
                    val clientAddress = packet.address
                    val clientPort = packet.port
                    savedClientAddress = clientAddress
                    savedClientPort = clientPort

                    // Store client packet for verification
                    synchronized(clientSentPackets) {
                        clientSentPackets.add(receivedData)
                    }

                    // Respond based on packet type
                    when {
                        // Connection request (Frame 1, 2)
                        isConnectionRequest(receivedData) -> {
                            connectionRequestCount++
                        }
                        // Device UID packet (Frame 3)
                        isDeviceUIDPacket(receivedData) -> {
                            deviceUIDReceived = true
                            // Send initial response sequence (Frames 4, 5, 6, 8)
                            Thread.sleep(100)
                            sendEightByteResponse(clientAddress, clientPort)
                            Thread.sleep(50)
                            sendConnectionAck(clientAddress, clientPort)
                            Thread.sleep(50)
                            sendInterfaceVersionMessage(clientAddress, clientPort, messageIdCounter++)
                            Thread.sleep(200)
                            sendSessionStateMessage(clientAddress, clientPort, messageIdCounter++)
                        }
                        // ACK packet
                        isAckPacket(receivedData) -> {
                            // Server received client ACK, no response needed
                        }
                        // Data packet (0xae 0x7f)
                        isDataPacket(receivedData) -> {
                            // Send ACK for data packet
                            val msgId = extractMessageId(receivedData)
                            sendAckPacket(clientAddress, clientPort, msgId)

                            // Check what message was sent
                            val jsonStr = extractJsonFromPacket(receivedData)
                            println("MOCK SERVER: Received data packet with JSON: ${jsonStr.take(100)}")
                            when {
                                jsonStr.contains("ClientRequestPlayerIDMessage") -> {
                                    println("MOCK SERVER: Sending AssignPlayerIDAndSlotMessage")
                                    // Send AssignPlayerIDAndSlotMessage (Frame 12)
                                    Thread.sleep(200)
                                    sendAssignPlayerMessage(clientAddress, clientPort, messageIdCounter++)
                                    println("MOCK SERVER: Sending ResourceRequirementsWithAvatars")
                                    // Send ResourceRequirementsMessage with avatar list (Frame 14-15)
                                    Thread.sleep(200)
                                    sendResourceRequirementsWithAvatars(clientAddress, clientPort, messageIdCounter++)
                                    println("MOCK SERVER: Sent all responses to ClientRequestPlayerIDMessage")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (mockServerRunning && !Thread.interrupted()) {
                        e.printStackTrace()
                    }
                }
            }
        }
        mockServerThread.start()
    }

    private fun isConnectionRequest(data: ByteArray): Boolean {
        return data.size == 38 &&
               data[0] == 0x8a.toByte() &&
               data[1] == 0x33.toByte() &&
               data[2] == 0xff.toByte() &&
               data[3] == 0xff.toByte()
    }

    private fun isDeviceUIDPacket(data: ByteArray): Boolean {
        return data.size == 44 &&
               data[0] == 0x0c.toByte() &&
               data[1] == 0x89.toByte()
    }

    private fun isAckPacket(data: ByteArray): Boolean {
        return data.size == 38 &&
               data[0] == 0x8a.toByte() &&
               data[1] == 0x33.toByte() &&
               !(data[2] == 0xff.toByte() && data[3] == 0xff.toByte())
    }

    private fun isDataPacket(data: ByteArray): Boolean {
        return data.size > 42 &&
               data[0] == 0xae.toByte() &&
               data[1] == 0x7f.toByte()
    }

    private fun extractMessageId(data: ByteArray): Short {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(2)
        return buffer.short
    }

    private fun extractJsonFromPacket(data: ByteArray): String {
        var jsonStart = -1
        for (i in data.indices) {
            if (data[i] == '{'.code.toByte()) {
                jsonStart = i
                break
            }
        }
        if (jsonStart == -1) return ""
        return String(data.sliceArray(jsonStart until data.size), Charsets.UTF_8)
            .substringBefore('\u0000') // Remove null terminators
    }

    // ==================== Mock Server Response Methods ====================

    private fun sendEightByteResponse(address: InetAddress, port: Int) {
        // Frame 4: 8-byte response
        val data = byteArrayOf(
            0x4e.toByte(), 0xdd.toByte(), 0x96.toByte(), 0x66.toByte(),
            0x3b.toByte(), 0x27.toByte(), 0x4f.toByte(), 0x69.toByte()
        )
        val packet = DatagramPacket(data, data.size, address, port)
        mockServerSocket.send(packet)
    }

    private fun sendConnectionAck(address: InetAddress, port: Int) {
        // Frame 5: Connection ACK
        val data = ByteArray(38)
        data[0] = 0x8a.toByte()
        data[1] = 0x33.toByte()
        data[2] = 0xff.toByte()
        data[3] = 0xff.toByte()
        val packet = DatagramPacket(data, data.size, address, port)
        mockServerSocket.send(packet)
    }

    private fun sendInterfaceVersionMessage(address: InetAddress, port: Int, messageId: Short) {
        // Frame 6: InterfaceVersionMessage
        val jsonMsg = """{"TypeString":"InterfaceVersionMessage","InterfaceVersion":"KIP_2016-11-14-1222"}"""
        sendJsonMessage(jsonMsg, messageId, address, port)
    }

    private fun sendSessionStateMessage(address: InetAddress, port: Int, messageId: Short) {
        // Frame 8: SessionStateMessage
        val jsonMsg = """{"TypeString":"SessionStateMessage","SessionID":1838788817}"""
        sendJsonMessage(jsonMsg, messageId, address, port)
    }

    private fun sendAssignPlayerMessage(address: InetAddress, port: Int, messageId: Short) {
        // Frame 12: AssignPlayerIDAndSlotMessage
        val jsonMsg = """{"TypeString":"AssignPlayerIDAndSlotMessage","PlayerID":5,"SlotID":0,"UDPPortOffset":0,"DisplayName":"Player 1","PSNID":""}"""
        sendJsonMessage(jsonMsg, messageId, address, port)
    }

    private fun sendResourceRequirementsWithAvatars(address: InetAddress, port: Int, messageId: Short) {
        var msgId = messageId

        // Send ResourceRequirementsMessage
        sendJsonMessage("""{"TypeString":"ResourceRequirementsMessage","Requirements":[]}""", msgId++, address, port)
        Thread.sleep(50)

        // Send avatar list as separate messages
        val avatars = listOf("COWGIRL", "GOFF", "HOTDOGMAN", "LOVER", "MOUNTAINEER", "SCIENTIST", "SPACEMAN", "MAGICIAN")
        avatars.forEach { avatarId ->
            sendJsonMessage(
                """{"TypeString":"KnowledgeIsPower.ServerAvatarStatusMessage","AvatarID":"$avatarId","Available":true}""",
                msgId++,
                address,
                port
            )
            Thread.sleep(10)
        }
    }

    private fun sendJsonMessage(jsonStr: String, messageId: Short, address: InetAddress, port: Int) {
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(jsonBytes.size + 42).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0xae.toByte())
        buffer.put(0x7f.toByte())
        buffer.putShort(messageId)
        buffer.putShort(0x0001) // Single packet
        buffer.putShort(0x0000)
        buffer.putLong(0)
        buffer.putLong((jsonBytes.size + 16).toLong())
        buffer.putLong(0)
        buffer.put(0x33.toByte())
        buffer.put(0x29.toByte())
        buffer.put(0xb1.toByte())
        buffer.put(0xe2.toByte())
        buffer.put(0xff.toByte())
        buffer.put(0xff.toByte())
        buffer.putInt(jsonBytes.size)
        buffer.put(jsonBytes)

        val packet = DatagramPacket(buffer.array(), buffer.position(), address, port)
        mockServerSocket.send(packet)
    }

    private fun sendAckPacket(address: InetAddress, port: Int, messageId: Short) {
        val buffer = ByteBuffer.allocate(38).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x8a.toByte())
        buffer.put(0x33.toByte())
        buffer.putShort(messageId)
        buffer.put(ByteArray(34)) // Padding

        val packet = DatagramPacket(buffer.array(), buffer.position(), address, port)
        mockServerSocket.send(packet)
    }

    // ==================== Verification Methods ====================

    private fun verifyConnectionRequest(data: ByteArray) {
        assertEquals("Connection request should be 38 bytes", 38, data.size)
        assertEquals("Connection request header", 0x8a.toByte(), data[0])
        assertEquals("Connection request secondary header", 0x33.toByte(), data[1])
        assertEquals("Connection request marker byte 2", 0xff.toByte(), data[2])
        assertEquals("Connection request marker byte 3", 0xff.toByte(), data[3])
        assertEquals("Connection request marker byte 4", 0xff.toByte(), data[4])
        assertEquals("Connection request marker byte 5", 0xff.toByte(), data[5])

        // Rest should be zeros
        for (i in 6 until 38) {
            assertEquals("Connection request padding at byte $i", 0.toByte(), data[i])
        }
    }

    private fun verifyDeviceUIDPacket(data: ByteArray) {
        assertEquals("Device UID packet should be 44 bytes", 44, data.size)
        assertEquals("Device UID header byte 0", 0x0c.toByte(), data[0])
        assertEquals("Device UID header byte 1", 0x89.toByte(), data[1])
        assertEquals("Device UID header byte 2", 0xe8.toByte(), data[2])
        assertEquals("Device UID header byte 3", 0x84.toByte(), data[3])
        assertEquals("Device UID header byte 4", 0x61.toByte(), data[4])
        assertEquals("Device UID header byte 5", 0x03.toByte(), data[5])
        assertEquals("Device UID header byte 6", 0xf4.toByte(), data[6])
        assertEquals("Device UID header byte 7", 0x69.toByte(), data[7])
        assertEquals("Device UID header byte 8", 0x20.toByte(), data[8])

        // Extract UID (starts at byte 12, 32 bytes max)
        val uidBytes = data.sliceArray(12 until minOf(44, 12 + deviceUID.length))
        val extractedUID = String(uidBytes, Charsets.UTF_8)
        assertEquals("Device UID should match", deviceUID, extractedUID)
    }
}
