package com.game.remoteclient.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.game.protocol.CategoryChoice
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.ColorTint
import com.game.protocol.ServerCategorySelectChoices
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.databinding.FragmentCategorySelectionBinding

class CategorySelectionFragment : Fragment() {

    private var _binding: FragmentCategorySelectionBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var categoryChoices: List<CategoryChoice> = emptyList()
    private var selectionEnabled = false

    // Default colors
    private var backgroundColor = Color.parseColor("#C4B8A8")
    private var backgroundSecondary = Color.parseColor("#D4C8B8")

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
    }

    private fun setupDoorClickListeners() {
        val doors = listOf(binding.door0, binding.door1, binding.door2, binding.door3)
        doors.forEachIndexed { index, doorView ->
            doorView.setOnClickListener {
                if (selectionEnabled && index < categoryChoices.size) {
                    val choice = categoryChoices[index]
                    // Send the ChosenCategoryIndex from the CategoryChoice
                    networkManager.sendCategorySelection(choice.DoorIndex)
                    selectionEnabled = false
                    highlightSelectedDoor(index)
                }
            }
        }
    }

    private fun observeMessages() {
        networkManager.onCategoryChoicesMessage = { message ->
            activity?.runOnUiThread {
                updateCategoryChoices(message)
            }
        }

        networkManager.onCategorySelectRequest = {
            activity?.runOnUiThread {
                enableSelection()
            }
        }

        networkManager.onHoldingScreenMessage = { message ->
            activity?.runOnUiThread {
                handleHoldingScreenMessage(message)
            }
        }
    }

    private fun handleHoldingScreenMessage(message: ClientHoldingScreenCommandMessage) {
        binding.titleText.text = message.HoldingScreenText.replace("\\n", "\n")
    }

    private fun updateCategoryChoices(message: ServerCategorySelectChoices) {
        categoryChoices = message.CategoryChoices
        backgroundColor = colorTintToInt(message.BackgroundTint)
        backgroundSecondary = colorTintToInt(message.SecondaryTint)

        applyBackgroundColors()

        val doors = listOf(binding.door0, binding.door1, binding.door2, binding.door3)
        val labels = listOf(binding.label0, binding.label1, binding.label2, binding.label3)

        doors.forEachIndexed { index, doorView ->
            if (index < categoryChoices.size) {
                val choice = categoryChoices[index]
                doorView.visibility = View.VISIBLE
                doorView.doorIndex = index
                doorView.setDoorColor(colorTintToInt(choice.Colour))
                doorView.alpha = 0.7f // Dim until selection is enabled

                labels[index].visibility = View.VISIBLE
                labels[index].text = choice.DisplayText
            } else {
                doorView.visibility = View.INVISIBLE
                labels[index].visibility = View.INVISIBLE
            }
        }
    }

    private fun enableSelection() {
        selectionEnabled = true
        val doors = listOf(binding.door0, binding.door1, binding.door2, binding.door3)
        doors.forEach { it.alpha = 1.0f }
    }

    private fun highlightSelectedDoor(selectedIndex: Int) {
        val doors = listOf(binding.door0, binding.door1, binding.door2, binding.door3)
        val labels = listOf(binding.label0, binding.label1, binding.label2, binding.label3)

        doors.forEachIndexed { index, doorView ->
            doorView.alpha = if (index == selectedIndex) 1.0f else 0.3f
        }
        labels.forEachIndexed { index, label ->
            label.alpha = if (index == selectedIndex) 1.0f else 0.3f
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
        networkManager.onCategoryChoicesMessage = null
        networkManager.onCategorySelectRequest = null
        networkManager.onHoldingScreenMessage = null
        _binding = null
    }
}
