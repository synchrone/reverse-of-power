package com.game.remoteclient.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
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

    // Drag state
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var isDragging = false

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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandling() {
        binding.itemSlot.setOnTouchListener { view, event ->
            if (currentItem == null) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX - view.translationX
                    dragStartY = event.rawY - view.translationY
                    isDragging = true
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        view.translationX = event.rawX - dragStartX
                        view.translationY = event.rawY - dragStartY
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                        if (event.action == MotionEvent.ACTION_UP) {
                            val itemCenterX = view.x + view.width / 2f
                            val item = currentItem
                            if (item != null) {
                                when {
                                    itemCenterX < binding.leftBucket.right -> {
                                        processSort(item, leftBucketId)
                                        return@setOnTouchListener true
                                    }
                                    itemCenterX > binding.rightBucket.left -> {
                                        processSort(item, rightBucketId)
                                        return@setOnTouchListener true
                                    }
                                }
                            }
                        }
                        // Snap back to center
                        view.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .setDuration(200)
                            .start()
                        true
                    } else false
                }
                else -> false
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
            binding.itemSlot.translationX = 0f
            binding.itemSlot.translationY = 0f
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

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        _binding = null
    }
}
