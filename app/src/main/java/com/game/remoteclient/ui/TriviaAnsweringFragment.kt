package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ColorTint
import com.game.protocol.PowerType
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
    private var wrongAnswerCount = 0
    private var penaltyActive = false
    private val handler = Handler(Looper.getMainLooper())

    private var triviaCb: ((ServerBeginTriviaAnsweringPhase) -> Unit)? = null
    private var holdingScreenCb: ((com.game.protocol.ClientHoldingScreenCommandMessage) -> Unit)? = null

    // Gloop cross-button swipe state
    private var gloopOverlays = emptyList<GloopOverlayView>()
    private var gloopButtons = emptyList<Button>()
    private var lastGloopTarget: GloopOverlayView? = null
    private var gloopDownX = 0f
    private var gloopDownY = 0f
    private var gloopMoved = false

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
        wrongAnswerCount = 0
        penaltyActive = false
        questionStartTime = SystemClock.elapsedRealtime()

        binding.questionText.text = trivia.QuestionText
        binding.wrongAnswerOverlay.visibility = View.GONE
        binding.finalsWarning.visibility = if (trivia.RoundType == 5) View.VISIBLE else View.GONE

        val backgroundColor = trivia.BackgroundTint?.let { colorTintToInt(it) } ?: Color.parseColor("#333333")
        val backgroundSecondary = trivia.SecondaryTint?.let { colorTintToInt(it) } ?: Color.parseColor("#222222")
        binding.sunburstBackground.setColors(backgroundColor, backgroundSecondary)

        val buttons = listOf(binding.answer0, binding.answer1, binding.answer2, binding.answer3)
        val nibblersOverlays = listOf(binding.nibblersOverlay0, binding.nibblersOverlay1, binding.nibblersOverlay2, binding.nibblersOverlay3)
        val iceOverlays = listOf(binding.iceOverlay0, binding.iceOverlay1, binding.iceOverlay2, binding.iceOverlay3)
        val gloopOverlays = listOf(binding.gloopOverlay0, binding.gloopOverlay1, binding.gloopOverlay2, binding.gloopOverlay3)
        val zipperOverlays = listOf(binding.zipperOverlay0, binding.zipperOverlay1, binding.zipperOverlay2, binding.zipperOverlay3)
        val letterScatterOverlays = listOf(binding.letterScatterOverlay0, binding.letterScatterOverlay1, binding.letterScatterOverlay2, binding.letterScatterOverlay3)
        val isFinals = trivia.RoundType == 5

        // Check for power play stacks
        val nibblersCount = trivia.PowerPlays.filter { it.PowerType == PowerType.NIBBLERS }.sumOf { it.Count }
        val freezeCount = trivia.PowerPlays.filter { it.PowerType == PowerType.FREEZE }.sumOf { it.Count }
        val gloopCount = trivia.PowerPlays.filter { it.PowerType == PowerType.GLOOP }.sumOf { it.Count }
        val zipperCount = trivia.PowerPlays.filter { it.PowerType == PowerType.ZIPPERS }.sumOf { it.Count }
        val letterScatterCount = trivia.PowerPlays.filter { it.PowerType == PowerType.LETTER_SCATTER }.sumOf { it.Count }

        // Reset all overlays
        nibblersOverlays.forEach { it.reset() }
        iceOverlays.forEach { it.reset() }
        gloopOverlays.forEach { it.reset() }
        zipperOverlays.forEach { it.reset() }
        letterScatterOverlays.forEach { it.reset() }

        // Map answers by DisplayIndex so 50/50 (2 answers) lands on correct button positions
        val answerByIndex = trivia.Answers.associateBy { it.DisplayIndex }

        buttons.forEachIndexed { index, button ->
            val answer = answerByIndex[index]
            if (answer != null) {
                button.text = answer.DisplayText
                button.visibility = View.VISIBLE
                button.alpha = 1.0f
                button.isEnabled = true
                button.setOnClickListener {
                    if (answered || penaltyActive) return@setOnClickListener

                    if (isFinals && !answer.IsCorrect) {
                        onWrongAnswer(buttons)
                    } else {
                        answered = true
                        timer?.cancel()
                        val answerTime = (SystemClock.elapsedRealtime() - questionStartTime) / 1000.0
                        highlightSelected(buttons, index)
                        Log.d("TriviaAnswering", "Selected answer $index: ${answer.DisplayText} (${answerTime}s)")
                        networkManager.sendTriviaAnswer(answer.DisplayIndex, answerTime, wrongAnswerCount)
                    }
                }

                // Letter scatter subsumes nibblers when both are active
                if (letterScatterCount > 0) {
                    letterScatterOverlays[index].activate(answer.DisplayText, index, nibblersCount)
                    button.setTextColor(Color.TRANSPARENT)
                } else if (nibblersCount > 0) {
                    nibblersOverlays[index].activate(nibblersCount, index, answer.DisplayText)
                    button.setTextColor(Color.TRANSPARENT)
                }

                // Apply freeze overlay if active
                if (freezeCount > 0) {
                    val overlay = iceOverlays[index]
                    overlay.activate(freezeCount, index)
                    overlay.onIceShattered = null
                }

                // Apply gloop overlay if active (stacks on top of freeze)
                if (gloopCount > 0) {
                    val overlay = gloopOverlays[index]
                    overlay.activate(gloopCount, index)
                    overlay.onGloopCleared = null
                }

                // Apply zipper overlay if active
                if (zipperCount > 0) {
                    val overlay = zipperOverlays[index]
                    overlay.activate(zipperCount, index)
                    overlay.onZipperOpened = null
                }

            } else {
                button.visibility = View.INVISIBLE
            }
        }

        // Set up full-screen gloop touch interceptor for cross-button swiping
        if (gloopCount > 0) setupGloopInterceptor(buttons)

        // Check for bombles power plays — multiple players can stack
        val bomblesCount = trivia.PowerPlays.filter { it.PowerType == PowerType.BOMBLES }.sumOf { it.Count }
        if (bomblesCount > 0) {
            binding.bomblesOverlay.activate(bomblesCount)
            binding.bomblesOverlay.onBombleTouched = {
                if (!answered && !penaltyActive) {
                    onBombleTouched(buttons)
                }
            }
        } else {
            binding.bomblesOverlay.deactivate()
        }

        // Check for bug power play — rendered on top of all other effects
        val bugCount = trivia.PowerPlays.filter { it.PowerType == PowerType.BUG }.sumOf { it.Count }
        if (bugCount > 0) {
            binding.bugOverlay.activate(bugCount)
            binding.bugOverlay.onBugCleared = null
        } else {
            binding.bugOverlay.reset()
        }

        // Check for lockdown power play — full-screen chains with padlocks
        val lockdownCount = trivia.PowerPlays.filter { it.PowerType == PowerType.LOCKDOWN }.sumOf { it.Count }
        if (lockdownCount > 0) {
            binding.lockdownOverlay.activate(lockdownCount * 3)
            binding.lockdownOverlay.onLockdownCleared = null
        } else {
            binding.lockdownOverlay.reset()
        }

        // Check for disco inferno — flashing color grid at bottom of effect stack
        val discoCount = trivia.PowerPlays.filter { it.PowerType == PowerType.DISCO_INFERNO }.sumOf { it.Count }
        if (discoCount > 0) {
            binding.discoOverlay.activate()
        } else {
            binding.discoOverlay.reset()
        }

        // No visual trivia effect needed for:
        // - BET: rewards the caster, no hindrance to the target
        // - POINTS_DOUBLER / FIFTY_FIFTY: server adjusts answers/scoring, no overlay
        // - DOUBLE_TROUBLE variants: server sends the component types (FREEZE+GLOOP etc.) individually
        // - LETTER_SCATTER: handled per-button above (dancing text, subsumes nibblers)

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

    private fun onWrongAnswer(buttons: List<Button>) {
        wrongAnswerCount++
        penaltyActive = true
        Log.d("TriviaAnswering", "Wrong answer #$wrongAnswerCount, 1.9s penalty")

        // Flash full-screen red overlay
        binding.wrongAnswerOverlay.visibility = View.VISIBLE

        // Disable all buttons during penalty
        buttons.forEach { it.isEnabled = false }

        // After 2 seconds, remove overlay and re-enable buttons
        handler.postDelayed({
            if (_binding == null) return@postDelayed
            binding.wrongAnswerOverlay.visibility = View.GONE
            penaltyActive = false
            buttons.forEach { button ->
                if (button.visibility == View.VISIBLE) {
                    button.isEnabled = true
                }
            }
        }, 1900)
    }

    private fun onBombleTouched(buttons: List<Button>) {
        penaltyActive = true
        Log.d("TriviaAnswering", "Bomble touched! 1.9s penalty")

        binding.bomblesExplosionOverlay.visibility = View.VISIBLE
        buttons.forEach { it.isEnabled = false }

        handler.postDelayed({
            if (_binding == null) return@postDelayed
            binding.bomblesExplosionOverlay.visibility = View.GONE
            penaltyActive = false
            buttons.forEach { button ->
                if (button.visibility == View.VISIBLE) {
                    button.isEnabled = true
                }
            }
        }, 1900)
    }

    private fun highlightSelected(buttons: List<Button>, selectedIndex: Int) {
        buttons.forEachIndexed { index, button ->
            button.alpha = if (index == selectedIndex) 1.0f else 0.3f
        }
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    private fun setupGloopInterceptor(buttons: List<Button>) {
        gloopOverlays = listOf(
            binding.gloopOverlay0, binding.gloopOverlay1,
            binding.gloopOverlay2, binding.gloopOverlay3
        )
        gloopButtons = buttons

        // Hide interceptor when each overlay clears; remove when all done
        gloopOverlays.forEach { overlay ->
            overlay.onGloopCleared = {
                if (gloopOverlays.none { it.visibility == View.VISIBLE }) {
                    binding.gloopTouchInterceptor.visibility = View.GONE
                }
            }
        }

        binding.gloopTouchInterceptor.visibility = View.VISIBLE
        binding.gloopTouchInterceptor.setOnTouchListener { _, event ->
            val rx = event.rawX
            val ry = event.rawY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    gloopDownX = rx
                    gloopDownY = ry
                    gloopMoved = false
                    lastGloopTarget = null

                    val hit = findGloopAt(rx, ry)
                    if (hit != null) {
                        val loc = IntArray(2)
                        hit.getLocationOnScreen(loc)
                        hit.handleSwipe(rx - loc[0], ry - loc[1], isNewContact = true)
                        lastGloopTarget = hit
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = rx - gloopDownX
                    val dy = ry - gloopDownY
                    if (dx * dx + dy * dy > 20 * 20) gloopMoved = true

                    val hit = findGloopAt(rx, ry)
                    if (hit != null) {
                        val loc = IntArray(2)
                        hit.getLocationOnScreen(loc)
                        hit.handleSwipe(rx - loc[0], ry - loc[1], isNewContact = hit != lastGloopTarget)
                        lastGloopTarget = hit
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // If it was a tap (no significant movement), forward click to button underneath
                    if (!gloopMoved) {
                        val btnIndex = findButtonAt(rx, ry)
                        if (btnIndex >= 0) {
                            gloopButtons[btnIndex].performClick()
                        }
                    }
                    lastGloopTarget = null
                    true
                }
                else -> true
            }
        }
    }

    private fun findGloopAt(screenX: Float, screenY: Float): GloopOverlayView? {
        for (overlay in gloopOverlays) {
            if (overlay.visibility != View.VISIBLE) continue
            val loc = IntArray(2)
            overlay.getLocationOnScreen(loc)
            if (screenX >= loc[0] && screenX < loc[0] + overlay.width &&
                screenY >= loc[1] && screenY < loc[1] + overlay.height) {
                return overlay
            }
        }
        return null
    }

    private fun findButtonAt(screenX: Float, screenY: Float): Int {
        for ((i, button) in gloopButtons.withIndex()) {
            if (button.visibility != View.VISIBLE) continue
            val loc = IntArray(2)
            button.getLocationOnScreen(loc)
            if (screenX >= loc[0] && screenX < loc[0] + button.width &&
                screenY >= loc[1] && screenY < loc[1] + button.height) {
                return i
            }
        }
        return -1
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
        handler.removeCallbacksAndMessages(null)
        binding.bomblesOverlay.deactivate()
        listOf(binding.nibblersOverlay0, binding.nibblersOverlay1, binding.nibblersOverlay2, binding.nibblersOverlay3).forEach { it.reset() }
        listOf(binding.gloopOverlay0, binding.gloopOverlay1, binding.gloopOverlay2, binding.gloopOverlay3).forEach { it.reset() }
        if (networkManager.onTriviaMessage === triviaCb) networkManager.onTriviaMessage = null
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        _binding = null
    }
}
