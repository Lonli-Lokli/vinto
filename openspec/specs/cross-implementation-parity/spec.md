<!--
  Canonical spec, synced from `migrate-to-kotlin-multiplatform` when it was archived.

  What the frozen corpus still proves, and what it stopped proving when the TypeScript engine
  was deleted. Read it with `game-recording`'s frozen-corpus requirement beside it.

  Deltas land here on archive, not before — a requirement in a change folder is a
  proposal, and a requirement here is what the game is held to.
-->
# cross-implementation-parity

## Requirements

### Requirement: Kotlin engine replays the TypeScript corpus identically

The `parity-tests` module SHALL replay every recording under `fixtures/recordings/` with
the Kotlin engine and SHALL fail if any per-action canonical state hash or the final
state hash differs from the recorded value. This suite SHALL run on JVM in CI for every
change and additionally on an Android emulator and an iOS simulator before a release.

#### Scenario: Corpus green on JVM

- **WHEN** the CI `kmp-jvm` job runs
- **THEN** every fixture replays with `ok === true`

#### Scenario: Corpus green on device targets

- **WHEN** the release pipeline runs the parity suite on Android emulator and iOS simulator
- **THEN** every fixture replays with `ok === true` on both

#### Scenario: Divergence blocks merge

- **WHEN** any fixture diverges
- **THEN** the job fails, prints the divergence report (fixture, action index, both hashes, both states) and the change cannot be merged

### Requirement: Kotlin recordings replay in the TypeScript engine

The Kotlin stack SHALL record games in `GameRecording` format v1 (including per-action
hashes) and CI SHALL replay a fresh set of Kotlin self-play recordings with
`tools/replay-recording.ts`; any divergence fails the pipeline.

#### Scenario: Round trip

- **WHEN** the `parity-roundtrip` job generates 20 seeded Kotlin self-play recordings and replays them in TypeScript
- **THEN** all 20 pass

#### Scenario: Human game exported from the mobile app

- **WHEN** a player exports a game from the Kotlin app and it is replayed with the TypeScript CLI
- **THEN** it replays without divergence

### Requirement: Server engine is covered by the same gate

The online server SHALL depend on the same `shared/engine` artifact as the app (no fork),
and server-side recordings of online games SHALL replay in both engines; the
`parity-roundtrip` job SHALL include recordings produced by an automated multi-client
harness against a locally started server.

#### Scenario: Online game recording round trip

- **WHEN** the harness plays a 4-seat game with two scripted human clients and two bots against the server
- **THEN** the server's recording replays without divergence in the Kotlin and TypeScript engines

### Requirement: Fixture governance

Regenerating or altering committed fixtures SHALL require a rules justification in the PR
description and SHALL update both implementations in the same PR so the corpus never
encodes behaviour only one engine has.

#### Scenario: Rules change

- **WHEN** a game rule is deliberately changed
- **THEN** the PR updates the TypeScript engine, the Kotlin engine, the affected fixtures and `VINTO_RULES.md`/`SCENARIOS.md` together, and both parity jobs are green

### Requirement: Bot strength parity (statistical)

The Kotlin bot SHALL be validated by seeded self-play tournaments (≥ 500 games per
difficulty, 4-player tables) comparing win rates and coalition win rates with
the TypeScript bot; a drop greater than 5 percentage points SHALL block the release.

#### Scenario: Coalition strength preserved

- **WHEN** the tournament measures how often the coalition beats a Vinto caller in positions where a winning line exists (using the coalition-planner scenario corpus)
- **THEN** the Kotlin rate is at least the TypeScript rate minus 5 pp
