# game-replay

## ADDED Requirements

### Requirement: Pure replay reproduces a recording

`@vinto/engine` SHALL export `replayRecording(recording, options?)` which starts from
`recording.initialState`, applies each recorded action with `GameEngine.reduce`, and
returns `{ ok, finalState, steps }`. When per-action hashes are present it SHALL compare
the canonical hash after each step; it SHALL always compare the final state to
`recording.finalState` by hash. Replay SHALL never invoke bot logic or any client code.

#### Scenario: Faithful replay

- **WHEN** a recording produced by the client is replayed
- **THEN** `ok === true`, every intermediate hash matches and `hashGameState(finalState) === hashGameState(recording.finalState)`

#### Scenario: Divergence is reported precisely

- **WHEN** an intermediate state hash does not match the recorded hash
- **THEN** replay stops and returns `divergence = { index, action, expectedHash, actualHash, stateBefore, stateAfter }`

#### Scenario: Rejected action during replay

- **WHEN** the engine rejects a recorded action (rule mismatch between recorder and replayer)
- **THEN** replay stops with `ok === false` and a divergence naming the action index and the rejection reason

### Requirement: Replay CLI

The repository SHALL provide `tools/replay-recording.ts <file|dir>` which replays one
recording or every recording in a directory, prints a one-line PASS/FAIL per file, dumps
the first divergence (both states, pretty-printed) to a report file on failure, and exits
non-zero if any file fails.

#### Scenario: Directory run

- **WHEN** the CLI is pointed at `fixtures/recordings/`
- **THEN** it replays every `*.json` file and exits 0 only if all pass

### Requirement: Parity test suite runs in CI

The engine test suite SHALL include a Vitest test that replays every file under
`fixtures/recordings/` and fails on any divergence, so that any engine change that alters
behaviour is caught against the committed corpus (or requires regenerating fixtures with a
justification).

#### Scenario: Behavioural regression detected

- **WHEN** an engine handler is changed such that a recorded action now produces a different state
- **THEN** the parity test fails and names the fixture and action index

### Requirement: Recordings are implementation-agnostic

The recording format SHALL carry no TypeScript-specific structure: any implementation
that can parse `GameState` and `GameAction` JSON SHALL be able to replay a recording and
compute identical canonical hashes. Recordings written by a different implementation
(e.g. the Kotlin engine) SHALL replay in the TypeScript engine under the same rules.

#### Scenario: Foreign recording replays

- **WHEN** a valid v1 recording whose `meta.producer` is not the TypeScript client is replayed
- **THEN** replay proceeds exactly as for a native recording (no producer-specific branches)

### Requirement: Replay viewer (debug-only)

The web app SHALL offer a debug-only replay viewer (behind the existing debug panel; not
part of the player-facing UI) that loads a recording JSON and steps forward/backward
through actions, rendering the state at each step through the normal game UI without
running bots. This requirement is the lowest priority of the change and MAY be delivered
last.

#### Scenario: Step through a loaded recording

- **WHEN** a recording is loaded in the viewer and "next" is pressed
- **THEN** the displayed state equals the replayed state after the next action
