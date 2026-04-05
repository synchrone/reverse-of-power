package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientEndOfGameFactCommandMessage
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.ClientQuizCommandMessage
import com.game.protocol.ColorTint
import com.game.protocol.ServerBeginTriviaAnsweringPhase
import com.game.protocol.ServerBeginLinkingAnsweringPhase
import com.game.protocol.ServerBeginPowerPlayPhase
import com.game.protocol.ServerBeginEliminatingAnsweringPhase
import com.game.protocol.ServerBeginMatchingAnsweringPhase
import com.game.protocol.ServerBeginMissingLetterAnsweringPhase
import com.game.protocol.ServerBeginSortingAnsweringPhase
import com.game.protocol.ServerColourMessage
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentHoldingScreenBinding

class HoldingScreenFragment : Fragment() {

    private var _binding: FragmentHoldingScreenBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    // Default colors (beige/taupe from screenshot)
    private var backgroundColor = Color.parseColor("#C4B8A8")
    private var backgroundSecondary = Color.parseColor("#D4C8B8")
    private var foregroundColor = Color.TRANSPARENT

    // Store references to our callbacks so we only clear our own
    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var colourCb: ((ServerColourMessage) -> Unit)? = null
    private var categoryCb: ((com.game.protocol.ServerCategorySelectChoices) -> Unit)? = null
    private var triviaCb: ((ServerBeginTriviaAnsweringPhase) -> Unit)? = null
    private var quizCb: ((ClientQuizCommandMessage) -> Unit)? = null
    private var powerPlayCb: ((ServerBeginPowerPlayPhase) -> Unit)? = null
    private var linkingCb: ((ServerBeginLinkingAnsweringPhase) -> Unit)? = null
    private var orderingCb: ((ServerBeginLinkingAnsweringPhase) -> Unit)? = null
    private var sortingCb: ((ServerBeginSortingAnsweringPhase) -> Unit)? = null
    private var eliminationCb: ((ServerBeginEliminatingAnsweringPhase) -> Unit)? = null
    private var missingLetterCb: ((ServerBeginMissingLetterAnsweringPhase) -> Unit)? = null
    private var matchingCb: ((ServerBeginMatchingAnsweringPhase) -> Unit)? = null
    private var endOfGameFactCb: ((ClientEndOfGameFactCommandMessage) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHoldingScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyColors()
        observeMessages()
    }

    private fun observeMessages() {
        holdingScreenCb = { message ->
            activity?.runOnUiThread { handleHoldingScreenMessage(message) }
        }
        colourCb = { message ->
            activity?.runOnUiThread { handleColourMessage(message) }
        }
        categoryCb = { _ ->
            activity?.runOnUiThread { navigateToCategorySelection() }
        }
        triviaCb = { _ ->
            activity?.runOnUiThread { navigateToTriviaAnswering() }
        }
        quizCb = { message ->
            activity?.runOnUiThread {
                when (message.action) {
                    ClientQuizCommandMessage.ACTION_CONTINUE -> navigateToContinue()
                }
            }
        }
        powerPlayCb = { _ ->
            activity?.runOnUiThread { navigateToPowerPlay() }
        }
        linkingCb = { _ ->
            activity?.runOnUiThread { navigateToLinkingPairs() }
        }
        orderingCb = { _ ->
            activity?.runOnUiThread { navigateToOrderingAnswers() }
        }
        sortingCb = { _ ->
            activity?.runOnUiThread { navigateToSortingAnswers() }
        }
        eliminationCb = { _ ->
            activity?.runOnUiThread { navigateToEliminationAnswering() }
        }
        missingLetterCb = { _ ->
            activity?.runOnUiThread { navigateToMissingLetterAnswering() }
        }
        matchingCb = { _ ->
            activity?.runOnUiThread { navigateToMatchingAnswering() }
        }
        endOfGameFactCb = { _ ->
            activity?.runOnUiThread { navigateToEndOfGameFact() }
        }

        networkManager.onHoldingScreenMessage = holdingScreenCb
        networkManager.onColourMessage = colourCb
        networkManager.onCategoryChoicesMessage = categoryCb
        networkManager.onTriviaMessage = triviaCb
        networkManager.onQuizCommand = quizCb
        networkManager.onPowerPlayMessage = powerPlayCb
        networkManager.onLinkingMessage = linkingCb
        networkManager.onOrderingMessage = orderingCb
        networkManager.onSortingMessage = sortingCb
        networkManager.onEliminationMessage = eliminationCb
        networkManager.onMissingLetterMessage = missingLetterCb
        networkManager.onMatchingMessage = matchingCb
        networkManager.onEndOfGameFact = endOfGameFactCb
    }

