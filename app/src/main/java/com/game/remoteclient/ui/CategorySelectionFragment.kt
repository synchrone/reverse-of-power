package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.game.protocol.CategoryChoice
import com.game.protocol.ColorTint
import com.game.protocol.ServerBeginCategorySelectOverride
import com.game.protocol.ServerCategorySelectChoices
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentCategorySelectionBinding
import androidx.navigation.fragment.findNavController

class CategorySelectionFragment : Fragment() {

    private var _binding: FragmentCategorySelectionBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var categoryChoices: List<CategoryChoice> = emptyList()
    private var selectionEnabled = false
    private var selectedDoorIndex: Int? = null

    // Default colors
    private var backgroundColor = Color.parseColor("#C4B8A8")
    private var backgroundSecondary = Color.parseColor("#D4C8B8")

    private var categoryCb: ((ServerCategorySelectChoices) -> Unit)? = null
    private var categorySelectCb: (() -> Unit)? = null
    private var holdingScreenCb: ((com.game.protocol.ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var triviaCb: ((com.game.protocol.ServerBeginTriviaAnsweringPhase) -> Unit)? = null
    private var categoryOverrideCb: ((ServerBeginCategorySelectOverride) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategorySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDoorClickListeners()
        observeMessages()
        applyBackgroundColors()

        // Initialize door indices for different styles
        binding.door0.doorIndex = 0
        binding.door1.doorIndex = 1
        binding.door2.doorIndex = 2
        binding.door3.doorIndex = 3

        // Apply any pending category choices that arrived before navigation
        networkManager.pendingCategoryChoices?.let { choices ->
            updateCategoryChoices(choices)
            networkManager.pendingCategoryChoices = null
        }

        // Handle category select request that arrived before this fragment was ready
        if (networkManager.pendingCategorySelectRequest) {
            networkManager.pendingCategorySelectRequest = false
            networkManager.sendCategorySelection(selectedDoorIndex ?: -1)
        }
    }

    private fun setupDoorClickListeners() {
        val columns = listOf(binding.doorColumn0, binding.doorColumn1, binding.doorColumn2, binding.doorColumn3)
        columns.forEachIndexed { index, column ->
            column.setOnClickListener {
                Log.d("CategorySelection", "Door $index tapped, selectionEnabled=$selectionEnabled, choices=${categoryChoices.size}")
                if (selectionEnabled && index < categoryChoices.size) {
                    selectedDoorIndex = index
                    networkManager.sendCategorySelection(index)
                    selectionEnabled = false
                    highlightSelectedDoor(index)
                }
            }
        }
    }

    private fun observeMessages() {
        categoryCb = { message ->
            activity?.runOnUiThread { updateCategoryChoices(message) }
        }
        categorySelectCb = {
            activity?.runOnUiThread {
                enableSelection()
                // Re-send if already picked, otherwise send -1 (no choice)
                networkManager.sendCategorySelection(selectedDoorIndex ?: -1)
            }
        }
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }
        triviaCb = { _ ->
            activity?.runOnUiThread { navigateToTriviaAnswering() }
        }
        categoryOverrideCb = { _ ->
            activity?.runOnUiThread { navigateToPowerPick() }
        }

        networkManager.onCategoryChoicesMessage = categoryCb
        networkManager.onCategorySelectRequest = categorySelectCb
        networkManager.onHoldingScreenMessage = holdingScreenCb
        networkManager.onTriviaMessage = triviaCb
        networkManager.onCategoryOverrideMessage = categoryOverrideCb
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    private fun navigateToTriviaAnswering() {
        findNavController().navigate(R.id.action_categorySelection_to_triviaAnswering)
    }

    private fun navigateToPowerPick() {
        findNavController().navigate(R.id.action_categorySelection_to_powerPick)
    }

    private fun updateCategoryChoices(message: ServerCategorySelectChoices) {
        categoryChoices = message.CategoryChoices
        backgroundColor = colorTintToInt(message.BackgroundTint)
        backgroundSecondary = colorTintToInt(message.SecondaryTint)

        applyBackgroundColors()

        val columns = listOf(binding.doorColumn0, binding.doorColumn1, binding.doorColumn2, binding.doorColumn3)
        val doors = listOf(binding.door0, binding.door1, binding.door2, binding.door3)
        val labels = listOf(binding.label0, binding.label1, binding.label2, binding.label3)

        columns.forEachIndexed { index, column ->
            if (index < categoryChoices.size) {
                val choice = categoryChoices[index]
                column.visibility = View.VISIBLE
                doors[index].doorIndex = index
                doors[index].setDoorColor(colorTintToInt(choice.Colour))
                labels[index].text = choice.DisplayText
            } else {
                column.visibility = View.INVISIBLE
            }
        }

        // Preserve existing selection; otherwise enable selection
        if (selectedDoorIndex != null) {
            highlightSelectedDoor(selectedDoorIndex!!)
        } else {
            selectionEnabled = true
            columns.forEach { it.alpha = 1.0f }
        }
    }

    private fun enableSelection() {
        selectionEnabled = true
        val columns = listOf(binding.doorColumn0, binding.doorColumn1, binding.doorColumn2, binding.doorColumn3)
        columns.forEach { it.alpha = 1.0f }
    }

    private fun highlightSelectedDoor(selectedIndex: Int) {
        val columns = listOf(binding.doorColumn0, binding.doorColumn1, binding.doorColumn2, binding.doorColumn3)
        columns.forEachIndexed { index, column ->
            column.alpha = if (index == selectedIndex) 1.0f else 0.3f
        }
    }

    private fun colorTintToInt(tint: ColorTint): Int {
        return Color.argb(
            (tint.a * 255).toInt().coerceIn(0, 255),
            (tint.r * 255).toInt().coerceIn(0, 255),
            (tint.g * 255).toInt().coerceIn(0, 255),
            (tint.b * 255).toInt().coerceIn(0, 255)
        )
    }

    private fun applyBackgroundColors() {
        binding.sunburstBackground.setColors(backgroundColor, backgroundSecondary)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (networkManager.onCategoryChoicesMessage === categoryCb) networkManager.onCategoryChoicesMessage = null
        if (networkManager.onCategorySelectRequest === categorySelectCb) networkManager.onCategorySelectRequest = null
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onTriviaMessage === triviaCb) networkManager.onTriviaMessage = null
        if (networkManager.onCategoryOverrideMessage === categoryOverrideCb) networkManager.onCategoryOverrideMessage = null
        _binding = null
    }
}
