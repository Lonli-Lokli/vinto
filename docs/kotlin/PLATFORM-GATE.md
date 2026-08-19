# Platform gate results

Task 2a of change `migrate-to-kotlin-multiplatform`. This gate runs **before** any porting,
because a failure here changes the architecture rather than the schedule.

Measured 2026-08-18 on the development machine (Windows 11, JDK 17 Temurin, Gradle 8.14,
Kotlin 2.1.21, Node 24).

---

## 2a.1 — Worker bundle size: PASS, with large margin

The gate payload (`kmp/worker`) is a Kotlin/JS production executable exercising the seeded
PRNG, a 54-card Fisher-Yates shuffle and a kotlinx.serialization round trip — the same
shape of work a Durable Object performs per action.

| Module                               |                  Raw |              Gzipped |
| ------------------------------------ | -------------------: | -------------------: |
| `kotlin-kotlin-stdlib`               |              346,426 |               61,074 |
| `kotlinx-serialization-core`         |              210,923 |               33,226 |
| `kotlinx-serialization-json`         |              180,667 |               32,134 |
| `vinto-kmp-shared-shapes` (our code) |                4,146 |                1,409 |
| `vinto-kmp-worker` (our code)        |                9,404 |                2,733 |
| **Total**                            | **751,690 (734 KB)** | **126,594 (123 KB)** |

Cloudflare's Worker script limit is **3 MB after gzip** on the free plan (10 MB paid), so
this uses **~4% of the budget**.

The distribution matters more than the total: **our code is 4 KB gzipped; the other 119 KB
is fixed cost** from the Kotlin stdlib and kotlinx.serialization, which do not grow as the
engine is ported. Even a generous estimate for the full engine plus MCTS leaves the bundle
far inside the limit.

Kotlin/JS also tree-shakes aggressively — a library build with nothing `@JsExport`ed
produced 781 bytes total — so only code reachable from the exported surface ships.

**Conclusion: bundle size is not a risk for the Worker. This was the single biggest
unknown in the Cloudflare design and it is now closed.**

## 2a.1b — MCTS inside the Durable Object CPU budget: PASS

Measured 2026-08-19, now that the bot is ported and the room runs it. This was the last open
item on the platform gate.

A Durable Object allows **30 s CPU per request** (raisable to 5 minutes via `limits.cpu_ms`),
versus **10 ms** for a plain Worker — which is why the room is a Durable Object and not a
Worker, and which the `/replay` incident below demonstrated the hard way.

Three seeded games driven through the real room functions, timing every request end to end. A
request costs one client action plus every bot turn it triggers, which is the unit that
matters: it is what the Durable Object is charged for.

| | per request |
| --- | ---: |
| mean | 131 ms |
| p50 | 4 ms |
| p95 | 590 ms |
| **max** | **1,591 ms** |

141 requests, 459 bot actions, `moderate` difficulty (2,000 MCTS iterations per decision).

**The worst request observed uses ~5% of the budget**, and the median is nothing at all — most
requests trigger no search, because the bots only move when the turn reaches them. The tail is
a request that hands three bots a turn each and pays for three searches.

Two caveats worth stating rather than burying. This is Node rather than workerd, so treat it
as an order-of-magnitude reading — the same caution as the `/replay` timings below, and for
the same reason. And `hard` difficulty is 5,000 iterations rather than 2,000, so its tail
would be roughly 2.5x this one: still inside the budget, but it is the setting to watch.

## 2a.2 — Compose/Wasm bundle: MEASURED, and it is the problem

`kmp/composeApp` is a hello-world: `MaterialTheme`, one `Column`, two `Text`s and one
`Button` with a tap counter. Production `wasmJsBrowserDistribution`:

| Artefact                |                      Raw |                Gzipped |
| ----------------------- | -----------------------: | ---------------------: |
| skiko runtime (`.wasm`) |                8,401,120 |              3,232,928 |
| app code (`.wasm`)      |                1,464,595 |                458,410 |
| `composeApp.js`         |                  554,874 |                100,928 |
| **Total**               | **10,421,887 (10.2 MB)** | **3,792,573 (3.7 MB)** |

