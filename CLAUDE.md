# Knowledge Is Power - Reverse-Engineered Game Controller

Android app that acts as a controller for the PS4 game "Knowledge Is Power" by reverse-engineering its proprietary UDP protocol. Replaces the official PlayStation companion app.

## Project Overview

- **Package**: `com.game.remoteclient`
- **Language**: Kotlin (2.3.0)
- **Min SDK**: 26 (Android 8.0), **Target SDK**: 35, **Compile SDK**: 36
- **Architecture**: Fragment-based MVVM with Jetpack Navigation
- **Build**: Gradle with Kotlin DSL, Java 17

## Key Directories

```
app/src/main/java/com/game/
├── protocol/                    # Reverse-engineered protocol layer
│   ├── GameMessages.kt          # 30+ serializable message types
│   ├── GameProtocolClient.kt    # UDP client, packet encode/decode, fragmentation
│   └── ProtocolDecoder.kt       # Standalone packet decoder (sealed class hierarchy)
└── remoteclient/
    ├── GameRemoteClientApplication.kt
    ├── models/                  # GameServer, GameState enum, Player
    ├── network/
    │   └── NetworkManager.kt    # Singleton, state management, message routing
    ├── ui/                      # 6 fragments + 4 custom views + 3 adapters
    └── utils/
        └── PermissionHelper.kt  # Camera permission

app/src/test/java/com/game/protocol/
├── GameProtocolClientTest.kt    # Packet parsing unit tests (fixture-based)
└── UdpPacketFixtures.kt         # Real captured packets as byte arrays (~260KB)

docs/                            # Protocol captures and analysis
├── command format.txt           # Binary packet format reference
├── protocol ordering.txt        # Message sequence documentation
├── *.pcapng                     # Wireshark network captures
├── *.txt                        # Decoded packet traces
└── ui/                          # Game UI screenshots
```

## Protocol Details

### Transport
- **UDP** on port **9066** (server) / **9060** (client listen)
- Custom binary framing with JSON payloads
- ACK/NACK reliability layer on top of UDP

### Data Packet Structure (22-byte header + payload)
All fields are **little-endian int32** unless noted.
```
Bytes 0-1:   0xAE 0x7F (magic, identifies a data packet)
Bytes 2-5:   Message ID (LE int32, auto-incrementing counter)
Bytes 6-9:   Total packets for this message (LE int32, 1 if unfragmented)
Bytes 10-13: Packet index, zero-based (LE int32)
Bytes 14-17: Payload length (LE int32, bytes in this packet after header)
Bytes 18-21: Byte offset for reassembly (LE int32, cumulative offset)
Bytes 22+:   Payload (up to 1002 bytes per packet; 1024 max packet - 22 header)
```

### Payload Body (after reassembly of all fragments)
```
Bytes 0-1:   0x33 0x29 (body magic)
Bytes 2-5:   Transfer ID (LE int32) if > 0, otherwise this IS the data type marker
Bytes 6-9:   Body length (LE int32)
Remaining:   Payload data (JSON string, multi-JSON, or JPEG)
```
When Transfer ID > 0 (used for images), the actual data type marker follows at bytes 6-9 and body length at bytes 10-13.

### Data Type Markers (little-endian int32)
| Marker bytes         | Int value     | Type      | Content |
|----------------------|---------------|-----------|---------|
| `B1 E2 FF FF`        | -7503         | JSON      | Single JSON object |
| `84 12 40 EE`        | -297790844    | MultiJSON | Sequence of (length + type + JSON) entries, terminated by `0x32 0x90` |
| `FF D8 FF E0`        | -520103681    | JPEG      | Raw JPEG image data |

### MultiJSON Sub-entry Format
```
Bytes 0-3:   Document length (LE int32)
Bytes 4-7:   Document type marker (LE int32, e.g. -7503 for JSON)
Bytes 8+:    Document data
```

### Non-Data Packet Types (no 0xAE7F header)
- **ACK**: `0x8A 0x33` + message ID (LE int32) + 32 zero bytes (38 bytes total). All-zero payload = ACK; non-zero payload = NACK
- **Connection request**: `0x8A 0x33 0xFF 0xFF 0xFF 0xFF` + 32 zero bytes (38 bytes total)
- **Connection ack**: Same format as ACK with `0xFF 0xFF 0xFF 0xFF` as message ID
- **Device UID**: `0x0C 0x89 0xE8 0x84` + 4 flag bytes (`0x61 0x03 0xF4 0x63`) + UID length (LE int32) + UID string (UTF-8)


