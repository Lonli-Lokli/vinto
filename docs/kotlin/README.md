# Kotlin workspace — setup, state and handoff

Everything needed to pick this migration up on another machine. The iOS targets have now
been built and tested on a Mac (§5); Android is the remaining untried platform.

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

**Done — first macOS session**

- The iOS targets of `shared:shapes` **compile for the first time** (`iosArm64`,
  `iosSimulatorArm64`); `Prng` needed no changes, so the toolchain is sound
- `PrngVectorsTest` moved to `commonTest` and now runs on **JVM, JS/Node and the iOS
  simulator** — 6 tests on each — against the one shared vector file (§5 step 2 records how
  and why)
- `kmp/gradle.properties` added: the Compose/Wasm production compile ran out of memory on
  the Kotlin daemon's default heap (§7)
- **`composeApp` now targets Android, iOS and web from one `commonMain`**, with the `iosApp`
  Xcode project embedding the Compose framework. Verified by _running_, not just building:
  the same UI renders on the iOS simulator and on an Android emulator (§5 steps 3–4)
- **Platform gate 2a.3 passes**: `kmp/worker` is a real Cloudflare Worker with a `Room`
  Durable Object. Two WebSocket clients join one room, exchange actions and resync, and the
  room rebuilds from storage after every instance is destroyed. The harness asserts the
  Durable Object's values against `fixtures/prng/vectors.json`, so it doubles as a
  cross-language check — Kotlin inside a Durable Object reproduces the numbers TypeScript
  verifies. Details, and the one sliver that needs a deployed Worker: `PLATFORM-GATE.md`

**Next**

1. Port `shapes` proper (`GameState`, `Card`, `GameAction` + serializers), then the engine
   behind the parity gate. Note `shapes` still has no `androidTarget`/`wasmJs`, so
   `composeApp` cannot depend on it yet — add those when the port starts
2. Gate item 2a.1b (MCTS inside the Durable Object CPU budget) stays open **by design**: it
   cannot be measured until the bot is ported in phase 6, and it is not a blocker

---

## 2. Prerequisites

| Tool        | Version used  | Notes                                                                                                      |
| ----------- | ------------- | ---------------------------------------------------------------------------------------------------------- |
| JDK         | 17 (Temurin)  | Gradle toolchain; 17+ is fine                                                                              |
| Gradle      | 8.14          | Via the committed wrapper — do not install system Gradle                                                   |
| Node        | 24            | For the TypeScript side and `vite-node` tools                                                              |
| Xcode       | latest stable | **macOS only**; needed for the iOS targets                                                                 |
| wrangler    | 4.x           | `npx wrangler` — no global install needed                                                                  |
| Android SDK | platform 36   | For `composeApp`'s Android target. Point Gradle at it via `sdk.dir` in `kmp/local.properties` (gitignored) |

```bash
git clone <repo> && cd vinto
npm ci                 # TypeScript side
cd kmp && ./gradlew --version   # bootstraps Gradle 8.14 on first run

# Android only: tell Gradle where the SDK is (local.properties is gitignored).
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## 3. Module map (`kmp/`)

| Module          | Targets                                                 | Purpose                                                                                                    |
| --------------- | ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `shared:shapes` | jvm, js, (iosArm64, iosSimulatorArm64 on macOS)         | Types + `Prng`. The port starts here. Its tests run on every target above.                                 |
| `worker`        | js                                                      | Cloudflare Worker + `Room` Durable Object. Kotlin room logic under a thin JS shim in `worker/cloudflare/`. |
| `composeApp`    | android, wasmJs, (iosArm64, iosSimulatorArm64 on macOS) | Compose UI — one `commonMain` for all three clients. Still the gate payload UI; real screens are phase 7.  |
| `iosApp`        | —                                                       | Xcode project embedding `composeApp`'s `ComposeApp` framework. macOS only.                                 |

The full intended layout is in design D1. Modules are added as they are ported rather than
scaffolded empty.

## 4. Commands

```bash
# --- Kotlin (run from kmp/) ---
./gradlew :shared:shapes:allTests             # PRNG parity on every target (JVM, JS, iOS sim)
./gradlew :shared:shapes:jvmTest              # just the JVM leg, when iterating
./gradlew :worker:jsNodeProductionRun         # PRNG self-check (prints the gate number)
./gradlew :composeApp:wasmJsBrowserDistribution   # build the Compose web bundle
./gradlew :composeApp:assembleDebug           # Android APK
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # just the iOS framework
./gradlew build                               # everything available on this host

# --- iOS app (run from kmp/iosApp/) ---
# The Xcode build invokes Gradle itself, via its "Build Kotlin framework" phase.
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build

# --- Cloudflare Worker (run from kmp/worker/cloudflare/) ---
# Build the Kotlin bundle first: the shim imports it out of build/compileSync/.
(cd ../.. && ./gradlew :worker:jsProductionExecutableCompileSync)
npx wrangler dev --port 8787 --local          # local workerd; deploys nothing
node gate-two-clients.mjs                     # platform gate 2a.3
npx wrangler deploy --dry-run --outdir /tmp/w # measure the real Worker bundle

