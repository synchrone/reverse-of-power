package com.game.integration

import com.game.protocol.*
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class PlayerBot(
    val name: String,
    consoleHost: String,
    val localIp: String,
    private val avatarJpeg: ByteArray,
    private val accuracy: Double = 1.0,
    private val thinkMs: Long = 1500,
) {
    val client = GameProtocolClient(UUID.randomUUID().toString().replace("-", ""), consoleHost, listenHostStr = localIp)
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "bot-$name").apply { isDaemon = true } }

    private val avatars = linkedMapOf<String, Boolean>()
    private var wantsAvatar = false
    private var requestedAvatar: String? = null
    private var categoryOpen = false
    private var categoryCount = 0
    private var chosenCategory: Int? = null
    private var powerPlays: List<PowerPlay> = emptyList()
    private var powerPlaySent = true

    val received = CopyOnWriteArrayList<String>()
    val answers = CopyOnWriteArrayList<String>()
    val trivia = AtomicInteger()
    val categories = AtomicInteger()
    val powerPlaysUsed = AtomicInteger()
    val challenges = AtomicInteger()
    val continues = AtomicInteger()

    @Volatile var avatarId: String? = null
        private set
    @Volatile var readyToStart = false
        private set
    @Volatile var startPressed = false
        private set
    @Volatile var lastMessageAt = 0L
        private set

    val playerId: Int? get() = client.assignedPlayerId
    val slotId: Int? get() = client.assignedSlotId

    init {
        client.onMessageReceived = { message ->
            worker.execute { handle(message) }
            true
        }
    }

    fun join(timeoutMs: Long = 15_000): Boolean {
        // A second client on the same host never sees ConnectionInit, but is admitted all the same.
        runBlocking { client.connect(2_000) }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (playerId == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
        return playerId != null
    }

    fun pressStart() = worker.execute {
        send(StartGameButtonPressedResponseMessage())
        startPressed = true
    }

    fun close() {
        worker.shutdownNow()
        client.close()
    }

    override fun toString() =
        "$name ip=$localIp player=$playerId slot=$slotId avatar=$avatarId trivia=$trivia categories=$categories " +
            "powerPlays=$powerPlaysUsed challenges=$challenges continues=$continues last=${received.takeLast(6)}"

    private fun handle(message: GameMessage) {
        if (message is ServerToClientTimeSyncMessage) return
        received += message.TypeString.removePrefix("KnowledgeIsPower.")
        lastMessageAt = System.currentTimeMillis()

        when (message) {
            is ServerAvatarStatusMessage -> {
                avatars[message.AvatarID] = message.Available
                if (wantsAvatar && requestedAvatar == null) requestAvatar()
            }
            is ClientQuizCommandMessage -> when (message.action) {
                ClientQuizCommandMessage.ACTION_RESET_TO_NAME -> {
                    wantsAvatar = true
                    requestedAvatar = null
                    requestAvatar()
                }
                ClientQuizCommandMessage.ACTION_READY -> readyToStart = true
                ClientQuizCommandMessage.ACTION_CONTINUE -> later {
                    send(ContinuePressedResponseMessage())
                    continues.incrementAndGet()
                }
            }
            is ServerAvatarRequestResponseMessage -> if (message.AvatarID == requestedAvatar) {
                if (message.Available) {
                    sendProfile(message.AvatarID)
                } else {
                    avatars[message.AvatarID] = false
                    requestedAvatar = null
                    requestAvatar()
                }
            }
            is ServerCategorySelectChoices -> if (!categoryOpen && message.CategoryChoices.isNotEmpty()) {
                categoryOpen = true
                categoryCount = message.CategoryChoices.size
                later { if (categoryOpen && chosenCategory == null) pickCategory() }
            }
            is ServerRequestCategorySelectChoice -> {
                if (chosenCategory == null && categoryCount > 0) pickCategory()
                else send(ClientCategorySelectChoice(ChosenCategoryIndex = chosenCategory ?: -1))
                categoryOpen = false
                chosenCategory = null
            }
            is ServerStopCategorySelectOverride -> send(ClientStopCategorySelectOverrideResponse(OverrideSent = false))
            is ServerBeginTriviaAnsweringPhase -> {
                val delay = minOf(thinkMs, (message.QuestionDuration * 500).toLong())
                later(delay) { answerTrivia(message, delay) }
            }
            is ServerBeginPowerPlayPhase -> {
                powerPlays = message.PowerPlays
                powerPlaySent = false
                later { if (playablePowerPlay() != null) choosePowerPlay() }
            }
            is ServerRequestPowerPlayChoice -> choosePowerPlay()
            is ServerBeginLinkingAnsweringPhase -> later { answerLinking(message) }
            is ServerBeginSortingAnsweringPhase -> later { answerSorting(message) }
            is ServerBeginEliminatingAnsweringPhase -> later {
                challenge(ClientToServerEliminatingAnswer(AnswerCount = message.EliminatingAnswerData.size))
            }
            is ServerBeginMissingLetterAnsweringPhase -> later {
                challenge(PrototypeClientToServerMissingLetterAnswer(ClientMissingLetterCorrectAnswerCount = message.MissingLetterAnswerData.size))
            }
            is ServerBeginMatchingAnsweringPhase -> later {
                challenge(ClientToServerMatchingAnswer(ClientMatchingCorrectAnswerCount = message.MatchingData.size))
            }
            is ServerBeginOrderingAnsweringPhase -> later {
                challenge(ClientToServerOrderingAnswer(ClientOrderingCorrectAnswerCount = message.OrderingAnswerData.size))
            }
            else -> Unit
        }
    }

    private fun requestAvatar() {
        val pick = avatars.filterValues { it }.keys.randomOrNull() ?: return
        requestedAvatar = pick
        send(ClientRequestAvatarMessage(RequestID = UUID.randomUUID().toString(), AvatarID = pick, Request = true))
    }

    private fun sendProfile(avatar: String) {
        wantsAvatar = false
        val guid = UUID.randomUUID().toString()
        client.sendImage(avatarJpeg, guid, 2)
        send(ClientPlayerProfileMessage(
            playerName = name,
            uppercasePlayerName = name.uppercase(),
            deviceCultureName = "en-US",
            playerCardId = avatar,
        ))
        client.sendImage(avatarJpeg, guid, 3)
        avatarId = avatar
    }

    private fun pickCategory() {
        val index = Random.nextInt(categoryCount)
        chosenCategory = index
        send(ClientCategorySelectChoice(ChosenCategoryIndex = index))
        categories.incrementAndGet()
    }

    private fun answerTrivia(phase: ServerBeginTriviaAnsweringPhase, delayMs: Long) {
        val correct = phase.Answers.firstOrNull { it.IsCorrect }
        val wrong = phase.Answers.filterNot { it.IsCorrect }
        val chosen = if (correct != null && (wrong.isEmpty() || Random.nextDouble() < accuracy)) correct else wrong.randomOrNull()
        send(ClientTriviaAnswer(ChosenAnswerDisplayIndex = chosen?.DisplayIndex ?: -1, AnswerTime = delayMs / 1000.0))
        trivia.incrementAndGet()
        val options = phase.Answers.joinToString(" | ") { "${it.DisplayIndex}:${it.DisplayText}${if (it.IsCorrect) "*" else ""}" }
        answers += "${phase.QuestionText} [$options] -> ${chosen?.DisplayIndex}"
    }

    private fun playablePowerPlay() =
        powerPlays.firstOrNull { it.effectivePowerType != PowerType.NONE && it.PowerPlayTargets.isNotEmpty() }

    private fun choosePowerPlay() {
        if (powerPlaySent) return
        powerPlaySent = true
        val play = playablePowerPlay()
        if (play == null) {
            send(ClientPowerPlayChoice(PowerPlaySlotIndex = -1, TargetSlotIndex = emptyList()))
            return
        }
        val targets = if (play.TargetCount > 1) play.PowerPlayTargets else listOf(play.PowerPlayTargets.random())
        send(ClientPowerPlayChoice(PowerPlaySlotIndex = play.DisplayIndex, TargetSlotIndex = targets))
        powerPlaysUsed.incrementAndGet()
    }

    private fun answerLinking(phase: ServerBeginLinkingAnsweringPhase) {
        val bottoms = phase.LinkingAnswers.filter { it.Direction == 2 }.associateBy { matchKey(it.AnswerID) }
        val pairs = phase.LinkingAnswers.filter { it.Direction == 1 }.mapNotNull { top ->
            bottoms[matchKey(top.AnswerID)]?.let { ClientLinkingAnswerEntry(FromID = top.AnswerID, ToID = it.AnswerID, Correct = true) }
        }
        challenge(ClientLinkingAnswer(ClientLinkingCorrectAnswerCount = pairs.size, ClientAnswers = pairs))
    }

    private fun matchKey(answerId: String): String {
        val afterAnswer = answerId.substringAfter("Answer", "")
        return if (afterAnswer.length >= 2) afterAnswer.drop(1) else answerId
    }

    private fun answerSorting(phase: ServerBeginSortingAnsweringPhase) {
        val entries = phase.SortingAnswers.map {
            ClientSortingAnswerEntry(
                AnswerID = it.AnswerID,
                AnswerBucketID = if (it.AnswerDirection == 1) phase.LeftBucketID else phase.RightBucketID,
                Correct = true,
            )
        }
        challenge(ClientSortingAnswer(ClientSortingCorrectAnswerCount = entries.size, SortingAnswers = entries))
    }

    private fun challenge(answer: GameMessage) {
        send(answer)
        challenges.incrementAndGet()
    }

    private fun send(message: GameMessage) = client.sendMessage(message)

    private fun later(delayMs: Long = thinkMs, action: () -> Unit) {
        worker.schedule({ runCatching(action).onFailure { it.printStackTrace() } }, delayMs, TimeUnit.MILLISECONDS)
    }
}