For comparison, the entire existing Next.js client — the whole game, all chunks, some of
them lazy-loaded — is **2.0 MB raw / 620 KB gzipped**.

**A Compose/Wasm hello-world is therefore about 6× the gzipped payload of the complete
existing web app, before a single line of game UI.** The bulk is skiko, the Skia renderer
compiled to WebAssembly; it is a fixed cost, so the real UI would grow this sublinearly —
but the floor is the floor.

This does **not** affect Compose on Android or iOS, which are native and carry no such
payload. The finding is specific to the web target.

Judgement: acceptable for an installed app or a returning player with a warm cache;
questionable for a casual card game that people open from a shared link on a phone. This
is a product call, not a technical blocker — recorded here so it is made with the number
in hand rather than in the abstract.

## 2a.3 — Two clients through one Durable Object: PASS

Measured 2026-08-19 on macOS (Xcode 26.6, wrangler 4.124.0) against `wrangler dev --local`,
which runs the real workerd runtime. **Nothing was deployed to Cloudflare** — every result
below is from the local runtime, and the one thing that needs a deployed Worker is called
out at the end.

`kmp/worker` is now a real Worker rather than a Node self-check: a thin routing Worker plus
a `Room` Durable Object (`worker/cloudflare/index.mjs`) over Kotlin room logic
(`worker/src/jsMain/.../Room.kt`). The split is deliberate — the JavaScript moves bytes and
sockets, and every decision about room state is Kotlin, which is where `GameEngine.reduce`
lands once the engine is ported.

`worker/cloudflare/gate-two-clients.mjs` drives it. All 14 checks in the main run pass
(a further 4 belong to the resume check below):

| Property                                                 | Result |
| -------------------------------------------------------- | ------ |
| Two clients join one room and are seated 0 and 1         | pass   |
| Room has exactly 4 seats (design D9)                     | pass   |
| Six alternating actions; both clients see all six        | pass   |
| Log indices monotonic from 0, seats alternate            | pass   |
| Reconnect with the same `clientId` returns the same seat | pass   |
| `resync` from a cursor returns only unseen events        | pass   |
| State survives every socket closing                      | pass   |

### The gate doubles as a cross-language check

The room seed is **12345** because `fixtures/prng/vectors.json` publishes the bounded
sequence for seed 12345 / bound 54. Every accepted action draws from it, so the harness
asserts the Durable Object's event values against the same committed file the TypeScript and
Kotlin unit tests read:

```
action values match the published sequence   [9, 24, 16, 16, 30, 43]
```

That is Kotlin compiled to JavaScript, running inside a Durable Object, reproducing the
numbers TypeScript verifies. The parity contract now holds on the server too.

### Hibernation

The sockets are accepted with `ctx.acceptWebSocket()`, not `server.accept()`. This is
checkable rather than asserted: the `webSocketMessage()` handler on the Durable Object class
**only fires for hibernation-API sockets** — a `server.accept()` socket delivers to an event
listener instead and would never reach it. Every message in the run above arrived there, so
the object is hibernatable.

What makes that safe is that the object holds no authoritative state in memory: the room is
read from storage at the start of each handler, and each socket's seat rides on the socket
via `serializeAttachment`, which survives hibernation where a `Map` keyed by socket would
not.

Resume was verified by destroying every instance — stopping `wrangler dev` entirely,
restarting it, and re-reading the room (`gate-two-clients.mjs --verify <room>`):

```
log survived · seats survived · generator state survived · replays to the published sequence
```

A process restart is strictly harsher than hibernation on state, since hibernation keeps
storage and the sockets while losing only memory.

**The remaining sliver**: eviction-and-resume with _live sockets still attached_ cannot be
forced locally — workerd exposes no way to trigger it, and it is timing-dependent. That one
behaviour needs a deployed Worker to observe directly. The API contract that governs it is
in use and the state it depends on is proven durable, so this is a confirmation to schedule,
not an open design risk.

## Engine in the Worker runtime: PASS

Measured 2026-08-19 against `wrangler dev --local` (real workerd). The Worker now carries the
ported engine and exposes `POST /replay`, which replays a `GameRecording` and returns either
`ok` or the exact action that diverged.

