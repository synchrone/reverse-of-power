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
    val action: Int, // 14 = show ready button; 31 = go back to name selection screen; 29, 30, 15 = something around game being exited
    val time: Double
) : GameMessage()

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
data class ServerBeginTriviaAnsweringPhase(
    override val TypeString: String = "KnowledgeIsPower.ServerBeginTriviaAnsweringPhase",
    val QuestionID: String,
    val QuestionText: String,
    val QuestionDuration: Double,
    val Answers: List<TriviaAnswer>,
    val PowerPlays: List<String>,
    val PowerPlayPlayers: List<PowerPlayPlayer>,
    val RoundType: Int,
    val BackgroundTint: ColorTint,
    val PrimaryTint: ColorTint,
    val SecondaryTint: ColorTint
) : GameMessage()

// ==================== Protocol Packet Classes ====================

data class ProtocolPacket(
    val header: Byte,
    val secondaryHeader: Byte,
    val messageId: Short,
    val packetNumber: Short,
    val totalPackets: Short,
    val dataLength: Long,
    val payload: ByteArray
)

data class AckPacket(
    val header: Byte = 0x8a.toByte(),
    val secondaryHeader: Byte = 0x33.toByte(),
    val messageId: Byte,
    val padding: ByteArray = ByteArray(34)
)
