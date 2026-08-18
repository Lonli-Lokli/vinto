# Game Recording, Determinism and Replay

This document is the **contract between engine implementations**. The TypeScript engine
is the reference; any second implementation (starting with the Kotlin Multiplatform port)
must reproduce everything specified here byte for byte.

If you change anything in this document, you are changing a cross-language contract:
update both implementations and regenerate the fixture corpus in the same change.

---

## 1. Why this exists

A game is reproducible if it is fully described by its starting state plus the ordered
list of accepted actions. Given that, two engines can be compared mechanically: replay the
same recording through both and compare a hash of the state after every action. The first
action whose hashes disagree names the handler that was ported wrong.

That only works if the engine is deterministic, which requires three things:

1. all randomness comes from a seeded generator carried **inside** `GameState`
2. no clocks, uuids or ambient randomness anywhere in the reducer path
3. a canonical serialisation, so "the same state" is a byte-level question

---

## 2. The pseudo-random generator

**Algorithm: mulberry32.** The generator state is an unsigned 32-bit integer held in
`GameState.rngState`. Every operation is 32-bit integer arithmetic; there is no
floating-point step anywhere, deliberately.

```
next(state):
  state = (state + 0x6D2B79F5) mod 2^32
  t = state
  t = imul(t xor (t ushr 15), t or 1)
  t = t xor (t + imul(t xor (t ushr 7), t or 61))
  value = (t xor (t ushr 14)) as uint32
  return { value, state }

nextInt(state, bound):        // bound must be a positive integer
  { value, state } = next(state)
  return { value: value mod bound, state }   // unsigned modulo

shuffle(items, state):        // Fisher-Yates, descending
  for i = items.length - 1 downTo 1:
     { value: j, state } = nextInt(state, i + 1)
     swap(items[i], items[j])
  return { items, state }
```

`nextInt` uses modulo rather than rejection sampling. The bias is irrelevant for the
bounds this game uses (at most 54) and modulo is trivially identical across languages,
which rejection sampling is not.

Reference implementation: `packages/shapes/src/lib/prng.ts`.

### Test vectors

`fixtures/prng/vectors.json` is committed and is read directly by both implementations'
tests. It contains, for a set of seeds: the first 10 raw outputs with the resulting
generator state, bounded sequences for several bounds, and the full order of a shuffled
54-element deck. **Port the PRNG and make these vectors pass before porting anything
else** — every other divergence is harder to debug than this one.

### Notes for the Kotlin port

- `rngState` is a **uint32**, and Kotlin's `Int` is signed. Hold it as `Long` masked with
  `0xFFFFFFFFL`, or as `UInt`. Serialising it through a signed `Int` corrupts any value
  at or above 2^31.
- JavaScript `Math.imul` is a 32-bit signed multiply that wraps; Kotlin's `Int * Int`
  wraps identically. `>>>` in JS is `ushr` in Kotlin.
- The trap is `nextInt`: Kotlin's `%` on a negative `Int` yields a negative result. Do the
  modulo in unsigned space.

---

## 3. Determinism rules for the engine

The reducer path must never reference a clock, ambient randomness or a uuid generator.
Concretely, none of `Date.*`, `new Date`, `Math.random`, `crypto.*`, `performance.now`, or
any uuid library may appear in engine sources. This is enforced by a test
(`packages/engine/src/lib/__tests__/purity-guard.test.ts`) that scans the sources and
fails on a hit.

Consequences already applied to the reference implementation:

| Concern                       | Rule                                                                                                               |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Deal and mid-game reshuffle   | Consume `GameState.rngState`; the advanced state **must** be written back                                          |
| Engine-minted card ids        | Derived from state. Toss-in queued cards use `tossin_queued_<turnNumber>_<playerId>_<rank>_<remainingQueueLength>` |
| `gameId`                      | `vinto-<seed>` — the seed round-trips through the state                                                            |
| `GameActionHistory.timestamp` | The zero-based index of the accepted action, never wall-clock                                                      |
| Seed selection                | Chosen **outside** the engine; if a caller supplies none, the client generates one and it is recorded              |

Player count is fixed at 4. A recording whose `initialState.players` has any other length
is invalid.

---

## 4. Canonical serialisation

`canonicalizeGameState(state) -> string` (`packages/shapes/src/lib/canonical-json.ts`)
produces the string that gets hashed. The rules:

- object keys sorted **lexicographically at every level**
- arrays in their natural order
- a `Pile` is emitted as a plain JSON array, **top card first**
- properties whose value is `undefined` are **omitted**; `null` is **kept** and emitted as `null`
- no whitespace anywhere
- **integers only.** A non-integer or non-finite number is an error, not a rounding
  problem: the canonicaliser throws and names the offending path

The integer-only rule is not fussiness. TypeScript prints `1` where Kotlin prints `1.0`
for the same value, so a single fractional number in `GameState` would make the two
implementations disagree forever, in a way that looks like a logic bug.

### Excluded fields

Exactly three fields are excluded from the canonical form. **Everything else in
`GameState` is hashed.**

