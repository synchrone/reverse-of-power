package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage

import com.game.remoteclient.BuildConfig
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentEndOfGameFactBinding

class EndOfGameFactFragment : Fragment() {

    private var _binding: FragmentEndOfGameFactBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var isScrollOpened = false
    private var canContinue = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEndOfGameFactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sunburstBackground.setColors(
            Color.parseColor("#2196F3"),
            Color.parseColor("#1976D2")
        )

        val factMessage = networkManager.pendingEndOfGameFact
        networkManager.pendingEndOfGameFact = null

        val factoids = resources.getStringArray(R.array.end_of_game_factoids)
        val factIndex = Math.abs(factMessage?.FactNumber ?: 0) % factoids.size
        binding.scrollView.factText = factoids[factIndex]
        binding.scrollView.isOpened = false
        binding.scrollView.setOnClickListener {
            if (!isScrollOpened) {
                openScroll()
            } else if (BuildConfig.DEBUG && !canContinue) {
                canContinue = true
                enableContinue()
            }
        }

        binding.continueButton.setOnClickListener {
            val nav = findNavController()
            if (!nav.popBackStack(R.id.nameEntryFragment, false)) {
                nav.popBackStack()
            }
            binding.continueButton.isEnabled = false
        }

        observeMessages()
    }

    private fun openScroll() {
        isScrollOpened = true
        binding.scrollView.alpha = 0.8f
        binding.scrollView.animate().alpha(1f).setDuration(300).start()
        binding.scrollView.isOpened = true

        if (canContinue) enableContinue()
    }

    private fun enableContinue() {
        binding.continueButton.visibility = View.VISIBLE
    }

    private fun observeMessages() {
        holdingScreenCb = { _ ->
            activity?.runOnUiThread {
                findNavController().popBackStack(R.id.holdingScreenFragment, false)
            }
        }
        networkManager.onHoldingScreenMessage = holdingScreenCb

        // Delay reset-to-name: show the fact first, let the user continue manually
        networkManager.onResetToNameEntryInterceptor = {
            activity?.runOnUiThread {
                canContinue = true
                if (isScrollOpened) enableContinue()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        networkManager.onResetToNameEntryInterceptor = null
        _binding = null
    }
}
