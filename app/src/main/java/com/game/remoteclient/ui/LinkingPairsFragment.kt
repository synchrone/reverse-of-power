package com.game.remoteclient.ui

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
import com.game.remoteclient.databinding.FragmentLinkingPairsBinding

class LinkingPairsFragment : Fragment() {

    private var _binding: FragmentLinkingPairsBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    // Colors
    private val normalPrimary = Color.parseColor("#E8679A")
    private val normalSecondary = Color.parseColor("#D05888")
    private val correctPrimary = Color.parseColor("#4DB6AC")
    private val correctSecondary = Color.parseColor("#3DA69C")
    private val incorrectPrimary = Color.parseColor("#C85080")
    private val incorrectSecondary = Color.parseColor("#B04070")

    // Game state
    private val topQueue = mutableListOf<LinkingAnswer>()
    private val bottomQueue = mutableListOf<LinkingAnswer>()
    private val activeTop = arrayOfNulls<LinkingAnswer>(3)
    private val activeBottom = arrayOfNulls<LinkingAnswer>(3)
    private val attempts = mutableListOf<ClientLinkingAnswerEntry>()
    private var correctCount = 0
    private var totalPairs = 0
    private var timer: CountDownTimer? = null
    private var dragFromSlot: Int? = null
    private var answerSent = false

    // Callbacks
    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var linkingCb: ((ServerBeginLinkingAnsweringPhase) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLinkingPairsBinding.inflate(inflater, container, false)
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
            val slot = findTopSlotAt(x, y)
            if (slot != null && activeTop[slot] != null) {
                dragFromSlot = slot
                val center = getViewCenter(getTopSlotView(slot))
                binding.linkingLine.startLine(center[0], center[1])
                true
            } else {
                false
            }
        }

