package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.ServerBeginCategorySelectOverride
import com.game.protocol.ServerCategorySelectOverrideSuccess
import com.game.protocol.ServerStopCategorySelectOverride
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentPowerPickBinding

class PowerPickFragment : Fragment() {

    private var _binding: FragmentPowerPickBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var overrideSent = false
    private var phaseStartTime = 0L

    // Default colors (beige from screenshot)
    private var backgroundColor = Color.parseColor("#C4B8A8")
    private var backgroundSecondary = Color.parseColor("#D4C8B8")

    // Accepted state colors (blue from screenshot)
    private val acceptedBackground = Color.parseColor("#4A7AB5")
    private val acceptedSecondary = Color.parseColor("#3A6AA5")

    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var stopOverrideCb: ((ServerStopCategorySelectOverride) -> Unit)? = null
    private var overrideSuccessCb: ((ServerCategorySelectOverrideSuccess) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPowerPickBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sunburstBackground.setColors(backgroundColor, backgroundSecondary)
        observeMessages()

        networkManager.pendingCategoryOverride?.let { override ->
            showOverrideOffer(override)
            networkManager.pendingCategoryOverride = null
        }
    }

    private fun observeMessages() {
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }
        stopOverrideCb = { _ ->
            activity?.runOnUiThread { handleStopOverride() }
        }
        overrideSuccessCb = { message ->
            activity?.runOnUiThread { handleOverrideSuccess(message) }
        }

        networkManager.onHoldingScreenMessage = holdingScreenCb
        networkManager.onStopCategoryOverride = stopOverrideCb
        networkManager.onCategoryOverrideSuccess = overrideSuccessCb
    }

    private fun showOverrideOffer(override: ServerBeginCategorySelectOverride) {
        phaseStartTime = SystemClock.elapsedRealtime()
        binding.categoryNameText.text = override.InitialCategorySelectChoice

        binding.pentagonIcon.setOnClickListener {
            if (!overrideSent) {
                onPowerPickTapped()
            }
        }
    }

    private fun onPowerPickTapped() {
        overrideSent = true
        val elapsed = (SystemClock.elapsedRealtime() - phaseStartTime) / 1000.0

        Log.d("PowerPickFragment", "Power Pick used after ${elapsed}s")
        networkManager.sendCategorySelectOverride(elapsed)

        // Switch to accepted state
        showAcceptedState()
    }

    private fun showAcceptedState() {
        binding.pickContainer.visibility = View.GONE
        binding.acceptedContainer.visibility = View.VISIBLE
        binding.acceptedCategoryText.text = binding.categoryNameText.text
        binding.sunburstBackground.setColors(acceptedBackground, acceptedSecondary)
    }

    private fun handleStopOverride() {
        // Always respond with whether we sent an override
        networkManager.sendStopCategoryOverrideResponse(overrideSent = overrideSent)
        if (!overrideSent) {
            // No override used — go back immediately
            navigateToHoldingScreen()
        }
        // If override was sent, wait for ServerCategorySelectOverrideSuccess before navigating
    }

    private fun handleOverrideSuccess(message: ServerCategorySelectOverrideSuccess) {
        Log.d("PowerPickFragment", "Override success=${message.CategorySelectOverrideSuccess} by ${message.CategorySelectOverridePlayerName}")
        navigateToHoldingScreen()
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onStopCategoryOverride === stopOverrideCb) networkManager.onStopCategoryOverride = null
        if (networkManager.onCategoryOverrideSuccess === overrideSuccessCb) networkManager.onCategoryOverrideSuccess = null
        _binding = null
    }
}