### Connection Flow
```
Client                          Server
  |-- connection request (0x8A33) -->|
  |-- device UID (0x0C89) --------->|
  |<--- connection ack (0x8A33) ----|
  |<--- InterfaceVersionMessage ----|  (version "KIP_2016-11-14-1222")
  |<--- SessionStateMessage --------|  (assigns session ID)
  |--- ClientRequestPlayerIDMessage>|
  |<--- AssignPlayerIDAndSlotMessage|  (playerId, slotId, displayName)
  |--- DeviceInfoMessage ---------->|
  |--- ClientRequestAvatarStatus -->|
  |<--- ResourceRequirementsMessage |
  |--- AllResourcesReceivedMessage->|
  |<--- ServerAvatarStatusMessage[] |  (8 avatars)
  |<--- ClientQuizCommandMessage    |  (action=31: ready for avatar selection)
```

### Game Message Types (TypeString-based JSON dispatch)
- **Session**: InterfaceVersionMessage, SessionStateMessage, ClientRequestPlayerIDMessage, AssignPlayerIDAndSlotMessage, RejoiningClientOwnProfileMessage
- **Avatar**: ClientRequestAvatarStatusMessage, ServerAvatarStatusMessage, ClientRequestAvatarMessage, ServerAvatarRequestResponseMessage (prefixed `KnowledgeIsPower.`)
- **Profile**: ClientPlayerProfileMessage, DeviceInfoMessage
- **Images**: ClientImageResourceContentTransferMessage, ImageResourceContentTransferMessage
- **Resources**: ResourceRequirementsMessage, RequestResourceMessage, AllResourcesReceivedMessage
- **Game flow**: ClientQuizCommandMessage (actions: 4=continue, 14=game begins, 15=exit, 29=paused, 30=unpaused/look at TV, 31=ready screen), ClientHoldingScreenCommandMessage (HoldingScreenType: 4="Look at the TV", 9="Get ready!"), StartGameButtonPressedResponseMessage, ContinuePressedResponseMessage
- **Category**: ServerCategorySelectChoices, ServerRequestCategorySelectChoice, ClientCategorySelectChoice, ServerBeginCategorySelectOverride, ServerStopCategorySelectOverride, ClientStopCategorySelectOverrideResponse, ServerCategorySelectOverrideSuccess, ClientCategorySelectOverride (prefixed `KnowledgeIsPower.`)
- **Trivia**: ServerBeginTriviaAnsweringPhase, ClientTriviaAnswer (prefixed `KnowledgeIsPower.`)
- **Linking**: ServerBeginLinkingAnsweringPhase, ClientLinkingAnswer (prefixed `KnowledgeIsPower.`)
- **Sorting**: ServerBeginSortingAnsweringPhase, ClientSortingAnswer (prefixed `KnowledgeIsPower.`)
- **Power Play**: ServerBeginPowerPlayPhase, ServerRequestPowerPlayChoice, ClientPowerPlayChoice (prefixed `KnowledgeIsPower.`)
- **End of Game**: ServerRequestEndOfGameFactCount, ClientEndOfGameFactCount (prefixed `KnowledgeIsPower.`), ClientEndOfGameFactCommandMessage (bare TypeString)
- **Players**: PlayerJoinedMessage, PlayerLeftMessage, PlayerNameQuizStateMessage
- **UI**: ServerColourMessage, ClientGameIDMessage

### Power Play Types
| PowerType | Name           | Description                  | Color       |
|-----------|----------------|------------------------------|-------------|
| 4         | FREEZE         | Encase answers in ice        | Ice blue    |
| 5         | BOMBLES        | Throw bombs over answers     | Orange-red  |
| 6         | NIBBLERS       | Nibble away at answers       | Orange-red  |
| 7         | GLOOP          | Cover answers in gloop       | Green       |
| 11        | DOUBLE TROUBLE | Freeze and bombles combined  | Purple      |

### Protocol Auto-Responses (handled by GameProtocolClient)
These messages are handled automatically at the protocol level without UI involvement:
- `SessionStateMessage` → sends `ClientRequestPlayerIDMessage`
- `AssignPlayerIDAndSlotMessage` → sends `DeviceInfoMessage` + `ClientRequestAvatarStatusMessage`
- `ResourceRequirementsMessage` → requests missing resources or sends `AllResourcesReceivedMessage`
- `ServerRequestEndOfGameFactCount` → sends `ClientEndOfGameFactCount(FactCount=0)`

