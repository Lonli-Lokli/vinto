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

- **`shapes` is ported** (task 3.1): `Card`, `Rank`, an immutable `Pile`, `GameState` and
  every nested type, all enums carrying their TypeScript string values, `CARD_CONFIGS`, plus
  the canonical JSON writer, a pure-Kotlin SHA-256 and `hashGameState`. It now builds for
  **jvm, android, js, wasmJs and iOS**, so `composeApp` can depend on it.
  The gate: all 50 recordings in `fixtures/recordings/` carry a `finalStateHash` written by
  TypeScript, and Kotlin reproduces every one of them (§6a)

- **`GameAction` is ported** (task 3.2): all 25 action types as a sealed hierarchy, with the
  `{ type, payload }` wire shape built by hand — kotlinx's own polymorphism puts its
  discriminator beside the payload's fields rather than above them. Every one of the 13,900
  recorded actions round-trips to the same canonical form

- **The engine is ported and passes the parity gate** (phase 4): all 25 handlers, and
  **all 50 recordings / 13,900 actions replay with canonical state hashes matching
  TypeScript's**, per action, plus final-state verification (§6b)
- **detekt** runs over every Kotlin module with `maxIssues: 0`
- **`ActionValidator` is ported**, and tested by re-attributing every seat-bound action in the
  corpus to all three other players — 18,066 attempts, none accepted (§6b)
- **The engine runs correctly in the Cloudflare runtime**, not just on the JVM: the Worker
  exposes `POST /replay` and all 50 recordings replay through it in workerd (§6c)

- **The bot is ported and follows the rules** (phase 5): all of `packages/bot` — memory,
  opponent modeller, heuristics, evaluators, determinization, rollout policy, move generator,
  state transition, outcome simulator, Vinto round solver, coalition planner, MCTS decision
  service — plus `BotRunner`, which turns decisions into actions for a server that has no UI.
  **Decision parity with TypeScript was not required and was not attempted**; rule-following
  was, and is gated: four Kotlin bots play whole games through the real engine with every
  proposed action passing `ActionValidator` first, and games must reach `scoring` (§6e)

- **The room runs the real game**: the `Room` Durable Object deals from a seed, validates
  every action, checks the seat boundary above it, sends each socket its own redacted view,
  and plays the bots server-side. Verified two ways — `gate-real-room.mjs` in plain Node for
  the game questions, and `gate-two-clients.mjs` through workerd for sockets, hibernation and
  reconnect (§6f)

- **The platform gate is closed.** 2a.1b was the last open item and it passes: the worst
  request observed costs 1.6 s of a Durable Object's 30 s budget (`PLATFORM-GATE.md`)

**Next**

1. Open the room in a deployment: set `ROOM_OPEN` to `"true"` (§6d). The engine reason for
   keeping it shut is gone; what remains is the operator's call about who may create rooms
2. Phase 6: `GameSession` / `LocalGameSession`, the recorder, and the Koin wiring — the
   client-side half that the Compose UI will sit on
3. Phase 7: the Compose Multiplatform UI

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
| `shared:engine` | jvm, android, js, wasmJs, (iOS on macOS)                | `GameEngine.reduce`, toss-in and scoring utils, the replay harness. Partly ported — see §6b.               |
| `shared:bot`    | jvm, android, js, wasmJs, (iOS on macOS)                | MCTS decision service, coalition planner and `BotRunner`. Reads only what a seat may see.                   |
| `shared:client` | jvm, android, js, wasmJs, (iOS on macOS)                | `GameSession` and `LocalGameSession` — a solo game with no room and no socket. See §6d.                     |
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
./gradlew :composeApp:installDebug            # ...onto a connected phone or emulator
./gradlew :composeApp:assembleRelease         # release APK; debug-signed unless §6f says otherwise
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

## 6a. How the ported `shapes` is verified

Three layers, weakest to strongest:

