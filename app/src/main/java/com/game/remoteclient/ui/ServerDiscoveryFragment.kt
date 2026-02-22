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
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.models.GameServer
import kotlinx.coroutines.launch

class ServerDiscoveryFragment : Fragment() {

    private var _binding: FragmentServerDiscoveryBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }
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
        // Disable interaction during connection attempt
        binding.connectButton.isEnabled = false
        binding.scanningProgress.visibility = View.VISIBLE
        binding.scanningText.text = "Connecting to server..."
        binding.scanningText.visibility = View.VISIBLE

        // Listen for rejoin before connecting — server may send it during handshake
        networkManager.onRejoining = { _ ->
            activity?.runOnUiThread {
                findNavController().navigate(R.id.action_serverDiscovery_to_holdingScreen)
            }
        }

        lifecycleScope.launch {
            try {
                val success = networkManager.connectToServer(server)

                binding.scanningProgress.visibility = View.GONE
                binding.scanningText.visibility = View.GONE
                binding.connectButton.isEnabled = true

                if (success && !networkManager.isRejoining) {
                    // Normal flow — navigate to name entry screen
                    val action = ServerDiscoveryFragmentDirections.actionServerDiscoveryToNameEntry()
                    findNavController().navigate(action)
                } else if (!success) {
                    networkManager.onRejoining = null
                    Toast.makeText(
                        requireContext(),
                        "Failed to connect to ${server.fullAddress}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // If isRejoining, the onRejoining callback already navigated
            } catch (e: Exception) {
                binding.scanningProgress.visibility = View.GONE
                binding.scanningText.visibility = View.GONE
                binding.connectButton.isEnabled = true
                networkManager.onRejoining = null
                Toast.makeText(
                    requireContext(),
                    "Connection error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        networkManager.onRejoining = null
        _binding = null
    }
}
