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



// ODD one out:
Unknown TypeString: KnowledgeIsPower.ServerBeginEliminatingAnsweringPhase in {"TypeString":"KnowledgeIsPower.ServerBeginEliminatingAnsweringPhase","ChallengeId":"ELM00028","QuestionText":"Smash the movie that ISN’T science fiction","QuestionDuration":45.0,"EliminatingAnswerData":[{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo1]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo1]","CorrectAnswer":"Dumb & Dumber","IncorrectAnswers":["Moon","Jurassic Park"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo2]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo2]","CorrectAnswer":"Love Actually","IncorrectAnswers":["District 9","RoboCop"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo3]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo3]","CorrectAnswer":"Kill Bill: Volume 1","IncorrectAnswers":["Avatar","Planet Of The Apes"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo4]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo4]","CorrectAnswer":"Superbad","IncorrectAnswers":["Interstellar","Donnie Darko"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo5]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo5]","CorrectAnswer":"Bridesmaids","IncorrectAnswers":["Blade Runner","War Of The Worlds"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo6]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo6]","CorrectAnswer":"Step Brothers","IncorrectAnswers":["Minority Report","Predator"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo7]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo7]","CorrectAnswer":"The Silence Of The Lambs","IncorrectAnswers":["Looper","Sunshine"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo8]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo8]","CorrectAnswer":"Knocked Up","IncorrectAnswers":["The Martian","Prometheus"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo9]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo9]","CorrectAnswer":"When Harry Met Sally","IncorrectAnswers":["Alien","Star Trek"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo10]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo10]","CorrectAnswer":"My Best Friend’s Wedding","IncorrectAnswers":["The Matrix","Guardians Of The Galaxy"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo11]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo11]","CorrectAnswer":"Memento","IncorrectAnswers":["Inception","Edge Of Tomorrow"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo12]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo12]","CorrectAnswer":"Notting Hill","IncorrectAnswers":["E.T. The Extra-Terrestrial","Ready Player One"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo13]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo13]","CorrectAnswer":"The Heat","IncorrectAnswers":["Ex Machina","Back To The Future"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo14]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo14]","CorrectAnswer":"Bridget Jones’s Diary","IncorrectAnswers":["Men In Black","The Empire Strikes Back"]},{"CorrectInfo":"[MISSING:ELM00028_CorrectInfo15]","IncorrectInfo":"[MISSING:ELM00028_IncorrectInfo15]","CorrectAnswer":"Love, Simon","IncorrectAnswers":["Metropolis","Aliens"]}],"serverTick":77014217840}