1. `Sha256Test`, `CanonicalJsonTest`, `PrngVectorsTest` — unit tests in `commonTest`, so they
   run on **all five targets** (jvm, android, js, wasmJs, iOS simulator). They pin the rules
   that the corpus cannot reach: the `botMemory` exclusion is never present in a real
   recording, so it is asserted directly.
2. `RecordingParityTest` (JVM only, it reads the 4.5 MB corpus from disk) — decodes each of
   the 50 recordings' `finalState` into the Kotlin model with `ignoreUnknownKeys = false`,
   so an unmodelled field is an error rather than a silent drop. It also round-trips all
   **13,900** recorded actions through `GameActionSerializer`, comparing canonical forms.
3. The same test re-encodes that state, canonicalises and hashes it, and compares against the
   `finalStateHash` **TypeScript wrote**. One number covers lossless decode, correct
   optional-versus-nullable handling, a byte-identical canonical form, and SHA-256 agreeing
   with WebCrypto. It cannot pass by accident.

Confirmed non-vacuous: reversing the canonical key sort fails it, dropping `turnActions`
from the exclusions fails it, and forcing an unset optional to encode as `null` fails the
action round-trip. (Dropping `botMemory` does **not**, which is why layer 1 exists.)

The corpus reaches 17 of the 25 action types. The other eight, and the `rank: 'A'` variant
of `SELECT_ACTION_TARGET` that appears in no recording, are pinned by layer 1 instead —
which is the general shape of this: the corpus proves agreement on what real games do, and
the unit tests cover what they happen not to.

One deliberate check worth keeping: the optional-field rule is carried by the
`@EncodeDefault(NEVER)` annotations alone, not by `VintoJson`'s configuration. Flipping
`encodeDefaults` to `true` leaves every parity test green, so a call site that builds its own
`Json` cannot start emitting `"declaredRank":null` where TypeScript writes nothing.

**Deviation from design D1**, recorded deliberately: canonical JSON, SHA-256 and `Prng` live
in `shared/shapes` rather than a `shared/recording` module, because that is where TypeScript
keeps them and the port is file-for-file (D3). Revisit when the `GameRecording` model lands.

## 6b. The engine port, and how it is being driven

The harness came first, not last. `CorpusReplayTest` replays TypeScript's recordings through
the Kotlin engine and compares the canonical state hash **after every action**, so a wrong
handler is localised to one action rather than showing up as "the final state differs".

While the port was in progress this ran as a ratchet that named the frontier on every run,
so the next handler to write was never a guess. It is now a **hard gate**: all 50 recordings,
all 13,900 actions, plus final-state verification.

Confirmed non-vacuous — deleting one line of knowledge tracking from the Jack swap (the owner
must _lose_ knowledge of a blind-swapped position) fails it.

### The validator needed a different kind of test

The corpus cannot check `ActionValidator` at all: every action in it was legal when recorded,
so a validator that returned `Valid` unconditionally replays all 13,900 identically. Replaying
with the real validator live therefore proves one direction only — that nothing legal is
rejected.

The other direction is the one that matters, since this is the anti-cheat boundary D9 rests on.
`ValidatorImpersonationTest` gets it from the corpus anyway: replay every recording and, at
each step, re-attribute the action that genuinely happened to **every other player at the
table**. Each is an attempt to act out of turn in a position that actually arose.
**18,066 attempts, none accepted.**

Rule-specific cases the sweep cannot reach — the coalition may not target the Vinto caller, a
failed toss-in ends participation for the round, setup peek limits — are posed against corpus
states in `ValidatorRulesTest` rather than hand-built ones: a fabricated state proves a branch
is reachable, a real one proves the rule bites where it matters. Deleting either rule fails its
test.

**Why the handlers mutate.** The TypeScript handlers deep-copy the state and then mutate it
freely. Rewriting each into immutable `copy()` chains would be better Kotlin and a worse
migration — the parity gate cannot tell a faithful restructuring from a subtly wrong one. So
`MutableGameState` is a working copy that handlers mutate exactly as their TypeScript
counterparts do, and `reduce` freezes it on the way out. `reduce` stays a pure function; the
mutation never escapes the call. Rewriting handlers idiomatically later is safe precisely
because the gate holds the behaviour still.

