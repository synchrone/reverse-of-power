package com.game.remoteclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentWaitingRoomBinding

class WaitingRoomFragment : Fragment() {

    private var _binding: FragmentWaitingRoomBinding? = null
    private val binding get() = _binding!!

    private val args: WaitingRoomFragmentArgs by navArgs()
    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    companion object {
        private const val ACTION_SHOW_READY_BUTTON = 14
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaitingRoomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeQuizCommands()
    }

    private fun setupListeners() {
        binding.circularStartButton.onProgressComplete = {
            networkManager.sendStartGameButtonPressed()
        }
    }

    private fun observeQuizCommands() {
        networkManager.onQuizCommand = { action ->
            activity?.runOnUiThread {
                handleQuizCommand(action)
            }
        }

        networkManager.onHoldingScreenMessage = { message ->
            activity?.runOnUiThread {
                navigateToHoldingScreen()
            }
        }
    }

    private fun navigateToHoldingScreen() {
        findNavController().navigate(R.id.action_waitingRoom_to_holdingScreen)
    }

    private fun handleQuizCommand(action: Int) {
        when (action) {
            ACTION_SHOW_READY_BUTTON -> {
                showReadyState()
            }
        }
    }

    private fun showReadyState() {
        binding.waitingContainer.visibility = View.GONE
        binding.readyContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        networkManager.onQuizCommand = null
        networkManager.onHoldingScreenMessage = null
        _binding = null
    }
}
