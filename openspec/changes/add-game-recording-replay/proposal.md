# Change: Record full games to JSON and replay them deterministically

## Why

We are about to re-implement the engine, bot and client in Kotlin (see change
`migrate-to-kotlin-multiplatform`). The only trustworthy way to prove the Kotlin engine
behaves _exactly_ like the TypeScript engine is to replay real games through both and
compare the resulting states after every action.

Today that is impossible:

- The engine is not fully deterministic: `shuffleCards` uses `crypto.getRandomValues`
  (initial deal and the mid-game reshuffle in `advanceTurnAfterTossIn`), two engine
  handlers mint ids with `Date.now()`, and the client stamps `Date.now()` into
  `turnActions`/`roundActions` inside `GameState`.
- `GameClient` keeps an in-memory `_actionHistory`, but there is no persisted, versioned
  format that captures the initial state, the seed and every accepted action, and no
  headless replay that reproduces a game from such a file.

## What Changes

- **Deterministic engine**: a seeded PRNG stored in `GameState.rngState` replaces all
  ambient randomness in the engine; engine-generated ids and history entries are derived
  from state, never from clocks. Nothing in the reducer path may call `Date.now()`,
  `Math.random()`, `crypto.*` or `uuid`.
- **GameRecording format (v1)**: a versioned JSON document containing settings, seed,
  the full initial `GameState`, every accepted `GameAction` in order, the final state and
  optional per-action canonical state hashes.
- **Recording in the client**: `GameClient` records every _accepted_ action; the game can
  be exported as JSON at any time (debug panel + settings), the last game is auto-saved
  locally, and a rejected action is never recorded.
- **Replay**: a pure `replayRecording()` in `@vinto/engine` that reproduces a game from a
  recording, verifies hashes/final state and reports the first divergence; a Node CLI
  (`tools/replay-recording.ts`) and a fixture generator (`tools/generate-recordings.ts`)
  that plays seeded headless bot-vs-bot games and commits them under
  `fixtures/recordings/`.
- **Parity harness (TS side)**: a Vitest suite that replays every committed fixture and
  fails on any divergence — this is the same harness the Kotlin engine will run against.
- **Optional (should)**: an in-app replay viewer that steps through a recording.

## Impact

- Affected specs: `deterministic-engine` (new), `game-recording` (new), `game-replay` (new)
- Affected code:
  - `packages/shapes`: `GameState.rngState`, `Prng` utility, canonical JSON + hash helpers,
    `GameRecording` types
  - `packages/engine`: `toss-in-utils.ts` (reshuffle via PRNG, id), `player-toss-in-finished.ts`
    (id), `replay.ts` (new)
  - `packages/local-client`: `initializeGame.ts` (seeded deal), `game-client.ts` (recorder,
    deterministic history), `GameRecorder` (new), export/auto-save
  - `apps/vinto`: export button (debug panel/settings), optional replay viewer
  - `tools/`: replay CLI, fixture generator; `fixtures/recordings/` (new, committed)
- **BREAKING (internal)**: `shuffleCards(deck)` becomes `shuffleCards(deck, prng)`;
  `createInitialGameState` gains `rngState`. Saved games from before this change cannot be
  replayed (they were never reproducible anyway).