## 6c. Hosting on kupalinka.app

Vinto is hosted alongside the portfolio games in `~/sources/gulnya/games-portfolio-brief.md`.
Two hostnames, two deploy targets:

| host                       | what                                            | how                                            |
| -------------------------- | ----------------------------------------------- | ---------------------------------------------- |
| `vinto.kupalinka.app`      | the Compose/Wasm client                         | Cloudflare **Pages** project `vinto`           |
| `vinto-room.kupalinka.app` | the room Worker + Durable Object, and `/replay` | `wrangler deploy` from `kmp/worker/cloudflare` |

The Worker gets its own hostname rather than a path under `vinto.kupalinka.app`, because that
host is a Pages project and layering a Worker route over a Pages custom domain is a precedence
puzzle nobody should re-derive during an incident. It is still **same-site**, so the client's
CSP needs one `connect-src` entry on its own site. `px.kupalinka.app` is a separate Worker for
the same reason.

**Compose for Web is an exception to the portfolio convention**, which says plain DOM over
`:engine` and never Compose. Taken deliberately, with the 3.7 MB measurement in hand; the
reasoning is in design **D1a** and must not be copied to another game without reading it.

### What the exception does not excuse

These are properties of the zone and of the visitor, not of the module shape, and they apply to
`vinto.kupalinka.app` exactly as they do to every other game:

- **Every script the page reaches carries a content hash.** The `kupalinka.app` zone's Browser
  Cache TTL _overrides_ a weaker origin `Cache-Control`, so under fixed names a stale script
  keeps naming a wasm binary the next deploy replaced — a 404 and a dead page, not a stale one.
  This has taken portfolio sites down before; treat it as a hard invariant, not a preference.
- **A newly-deployed asset fetched at its canonical URL before the edge has it** returns the
  Pages SPA fallback: `index.html`, **200**, `text/html` — then cached `immutable` for a year by
  a path-matched `_headers` rule. Content-addressing makes that permanent rather than momentary.
  Probe with `?cb=`, and never point a headless browser at a fresh deploy.
- **Usage counting from the loader**, not the bundle, so a visitor whose browser cannot run
  WasmGC still counts. No cookie banner, an opt-out control, GPC/DNT honoured.
- **The §3b gate**: responsive at 1440px as well as 380px, keyboard-complete, focus and scroll
  surviving re-render, both themes at WCAG AA, `prefers-reduced-motion` honoured.

### The shared machinery does not currently reach Vinto

The first three of those live in `~/sources/gulnya/web-template/`, which exists precisely
because copying them by hand went wrong: the brief records Niva shipping a deploy script with
**no chain verification at all**, printing a green tick over a dead site, months after Vodar's
grew that check. Nothing was wrong with either file — "copy this verbatim" is an instruction to
a person, and people copy things once.

`sync.mjs` mirrors a `web/` module layout. Vinto's web build is `kmp/composeApp` with a Gradle
root one directory down, so it cannot participate as-is, and hand-copying `content-hash.js` and
`web-deploy.sh` into this repo would make Vinto the **third** copy — exactly the outcome the
brief warns about, and it names that as the moment to stop copying and move the verification
into versioned tooling instead.

Unresolved on purpose: it means editing shared tooling that two shipped games depend on, which
is not a change to make casually or as a side effect of hosting Vinto. Until it is resolved,
`vinto.kupalinka.app` has no deploy script — which is survivable only because the client is not
ready to publish anyway.

### The web client is not ready to deploy

`composeApp` is still the platform-gate tap counter. Publishing `vinto.kupalinka.app` today
would publish that. Phase 7 builds the real UI.

## 6d. Deploying the engine, with no UI

The Worker carries the real engine and exposes `POST /replay`: send a `GameRecording`, get
back `ok` or the exact action that diverged. That is enough to verify the engine on a real
deployment before any UI exists.

