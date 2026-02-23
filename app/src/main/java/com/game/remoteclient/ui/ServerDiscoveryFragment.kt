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

    private var manualEntryExpanded = false

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

        binding.rescanButton.setOnClickListener {
            startServerScan()
        }

        binding.manualEntryHeader.setOnClickListener {
            manualEntryExpanded = !manualEntryExpanded
            binding.ipEntryCard.visibility = if (manualEntryExpanded) View.VISIBLE else View.GONE
            binding.expandIcon.rotation = if (manualEntryExpanded) 180f else 0f
        }
    }

    private fun startServerScan() {
        binding.scanningContainer.visibility = View.VISIBLE
        binding.noServersText.visibility = View.GONE
        binding.rescanButton.isEnabled = false
        serverAdapter.submitList(emptyList())

        lifecycleScope.launch {
            try {
                val servers = networkManager.scanForServers()
                if (_binding == null) return@launch
                binding.scanningContainer.visibility = View.GONE
                binding.rescanButton.isEnabled = true

                if (servers.isEmpty()) {
                    binding.noServersText.visibility = View.VISIBLE
                } else {
                    binding.noServersText.visibility = View.GONE
                    serverAdapter.submitList(servers)
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.scanningContainer.visibility = View.GONE
                binding.rescanButton.isEnabled = true
                binding.noServersText.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onServerSelected(server: GameServer) {
        binding.connectButton.isEnabled = false
        binding.scanningContainer.visibility = View.VISIBLE
        binding.scanningText.text = "Connecting to server..."

        networkManager.onRejoining = { _ ->
            activity?.runOnUiThread {
                if (_binding != null) {
                    findNavController().navigate(R.id.action_serverDiscovery_to_holdingScreen)
                }
            }
        }

        lifecycleScope.launch {
            try {
                val success = networkManager.connectToServer(server)
                if (_binding == null) return@launch

                binding.scanningContainer.visibility = View.GONE
                binding.connectButton.isEnabled = true

                if (success && !networkManager.isRejoining) {
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
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.scanningContainer.visibility = View.GONE
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
