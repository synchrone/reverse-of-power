# Knowledge Is Power — Community Controller App

A community-built Android controller app for **Knowledge Is Power**, the beloved PlayStation party quiz game. This project provides a free, open-source replacement for the original companion app, keeping the game playable for years to come.

Knowledge Is Power brought families and friends together around the TV for hilarious quiz nights — picking categories, sabotaging each other with power plays, and racing to answer first. With the official app no longer maintained, this project ensures the fun lives on.

## Features

- **Full game support** — trivia, linking, sorting, category selection, and power plays
- **All power play effects** — freeze, bombles, nibblers, gloop, and Double Trouble combinations
- **Player profiles** — name entry, camera selfie, and avatar selection
- **Auto-discovery** — finds your PlayStation on the local network automatically
- **Mid-game reconnect** — rejoin a game in progress
- **Works with both editions** — original and Decades (WIP)
- **PS4 and PS5 compatible**

## Getting Started

- Android 8.0+
- PS4 or PS5 with Knowledge Is Power installed
- Both devices on the same WiFi network

Download the latest APK from the [Releases](../../releases) page, or build from source with `./gradlew assembleDebug`.

## Known Limitations

The original app bundled proprietary assets that cannot be redistributed. The following are missing but don't prevent gameplay:

- **Photo overlay masks** — the fun frames and costumes overlaid on player selfies are not available
- **Sound effects** — the app is silent; all game audio still plays through the TV

Some less common power play types may not have full visual effects yet. The game handles this gracefully — unrecognized power plays are displayed with a generic icon and the round continues normally.

## Contributing

Contributions are welcome! Whether it's bug fixes, new features, or protocol documentation, feel free to open an issue or pull request.

Protocol documentation and network captures are available in the `docs/` directory.

## License

GPLv3 — see [LICENSE](LICENSE) for details.

## Acknowledgments

This is a fan project with no affiliation to Sony Interactive Entertainment or Wish Studios. Knowledge Is Power is a trademark of Sony Interactive Entertainment. This project exists solely to preserve a wonderful party game for the community.
