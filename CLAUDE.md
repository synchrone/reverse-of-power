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

### Packet Structure (22-byte header)
```
Bytes 0-1:   0xAE 0x7F (magic)
Byte 2:      Message ID (auto-incrementing counter)
Bytes 3-6:   Total packets for this message (big-endian int)
Bytes 7-10:  Packet index, zero-based (big-endian int)
Bytes 11-13: 0x00 0x00 0x00 (static)
Bytes 14-17: Payload length (little-endian int)
Bytes 18-21: Byte offset for reassembly (little-endian int)
```

### Payload Body (after header)
```
Bytes 0-1:   0x33 0x29 (body magic)
Bytes 2-5:   Transfer ID (if > 0) OR data type marker
Bytes 6-9:   Body length (little-endian int)
Remaining:   Payload data
```

### Data Type Markers (little-endian int32)
| Marker bytes         | Int value     | Type      |
|----------------------|---------------|-----------|
| `B1 E2 FF FF`        | -7503         | JSON      |
| `84 12 40 EE`        | -297790844    | MultiJSON |
| `FF D8 FF E0`        | -520103681    | JPEG      |

### Other Packet Types
- **ACK**: `0x8A 0x33` + message ID + 34 zero bytes (38 bytes total)
- **Connection request**: `0x8A 0x33 0xFF 0xFF 0xFF 0xFF` + 32 zero bytes
- **Device UID**: `0x0C 0x89 0xE8 0x84` + flags + UID length (LE int) + UID string
- **Game in progress**: `0xE5 0x58 0xFC 0x89 0x5C 0x8D 0xF0 0x01`

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
- **Session**: InterfaceVersionMessage, SessionStateMessage, ClientRequestPlayerIDMessage, AssignPlayerIDAndSlotMessage
- **Avatar**: ClientRequestAvatarStatusMessage, ServerAvatarStatusMessage, ClientRequestAvatarMessage, ServerAvatarRequestResponseMessage (prefixed `KnowledgeIsPower.`)
- **Profile**: ClientPlayerProfileMessage, DeviceInfoMessage
- **Images**: ClientImageResourceContentTransferMessage, ImageResourceContentTransferMessage
- **Game flow**: ClientQuizCommandMessage (actions: 14=game begins, 15=exit, 29=paused, 30=unpaused/look at TV, 31=ready screen), ClientHoldingScreenCommandMessage, StartGameButtonPressedResponseMessage
- **Category**: ServerCategorySelectChoices, ServerRequestCategorySelectChoice, ClientCategorySelectChoice, ServerBeginCategorySelectOverride, ServerStopCategorySelectOverride (prefixed `KnowledgeIsPower.`)
- **Trivia**: ServerBeginTriviaAnsweringPhase (prefixed `KnowledgeIsPower.`)
- **Players**: PlayerJoinedMessage, PlayerLeftMessage, PlayerNameQuizStateMessage
- **UI**: ServerColourMessage, ClientGameIDMessage

### Image Transfer
Images (JPEG) use a two-phase transfer:
1. Send `ClientImageResourceContentTransferMessage` (JSON) with TransferID and ImageGUID
2. Send chunked binary JPEG with same TransferID in body header
3. Fragment size: 1002 bytes payload (1024 - 22 header)
4. Player avatar is sent twice (two different TransferIDs) with profile message between them

### Fragmentation
Large messages are split across multiple UDP packets sharing the same message ID. Reassembly uses packet index (0-based) and total packet count from the header.

## UI Flow (Navigation Graph)
```
ServerDiscoveryFragment → NameEntryFragment → AvatarSelectionFragment
    → WaitingRoomFragment → HoldingScreenFragment → CategorySelectionFragment
```

### Custom Views
- **CircularStartButton**: Animated circular progress/start button
- **DoorView**: Category selection door with Canvas rendering
- **RetroTvView**: TV-shaped frame for holding screen
- **SunburstBackgroundView**: Animated sunburst rays background

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
- `ProtocolDecoder` is a standalone decoder (sealed class return types) used by PcapReplay and tests
- Message routing uses `TypeString` field — some messages use bare names, avatar/category/trivia messages are prefixed with `KnowledgeIsPower.`
- Thread safety: `sendLock` mutex prevents interleaved UDP packets during chunked sends
- Image matching: JPEG data and control messages can arrive in either order; pending maps handle both cases

## Known Issues / Areas of Active Work
- Server discovery is stub (TODO: UPnP scanning)
- powerplay New=true means we can show a randomizing animation to attract attention to the new type
- 