# Tasks: migrate-to-kotlin-multiplatform

Prerequisites — the dependency on `add-game-recording-replay` is **not uniform across
phases**:

- **Phase 1 (one bot engine) — DONE.** It needed only the _determinism_ half of that
  change (`add-game-recording-replay` §1: seeded PRNG, `rngState`, seeded
  `initializeGame`) to produce seeded self-play games. It had to land **before** that
  change commits its fixture corpus (its task 3.5), because phase 1 removes `botVersion`
  from `GameState` and so changes every canonical hash; committing the corpus first would
  have forced regenerating all ≥ 50 fixtures. That sequencing held — no fixtures existed
  when `botVersion` was removed, so none needed regenerating.
- **Phases 2 onward** need `add-game-recording-replay` fully implemented and **archived**
  (recording format v1, `fixtures/recordings/` corpus, PRNG test vectors at
  `fixtures/prng/vectors.json`, `RECORDING.md`) — the Kotlin port has no fixed target
  before then.

## 1. One bot engine (TypeScript, before any port)

Runs after `add-game-recording-replay` §1 (determinism) and before its §3.5 (corpus commit).
The headless seeded self-play runner built here is the same harness
`tools/generate-recordings.ts` needs — build it once and share it.

- [x] 1.1 Headless seeded 4-player bot-vs-bot runner. Built as `tools/generate-recordings.ts` (run with `npm run recordings:generate`) rather than a separate `headless-selfplay.ts`: the tournament was cancelled, so the fixture generator is the only consumer and a second harness would have been dead code
- [x] 1.2 / 1.3 **Tournament cancelled — decision recorded in `docs/bot/BOT-ENGINE-DECISION.md` in place of a tournament report.** v2 read opponents' hidden hands in three places (`strategic-bot-decision.ts` Vinto check, threat assessment at :549, target selection at :835), each explicitly skipping the bot itself and then calling `calculateScore(player.cards)`; v1 uses `estimatePlayerScore` from `BotMemory`. A head-to-head result would have measured how much cheating is worth, and could not have justified deleting the honest bot. Measured cost was also ~75s/game (500 games x 3 difficulties ~= 31 hours), and `BotAIAdapter` holds one decision service for all four seats (`botAIAdapter.ts:96`), so mixed tables would have required changing production code purely to run the tournament
- [x] 1.4 Deleted v2 (`strategic-bot-decision.ts`) and `botVersion` throughout: `domain-types.ts` (`BotVersion`), `game-state-types.ts` (field), `action-types.ts` (`UpdateBotVersionAction`), `cases/update-bot-version.ts` (file), `game-actions.ts`, `game-engine.ts`, `action-validator.ts`, `bot-factory.ts` (now takes difficulty only), `botAIAdapter.ts`, `initializeGame.ts`, `GameRecordingSettings`, both UI selectors, and all test helpers. No fixtures needed regenerating — the corpus was deliberately sequenced after this
- [x] 1.5 Verified: 552 tests green across 5 projects, all 5 typecheck, lint clean, and a fresh self-play game reaches `scoring` and replays cleanly. `grep -rniE "botversion|strategicbot"` over `packages/*/src`, `apps/vinto/src` and `tools/` returns only the explanatory comment in `bot-factory.ts`
  - Side effect worth noting: installing `vite-node` bumped several loosely-ranged packages in the lockfile, including `react-error-boundary` to 6.1.3, whose v6 types widened `error` to `unknown`. `apps/vinto/.../error-boundary.tsx` was updated to use the library's own `FallbackProps` and narrow once via a `toError` helper

## 2. Workspace, tooling, CI skeleton

