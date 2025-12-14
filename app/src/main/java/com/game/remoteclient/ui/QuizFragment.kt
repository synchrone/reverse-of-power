package com.game.remoteclient.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentQuizBinding
import com.game.remoteclient.models.GameState
import com.game.remoteclient.models.QuizQuestion
import com.game.remoteclient.network.NetworkManager
import kotlinx.coroutines.launch

class QuizFragment : Fragment() {

    private var _binding: FragmentQuizBinding? = null
    private val binding get() = _binding!!

    private lateinit var networkManager: NetworkManager
    private var countDownTimer: CountDownTimer? = null
    private var answerSubmitted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        networkManager = NetworkManager.getInstance()
        setupAnswerButtons()
        observeQuestion()
        observeGameState()
    }

    private fun setupAnswerButtons() {
        binding.answerAButton.setOnClickListener { submitAnswer(0) }
        binding.answerBButton.setOnClickListener { submitAnswer(1) }
        binding.answerCButton.setOnClickListener { submitAnswer(2) }
        binding.answerDButton.setOnClickListener { submitAnswer(3) }
    }

    private fun observeQuestion() {
        lifecycleScope.launch {
            networkManager.currentQuestion.collect { question ->
                question?.let {
                    displayQuestion(it)
                }
            }
        }
    }

    private fun observeGameState() {
        lifecycleScope.launch {
            networkManager.gameState.collect { state ->
                when (state) {
                    GameState.WAITING -> {
                        navigateToGetReady()
                    }
                    GameState.GET_READY -> {
                        navigateToGetReady()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun displayQuestion(question: QuizQuestion) {
        answerSubmitted = false
        binding.answerOverlay.visibility = View.GONE
        binding.answerSubmittedText.visibility = View.GONE

        binding.questionText.text = question.questionText

        if (question.answers.size >= 4) {
            binding.answerAButton.text = question.answers[0]
            binding.answerBButton.text = question.answers[1]
            binding.answerCButton.text = question.answers[2]
            binding.answerDButton.text = question.answers[3]
        }

        enableAnswerButtons()
        startTimer(question.timeLimit)
    }

    private fun startTimer(seconds: Int) {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                binding.timerText.text = secondsLeft.toString()

                // Change color as time runs out
                if (secondsLeft <= 5) {
                    binding.timerText.setTextColor(resources.getColor(R.color.answer_b, null))
                } else {
                    binding.timerText.setTextColor(resources.getColor(R.color.text_primary, null))
                }
            }

            override fun onFinish() {
                binding.timerText.text = "0"
                if (!answerSubmitted) {
                    submitAnswer(-1) // No answer selected
                }
            }
        }.start()
    }

    private fun submitAnswer(answerIndex: Int) {
        if (answerSubmitted) return

        answerSubmitted = true
        countDownTimer?.cancel()

        networkManager.sendAnswer(answerIndex)

        disableAnswerButtons()
        showAnswerSubmitted()
    }

    private fun showAnswerSubmitted() {
        binding.answerOverlay.visibility = View.VISIBLE
        binding.answerSubmittedText.visibility = View.VISIBLE
    }

    private fun enableAnswerButtons() {
        binding.answerAButton.isEnabled = true
        binding.answerBButton.isEnabled = true
        binding.answerCButton.isEnabled = true
        binding.answerDButton.isEnabled = true
    }

    private fun disableAnswerButtons() {
        binding.answerAButton.isEnabled = false
        binding.answerBButton.isEnabled = false
        binding.answerCButton.isEnabled = false
        binding.answerDButton.isEnabled = false
    }

    private fun navigateToGetReady() {
        val action = QuizFragmentDirections.actionQuizToGetReady()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}
