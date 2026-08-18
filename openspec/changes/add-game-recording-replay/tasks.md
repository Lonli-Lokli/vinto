# Tasks: add-game-recording-replay

## 1. Deterministic engine

- [x] 1.1 Add `Prng` (mulberry32: `next`, `nextInt(bound)`, `shuffle`) to `@vinto/shapes` with unit tests and published test vectors committed to `fixtures/prng/vectors.json` (seed → first 10 outputs, seed → shuffled 54-card order); the Kotlin port reads this exact file, so it is a committed cross-language contract artifact, not test-local data
- [x] 1.2 Add `rngState: number` to `GameState`. `createInitialGameState` was deleted rather than threaded: it had zero callers, duplicated `initializeGame`'s state literal and contradicted it (`turnNumber: 0` vs `1`). `initializeGame` is now the single constructor; `rehydrateGameState` was added in its place in `@vinto/shapes` (restores `Pile` instances after `JSON.parse` — needed by state import today and by replay in phase 3)
- [x] 1.3 Change `shuffleCards(deck, rngState)` to return `{ deck, rngState }`; update `Pile.reshuffleFrom` to accept/return generator state
- [x] 1.4 `advanceTurnAfterTossIn`: reshuffle via `state.rngState`, store the advanced state
- [x] 1.5 Replace `Date.now()` ids in `player-toss-in-finished.ts` and `toss-in-utils.ts` with state-derived ids
- [x] 1.6 `GameClient.addActionToHistory`: `timestamp` = accepted-action index (client keeps a counter); update `GameActionHistory` doc comment
- [x] 1.7 `initializeGame`: `seed?` in `GameSettings`, fixed-order deck, seeded shuffle, deterministic `gameId`, seed generated client-side when absent
- [x] 1.7a Enforce the 4-player rule at initialisation: `quickStartGame` (`initializeGame.ts:222`) currently builds a **2-player** game, contradicting the `deterministic-engine` requirement — make it 4-player or delete it; drop `playerCount`/`botCount` from `GameSettings` and from every UI call site
- [x] 1.8 Add engine test that greps `packages/engine/src` (excluding tests) for `Date.`, `Math.random`, `crypto`, `uuid`, `performance.now` and fails on a hit
- [x] 1.9 Update all existing tests/helpers for the new signatures (`createTestState` supplies `rngState`)

## 2. Recording format and canonical hashing

- [x] 2.1 `GameRecording` v1 types in `@vinto/shapes` (+ `assertRecordingVersion`)
- [x] 2.2 `canonicalizeGameState` + `hashGameState` (node: `node:crypto`; browser: `crypto.subtle` async variant) with tests: key-order independence, sensitivity to game-logic fields (incl. `opponentKnowledge`), exclusion of `botMemory` + `turnActions` + `roundActions`, integer-only assertion
- [x] 2.3 `GameRecorder` in `@vinto/local-client`: constructed with settings + initial state, `record(action)`, `toRecording(finalState)`, `toJSON()`
- [x] 2.4 Wire recorder into `GameClient.dispatch` (accepted actions only); `exportRecording()`; tests: accepted vs rejected. Bot+human interleaving is covered structurally (bots dispatch through the same `GameClient.dispatch`) but is asserted end-to-end by the self-play fixture generator in task 3.3, not by a unit test
- [x] 2.5 Auto-save (debounced) to `localStorage` under a versioned key; clear on new game; test with a storage stub

## 3. Replay

- [x] 3.1 `replayRecording()` in `@vinto/engine` (`replay.ts`) with divergence reporting; unit tests for faithful replay, hash mismatch, rejected action, unknown version. Comparison is by canonical hash, not deep equality: replay reconstructs engine state only, and `turnActions`/`roundActions` are client-written, so a replayed state legitimately has no history
- [ ] 3.2 `tools/replay-recording.ts` CLI (file or directory, PASS/FAIL lines, divergence report file, non-zero exit)
- [ ] 3.3 `tools/generate-recordings.ts` (seeded headless self-play via `BotAIAdapter` with `skipDelays`, always 4 players, hashes filled in)
  - **3.2 and 3.3 are blocked on a TypeScript runner.** Nothing in the repo can execute a `tools/*.ts` script that imports `@vinto/*`: the packages' `exports` point at `.ts` source, and `ts-node` does not compile sources reached through `node_modules` workspace symlinks. Neither `tsx` nor `vite-node` is installed, and `npx tsc` is denied by project policy. This needs a devDependency decision (`vite-node` is the closest fit — same family as the vitest already in use) before either CLI can be written. The replay logic itself (3.1) is done and runner-independent, so both tools become thin wrappers once a runner exists
- [ ] 3.4 Export scenario-test action sequences as `fixtures/recordings/scenario-*.json`
- [ ] 3.5 Commit initial corpus: ≥ 50 self-play games, all scenarios, at least one game with a mid-game reshuffle and one with a coalition final round
  - **Prerequisite — do not commit the corpus before the state model is frozen.** Phase 1 of change `migrate-to-kotlin-multiplatform` (bot tournament → delete the losing bot → remove `botVersion`) mutates `GameState` (`packages/shapes/src/lib/game-state-types.ts:48`), so every canonical hash changes when it lands. Committing ≥ 50 fixtures first guarantees regenerating all of them and sets a bad precedent for fixture governance in the very first corpus commit. Tasks 3.1–3.4 and all of phase 2 may proceed in parallel with that work; only this commit blocks on it.
- [x] 3.6 Vitest parity suite in `packages/engine` replaying every fixture; add to `nx test engine`. Verified in both directions: it replays a real generated fixture green, and fails with a readable divergence report (action index, reason, both hashes) when a hash is corrupted. While the corpus is absent it skips with a stated reason rather than passing vacuously, and once the directory exists it asserts the corpus is non-empty and that every recorded action carries a state hash

## 4. UI

- [ ] 4.1 "Export game (JSON)" in the debug panel and in settings/menu (download file)
- [ ] 4.2 (should) Debug-only replay viewer: load JSON, step next/prev, render state through the normal UI with bots disabled
- [ ] 4.3 Docs: `docs/game-engine/RECORDING.md` describing the format, canonicalisation, hashing, PRNG and test vectors (this document is the contract for the Kotlin port)

## 5. Verification

- [ ] 5.1 All package test suites green; lint clean; `nx build @vinto/game` succeeds
  - **Known pre-existing blocker (not caused by this change):** `nx build @vinto/game` fails at HEAD with `<Html> should not be imported outside of pages/_document` while statically exporting `/404` through the auto-generated pages-router `_error`. Reproduces with plain `next build` in `apps/vinto`, so nx is not involved. Ruled out: missing `app/not-found.tsx`, the `withSentryConfig` wrapper, root-page prerender (`force-dynamic`), stale `.next`, version mismatch (next 15.5.11 / react 19.2.3 / @nx/next 22.5.2 consistent), and `global-error.tsx`. Needs a bisect of `src/app`. Until it is fixed, use `nx run-many --target=typecheck` as the workspace typecheck gate (`@vinto/bot` excluded — 42 pre-existing errors in its test files)
- [ ] 5.2 Manually: play a game in the browser, export, replay with the CLI → PASS; reload mid-game → auto-saved recording exports and replays
