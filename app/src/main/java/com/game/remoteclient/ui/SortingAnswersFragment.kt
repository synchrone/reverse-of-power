package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.ClientSortingAnswerEntry
import com.game.protocol.ServerBeginSortingAnsweringPhase
import com.game.protocol.SortingAnswer
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentSortingAnswersBinding

class SortingAnswersFragment : Fragment() {

    private var _binding: FragmentSortingAnswersBinding? = null
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
    private val itemQueue = mutableListOf<SortingAnswer>()
    private var currentItem: SortingAnswer? = null
    private val attempts = mutableListOf<ClientSortingAnswerEntry>()
    private var correctCount = 0
    private var totalItems = 0
    private var leftBucketId = ""
    private var rightBucketId = ""
    private var timer: CountDownTimer? = null
    private var answerSent = false

    // Callbacks
    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSortingAnswersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
        observeMessages()
        setupDragHandling()

        networkManager.pendingSorting?.let { phase ->
            startGame(phase)
            networkManager.pendingSorting = null
        }
    }

    private fun observeMessages() {
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }
        networkManager.onHoldingScreenMessage = holdingScreenCb
    }

    private fun setupDragHandling() {
        binding.dragLine.onDragStart = { x, y ->
            if (currentItem != null && isPointInView(binding.itemSlot, x, y)) {
                val centerX = binding.itemSlot.x + binding.itemSlot.width / 2f
                val centerY = binding.itemSlot.y + binding.itemSlot.height / 2f
                binding.dragLine.startLine(centerX, centerY)
                true
            } else {
                false
            }
        }

        binding.dragLine.onDragEnd = { x, y ->
            val item = currentItem
            if (item != null) {
                when {
                    isPointInView(binding.leftBucket, x, y) -> processSort(item, leftBucketId)
                    isPointInView(binding.rightBucket, x, y) -> processSort(item, rightBucketId)
                }
            }
        }
    }

    private fun startGame(phase: ServerBeginSortingAnsweringPhase) {
        itemQueue.clear()
        itemQueue.addAll(phase.SortingAnswers.shuffled())
        totalItems = itemQueue.size
        correctCount = 0
        attempts.clear()
        answerSent = false

        leftBucketId = phase.LeftBucketID
        rightBucketId = phase.RightBucketID
        binding.leftBucketLabel.text = phase.LeftBucketLabel
        binding.rightBucketLabel.text = phase.RightBucketLabel
        binding.questionText.text = phase.QuestionText

        showNextItem()
        updateRemainingCount()

        val durationMs = (phase.QuestionDuration * 1000).toLong()
        timer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (_binding == null) return
                sendAnswerAndNavigate()
            }
        }.start()
    }

    private fun showNextItem() {
        if (itemQueue.isNotEmpty()) {
            currentItem = itemQueue.removeFirst()
            binding.itemText.text = currentItem!!.DisplayText
            binding.itemSlot.visibility = View.VISIBLE
            binding.itemSlot.alpha = 1f
        } else {
            currentItem = null
            binding.itemSlot.visibility = View.INVISIBLE
        }
    }

    private fun processSort(item: SortingAnswer, chosenBucketId: String) {
        val correctBucketId = if (item.AnswerDirection == 1) leftBucketId else rightBucketId
        val isCorrect = chosenBucketId == correctBucketId

        attempts.add(ClientSortingAnswerEntry(
            AnswerID = item.AnswerID,
            AnswerBucketID = chosenBucketId,
            Correct = isCorrect
        ))

        Log.d("SortingAnswers", "Sort: ${item.DisplayText} → $chosenBucketId = $isCorrect")

        if (isCorrect) {
            correctCount++
            showFeedback(item.DisplayText, true)
        } else {
            showFeedback(item.DisplayText, false)
        }

        binding.feedbackOverlay.postDelayed({
            if (_binding == null) return@postDelayed
            hideFeedback()
            showNextItem()
            updateRemainingCount()

            if (currentItem == null && itemQueue.isEmpty()) {
                sendAnswerAndNavigate()
            }
        }, 400)
    }

    private fun showFeedback(text: String, correct: Boolean) {
        binding.feedbackOverlay.visibility = View.VISIBLE
        binding.feedbackText.text = text
        binding.itemSlot.visibility = View.INVISIBLE

        if (correct) {
            binding.sunburstBackground.setColors(correctPrimary, correctSecondary)
            binding.feedbackText.setTextColor(correctPrimary)
        } else {
            binding.sunburstBackground.setColors(incorrectPrimary, incorrectSecondary)
            binding.feedbackText.setTextColor(incorrectPrimary)
        }
    }

    private fun hideFeedback() {
        binding.feedbackOverlay.visibility = View.GONE
        binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
    }

    private fun updateRemainingCount() {
        val remaining = itemQueue.size + (if (currentItem != null) 1 else 0)
        if (remaining > 0) {
            binding.timerText.text = "$remaining to go"
            binding.timerText.visibility = View.VISIBLE
        } else {
            binding.timerText.visibility = View.GONE
        }
    }

    private fun sendAnswerAndNavigate() {
        if (answerSent) return
        answerSent = true
        timer?.cancel()

        Log.d("SortingAnswers", "Sending answer: $correctCount correct out of $totalItems")
        networkManager.sendSortingAnswer(correctCount, attempts)
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    private fun isPointInView(view: View, x: Float, y: Float): Boolean {
        val location = IntArray(2)
        view.getLocationInWindow(location)

        val overlayLocation = IntArray(2)
        binding.dragLine.getLocationInWindow(overlayLocation)

        val viewX = (location[0] - overlayLocation[0]).toFloat()
        val viewY = (location[1] - overlayLocation[1]).toFloat()

        return x >= viewX && x <= viewX + view.width &&
               y >= viewY && y <= viewY + view.height
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        _binding = null
    }
}
