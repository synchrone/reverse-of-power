//● Here's a summary of the protocol differences between KIP and KIP: Decades (excluding ACK/NACK/TimeSync):
//
//Connection
//
//┌──────────────────┬─────────────────────┬────────────────────────────────────────────────────┐
//│                  │         KIP         │                      Decades                       │
//├──────────────────┼─────────────────────┼────────────────────────────────────────────────────┤
//│ InterfaceVersion │ KIP_2016-11-14-1222 │ KIP2_2018-06-08-1531                               │
//├──────────────────┼─────────────────────┼────────────────────────────────────────────────────┤
//│ DeviceUID magic  │ 0x0C 0x89 0xE8 0x84 │ 0xAF 0xE4 0x87 0x3D (currently decoded as Unknown) │
//├──────────────────┼─────────────────────┼────────────────────────────────────────────────────┤
//│ DeviceUID flags  │ 0x61 0x03 0xF4 0x63 │ 0x82 0xED 0x6C 0x47                                │
//└──────────────────┴─────────────────────┴────────────────────────────────────────────────────┘
//
//New Message Types in Decades
//
//┌────────────────────────────────────────────┬─────────────────────────────────────────────────────┐
//│                  Message                   │                       Purpose                       │
//├────────────────────────────────────────────┼─────────────────────────────────────────────────────┤
//│ ServerRoomMessage                          │ Sent before each round with Room + RoundType        │
//├────────────────────────────────────────────┼─────────────────────────────────────────────────────┤
//│ ServerAwaitTriviaAnsweringPhaseMessage     │ Pre-trivia notification with power play info        │
//├────────────────────────────────────────────┼─────────────────────────────────────────────────────┤
//│ ServerBeginMissingLetterAnsweringPhase     │ New minigame replacing Sorting                      │
//├────────────────────────────────────────────┼─────────────────────────────────────────────────────┤
//│ PrototypeClientToServerMissingLetterAnswer │ Response with ClientMissingLetterCorrectAnswerCount │
//├────────────────────────────────────────────┼─────────────────────────────────────────────────────┤
//│ ClientToServerOngoingChallengeMessage      │ Real-time progress during Missing Letter & Linking  │
//├────────────────────────────────────────────┼─────────────────────────────────────────────────────┤
//│ StartGameButtonPressedResponseMessage      │ Sent at game start (Response=9)                     │
//└────────────────────────────────────────────┴─────────────────────────────────────────────────────┘
//
//Removed from Decades
//
//- ServerColourMessage — color tinting removed (fields null in shared messages)
//- Sorting minigame (ServerBeginSortingAnsweringPhase / ClientSortingAnswer)
//- Player lifecycle (PlayerJoinedMessage, PlayerLeftMessage, PlayerNameQuizStateMessage)
//- End-of-game facts (ServerRequestEndOfGameFactCount, ClientEndOfGameFactCount, ClientEndOfGameFactCommandMessage)
//
//Field Changes in Shared Messages
//
//- ServerBeginTriviaAnsweringPhase: color fields are null, adds serverTick
//- ServerBeginPowerPlayPhase: PowerType=-1 sentinel, new PowerTypes=[int] array field
//- ServerCategorySelectChoices: colors null, adds HeaderText (e.g. "The 80s")
//- ClientHoldingScreenCommandMessage: adds serverTick
//
//New Power Play Types
//
//Decades adds types 20, 22, 24, 26, 28, 29 and drops Nibblers (6). Uses PowerTypes array instead of single PowerType.
//
//Other Differences
//
//- Avatars: 8 new (HIP_HOP, ELECTRO, ROCKER, etc.), 4 KIP ones appear as Available=false
//- RoundType 6 replaces KIP's 1; RoundType 8 = Missing Letter
//- Question IDs: TR2/LST/LSF/CMP prefixes instead of TRV/SRT
//- Resource flow: Decades uses Rqrmnt=1 + explicit RequestResourceMessage; KIP uses Rqrmnt=2
//- Game flow: Decades adds ServerRoomMessage → ServerAwaitTriviaAnsweringPhase before each trivia round; KIP uses ServerColourMessage →
//direct trivia