```bash
cd kmp/worker/cloudflare
(cd ../.. && ./gradlew :worker:jsProductionExecutableCompileSync)
npx wrangler dev --port 8787 --local
node gate-engine-replay.mjs            # 50/50, 13,900 actions

# against a deployment
npx wrangler deploy                    # needs `wrangler login` first
GATE_URL=https://vinto-room.kupalinka.app node gate-engine-replay.mjs
```

**The first deploy needs zone permissions**, not just Workers ones: `custom_domain: true`
makes wrangler create the `vinto-room.kupalinka.app` DNS record in the `kupalinka.app` zone.

### A deployment today is a self-test, and the code enforces that

`ROOM_OPEN` defaults to `"false"`, and a WebSocket upgrade against a closed room is refused
with **503** and the reason. This is a gate in `index.mjs`, not a note here, because the
consequence of forgetting is a deployed Durable Object accepting _any_ action from _any_
client — `ActionValidator` permits everything, and design D9 puts server-side validation at the
centre of the anti-cheat model. Flip it to `"true"` in the same commit that lands the
validator, never before.

What a deployment does answer:

| endpoint          |                                                                             |
| ----------------- | --------------------------------------------------------------------------- |
| `GET /health`     | `{"ok":true,"service":"vinto-room","engine":"kotlin","roomOpen":false}`     |
| `POST /replay`    | replays a `GameRecording` through the real engine; bodies over 1 MB refused |
| WebSocket upgrade | 503, with the reason                                                        |

`/replay` is a pure function of the posted document — it holds no state and mutates nothing —
which is what makes it safe to expose while the validator is missing. The 1 MB cap is there
because it is public and CPU-bound at roughly 1 ms per action; the largest recording in the
corpus is 141 KB.

Tearing it down is `npx wrangler delete`, so this is a cheap thing to try and an easy thing to
undo.

### What the first real deployment taught, that local could not

Deployed 2026-08-19 to `vinto-room.kupalinka.app`. All 50 recordings and 13,900 actions replay
on the live edge. Three things surfaced that `wrangler dev` had no way to show:

- **`/replay` belonged in the Durable Object, not the Worker.** A plain Worker gets ~10 ms of
  CPU per invocation; a Durable Object gets 30 s per request. Replaying one game costs ~250 ms,
  so production answered `error code: 1102` — the exact limit design D9 cites as the reason the
  room is a Durable Object at all. `wrangler dev` enforces no CPU limit whatsoever, and the
  production limit is applied on a rolling average, so single requests passed while a batch did
  not. D9 was right; the endpoint was in the wrong place.
- **A plain `GET /?room=anything` created a Durable Object and wrote it to storage**, for any
  name a stranger cared to invent, and read it back. Locally that was a reasonable inspection
  aid for the 2a.3 harness. The `ROOM_OPEN` gate now sits above it, so a closed deployment
  creates nothing and discloses nothing. The only thing that had changed about the code was
  that it was on the internet.
- **Propagation is not atomic, and it caught me twice.** Verifying seconds after `wrangler
deploy` returned the _old_ behaviour and sent me chasing a second bug that did not exist; then
  one probe seeing the new behaviour did not mean every edge node had it. Poll until the
  behaviour changes, then keep checking. Same hazard the portfolio brief documents for Pages
  deploys, in a different costume.

Run `GATE_URL=https://vinto-room.kupalinka.app node gate-engine-replay.mjs` after any deploy.
It backs off on 503 and **counts** the retries, so throughput pressure stays visible instead of
being quietly absorbed.

Why this is worth having rather than trusting the JVM gate: Kotlin/JS represents `Long` as a
pair of `Int`s and uses a different serialiser backend, so passing on the JVM does not imply
passing on Cloudflare. It now passes on both.

