package com.game.protocol

import kotlinx.serialization.*

// ==================== Message Models ====================

@Serializable
abstract class GameMessage {
    abstract val TypeString: String
}

@Serializable
data class InterfaceVersionMessage(
    override val TypeString: String = "InterfaceVersionMessage",
    val InterfaceVersion: String
) : GameMessage()

@Serializable
data class SessionStateMessage(
    override val TypeString: String = "SessionStateMessage",
    val SessionID: Long
) : GameMessage()

@Serializable
data class ClientRequestPlayerIDMessage(
    override val TypeString: String = "ClientRequestPlayerIDMessage",
    val UID: String
) : GameMessage()

@Serializable
data class AssignPlayerIDAndSlotMessage(
    override val TypeString: String = "AssignPlayerIDAndSlotMessage",
    val PlayerID: Int,
    val SlotID: Int,
    val UDPPortOffset: Int,
    val DisplayName: String,
    val PSNID: String
) : GameMessage()

@Serializable
data class ResourceRequirement(
    val GUID: String,
    val Rqrmnt: Int
)

@Serializable
data class ResourceRequirementsMessage(
    override val TypeString: String = "ResourceRequirementsMessage",
    val Requirements: List<ResourceRequirement>
) : GameMessage()

@Serializable
data class AllResourcesReceivedMessage(
    override val TypeString: String = "AllResourcesReceivedMessage",
    val Requirements: List<ResourceRequirement>
) : GameMessage()

@Serializable
data class ClientQuizCommandMessage(
    override val TypeString: String = "ClientQuizCommandMessage",
    val action: Int,
    val time: Double
) : GameMessage() {
    companion object {
        const val ACTION_CONTINUE = 4
        const val ACTION_READY = 14
        const val ACTION_EXIT = 15
        const val ACTION_PAUSED = 29
        const val ACTION_UNPAUSED = 30
        const val ACTION_RESET_TO_NAME = 31
    }
}

@Serializable // 1
data class ClientRequestAvatarStatusMessage(
    override val TypeString: String = "KnowledgeIsPower.ClientRequestAvatarStatusMessage"
) : GameMessage()

@Serializable // 2
data class ServerAvatarStatusMessage(
    override val TypeString: String = "KnowledgeIsPower.ServerAvatarStatusMessage",
    val AvatarID: String,
    val Available: Boolean
) : GameMessage()

@Serializable // 3
data class ClientRequestAvatarMessage(
    override val TypeString: String = "KnowledgeIsPower.ClientRequestAvatarMessage",
    val RequestID: String,
    val AvatarID: String,
    val Request: Boolean
) : GameMessage()

@Serializable // 4
data class ServerAvatarRequestResponseMessage(
    override val TypeString: String = "KnowledgeIsPower.ServerAvatarRequestResponseMessage",
    val RequestID: String,
    val AvatarID: String,
    val Available: Boolean
) : GameMessage()


@Serializable
data class ClientPlayerProfileMessage(
    override val TypeString: String = "ClientPlayerProfileMessage",
    val playerName: String,
    val uppercasePlayerName: String,
    val deviceCultureName: String,
    val playerCardId: String
) : GameMessage()

@Serializable
data class DeviceInfoMessage(
    override val TypeString: String = "DeviceInfoMessage",
    val Response: Int,
    val DeviceSize: Int,
    val DeviceOS: Int,
    val DeviceModel: String,
    val DeviceType: String,
    val DeviceUID: String,
    val DeviceOperatingSystem: String
) : GameMessage()

@Serializable
data class ClientImageResourceContentTransferMessage(
    override val TypeString: String = "ClientImageResourceContentTransferMessage",
    val TransferID: Int,
    val ImageGUID: String,
    val ImgType: Int
) : GameMessage()

@Serializable
data class ImageResourceContentTransferMessage(
    override val TypeString: String = "ImageResourceContentTransferMessage",
    val TransferID: Int,
    val ImageGUID: String,
    val Acknowledge: Boolean,
    @Transient
    var image: ByteArray? = null
) : GameMessage()

