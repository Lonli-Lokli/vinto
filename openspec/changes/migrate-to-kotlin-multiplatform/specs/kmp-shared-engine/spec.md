# kmp-shared-engine

## ADDED Requirements

### Requirement: Kotlin engine is a pure reducer with the TypeScript state model

The Kotlin engine (`shared/engine`) SHALL expose
`GameEngine.reduce(state: GameState, action: GameAction): ReduceResult` implemented as pure
functions over immutable `data class` state whose JSON representation is field-for-field
identical to the TypeScript `GameState` (`Pile` as array top-first, `Rank`/phase enums
with the TypeScript string values, `GameAction` as `{ "type", "payload" }` with the same
type names). The engine SHALL contain no platform APIs, clocks, or randomness other than
`GameState.rngState`.

#### Scenario: JSON interoperability

- **WHEN** a `GameState` or `GameAction` JSON produced by the TypeScript implementation is decoded in Kotlin and re-encoded
- **THEN** the canonical form of the result is byte-identical to the canonical form of the input

#### Scenario: Purity guard

- **WHEN** the Kotlin engine test suite runs
- **THEN** a test scanning `shared/engine` sources fails on references to `Clock`, `System.currentTimeMillis`, `Random`, `uuid` or `kotlinx.datetime` clocks

### Requirement: Four players, always

The Kotlin engine, client, server and UI SHALL support exactly 4-player games and nothing
else; there SHALL be no player-count parameter anywhere in the Kotlin stack.

#### Scenario: Only four seats

- **WHEN** a game or room is created
- **THEN** it has exactly 4 seats (humans + bots)

### Requirement: Rule-complete port

Every action type, validator rule and case handler in `packages/engine` SHALL have a
Kotlin counterpart with equivalent behaviour, including toss-in flow (multi-rank, queued
actions, penalties), King declaration, Queen peek-and-swap, Jack swap, Ace force-draw,
swap-with-declaration, Vinto/final round, coalition targeting restrictions, draw-pile
reshuffle and scoring.

#### Scenario: Ported handler tests pass

- **WHEN** the TypeScript engine unit/rules/scenario tests are ported to kotlin.test
- **THEN** all of them pass against the Kotlin engine

#### Scenario: Scoring parity

- **WHEN** a final-round state is scored
- **THEN** the Kotlin `calculateFinalScores` returns the same per-player results as the TypeScript implementation (coalition pooling included)

### Requirement: Identical seeded PRNG

The Kotlin `Prng` SHALL implement mulberry32 with `nextInt(bound) = value mod bound` and
Fisher–Yates shuffle exactly as `RECORDING.md` specifies and SHALL reproduce the published
test vectors.

#### Scenario: Test vectors

- **WHEN** the Kotlin `Prng` is seeded with each published seed
- **THEN** its first N outputs and the resulting 54-card shuffle order match the vectors from the TypeScript implementation

### Requirement: Kotlin replay API

`shared/engine` SHALL provide `replayRecording(recording)` with the same semantics as
the TypeScript function (per-action hash comparison, final-state comparison, precise
divergence report, no bot logic).

#### Scenario: Divergence localisation

- **WHEN** a Kotlin handler deviates from the TypeScript behaviour for some recorded action
- **THEN** replay reports the exact action index, the expected and actual hashes and both states