**What must be true before a deployment takes real client input.** `/replay` is safe to
expose — it is a pure function of the posted document. The WebSocket room is not yet, but the
reason has changed: `ActionValidator` is now ported in full and is the boundary design D9
depends on, so what is missing is that the `Room` Durable Object still runs placeholder logic
instead of calling `GameEngine.reduce`. `ROOM_OPEN` stays `"false"` until it does.

Deploying needs a Cloudflare account and `wrangler login`; `wrangler deploy` should be a
deliberate decision rather than a side effect of a build.

## 6e. The bot, and what a self-play gate is for

The bot is ported in full, and the verification is worth explaining because it is not the one
the other phases use.

The engine had a corpus: 50 recorded games with per-action hashes, so "did the port work" has
an exact answer. **A bot has no such thing.** Its output is a decision, and two reasonable
bots disagree constantly without either being wrong. Demanding decision parity would have
meant transcribing every heuristic literally including its bugs, and would still not have
produced it — MCTS is stochastic, and the two implementations sample different random streams.

So the requirement was set differently: *the bot need not follow the TypeScript exactly, but
it must follow the rules.* That is checkable. `SelfPlayGateTest` plays whole games with four
Kotlin bots through the real `GameEngine`, and every action a bot proposes goes through
`ActionValidator` before it is reduced — the same boundary a Durable Object runs, so anything
rejected here would be rejected in a live room.

Three things it asserts, each for a different failure:

| assertion            | catches                                                             |
| -------------------- | ------------------------------------------------------------------- |
| every action is legal | a bot that cheats, or that gets stuck holding an action the engine refuses |
| every game reaches `scoring` | a game where each action is legal but two states hand back and forth forever |
| some game ends on a Vinto call | an endgame that is unreachable in practice, so games only end when the deck dries up |

It earned its keep immediately, finding five defects that no unit test would have:

- **Memories outlive the hands they describe.** A toss-in removes a card and renumbers
  everything after it; the memory keeps its old index. A shrunken hand therefore "remembered"
  a card past its own end, and the move generator offered it as a target the engine rejects.
- **A tossed-in Jack or Queen was a dead end.** The validator allows `selecting` — where a bot
  sits while resolving a tossed-in action — for `CONFIRM_PEEK`, `DECLARE_KING_ACTION` and
  `SELECT_ACTION_TARGET`, and forbade it for exactly these two swaps. A bot could choose both
  targets and then had no legal move at all. The same hole is in
  `packages/engine/src/lib/action-validator.ts`.
- **Cached action plans could go stale**, since they are read a ply deep in the search tree.
- **Target selection answered the wrong question** once the engine had already committed a
  card: "would I rather swap?" is no longer on the table at that point.
- **The search could not see the deck run out** — `deckSize` was hardcoded to a full deck.

**One dead end is deliberately left open.** The draw pile is refilled when a turn ends with
one card on it, but a forced draw or a wrong-declaration penalty takes a card *without* ending
a turn, so it can reach zero — and a turn that starts with no deck and nothing takeable on the
discard has no legal move. Refilling at zero as well looks like a one-word fix and is not:
`reshuffleFrom` advances `rngState` whether or not it moves anything, so recording
`selfplay-moderate-18` diverges at action 322. Corpus parity is worth more than closing a rare
dead end; the engine is untouched and `BotRunner` reports the position rather than proposing an
illegal draw.

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
| Bot verification is rule-following, not decision parity                     | §6e, tasks 5.5/5.6                        |
| One decision service **per bot**, not one shared across seats               | `BotRunner`; TypeScript wipes memory each turn |

## 6d. Single-player runs on the device

A solo game creates **no room, no token and no socket**. `shared:client` holds a `GameSession`
interface that a local game and an online one both implement, so a screen cannot tell which it
has — which is what keeps the free single-player mode free to host, rather than a Durable
Object running three MCTS searches a turn for one person.

`LocalGameSession` is the engine and `BotRunner` in-process. It reads the same redacted
`PlayerView` the server sends, validates through the same `ActionValidator`, and enforces the
same seat boundary from the same `GameAction.actorId` the Durable Object uses. A local game
that let the UI act for a bot would be teaching the UI a habit that fails online.

