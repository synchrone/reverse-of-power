package com.game.remoteclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentNameEntryBinding

class NameEntryFragment : Fragment() {

    private var _binding: FragmentNameEntryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNameEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        binding.continueButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (name.length < 2) {
                Toast.makeText(requireContext(), "Name must be at least 2 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Navigate to avatar selection
            val action = NameEntryFragmentDirections.actionNameEntryToAvatarSelection(
                playerName = name
            )
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
