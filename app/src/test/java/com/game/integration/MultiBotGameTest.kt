package com.game.integration

import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MultiBotGameTest {

    private val host: String? = System.getenv("PS_HOST")
    private val bots = mutableListOf<PlayerBot>()
    private val console = Ps5Console(System.getenv("PSCTL_URL") ?: "http://127.0.0.1:8765")
    private val outDir = File("build/integration/${System.currentTimeMillis()}")
    private var matchStarted = false

    @Before
    fun startFromAnEmptyLobby() {
        assumeTrue("set PS_HOST to run the live console tests", !host.isNullOrBlank())
        if (!console.isInLobby()) println("reset before test: ${console.resetToLobby()}")
    }

    @After
    fun leaveAndCleanUp() {
        bots.forEach { it.close() }
        if (host.isNullOrBlank()) return
        if (matchStarted || !console.isInLobby()) println("reset after test: ${console.resetToLobby()}")
    }

    @Test
    fun botsJoinTheLobby() {
        spawnBots()
        assertEquals("every bot needs its own slot: $bots", bots.size, bots.map { it.slotId }.toSet().size)
        awaitAll("every bot to pick an avatar", 30_000) { it.avatarId != null }
        snapshot("lobby")
        println(bots.joinToString("\n"))
    }

    @Test
    fun botsPlayAGame() {
        val questions = System.getenv("PS_GAME_QUESTIONS")?.toIntOrNull()
        assumeTrue("set PS_GAME_QUESTIONS=N to start a match and play N trivia questions", questions != null)

        spawnBots()
        awaitAll("every bot to pick an avatar", 30_000) { it.avatarId != null }
        await("the start prompt", 30_000) { bots.first().readyToStart }
        snapshot("lobby")

        bots.first().pressStart()
        matchStarted = true
        await("the match to start", 120_000) { bots.any { it.categories.get() > 0 || it.trivia.get() > 0 } }
        snapshot("started")

        awaitAll("$questions trivia answers", questions!! * 180_000L) { it.trivia.get() >= questions }
        snapshot("played")
        println(bots.joinToString("\n"))
        bots.forEach { bot -> bot.answers.forEach { println("${bot.name}: $it") } }
    }

    private fun spawnBots() {
        val avatar = avatarJpeg()
        botIps().forEachIndexed { i, ip ->
            val bot = PlayerBot("Bot ${i + 1}", host!!, ip, avatar)
            bots += bot
            assertTrue("${bot.name} on $ip never got a player slot", bot.join())
        }
    }

    private fun botIps(): List<String> {
        val ips = System.getenv("PS_BOT_IPS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf(DatagramSocket().use { it.connect(InetAddress.getByName(host), 9066); it.localAddress.hostAddress })
        val local = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .map { it.hostAddress }
            .toSet()
        val missing = ips - local
        assertTrue(
            "bot IPs not assigned to this host: $missing (scripts/bot-ips up ${missing.joinToString(" ")})",
            missing.isEmpty(),
        )
        return ips
    }

    private fun avatarJpeg(): ByteArray =
        listOf("src/main/res/raw/avatar_stub.jpeg", "app/src/main/res/raw/avatar_stub.jpeg")
            .map(::File)
            .first { it.exists() }
            .readBytes()

    private fun snapshot(name: String) {
        runCatching {
            console.ensureConnected()
            console.saveFrame(File(outDir, "$name.png"))
        }.onSuccess { println("frame: ${it.absolutePath}") }
            .onFailure { println("no console frame for $name: ${it.message}") }
    }

    private fun awaitAll(what: String, timeoutMs: Long, condition: (PlayerBot) -> Boolean) =
        await(what, timeoutMs) { bots.all(condition) }

    private fun await(what: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                snapshot("timeout")
                fail("timed out after ${timeoutMs / 1000}s waiting for $what\n${bots.joinToString("\n")}")
            }
            Thread.sleep(250)
        }
    }
}
