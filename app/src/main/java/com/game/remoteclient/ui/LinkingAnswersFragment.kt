package com.game.remoteclient.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.ClientLinkingAnswerEntry
import com.game.protocol.LinkingAnswer
import com.game.protocol.ServerBeginLinkingAnsweringPhase
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentLinkingAnswersBinding

class LinkingAnswersFragment : Fragment() {

    private var _binding: FragmentLinkingAnswersBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    // Colors
    private val normalPrimary = Color.parseColor("#E8679A")
    private val normalSecondary = Color.parseColor("#D05888")
    private val correctPrimary = Color.parseColor("#4DB6AC")
    private val correctSecondary = Color.parseColor("#3DA69C")
    private val incorrectPrimary = Color.parseColor("#C85080")
    private val incorrectSecondary = Color.parseColor("#B04070")

    // Game state — rounds grouped by MatchIndex
    private var rounds = listOf<List<LinkingAnswer>>()
    private var currentRoundIndex = 0
    private var totalRounds = 0
    private val attempts = mutableListOf<ClientLinkingAnswerEntry>()
    private var correctCount = 0
    private var timer: CountDownTimer? = null
    private var answerSent = false

    // Current round state
    private var currentSlots = listOf<LinkingAnswer>() // shuffled slots for display
    private var correctOrder = listOf<LinkingAnswer>() // sorted by Direction
    private var connectedChain = mutableListOf<Int>() // slot indices in order connected
    private var chainLocked = false // true during feedback animation

    // Callbacks
    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var linkingCb: ((ServerBeginLinkingAnsweringPhase) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLinkingAnswersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
        observeMessages()
        setupDragHandling()

        networkManager.pendingLinking?.let { phase ->
            startGame(phase)
            networkManager.pendingLinking = null
        }
    }

