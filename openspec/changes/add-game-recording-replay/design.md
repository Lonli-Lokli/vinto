# Design: game recording, determinism and replay

## Context

`GameEngine.reduce(state, action)` is already a pure reducer over an immutable,
serialisable `GameState`, and `GameClient.dispatch` is the single choke point through
which every accepted action flows (humans and bots alike). That makes "record the
accepted actions + the starting state" sufficient to reproduce a game — **provided the
engine is deterministic**. The remaining sources of non-determinism are small and
enumerable (see proposal). Bots do _not_ need to be deterministic: a recording captures
the actions they chose, and replay never re-runs bot logic.

The format is also the contract for the Kotlin port, so every choice below is made
with "trivially implementable identically in Kotlin" as the tie-breaker.

## Goals / Non-Goals

- Goals: exact reproducibility of any recorded game; a stable, versioned JSON format;
  cheap per-action divergence detection; fixtures a second implementation can consume.
- Non-goals: deterministic bots; replaying games recorded before this change; a
  full-featured replay UI (a stepper is optional); network/multiplayer transport.

## Decisions

### D1. PRNG: mulberry32 stored in `GameState.rngState`

- `rngState` is an unsigned 32-bit integer (JSON number, `0 ≤ n < 2^32`).
- `next(state)`: `s = (s + 0x6D2B79F5) >>> 0; t = s; t = imul(t ^ (t >>> 15), t | 1);
t ^= t + imul(t ^ (t >>> 7), t | 61); out = (t ^ (t >>> 14)) >>> 0` → returns
  `{ value: out, state: s }`. Only 32-bit integer ops → identical in Kotlin (`Int`/`UInt`,
  `ushr`, `*` wrapping).
- `nextInt(bound)` = `value mod bound` (unsigned). Modulo bias for bounds ≤ 54 is
  irrelevant and this avoids any floating-point path.
- Fisher–Yates: `for i in n-1 downTo 1: j = nextInt(i+1); swap(i, j)`.
- Why not xoshiro/PCG: they need 64-bit or multi-word state; mulberry32's single 32-bit
  word keeps the state a plain JSON number.
- The engine advances `rngState` only where it consumes randomness (currently just the
  draw-pile reshuffle). Consumers MUST write the new state back.

### D2. Seed → initial state

- `GameSettings.seed?: number` (uint32). If absent the client picks one (from
  `crypto.getRandomValues`, outside the engine) and stores it in the recording.
- `initializeGame` derives `gameId` deterministically from the seed and settings
  (e.g. `vinto-<seed>`) instead of `uuidv4()`, creates the deck in fixed order, shuffles it
  with the seeded PRNG, deals, and stores the post-shuffle `rngState` in the state.
- The recording still embeds the **full** `initialState`, so replay never depends on the
  dealing algorithm — the seed is informative and lets a fresh client regenerate the
  same game.

### D3. Deterministic ids and history in state

- `player-toss-in-finished.ts` and `toss-in-utils.ts` mint queued-action card ids from
  state: `tossin_queued_<turnNumber>_<playerId>_<rank>_<index>`.
- `GameActionHistory.timestamp` becomes a deterministic sequence number (the index of the
  accepted action that produced it). If wall-clock display time is ever wanted it lives
  in the recording metadata, not in `GameState`. This keeps exported recordings
  byte-stable across runs; it is **not** a parity requirement, since history is excluded
  from the canonical hash (D4).
- Rule (enforced by a lint rule + a unit test that greps the engine sources): the reducer
  path never references `Date`, `Math.random`, `crypto`, `uuid`, `performance.now`.
- Note: `turnActions`/`roundActions` are written by `GameClient.addActionToHistory`, not
  by the reducer, so the purity guard over `packages/engine/src` does not cover them.

### D4. Canonical JSON and state hash

- Canonical form of a `GameState`: JSON with object keys sorted lexicographically at
  every level, arrays in order, `Pile` serialised as an array top-first (existing
  `toJSON`), `undefined` properties omitted, `null` kept, no whitespace, integers only
  (assert: no non-integer numbers may appear in `GameState`).