**All 50 recordings, 13,900 actions, replay correctly in the Worker runtime.**

This is not a duplicate of the JVM parity gate; it closes a different risk. Everything
proving the port ran on the JVM, while Cloudflare runs Kotlin/JS — where `Long` is a pair of
`Int`s and the serialiser backend differs. Agreement on one does not imply the other, and now
it is measured rather than assumed.

```
50/50 recordings replayed in the Worker runtime, 13900 actions, 6454 ms
ENGINE RUNTIME GATE PASS
```

### An early read on 2a.1b (MCTS inside the CPU budget)

Repeated runs of the full corpus through `wrangler dev`, in order: 6.5 s, 44.5 s, 24.8 s,
13.7 s, 12.5 s. **The first figure was an outlier and should not be quoted** — an earlier
revision of this document did, and the number was wrong by roughly 2x. Warm steady state is
~12.5 s for 13,900 actions, so about **0.9 ms per action**, and the spread is wide enough that
local `wrangler dev` should be treated as an order-of-magnitude reading rather than a benchmark.

Two things that measurement includes which a live room does not: it parses a whole recording
(up to 141 KB) per request, and it canonicalises and SHA-256-hashes the entire state after
_every_ action to compare against the recorded hash. The hashing is the expensive half and it
exists for parity checking.

The useful framing is per request rather than per action, since that is what the limit is on: a
whole 278-action game replays in ~250 ms against a Durable Object's **30 s** of CPU per request,
and a live room does one reduction per request rather than a game's worth. The engine is not
close to the budget. Whether **MCTS** fits remains the open half of 2a.1b and needs the ported
bot (phase 6) — that is where the CPU actually goes.

## 2a.1 revisited — the real Worker bundle

The 123 KB figure above was a synthetic payload. The actual Worker — routing, Durable
Object, room logic, hibernation handlers and the Kotlin bundle — measured with
`wrangler deploy --dry-run`:

| Bundle                             |          Raw |    Gzipped |
| ---------------------------------- | -----------: | ---------: |
| Gate payload (synthetic)           |       734 KB |     123 KB |
| Real Worker + Durable Object       |       768 KB |     126 KB |
| **…plus the engine, bot and room** | **1,612 KB** | **244 KB** |

The room implementation cost ~3 KB gzipped over the synthetic floor. Adding the *whole ported
game* — engine, validator, MCTS bot, coalition planner, room logic — took it to **244 KB
gzipped**, still **~8% of the 3 MB free-plan limit**. The projection in 2a.1 holds: our code
roughly doubled the bundle, and the fixed cost is the Kotlin stdlib and kotlinx.serialization.

---

## Unplanned result: cross-language PRNG parity already holds

The gate payload was chosen so it would double as real work — porting `Prng` (task 3.3).
It produced the first genuine cross-language check, and it passes:

|                                 | `rngState` after shuffling 54 cards with seed 42 | first 5          |
| ------------------------------- | ------------------------------------------------ | ---------------- |
| TypeScript (`packages/shapes`)  | 2583707619                                       | 29, 3, 44, 51, 5 |
| Kotlin/JS (`kmp/shared/shapes`) | 2583707619                                       | 29, 3, 44, 51, 5 |

`kmp/shared/shapes/src/jvmTest/.../PrngVectorsTest.kt` reads **the same committed
`fixtures/prng/vectors.json`** the TypeScript test reads, rather than a Kotlin copy of the
numbers, so the two implementations cannot drift without failing.

The test was verified to fail: replacing the unsigned modulo in `nextInt` with Kotlin's
signed `%` — the exact trap documented in `RECORDING.md` — fails three of the six tests,
including the one written for it.

---

## What this means for sequencing

- The Worker half of the Cloudflare design is **de-risked** — bundle size (2a.1) and the
  multi-client Durable Object room (2a.3) both pass, the latter against the real workerd
  runtime. Porting can proceed.
- The Compose/Wasm measurement (2a.2) should happen before committing to the web rewrite,
  since it is the remaining decision-changing unknown.
- iOS targets are absent from `kmp/` deliberately: Kotlin/Native cannot build them on
  Windows. They are added behind a host check when a macOS machine or CI runner is
  available.
