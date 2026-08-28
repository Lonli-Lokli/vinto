# kmp-game-client

## ADDED Requirements

### Requirement: GameSession abstraction with local and remote implementations

`shared/client` SHALL define `GameSession` (`localPlayerId`, `send(action)`,
`events: Flow<SessionEvent>`, `view: StateFlow<PlayerView>`, `visualView:
StateFlow<PlayerView>`, `syncVisualState()`) and SHALL provide `LocalGameSession`
(in-process engine + recorder + bots, full `GameState` held privately) and
`RemoteGameSession` (server transport). `send` is the only mutation path; a rejected
action leaves views unchanged and surfaces the reason.

#### Scenario: Local dispatch updates logical view only

- **WHEN** a valid action is sent to a `LocalGameSession`
- **THEN** `view` emits the new projected view, and `visualView` is unchanged until `syncVisualState()` is called

#### Scenario: Rejected action

- **WHEN** an invalid action is sent
- **THEN** neither view emits and an error callback receives the validator reason

#### Scenario: Same UI for both sessions

- **WHEN** the game screen is bound to a `LocalGameSession` or a `RemoteGameSession`
- **THEN** it behaves identically apart from latency; no UI code branches on the session type

### Requirement: Player views hide other seats' cards

`LocalGameSession` SHALL expose the human seat's state through the shared
`projectView(state, playerId)` projection so that single-player and online play render
from the same `PlayerView` type; the full `GameState` SHALL not be observable by the UI.

#### Scenario: Human never sees bot hands

- **WHEN** the human's `view` is inspected during play
- **THEN** bot cards appear face-down unless the human has knowledge of them (peeks, revealed cards, coalition-leader visibility)

### Requirement: Bot adapter driven by visual state

`BotAIAdapter` SHALL react to `visualState` (same snapshot fields as the TypeScript
adapter: current player, sub-phase, turn number, active toss-in, Vinto caller, coalition
leader, phase, difficulty), process reactions strictly sequentially, use
injectable delays, drive all bot phases (turn start, choosing, selecting, action targets,
toss-in participation, Vinto call, coalition leader auto-selection) and route final-round
coalition decisions through the coalition planner.

#### Scenario: Ported adapter integration tests pass

- **WHEN** the TypeScript `bot-tossin` and `coalition-final-round` integration tests are ported to `runTest` with virtual time
- **THEN** they pass unchanged in intent (same dispatch sequences and outcomes)

#### Scenario: Never acts mid-animation

- **WHEN** logical state advances but the animation layer has not synced visual state
- **THEN** no bot action is dispatched until the sync

### Requirement: Recording and seeded initialisation

The client SHALL initialise games with the seeded algorithm from `RECORDING.md`
(same deck order, shuffle, deal, `gameId` derivation) and SHALL record every accepted
action into a `GameRecording` v1 that can be exported and auto-saved.

#### Scenario: Same seed as TypeScript

- **WHEN** a Kotlin game is initialised with settings and seed identical to a TypeScript game
- **THEN** the initial `GameState` canonical hashes are equal

#### Scenario: Export

- **WHEN** the player exports mid-game or after scoring
- **THEN** a v1 recording is produced that replays in both engines

### Requirement: Dependency injection and configuration

The client SHALL be assembled with Koin modules (bot factory by difficulty/version, client,
recorder, persistence) so that platform apps and tests can substitute dispatchers, delays,
storage and clocks.

#### Scenario: Test assembly

- **WHEN** tests construct the client with a test dispatcher and zero delays
- **THEN** a full bot-only game runs to `scoring` deterministically under virtual time