        binding.linkingLine.onDragEnd = { x, y ->
            val fromSlot = dragFromSlot
            if (fromSlot != null) {
                val bottomSlot = findBottomSlotAt(x, y)
                if (bottomSlot != null && activeTop[fromSlot] != null && activeBottom[bottomSlot] != null) {
                    processMatch(fromSlot, bottomSlot)
                }
                dragFromSlot = null
            }
        }
    }

    private fun getViewCenter(view: View): FloatArray {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val overlayLoc = IntArray(2)
        binding.linkingLine.getLocationInWindow(overlayLoc)
        val x = (loc[0] - overlayLoc[0]).toFloat() + view.width / 2f
        val y = (loc[1] - overlayLoc[1]).toFloat() + view.height / 2f
        return floatArrayOf(x, y)
    }

    private fun startGame(phase: ServerBeginLinkingAnsweringPhase) {
        val allAnswers = phase.LinkingAnswers.sortedBy { it.MatchIndex }

        topQueue.clear()
        bottomQueue.clear()
        topQueue.addAll(allAnswers.filter { it.Direction == 1 })
        bottomQueue.addAll(allAnswers.filter { it.Direction == 2 })

        totalPairs = minOf(topQueue.size, bottomQueue.size)
        correctCount = 0
        attempts.clear()
        answerSent = false

        fillSlots()
        updateUI()

        binding.instructionText.text = "Drag to link answers"
        updateRemainingCount()

        val durationMs = (phase.QuestionDuration * 1000).toLong()
        timer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (_binding == null) return
                sendAnswer()
            }
        }.start()
    }

    private fun fillSlots() {
        fillRow(activeTop, topQueue)
        fillRow(activeBottom, bottomQueue)
        ensureMatch()
    }

    private fun fillRow(slots: Array<LinkingAnswer?>, queue: MutableList<LinkingAnswer>) {
        val occupied = slots.count { it != null }
        val toFill = minOf(3 - occupied, queue.size)
        if (toFill <= 0) return

        val emptyIndices = slots.indices.filter { slots[it] == null }.shuffled()
        for (i in 0 until toFill) {
            slots[emptyIndices[i]] = queue.removeFirst()
        }
    }

    private fun ensureMatch() {
        val topKeys = activeTop.filterNotNull().map { getMatchKey(it.AnswerID) }.toSet()
        val bottomKeys = activeBottom.filterNotNull().map { getMatchKey(it.AnswerID) }.toSet()

        if (topKeys.intersect(bottomKeys).isNotEmpty()) return

        // Try swapping from bottom queue to match any active top item
        for (topItem in activeTop.filterNotNull()) {
            val topKey = getMatchKey(topItem.AnswerID)
            val matchIdx = bottomQueue.indexOfFirst { getMatchKey(it.AnswerID) == topKey }
            if (matchIdx >= 0) {
                val match = bottomQueue.removeAt(matchIdx)
                val swapIdx = activeBottom.indices.first { activeBottom[it] != null }
                activeBottom[swapIdx]?.let { bottomQueue.add(it) }
                activeBottom[swapIdx] = match
                return
            }
        }

        // Try swapping from top queue to match any active bottom item
        for (bottomItem in activeBottom.filterNotNull()) {
            val bottomKey = getMatchKey(bottomItem.AnswerID)
            val matchIdx = topQueue.indexOfFirst { getMatchKey(it.AnswerID) == bottomKey }
            if (matchIdx >= 0) {
                val match = topQueue.removeAt(matchIdx)
                val swapIdx = activeTop.indices.first { activeTop[it] != null }
                activeTop[swapIdx]?.let { topQueue.add(it) }
                activeTop[swapIdx] = match
                return
            }
        }

        // Both queues exhausted and no matches visible — auto-submit
        sendAnswer()
    }

    private fun getMatchKey(answerId: String): String {
        val afterAnswer = answerId.substringAfter("Answer", "")
        return if (afterAnswer.length >= 2) afterAnswer.drop(1) else answerId
    }

    private fun processMatch(topSlot: Int, bottomSlot: Int) {
        val topItem = activeTop[topSlot] ?: return
        val bottomItem = activeBottom[bottomSlot] ?: return

        val topKey = getMatchKey(topItem.AnswerID)
        val bottomKey = getMatchKey(bottomItem.AnswerID)
        val isCorrect = topKey == bottomKey

        attempts.add(ClientLinkingAnswerEntry(
            FromID = topItem.AnswerID,
            ToID = bottomItem.AnswerID,
            Correct = isCorrect
        ))

        Log.d("LinkingPairs", "Match: ${topItem.DisplayText} → ${bottomItem.DisplayText} = $isCorrect")

        if (isCorrect) {
            correctCount++
            showFeedback(topItem.DisplayText, bottomItem.DisplayText, true)

            activeTop[topSlot] = null
            activeBottom[bottomSlot] = null

            binding.feedbackOverlay.postDelayed({
                if (_binding == null) return@postDelayed
                hideFeedback()
                fillSlots()
                updateUI()

                if (correctCount >= totalPairs) {
                    sendAnswer()
                }
            }, 500)
        } else {
            showFeedback(topItem.DisplayText, bottomItem.DisplayText, false)
            binding.feedbackOverlay.postDelayed({
                if (_binding == null) return@postDelayed
                hideFeedback()
            }, 500)
        }
    }

    private fun showFeedback(topText: String, bottomText: String, correct: Boolean) {
        binding.feedbackOverlay.visibility = View.VISIBLE
        binding.feedbackTopText.text = topText
        binding.feedbackBottomText.text = bottomText

        if (correct) {
            binding.sunburstBackground.setColors(correctPrimary, correctSecondary)
            binding.feedbackTopText.setTextColor(correctPrimary)
            binding.feedbackBottomText.setTextColor(correctPrimary)
        } else {
            binding.sunburstBackground.setColors(incorrectPrimary, incorrectSecondary)
            binding.feedbackTopText.setTextColor(incorrectPrimary)
            binding.feedbackBottomText.setTextColor(incorrectPrimary)
        }
    }

    private fun hideFeedback() {
        binding.feedbackOverlay.visibility = View.GONE
        binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
    }

    private fun updateUI() {
        updateSlot(binding.topText0, binding.topSlot0, activeTop[0])
        updateSlot(binding.topText1, binding.topSlot1, activeTop[1])
        updateSlot(binding.topText2, binding.topSlot2, activeTop[2])
        updateSlot(binding.bottomText0, binding.bottomSlot0, activeBottom[0])
        updateSlot(binding.bottomText1, binding.bottomSlot1, activeBottom[1])
        updateSlot(binding.bottomText2, binding.bottomSlot2, activeBottom[2])
        updateRemainingCount()
    }

    private fun updateRemainingCount() {
        val remaining = totalPairs - correctCount
        if (remaining > 0) {
            binding.timerText.text = "$remaining to go"
            binding.timerText.visibility = View.VISIBLE
            binding.instructionText.text = "Drag to link answers"
        } else {
            binding.timerText.visibility = View.GONE
            binding.instructionText.text = "Done, waiting for other players"
        }
    }

    private fun updateSlot(textView: TextView, slot: View, answer: LinkingAnswer?) {
        if (answer != null) {
            textView.text = answer.DisplayText
            slot.visibility = View.VISIBLE
            slot.alpha = 1f
        } else {
            slot.visibility = View.INVISIBLE
        }
    }

    private fun findTopSlotAt(x: Float, y: Float): Int? {
        if (isPointInView(binding.topSlot0, x, y)) return 0
        if (isPointInView(binding.topSlot1, x, y)) return 1
        if (isPointInView(binding.topSlot2, x, y)) return 2
        return null
    }

    private fun findBottomSlotAt(x: Float, y: Float): Int? {
        if (isPointInView(binding.bottomSlot0, x, y)) return 0
        if (isPointInView(binding.bottomSlot1, x, y)) return 1
        if (isPointInView(binding.bottomSlot2, x, y)) return 2
        return null
    }

    private fun isPointInView(view: View, x: Float, y: Float): Boolean {
        val location = IntArray(2)
        view.getLocationInWindow(location)

        val overlayLocation = IntArray(2)
        binding.linkingLine.getLocationInWindow(overlayLocation)

        val viewX = (location[0] - overlayLocation[0]).toFloat()
        val viewY = (location[1] - overlayLocation[1]).toFloat()

        return x >= viewX && x <= viewX + view.width &&
               y >= viewY && y <= viewY + view.height
    }

    private fun getTopSlotView(index: Int): View {
        return when (index) {
            0 -> binding.topSlot0
            1 -> binding.topSlot1
            else -> binding.topSlot2
        }
    }

    private fun sendAnswer() {
        if (answerSent) return
        answerSent = true
        timer?.cancel()

        Log.d("LinkingPairs", "Sending answer: $correctCount correct out of $totalPairs")
        networkManager.sendLinkingAnswer(correctCount, attempts)
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
