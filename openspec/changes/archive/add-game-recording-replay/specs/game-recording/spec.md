# game-recording

## ADDED Requirements

### Requirement: GameRecording JSON format v1

The system SHALL define a versioned `GameRecording` JSON document with: `formatVersion`
(integer, currently 1), `meta` (`recordedAt` ISO string, `producer` implementation tag,
optional `label`), `settings` (bot count, human name, difficulty, `seed`; games are
always 4 players), `initialState` (full `GameState` after dealing), `actions` (ordered list
of `{ action: GameAction, stateHash?: string }`), `finalState` (full `GameState` at export
time) and optional `finalStateHash`. Readers SHALL reject unknown `formatVersion`s with a
descriptive error.

#### Scenario: Round-trip serialisation

- **WHEN** a recording is serialised to JSON and parsed back
- **THEN** the parsed object is structurally equal to the original, including `Pile`s (arrays, top card first) and `rngState`

#### Scenario: Unknown version rejected

- **WHEN** a reader is given a document with `formatVersion: 99`
- **THEN** it throws/returns an error naming the unsupported version and does not attempt replay

### Requirement: Canonical state serialisation and hashing

The system SHALL provide `canonicalizeGameState(state): string` (keys sorted
lexicographically at every level, arrays in order, `Pile` as array top-first, `undefined`
omitted, `null` kept, no whitespace) and `hashGameState(state): string` (lowercase hex
SHA-256 of the UTF-8 canonical string). `GameState` SHALL contain integers only where
numbers appear.

The canonical form SHALL exclude exactly three fields, which are presentation or
bot-internal rather than game logic: `PlayerState.botMemory` (bot-internal, contains
floats, never written by the engine), `GameState.turnActions` and `GameState.roundActions`
(client-authored history whose `description` strings are user-facing prose). Every other
field of `GameState` SHALL be included. The exclusion list is part of the format contract
and SHALL be documented in `docs/game-engine/RECORDING.md`.

#### Scenario: Canonical form is order-independent

- **WHEN** two `GameState` objects differ only in property insertion order
- **THEN** their canonical strings and hashes are identical

#### Scenario: Any game-logic difference changes the hash

- **WHEN** a single card id, `rngState`, `knownCardPositions` or `opponentKnowledge` entry differs
- **THEN** the hashes differ

#### Scenario: History and bot memory do not affect the hash

- **WHEN** two `GameState` objects are equal except for their `turnActions`/`roundActions` entries (including `description` text and `timestamp`) or `PlayerState.botMemory`
- **THEN** their canonical strings and hashes are identical, so a second implementation is free to word action descriptions differently without breaking parity

### Requirement: The client records every accepted action

`GameClient` SHALL create a `GameRecorder` with the initial state and settings when a game
starts and SHALL append each action to the recording if and only if the engine accepted
it. Rejected actions SHALL NOT be recorded.

#### Scenario: Accepted action recorded

- **WHEN** a valid `DRAW_CARD` is dispatched
- **THEN** it becomes the next entry in the recording's `actions`

#### Scenario: Rejected action not recorded

- **WHEN** an invalid action (e.g. `SWAP_CARD` outside the `choosing` sub-phase) is dispatched
- **THEN** the recording is unchanged and the state is unchanged

#### Scenario: Bot and human actions recorded alike

- **WHEN** the `BotAIAdapter` dispatches actions for bots
- **THEN** they are recorded in dispatch order interleaved with human actions

### Requirement: Export a game to JSON at any time

The player SHALL be able to export the current game as a `GameRecording` JSON file from
the debug panel and from the settings/menu, both mid-game and after scoring. The export
SHALL include `finalState` as of the moment of export and MAY include per-action hashes.

#### Scenario: Export mid-game

- **WHEN** the player exports during the `playing` phase
- **THEN** a valid recording is produced whose `finalState.phase === 'playing'` and which replays to that exact state

#### Scenario: Export after scoring

- **WHEN** the game has reached the `scoring` phase
- **THEN** the exported recording's `finalState.phase === 'scoring'`

### Requirement: Last game is auto-saved locally

The client SHALL persist the in-progress recording to local storage (debounced) after
each accepted action under a versioned key, replacing the previous game when a new game
starts, so that a reproducible file survives a crash or reload.

#### Scenario: Reload preserves the recording

- **WHEN** the page is reloaded mid-game
- **THEN** the last auto-saved recording is available for export and replays to the last recorded state

### Requirement: Recording fixtures are generated headlessly and committed

The repository SHALL provide `tools/generate-recordings.ts` that plays N seeded headless
bot-only 4-player games with hashes filled in and writes them to
`fixtures/recordings/`, and the engine scenario tests SHALL export their action sequences
as recordings into the same directory. The initial fixture set SHALL contain at least 50
self-play games and every scenario recording.

#### Scenario: Generator output replays

- **WHEN** `generate-recordings --games 5 --seed 42` is run
- **THEN** five recordings are written and each replays without divergence

#### Scenario: Fixture set covers rule edge cases

- **WHEN** the fixture directory is inspected
- **THEN** it contains a recording for every scenario in `docs/game-engine/SCENARIOS.md` (toss-in success/failure, King declaration, Queen peek-and-swap, Vinto call, coalition final round, draw-pile reshuffle, penalty draws)
