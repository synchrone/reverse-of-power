package com.game.remoteclient.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

    private val handler = Handler(Looper.getMainLooper())
    private val randomizeRunnables = mutableListOf<Runnable>()

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
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                navigateToHoldingScreen()
            }
        }
        powerPlayRequestCb = {
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                onPowerPlayRequested()
            }
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

            val nameView = itemView.findViewById<TextView>(R.id.powerPlayName)
            val descView = itemView.findViewById<TextView>(R.id.powerPlayDescription)
            val icon = itemView.findViewById<View>(R.id.powerPlayIcon)

            itemView.setOnClickListener {
                if (selectionEnabled) {
                    onPowerPlaySelected(powerPlay)
                }
            }

            binding.powerPlayContainer.addView(itemView)

            if (powerPlay.New) {
                startRandomizeAnimation(nameView, descView, icon, powerPlay.PowerType)
            } else {
                nameView.text = name
                descView.text = description
                icon.background.setTint(getPowerPlayColor(powerPlay.PowerType))
            }
        }
    }

    private fun startRandomizeAnimation(
        nameView: TextView,
        descView: TextView,
        icon: View,
        realPowerType: Int
    ) {
        val allTypes = listOf(4, 5, 6, 7, 11)
        val otherTypes = allTypes.filter { it != realPowerType }.shuffled()
        // Slot reel sequence: fast spins then decelerate, land on real type
        val delays = listOf(80L, 80L, 80L, 80L, 80L, 80L, 120L, 160L, 220L, 300L, 400L)
        val types = delays.indices.map { otherTypes[it % otherTypes.size] } + realPowerType

        val nameHeight = nameView.lineHeight.takeIf { it > 0 } ?: 48

        // Initial state: first random name, already visible
        val (firstName, _) = getPowerPlayInfo(types[0])
        nameView.text = firstName
        descView.text = ""
        icon.background.setTint(getPowerPlayColor(types[0]))

        var cumulativeDelay = 300L // brief pause before reel starts
        for (i in 1 until types.size) {
            val powerType = types[i]
            val isLast = i == types.size - 1
            val spinDuration = if (i < delays.size) (delays[i] * 0.4).toLong() else 100L

            val runnable = Runnable {
                if (_binding == null) return@Runnable
                val (typeName, typeDesc) = getPowerPlayInfo(powerType)

                // Slide current text down out of view
                nameView.animate()
                    .translationY(nameHeight.toFloat())
                    .alpha(0f)
                    .setDuration(spinDuration)
                    .withEndAction {
                        if (_binding == null) return@withEndAction
                        // Snap to above, set new text, slide in from top
                        nameView.translationY = -nameHeight.toFloat()
                        nameView.text = typeName
                        nameView.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(spinDuration)
                            .withEndAction {
                                if (!isLast || _binding == null) return@withEndAction
                                // Reveal bounce on final landing
                                descView.text = typeDesc
                                descView.alpha = 0f
                                descView.animate().alpha(1f).setDuration(200).start()
                                nameView.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120).withEndAction {
                                    nameView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                                }.start()
                                icon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).withEndAction {
                                    icon.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                                }.start()
                            }
                            .start()
                    }
                    .start()

                icon.background.setTint(getPowerPlayColor(powerType))
            }
            handler.postDelayed(runnable, cumulativeDelay)
            randomizeRunnables.add(runnable)
            cumulativeDelay += if (i < delays.size) delays[i] else 400L
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

            val photo = itemView.findViewById<ImageView>(R.id.targetPhoto)
            val imageData = networkManager.receivedImages[player.ImageGUID]
            if (imageData != null) {
                val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                if (bitmap != null) {
                    photo.setImageBitmap(bitmap)
                } else {
                    Log.w("PowerPlayFragment", "Failed to decode image for ${player.Name} (${imageData.size} bytes, guid=${player.ImageGUID})")
                    photo.background.setTint(Color.parseColor("#80FFFFFF"))
                }
            } else {
                Log.d("PowerPlayFragment", "No image for ${player.Name} (guid=${player.ImageGUID}), have guids: ${networkManager.receivedImages.keys}")
                photo.background.setTint(Color.parseColor("#80FFFFFF"))
            }

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
                val photo = child.findViewById<ImageView>(R.id.targetPhoto)
                photo.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
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
            4 -> "FREEZE" to "Encase answers in ice" // it takes 5 taps to unfreeze
            5 -> "BOMBLES" to "Throw bombs over answers"
            6 -> "NIBBLERS" to "Nibble away at answers"
            7 -> "GLOOP" to "Cover answers in gloop"
            10 -> "DOUBLE TROUBLE" to "Freeze and gloop" // (gloop over freeze)
            11 -> "DOUBLE TROUBLE" to "Freeze and bombles"
            12 -> "DOUBLE TROUBLE" to "Nibblers and gloop"
            else -> "POWER PLAY #$powerType" to "please remember the effect and tell developers"
        }
    }

    private fun getPowerPlayColor(powerType: Int): Int {
        return when (powerType) {
            4 -> Color.parseColor("#4FC3F7")  // Freeze - ice blue
            5 -> Color.parseColor("#FFD600")  // Bombles - black/yellow
            6 -> Color.parseColor("#FF7043")  // Nibblers - orange-red
            7 -> Color.parseColor("#66BB6A")  // Gloop - green
            10 -> Color.parseColor("#59B89A") // Double trouble: freeze + gloop (blue-green)
            11 -> Color.parseColor("#A8C44B") // Double trouble: freeze + bombles (blue-yellow)
            12 -> Color.parseColor("#D48A30") // Double trouble: nibblers + gloop (orange-green)
            else -> Color.parseColor("#AB47BC") // Purple fallback
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        randomizeRunnables.forEach { handler.removeCallbacks(it) }
        randomizeRunnables.clear()
        if (networkManager.onHoldingScreenMessage === holdingScreenCb) networkManager.onHoldingScreenMessage = null
        if (networkManager.onPowerPlayRequest === powerPlayRequestCb) networkManager.onPowerPlayRequest = null
        _binding = null
    }
}
