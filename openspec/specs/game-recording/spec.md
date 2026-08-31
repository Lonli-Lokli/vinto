<!--
  Canonical spec, synced from `add-game-recording-replay` when it was archived.

  The change closed with four tasks marked retired rather than done: they were browser-side
  work in the Next.js client, which is being deleted (docs/kotlin/README.md §1d). The
  requirements below are not retired — they are held by the Kotlin engine and by the frozen
  corpus in `fixtures/`, which CI replays on the JVM, on Kotlin/JS and inside workerd.

  Amended when `retire-legacy-web` archived (16/16). "Recording fixtures are generated
  headlessly and committed" was **replaced**, not merely edited: it required
  `tools/generate-recordings.ts`, which was deleted with the TypeScript engine, and requiring a
  file that cannot exist is a spec nobody can satisfy. What replaced it says the opposite and
  says it deliberately — the corpus is frozen, new coverage goes somewhere else, and a
  requirement that the two never be confused. The reasoning is `retire-legacy-web/design.md` D2.
-->
# game-recording

## Requirements

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

### Requirement: The recording corpus is a frozen artefact

`fixtures/recordings` SHALL NOT be regenerated. It holds fifty games produced by the
TypeScript engine before that engine was retired, and its value is that it was written by an
implementation which had never read the Kotlin one.

#### Scenario: New engine coverage is wanted

- **WHEN** a rule change or a new action needs recorded coverage
- **THEN** the recording SHALL be generated by the Kotlin engine into a separate directory
  which states which engine produced it
- **AND** `fixtures/recordings` SHALL be left unchanged

#### Scenario: A recording in the frozen corpus is modified

- **WHEN** any file under `fixtures/recordings` differs from its committed hash
- **THEN** the test suite SHALL fail, naming the file

### Requirement: Parity claims are scoped to what still runs

Documentation SHALL NOT claim that two implementations agree *today*. It MAY state that the
Kotlin engine reproduces what the TypeScript engine computed when the corpus was recorded,
which is what the replay gate checks.
