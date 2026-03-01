package com.game.remoteclient.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.MissingLetterAnswerData
import com.game.protocol.ServerBeginMissingLetterAnsweringPhase
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentMissingLetterAnsweringBinding

class MissingLetterAnsweringFragment : Fragment() {

    private var _binding: FragmentMissingLetterAnsweringBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var currentRoundIndex = 0
    private var correctCount = 0
    private var totalRounds = 0
    private var answerSent = false
    private var penaltyActive = false
    private var timer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var rounds: List<MissingLetterAnswerData> = emptyList()

    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var missingLetterCb: ((ServerBeginMissingLetterAnsweringPhase) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMissingLetterAnsweringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sunburstBackground.setColors(
            Color.parseColor("#E8679A"),
            Color.parseColor("#D05888")
        )

        observeMessages()

        networkManager.pendingMissingLetter?.let { phase ->
            startGame(phase)
            networkManager.pendingMissingLetter = null
        }
    }

    private fun observeMessages() {
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }
        missingLetterCb = { message ->
            activity?.runOnUiThread { startGame(message) }
        }

        networkManager.onHoldingScreenMessage = holdingScreenCb
        networkManager.onMissingLetterMessage = missingLetterCb
    }

    private fun startGame(phase: ServerBeginMissingLetterAnsweringPhase) {
        rounds = phase.MissingLetterAnswerData
        totalRounds = rounds.size
        currentRoundIndex = 0
        correctCount = 0
        answerSent = false
        penaltyActive = false

        binding.questionText.text = phase.Question
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

    private val tileSpacing by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics).toInt()
    }
    private val wordGap by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics).toInt()
    }

    private val tileColorLetter = Color.parseColor("#F0EAD6")
    private val tileColorBlank = Color.parseColor("#C8B8A0")
    private val tileColorRevealed = Color.parseColor("#4DB6AC")

    private fun displayRound(round: MissingLetterAnswerData) {
        val missingChar = round.correct.first()
        // Constrain FlowLayout to 60% of screen width, compute tile size to fit ~2 rows
        val screenWidth = resources.displayMetrics.widthPixels
        val layoutWidth = (screenWidth * 0.60).toInt()
        binding.wordTiles.layoutParams = binding.wordTiles.layoutParams.apply { width = layoutWidth }
        binding.wordTiles.setPadding(0, 0, 0, 0)

        val totalItems = round.answerInfo.length // includes spaces (become gap spacers)
        val tilesPerRow = ((totalItems + 1) / 2).coerceIn(4, 12)
        val tileSize = ((layoutWidth - (tilesPerRow - 1) * tileSpacing) / tilesPerRow)
            .coerceAtMost(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f, resources.displayMetrics).toInt())
        val fontSize = tileSize * 0.45f / resources.displayMetrics.scaledDensity

        // Build letter tiles
        binding.wordTiles.removeAllViews()
        binding.wordTiles.horizontalSpacing = tileSpacing
        binding.wordTiles.verticalSpacing = tileSpacing

        for (ch in round.answerInfo) {
            if (ch == ' ') {
                // Word gap spacer
                val spacer = Space(requireContext())
                spacer.layoutParams = ViewGroup.LayoutParams(wordGap, tileSize)
                binding.wordTiles.addView(spacer)
            } else {
                val isMissing = ch.equals(missingChar, ignoreCase = true)
                val tile = TextView(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(tileSize, tileSize)
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#333333"))
                    setBackgroundResource(R.drawable.letter_tile)
                    if (isMissing) {
                        text = ""
                        backgroundTintList = ColorStateList.valueOf(tileColorBlank)
                        tag = "missing"
                    } else {
                        text = ch.toString()
                        backgroundTintList = ColorStateList.valueOf(tileColorLetter)
                    }
                }
                binding.wordTiles.addView(tile)
            }
        }

        // Set up letter buttons
        val letterButtons = getLetterButtons()
        val letters = round.letters.toList()

        letterButtons.forEachIndexed { index, button ->
            if (index < letters.size) {
                val letter = letters[index].toString()
                val isCorrect = letter.equals(round.correct, ignoreCase = true)
                button.text = letter
                button.visibility = View.VISIBLE
                button.alpha = 1f
                button.isEnabled = true
                button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0EAD6"))
                button.setOnClickListener {
                    if (answerSent || penaltyActive) return@setOnClickListener
                    onLetterTapped(isCorrect, round, letterButtons, index)
                }
            } else {
                button.visibility = View.GONE
            }
        }
    }

    private fun revealTiles(round: MissingLetterAnswerData) {
        val missingChar = round.correct.first()
        for (i in 0 until binding.wordTiles.childCount) {
            val child = binding.wordTiles.getChildAt(i)
            if (child is TextView && child.tag == "missing") {
                child.text = missingChar.toString()
                child.backgroundTintList = ColorStateList.valueOf(tileColorRevealed)
            }
        }
    }

    private fun onLetterTapped(
        isCorrect: Boolean,
        round: MissingLetterAnswerData,
        buttons: List<TextView>,
        tappedIndex: Int
    ) {
        if (isCorrect) {
            correctCount++
            buttons[tappedIndex].backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#4DB6AC"))
            revealTiles(round)
            buttons.forEach { it.isEnabled = false }

            handler.postDelayed({
                if (_binding == null) return@postDelayed
                advanceRound()
            }, 400)
        } else {
            penaltyActive = true
            buttons[tappedIndex].backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#C85080"))
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

    private fun getLetterButtons(): List<TextView> =
        listOf(binding.letter0, binding.letter1, binding.letter2, binding.letter3, binding.letter4)

    private fun sendAnswer() {
        if (answerSent) return
        answerSent = true
        timer?.cancel()
        Log.d("MissingLetterAnswering", "Completed: $correctCount/$totalRounds correct")
        networkManager.sendMissingLetterAnswer(correctCount)
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onMissingLetterMessage === missingLetterCb) networkManager.onMissingLetterMessage = null
        _binding = null
    }
}
