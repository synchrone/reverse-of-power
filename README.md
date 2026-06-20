# Reverse of Power

**Reverse of Power** is a free, open-source Android app that turns your phone or tablet into a controller for the PlayStation "PlayLink" party quiz games — an unofficial, community-built replacement for the original companion app, so the games stay playable now that the official app is no longer maintained.

It speaks the game's local-network protocol directly: point it at a retail copy running on a stock PS4 or PS5 on the same Wi-Fi, and play exactly as the creators intended — pick categories, race through fastest-finger trivia, link and sort answers, and sabotage your friends with power plays.

## Compatibility

Reverse of Power works with the retail party quiz title **Knowledge is Power** and its sequel **Knowledge is Power: Decades** (Decades support is a work in progress). You need a legitimately purchased copy of the game running on your own console — this app only stands in for the phone controller; it is not the game and contains none of the game.

- PS4 and PS5 compatible
- Android 8.0+
- Phone/tablet and console on the same Wi-Fi network

## Features

- **Full game support** — trivia, linking, sorting, category selection, and power plays
- **Power-play effects** — freeze, bombs, munchers, gloop, and Double Trouble combinations
- **Player profiles** — name entry, camera selfie, and avatar selection
- **Auto-discovery** — finds your console on the local network automatically
- **Mid-game reconnect** — rejoin a game in progress

## Getting Started

[**Download the latest APK**](https://github.com/synchrone/reverse-of-power/releases/latest/download/app-release.apk) or see all versions on the [Releases](../../releases) page. You can also build from source with `./gradlew assembleDebug`.

1. Install the APK on your Android device.
2. Start the game on your PS4 or PS5.
3. Open Reverse of Power on the same Wi-Fi network — it discovers the console automatically and connects.

## Known Limitations

The original companion app shipped proprietary assets that cannot be redistributed, and this project deliberately ships none of them. The following are missing but don't prevent gameplay:

- **Photo overlay masks** — the frames and costumes overlaid on player selfies are not bundled
- **Sound effects** — the app is silent; all game audio still plays through the TV

Some less common power-play types may not have full visual effects yet. The game handles this gracefully — unrecognized power plays show a generic icon and the round continues normally.

## Contributing

Contributions are welcome — bug fixes, new features, or protocol documentation. Feel free to open an issue or pull request. Protocol notes and network captures live in the `docs/` directory.

## License

GPLv3 — see [LICENSE](LICENSE) for details.

## Disclaimer

Reverse of Power is an independent, fan-made project with **no affiliation with, sponsorship by, or endorsement from Sony Interactive Entertainment or Wish Studios**. "Knowledge is Power" and "Knowledge is Power: Decades" are trademarks of Sony Interactive Entertainment, used here only to identify the games this app is compatible with; all trademarks are the property of their respective owners. The project bundles none of the games' assets and exists solely to keep a purchased game playable for the small community of fans who still love it.