The claim is gated rather than asserted: `NoNetworkGuardTest` plays a whole round with a
`SecurityManager` installed that throws on any connect, listen or accept, and proves the guard
bites — three deliberate calls that must fail — before trusting the round that follows.

Two things that gate turned up, both faithful ports of TypeScript behaviour that only a UI was
keeping shut, and both fixed:

- the validator had no **phase** gate, only turn and sub-phase checks, so `DRAW_CARD` passed
  during setup and again after scoring. Never reachable from a button; entirely reachable from
  a socket.
- `PEEK_SETUP_CARD` validated the player it *named* rather than the one acting, so one player
  could spend another's setup peeks.

## 6f. Putting it on a phone

The solo game is playable on Android today — `./gradlew :composeApp:installDebug` and it is on
the device. What was missing was everything around the game rather than in it, and four of
those are now done. None of them is the Play release (task 8.1 proper): there is still no CI,
no upload key, no track.

**A launcher icon.** The web app's own orange V (`apps/vinto/public/favicon.png`), regenerated
into the three shapes Android has asked for over the years by
`kmp/tools/make-launcher-icons.py` — adaptive for API 26+, the legacy square/round pair for the
24–25 the app still supports, and a monochrome layer for themed icons on API 33+. The generated
PNGs are committed; nothing at build time runs the script. It is the same mark as the browser
deliberately: a different icon for the phone would make it a different game to anybody who has
played both.

**Portrait only.** The table sizes itself from the height it is given and has two sizes to step
down through, not a continuum (`CardScale.kt`); a phone in landscape has less height than the
smaller of them needs. Android 16 ignores the lock on large screens, which is the right place
to ignore it. Landscape as a supported layout is task 7.6.

**A window theme of its own** (`values/themes.xml`). It was inheriting
`Theme.Material.Light.NoActionBar`, which meant dark status-bar icons over a dark rail and a
white flash before the first composition. `Theme.Vinto` is dark Material with the rail as its
window background, so the bars carry the light icon set by inheritance rather than by
overriding a per-API flag, and the cold-start frames are the colour of the app.

**A release variant that assembles anywhere.** `assembleRelease` signs with the upload key
named by `kmp/keystore.properties` when that file exists, and with the debug key when it does
not. The fallback is the point: a release build that fails on a missing secret is one that goes
untested until the day it has to work. A debug-signed release APK installs and plays; it cannot
be published, and cannot be upgraded in place by a properly signed build later, because Android
treats a change of signing key as a different app.

To sign it properly, create the key once and write `kmp/keystore.properties` (gitignored, and
it names the keystore rather than containing it):

```bash
keytool -genkeypair -v -keystore ~/keys/vinto-upload.jks -alias vinto \
  -keyalg RSA -keysize 4096 -validity 10000
```

```properties
storeFile=/Users/you/keys/vinto-upload.jks
storePassword=...
keyAlias=vinto
keyPassword=...
```

## 6g. The menu, the settings and the lesson

The app opened straight onto a title and two buttons, because single player was the only
thing there was. It now has a front door, and three things behind it.

**Home.** A fan of five real cards from the game's own deck deals itself in behind the
wordmark, and under it a panel holding the mode that is finished: single player, its
difficulty on show rather than buried, and one button to a table. Below that, the three
things that are not a game — online, the lesson, the settings.

The arrangement follows what the premium card apps have converged on rather than what a
settings-first Android app does. Two ideas were worth taking. Marvel Snap's UI team put it
as *the cards take precedence in the visual hierarchy and the interface exists to highlight
them*, which is why the menu is made of the deck instead of illustrated with it. The poker
lobbies (Zynga, PokerStars) are built so the table everybody came for is one tap away and
nothing is between you and it — hence the difficulty chips sitting *in* the play panel
rather than behind a settings screen.