@Serializable
data class ClientGameIDMessage(
    override val TypeString: String = "ClientGameIDMessage",
    val GameID: String
) : GameMessage()

@Serializable
data class ClientHoldingScreenCommandMessage(
    override val TypeString: String = "ClientHoldingScreenCommandMessage",
    val action: Int, // 5 = look at the TV;
    val time: Double,
    val HoldingScreenText: String,
    val HoldingScreenType: Int, // 4 = look at the tv;  9 = get ready
    val OtherPlayerIndex: Int,
    val ShowPortraitPhotoControls: Boolean
) : GameMessage()

@Serializable
data class PlayerJoinedMessage(
    override val TypeString: String = "PlayerJoinedMessage",
    val CurrentPlayerID: Int,
    val OldPlayerID: Int,
    val SlotID: Int
) : GameMessage()

@Serializable
data class PlayerLeftMessage(
    override val TypeString: String = "PlayerLeftMessage",
    val PlayerID: Int
) : GameMessage()

@Serializable
data class PlayerNameQuizStateMessage(
    override val TypeString: String = "PlayerNameQuizStateMessage",
    val PlayerName: String,
    val PlayerID: Int
) : GameMessage()

@Serializable
data class ColorTint(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float
)

@Serializable
data class ServerColourMessage(
    override val TypeString: String = "KnowledgeIsPower.ServerColourMessage",
    val BackgroundTint: ColorTint,
    val PrimaryTint: ColorTint,
    val SecondaryTint: ColorTint,
    val Rainbow: Boolean,
    val RoundType: Int = -1
) : GameMessage()

@Serializable
data class StartGameButtonPressedResponseMessage(
    override val TypeString: String = "StartGameButtonPressedResponseMessage",
    val Response: Int = 9
) : GameMessage()

@Serializable
data class ContinuePressedResponseMessage(
    override val TypeString: String = "ContinuePressedResponseMessage",
    val Response: Int = 3
) : GameMessage()

@Serializable
data class CategoryChoice(
    val DisplayText: String,
    val Colour: ColorTint,
    val DoorIndex: Int,
    val ChoiceType: Int
)

@Serializable
data class ServerCategorySelectChoices(
    override val TypeString: String = "KnowledgeIsPower.ServerCategorySelectChoices",
    val CategoryChoices: List<CategoryChoice>,
    val BackgroundTint: ColorTint,
    val PrimaryTint: ColorTint,
    val SecondaryTint: ColorTint
) : GameMessage()

@Serializable
data class ServerRequestCategorySelectChoice(
    override val TypeString: String = "KnowledgeIsPower.ServerRequestCategorySelectChoice"
) : GameMessage()

@Serializable
data class ClientCategorySelectChoice(
    override val TypeString: String = "KnowledgeIsPower.ClientCategorySelectChoice",
    val ChosenCategoryIndex: Int
) : GameMessage()

@Serializable
data class ServerBeginCategorySelectOverride(
    override val TypeString: String = "KnowledgeIsPower.ServerBeginCategorySelectOverride",
    val DurationSeconds: Double,
    val InitialCategorySelectChoice: String,
    val DoorIndex: Int
) : GameMessage()

@Serializable
data class ClientCategorySelectOverride(
    override val TypeString: String = "KnowledgeIsPower.ClientCategorySelectOverride",
    val DurationSeconds: Double
) : GameMessage()

@Serializable
data class ServerStopCategorySelectOverride(
    override val TypeString: String = "KnowledgeIsPower.ServerStopCategorySelectOverride"
) : GameMessage()

@Serializable
data class ClientStopCategorySelectOverrideResponse(
    override val TypeString: String = "KnowledgeIsPower.ClientStopCategorySelectOverrideResponse",
    val OverrideSent: Boolean
) : GameMessage()

@Serializable
data class ServerCategorySelectOverrideSuccess(
    override val TypeString: String = "KnowledgeIsPower.ServerCategorySelectOverrideSuccess",
    val CategorySelectOverrideSuccess: Boolean,
    val CategorySelectOverridePlayerName: String
) : GameMessage()

