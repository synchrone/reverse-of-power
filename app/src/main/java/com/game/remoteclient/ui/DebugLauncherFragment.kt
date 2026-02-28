package com.game.remoteclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ActivePowerPlay
import com.game.protocol.ColorTint
import com.game.protocol.PowerPlay
import com.game.protocol.PowerPlayPlayer
import com.game.protocol.PowerType
import com.game.protocol.LinkingAnswer
import com.game.protocol.ServerBeginLinkingAnsweringPhase
import com.game.protocol.ServerBeginPowerPlayPhase
import com.game.protocol.ServerBeginSortingAnsweringPhase
import com.game.protocol.ServerBeginTriviaAnsweringPhase
import com.game.protocol.SortingAnswer
import com.game.protocol.TriviaAnswer
import com.game.remoteclient.GameRemoteClientApplication
import com.game.remoteclient.R
import com.game.remoteclient.databinding.FragmentDebugLauncherBinding

class DebugLauncherFragment : Fragment() {

    private var _binding: FragmentDebugLauncherBinding? = null
    private val binding get() = _binding!!

    private val networkManager by lazy { GameRemoteClientApplication.getInstance().networkManager }

    private val counts = mutableMapOf(
        PowerType.FREEZE to 0,
        PowerType.BOMBLES to 0,
        PowerType.NIBBLERS to 0,
        PowerType.GLOOP to 0
    )

    // Power play slot config: index into powerTypeOptions (-1 = none)
    private val powerTypeOptions = listOf(
        -1 to "(none)",
        PowerType.FREEZE to "Freeze",
        PowerType.BOMBLES to "Bombles",
        PowerType.NIBBLERS to "Nibblers",
        PowerType.GLOOP to "Gloop",
        PowerType.DOUBLE_TROUBLE_FREEZE_GLOOP to "DT: Freeze+Gloop",
        PowerType.DOUBLE_TROUBLE_FREEZE_BOMBLES to "DT: Freeze+Bombles",
        PowerType.DOUBLE_TROUBLE_NIBBLERS_GLOOP to "DT: Nibblers+Gloop"
    )
    private val ppSlotSelections = intArrayOf(0, 0, 0) // index into powerTypeOptions
    private var targetPlayerCount = 3

    private val fakePlayerNames = listOf("Alice", "Bob", "Charlie", "Diana", "Eve")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStepper(PowerType.FREEZE, binding.btnFreezeMinus, binding.btnFreezePlus, binding.txtFreezeCount)
        setupStepper(PowerType.BOMBLES, binding.btnBomblesMinus, binding.btnBomblesPlus, binding.txtBomblesCount)
        setupStepper(PowerType.NIBBLERS, binding.btnNibblersMinus, binding.btnNibblersPlus, binding.txtNibblersCount)
        setupStepper(PowerType.GLOOP, binding.btnGloopMinus, binding.btnGloopPlus, binding.txtGloopCount)

        setupPPSlotCycler(0, binding.btnPPSlot1Prev, binding.btnPPSlot1Next, binding.txtPPSlot1)
        setupPPSlotCycler(1, binding.btnPPSlot2Prev, binding.btnPPSlot2Next, binding.txtPPSlot2)
        setupPPSlotCycler(2, binding.btnPPSlot3Prev, binding.btnPPSlot3Next, binding.txtPPSlot3)

        binding.btnTargetsMinus.setOnClickListener {
            if (targetPlayerCount > 1) {
                targetPlayerCount--
                binding.txtTargetsCount.text = targetPlayerCount.toString()
            }
        }
        binding.btnTargetsPlus.setOnClickListener {
            if (targetPlayerCount < 5) {
                targetPlayerCount++
                binding.txtTargetsCount.text = targetPlayerCount.toString()
            }
        }

