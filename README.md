# Game Remote Client - Android App

A Kotlin-based Android application that serves as a remote control client for a multiplayer quiz game. Players can connect to a game server, join lobbies, and answer quiz questions using their mobile devices.

## Features

### 📱 Screens

1. **Server Discovery**
   - Manual IP address entry
   - Automatic network scanning for game servers
   - List of discovered servers with connection details

2. **Player Registration**
   - Enter your player name
   - Name validation (minimum 2 characters)

3. **Avatar Selection**
   - Choose a default avatar
   - Capture photo using front camera
   - CameraX integration for smooth camera experience
   - Photo preview and retake functionality

4. **Waiting Room / Lobby**
   - View all connected players
   - Real-time player list updates
   - Host can start the game
   - Visual indicators for host/player roles

5. **Get Ready Screen**
   - Transition screen between rounds
   - Countdown and status updates
   - Smooth state transitions

6. **Quiz Screen**
   - Full-screen 4-button layout (2x2 grid)
   - Color-coded answers:
     - Green (Top-Left) - Answer A
     - Red (Top-Right) - Answer B
     - Yellow (Bottom-Left) - Answer C
     - Blue (Bottom-Right) - Answer D
   - Countdown timer
   - Visual feedback on answer submission
   - Disabled buttons after answer selection

## 🏗️ Architecture

### Technology Stack

- **Language**: Kotlin
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM pattern with repository pattern
- **Navigation**: Jetpack Navigation Component with Safe Args
- **UI**: Material Design 3, View Binding
- **Networking**: OkHttp, WebSocket, Retrofit
- **Async**: Kotlin Coroutines & Flow
- **Camera**: CameraX
- **Image Loading**: Coil

### Project Structure

```
app/
├── src/main/
│   ├── java/com/game/remoteclient/
│   │   ├── models/          # Data models
│   │   │   ├── Player.kt
│   │   │   ├── GameServer.kt
│   │   │   ├── GameState.kt
│   │   │   └── QuizQuestion.kt
│   │   ├── network/         # Network layer
│   │   │   └── NetworkManager.kt
│   │   ├── ui/              # UI components
│   │   │   ├── ServerDiscoveryFragment.kt
│   │   │   ├── ServerAdapter.kt
│   │   │   ├── NameEntryFragment.kt
│   │   │   ├── AvatarSelectionFragment.kt
│   │   │   ├── WaitingRoomFragment.kt
│   │   │   ├── PlayerAdapter.kt
│   │   │   ├── GetReadyFragment.kt
│   │   │   └── QuizFragment.kt
│   │   ├── utils/           # Utility classes
│   │   │   └── PermissionHelper.kt
│   │   └── MainActivity.kt
│   ├── res/
│   │   ├── layout/          # XML layouts
│   │   ├── navigation/      # Navigation graph
│   │   ├── values/          # Strings, colors, themes
│   │   └── mipmap/          # App icons
│   └── AndroidManifest.xml
```

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- Gradle 8.2+
- JDK 8 or higher

### Building the Project

1. Clone the repository:
```bash
git clone <repository-url>
cd reverse-of-power
```

2. Open the project in Android Studio

3. Sync Gradle files:
   - File → Sync Project with Gradle Files

4. Build the project:
```bash
./gradlew build
```

5. Run on device/emulator:
   - Connect Android device or start emulator
   - Click Run (▶️) in Android Studio
   - Or: `./gradlew installDebug`

## 🔌 Network Communication

The app uses WebSocket for real-time communication with the game server.

### Expected Server Endpoints

- **WebSocket**: `ws://<server-ip>:8080/game`

### Message Format

The app sends/receives JSON messages:

**Join Game:**
```json
{
  "type": "join",
  "name": "PlayerName"
}
```

**Submit Answer:**
```json
{
  "type": "answer",
  "answer": 0
}
```

**Start Game (Host only):**
```json
{
  "type": "start_game"
}
```

## 📋 Permissions

The app requires the following permissions:

- `INTERNET` - Network communication
- `ACCESS_NETWORK_STATE` - Check network status
- `ACCESS_WIFI_STATE` - WiFi network discovery
- `CHANGE_WIFI_MULTICAST_STATE` - Network scanning
- `CAMERA` - Avatar photo capture (optional)

## 🎨 UI Customization

### Colors

Edit `app/src/main/res/values/colors.xml`:
- `answer_a` - Green button color
- `answer_b` - Red button color
- `answer_c` - Yellow button color
- `answer_d` - Blue button color
- `primary` - App primary color
- `accent` - Accent color

### Strings

All user-facing text is in `app/src/main/res/values/strings.xml` for easy localization.

## 🔧 Development Notes

### Network Manager

The `NetworkManager` is a singleton that handles:
- Server discovery via network scanning
- WebSocket connection management
- Real-time state updates via Kotlin Flow
- Message parsing and event handling

### State Management

Game state is managed using Kotlin StateFlow:
- `gameState: StateFlow<GameState>` - Current game state
- `players: StateFlow<List<Player>>` - Connected players
- `currentQuestion: StateFlow<QuizQuestion?>` - Current quiz question

### Navigation

Safe Args generates type-safe navigation classes:
- Arguments passed between screens
- Compile-time safety for navigation

## 🧪 Testing

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

## 📝 TODO / Future Enhancements

- [ ] Implement JSON message parsing in NetworkManager
- [ ] Add server discovery via mDNS/Bonjour
- [ ] Implement avatar upload to server
- [ ] Add sound effects and haptic feedback
- [ ] Implement score tracking and leaderboard
- [ ] Add game results screen
- [ ] Support landscape orientation
- [ ] Add dark theme support
- [ ] Implement reconnection logic
- [ ] Add more avatar customization options

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👥 Authors

- Development Team

## 🙏 Acknowledgments

- Material Design Components
- Jetpack Libraries
- Kotlin Coroutines
- CameraX Library
