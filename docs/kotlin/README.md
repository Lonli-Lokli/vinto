# Kotlin workspace — setup, state and handoff

Everything needed to pick this migration up on another machine, in particular a Mac where
the iOS targets can finally be built.

- **Plan of record**: `openspec/changes/migrate-to-kotlin-multiplatform/` (proposal, design, tasks)
- **Cross-language contract**: `docs/game-engine/RECORDING.md`
- **Platform measurements**: `docs/kotlin/PLATFORM-GATE.md`
- **Bot decision**: `docs/bot/BOT-ENGINE-DECISION.md`

---

## 1. Where the work stands

Branch: **`kotlin`** (not merged; CI has never run on it — see §7).

**Done — TypeScript side (`add-game-recording-replay`, 22/26 tasks)**

- Engine is deterministic: seeded mulberry32 in `GameState.rngState`, no clocks/uuids/ambient
  randomness in the reducer path (enforced by `purity-guard.test.ts`)
- `GameRecording` v1, canonical JSON, SHA-256 hashing, `replayRecording()` with divergence reports
- 50-recording parity corpus in `fixtures/recordings/`, coverage asserted by
  `replay-fixtures.test.ts` (scoring, coalition round, mid-game reshuffle, all action types)
- CLIs: `npm run recordings:generate`, `npm run recordings:replay`
- One bot engine (v1/MCTS); v2 and `botVersion` deleted
- Bots call Vinto, so self-play games actually end

**Done — Kotlin side (`migrate-to-kotlin-multiplatform` phases 1, 2a)**

- `kmp/` Gradle workspace with `shared:shapes`, `worker`, `composeApp`
- `Prng` ported and **verified against the same `fixtures/prng/vectors.json` the TypeScript
  tests read** — the first real cross-language parity check
- Platform gate measured: Worker bundle 123 KB gzipped (4% of the 3 MB budget);
  Compose/Wasm 3.7 MB gzipped (accepted by the product owner)

**Next**

1. iOS bring-up on the Mac (§5) — this is why the machine changed
2. Remaining gate items: MCTS inside the Durable Object CPU budget; two clients through one
   Durable Object
3. Port `shapes` proper (`GameState`, `Card`, `GameAction` + serializers), then the engine
   behind the parity gate

---

## 2. Prerequisites

| Tool     | Version used  | Notes                                                    |
| -------- | ------------- | -------------------------------------------------------- |
| JDK      | 17 (Temurin)  | Gradle toolchain; 17+ is fine                            |
| Gradle   | 8.14          | Via the committed wrapper — do not install system Gradle |
| Node     | 24            | For the TypeScript side and `vite-node` tools            |
| Xcode    | latest stable | **macOS only**; needed for the iOS targets               |
| wrangler | 4.x           | `npx wrangler` — no global install needed                |

```bash
git clone <repo> && cd vinto
npm ci                 # TypeScript side
cd kmp && ./gradlew --version   # bootstraps Gradle 8.14 on first run
```

## 3. Module map (`kmp/`)

| Module          | Targets                                         | Purpose                                                                           |
| --------------- | ----------------------------------------------- | --------------------------------------------------------------------------------- |
| `shared:shapes` | jvm, js, (iosArm64, iosSimulatorArm64 on macOS) | Types + `Prng`. The port starts here.                                             |
| `worker`        | js                                              | Cloudflare Worker / Durable Object bundle. Currently the platform-gate payload.   |
| `composeApp`    | wasmJs                                          | Compose UI. Currently the platform-gate payload; Android/iOS targets to be added. |

The full intended layout is in design D1. Modules are added as they are ported rather than
scaffolded empty.

## 4. Commands

```bash
# --- Kotlin (run from kmp/) ---
./gradlew :shared:shapes:jvmTest              # cross-language PRNG parity check
./gradlew :worker:jsNodeProductionRun         # run the Kotlin/JS gate payload
./gradlew :composeApp:wasmJsBrowserDistribution   # build the Compose web bundle
./gradlew build                               # everything available on this host

# --- TypeScript (run from repo root) ---
npm test                                      # all 5 projects
npx nx run-many --target=typecheck --all --skip-nx-cache
npm run recordings:replay -- fixtures/recordings    # parity gate via CLI
npm run recordings:generate -- --games 5 --seed 1   # ~75 s per game
```

## 5. iOS bring-up (do this first on the Mac)

iOS targets are declared behind a host check in `kmp/shared/shapes/build.gradle.kts`, so
they activate automatically on macOS. **They have never been compiled** — no Mac was
available — so expect to fix things.

1. `cd kmp && ./gradlew :shared:shapes:build` — this is the first time `iosArm64` and
   `iosSimulatorArm64` will ever compile. `Prng` is pure Kotlin with no platform APIs, so it
   should pass; if it does, the toolchain is sound.
2. Run the parity check on an iOS simulator target to prove the contract holds on Apple
   platforms, not just JVM/JS. Note `PrngVectorsTest` is currently **jvmTest only** because
   it reads the vectors file from disk; on iOS the fixture must be bundled as a resource or
   the vectors embedded at build time. Decide which, and prefer keeping the single shared
   file if at all possible — the whole point is that both languages read the _same_ bytes.