- **Excluded from the canonical form** (exactly three fields, everything else is hashed):
  - `PlayerState.botMemory` — bot-internal, contains floats, and is in fact never written
    into `GameState` by any engine or client code today.
  - `GameState.turnActions` / `GameState.roundActions` — client-authored history. Each
    entry carries a human-readable `description` string produced by the UI layer, so
    hashing them would make **UI copy part of the cross-language contract**: every Kotlin
    client would have to reproduce English action descriptions character-for-character,
    and any wording change would invalidate the entire fixture corpus. History is
    presentation, not game logic, and is excluded.
  - `PlayerState.opponentKnowledge` is deliberately **not** excluded: it is written by
    engine handlers (`declare-king-action`, `execute-queen-swap`, `participate-in-toss`,
    `select-action-target`) deterministically from actions, and is real game state.
- Hash = lowercase hex SHA-256 of the UTF-8 canonical string.
- Node/tools use `node:crypto`; the browser export computes hashes with `crypto.subtle`
  (async, only at export time) or leaves them empty — hashes are optional in the file and
  are always recomputed by the replay tool.

### D5. `GameRecording` v1

```jsonc
{
  "formatVersion": 1,
  "meta": {
    "recordedAt": "2026-08-17T15:00:00.000Z", // informational only
    "producer": "vinto-ts@<git sha or version>", // which implementation recorded it
    "label": "optional human note",
  },
  "settings": { "botCount": 3, "humanPlayerName": "You", "difficulty": "hard", "seed": 123456789 }, // always 4 players
  "initialState": {
    /* full GameState after dealing, phase 'setup' */
  },
  "actions": [{ "action": { "type": "DRAW_CARD", "payload": { "playerId": "p1" } }, "stateHash": "…sha256 of the state AFTER this action (optional)…" }],
  "finalState": {
    /* full GameState when the recording was exported */
  },
  "finalStateHash": "…optional…",
}
```

- Only **accepted** actions are recorded (rejected ones never mutate state).
- `finalState` may be mid-game (export at any time); replay of a mid-game recording is
  valid and ends where the recording ends.
- Format changes bump `formatVersion`; readers reject unknown versions with a clear error.

### D6. Recorder placement

- `GameRecorder` lives in `@vinto/local-client`, is created with the initial state and
  settings, and is fed from `GameClient.dispatch` when `result.success`. It exposes
  `toRecording(finalState)` and `toJSON()`.
- `GameClient` gains `exportRecording(): string` (used by the debug panel and a settings
  entry) and auto-saves the current recording to `localStorage` after every accepted
  action (debounced) under a versioned key, so a crash still leaves a reproducible file.

### D7. Replay API (pure, in `@vinto/engine`)

- `replayRecording(rec, opts?) => { ok, finalState, steps, divergence? }` — applies each
  action with `GameEngine.reduce`; a rejected action or a hash mismatch (when hashes are
  present) stops the replay and reports `{ index, action, expectedHash, actualHash,
stateBefore, stateAfter }`. Compares the final state to `rec.finalState` by canonical
  hash.
- The same function backs the Vitest parity suite and `tools/replay-recording.ts`.

### D8. Fixture generation

- `tools/generate-recordings.ts --games N --seed S` runs headless bot-only 4-player games
  (`BotAIAdapter` with `skipDelays`, exactly as the local-client tests do) and writes
  `fixtures/recordings/selfplay-<seed>.json` with hashes filled in.
- A second source: the engine scenario tests export their action sequences as
  `fixtures/recordings/scenario-<name>.json`, so every rule edge case in `SCENARIOS.md`
  has a fixture.
- Fixtures are committed and are the input of the cross-implementation parity gate.

## Risks / Trade-offs

- Changing `shuffleCards`' signature and `GameActionHistory.timestamp` touches tests and
  a little UI; the UI never displayed the timestamp, so risk is low.
- Recording every game to `localStorage` costs a few hundred KB per game; keep only the
  last game (and clear on new game).
- The modulo-based `nextInt` is slightly biased; irrelevant for shuffling 54 cards.

## Migration Plan

1. Land determinism changes + tests (no user-visible change).
2. Land recording format, recorder, export, auto-save.
3. Land replay API, CLI, fixture generator; commit an initial fixture set (≥ 50 self-play
   games, all scenario recordings).
4. Wire the parity suite into CI (`nx test engine` runs it).

## Open Questions

- Should the human's setup-phase peeks (`PEEK_SETUP_CARD`) be recorded? Yes — they are
  actions and affect `knownCardPositions`; recorded like everything else.
- Do we want the optional replay viewer in the web app before the mobile app exists?
  Proposed: minimal stepper behind the debug panel only.
