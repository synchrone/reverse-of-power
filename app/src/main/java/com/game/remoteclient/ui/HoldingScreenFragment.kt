package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.ColorTint
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
    private var foregroundColor = Color.WHITE

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
        networkManager.onHoldingScreenMessage = { message ->
            activity?.runOnUiThread {
                handleHoldingScreenMessage(message)
            }
        }

        networkManager.onColourMessage = { message ->
            activity?.runOnUiThread {
                handleColourMessage(message)
            }
        }

        networkManager.onCategoryChoicesMessage = { _ ->
            activity?.runOnUiThread {
                navigateToCategorySelection()
            }
        }
    }

    private fun navigateToCategorySelection() {
        findNavController().navigate(R.id.action_holdingScreen_to_categorySelection)
    }

    private fun handleHoldingScreenMessage(message: ClientHoldingScreenCommandMessage) {
        binding.retroTvView.text = message.HoldingScreenText.replace("\\n", "\n")
    }

    private fun handleColourMessage(message: ServerColourMessage) {
        backgroundColor = colorTintToInt(message.BackgroundTint)
        foregroundColor = colorTintToInt(message.PrimaryTint)
        backgroundSecondary = colorTintToInt(message.SecondaryTint)
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
        binding.retroTvView.setForegroundColor(foregroundColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        networkManager.onHoldingScreenMessage = null
        networkManager.onColourMessage = null
        networkManager.onCategoryChoicesMessage = null
        _binding = null
    }
}