3. Add `androidTarget()` and the iOS targets to `composeApp`, plus the `iosApp` Xcode
   project (design D1).
4. Revisit the host check: once macOS is the primary dev machine, consider making iOS
   targets unconditional and instead guarding on non-Mac hosts, so shared-code breakage
   surfaces immediately.

**Why this was deferred**: Kotlin/Native cannot build Apple targets on Windows at all. It is
a hard toolchain limitation, not a configuration gap.

## 6. Decisions already made — do not silently reopen

| Decision                                                                    | Where recorded                            |
| --------------------------------------------------------------------------- | ----------------------------------------- |
| Cloudflare Durable Object per room; no JVM server                           | design D1, D9                             |
| Bots run server-side (a client would need other seats' hidden cards)        | design D9, online-multiplayer spec        |
| Compose Multiplatform for web, 3.7 MB gzipped accepted                      | design D1 risks, `PLATFORM-GATE.md`       |
| One bot engine (v1/MCTS); v2 deleted for reading hidden hands               | `docs/bot/BOT-ENGINE-DECISION.md`         |
| Canonical hash excludes history + `botMemory`, includes `opponentKnowledge` | `RECORDING.md` §4                         |
| Every game is exactly 4 players                                             | deterministic-engine spec                 |
| Bots call Vinto when hand is fully known and worth ≤ 0                      | `packages/bot/src/lib/vinto-call-rule.ts` |

## 7. Traps and known issues

**Repo-wide**

- `npx tsc` and `npm run typecheck` are **denied by project policy**. Use
  `npx nx run-many --target=typecheck`.
- **Nx caches typecheck results**, which will happily report success for code that no longer
  compiles. Pass `--skip-nx-cache` when you want a real answer. This masked a genuine
  failure during this work.
- **`nx build @vinto/game` is broken** (pre-existing): `<Html> should not be imported outside
of pages/_document` while prerendering `/404`. Ruled out: missing `not-found.tsx`, the
  Sentry wrapper, root-page prerender, stale `.next`, version mismatch, `global-error.tsx`.
  Needs a bisect of `src/app`. It blocks CI's `build` job.
- **CI never runs on this branch**: `.github/workflows/ci.yml` triggers only on `master` and
  `test`. Merging will be the first real run.
- **At least two tests are flaky**, both driven by MCTS, which uses `Math.random`:
  `bot/…/mcts-coalition-cooperation.test.ts` ("should use coalition evaluation when in final
  round") and `local-client/…/bot-tossin.test.ts` ("should require all 3 bots to mark
  ready"). Each was observed to fail once and then pass three consecutive reruns. Expect
  intermittent red CI until the bot's randomness is made injectable — which the Kotlin port
  plans anyway (design D4: injected `Random`, fixed iteration budget in tests). Treat a
  single failure in these two as suspect before assuming a regression; rerun first.
- **Coverage thresholds are inert**: all five `vite.config.ts` files put `lines: 70` outside
  `coverage.thresholds`, which Vitest 4 ignores. Coverage gates nothing today.
- `tools/*.ts` need `vite-node` (`npm run recordings:*`); `ts-node` cannot load the
  workspace `.ts` sources through `node_modules` symlinks.
- lefthook pre-commit runs lint `--fix` and `nx format:write`, so files change under you
  during commits.

**Kotlin**

- `gradlew` **must stay LF** or it fails on Linux/CI with "bad interpreter". Pinned in
  `.gitattributes`; don't undo it.
- Kotlin/JS **tree-shakes to the exported surface** — a library build with nothing
  `@JsExport`ed produced 781 bytes. Measure with `binaries.executable()`, not `library()`.
- JVM-only stdlib functions (e.g. `toSortedSet()`) fail on other targets. The compiler
  catches them, but only for targets that are actually configured — see §5 point 4.
- `Prng` traps, both real and both covered by tests: the state is a **uint32** so it is
  carried as `Long` (a signed `Int` corrupts values ≥ 2^31), and `nextInt` must take the
  modulo in **unsigned** space because Kotlin's `%` can return negative.

**This Windows machine specifically** (may not apply on the Mac)

- Antivirus **deletes `gradlew.bat`** shortly after it is written. It is committed, so
  `git checkout kmp/gradlew.bat` restores it; add a repo exclusion if it recurs.
- No `python` on PATH; heredocs invoking `python` hang.

## 8. Verification checklist for a new machine

```bash
npm ci
npm test                                       # expect ~608 passing across 5 projects
npx nx run-many --target=typecheck --all --skip-nx-cache   # expect 5 green
npm run recordings:replay -- fixtures/recordings           # expect 50/50 clean
cd kmp && ./gradlew :shared:shapes:jvmTest                 # expect 6 tests, PRNG parity
./gradlew :worker:jsNodeProductionRun                      # expect "gate ok: rngState=2583707619"
```

That last number is the useful one: `rngState=2583707619` after shuffling 54 cards with
seed 42 is produced by **both** implementations. If it differs, the cross-language contract
has broken and nothing above it can be trusted.
