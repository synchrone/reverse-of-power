package com.game.remoteclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android:view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentWaitingRoomBinding
import com.game.remoteclient.models.GameServer
import com.game.remoteclient.models.GameState
import com.game.remoteclient.models.Player
import com.game.remoteclient.network.NetworkManager
import kotlinx.coroutines.launch

class WaitingRoomFragment : Fragment() {

    private var _binding: FragmentWaitingRoomBinding? = null
    private val binding get() = _binding!!

    private val args: WaitingRoomFragmentArgs by navArgs()
    private lateinit var networkManager: NetworkManager
    private lateinit var playerAdapter: PlayerAdapter

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

        networkManager = NetworkManager.getInstance()
        setupRecyclerView()
        setupListeners()
        connectToServer()
        observeGameState()
    }

    private fun setupRecyclerView() {
        playerAdapter = PlayerAdapter()

        binding.playersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playerAdapter
        }
    }

    private fun setupListeners() {
        binding.startGameButton.setOnClickListener {
            networkManager.startGame()
        }
    }

    private fun connectToServer() {
        val player = Player(name = args.playerName)
        val server = GameServer(args.serverIp)

        lifecycleScope.launch {
            val connected = networkManager.connectToServer(server, player)
            if (!connected) {
                // Handle connection failure
                binding.waitingText.text = "Failed to connect to server"
            }
        }
    }

    private fun observeGameState() {
        lifecycleScope.launch {
            networkManager.gameState.collect { state ->
                when (state) {
                    GameState.LOBBY -> {
                        // Show start button if host
                        // For now, always show it for testing
                        binding.startGameButton.visibility = View.VISIBLE
                    }
                    GameState.GET_READY -> {
                        navigateToGetReady()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            networkManager.players.collect { players ->
                playerAdapter.submitList(players)
                if (players.isNotEmpty()) {
                    binding.waitingProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun navigateToGetReady() {
        val action = WaitingRoomFragmentDirections.actionWaitingRoomToGetReady()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
