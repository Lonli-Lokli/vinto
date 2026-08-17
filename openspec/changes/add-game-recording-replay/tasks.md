# Tasks: add-game-recording-replay

## 1. Deterministic engine

- [ ] 1.1 Add `Prng` (mulberry32: `next`, `nextInt(bound)`, `shuffle`) to `@vinto/shapes` with unit tests and published test vectors (seed → first 10 outputs, seed → shuffled 54-card order) that the Kotlin port will reuse
- [ ] 1.2 Add `rngState: number` to `GameState`; `createInitialGameState` takes it
- [ ] 1.3 Change `shuffleCards(deck, rngState)` to return `{ deck, rngState }`; update `Pile.reshuffleFrom` to accept/return generator state
- [ ] 1.4 `advanceTurnAfterTossIn`: reshuffle via `state.rngState`, store the advanced state
- [ ] 1.5 Replace `Date.now()` ids in `player-toss-in-finished.ts` and `toss-in-utils.ts` with state-derived ids
- [ ] 1.6 `GameClient.addActionToHistory`: `timestamp` = accepted-action index (client keeps a counter); update `GameActionHistory` doc comment
- [ ] 1.7 `initializeGame`: `seed?` in `GameSettings`, fixed-order deck, seeded shuffle, deterministic `gameId`, seed generated client-side when absent
- [ ] 1.8 Add engine test that greps `packages/engine/src` (excluding tests) for `Date.`, `Math.random`, `crypto`, `uuid`, `performance.now` and fails on a hit
- [ ] 1.9 Update all existing tests/helpers for the new signatures (`createTestState` supplies `rngState`)

## 2. Recording format and canonical hashing

- [ ] 2.1 `GameRecording` v1 types in `@vinto/shapes` (+ `assertRecordingVersion`)
- [ ] 2.2 `canonicalizeGameState` + `hashGameState` (node: `node:crypto`; browser: `crypto.subtle` async variant) with tests: key-order independence, sensitivity, `botMemory` exclusion, integer-only assertion
- [ ] 2.3 `GameRecorder` in `@vinto/local-client`: constructed with settings + initial state, `record(action)`, `toRecording(finalState)`, `toJSON()`
- [ ] 2.4 Wire recorder into `GameClient.dispatch` (accepted actions only); `exportRecording()`; tests: accepted vs rejected, bot+human interleaving
- [ ] 2.5 Auto-save (debounced) to `localStorage` under a versioned key; clear on new game; test with a storage stub

## 3. Replay

- [ ] 3.1 `replayRecording()` in `@vinto/engine` (`replay.ts`) with divergence reporting; unit tests for faithful replay, hash mismatch, rejected action, unknown version
- [ ] 3.2 `tools/replay-recording.ts` CLI (file or directory, PASS/FAIL lines, divergence report file, non-zero exit)
- [ ] 3.3 `tools/generate-recordings.ts` (seeded headless self-play via `BotAIAdapter` with `skipDelays`, always 4 players, hashes filled in)
- [ ] 3.4 Export scenario-test action sequences as `fixtures/recordings/scenario-*.json`
- [ ] 3.5 Commit initial corpus: ≥ 50 self-play games, all scenarios, at least one game with a mid-game reshuffle and one with a coalition final round
- [ ] 3.6 Vitest parity suite in `packages/engine` replaying every fixture; add to `nx test engine`

## 4. UI

- [ ] 4.1 "Export game (JSON)" in the debug panel and in settings/menu (download file)
- [ ] 4.2 (should) Debug-only replay viewer: load JSON, step next/prev, render state through the normal UI with bots disabled
- [ ] 4.3 Docs: `docs/game-engine/RECORDING.md` describing the format, canonicalisation, hashing, PRNG and test vectors (this document is the contract for the Kotlin port)

## 5. Verification

- [ ] 5.1 All package test suites green; lint clean; `nx build @vinto/game` succeeds
- [ ] 5.2 Manually: play a game in the browser, export, replay with the CLI → PASS; reload mid-game → auto-saved recording exports and replays
