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
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentContinueBinding

class ContinueFragment : Fragment() {

    private var _binding: FragmentContinueBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContinueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.continueButton.setOnClickListener {
            binding.continueButton.isEnabled = false
            networkManager.sendContinueButtonPressed()
        }

        holdingScreenCb = { _ ->
            activity?.runOnUiThread {
                findNavController().popBackStack(R.id.holdingScreenFragment, false)
            }
        }
        networkManager.onHoldingScreenMessage = holdingScreenCb
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        _binding = null
    }
}
