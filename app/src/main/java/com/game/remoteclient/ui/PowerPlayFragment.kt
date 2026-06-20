package com.game.remoteclient.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ClientHoldingScreenCommandMessage
import com.game.protocol.PowerPlay
import com.game.protocol.PowerType
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

    // The phase currently being shown, so step-back can rebuild the picker without reconstructing it.
    private var currentPhase: ServerBeginPowerPlayPhase? = null
    // True while a target screen (choose-one or everyone) is showing and the player can still step back.
    private var targetScreenActive = false
    // True once a choice has been sent to the server; step-back is no longer allowed.
    private var choiceCommitted = false
    // The pending 300ms picker->target transition; stored so it can be cancelled on step-back.
    private var pendingTransition: Runnable? = null

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

        // Tap the top section (title) to step back from a target screen to the picker,
        // but only while a target screen is active and no choice has been committed.
        binding.stepBackZone.setOnClickListener {
            if (targetScreenActive && !choiceCommitted) {
                stepBackToPicker()
            }
        }

        // BUG #2: the peeking picker itself is a step-back affordance too. Card taps are handled
        // per-card (see showPowerPlayOptions), but tapping the gaps/background between the peeking
        // circles dispatches to the scrollview — handle that here so the whole top zone goes back.
        binding.powerPlayScrollView.setOnClickListener {
            if (targetScreenActive && !choiceCommitted) {
                stepBackToPicker()
            }
        }

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

    private fun showPowerPlayOptions(phase: ServerBeginPowerPlayPhase, animateNew: Boolean = true) {
        currentPhase = phase
        powerPlays = phase.PowerPlays
        players = phase.PowerPlayPlayers

        // Cancel any pending transition + reel steps + in-flight animations from a prior target screen.
        pendingTransition?.let { binding.powerPlayScrollView.removeCallbacks(it) }
        pendingTransition = null
        randomizeRunnables.forEach { handler.removeCallbacks(it) }
        randomizeRunnables.clear()
        binding.powerPlayScrollView.animate().cancel()
        binding.titleText.animate().cancel()
        binding.descriptionText.animate().cancel()
        binding.selectedPowerPlayLabel.animate().cancel()
        for (i in 0 until binding.targetContainer.childCount) {
            val c = binding.targetContainer.getChildAt(i)
            c.findViewById<View>(R.id.targetGlowRing)?.animate()?.cancel()
            c.findViewById<View>(R.id.targetSpotlight)?.animate()?.cancel()
        }

        // Enable selection immediately (request may arrive before or after)
        selectionEnabled = true
        targetScreenActive = false
        choiceCommitted = false
        selectedPowerPlay = null

        binding.titleText.text = getString(R.string.pick_a_power_play)
        binding.titleText.alpha = 1f
        binding.descriptionText.text = ""
        binding.descriptionText.alpha = 1f
        binding.descriptionText.visibility = View.VISIBLE   // peekPicker() hides this during target phase
        binding.selectedPowerPlayLabel.visibility = View.GONE
        binding.selectedPowerPlayLabel.alpha = 1f
        binding.targetArea.visibility = View.GONE
        binding.targetContainer.removeAllViews()
        binding.powerPlayScrollView.visibility = View.VISIBLE
        binding.powerPlayScrollView.scaleX = 1f
        binding.powerPlayScrollView.scaleY = 1f
        binding.powerPlayScrollView.pivotY = 0f             // peekPicker() pivots from the top; reset to a sane default
        binding.powerPlayScrollView.alpha = 1f
        (binding.powerPlayScrollView.layoutParams as android.widget.LinearLayout.LayoutParams).let {
            it.height = 0
            it.weight = 1f
            binding.powerPlayScrollView.layoutParams = it
        }
        binding.stepBackZone.visibility = View.GONE

        binding.powerPlayContainer.removeAllViews()

        for (powerPlay in powerPlays) {
            if (powerPlay.effectivePowerType == PowerType.NONE) continue
            val (name, description) = getPowerPlayInfo(powerPlay.effectivePowerType)
            val itemView = layoutInflater.inflate(R.layout.item_power_play, binding.powerPlayContainer, false)

            val nameView = itemView.findViewById<TextView>(R.id.powerPlayName)
            val descView = itemView.findViewById<TextView>(R.id.powerPlayDescription)
            val icon = itemView.findViewById<ImageView>(R.id.powerPlayIcon)

            itemView.setOnClickListener {
                if (selectionEnabled) {
                    onPowerPlaySelected(powerPlay)
                } else if (targetScreenActive && !choiceCommitted) {
                    // BUG #2: during the target phase the peeking picker circles act as a
                    // step-back affordance (the title's stepBackZone may not cover them).
                    stepBackToPicker()
                }
            }

            binding.powerPlayContainer.addView(itemView)

            if (powerPlay.New && animateNew) {
                startRandomizeAnimation(nameView, descView, icon, powerPlay.effectivePowerType)
            } else {
                nameView.text = name
                descView.text = description
                icon.background.setTint(getPowerPlayColor(powerPlay.effectivePowerType))
                getPowerPlayDrawable(powerPlay.effectivePowerType)?.let { icon.setImageResource(it) }
            }
        }
    }

    private fun startRandomizeAnimation(
        nameView: TextView,
        descView: TextView,
        icon: ImageView,
        realPowerType: PowerType
    ) {
        val allTypes = listOf(
            PowerType.FREEZE, PowerType.BOMBLES, PowerType.NIBBLERS, PowerType.GLOOP,
            PowerType.DOUBLE_TROUBLE_FREEZE_BOMBLES, PowerType.LOCKDOWN, PowerType.ZIPPERS,
            PowerType.BUG, PowerType.LETTER_SCATTER, PowerType.DISCO_INFERNO
        )
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
        getPowerPlayDrawable(types[0])?.let { icon.setImageResource(it) } ?: icon.setImageDrawable(null)

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
                getPowerPlayDrawable(powerType)?.let { icon.setImageResource(it) } ?: icon.setImageDrawable(null)
            }
            handler.postDelayed(runnable, cumulativeDelay)
            randomizeRunnables.add(runnable)
            cumulativeDelay += if (i < delays.size) delays[i] else 400L
        }
    }

    private fun onPowerPlaySelected(powerPlay: PowerPlay) {
        selectedPowerPlay = powerPlay
        val (name, description) = getPowerPlayInfo(powerPlay.effectivePowerType)

        binding.selectedPowerPlayLabel.text = name
        binding.selectedPowerPlayLabel.visibility = View.VISIBLE
        binding.selectedPowerPlayLabel.alpha = 0f
        binding.selectedPowerPlayLabel.animate().alpha(1f).setDuration(300).start()

        binding.descriptionText.text = description

        // Targeting is server-driven: PowerPlayTargets lists the candidate slots and TargetCount how many are
        // hit. TargetCount > 1 = a fixed multi-target set (e.g. everyone) → auto-send; TargetCount == 1 = the
        // player picks one candidate (self-help powers list only the player's own slot).
        if (powerPlay.TargetCount > 1) {
            selectionEnabled = false

            binding.titleText.animate().alpha(0f).setDuration(200).withEndAction {
                binding.titleText.text = name
                binding.titleText.animate().alpha(1f).setDuration(200).start()
            }.start()

            val scaleX = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "scaleX", 1f, 0.8f)
            val scaleY = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "scaleY", 1f, 0.8f)
            val fadeOut = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "alpha", 1f, 0.4f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY, fadeOut)
                duration = 300
                start()
            }

            val t = Runnable {
                if (_binding == null) return@Runnable
                pendingTransition = null
                targetScreenActive = true
                peekPicker()
                showEveryoneTarget(powerPlay)
            }
            pendingTransition = t
            binding.powerPlayScrollView.postDelayed(t, 300)
            return
        }

        // Animate transition to phase 2
        binding.titleText.animate().alpha(0f).setDuration(200).withEndAction {
            binding.titleText.text = getString(R.string.choose_a_target)
            binding.titleText.animate().alpha(1f).setDuration(200).start()
        }.start()

        // Shrink power play options
        val scaleX = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "scaleX", 1f, 0.8f)
        val scaleY = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "scaleY", 1f, 0.8f)
        val fadeOut = ObjectAnimator.ofFloat(binding.powerPlayScrollView, "alpha", 1f, 0.4f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, fadeOut)
            duration = 300
            start()
        }

        // Show target area
        val t = Runnable {
            if (_binding == null) return@Runnable
            pendingTransition = null
            targetScreenActive = true
            peekPicker()
            showTargetPlayers(powerPlay)
        }
        pendingTransition = t
        binding.powerPlayScrollView.postDelayed(t, 300)
    }

    private fun showTargetPlayers(powerPlay: PowerPlay) {
        binding.targetArea.visibility = View.VISIBLE
        binding.targetArea.alpha = 0f
        binding.targetArea.animate().alpha(1f).setDuration(300).start()

        binding.targetContainer.removeAllViews()

        // Candidates = whatever slots the server marked targetable (includes self for self-help powers)
        val targets = players.filter { powerPlay.PowerPlayTargets.contains(it.SlotIndex) }

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

    private fun showEveryoneTarget(powerPlay: PowerPlay) {
        binding.targetArea.visibility = View.VISIBLE
        binding.targetArea.alpha = 0f
        binding.targetArea.animate().alpha(1f).setDuration(300).start()

        binding.targetContainer.removeAllViews()

        // Fixed multi-target play (TargetCount > 1): show a single selectable "Everyone" card.
        // No auto-send — the player must tap it to confirm, or tap the title to step back.
        val itemView = layoutInflater.inflate(R.layout.item_power_play_target, binding.targetContainer, false)
        itemView.findViewById<TextView>(R.id.targetName).text = getString(R.string.pp_everyone)

        val photo = itemView.findViewById<ImageView>(R.id.targetPhoto)
        photo.scaleType = ImageView.ScaleType.CENTER_INSIDE
        photo.background.setTint(getPowerPlayColor(powerPlay.effectivePowerType))
        getPowerPlayDrawable(powerPlay.effectivePowerType)?.let { photo.setImageResource(it) }

        itemView.setOnClickListener { onEveryoneSelected(powerPlay, itemView) }

        binding.targetContainer.addView(itemView)
    }

    /** Player tapped the "Everyone" card: echo the server target list verbatim and confirm. */
    private fun onEveryoneSelected(powerPlay: PowerPlay, card: View) {
        if (choiceCommitted) return
        choiceCommitted = true
        targetScreenActive = false
        selectionEnabled = false

        val targetSlots = powerPlay.PowerPlayTargets
        Log.d("PowerPlayFragment", "Everyone multi-target slot=${powerPlay.DisplayIndex} -> $targetSlots")
        networkManager.sendPowerPlayChoice(
            powerPlaySlotIndex = powerPlay.DisplayIndex,
            targetSlotIndices = targetSlots
        )

        val glowRing = card.findViewById<View>(R.id.targetGlowRing)
        glowRing.visibility = View.VISIBLE
        glowRing.alpha = 0f
        glowRing.animate().alpha(1f).setDuration(300).withEndAction { pulseGlow(glowRing) }.start()

        showChoiceConfirmed()
    }

    /** Return from a target screen to the picker, restoring all picker state (no re-spin of New! reels). */
    private fun stepBackToPicker() {
        binding.targetArea.visibility = View.GONE
        currentPhase?.let { showPowerPlayOptions(it, animateNew = false) }
    }

    /**
     * Shrink the picker to a row of full-size circles whose BOTTOM rounded arcs peek out from under
     * the title (kept visible as the step-back affordance). BUG #1: windowing a tall match_parent
     * card to a short strip with the icon centred slices the 120dp icon with a hard horizontal line.
     * Instead we keep the circles at full scale and bottom-align each peeking card's icon inside a
     * ~72dp window, so only the rounded bottom arc shows — reading as circles tucked under the title,
     * never a flat bar. Per-card gravity/visibility changes are undone naturally by showPowerPlayOptions,
     * which re-inflates every card fresh on rebuild/step-back.
     */
    private fun peekPicker() {
        val sv = binding.powerPlayScrollView
        (sv.layoutParams as android.widget.LinearLayout.LayoutParams).let {
            it.height = (72 * resources.displayMetrics.density).toInt()
            it.weight = 0f
            sv.layoutParams = it
        }
        // Keep circles full-size (no scale slice). Bottom-align each card's icon and hide its name/desc
        // so only the icon's rounded bottom arc shows inside the short window.
        sv.scaleX = 1f
        sv.scaleY = 1f
        sv.alpha = 0.4f
        for (i in 0 until binding.powerPlayContainer.childCount) {
            val card = binding.powerPlayContainer.getChildAt(i)
            if (card is android.widget.LinearLayout) {
                card.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            card.findViewById<View>(R.id.powerPlayName)?.visibility = View.GONE
            card.findViewById<View>(R.id.powerPlayDescription)?.visibility = View.GONE
        }
        binding.stepBackZone.visibility = View.VISIBLE

        // BUG #3: the ~208dp target card won't fit the cramped landscape targetArea while the power
        // name label and description still take vertical space. Per the approved trade-off, hide
        // both during target selection — the title already shows context. (showChoiceConfirmed
        // re-shows descriptionText just for the "Locked in!" confirmation; showPowerPlayOptions
        // restores both on reset.)
        binding.selectedPowerPlayLabel.animate().cancel()
        binding.selectedPowerPlayLabel.visibility = View.GONE
        binding.descriptionText.animate().cancel()
        binding.descriptionText.visibility = View.GONE
    }

    /** Swap the description for a clear "locked in" confirmation once a choice is committed. */
    private fun showChoiceConfirmed() {
        // descriptionText was hidden by peekPicker() to free vertical space for the target card;
        // re-show it (just below the target area) so the confirmation stays visible after committing.
        val d = binding.descriptionText
        d.animate().cancel()
        d.text = getString(R.string.pp_locked_in)
        d.alpha = 0f
        d.visibility = View.VISIBLE
        d.animate().alpha(1f).setDuration(200).start()
    }


    private fun onTargetSelected(player: PowerPlayPlayer) {
        if (choiceCommitted || !selectionEnabled) return
        val powerPlay = selectedPowerPlay ?: return
        selectionEnabled = false
        choiceCommitted = true
        targetScreenActive = false

        Log.d("PowerPlayFragment", "Selected power play ${powerPlay.DisplayIndex} targeting ${player.Name} (slot ${player.SlotIndex})")

        networkManager.sendPowerPlayChoice(
            powerPlaySlotIndex = powerPlay.DisplayIndex,
            targetSlotIndices = listOf(player.SlotIndex)
        )

        // Highlight selected target with glow, dim others
        for (i in 0 until binding.targetContainer.childCount) {
            val child = binding.targetContainer.getChildAt(i)
            val nameView = child.findViewById<TextView>(R.id.targetName)
            if (nameView.text == player.Name) {
                // Show glow ring
                val glowRing = child.findViewById<View>(R.id.targetGlowRing)
                glowRing.visibility = View.VISIBLE
                glowRing.alpha = 0f
                glowRing.animate().alpha(1f).setDuration(300).start()

                // Show spotlight
                val spotlight = child.findViewById<View>(R.id.targetSpotlight)
                spotlight.visibility = View.VISIBLE
                spotlight.alpha = 0f
                spotlight.scaleX = 0.5f
                spotlight.scaleY = 0.5f
                spotlight.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).start()

                // Pulse the glow ring
                val photo = child.findViewById<ImageView>(R.id.targetPhoto)
                photo.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).start()
                glowRing.animate().alpha(1f).setDuration(300).withEndAction {
                    pulseGlow(glowRing)
                }.start()
            } else {
                child.animate().alpha(0.3f).setDuration(300).start()
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

    private fun pulseGlow(view: View) {
        if (_binding == null) return
        view.animate()
            .scaleX(1.1f).scaleY(1.1f).alpha(0.7f)
            .setDuration(800)
            .withEndAction {
                if (_binding == null) return@withEndAction
                view.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(800)
                    .withEndAction { pulseGlow(view) }
                    .start()
            }
            .start()
    }

    private fun getPowerPlayInfo(powerType: PowerType): Pair<String, String> {
        return when (powerType) {
            PowerType.FREEZE -> getString(R.string.pp_freeze_name) to getString(R.string.pp_freeze_desc)
            PowerType.BOMBLES -> getString(R.string.pp_bombs_name) to getString(R.string.pp_bombs_desc)
            PowerType.NIBBLERS -> getString(R.string.pp_munchers_name) to getString(R.string.pp_munchers_desc)
            PowerType.GLOOP -> getString(R.string.pp_gloop_name) to getString(R.string.pp_gloop_desc)
            PowerType.DOUBLE_TROUBLE_FREEZE_GLOOP -> getString(R.string.pp_double_trouble_name) to getString(R.string.pp_dt_freeze_gloop_desc)
            PowerType.DOUBLE_TROUBLE_FREEZE_BOMBLES -> getString(R.string.pp_double_trouble_name) to getString(R.string.pp_dt_freeze_bombs_desc)
            PowerType.DOUBLE_TROUBLE_NIBBLERS_GLOOP -> getString(R.string.pp_double_trouble_name) to getString(R.string.pp_dt_munchers_gloop_desc)
            PowerType.LOCKDOWN -> getString(R.string.pp_lockdown_name) to getString(R.string.pp_lockdown_desc)
            PowerType.ZIPPERS -> getString(R.string.pp_zippers_name) to getString(R.string.pp_zippers_desc)
            PowerType.BUG -> getString(R.string.pp_bug_name) to getString(R.string.pp_bug_desc)
            PowerType.LETTER_SCATTER -> getString(R.string.pp_letter_scatter_name) to getString(R.string.pp_letter_scatter_desc)
            PowerType.DISCO_INFERNO -> getString(R.string.pp_disco_name) to getString(R.string.pp_disco_desc)
            PowerType.FIFTY_FIFTY -> getString(R.string.pp_fifty_fifty_name) to getString(R.string.pp_fifty_fifty_desc)
            PowerType.POINTS_PARTY -> getString(R.string.pp_points_party_name) to getString(R.string.pp_points_party_desc)
            PowerType.POINTS_DOUBLER -> getString(R.string.pp_points_doubler_name) to getString(R.string.pp_points_doubler_desc)
            else -> getString(R.string.pp_unknown_name, powerType.value) to getString(R.string.pp_unknown_desc)
        }
    }

    private fun getPowerPlayDrawable(powerType: PowerType): Int? {
        return when (powerType) {
            PowerType.FREEZE -> R.drawable.ic_powerplay_freeze
            PowerType.BOMBLES -> R.drawable.ic_powerplay_bombles
            PowerType.NIBBLERS -> R.drawable.ic_powerplay_nibblers
            PowerType.GLOOP -> R.drawable.ic_powerplay_gloop
            PowerType.DOUBLE_TROUBLE_FREEZE_GLOOP -> R.drawable.ic_powerplay_dt_freeze_gloop
            PowerType.DOUBLE_TROUBLE_FREEZE_BOMBLES -> R.drawable.ic_powerplay_dt_freeze_bombles
            PowerType.DOUBLE_TROUBLE_NIBBLERS_GLOOP -> R.drawable.ic_powerplay_dt_nibblers_gloop
            PowerType.LOCKDOWN -> R.drawable.ic_powerplay_lockdown
            PowerType.ZIPPERS -> R.drawable.ic_powerplay_zippers
            PowerType.BUG -> R.drawable.ic_powerplay_bug
            PowerType.LETTER_SCATTER -> R.drawable.ic_powerplay_letter_scatter
            PowerType.DISCO_INFERNO -> R.drawable.ic_powerplay_disco
            PowerType.FIFTY_FIFTY -> R.drawable.ic_powerplay_fifty_fifty
            PowerType.POINTS_DOUBLER -> R.drawable.ic_powerplay_points_doubler
            PowerType.POINTS_PARTY -> R.drawable.ic_powerplay_points_party
            else -> null
        }
    }

    private fun getPowerPlayColor(powerType: PowerType): Int {
        return when (powerType) {
            PowerType.FREEZE -> Color.parseColor("#4FC3F7")  // ice blue
            PowerType.BOMBLES -> Color.parseColor("#FFD600")  // black/yellow
            PowerType.NIBBLERS -> Color.parseColor("#FF7043")  // orange-red
            PowerType.GLOOP -> Color.parseColor("#66BB6A")  // green
            PowerType.DOUBLE_TROUBLE_FREEZE_GLOOP -> Color.parseColor("#59B89A") // blue-green
            PowerType.DOUBLE_TROUBLE_FREEZE_BOMBLES -> Color.parseColor("#A8C44B") // blue-yellow
            PowerType.DOUBLE_TROUBLE_NIBBLERS_GLOOP -> Color.parseColor("#D48A30") // orange-green
            PowerType.LOCKDOWN -> Color.parseColor("#78909C")  // steel grey
            PowerType.ZIPPERS -> Color.parseColor("#CE93D8")  // light purple
            PowerType.BUG -> Color.parseColor("#8D6E63")  // brown
            PowerType.LETTER_SCATTER -> Color.parseColor("#FFA726")  // amber
            PowerType.DISCO_INFERNO -> Color.parseColor("#EC407A")  // hot pink
            PowerType.FIFTY_FIFTY -> Color.parseColor("#26C6DA")  // cyan
            PowerType.POINTS_DOUBLER -> Color.parseColor("#FFD700")  // gold (x2 your own points)
            PowerType.POINTS_PARTY -> Color.parseColor("#E91E63")  // party pink (points for everyone)
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
