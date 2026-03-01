package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.EliminatingAnswerData
import com.game.protocol.ServerBeginEliminatingAnsweringPhase
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentEliminationAnsweringBinding

class EliminationAnsweringFragment : Fragment() {

    private var _binding: FragmentEliminationAnsweringBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var currentRoundIndex = 0
    private var correctCount = 0
    private var totalRounds = 0
    private var answerSent = false
    private var penaltyActive = false
    private var timer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var rounds: List<EliminatingAnswerData> = emptyList()

    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var eliminationCb: ((ServerBeginEliminatingAnsweringPhase) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEliminationAnsweringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sunburstBackground.setColors(
            Color.parseColor("#E8679A"),
            Color.parseColor("#D05888")
        )

        observeMessages()

        networkManager.pendingElimination?.let { phase ->
            startGame(phase)
            networkManager.pendingElimination = null
        }
    }

    private fun observeMessages() {
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }
        eliminationCb = { message ->
            activity?.runOnUiThread { startGame(message) }
        }

        networkManager.onHoldingScreenMessage = holdingScreenCb
        networkManager.onEliminationMessage = eliminationCb
    }

    private fun startGame(phase: ServerBeginEliminatingAnsweringPhase) {
        rounds = phase.EliminatingAnswerData
        totalRounds = rounds.size
        currentRoundIndex = 0
        correctCount = 0
        answerSent = false
        penaltyActive = false

        binding.questionText.text = phase.QuestionText
        binding.wrongAnswerOverlay.visibility = View.GONE

        updateCounter()
        displayRound(rounds[currentRoundIndex])

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

    private fun displayRound(round: EliminatingAnswerData) {
        val answers = mutableListOf(round.CorrectAnswer)
        answers.addAll(round.IncorrectAnswers.take(2))
        answers.shuffle()

        val buttons = listOf(binding.answer0, binding.answer1, binding.answer2)

        buttons.forEachIndexed { index, button ->
            val text = answers[index]
            val isCorrect = text == round.CorrectAnswer
            button.text = text
            button.alpha = 1.0f
            button.isEnabled = true
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F0EAD6"))
            button.setOnClickListener {
                if (answerSent || penaltyActive) return@setOnClickListener
                onAnswerTapped(isCorrect, buttons, index)
            }
        }
    }

    private fun onAnswerTapped(isCorrect: Boolean, buttons: List<TextView>, tappedIndex: Int) {
        if (isCorrect) {
            correctCount++
            // Flash correct button green briefly
            buttons[tappedIndex].backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#4DB6AC"))
            buttons.forEach { it.isEnabled = false }

            handler.postDelayed({
                if (_binding == null) return@postDelayed
                advanceRound()
            }, 400)
        } else {
            // Wrong answer — red flash + penalty
            penaltyActive = true
            buttons[tappedIndex].backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#C85080"))
            buttons[tappedIndex].alpha = 0.5f
            buttons[tappedIndex].isEnabled = false
            binding.wrongAnswerOverlay.visibility = View.VISIBLE

            handler.postDelayed({
                if (_binding == null) return@postDelayed
                binding.wrongAnswerOverlay.visibility = View.GONE
                penaltyActive = false
            }, 800)
        }
    }

    private fun advanceRound() {
        currentRoundIndex++
        if (currentRoundIndex >= totalRounds) {
            sendAnswer()
            return
        }
        updateCounter()
        displayRound(rounds[currentRoundIndex])
    }

    private fun updateCounter() {
        val remaining = totalRounds - currentRoundIndex
        binding.counterText.text = "$remaining to go"
    }

    private fun sendAnswer() {
        if (answerSent) return
        answerSent = true
        timer?.cancel()
        Log.d("EliminationAnswering", "Completed: $correctCount/$totalRounds correct")
        networkManager.sendEliminationAnswer(correctCount)
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onEliminationMessage === eliminationCb) networkManager.onEliminationMessage = null
        _binding = null
    }
}
