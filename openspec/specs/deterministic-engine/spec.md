<!--
  Canonical spec, synced from `add-game-recording-replay` when it was archived.

  The change closed with four tasks marked retired rather than done: they were browser-side
  work in the Next.js client, which is being deleted (docs/kotlin/README.md §1d). The
  requirements below are not retired — they are held by the Kotlin engine and by the frozen
  corpus in `fixtures/`, which CI replays on the JVM, on Kotlin/JS and inside workerd.
-->
# deterministic-engine

## Requirements

### Requirement: Engine randomness comes only from state

The game engine SHALL obtain all randomness from a seeded pseudo-random generator whose
state is stored in `GameState.rngState` (unsigned 32-bit integer), using the mulberry32
algorithm with `nextInt(bound) = value mod bound` and Fisher–Yates shuffling as specified
in the change design. Any handler that consumes randomness SHALL write the advanced
generator state back into the returned `GameState`.

#### Scenario: Draw pile reshuffle is reproducible

- **WHEN** the draw pile is down to one card and the turn advances, causing the discard pile to be reshuffled into the draw pile
- **THEN** the resulting draw-pile order is a pure function of the previous `GameState` (including `rngState`) and the returned state carries the advanced `rngState`

#### Scenario: Two reductions of the same state and action are identical

- **WHEN** `GameEngine.reduce(state, action)` is invoked twice with structurally equal inputs
- **THEN** both results are structurally equal, including `rngState`, ids and history entries

### Requirement: No ambient clocks, randomness or uuids in the reducer path

The engine (validator, reducers, engine utilities) SHALL NOT call `Date.*`,
`performance.now`, `Math.random`, `crypto.*` or any uuid generator. Identifiers minted by
the engine SHALL be derived from state (e.g. turn number, player id, rank, index).

#### Scenario: Queued toss-in action ids are deterministic

- **WHEN** a toss-in queued action card is materialised for a player
- **THEN** its `id` is derived from `turnNumber`, the player id, the rank and the queue index, and does not depend on wall-clock time

#### Scenario: Static guard

- **WHEN** the engine test suite runs
- **THEN** a test scans the engine sources and fails if any forbidden non-deterministic API is referenced

### Requirement: Deterministic action history inside GameState

`GameActionHistory` entries stored in `GameState` (`turnActions`, `roundActions`) SHALL
use a deterministic `timestamp` equal to the zero-based index of the accepted action that
produced them, not wall-clock time.

History entries are excluded from the canonical state hash (see the `game-recording`
capability), so this requirement exists for stable, diff-friendly exported recordings and
readable debugging — not for cross-implementation parity. A second implementation is not
required to reproduce history entries byte-for-byte.

#### Scenario: History entry sequence numbers

- **WHEN** the client applies the 12th accepted action of a game
- **THEN** any history entry created for it has `timestamp === 11`

#### Scenario: Exported recordings are stable across runs

- **WHEN** the same seeded game is played twice and exported
- **THEN** the `initialState`, `actions` and `finalState` of the two recordings are identical, and contain no wall-clock or uuid values
- **AND** only `meta` differs, which is informational: `meta.recordedAt` is a real timestamp and is excluded from every hash

### Requirement: Games always have exactly four players

Every game SHALL have exactly 4 players (any mix of humans and bots). Game initialisation
SHALL not accept a player count, and the engine SHALL reject an initial state with a
different number of players. Existing UI/settings for player count SHALL be removed.

#### Scenario: Four players enforced

- **WHEN** a game is initialised
- **THEN** `players.length === 4` and no player-count option exists in settings or the recording format

#### Scenario: Wrong count rejected

- **WHEN** a recording whose `initialState.players` has 3 or 5 entries is replayed
- **THEN** replay refuses to start and reports an invalid player count

### Requirement: Seeded game initialisation

Game initialisation SHALL accept an optional unsigned 32-bit `seed`; the deck SHALL be
created in a fixed order and shuffled with the seeded generator; `gameId` SHALL be derived
from the seed and settings; the initial `GameState` SHALL contain the post-shuffle
`rngState`.

#### Scenario: Same seed, same deal

- **WHEN** two games are initialised with identical settings and seed
- **THEN** their initial `GameState`s are structurally equal (deck order, hands, ids, `rngState`)

#### Scenario: Seed omitted

- **WHEN** a game is initialised without a seed
- **THEN** the client generates one outside the engine and the resulting seed is available to the recorder

### Requirement: `shuffleCards` takes an explicit generator

`shuffleCards` SHALL take the deck and a generator state and return both the shuffled
deck and the advanced generator state; the previous ambient-random signature is removed.

#### Scenario: Pile reshuffle uses the injected generator

- **WHEN** `Pile.reshuffleFrom` is invoked by the engine
- **THEN** it is given the generator state from `GameState.rngState` and returns the advanced state for the engine to store