**Online is a button that works.** It opens a dialog saying what actually exists — a Worker
with a Durable Object per room, running this same engine, which two clients have already
joined and played through — and what does not, which is this app's half. A greyed-out
"coming soon" answers nothing; the question is a fair one and it has a real answer.

**Settings** are four choices, each written as what it *does*:

- **Bots**, the difficulty, shared with the home panel.
- **Pace** — calm, steady, brisk — a single multiplier over every duration in the animation
  layer, so the movements and the pauses keep their proportions at either end of the dial.
  It exists because the right speed is not the same for somebody learning the game and
  somebody on their twentieth round.
- **Theme**, because a phone's night setting is not always the one you want at a table.
- **Haptics**, and see below.

They live under their own key in the same vault as the saved game, deliberately not inside
`SavedGame`: a preference outlives the round it was set in, and abandoning a game must not
reset a speed the player has already decided they dislike.

**How to play is a real round, on a deck somebody arranged.** Not a page of rules, and not a
scripted walk with every button but one disabled — a tutorial that refuses your moves teaches
the sequence rather than the game, and has to say no the first time you deviate.

The design came out of a session with the `fable` model against the repo (`VINTO_RULES.md`,
`SCENARIOS.md`, the client sources); what shipped follows it closely. Five parts:

- **A stacked deal.** `initializeTeachingGame(deck)` takes a deck order instead of a seed, and
  refuses anything that is not a permutation of the real 54 cards — so the lesson cannot deal a
  hand that could not have been shuffled. `TeachingDeal` writes the order down: a 7 and a Joker
  to peek at, an 8 to throw in later, a plain 4 to draw first, then a Queen, a King and finally
  a Joker on the last turn. Seed search was considered and rejected: the constraints are joint
  over a dozen named positions, and a seed found today would be silently invalidated by the
  next bot or engine change. The recording contract is untouched, because `GameRecording`
  carries `initialState` in full and `replayRecording` starts from it.
- **A director for the bots.** The deck says what a bot *draws*, not what it does. A
  `BotDirector` on `LocalGameSession` may name a bot's move before the search is asked;
  whatever it names still passes `ActionValidator`, and a refused move falls through to MCTS —
  a script that has drifted costs the lesson its shape, not its playability. It does two
  things: bots put drawn cards down rather than swapping them into hand, and, once the round
  has taught what it can, **Don calls Vinto** — so the final round, the coalition and the
  scoring are played rather than described. Left alone a bot will not call inside a short
  round: the rule wants eight rotations, a fully known hand and a total of zero or less.
- **A coach derived from the position.** `lessonFor(view, table, taught)` is a pure function:
  it reads the table and says what is in front of the player. That is what lets every legal
  move stay legal — deviate and it simply talks about wherever you got to. Ordered talk beats
  are the exception (the object of the game, the tour, the Vinto call, the coalition, the
  scoring), because explaining scoring before the round is scored is not explaining.
- **A pointing hand.** One white outlined arrow, at one thing, from just outside it: a card, a
  seat plate, a rank chip, a button, the deck badge, the log box, the "?". White because every
  other colour on this table already means something. It points down from above by default —
  pointing up from below sits it on the next rank chip in the grid, naming the wrong card — and
  from inside the left end of a full-width button, where it cannot cover the button below.
- **Cards explained as they are met, with the card.** The first time a rank becomes visible,
  the coach gives its name, value and action in `CARD_CONFIGS`' own words — beside the actual
  picture, dealt from the same art the table uses. The help sheet's gallery does the same, as
  the web app's card reference already did: a player who learned "Q" from a list still has to
  match it against a picture on the felt, and showing the picture skips that step.

The director has one more job than "make the bots play their parts": the seat immediately
before the player **draws rather than takes**, so that an unused action card is still on the
pile when the player's turn comes round and the second way to start a turn can be shown. A bot
taking it first is correct play, not a fault, which is why the lesson teaches that rule in
words either way and points at it when the round allows. The Vinto call is timed on
`turnNumber`, which counts *turns* and not rotations — the first version called it on turn 4,
which is the third bot's **first** turn, and ended the lesson before the player had taken a
second one.