### Image Transfer
Images (JPEG) use a two-phase transfer:
1. Send `ClientImageResourceContentTransferMessage` (JSON, may itself be fragmented) with TransferID and ImageGUID
2. Send JPEG data with same TransferID in the body header (Transfer ID field > 0), data type marker is JPEG (`0xFFD8FFE0`)
3. JPEG data and control messages can arrive in either order; pending maps handle both cases
4. Player avatar is sent twice (two different TransferIDs) with `ClientPlayerProfileMessage` between them

### Fragmentation
Large payloads are split across multiple UDP packets sharing the same message ID:
- Max payload per packet: **1002 bytes** (1024 total packet size - 22 byte header)
- Header field `Total packets` > 1 indicates fragmented message
- Each fragment has its own `Packet index` (0-based) and `Byte offset` (cumulative)
- Reassembly: collect all fragments by message ID, concatenate in packet index order, then decode the body
- `encodeMessage()` always uses `wrapAndChunk()` to handle fragmentation transparently

## UI Flow (Navigation Graph)
```
ServerDiscoveryFragment
  ├─(new player)→ NameEntryFragment → CameraCaptureFragment → AvatarSelectionFragment → WaitingRoomFragment
  ├─(saved profile)→ WaitingRoomFragment  (auto-sends avatar request + image + profile)
  └─(rejoin)→ HoldingScreenFragment  (server sends RejoiningClientOwnProfileMessage)

WaitingRoomFragment → HoldingScreenFragment
HoldingScreenFragment → CategorySelectionFragment | TriviaAnsweringFragment | ContinueFragment
                       | PowerPlayFragment | LinkingAnswersFragment | SortingAnswersFragment
CategorySelectionFragment → PowerPickFragment | TriviaAnsweringFragment
```

### Custom Views
- **CircularStartButton**: Animated circular progress/start button
- **DoorView**: Category selection door with Canvas rendering
- **RetroTvView**: TV-shaped frame for holding screen
- **SunburstBackgroundView**: Animated sunburst rays background
- **LinkingLineView**: Drag-to-connect line overlay for linking minigame

## Build & Test

```bash
# Build
./gradlew assembleDebug

# Unit tests (protocol parsing)
./gradlew test

# Replay a PCAP file through decoder
./gradlew test -Dpcap.file=docs/successful-game-start.pcapng --tests "PcapReplayTest"
```

## Key Dependencies
- **OkHttp3** (5.3.2) + **Retrofit2** (3.0.0): present but protocol is custom UDP, not HTTP
- **kotlinx-serialization-json** (1.9.0): JSON encode/decode for game messages
- **CameraX** (1.4.1): Player avatar photo capture
- **Coil** (2.7.0): Image loading
- **Jetpack Navigation** (2.9.6): Fragment navigation with SafeArgs

## Important Patterns
- `NetworkManager` is a singleton accessed via `getInstance()`, initialized in `GameRemoteClientApplication`
- `GameProtocolClient` handles all protocol logic; `NetworkManager` wraps it with state management and UI callbacks
- `GameProtocolClient.handleProtocolMessage()` auto-responds to protocol-level messages (session, resources, end-of-game facts)
- `ProtocolDecoder` is a standalone decoder (sealed class return types) used by PcapReplay and tests
- Message routing uses `TypeString` field — some messages use bare names, avatar/category/trivia messages are prefixed with `KnowledgeIsPower.`
- Thread safety: `sendLock` mutex prevents interleaved UDP packets during chunked sends
- Image matching: JPEG data and control messages can arrive in either order; pending maps handle both cases
- Received images stored in `NetworkManager.receivedImages` (GUID → ByteArray) for use in UI (e.g. player photos in PowerPlay targets)
- Trivia answering auto-sends a no-answer (`ChosenAnswerDisplayIndex=-1`) via CountDownTimer when QuestionDuration expires
- Category selection is one-shot: once a door is chosen, subsequent ServerCategorySelectChoices updates preserve the highlight without re-enabling selection

## Known TODOs not yet implemented
- Avatar selection assets are not bundled, we need to reinvent them, as well as face masks
- End of game facts are not bundled in the game, we need to reinvent them- Trivia power play effects not rendered (ice/bombs/gloop overlays on answer buttons)
- Mid-game reconnect is sometimes buggy, getting the whole game stuck, especially around powerplay (is it resource management?)