    private fun navigateToCategorySelection() {
        findNavController().navigate(R.id.action_holdingScreen_to_categorySelection)
    }

    private fun navigateToTriviaAnswering() {
        findNavController().navigate(R.id.action_holdingScreen_to_triviaAnswering)
    }

    private fun navigateToContinue() {
        findNavController().navigate(R.id.action_holdingScreen_to_continue)
    }

    private fun navigateToPowerPlay() {
        findNavController().navigate(R.id.action_holdingScreen_to_powerPlay)
    }

    private fun navigateToLinkingPairs() {
        findNavController().navigate(R.id.action_holdingScreen_to_linkingPairs)
    }

    private fun navigateToOrderingAnswers() {
        findNavController().navigate(R.id.action_holdingScreen_to_linkingAnswers)
    }

    private fun navigateToSortingAnswers() {
        findNavController().navigate(R.id.action_holdingScreen_to_sortingAnswers)
    }

    private fun navigateToEliminationAnswering() {
        findNavController().navigate(R.id.action_holdingScreen_to_eliminationAnswering)
    }

    private fun navigateToMissingLetterAnswering() {
        findNavController().navigate(R.id.action_holdingScreen_to_missingLetterAnswering)
    }

    private fun navigateToMatchingAnswering() {
        findNavController().navigate(R.id.action_holdingScreen_to_matchingAnswering)
    }

    private fun navigateToEndOfGameFact() {
        findNavController().navigate(R.id.action_holdingScreen_to_endOfGameFact)
    }



    private fun handleHoldingScreenMessage(message: ClientHoldingScreenCommandMessage) {
        val text = message.HoldingScreenText.replace("\\n", "\n")
        val defaultText = when (message.HoldingScreenType) {
            9 -> "Get ready!"
            else -> "Look at the TV"
        }
        binding.retroTv.text = text.ifEmpty { defaultText }
    }

    private fun isBlack(tint: ColorTint): Boolean =
        tint.r == 0f && tint.g == 0f && tint.b == 0f

    private fun handleColourMessage(message: ServerColourMessage) {
        if (isBlack(message.BackgroundTint) && isBlack(message.PrimaryTint) && isBlack(message.SecondaryTint)) {
            backgroundColor = Color.parseColor("#C4B8A8")
            backgroundSecondary = Color.parseColor("#D4C8B8")
            foregroundColor = Color.TRANSPARENT
        } else {
            backgroundColor = colorTintToInt(message.BackgroundTint)
            foregroundColor = colorTintToInt(message.PrimaryTint)
            backgroundSecondary = colorTintToInt(message.SecondaryTint)
        }
        applyColors()
    }

    private fun colorTintToInt(tint: ColorTint): Int {
        return Color.argb(
            (tint.a * 255).toInt().coerceIn(0, 255),
            (tint.r * 255).toInt().coerceIn(0, 255),
            (tint.g * 255).toInt().coerceIn(0, 255),
            (tint.b * 255).toInt().coerceIn(0, 255)
        )
    }

    private fun applyColors() {
        binding.sunburstBackground.setColors(backgroundColor, backgroundSecondary)
        binding.retroTv.setFillColor(foregroundColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onColourMessage === colourCb) networkManager.onColourMessage = null
        if (networkManager.onCategoryChoicesMessage === categoryCb) networkManager.onCategoryChoicesMessage = null
        if (networkManager.onTriviaMessage === triviaCb) networkManager.onTriviaMessage = null
        if (networkManager.onQuizCommand === quizCb) networkManager.onQuizCommand = null
        if (networkManager.onPowerPlayMessage === powerPlayCb) networkManager.onPowerPlayMessage = null
        if (networkManager.onLinkingMessage === linkingCb) networkManager.onLinkingMessage = null
        if (networkManager.onOrderingMessage === orderingCb) networkManager.onOrderingMessage = null
        if (networkManager.onSortingMessage === sortingCb) networkManager.onSortingMessage = null
        if (networkManager.onEliminationMessage === eliminationCb) networkManager.onEliminationMessage = null
        if (networkManager.onMissingLetterMessage === missingLetterCb) networkManager.onMissingLetterMessage = null
        if (networkManager.onMatchingMessage === matchingCb) networkManager.onMatchingMessage = null
        if (networkManager.onEndOfGameFact === endOfGameFactCb) networkManager.onEndOfGameFact = null
        _binding = null
    }
}
