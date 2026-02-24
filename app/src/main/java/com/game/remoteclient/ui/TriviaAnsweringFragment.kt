package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ColorTint
import com.game.protocol.ServerBeginTriviaAnsweringPhase
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentTriviaAnsweringBinding

class TriviaAnsweringFragment : Fragment() {

    private var _binding: FragmentTriviaAnsweringBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var answered = false
    private var questionStartTime = 0L
    private var timer: CountDownTimer? = null

    private var triviaCb: ((ServerBeginTriviaAnsweringPhase) -> Unit)? = null
    private var holdingScreenCb: ((com.game.protocol.ClientHoldingScreenCommandMessage) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTriviaAnsweringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeMessages()

        // Apply pending trivia if it arrived before navigation
        networkManager.pendingTrivia?.let { trivia ->
            displayTrivia(trivia)
            networkManager.pendingTrivia = null
        }
    }

    private fun observeMessages() {
        triviaCb = { message ->
            activity?.runOnUiThread { displayTrivia(message) }
        }
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }

        networkManager.onTriviaMessage = triviaCb
        networkManager.onHoldingScreenMessage = holdingScreenCb
    }

    private fun displayTrivia(trivia: ServerBeginTriviaAnsweringPhase) {
        answered = false
        questionStartTime = SystemClock.elapsedRealtime()

        binding.questionText.text = trivia.QuestionText

        val backgroundColor = trivia.BackgroundTint?.let { colorTintToInt(it) } ?: Color.parseColor("#333333")
        val backgroundSecondary = trivia.SecondaryTint?.let { colorTintToInt(it) } ?: Color.parseColor("#222222")
        binding.sunburstBackground.setColors(backgroundColor, backgroundSecondary)

        val buttons = listOf(binding.answer0, binding.answer1, binding.answer2, binding.answer3)
        val isFinals = trivia.RoundType == 5
        buttons.forEachIndexed { index, button ->
            if (index < trivia.Answers.size) {
                val answer = trivia.Answers[index]
                button.text = answer.DisplayText
                button.visibility = View.VISIBLE
                button.alpha = 1.0f
                button.setOnClickListener {
                    if (!answered) {
                        answered = true
                        timer?.cancel()
                        val answerTime = (SystemClock.elapsedRealtime() - questionStartTime) / 1000.0
                        highlightSelected(buttons, index)
                        Log.d("TriviaAnswering", "Selected answer $index: ${answer.DisplayText} (${answerTime}s)")
                        networkManager.sendTriviaAnswer(answer.DisplayIndex, answerTime)
                    }
                }
            } else {
                button.visibility = View.INVISIBLE
            }
        }

        // Send a "no answer" response when time runs out
        timer?.cancel()
        val durationMs = (trivia.QuestionDuration * 1000).toLong()
        timer = object : CountDownTimer(durationMs, durationMs) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (_binding == null || answered) return
                answered = true
                Log.d("TriviaAnswering", "Time expired, sending no-answer")
                networkManager.sendTriviaAnswer(-1, trivia.QuestionDuration)
            }
        }.start()
    }

    private fun highlightSelected(buttons: List<Button>, selectedIndex: Int) {
        buttons.forEachIndexed { index, button ->
            button.alpha = if (index == selectedIndex) 1.0f else 0.3f
        }
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    private fun colorTintToInt(tint: ColorTint): Int {
        return Color.argb(
            (tint.a * 255).toInt().coerceIn(0, 255),
            (tint.r * 255).toInt().coerceIn(0, 255),
            (tint.g * 255).toInt().coerceIn(0, 255),
            (tint.b * 255).toInt().coerceIn(0, 255)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        if (networkManager.onTriviaMessage === triviaCb) networkManager.onTriviaMessage = null
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        _binding = null
    }
}