- [~] 2.1 Gradle workspace (Kotlin 2.1, KMP plugin, Compose Multiplatform, version catalog): **done, and it is the repository root** rather than `kmp/`. Built under `kmp/` while the web client was still the product; hoisted when it stopped being — `./gradlew` runs from the root, `fixtures/` is a sibling of `shared/`, and the Next.js workspace is retired to `legacy-web/` (design D1, `docs/kotlin/README.md` §1a). Modules present: `shared:shapes`, `shared:engine`, `shared:bot`, `shared:client`, `shared:protocol`, `shared:room`, `worker`, `composeApp`, plus the `iosApp` Xcode project. **Two from the plan do not exist and were not forgotten**: `shared:recording` (the recording model lives in `shared:shapes`, beside the canonical JSON and hashing it depends on — deviation recorded under 3.4) and `parity-tests` (the corpus suites live in the module they gate, so a failure names the engine rather than a test project). `shared:room` was added, and is not in the plan: the room's rules moved out of the Worker so the JVM could test them
- [~] 2.2 Targets: **done** — `androidTarget`, `iosArm64`, `iosSimulatorArm64` (both behind a host check, since Kotlin/Native cannot build Apple targets off macOS), `js(IR)` for the Worker, `wasmJs` for Compose web, and `jvm` for tests and tooling only. Libraries: kotlinx.serialization and kotlinx.coroutines throughout, kotlin.test everywhere, coroutines-test where a suspend API is gated. **Not used, and each for a reason rather than an omission**: Ktor client (`RemoteGameSession` speaks to the room over the platform WebSocket — one dependency fewer on every target, and wasmJs has no Ktor engine worth the weight), Koin (task 6.8, and nothing has yet needed a container), kotlinx-datetime (the engine has no clock at all, by design), Turbine (the `StateFlow` assertions read fine with `runTest` and `first()`)
- [~] 2.3 GitHub Actions: **written, never run.** `.github/workflows/kmp.yml` carries all four checks the plan asks for plus a fifth: `kmp-detekt` (every module, every source set), `kmp-jvm` (the six shared modules' JVM suites — the corpus replay and the validator), `kmp-android` (`assembleDebug` plus the Compose suites headless), `kmp-worker` (the Kotlin/JS bundle, all nine room gates — six in plain Node, three through `wrangler dev` — and the gzipped bundle measured against the 3 MB script limit), `kmp-ios` (simulator tests for the five Apple-target modules and the framework Xcode embeds). Gradle caching through `gradle/actions/setup-gradle`, written from branch pushes and read on pull requests; `~/.konan` cached on the macOS job. Jobs are path-filtered and unchained, the macOS leg is rationed to pushes, nightly, dispatch and `ios`-labelled pull requests, JDK is pinned to 17 (every module sets `jvmTarget = 17` with no toolchain), and wrangler is pinned to an exact version. Two deliberate exclusions, both documented where they are made: the golden-screenshot suite (a fresh runner would write its own goldens and pass, asserting nothing) and `nx build @vinto/game` (broken since before this branch, on a frozen workspace). **Four runs in, three of five checks green** (`kmp-detekt`, `kmp-jvm`, `kmp-worker` — the last one all nine room gates and the bundle-size budget). **First run, and what it found**: `assembleDebug`, the Kotlin/JS bundle and every action resolved on the first push; `kmp-detekt` and `kmp-worker` went red. detekt found **seven pre-existing findings** — reproduced identically against the tree *before* the move with the detekt CLI, so none is fallout from it — now listed in `config/detekt/baseline.xml`, which holds the line at today's debt and fails on anything new. The worker's plain-Node gates failed as one opaque step; they are now nine separately named steps, so the next run names the gate on the summary page instead of hiding it in a log this container cannot page through. Four pre-existing defects surfaced and were dealt with: seven detekt findings (baselined) and three room-gate assertions the room had outgrown (fixed, each reproduced locally against a real compiled Kotlin/JS bundle and confirmed identical on a worktree from before the move). **Still red**: `composeApp` does not compile for two targets — `:composeApp:jvmTestClasses` and `:composeApp:compileKotlinIosSimulatorArm64` — while `assembleDebug` passes, so commonMain and androidMain are sound. Those two source sets have never been compiled anywhere (§6i: composeApp ships "verified by `:composeApp:detekt`"), and neither reproduces in this container: androidx answers 403 from dl.google.com and there is no Mac. Attribution there is by reasoning rather than by experiment — the only edits this work made to `composeApp` are comments and one `Test`-task filter, and the steps that fail are compilations of source sets it did not touch. The compiler's message is in the job log; reading it is the first item of §6i step 1
- [x] 2.4 Developer docs: **done** — `docs/kotlin/README.md` is the handoff document: prerequisites and first-run setup (§2), the module map (§3), every command including parity and `wrangler dev` (§4), iOS bring-up (§5), how each port is verified (§6a–§6h), the maintainer's runbook for taking the room live (§6i), the traps (§7) and a verification checklist for a new machine (§8). The repository move and the CI it enables are §1a and §1b

## 2a. Platform prototype gate (before any porting)

A failure here changes the design rather than the schedule, so it runs before the ports —
not after the UI is built.

- [~] 2a.1 Kotlin/JS bundle containing `shared/engine`: **done** — the Worker now carries the real engine and measures **186 KB gzipped**, ~6% of the 3 MB limit. All 50 recordings replay through it in workerd via `POST /replay`, closing the Kotlin/JS-versus-JVM risk. Engine cost is ~0.9 ms per action warm (local `wrangler dev`, wide spread — treat as an order of magnitude, not a benchmark), against a 30 s Durable Object budget **per request**; a whole 278-action game replays in ~250 ms. **MCTS is still unmeasured** — it needs the bot port (phase 6)
- [x] 2a.2 Compose/Wasm bundle measured: **done** — 3.7 MB gzipped, accepted by the product owner with the reasoning in design D1a. Superseded by the real client rather than a hello-world page
- [x] 2a.3 End-to-end smoke: **PASS** — `worker` is now a real Worker with a `Room` Durable Object over Kotlin room logic; two clients join, exchange actions, resync from the log cursor, and reconnect to the same seat. Sockets use the hibernation API (`ctx.acceptWebSocket`), proven by messages arriving at `webSocketMessage()`, which only fires for hibernatable sockets; no authoritative state is held in memory. Resume verified by destroying every instance (restarting `wrangler dev`) and re-reading the room. Eviction with live sockets attached cannot be forced locally and needs a deployed Worker — the one open sliver. Results in `docs/kotlin/PLATFORM-GATE.md`
- [x] 2a.4 Measurements recorded in `docs/kotlin/PLATFORM-GATE.md`: **done, and the gate is closed** — 2a.1b was the last open item and passes; the worst request observed costs 1.6 s of a Durable Object's 30 s budget. No fallback was needed

## 3. Shared shapes, PRNG, recording

- [x] 3.1 Port `shapes`: **done** — `Card`, `Rank`, `Pile` (immutable, since the Kotlin engine is a pure reducer), `GameState`, `PlayerState`, `PendingAction`, `ActiveTossIn`, history types, all enums carrying their TypeScript string values, `CARD_CONFIGS` and helpers. TypeScript's `field?: T` versus `field: T | null` distinction is reproduced with `@EncodeDefault(NEVER)` versus a plain nullable, because the canonical form preserves it and therefore so does the hash. Verified against all 50 recordings in `fixtures/recordings/` (see 3.4)
- [x] 3.2 `GameAction` sealed hierarchy + `{ type, payload }` serializer: **done** — all 25 action types with payloads shared where TypeScript shares them, plus the `SELECT_ACTION_TARGET` payload union discriminated on its literal `rank` field (`'A'` versus `'Any'`, which is not a card rank despite the name). kotlinx's built-in polymorphism writes its discriminator beside the payload's own fields, so the two-level shape is built by hand. Verified far beyond "JSON samples": every one of the **13,900** recorded actions in `fixtures/recordings/` decodes and re-encodes to the same canonical form. The corpus covers 17 of the 25 types, so the rest — including the `rank: 'A'` variant, which appears in no recording — are pinned by unit tests in `commonTest` that run on all five targets
- [x] 3.3 `Prng` (mulberry32) passing the published test vectors: **done** — `PrngVectorsTest` reads the same `fixtures/prng/vectors.json` the TypeScript tests read (embedded by a Gradle task rather than copied, so it cannot drift) and runs on JVM, JS, wasmJs and the iOS simulator
- [~] 3.4 Canonical JSON writer, multiplatform SHA-256 and `hashGameState`: **done**; the `GameRecording` v1 model itself is not yet ported. **Deviation from D1**: these live in `shared/shapes`, not `shared/recording`, because that is where TypeScript keeps them (`packages/shapes/src/lib/canonical-json.ts`, `prng.ts`) and the port is file-for-file (D3). Verified more strongly than "byte-equal to samples": all 50 recordings carry a `finalStateHash` from TypeScript, and the Kotlin model decodes each state, re-encodes it, canonicalises and hashes it to the same value — one number covering lossless decode, correct optional-field handling, byte-identical canonical form and SHA-256 agreement. Negative controls confirmed the check is not vacuous
- [x] 3.5 Purity guard test for `shared/engine` sources (no clocks/random/uuid): **done** — `shared/engine/src/jvmTest/.../PurityGuardTest.kt`

## 4. Engine port (parity gate #1)

- [x] 4.1 `ActionValidator` + `GameEngine.reduce` + `ReduceResult`: **done**. The validator is ported in full and is the anti-cheat boundary design D9 depends on. The corpus cannot test it — every recorded action was legal, so an always-`Valid` validator replays all 13,900 identically — so it is covered two other ways: replaying the corpus with it live proves nothing legal is rejected, and `ValidatorImpersonationTest` re-attributes every seat-bound action to all three other players across every real game position (**18,066 attempts, none accepted**). Rule-specific cases — the coalition may not target the Vinto caller, a failed toss-in ends participation, setup peek limits — are posed against corpus states in `ValidatorRulesTest`, with negative controls confirming each bites
- [x] 4.2 Port `utils/`: **done** — toss-in flow (`getAutomaticallyReadyPlayers`, `advanceTurnAfterTossIn` with seeded reshuffle, `clearTossInAfterActionableCard`, `addTossInCard`, `queuedTossInCardId`), action-utils and scoring
- [x] 4.3 Port every case handler file-for-file: **all 25 done**. Original note: 13 of 25 — draw, play-discard, swap-card (with declaration), discard, use-card, confirm/skip peek, set-coalition-leader, peek-setup-card, finish-setup, process-ai-turn, update-difficulty, empty. Remaining, in the order the parity harness asks for them: select-action-target, player-toss-in-finished, participate-in-toss, finish-toss-in, jack/queen execute+skip, declare-king-action, call-vinto, set-next-draw-card, swap-hand-with-deck
- [x] 4.4 Port TypeScript engine tests: **done — all fifteen files**. Eight card actions, rules, scenarios, toss-in state, determinism, replay, the `replay-fixtures` corpus-coverage block, and the purity guard. They live in `commonTest` wherever they need nothing from disk, so they run on all six targets rather than only the JVM. The port found a real hole: **taking from the discard was never validated** — the rule allows only an unused action card, and neither implementation enforced it, so a client could lift a Joker off the pile. Three more TypeScript cases assert nothing at all (a comment reading "Test documents expected behavior" and no assertion) and are written properly here
- [x] 4.5 `projectView(state, playerId): PlayerView`: **done**. Not a port — it exists in no TypeScript file, because the web app is local single-player where the client legitimately owns the whole state. Written against the online-multiplayer spec, with the coalition-leader rule taken from `player-area-logic.ts` (a member is anyone with a coalition list who is not the caller, which includes the leader's own hand). **Hidden cards carry no id**: ids are `7_0`, `K_2`, so shipping them would leak every hand while looking redacted. Leak test serialises each seat's view over every corpus state and greps for forbidden ids — ~56,000 projections. One web-app rule deliberately dropped: the Vinto caller seeing bot-known cards is a display affordance for all-bot opponents and would hand a human caller other humans' cards
- [x] 4.6 `replayRecording()` in Kotlin with divergence report: **done**, and used from the start rather than at the end — it is what tells the port which handler to write next
- [x] 4.7 Replay the full corpus on JVM: **PASS, and it is now a hard gate rather than a ratchet.** All 50 recordings and all **13,900 actions** replay with canonical state hashes matching TypeScript's, per action, plus final-state verification. Confirmed non-vacuous: removing one line of knowledge tracking from the Jack swap fails it. Not yet wired into CI
- [~] 4.8 **iOS half done; the Android emulator half is blocked, and the block is real rather than a preference.** `kmp-ios` runs `:shared:client:iosSimulatorArm64Test`, so as of 6.7 a whole game is generated and replayed through the real `replayRecording` harness on **Kotlin/Native**, arm64, every time that job runs — which is the property this task wanted from the simulator leg. The 50-recording corpus itself is still JVM-only, because those suites read it off disk and a Native test binary has no filesystem to read it from; embedding it the way `fixtures/prng/vectors.json` is embedded does not scale to 4.5 MB (a JVM class file caps a string constant at 64 KB, so it would need chunking).
      **Android emulator: blocked here, recorded in README §1f.** An instrumented `connectedAndroidTest` reading the corpus from an asset is the right shape and needs `androidx.test`, and this container cannot resolve androidx at all — dl.google.com answers 403 (§1c), and it is not mirrored on Maven Central. So the job cannot be compiled here, only pushed and hoped for, which is exactly what this loop is not allowed to do. It is a nightly job on a machine that can build `composeApp`, and it is an hour's work there

## 5. Bot port (the single winner from phase 1)

- [x] 5.1 Port deterministic components: **done** — constants, shapes, Vinto call rule, opponent modeler, memory, heuristics, evaluation helpers, state evaluator, score estimator, outcome simulator, Vinto round solver, action planning. Two deliberate fixes: `BotMemory` injects `Random` and replaces `Date.now()` with an internal tick counter (a wall clock is not replayable), and `shouldUseAceAction` no longer sums opponents' real cards — it estimates from belief, because the bot may not read hidden hands (`docs/bot/BOT-ENGINE-DECISION.md`)
- [x] 5.2 Port `coalition-planner`: **done**, with its TypeScript scenario suite ported case-for-case (14 tests). Those tests assert *specific decisions*, so they check the Kotlin planner finds the same winning lines rather than merely a legal one — every scenario is a position where the coalition is losing and one line saves it. The rule that the coalition may not touch the caller's cards is structural: the caller is never among the search's hands, so no index can name one
- [x] 5.3 Port the search/decision core: **done** — types and node, move generator, state transition, determinization, rollout policy, evaluators, decision service. `Random` is injected and threaded through determinization, rollouts and expansion; the search runs on its iteration budget alone unless a caller opts into a time limit, since a clock-bounded search returns different moves on different machines (design D4)
- [x] 5.4 `BotDecisionServiceFactory.create(difficulty)`: **done**. The `suspend` wrapper on `Dispatchers.Default` is deferred to 6.5, where the caller that needs it lives; the decisions themselves are synchronous and pure, which is what makes them testable
- [x] 5.5 **Rule-following gate, done — and it is the gate that matters.** Decision parity with TypeScript was explicitly not required (user direction: the bot need not follow exactly, but must follow the rules), and is unverifiable in any case. `SelfPlayGateTest` checks what is checkable: four Kotlin bots play whole games through the real `GameEngine`, every proposed action passes `ActionValidator` first, and games must reach `scoring`. It also requires some game to end on a Vinto call — a game that only ends when the deck runs dry is one the bots never took charge of. It found five real defects no unit test would have (stale memory positions reaching the move generator, a dead end for tossed-in Jacks and Queens, stale cached action plans, target selection answering the wrong question once the engine had committed a card, and a search that could not see the deck run out)
- [x] 5.6 **Done — bot strength, measured against itself.** `TournamentTest` plays seeds 1..12 at all three difficulties through the real engine and tallies aggregate final scores, the caller/coalition split, mean actions and best/worst hand into `fixtures/bot/self-play-baseline.json`; the play loop is shared with `SelfPlayGateTest` (`Tournament.kt`) so legality and strength are asked of one table rather than two. Verified: `-Ptournament=write` then `-Ptournament` reproduced every committed number exactly across three runs, with only `ms/decision` moving — which is why latency is printed and never committed, and why every committed figure is an integer (means in hundredths).
      **It is a manually-run gate and says so**: 6m 39s against 1m 26s for the rest of `:shared:bot:jvmTest`, so it is excluded unless `-Ptournament` is passed, the same shape as `-Pscreenshots`. The legality gate still runs on every push.
      Two findings recorded in README §6k rather than acted on blind: `hard` costs 24x `easy` per decision for the worst mean hand of the three, and a homogeneous table cannot rank difficulties at all — every seat plays at the same level, so ranking them means a different and much bigger tournament

## 6. Client port: `GameSession` (parity gate #2, round trip)

- [~] 6.1 `GameSession` interface and `SessionEvent`: **done** in the new `shared/client`, with
      `view` as a `StateFlow<PlayerView>`. The interface exists so a local game and an online one
      are indistinguishable to the UI (design R1), which is what makes single-player free to host.
      `visualView`/`syncVisualState()` are **not** ported: they are the TypeScript animation seam,
      and whether Compose needs the same shape is a phase 7 question — porting them blind would
      bake a React-era design into the UI layer before anything has asked for it
- [~] 6.2 `LocalGameSession`: **engine + bots in-process, done and gated** — one human against
      three bots, no room, no socket, proven by `NoNetworkGuardTest` playing a whole round under
      a `SecurityManager` that throws on any network call, with the guard's own bite asserted
      first. It reads the redacted `PlayerView` and enforces the same seat boundary the Durable
      Object does, from the same `GameAction.actorId`. Recorder and history sequence numbers wait
      on 6.4
- [x] 6.3 `initializeGame` seeded: **done**, and verified beyond "samples" — every recording carries the seed it was dealt from, so all 50 are checked by dealing in Kotlin and comparing the canonical state hash against TypeScript's `initialState`. Lives in `shared/engine` rather than `shared/client`: the **server** deals, and a Durable Object must not depend on a client module to do it. The seed is a required parameter, unlike TypeScript where it falls back to `crypto` — picking a seed is ambient randomness and belongs outside the engine
- [x] 6.4 `GameRecorder` + export + local auto-save: **done** — `shared/client/.../Recorder.kt` with `RecorderTest`; the vault is the `expect`/`actual` storage and the table's bug-report control exports a full recording
- [x] 6.5 **Done — and most of it was already built, which is why it is worth writing down what it turned into.** The TypeScript `BotAIAdapter` is 1,500 lines wound around animations, MobX reactions and `await delay(...)`, because it drives a UI. Split here: the *deciding* is `BotRunner`, a pure function of the state shared with the Durable Object (sequential by construction, coalition routing, leader auto-selection, opponent tracking through `observe`), and the *pacing* reaches the UI as frames rather than as sleeps inside the bot driver — so `await delay(...)` has no counterpart on purpose. What was genuinely 6.5's is the coroutine seam, and it existed: `LocalGameSession(botDispatcher = …)`, `Dispatchers.Default` from the app, null in tests.
      What did **not** exist was anything checking that seam was live. `BotDispatcherTest` (commonTest, so JVM/JS/Wasm) passes a dispatcher that records whether the block ran on it and a `BotDirector` that reports from the innermost point of the bot loop; it asserts the search ran on the injected dispatcher every time, that a dispatcher nobody was given is never used, and that the whole run of bot turns rides on **one** hop rather than one per bot — so a table of three bots never bounces back to the drawing thread mid-thought. Proved non-vacuous by collapsing `onBotDispatcher` to `block()`: "the injected dispatcher was never used". 165 JVM / 150 JS / 150 Wasm tests green, detekt clean
- [x] 6.6 Both halves. **`bot-tossin` was already ported** — `shared/bot`'s `TossInDecisionTest` covers all eight of the TypeScript cases one for one (a single matching rank, several open ranks, no match, several copies, one match among many, no match among many, an all-matching hand, an empty hand). Verified case by case rather than assumed; nothing was missing but the tick.
      **`coalition-final-round` is new**: `CoalitionFinalRoundTest` in `shared/client`'s jvmTest, three bots playing a real final round against a human caller through `LocalGameSession`. Deliberately **not** a port of the TypeScript assertions, which read "wins by using a drawn Jack to move a Joker into a teammate" — a specific tactic, a 30-second timeout, and a stochastic MCTS search deciding whether the test passes. §7 already lists two TypeScript tests as flaky for that reason. What is asserted instead is what must hold whatever the bots choose: the round always finishes, the caller is still the caller at the end and the phase reaches `scoring`, and every coalition member is scored on the coalition's **best** hand rather than their own. A bot that cannot find the Jack play passes; one that hangs the round or breaks the coalition rule does not.
      The sweep carries a floor (`enoughSeeds`) because not every deal lets the caller call in the toss-in window: without it the loop could `continue` past all forty seeds and pass having asserted nothing. A probe confirmed how many actually reach the call
- [x] 6.7 **Done — the round trip runs on all three targets, and it found a live bug.** `RecordingRoundTripTest` in `shared/client`'s **`commonTest`** plays a whole game to `scoring` with `playItselfOut`, exports the report, and replays it through the real `replayRecording` harness **reached through text** rather than through the object just built in memory. No new CI job was needed and none was added: `kmp-jvm` and `kmp-web` already run `commonTest` on JVM, Kotlin/JS and Wasm, so the three legs existed and the test was the missing half. Verified on each: JVM 9.2 s, JS and Wasm green (Wasm 24.5 s), `:shared:client:jvmTest` 163 tests / 0 failures, detekt clean. Nothing is committed and nothing goes stale, because each target generates the recording it replays.
      Two of the three assertions exist to keep the first honest: a corrupted hash must be caught *at the action that carries it*, and one seed must produce one document byte for byte — which is what makes two targets comparable at all.
      **What it found:** a player's exported bug report could not be replayed by anything. `Recording.formatVersion` has a default, `VintoJson` has `encodeDefaults` off, so the field was absent from every report — and `GameRecording.formatVersion` is required, so `CorpusReplayTest` and the Worker's `POST /replay` both refused to parse one, against a comment in `Recorder.kt` promising the opposite. Fixed with `@EncodeDefault(ALWAYS)`; `RecorderTest` never caught it because it replays the object it built in memory. See README §6l
- [x] 6.8 **Decided against Koin.** The port reached a shipped architecture with explicit construction and no DI container, and adding one now would be churn against working code for no problem anybody has. `project.md` and `config.yaml` no longer name it. The half of this task that had value — a headless runner playing a full bot game — exists as `SelfPlayGateTest`

## 7. Compose Multiplatform UI (single player first)

- [~] 7.1 Prototype gate: **decided — an overlay layer**, and it is built and running on an
      Android emulator: a card leaves the deck, crosses the table and lands on the pile, drawn
      above the felt rather than by rearranging it. `LookaheadScope` was the alternative and is
      the wrong shape here — a card going from a hand to the discard passes over three other
      hands, and animating it in place means making room for it in every one of them. Not yet
      run on a physical phone or on iOS
- [x] 7.2 Design tokens (light/dark), typography, theme: **done**, and deliberately *not*
      Material-looking. Material's own button is a stadium, and it was the single thing that
      made the screen read as an Android app rather than a game. The controls are now the web
      app's: four-pixel corners, a solid colour, a small shadow, on a dark rail that stays dark
      in both themes. The colours carry meaning, ported from `BUTTON_ACTION_VARIANTS` — green
      gets on with the turn, blue puts a card in a hand, slate declines, orange ends the round,
      amber names a rank — so a player who learned them on the web does not learn them twice.
      Reduced-motion is honoured: a Motion setting (system / full / reduced) with the system
      preference read per platform, and "reduced" meaning no movement with every ring, verdict
      and pause kept — the game still narrated, it just doesn't move
- [x] 7.9 Help: a "?" on the panel opens what the rules say about whatever is happening — the
      card in hand, or the phase — followed by every rank and what it does. The words are
      `CARD_CONFIGS`, ported with the engine, so the web app and this teach the same game. A
      drawn action card also explains itself inline, without being asked
- [~] 7.3 Screens/navigation: opening, home, settings, the lesson, the game table, and an
      end-of-round score sheet with hand / round / game columns. Home is now a front door
      rather than a title and two buttons: the deck deals itself in behind the wordmark, single
      player sits in a panel with its difficulty on show and one button to a table, and the
      other three ways in are under it. **Online is a button that works** — it says what exists
      (a Worker with a Durable Object per room, running this engine) and what does not (this
      app's half), because a greyed-out "coming soon" answers nothing. Settings are four
      choices — bots, pace, theme, haptics — kept under their own vault key, since a preference
      outlives the round it was set in. Android's back button is honoured through an
      `expect`/`actual` `SystemBack`; without it, back from the settings closed the app. **Not
      started**: the lobby (it needs the online client, phase 9) and the debug screens
- [x] 7.10 Persistence: the whole game — difficulty, session seed, round number, standings and
      the round in progress — is written down after every move and comes back on launch. The
      *state* rather than a seed and a log: replaying from a seed restores the cards and not
      the bots' memories, and would hand you three opponents who had forgotten the round they
      were in the middle of
- [x] 7.11 A game is a session of rounds with points carried between them, as the rules
      describe. Locally there is no thirty-minute clock and nobody to keep waiting, so it ends
      when the player says so
- [x] 7.4 Game table composables, seat-agnostic (rendered for `viewerId`): four-sided seating in
      the web app's phone arrangement, name plates, piles, pending card, action targets, rank
      declaration, King declaration, Jack/Queen flows, toss-in with pass, Vinto at the end of
      your own turn, coalition leader choice, waiting indicators. The *decisions* live in
      `TableModel.kt` as a pure function of the view and are covered by `TableModelTest`; the
      composables draw what it returns and decide nothing
- [x] 7.5 Animation: card flight, card flip on reveal, a pulse on what can be tapped and a glow
      on the seat whose turn it is. The flights are derived by a pure `flightsFor` and tested;
      the overlay draws them. **The screen no longer runs ahead of its own animation**: the
      session emits one `Frame` per move — the scenes *and* the table that move left behind —
      and the stage draws the frame it is playing, catching up to the live view when there is
      nothing left to show. The engine still runs ahead, as it must, but the player is no
      longer shown the final position while cards fly out of hands they have already left.
      With that true the vocabulary could be slowed to something readable (460 ms a card, a
      380 ms pause when the turn passes), and the speed is now a setting. Reduced motion is a
      setting (see 7.2), and the sound layer exists: four synthesized sounds and no more —
      a card dealt, a card landing, a penalty, the round ending — behind a silent-default
      `LocalSounds` with platform actuals, regenerable by `tools/make-sfx.py`
- [x] 7.12 **How to play**: a real round on a written-down deck, with a director that makes the
      bots play their parts and calls Vinto so the final round, the coalition and the scoring
      are *played* rather than described; a coach derived from the position (so every legal
      move stays legal); a pointing hand for cards, chips, buttons, seats and the screen's own
      furniture; and each card explained in `CARD_CONFIGS`' words the first time it is seen.
      Designed with the `fable` model against `VINTO_RULES.md` and the client sources; see
      `docs/kotlin/README.md` §6g. Engine gained one entry point (`initializeTeachingGame`,
      which refuses a deck that is not a permutation of the real one); the client gained
      `BotDirector`, `TeachingDeal` and the pure `lessonFor`
- [x] 7.6 Accessibility semantics for cards/controls, touch targets, large fonts, landscape.
      Audited: the deck names its count, the toss-in corner describes itself as one sentence,
      headings mark the panel prompt / standings columns / final-round line / settings
      plaques, and the thrown row grows under a large font instead of clipping.
      `TouchTargetTest` re-runs its bounds at `fontScale = 2f` (the setting that finds
      controls sized by their label), `TossInAreaTest` asserts the spoken summary, and the
      landscape half shipped with the orientation unlock
- [x] 7.7 **Already built in phase 7; verified and ticked rather than re-implemented.** Export/share: the report dialog in `GameScreen` hands `report.toJson()` to `shareText` and falls back to the clipboard where a platform has no share sheet — held by `RecorderTest.aReportReplaysActionForAction`, which replays an exported report action for action, and `aReportCarriesWhatItNeedsToBeDealtAgain`. Restore on relaunch: `LocalGame.resume(vault, …)` is called from `App`'s opening effect and pins the round to `Screen.Playing`, held by `LocalGameTest.aResumedGameIsStillPlayable`. Nothing was missing; the checkbox was
- [x] 7.8 UI tests: `TableUiTest` plays from the home screen to the first draw and back into a
      saved game; `MenuUiTest` covers the four ways in, the online explanation, a setting
      reaching the vault and the lesson opening on a real table; `VersionTest` keeps the
      version on screen and the one in the APK from drifting. Both remainders exist:
      `FullGameUiTest` plays a whole round on the real `GameScreen` — setup and a turn by
      tapping the actual buttons, the rest at machine speed through `playItselfOut` — and
      holds the screen to the end-of-round promises; `ScreenshotTest` photographs home,
      settings and both table arrangements in both themes against goldens (`Goldens.kt`;
      first run writes, mismatch leaves an `.actual.png` beside)

## 8. Delivery (single player)

- [~] 8.1 Android: signing, Play internal track from CI on tags; iOS: bundle id, TestFlight from CI.
      **The sideload half is done**: `assembleRelease` signs with the upload key named by
      `keystore.properties` and falls back to the debug key when that file is absent, so the
      release variant assembles on a machine that has never been given a secret — a build that
      fails on a missing key is one nobody runs until the day it must work. With it, the app now
      has a launcher icon (the web app's own V, regenerated by `tools/make-launcher-icons.py`
      into adaptive, legacy and monochrome layers), a portrait lock, and a window theme that is
      dark Material over the rail instead of `Theme.Material.Light` — which had been putting dark
      status-bar icons on a dark rail and a white flash before the first frame. See
      `docs/kotlin/README.md` §6f. **Still open**: an upload key, a Play track, CI on tags
      (which needs 2.3 first — there is no Gradle job at all), R8, and everything iOS
- [~] 8.2 Crash reporting in `composeApp` — **built, hand-rolled instead of the SDK**, with the reasoning and the bundle measurement in `add-live-analytics/design.md` §A9. The SDK does publish for all four of our targets (checked first, not assumed) and it does catch native crashes a `try`/`catch` cannot; it loses anyway because the web client has no native crash to catch — a Wasm trap is an ordinary JS exception — and the web bundle is the one target with no headroom. Flagged for review rather than settled: `installCrashHandler` is an `expect`/`actual`, so adopting the SDK on Android and iOS alone stays a two-file change.
      What is built: `crash/Crash.kt` (DSN parse, scrubbing, envelope) and `crash/CrashReporter.kt`, with four handlers — `Thread.setDefaultUncaughtExceptionHandler` chained to the previous one on JVM and Android, `setUnhandledExceptionHook` on iOS, and `error` + `unhandledrejection` listeners on wasm (the second is where a coroutine's exception actually surfaces there). Verified by `CrashReportTest` (9) and `CrashReporterTest` (4): no `user` object or device id can be emitted, an escaped room code in a stack trace is scrubbed, a device in a crash loop still sends exactly one report, and a reporter that cannot reach Sentry does not throw on the crash path.
      **The bundle measurement came out inconclusive, and is recorded as such.** Baseline 4,544,016 B gzipped, with the SDK declared 4,544,014 B — identical, because an unreferenced dependency is tree-shaken away on Kotlin/Wasm. A real figure needs the SDK actually called, which is most of the integration the measurement was meant to inform; it was not spent, because the decision is categorical rather than quantitative (no native crash exists for it to catch on the web). What the exercise *did* measure is the hand-rolled reporter's own cost: **+6,341 B gzipped, 0.14% of the bundle**, for all four clients.
      **The breadcrumbs half is now built too.** `CrashPlace` (gameId, round, turn) is the mirror of the room's `roomContext` from 9.9, read at the moment of the crash and emitted as Sentry `extra` — omitted entirely when there is no game, because an `extra` block that is always present teaches a reader to skim past it, and three nulls suggest the app looked and found no game when it never had one. `gameId` is the same id the bug-report control writes into an exported recording, so a crash and a replayable document can be put side by side. It carries **no room code, no nickname, no seat and no device**, and `scrubReport` still runs over the whole envelope, so a code that arrived through the address rather than through a stack trace is stripped as well.
      It is `turn` rather than an action index deliberately: the *view* carries it, and the view is the one thing a local game and an online one hold identically — an address that only worked in solo play would be missing from exactly the sessions that are hardest to reproduce. Written from `rememberHolder`, the single point both tables pass through, into a plain holder rather than a `CompositionLocal` (a local changing every turn would recompose the whole tree to serve a side channel that fires once per process), and cleared on the way out so a crash in the menu is not filed against the game before it.
      Verified: four new cases in `CrashReportTest` (the address reaches the wire, no game means no `extra` at all, a partial address carries only what is known, and an address carrying a code is still scrubbed), plus the wiring itself asserted in `FullGameUiTest` — the only suite with a real table in it — since what can regress is the wiring rather than the encoding. `:composeApp:jvmTest` 112 tests / 0 failures, detekt clean.
      **Still not ticked**, for one reason rather than two: the privacy manifest and the permissions review need Xcode (README §1f). The DSN is empty in the source; DEPLOYMENT.md §7a says where a release build sets it
- [x] 8.3 **Done — mostly as a task rather than a checklist**, because a checklist in a document is a list of things somebody forgets one of. `./gradlew releaseGate` is everything a Linux machine can check in one command: detekt over every module and source set, the six shared modules' JVM suites, the same `commonTest` suites on Kotlin/JS and Wasm, the Compose screens headless, and the Worker bundle compiling. Verified: **3m 03s, BUILD SUCCESSFUL**, 108 Compose / 165 client / 217 bot tests among them, and `--dry-run` checked that the graph reaches every module's `detekt` including `:worker` and `:composeApp`. What it cannot run is named in `docs/kotlin/RELEASE-GATE.md` beside the machine that can, rather than quietly left out.
      Three of the five gates 8.3 named have changed shape and the document says how. `parity-roundtrip` is now the cross-**target** round trip (6.7). "device parity" is 4.8, half done and half blocked. **"Tournament within 5 pp" is gone**: the tolerance existed for comparing against TypeScript, and the comparison that replaced it is against the bot's own committed integers, where an exact match is both achievable and stricter — so there is no tolerance to set. The tournament stays out of the one command on purpose (6m 39s of MCTS; §6k)
- [x] 8.4 `docs/kotlin/ARCHITECTURE.md` written: the module graph, the seven invariants each named with the test that holds it, why the reducer is the centre (and why its handlers mutate), what each seam is for, the three Durable Object consequences, and what is deliberately *not* shared. Split from `README.md` on purpose — that file is the *state* of the work and changes when a task is ticked; this one is the *shape* and changes when a decision does. `README.md` and `CLAUDE.md` point at it.
      The "both engines + fixtures" policy is recorded **with its expiry date** rather than copied: it is true today and stops being true when `legacy-web/` is deleted, because the corpus can then no longer be regenerated (§1d). What replaces it is already running — the same `commonTest` suites on JVM, JS and Wasm, which is the property that still matters once one engine ships, since a `Long` is two `Int`s on Kotlin/JS

## 9. Online multiplayer

- [x] 9.1 `shared/protocol`: message types (`join`, `action`, `event{index, action, view}`, `resync`, `error`) with serializers shared by client and server; protocol doc `docs/kotlin/PROTOCOL.md`.
      The wire came first — `index.mjs` had been serving the gates — so the module transcribes
      it and `ProtocolWireTest` pins it with literals copied from the JavaScript
- [x] 9.2 `worker` (Cloudflare Worker + Durable Object per room, Kotlin/JS): rooms (create/join by code, always 4 seats, host start, bot fill), the Durable Object owning `GameState`, authoritative validate/apply via `shared/engine`, `BotAIAdapter` running in the object (never on a client — a client cannot decide a bot move without that seat’s hidden cards), per-seat `projectView` broadcast, `GameRecorder` per room, WebSocket Hibernation so idle rooms cost no duration.
      The last piece was the recorder: every finished round is filed as a `GameRecording` v1
      (`recording:<n>` in DO storage, served on a plain GET), and `RoomRecordingTest` replays
      what a driven room produces. The cores themselves moved to `shared/room` (jvm+js) where
      the JVM can finally test them; the worker keeps `@JsExport` delegates
- [x] 9.3 Reconnection/resync by recording index; idempotent (seat, index) handling; grace period → bot takeover of a disconnected seat.
      Server half existed (log-index cursor, seat grace, takeover); the client half is
      `RemoteRoom`'s reconnect loop — rejoin by vaulted token, `resync(cursor)`, one landing
      frame — held by `RemoteSessionTest` and the two-client harness's mid-game socket kill
- [x] 9.4 Human pacing: toss-in ready timeout, coalition-leader selection timeout (configurable per room).
      15 s / 20 s as room constants rather than per-room settings (a knob nobody asked to turn
      yet); deadlines are data folded into the one alarm, expiry moves for the laggard through
      the ordinary validate-and-reduce path, logged `byBot` — `PacingTimeoutTest`
- [x] 9.5 Guest identity (device-bound id + nickname), room codes/links.
      `Identity.kt`: a guest id minted once from caller-supplied entropy, a remembered
      nickname, and per-room seat tokens vaulted the moment `joined` delivers them. Codes are
      typed into `OnlineScreen`; a `?room=CODE` deep link waits on a navigator (see `App.kt`)
- [x] 9.6 `RemoteGameSession` (Ktor client WebSocket, or platform WebSocket on Wasm) implementing `GameSession`; offline detection and reconnect UX.
      Decided against Ktor entirely: `RoomSocket`/`RoomConnector` interfaces in shared code
      (which keeps `NoNetworkGuardTest` honest) with each platform's own socket in the app —
      `java.net.http`, OkHttp, `NSURLSessionWebSocketTask`, the browser's `WebSocket`. The
      session builds the same `Frame`s the local one does, from the per-event views
- [x] 9.7 Lobby UI (create/join/seats/start), in-game connection indicators; seat-agnostic game screen verified with 2 humans + 2 bots and 4 humans.
      `OnlineScreen` and `RoomScreen` over a pure, tested `LobbyModel`; the connection badge
      appears wherever the socket wavers. 2 humans + 2 bots is verified by the harness; the
      4-human table is a maintainer run against a real deployment (runbook §6i)
- [x] 9.8 Multi-client test harness (scripted human clients + bots vs a local server) producing recordings; add to `parity-roundtrip`.
      `TwoClientGameTest`: two real `RemoteGameSession`s against the room's own entry points
      over channel sockets — agreement at scoring, one frame per logged action, a mid-game
      reconnect, and the filed recording replayed through the engine in the same test (which
      is the parity check, run on every build rather than added to a script)
- [~] 9.9 **Three of four done; the fourth needs a deployment.** Two clauses were written for an architecture that was not built: there is no **Sentry JVM** and no **container** — the room is a Durable Object, decided in design D1/D9, and the provider was decided with it. Read as "the server reports, and its reports are useful", which is what landed.
      **Sentry on the Worker, with the address of the failure.** `reportError` existed; nothing passed it anything but a surface, so a report said only that *a* room socket failed. `roomContext` now answers the deal's `gameId`, the round being played, and the action index **as an offset into that round** — which together are the address of a stored recording (`recording:<round>`) and a position inside it, so a report is something you replay rather than something you read. Wired into the two handlers that could fail invisibly: `webSocketMessage` (a throw closes the socket and the player just sees the game stop) and `alarm` (no socket, no request — the buzzer simply never rings again). Both report and **rethrow**, so the runtime does exactly what it did before. The context builder never carries the room code or the room id: a code is a join credential, `scrub` would strip one that arrived by accident, and this is the same rule applied on purpose. Verified: `gate-sentry.mjs` extended to 16 new checks (the address, the omissions, and four malformed states that must yield a context rather than a throw), **PASS**; all twelve room gates re-run after the `#onMessage` refactor — nine in Node, three through `wrangler dev`, `gate-two-clients` playing a full round through sockets — **all PASS**
      **Recordings persisted for finished games: already done** — `#fileRecording` writes `recording:<round>` once per round, detected by the session growing a round, so it fires however the round ended
      **Load test with 100 concurrent rooms: blocked**, README §1f. It needs a deployment; `wrangler dev` enforces no CPU limit whatsoever (§6d), so a load test against it would measure this laptop
- [ ] 9.10 **BLOCKED** (README §1f), and on four separate things rather than one: an upload key, Play and App Store accounts, a signed build, and a room that is actually open — a store release "with multiplayer enabled" is a release of a client pointed at a deployed Worker with `ROOM_OPEN` flipped, which is §6i step 4. What is built: `assembleRelease` signs with the upload key when `keystore.properties` exists and with the debug key when it does not, so the signing path is exercised without the secret

## 10. Follow-up: one engine

- [x] 10.1 **Opened: `openspec/changes/retire-legacy-web/`** — proposal, design and 16 tasks in four phases, with the deletion last so the decision stays reversible until it is taken (design D5). Half the original task is already settled by events: the Kotlin engine *is* the web client (Compose Web ships), `legacy-web/`'s CI is gone (§1d), and `nx build @vinto/game` has been broken since before this branch.
      **The decision record is the point, and it says no to the obvious move.** The instinct is to port `generate-recordings.ts` to Kotlin before deleting it, so the corpus stays extensible; that is rejected. A recording generated by the engine under test proves the engine agrees with itself — which is worth having and is already had, by `RecordingRoundTripTest` (§6l) and the tournament baseline (§6k). It cannot catch a handler ported wrong from the start in a self-consistent way, which is what the corpus was built for. So the fifty recordings are worth more frozen: they are the last artefact here written by an engine that had never seen this one, and regenerating them destroys that permanently and *silently*, because the format does not record which engine wrote it. The change therefore adds a manifest test so the freeze is enforced rather than merely intended, and a separate directory for Kotlin-generated recordings so the two can never be confused.
      **Also corrected**: the clause "until then the parity gate keeps both engines identical" is no longer true and has not been since §1d removed the TypeScript CI. What holds is weaker and the change says it plainly — the corpus records what TypeScript computed once, and Kotlin still reproduces it