@Serializable
data class TriviaAnswer(
    val DisplayIndex: Int,
    val DisplayText: String,
    val IsCorrect: Boolean
)

@Serializable
data class PowerPlayPlayer(
    val SlotIndex: Int,
    val Name: String,
    val ImageGUID: String,
    val Colour: ColorTint,
    val Self: Boolean,
    val Away: Boolean
)

@Serializable
data class ActivePowerPlay(
    val PowerType: Int,
    val Count: Int,
    val Culprits: List<Int>
)

@Serializable
data class ServerBeginTriviaAnsweringPhase(
    override val TypeString: String = "KnowledgeIsPower.ServerBeginTriviaAnsweringPhase",
    val QuestionID: String,
    val QuestionText: String,
    val QuestionDuration: Double,
    val Answers: List<TriviaAnswer>,
    val PowerPlays: List<ActivePowerPlay>,
    val PowerPlayPlayers: List<PowerPlayPlayer>,
    val RoundType: Int,
    val BackgroundTint: ColorTint,
    val PrimaryTint: ColorTint,
    val SecondaryTint: ColorTint
) : GameMessage()

@Serializable
data class LinkingAnswer(
    val DisplayText: String,
    val AnswerID: String,
    val MatchIndex: Int,
    val Direction: Int
)

@Serializable
data class ServerBeginLinkingAnsweringPhase(
    override val TypeString: String = "KnowledgeIsPower.ServerBeginLinkingAnsweringPhase",
    val QuestionID: String,
    val QuestionText: String,
    val QuestionDuration: Double,
    val LinkingAnswers: List<LinkingAnswer>
) : GameMessage()

@Serializable
data class ClientLinkingAnswerEntry(
    val FromID: String,
    val ToID: String,
    val Correct: Boolean
)

@Serializable
data class ClientLinkingAnswer(
    override val TypeString: String = "KnowledgeIsPower.ClientLinkingAnswer",
    val ClientLinkingCorrectAnswerCount: Int,
    val ClientAnswers: List<ClientLinkingAnswerEntry>
) : GameMessage()

@Serializable
data class PowerPlay(
    val DisplayIndex: Int,
    val PowerType: Int,
    val PowerTarget: Int,
    val PowerPlayTargets: List<Int>,
    val New: Boolean,
    val TargetCount: Int
)

@Serializable
data class ServerBeginPowerPlayPhase(
    override val TypeString: String = "KnowledgeIsPower.ServerBeginPowerPlayPhase",
    val PowerPlays: List<PowerPlay>,
    val PowerPlayPlayers: List<PowerPlayPlayer>,
    val RoundType: Int
) : GameMessage()

@Serializable
data class ServerRequestPowerPlayChoice(
    override val TypeString: String = "KnowledgeIsPower.ServerRequestPowerPlayChoice"
) : GameMessage()

@Serializable
data class ClientPowerPlayChoice(
    override val TypeString: String = "KnowledgeIsPower.ClientPowerPlayChoice",
    val PowerPlaySlotIndex: Int,
    val TargetSlotIndex: List<Int>
) : GameMessage()

@Serializable
data class ClientTriviaAnswer(
    override val TypeString: String = "KnowledgeIsPower.ClientTriviaAnswer",
    val ChosenAnswerDisplayIndex: Int,
    val AnswerTime: Double,
    val NumBombsExploded: Int = 0,
    val PaintCleared: Boolean = false,
    val IceCleared: Boolean = false,
    val NumWrongAnswers: Int = 0,
    val TotalPaintLayersPerAnswer: Int = 0,
    val PaintLayersClearedAnswer0: Int = 0,
    val PaintLayersClearedAnswer1: Int = 0,
    val PaintLayersClearedAnswer2: Int = 0,
    val PaintLayersClearedAnswer3: Int = 0,
    val TotalIceLayersPerAnswer: Int = 0,
    val IceLayersClearedAnswer0: Int = 0,
    val IceLayersClearedAnswer1: Int = 0,
    val IceLayersClearedAnswer2: Int = 0,
    val IceLayersClearedAnswer3: Int = 0
) : GameMessage()