        binding.btnLaunchTrivia.setOnClickListener { launchTrivia() }
        binding.btnLaunchPowerPlay.setOnClickListener { launchPowerPlay() }
        binding.btnLinking.setOnClickListener { launchLinking() }
        binding.btnSorting.setOnClickListener { launchSorting() }
    }

    private fun setupStepper(powerType: Int, minus: View, plus: View, display: TextView) {
        minus.setOnClickListener {
            val current = counts[powerType] ?: 0
            if (current > 0) {
                counts[powerType] = current - 1
                display.text = (current - 1).toString()
            }
        }
        plus.setOnClickListener {
            val current = counts[powerType] ?: 0
            if (current < 3) {
                counts[powerType] = current + 1
                display.text = (current + 1).toString()
            }
        }
    }

    private fun setupPPSlotCycler(slotIndex: Int, prev: View, next: View, display: TextView) {
        prev.setOnClickListener {
            val current = ppSlotSelections[slotIndex]
            ppSlotSelections[slotIndex] = if (current <= 0) powerTypeOptions.size - 1 else current - 1
            display.text = powerTypeOptions[ppSlotSelections[slotIndex]].second
        }
        next.setOnClickListener {
            val current = ppSlotSelections[slotIndex]
            ppSlotSelections[slotIndex] = if (current >= powerTypeOptions.size - 1) 0 else current + 1
            display.text = powerTypeOptions[ppSlotSelections[slotIndex]].second
        }
    }

    private fun launchTrivia() {
        val powerPlays = counts.filter { it.value > 0 }.map { (type, count) ->
            ActivePowerPlay(PowerType = type, Count = count)
        }
        val roundType = if (binding.chkFinals.isChecked) 5 else 1

        val tint = ColorTint(r = 0.4f, g = 0.2f, b = 0.6f, a = 1f)
        val secondaryTint = ColorTint(r = 0.3f, g = 0.15f, b = 0.5f, a = 1f)

        networkManager.pendingTrivia = ServerBeginTriviaAnsweringPhase(
            QuestionID = "DEBUG_Q001",
            QuestionText = "What is the capital of France?",
            QuestionDuration = 30.0,
            Answers = listOf(
                TriviaAnswer(DisplayIndex = 0, DisplayText = "English bulldog", IsCorrect = false),
                TriviaAnswer(DisplayIndex = 1, DisplayText = "African elephant", IsCorrect = true),
                TriviaAnswer(DisplayIndex = 2, DisplayText = "Angus cow", IsCorrect = false),
                TriviaAnswer(DisplayIndex = 3, DisplayText = "Ostrich egg", IsCorrect = false)
            ),
            PowerPlays = powerPlays,
            PowerPlayPlayers = emptyList(),
            RoundType = roundType,
            BackgroundTint = tint,
            SecondaryTint = secondaryTint
        )
        findNavController().navigate(R.id.action_debugLauncher_to_triviaAnswering)
    }

    private fun launchPowerPlay() {
        val newChecks = listOf(binding.chkPPSlot1New.isChecked, binding.chkPPSlot2New.isChecked, binding.chkPPSlot3New.isChecked)
        val selectedTypes = ppSlotSelections.toList().mapIndexedNotNull { slotIdx, optionIdx ->
            val powerType = powerTypeOptions[optionIdx].first
            if (powerType < 0) null
            else Triple(slotIdx, powerType, newChecks[slotIdx])
        }

        if (selectedTypes.isEmpty()) return

        val targetSlots = (0 until targetPlayerCount).toList()
        val defaultColor = ColorTint(r = 0.5f, g = 0.5f, b = 0.5f, a = 1f)

        val powerPlays = selectedTypes.map { (slotIdx, powerType, isNew) ->
            PowerPlay(
                DisplayIndex = slotIdx,
                PowerType = powerType,
                PowerTarget = -1,
                PowerPlayTargets = targetSlots,
                New = isNew,
                TargetCount = 1
            )
        }

        val players = (0 until targetPlayerCount).map { i ->
            PowerPlayPlayer(
                SlotIndex = i,
                Name = fakePlayerNames[i],
                ImageGUID = "",
                Colour = defaultColor,
                Self = false,
                Away = false
            )
        }

        networkManager.pendingPowerPlay = ServerBeginPowerPlayPhase(
            PowerPlays = powerPlays,
            PowerPlayPlayers = players,
            RoundType = 1
        )
        findNavController().navigate(R.id.action_debugLauncher_to_powerPlay)
    }

    private fun launchLinking() {
        networkManager.pendingLinking = ServerBeginLinkingAnsweringPhase(
            QuestionID = "DEBUG_LNK001",
            QuestionText = "Match the countries to their capitals",
            QuestionDuration = 45.0,
            LinkingAnswers = listOf(
                LinkingAnswer(DisplayText = "France", AnswerID = "LNK001_AnswerA01", MatchIndex = 1, Direction = 1),
                LinkingAnswer(DisplayText = "Paris", AnswerID = "LNK001_AnswerB01", MatchIndex = 1, Direction = 2),
                LinkingAnswer(DisplayText = "Germany", AnswerID = "LNK001_AnswerA02", MatchIndex = 2, Direction = 1),
                LinkingAnswer(DisplayText = "Berlin", AnswerID = "LNK001_AnswerB02", MatchIndex = 2, Direction = 2),
                LinkingAnswer(DisplayText = "Spain", AnswerID = "LNK001_AnswerA03", MatchIndex = 3, Direction = 1),
                LinkingAnswer(DisplayText = "Madrid", AnswerID = "LNK001_AnswerB03", MatchIndex = 3, Direction = 2),
                LinkingAnswer(DisplayText = "Italy", AnswerID = "LNK001_AnswerA04", MatchIndex = 4, Direction = 1),
                LinkingAnswer(DisplayText = "Rome", AnswerID = "LNK001_AnswerB04", MatchIndex = 4, Direction = 2),
                LinkingAnswer(DisplayText = "Japan", AnswerID = "LNK001_AnswerA05", MatchIndex = 5, Direction = 1),
                LinkingAnswer(DisplayText = "Tokyo", AnswerID = "LNK001_AnswerB05", MatchIndex = 5, Direction = 2)
            )
        )
        findNavController().navigate(R.id.action_debugLauncher_to_linkingAnswers)
    }

    private fun launchSorting() {
        networkManager.pendingSorting = ServerBeginSortingAnsweringPhase(
            QuestionID = "DEBUG_SRT001",
            QuestionText = "Sort these into the correct category",
            LeftBucketLabel = "Mammals",
            LeftBucketID = "BUCKET_LEFT",
            RightBucketLabel = "Reptiles",
            RightBucketID = "BUCKET_RIGHT",
            QuestionDuration = 45.0,
            SortingAnswers = listOf(
                SortingAnswer(DisplayText = "Dog", AnswerID = "SRT001_01", AnswerDirection = 1),
                SortingAnswer(DisplayText = "Snake", AnswerID = "SRT001_02", AnswerDirection = 2),
                SortingAnswer(DisplayText = "Cat", AnswerID = "SRT001_03", AnswerDirection = 1),
                SortingAnswer(DisplayText = "Lizard", AnswerID = "SRT001_04", AnswerDirection = 2),
                SortingAnswer(DisplayText = "Elephant", AnswerID = "SRT001_05", AnswerDirection = 1),
                SortingAnswer(DisplayText = "Crocodile", AnswerID = "SRT001_06", AnswerDirection = 2),
                SortingAnswer(DisplayText = "Whale", AnswerID = "SRT001_07", AnswerDirection = 1),
                SortingAnswer(DisplayText = "Turtle", AnswerID = "SRT001_08", AnswerDirection = 2)
            )
        )
        findNavController().navigate(R.id.action_debugLauncher_to_sortingAnswers)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
