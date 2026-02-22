package com.game.remoteclient.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.PowerPlay
import com.game.protocol.PowerPlayPlayer
import com.game.protocol.ServerBeginPowerPlayPhase
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentPowerPlayBinding

class PowerPlayFragment : Fragment() {

    private var _binding: FragmentPowerPlayBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private var powerPlays: List<PowerPlay> = emptyList()
    private var players: List<PowerPlayPlayer> = emptyList()
    private var selectedPowerPlay: PowerPlay? = null
    private var selectionEnabled = false

    // Default colors (orange for power play phase)
    private var backgroundColor = Color.parseColor("#E8A040")
    private var backgroundSecondary = Color.parseColor("#D08830")

    private var holdingScreenCb: ((ClientHoldingScreenCommandMessage) -> Unit)? = null
    private var powerPlayRequestCb: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPowerPlayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sunburstBackground.setColors(backgroundColor, backgroundSecondary)
        observeMessages()

        // Apply pending power play data
        networkManager.pendingPowerPlay?.let { phase ->
            showPowerPlayOptions(phase)
            networkManager.pendingPowerPlay = null
        }
    }

    private fun observeMessages() {
        holdingScreenCb = { _ ->
            activity?.runOnUiThread { navigateToHoldingScreen() }
        }
        powerPlayRequestCb = {
            activity?.runOnUiThread { onPowerPlayRequested() }
        }

        networkManager.onHoldingScreenMessage = holdingScreenCb
        networkManager.onPowerPlayRequest = powerPlayRequestCb
    }

    private fun showPowerPlayOptions(phase: ServerBeginPowerPlayPhase) {
        powerPlays = phase.PowerPlays
        players = phase.PowerPlayPlayers

        // Enable selection immediately (request may arrive before or after)
        selectionEnabled = true

        binding.titleText.text = "PICK A POWER PLAY"
        binding.descriptionText.text = ""
        binding.selectedPowerPlayLabel.visibility = View.GONE
        binding.targetArea.visibility = View.GONE
        binding.powerPlayScrollView.visibility = View.VISIBLE

        binding.powerPlayContainer.removeAllViews()

        for (powerPlay in powerPlays) {
            val (name, description) = getPowerPlayInfo(powerPlay.PowerType)
            val itemView = layoutInflater.inflate(R.layout.item_power_play, binding.powerPlayContainer, false)

            itemView.findViewById<TextView>(R.id.powerPlayName).text = name
            itemView.findViewById<TextView>(R.id.powerPlayDescription).text = description

            val icon = itemView.findViewById<View>(R.id.powerPlayIcon)
            icon.background.setTint(getPowerPlayColor(powerPlay.PowerType))

            itemView.setOnClickListener {
                if (selectionEnabled) {
                    onPowerPlaySelected(powerPlay)
                }
            }

            binding.powerPlayContainer.addView(itemView)
        }
    }

    private fun onPowerPlaySelected(powerPlay: PowerPlay) {
        selectedPowerPlay = powerPlay
        val (name, description) = getPowerPlayInfo(powerPlay.PowerType)

        // Animate transition to phase 2
        binding.titleText.animate().alpha(0f).setDuration(200).withEndAction {
            binding.titleText.text = "CHOOSE A TARGET"
            binding.titleText.animate().alpha(1f).setDuration(200).start()
        }.start()

        binding.selectedPowerPlayLabel.text = name
        binding.selectedPowerPlayLabel.visibility = View.VISIBLE
        binding.selectedPowerPlayLabel.alpha = 0f
        binding.selectedPowerPlayLabel.animate().alpha(1f).setDuration(300).start()

        binding.descriptionText.text = description

        // Shrink power play options
        val scaleX = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "scaleX", 1f, 0.5f)
        val scaleY = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "scaleY", 1f, 0.5f)
        val fadeOut = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "alpha", 1f, 0f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, fadeOut)
            duration = 300
            start()
        }

        // Show target area
        binding.powerPlayScrollView.postDelayed({
            if (_binding == null) return@postDelayed
            binding.powerPlayScrollView.visibility = View.GONE
            showTargetPlayers(powerPlay)
        }, 300)
    }

    private fun showTargetPlayers(powerPlay: PowerPlay) {
        binding.targetArea.visibility = View.VISIBLE
        binding.targetArea.alpha = 0f
        binding.targetArea.animate().alpha(1f).setDuration(300).start()

        binding.targetContainer.removeAllViews()

        // Filter to non-self players that are valid targets
        val targets = players.filter { !it.Self && powerPlay.PowerPlayTargets.contains(it.SlotIndex) }

        for (player in targets) {
            val itemView = layoutInflater.inflate(R.layout.item_power_play_target, binding.targetContainer, false)

            itemView.findViewById<TextView>(R.id.targetName).text = player.Name

            val circle = itemView.findViewById<View>(R.id.targetCircle)
            circle.background.setTint(Color.parseColor("#80FFFFFF"))

            itemView.setOnClickListener {
                onTargetSelected(player)
            }

            binding.targetContainer.addView(itemView)
        }
    }

    private fun onTargetSelected(player: PowerPlayPlayer) {
        val powerPlay = selectedPowerPlay ?: return
        selectionEnabled = false

        Log.d("PowerPlayFragment", "Selected power play ${powerPlay.DisplayIndex} targeting ${player.Name} (slot ${player.SlotIndex})")

        networkManager.sendPowerPlayChoice(
            powerPlaySlotIndex = powerPlay.DisplayIndex,
            targetSlotIndices = listOf(player.SlotIndex)
        )

        // Highlight selected target, dim others
        for (i in 0 until binding.targetContainer.childCount) {
            val child = binding.targetContainer.getChildAt(i)
            val nameView = child.findViewById<TextView>(R.id.targetName)
            if (nameView.text == player.Name) {
                val circle = child.findViewById<View>(R.id.targetCircle)
                circle.background.setTint(getPowerPlayColor(powerPlay.PowerType))
                circle.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
            } else {
                child.alpha = 0.3f
            }
        }
    }

    private fun onPowerPlayRequested() {
        if (selectedPowerPlay == null) {
            // No power play chosen — send empty response
            Log.d("PowerPlayFragment", "No power play selected, sending empty choice")
            networkManager.sendPowerPlayChoice(
                powerPlaySlotIndex = -1,
                targetSlotIndices = emptyList()
            )
        }
    }

    private fun navigateToHoldingScreen() {
        findNavController().popBackStack(R.id.holdingScreenFragment, false)
    }

    private fun getPowerPlayInfo(powerType: Int): Pair<String, String> {
        return when (powerType) {
            4 -> "FREEZE" to "Encase answers in ice"
            5 -> "BOMBLES" to "Throw bombs over answers"
            6 -> "NIBBLERS" to "Nibble away at answers"
            7 -> "GLOOP" to "Cover answers in gloop"
            11 -> "NIBBLERS" to "Nibble away at answers"
            else -> "POWER PLAY #$powerType" to ""
        }
    }

    private fun getPowerPlayColor(powerType: Int): Int {
        return when (powerType) {
            4 -> Color.parseColor("#4FC3F7") // Freeze ice blue
            5 -> Color.parseColor("#FF7043") // Bombles orange-red
            6 -> Color.parseColor("#FF7043") // Nibblers orange-red
            7 -> Color.parseColor("#66BB6A") // Gloop green
            11 -> Color.parseColor("#FF7043") // Nibblers orange-red
            else -> Color.parseColor("#AB47BC") // Purple fallback
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onPowerPlayRequest === powerPlayRequestCb) networkManager.onPowerPlayRequest = null
        _binding = null
    }
}