It also makes **one bot throw a card into a toss-in window**, once, the first time a bot is
holding a match it has actually seen. The window is the one moment in Vinto that belongs to
the whole table at once, and a player whose window only ever contains themselves learns it as
"a prompt I dismiss"; when it happens the coach names whoever did it. "Has actually seen" is
the bots' own rule rather than a convenience — guessing costs a penalty card and bars you from
the rest of the round, so a bot tossing a card it had not read would be demonstrating bad play.

The coach also answers, once and without reproach, the first time somebody presses a button
other than the one being pointed at. That press is a player quietly testing whether this is a
real game or a rail, and it deserves an answer.

Two things are deliberately not free: the lesson runs at no less than **calm** pace whatever
the setting says, and **Call Vinto is hidden until a bot calls it** — the one tap that ends the
lesson before it starts, cannot be undone, and means nothing yet to the person pressing it.

The coach sits in the control panel's reserved height rather than in a strip above the table.
Stacking it above cost the felt 150 dp and the side seats' hands re-flowed into rows — the
lesson was being taught on a table that was not the one being learned. It is bounded and
scrolls inside its own box, so a King's fourteen rank chips plus a three-line prompt plus a
lesson cannot squeeze the felt out of existence.

**Haptics.** Three kicks and no more: something touched, a move committed, a rule bitten —
that last only for the hand it happened to, since a buzz for a bot's penalty is a buzz for
something that is not your problem. Off is one setting away, which is what keeps the three
that remain meaningful.

**Back works.** `SystemBack` is an `expect`/`actual` around Android's `BackHandler`; the other
targets no-op and use the on-screen button. Without it, back from the settings screen closed
the app, which looks exactly like a crash.

## 6h. Words, and where they live

Every string the **UI module** says is now in
`kmp/composeApp/src/commonMain/composeResources/values/strings.xml`. A translation is a file
beside it — `values-be/strings.xml`, `values-uk/strings.xml` — and nothing else changes, which
is what the `translate-game` skill expects to find.

Two rules for that file, written at the top of it as well: name a string for what it *says*
rather than where it sits, and never build a sentence out of two of them. Word order is not
universal, and a translator handed half a sentence cannot fix the half they were not given.

Accessibility descriptions went in too. A screen reader announcing "a face-down card" in a
Belarusian game is the same failure as an untranslated button.

The choice labels moved off the enums. `Difficulty.serialName` is a **wire value** — it is in
every saved game and every recording — and capitalising it to put on a button worked exactly as
long as the app was English. `Labels.kt` maps each enum to a resource instead.

### What is still English, and why it is harder

Roughly two hundred strings remain in `shared/client`, and they cannot simply move: that module
has no Compose, and its copy is not written as sentences to translate but *assembled* from
grammar.

- `Narration.kt` conjugates verbs — `youForm`/`theyForm`, "You draw" against "Raph draws". In
  Belarusian or Ukrainian that is not a suffix swap; it is a different sentence.
- `TableModel.kt` builds prompts and button labels with interpolation ("A 7 went down — toss in
  a match?"), and the pointer keys off those labels.
- `TeachScript.kt` is the lesson, which is mostly prose.
- `CardConfig.kt` in `shared/shapes` carries the card names and descriptions, ported verbatim
  from TypeScript so both clients teach the same game.

The fix is the same in each case and it is **not** a string table in a non-UI module: those
functions should return a *typed message* — the id and its arguments — and the UI should render
it from resources. `Table.prompt: String` becomes something like `Table.prompt: Say`, and the
tests get better rather than worse, because asserting `Say.YouDrew(SEVEN)` says what is meant
where asserting an English sentence says what it currently reads.

Until that lands the app is half-translated: menus, settings, the score sheet, the help sheet
and the spoken descriptions follow the phone's language; the table's prompts, the move log and
the lesson do not.

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