| Excluded                 | Why                                                                                                 |
| ------------------------ | --------------------------------------------------------------------------------------------------- |
| `GameState.turnActions`  | Client-authored history. Each entry holds a human-readable `description` produced by the UI layer   |
| `GameState.roundActions` | Same                                                                                                |
| `PlayerState.botMemory`  | Bot-internal, holds floating-point confidences, and is never written into `GameState` by the engine |

History is excluded on purpose. Hashing it would make **UI copy part of this contract**:
every client would have to reproduce English action descriptions character for character,
and rewording a message would invalidate the entire fixture corpus. A Kotlin client is
free to word its history however it likes, including translating it.

`PlayerState.opponentKnowledge` is deliberately **not** excluded. Engine handlers
(`declare-king-action`, `execute-queen-swap`, `participate-in-toss`,
`select-action-target`) write it deterministically from actions, so it is real game state
and must match exactly.

### Hash

`hashGameState(state)` is the **lowercase hex SHA-256 of the UTF-8 canonical string**.

The reference implementation uses WebCrypto (`crypto.subtle.digest`) rather than
`node:crypto`, so Node, the browser and tests share one code path. The Kotlin port should
likewise use a single pure-Kotlin SHA-256 across all targets (for example
`org.kotlincrypto.hash:sha2`) rather than per-platform implementations, so no platform can
drift.

---

## 5. `GameRecording` format v1

```jsonc
{
  "formatVersion": 1,
  "meta": {
    "recordedAt": "2026-08-18T12:00:00.000Z", // informational; never hashed
    "producer": "vinto-ts/generate-recordings",
    "label": "optional human note",
  },
  "settings": {
    "humanPlayerName": "You",
    "difficulty": "moderate",
    "botVersion": "v1",
    "seed": 4242,
  },
  "initialState": {/* full GameState after dealing, phase 'setup' */},
  "actions": [
    {
      "action": { "type": "DRAW_CARD", "payload": { "playerId": "human-1" } },
      "stateHash": "…sha256 of the state AFTER this action; optional…",
    },
  ],
  "finalState": {/* full GameState when the recording was written */},
  "finalStateHash": "…optional…",
}
```

Rules:

- **Only accepted actions are recorded.** A rejected action never mutated state, so
  replaying it would diverge.
- A recording may end **mid-game**. `finalState` is simply the state when the recording
  was written; replay ends where the recording ends. This is normal, not a defect.
- `meta` is informational and is never hashed, so two exports of the same seeded game
  differ only in `meta`.
- Readers **must reject** an unrecognised `formatVersion` with a clear error rather than
  attempting a replay. A change to the shape or to any rule in this document bumps the
  version.

Types: `packages/shapes/src/lib/game-recording-types.ts`.

---

## 6. Replay

`replayRecording(recording)` (`packages/engine/src/lib/replay.ts`) rehydrates
`initialState`, applies each action through `GameEngine.reduce`, and reports the **first**
divergence as one of:

| Reason                 | Meaning                                                           |
| ---------------------- | ----------------------------------------------------------------- |
| `action-rejected`      | The engine refused an action the recording says was accepted      |
| `hash-mismatch`        | The state after an action does not match its recorded `stateHash` |
| `final-state-mismatch` | The replayed final state does not match `finalState`              |

Two implementation details a port must match:

1. **Rehydration.** `JSON.parse` turns a `Pile` into a plain array. It must be rebuilt
   before use (`rehydrateGameState`), or the first draw fails.
2. **Compare by canonical hash, not by deep equality.** Replay reconstructs _engine_
   state only, and `turnActions`/`roundActions` are written by the client — so a replayed
   state legitimately has no history. Comparison works precisely because the canonical
   form excludes history.

---

## 7. The fixture corpus

`fixtures/recordings/*.json` is the cross-implementation gate. Every implementation
replays the whole corpus; any divergence blocks the release.

- Generate: `npm run recordings:generate -- --games 50 --seed 1`
- Verify: `npm run recordings:replay -- fixtures/recordings`
- In CI, the same corpus runs as a vitest suite
  (`packages/engine/src/lib/__tests__/replay-fixtures.test.ts`), which also requires that
  the corpus is non-empty and that **every** recorded action carries a `stateHash`. A
  fixture without hashes only proves the engine did not crash, not that it agreed step by
  step.

Regenerating the corpus requires a stated justification, and the change must update every
implementation together.

### What the corpus must cover

Replaying cleanly is not enough. A corpus that never reaches scoring, never reshuffles and
never plays a coalition round would look like a gate while exercising only the easy half of
the engine, so `replay-fixtures.test.ts` also asserts that the committed corpus contains:

- at least 50 recordings, each with a `stateHash` on every action
- at least one game that reaches the `scoring` phase
- at least one coalition final round (`vintoCallerId` and `coalitionLeaderId` both set)
- at least one **mid-game draw-pile reshuffle** — the engine's only consumer of `rngState`,
  so without one the seeded generator is never exercised end to end
- at least one occurrence of each of `DRAW_CARD`, `DISCARD_CARD`, `USE_CARD_ACTION`,
  `SELECT_ACTION_TARGET`, `PARTICIPATE_IN_TOSS_IN`, `DECLARE_KING_ACTION` and `CALL_VINTO`

Games are capped by `--max-actions` rather than wall-clock, so generation does not depend
on machine speed. A recording that hits the cap is a valid mid-game fixture.
