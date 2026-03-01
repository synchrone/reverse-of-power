package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.MatchingData
import com.game.protocol.ServerBeginMatchingAnsweringPhase
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentMatchingAnsweringBinding

class MatchingAnsweringFragment : Fragment() {

    private var _binding: FragmentMatchingAnsweringBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private val normalPrimary = Color.parseColor("#9C27B0")
    private val normalSecondary = Color.parseColor("#7B1FA2")
    private val correctPrimary = Color.parseColor("#4DB6AC")
    private val correctSecondary = Color.parseColor("#3DA69C")
    private val incorrectPrimary = Color.parseColor("#C85080")
    private val incorrectSecondary = Color.parseColor("#B04070")

    private var rounds: List<MatchingData> = emptyList()
    private var dummyAnswers: List<String> = emptyList()
    private var currentRoundIndex = 0
    private var correctCount = 0
    private var totalRounds = 0
    private var answerSent = false
    private var locked = false
    private var timer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var matchingCb: ((ServerBeginMatchingAnsweringPhase) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMatchingAnsweringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
        setupGestures()
        observeMessages()

        networkManager.pendingMatching?.let { phase ->
            startGame(phase)
            networkManager.pendingMatching = null
        }
    }

    private fun observeMessages() {
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }
        matchingCb = { message ->
            activity?.runOnUiThread { startGame(message) }
        }

        networkManager.onHoldingScreenMessage = holdingScreenCb
        networkManager.onMatchingMessage = matchingCb
    }

    private var swipeConsumed = false
    private var cumulativeDragX = 0f

    private fun setupGestures() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                swipeConsumed = false
                cumulativeDragX = 0f
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (locked || answerSent) return false
                cumulativeDragX -= distanceX
                val threshold = resources.displayMetrics.density * 40f
                if (!swipeConsumed && kotlin.math.abs(cumulativeDragX) > threshold) {
                    swipeConsumed = true
                    if (cumulativeDragX > 0) binding.hexColumn.prev() else binding.hexColumn.next()
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (locked || answerSent || swipeConsumed) return false
                val dx = e2.x - (e1?.x ?: e2.x)
                if (kotlin.math.abs(dx) < resources.displayMetrics.density * 24f) return false
                swipeConsumed = true
                if (dx > 0) binding.hexColumn.prev() else binding.hexColumn.next()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (locked || answerSent) return false
                if (!binding.hexColumn.isTapOnFrontFace(e.x, e.y)) return false
                confirmAnswer()
                return true
            }
        })

        binding.rootLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun startGame(phase: ServerBeginMatchingAnsweringPhase) {
        rounds = phase.MatchingData
        totalRounds = rounds.size
        currentRoundIndex = 0
        correctCount = 0
        answerSent = false
        locked = false

        dummyAnswers = phase.DummyAnswerData

        updateCounter()
        displayRound()

        timer?.cancel()
        val durationMs = (phase.QuestionDuration * 1000).toLong()
        timer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (_binding == null || answerSent) return
                sendAnswer()
            }
        }.start()
    }

    private fun displayRound() {
        if (currentRoundIndex >= rounds.size) return
        val round = rounds[currentRoundIndex]
        binding.questionText.text = round.QuestionText
        binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
        binding.hexColumn.setBodyColor(normalPrimary)

        // Pick 6 faces: correct answer + 5 dummies (excluding the correct answer)
        val pool = dummyAnswers.filter { it != round.AnswerText }
        val fillers = if (pool.size >= 5) {
            pool.shuffled().take(5)
        } else {
            // Not enough unique dummies — repeat from pool to fill 5 slots
            (0 until 5).map { pool[it % pool.size] }
        }
        val faces = (fillers + round.AnswerText).shuffled()
        binding.hexColumn.setAnswerList(faces)
    }

    private fun confirmAnswer() {
        if (currentRoundIndex >= rounds.size) return
        val round = rounds[currentRoundIndex]
        val selectedAnswer = binding.hexColumn.getCurrentAnswer()
        val isCorrect = selectedAnswer == round.AnswerText

        locked = true
        binding.hexColumn.pressCurrentFace()

        if (isCorrect) {
            correctCount++
            networkManager.sendOngoingChallengeUpdate(correctCount)
            binding.sunburstBackground.setColors(correctPrimary, correctSecondary)
            binding.hexColumn.setBodyColor(correctPrimary)
            Log.d("MatchingAnswering", "Correct: '$selectedAnswer' for '${round.QuestionText}'")

            handler.postDelayed({
                if (_binding == null) return@postDelayed
                locked = false
                advanceRound()
            }, 500)
        } else {
            binding.sunburstBackground.setColors(incorrectPrimary, incorrectSecondary)
            binding.hexColumn.setBodyColor(incorrectPrimary)
            Log.d("MatchingAnswering", "Wrong: '$selectedAnswer' expected '${round.AnswerText}'")

            handler.postDelayed({
                if (_binding == null) return@postDelayed
                binding.sunburstBackground.setColors(normalPrimary, normalSecondary)
                binding.hexColumn.setBodyColor(normalPrimary)
                locked = false
            }, 400)
        }
    }

    private fun advanceRound() {
        currentRoundIndex++
        if (currentRoundIndex >= totalRounds) {
            sendAnswer()
            return
        }
        updateCounter()
        displayRound()
    }

    private fun updateCounter() {
        val remaining = totalRounds - currentRoundIndex
        binding.counterText.text = "$remaining to go"
    }

    private fun sendAnswer() {
        if (answerSent) return
        answerSent = true
        timer?.cancel()
        Log.d("MatchingAnswering", "Completed: $correctCount/$totalRounds correct")
        networkManager.sendMatchingAnswer(correctCount)
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    override fun onPause() {
        super.onPause()
        _binding?.hexColumn?.onPause()
    }

    override fun onResume() {
        super.onResume()
        _binding?.hexColumn?.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onMatchingMessage === matchingCb) networkManager.onMatchingMessage = null
        _binding = null
    }
}
