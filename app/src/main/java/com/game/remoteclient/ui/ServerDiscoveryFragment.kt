package com.game.remoteclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentServerDiscoveryBinding
import com.game.remoteclient.models.GameServer
import com.game.remoteclient.network.NetworkManager
import kotlinx.coroutines.launch

class ServerDiscoveryFragment : Fragment() {

    private var _binding: FragmentServerDiscoveryBinding? = null
    private val binding get() = _binding!!

    private lateinit var networkManager: NetworkManager
    private lateinit var serverAdapter: ServerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServerDiscoveryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        networkManager = NetworkManager.getInstance()
        setupRecyclerView()
        setupListeners()
        startServerScan()
    }

    private fun setupRecyclerView() {
        serverAdapter = ServerAdapter { server ->
            onServerSelected(server)
        }

        binding.serversRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = serverAdapter
        }
    }

    private fun setupListeners() {
        binding.connectButton.setOnClickListener {
            val ipAddress = binding.ipAddressInput.text.toString().trim()
            if (ipAddress.isNotEmpty()) {
                val server = GameServer(ipAddress)
                onServerSelected(server)
            } else {
                Toast.makeText(requireContext(), "Please enter an IP address", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startServerScan() {
        binding.scanningProgress.visibility = View.VISIBLE
        binding.scanningText.visibility = View.VISIBLE
        binding.noServersText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val servers = networkManager.scanForServers()
                binding.scanningProgress.visibility = View.GONE
                binding.scanningText.visibility = View.GONE

                if (servers.isEmpty()) {
                    binding.noServersText.visibility = View.VISIBLE
                } else {
                    serverAdapter.submitList(servers)
                }
            } catch (e: Exception) {
                binding.scanningProgress.visibility = View.GONE
                binding.scanningText.visibility = View.GONE
                binding.noServersText.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onServerSelected(server: GameServer) {
        // Navigate to name entry screen
        val action = ServerDiscoveryFragmentDirections.actionServerDiscoveryToNameEntry(server.ipAddress)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