    private fun observeMessages() {
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }
        linkingCb = { message ->
            activity?.runOnUiThread { startGame(message) }
        }
        networkManager.onHoldingScreenMessage = holdingScreenCb
        networkManager.onLinkingMessage = linkingCb
    }

    private fun setupDragHandling() {
        binding.linkingLine.onDragStart = { x, y ->
            if (chainLocked) {
                false
            } else {
                val slot = findSlotAt(x, y)
                if (slot != null && canStartFrom(slot)) {
                    connectedChain.add(slot)
                    updateSlotHighlights()
                    val center = getSlotCenter(getSlotView(slot))
                    binding.linkingLine.startLine(center[0], center[1])
                    true
                } else {
                    false
                }
            }
        }

        binding.linkingLine.onDragMove = { x, y ->
            if (connectedChain.isNotEmpty() && !chainLocked) {
                val slot = findSlotAt(x, y)
                if (slot != null && !connectedChain.contains(slot)) {
                    if (isCorrectNext(slot)) {
                        // Correct next item — commit the drawn path as a chain line
                        val toCenter = getSlotCenter(getSlotView(slot))
                        binding.linkingLine.commitDragAsChain(toCenter[0], toCenter[1])

                        connectedChain.add(slot)
                        updateSlotHighlights()
                        binding.linkingLine.startLine(toCenter[0], toCenter[1])

                        if (connectedChain.size == currentSlots.size) {
                            binding.linkingLine.clearLine()
                            evaluateChain()
                        }
                    } else {
                        // Wrong item — flash incorrect, reset chain, keep layout
                        binding.linkingLine.clearLine()
                        recordFailedAttempt()
                        chainLocked = true
                        binding.sunburstBackground.setColors(incorrectPrimary, incorrectSecondary)
                        binding.root.postDelayed({
                            if (_binding == null) return@postDelayed
                            binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
                            chainLocked = false
                            resetChain()
                        }, 400)
                    }
                }
            }
        }

        binding.linkingLine.onDragEnd = { _, _ ->
            if (!chainLocked && connectedChain.size < currentSlots.size) {
                resetChain()
            }
        }
    }

    private fun canStartFrom(slot: Int): Boolean {
        return connectedChain.isEmpty() && slot < currentSlots.size &&
            currentSlots[slot].AnswerID == correctOrder[0].AnswerID
    }

    private fun isCorrectNext(slot: Int): Boolean {
        val expectedIndex = connectedChain.size
        return currentSlots[slot].AnswerID == correctOrder[expectedIndex].AnswerID
    }

    private fun recordFailedAttempt() {
        val chainAnswers = connectedChain.map { currentSlots[it] }
        for (i in 0 until chainAnswers.size - 1) {
            attempts.add(ClientLinkingAnswerEntry(
                FromID = chainAnswers[i].AnswerID,
                ToID = chainAnswers[i + 1].AnswerID,
                Correct = false
            ))
        }
    }

    private fun startGame(phase: ServerBeginLinkingAnsweringPhase) {
        // Group answers by MatchIndex → each group is one round
        rounds = phase.LinkingAnswers
            .groupBy { it.MatchIndex }
            .toSortedMap()
            .values
            .map { group -> group.sortedBy { it.Direction } }

        totalRounds = rounds.size
        currentRoundIndex = 0
        correctCount = 0
        attempts.clear()
        answerSent = false

        binding.instructionText.text = "Drag to link in order"
        updateCounter()
        displayRound()

        timer?.cancel()
        val durationMs = (phase.QuestionDuration * 1000).toLong()
        timer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (_binding == null) return
                sendAnswer()
            }
        }.start()
    }

    private fun displayRound() {
        if (currentRoundIndex >= rounds.size) return

        val round = rounds[currentRoundIndex]
        // Shuffle for display but keep track of correct order via Direction
        currentSlots = round.shuffled()
        correctOrder = round.sortedBy { it.Direction }
        connectedChain.clear()
        binding.linkingLine.clearChainLines()

        val slotViews = getSlotViews()
        slotViews.forEachIndexed { index, tv ->
            if (index < currentSlots.size) {
                tv.text = currentSlots[index].DisplayText
                tv.visibility = View.VISIBLE
                tv.alpha = 1f
                tv.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0EAD6"))
            } else {
                tv.visibility = View.GONE
            }
        }

        binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
    }

    private fun evaluateChain() {
        val chainAnswers = connectedChain.map { currentSlots[it] }
        for (i in 0 until chainAnswers.size - 1) {
            attempts.add(ClientLinkingAnswerEntry(
                FromID = chainAnswers[i].AnswerID,
                ToID = chainAnswers[i + 1].AnswerID,
                Correct = true
            ))
        }

        correctCount++
        val phraseText = correctOrder.joinToString(" ") { it.DisplayText }
        Log.d("LinkingAnswers", "Chain complete: $phraseText")

        chainLocked = true
        showFeedback(phraseText, true)
        binding.feedbackOverlay.postDelayed({
            if (_binding == null) return@postDelayed
            hideFeedback()
            chainLocked = false
            currentRoundIndex++
            updateCounter()
            if (currentRoundIndex >= totalRounds) {
                sendAnswer()
            } else {
                displayRound()
            }
        }, 600)
    }

    private fun resetChain() {
        connectedChain.clear()
        binding.linkingLine.clearChainLines()
        updateSlotHighlights()
    }

    private fun updateSlotHighlights() {
        val slotViews = getSlotViews()
        slotViews.forEachIndexed { index, tv ->
            if (index >= currentSlots.size) return@forEachIndexed
            val tint = if (connectedChain.contains(index)) "#B2DFDB" else "#F0EAD6"
            tv.backgroundTintList = ColorStateList.valueOf(Color.parseColor(tint))
        }
    }

    private fun showFeedback(text: String, correct: Boolean) {
        binding.feedbackOverlay.visibility = View.VISIBLE
        binding.feedbackTopText.text = text
        binding.feedbackBottomText.visibility = View.GONE

        if (correct) {
            binding.sunburstBackground.setColors(correctPrimary, correctSecondary)
            binding.feedbackTopText.setTextColor(correctPrimary)
        } else {
            binding.sunburstBackground.setColors(incorrectPrimary, incorrectSecondary)
            binding.feedbackTopText.setTextColor(incorrectPrimary)
        }
    }

    private fun hideFeedback() {
        binding.feedbackOverlay.visibility = View.GONE
        binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
    }

    private fun updateCounter() {
        val remaining = totalRounds - currentRoundIndex
        binding.timerText.text = "$remaining to go"
    }

    private fun getSlotViews(): List<TextView> =
        listOf(binding.slot0, binding.slot1, binding.slot2, binding.slot3, binding.slot4)

    private fun getSlotView(index: Int): TextView = getSlotViews()[index]

    private fun getSlotCenter(view: View): FloatArray {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val overlayLoc = IntArray(2)
        binding.linkingLine.getLocationInWindow(overlayLoc)
        val x = (loc[0] - overlayLoc[0]).toFloat() + view.width / 2f
        val y = (loc[1] - overlayLoc[1]).toFloat() + view.height / 2f
        return floatArrayOf(x, y)
    }

    private fun findSlotAt(x: Float, y: Float): Int? {
        val slotViews = getSlotViews()
        for (i in slotViews.indices) {
            if (i >= currentSlots.size) continue
            if (slotViews[i].visibility != View.VISIBLE) continue
            if (isPointInView(slotViews[i], x, y)) return i
        }
        return null
    }

    private fun isPointInView(view: View, x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val overlayLoc = IntArray(2)
        binding.linkingLine.getLocationInWindow(overlayLoc)
        val viewX = (loc[0] - overlayLoc[0]).toFloat()
        val viewY = (loc[1] - overlayLoc[1]).toFloat()
        return x >= viewX && x <= viewX + view.width &&
               y >= viewY && y <= viewY + view.height
    }

    private fun sendAnswer() {
        if (answerSent) return
        answerSent = true
        timer?.cancel()
        Log.d("LinkingAnswers", "Sending answer: $correctCount correct out of $totalRounds")
        networkManager.sendOrderingAnswer(correctCount)
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onLinkingMessage === linkingCb) networkManager.onLinkingMessage = null
        _binding = null
    }
}