# --- TypeScript (run from repo root) ---
npm test                                      # all 5 projects
npx nx run-many --target=typecheck --all --skip-nx-cache
npm run recordings:replay -- fixtures/recordings    # parity gate via CLI
npm run recordings:generate -- --games 5 --seed 1   # ~75 s per game
```

## 5. iOS bring-up

iOS targets are declared behind a host check in `kmp/shared/shapes/build.gradle.kts`, so
they activate automatically on macOS.

**Why this was deferred until a Mac existed**: Kotlin/Native cannot build Apple targets on
Windows at all. It is a hard toolchain limitation, not a configuration gap.

### Step 1 — first compile ✅ done

`./gradlew :shared:shapes:build` compiled `iosArm64` and `iosSimulatorArm64` for the first
time. `Prng` is pure Kotlin with no platform APIs and needed **no changes**, so the
toolchain is sound and nothing in the shared code was Windows-shaped.

### Step 2 — parity on iOS ✅ done

`PrngVectorsTest` now lives in `commonTest` and runs on JVM, JS/Node and the iOS simulator —
6 tests on each, all green. Verify with `./gradlew :shared:shapes:allTests`.

Getting the fixture onto a target with no filesystem needed a decision. The options were to
bundle `vectors.json` as a Kotlin/Native resource, or to embed it. **Embedding won**, but
not by transcribing the numbers into Kotlin — a hand-copied table can drift from the file
TypeScript reads, and the parity test would then pass while proving nothing.

Instead, the Gradle task `:shared:shapes:generatePrngVectorsSource` reads
`fixtures/prng/vectors.json` and emits it as a Kotlin string constant. So:

- there is still exactly **one** shared file, and it is a declared task input, so changing it
  regenerates the constant — no second copy to keep in sync
- the JSON is embedded **verbatim**; the test parses the same bytes with the same serializer
  it used before (a round-trip check confirmed the embedded literal is byte-identical to the
  file)
- the wiring was verified negatively: perturbing one `finalState` in the fixture by 1 made
  the iOS test fail, so the check is not vacuous

Resource bundling was rejected because Kotlin/Native test binaries have no straightforward
resource access without an extra plugin, and it would have bought nothing over this.

### Step 3 — `composeApp` on Android and iOS ✅ done

`composeApp` now has `androidTarget()`, `wasmJs` and (on macOS) both iOS targets, with the
UI in `commonMain` and one small entry point per platform:

| Platform | Entry point                                  |
| -------- | -------------------------------------------- |
| Android  | `androidMain/.../MainActivity.kt` + manifest |
| iOS      | `iosMain/.../MainViewController.kt`          |
| Web      | `wasmJsMain/.../Main.kt`                     |

`kmp/iosApp` is a plain Xcode project whose "Build Kotlin framework" phase shells out to
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`, so building in Xcode builds the
Kotlin side too. `ContentView.swift` wraps `MainViewController()` from the exported
`ComposeApp` framework — renaming either side breaks the other, and nothing checks that for
you.

Verified by **running**, not just compiling: the same `App()` renders on the iOS simulator
("Running on: iOS 26.5") and on an Android emulator ("Running on: Android 34"). The
`platformName()` `expect`/`actual` exists precisely to make that visible — it is the cheapest
proof the multiplatform wiring is real before storage and clocks depend on it.

Two things that will bite anyone repeating this:

- Compose Multiplatform's Android artifacts are AndroidX, so `android.useAndroidX=true` is
  required in `gradle.properties`; without it the build fails at `checkDebugAarMetadata`.
- A headless emulator (`-no-window`) returns an all-black `screencap` under the default GPU
  mode. Use `-gpu swiftshader_indirect` if you need a screenshot; the app itself was fine,
  only the framebuffer was.

### Step 4 — revisit the host check ✅ done

Left conditional, but no longer silent. Making the Apple targets unconditional would just
turn a non-Mac build into a hard toolchain failure, which helps nobody. Instead both
`shared/shapes` and `composeApp` now `logger.warn` when they skip the iOS targets, so a host
that cannot compile them says so rather than quietly building less than you asked for.

The underlying hazard is unchanged and worth restating: a `commonMain` change that breaks
iOS cannot fail on a non-Mac host. Build on macOS before trusting a shared-code change.

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

- **The Compose/Wasm production compile needs more heap than the Kotlin daemon's default.**
  On an 8 GB Mac `:composeApp:compileProductionExecutableKotlinWasmJs` failed with "Not
  enough memory to run compilation" — while the same task had succeeded on a larger machine
  with no `gradle.properties` at all. `kmp/gradle.properties` now pins
  `kotlin.daemon.jvmargs=-Xmx3g` and `org.gradle.jvmargs=-Xmx2g` so the build does not
  silently depend on how much RAM the developer happens to have. If it still OOMs, raise the
  Kotlin daemon figure first, and run `./gradlew --stop` after changing either value —
  a running daemon keeps its old heap.
- **`android.useAndroidX=true` is mandatory**, not a preference: Compose Multiplatform's
  Android artifacts are AndroidX, and without it the build fails at `checkDebugAarMetadata`.
  It lives in `kmp/gradle.properties`.
- **A headless emulator (`-no-window`) screenshots all black** under the default GPU mode —
  the app is running fine, the framebuffer just is not. Boot with `-gpu swiftshader_indirect`
  when you need `adb exec-out screencap` to show anything.
- **Backticked test names with spaces are JVM-only.** Kotlin rejects them for JS and Native,
  so anything in `commonTest` must use camelCase names. This is why `PrngVectorsTest` reads
  `reproducesEveryPublishedShuffle` and not `` `reproduces every published shuffle` ``.
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
cd kmp && ./gradlew :shared:shapes:allTests                # expect 6 PRNG tests per target
./gradlew :worker:jsNodeProductionRun                      # expect "gate ok: rngState=2583707619"
```

That last number is the useful one: `rngState=2583707619` after shuffling 54 cards with
seed 42 is produced by **both** implementations. If it differs, the cross-language contract
has broken and nothing above it can be trusted.
