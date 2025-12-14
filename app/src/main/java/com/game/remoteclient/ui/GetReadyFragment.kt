package com.game.remoteclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.game.remoteclient.databinding.FragmentGetReadyBinding
import com.game.remoteclient.models.GameState
import com.game.remoteclient.network.NetworkManager
import kotlinx.coroutines.launch

class GetReadyFragment : Fragment() {

    private var _binding: FragmentGetReadyBinding? = null
    private val binding get() = _binding!!

    private lateinit var networkManager: NetworkManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGetReadyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        networkManager = NetworkManager.getInstance()
        observeGameState()
    }

    private fun observeGameState() {
        lifecycleScope.launch {
            networkManager.gameState.collect { state ->
                when (state) {
                    GameState.PLAYING -> {
                        navigateToQuiz()
                    }
                    GameState.WAITING -> {
                        binding.statusText.text = "Waiting for next round..."
                    }
                    else -> {}
                }
            }
        }
    }

    private fun navigateToQuiz() {
        val action = GetReadyFragmentDirections.actionGetReadyToQuiz()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
