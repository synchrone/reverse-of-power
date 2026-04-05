package com.game.remoteclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.game.protocol.ActivePowerPlay
import com.game.protocol.ClientEndOfGameFactCommandMessage
import com.game.protocol.ColorTint
import com.game.protocol.PowerPlay
import com.game.protocol.PowerPlayPlayer
import com.game.protocol.PowerType
import com.game.protocol.LinkingAnswer
import com.game.protocol.ServerAvatarStatusMessage
import com.game.protocol.ServerBeginLinkingAnsweringPhase
import com.game.protocol.ServerBeginPowerPlayPhase
import com.game.protocol.EliminatingAnswerData
import com.game.protocol.MissingLetterAnswerData
import com.game.protocol.MatchingData
import com.game.protocol.ServerBeginEliminatingAnsweringPhase
import com.game.protocol.ServerBeginMatchingAnsweringPhase
import com.game.protocol.ServerBeginMissingLetterAnsweringPhase
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
        PowerType.NONE to "(none)",
        PowerType.FREEZE to "Freeze",
        PowerType.BOMBLES to "Bombles",
        PowerType.NIBBLERS to "Nibblers",
        PowerType.GLOOP to "Gloop",
        PowerType.DOUBLE_TROUBLE_FREEZE_GLOOP to "DT: Freeze+Gloop",
        PowerType.DOUBLE_TROUBLE_FREEZE_BOMBLES to "DT: Freeze+Bombles",
        PowerType.DOUBLE_TROUBLE_NIBBLERS_GLOOP to "DT: Nibblers+Gloop",
        PowerType.LOCKDOWN to "Lockdown",
        PowerType.ZIPPERS to "Zippers",
        PowerType.BUG to "Bug",
        PowerType.LETTER_SCATTER to "Letter Scatter",
        PowerType.DISCO_INFERNO to "Disco Inferno",
        PowerType.FIFTY_FIFTY to "50/50",
        PowerType.POINTS_DOUBLER to "Points Doubler"
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
        binding.btnLinkingPairs.setOnClickListener { launchLinkingPairs() }
        binding.btnLinkingChain.setOnClickListener { launchLinkingChain() }
        binding.btnSorting.setOnClickListener { launchSorting() }
        binding.btnElimination.setOnClickListener { launchElimination() }
        binding.btnMissingLetter.setOnClickListener { launchMissingLetter() }
        binding.btnMatching.setOnClickListener { launchMatching() }
        binding.btnAvatarSelection.setOnClickListener { launchAvatarSelection() }
        binding.btnEndOfGameFact.setOnClickListener { launchEndOfGameFact() }
    }

    private fun setupStepper(powerType: PowerType, minus: View, plus: View, display: TextView) {
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
            if (powerType == PowerType.NONE) null
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

    private fun launchLinkingPairs() {
        // KIP-style: top/bottom half-circles, Direction 1=top, 2=bottom, match by AnswerID suffix
        networkManager.pendingLinking = ServerBeginLinkingAnsweringPhase(
            QuestionID = "DEBUG_LNK001",
            QuestionText = "Link the heroes to their villains",
            QuestionDuration = 45.0,
            LinkingAnswers = listOf(
                LinkingAnswer(DisplayText = "Superman", AnswerID = "LNK00086_AnswerA01", MatchIndex = 1, Direction = 1),
                LinkingAnswer(DisplayText = "Lex Luthor", AnswerID = "LNK00086_AnswerB01", MatchIndex = 1, Direction = 2),
                LinkingAnswer(DisplayText = "Batman", AnswerID = "LNK00086_AnswerA02", MatchIndex = 2, Direction = 1),
                LinkingAnswer(DisplayText = "The Joker", AnswerID = "LNK00086_AnswerB02", MatchIndex = 2, Direction = 2),
                LinkingAnswer(DisplayText = "Spider-Man", AnswerID = "LNK00086_AnswerA03", MatchIndex = 3, Direction = 1),
                LinkingAnswer(DisplayText = "Green Goblin", AnswerID = "LNK00086_AnswerB03", MatchIndex = 3, Direction = 2),
                LinkingAnswer(DisplayText = "James Bond", AnswerID = "LNK00086_AnswerA04", MatchIndex = 4, Direction = 1),
                LinkingAnswer(DisplayText = "Dr. No", AnswerID = "LNK00086_AnswerB04", MatchIndex = 4, Direction = 2),
                LinkingAnswer(DisplayText = "Harry Potter", AnswerID = "LNK00086_AnswerA05", MatchIndex = 5, Direction = 1),
                LinkingAnswer(DisplayText = "Voldemort", AnswerID = "LNK00086_AnswerB05", MatchIndex = 5, Direction = 2)
            )
        )
        findNavController().navigate(R.id.action_debugLauncher_to_linkingPairs)
    }

    private fun launchLinkingChain() {
        // Decades-style: words grouped by MatchIndex, Direction = order within phrase
        networkManager.pendingLinking = ServerBeginLinkingAnsweringPhase(
            QuestionID = "DEBUG_LNK002",
            QuestionText = "Link the words to form song titles",
            QuestionDuration = 45.0,
            LinkingAnswers = listOf(
                // "Walk This Way" (3 words)
                LinkingAnswer(DisplayText = "Walk", AnswerID = "LNK001_01", MatchIndex = 1, Direction = 1),
                LinkingAnswer(DisplayText = "This", AnswerID = "LNK001_02", MatchIndex = 1, Direction = 2),
                LinkingAnswer(DisplayText = "Way", AnswerID = "LNK001_03", MatchIndex = 1, Direction = 3),
                // "Into The Groove" (3 words)
                LinkingAnswer(DisplayText = "Into", AnswerID = "LNK001_04", MatchIndex = 2, Direction = 1),
                LinkingAnswer(DisplayText = "The", AnswerID = "LNK001_05", MatchIndex = 2, Direction = 2),
                LinkingAnswer(DisplayText = "Groove", AnswerID = "LNK001_06", MatchIndex = 2, Direction = 3),
                // "Move Your Feet" (3 words)
                LinkingAnswer(DisplayText = "Move", AnswerID = "LNK001_07", MatchIndex = 3, Direction = 1),
                LinkingAnswer(DisplayText = "Your", AnswerID = "LNK001_08", MatchIndex = 3, Direction = 2),
                LinkingAnswer(DisplayText = "Feet", AnswerID = "LNK001_09", MatchIndex = 3, Direction = 3),
                // "You're Still The One" (4 words)
                LinkingAnswer(DisplayText = "You're", AnswerID = "LNK001_10", MatchIndex = 4, Direction = 1),
                LinkingAnswer(DisplayText = "Still", AnswerID = "LNK001_11", MatchIndex = 4, Direction = 2),
                LinkingAnswer(DisplayText = "The", AnswerID = "LNK001_12", MatchIndex = 4, Direction = 3),
                LinkingAnswer(DisplayText = "One", AnswerID = "LNK001_13", MatchIndex = 4, Direction = 4)
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

    private fun launchElimination() {
        networkManager.pendingElimination = ServerBeginEliminatingAnsweringPhase(
            ChallengeId = "DEBUG_ELM001",
            QuestionText = "Smash the movie that ISN'T science fiction",
            QuestionDuration = 45.0,
            EliminatingAnswerData = listOf(
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Dumb & Dumber", IncorrectAnswers = listOf("Moon", "Jurassic Park")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Love Actually", IncorrectAnswers = listOf("District 9", "RoboCop")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Kill Bill: Volume 1", IncorrectAnswers = listOf("Avatar", "Planet Of The Apes")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Superbad", IncorrectAnswers = listOf("Interstellar", "Donnie Darko")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Bridesmaids", IncorrectAnswers = listOf("Blade Runner", "War Of The Worlds")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Step Brothers", IncorrectAnswers = listOf("Minority Report", "Predator")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "The Silence Of The Lambs", IncorrectAnswers = listOf("Looper", "Sunshine")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Knocked Up", IncorrectAnswers = listOf("The Martian", "Prometheus")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "When Harry Met Sally", IncorrectAnswers = listOf("Alien", "Star Trek")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "My Best Friend's Wedding", IncorrectAnswers = listOf("The Matrix", "Guardians Of The Galaxy")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Memento", IncorrectAnswers = listOf("Inception", "Edge Of Tomorrow")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Notting Hill", IncorrectAnswers = listOf("E.T. The Extra-Terrestrial", "Ready Player One")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "The Heat", IncorrectAnswers = listOf("Ex Machina", "Back To The Future")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Bridget Jones's Diary", IncorrectAnswers = listOf("Men In Black", "The Empire Strikes Back")),
                EliminatingAnswerData(CorrectInfo = "", IncorrectInfo = "", CorrectAnswer = "Love, Simon", IncorrectAnswers = listOf("Metropolis", "Aliens"))
            )
        )
        findNavController().navigate(R.id.action_debugLauncher_to_eliminationAnswering)
    }

    private fun launchMissingLetter() {
        networkManager.pendingMissingLetter = ServerBeginMissingLetterAnsweringPhase(
            ChallengeId = "DEBUG_CMP001",
            Question = "Complete the names of the comedy films",
            QuestionDuration = 30.0,
            MissingLetterAnswerData = listOf(
                MissingLetterAnswerData(answerInfo = "ANCHORMAN", correct = "A", letters = "AEIOU", finalDisplay = "ANCHORMAN"),
                MissingLetterAnswerData(answerInfo = "SCHOOL OF ROCK", correct = "O", letters = "AEIOU", finalDisplay = "SCHOOL OF ROCK"),
                MissingLetterAnswerData(answerInfo = "PAUL BLART: MALL COP", correct = "A", letters = "AEIOU", finalDisplay = "PAUL BLART: MALL COP"),
                MissingLetterAnswerData(answerInfo = "LITTLE MISS SUNSHINE", correct = "I", letters = "AEIOU", finalDisplay = "LITTLE MISS SUNSHINE"),
                MissingLetterAnswerData(answerInfo = "TRADING PLACES", correct = "A", letters = "AEIOU", finalDisplay = "TRADING PLACES"),
                MissingLetterAnswerData(answerInfo = "TED", correct = "E", letters = "AEIOU", finalDisplay = "TED"),
                MissingLetterAnswerData(answerInfo = "BRIDESMAIDS", correct = "I", letters = "AEIOU", finalDisplay = "BRIDESMAIDS"),
                MissingLetterAnswerData(answerInfo = "ZOOLANDER", correct = "O", letters = "AEIOU", finalDisplay = "ZOOLANDER"),
                MissingLetterAnswerData(answerInfo = "RUSH HOUR", correct = "U", letters = "AEIOU", finalDisplay = "RUSH HOUR"),
                MissingLetterAnswerData(answerInfo = "BAD SANTA", correct = "A", letters = "AEIOU", finalDisplay = "BAD SANTA")
            )
        )
        findNavController().navigate(R.id.action_debugLauncher_to_missingLetterAnswering)
    }

    private fun launchMatching() {
        networkManager.pendingMatching = ServerBeginMatchingAnsweringPhase(
            ChallengeId = "LSM00011en",
            QuestionDuration = 55.0,
            MatchingData = listOf(
                MatchingData(QuestionText = "90s film starring Michael Jordan: Space ___", AnswerText = "Jam"),
                MatchingData(QuestionText = "Actress who plays Storm in the X-Men movies: Halle ___", AnswerText = "Berry"),
                MatchingData(QuestionText = "Tommys surname in Rugrats", AnswerText = "Pickles"),
                MatchingData(QuestionText = "2007 Pixar film about a gastronomic rat: ___", AnswerText = "Ratatouille"),
                MatchingData(QuestionText = "2000 animated movie: ___ Run", AnswerText = "Chicken"),
                MatchingData(QuestionText = "Dr Seuss story: Green Eggs And ___", AnswerText = "Ham"),
                MatchingData(QuestionText = "Comic in which youd find Snoopy: ___", AnswerText = "Peanuts"),
                MatchingData(QuestionText = "2005 movie remake: Charlie And The ___ Factory", AnswerText = "Chocolate"),
                MatchingData(QuestionText = "Simba eats these with Timon and Pumbaa in The Lion King", AnswerText = "Bugs"),
                MatchingData(QuestionText = "Collectible stickers brand", AnswerText = "Panini"),
                MatchingData(QuestionText = "00s Adam Sandler movie: 50 First ___", AnswerText = "Dates"),
                MatchingData(QuestionText = "Jack Skellingtons title in The Nightmare Before Christmas: The ___ King", AnswerText = "Pumpkin"),
                MatchingData(QuestionText = "Popeyes favourite food", AnswerText = "Spinach"),
                MatchingData(QuestionText = "Lead character in 30 Rock played by Tina Fey: Liz ___", AnswerText = "Lemon"),
                MatchingData(QuestionText = "2007 song from MIKA", AnswerText = "Lollipop")
            ),
            DummyAnswerData = listOf("Bread", "Sausage", "Carrot", "Eggs")
        )
        findNavController().navigate(R.id.action_debugLauncher_to_matchingAnswering)
    }

    private fun launchEndOfGameFact() {
        networkManager.pendingEndOfGameFact = ClientEndOfGameFactCommandMessage(
            action = 32,
            time = 0.0,
            FactNumber = (0..255).random()
        )
        findNavController().navigate(R.id.action_debugLauncher_to_endOfGameFact)
    }

    private fun launchAvatarSelection() {
        val stubAvatars = listOf(
            ServerAvatarStatusMessage(AvatarID = "COWGIRL", Available = true),
            ServerAvatarStatusMessage(AvatarID = "GOFF", Available = true),
            ServerAvatarStatusMessage(AvatarID = "HOTDOGMAN", Available = true),
            ServerAvatarStatusMessage(AvatarID = "LOVER", Available = true),
            ServerAvatarStatusMessage(AvatarID = "MOUNTAINEER", Available = true),
            ServerAvatarStatusMessage(AvatarID = "SCIENTIST", Available = true),
            ServerAvatarStatusMessage(AvatarID = "SPACEMAN", Available = true),
            ServerAvatarStatusMessage(AvatarID = "MAGICIAN", Available = true)
        )
        networkManager.availableAvatars.clear()
        networkManager.availableAvatars.addAll(stubAvatars)

        val action = DebugLauncherFragmentDirections.actionDebugLauncherToCameraCapture(playerName = "Debug")
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
