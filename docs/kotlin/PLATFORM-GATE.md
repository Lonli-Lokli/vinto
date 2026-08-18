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

## 2a.1b — MCTS inside the Durable Object CPU budget: NOT YET MEASURED

Cannot be measured until the bot is ported (phase 6). What is known:

- A Durable Object allows **30 s CPU per request**, raisable to 5 minutes via
  `limits.cpu_ms` — versus **10 ms** for a plain Worker, which is why the room is a
  Durable Object and not a Worker.
- The TypeScript MCTS currently takes on the order of a second per decision (~75 s for a
  full ~300-action self-play game, four bots).

The headroom looks ample, but this stays **open** until measured with the real ported bot.
It is not a blocker for starting the port.

## 2a.2 — Compose/Wasm bundle: NOT YET MEASURED

Outstanding. Compose for web is the least mature Compose target and its bundle is expected
to be far larger than the Worker's. This is now **the largest remaining platform risk**,
and it gates the decision to rewrite the web client in Compose.

## 2a.3 — Two clients through one Durable Object: NOT YET MEASURED

Outstanding. Needs the protocol module and a deployed Worker.

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

- The Worker half of the Cloudflare design is **de-risked**. Porting can proceed.
- The Compose/Wasm measurement (2a.2) should happen before committing to the web rewrite,
  since it is the remaining decision-changing unknown.
- iOS targets are absent from `kmp/` deliberately: Kotlin/Native cannot build them on
  Windows. They are added behind a host check when a macOS machine or CI runner is
  available.
