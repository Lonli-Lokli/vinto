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

- [ ] 2.1 Create `kmp/` Gradle workspace (Kotlin 2.x, KMP plugin, Compose Multiplatform, version catalog); modules `shared:shapes`, `shared:engine`, `shared:recording`, `shared:bot`, `shared:client`, `shared:protocol`, `worker`, `parity-tests`, `composeApp`, `iosApp`
- [ ] 2.2 Targets: `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `js(IR)` (Cloudflare Worker), `wasmJs` (Compose web) and `jvm` (tests/tooling only — there is no JVM server); kotlinx.serialization/coroutines/datetime, Koin, Ktor **client** only, kotlin.test, Turbine
- [ ] 2.3 GitHub Actions: `kmp-jvm` (unit + parity), `kmp-android` (assemble), `kmp-ios` (macos: build framework + simulator tests), `kmp-worker` (Kotlin/JS bundle + `wrangler deploy --dry-run`, asserting the bundle stays under the Worker script-size limit), Gradle/Konan caching
- [ ] 2.4 Developer docs: `docs/kotlin/README.md` (setup, module map, how to run parity, how to run the Worker locally with `wrangler dev`)

## 2a. Platform prototype gate (before any porting)

A failure here changes the design rather than the schedule, so it runs before the ports —
not after the UI is built.

- [~] 2a.1 Kotlin/JS bundle containing `shared/engine`: **done** — the Worker now carries the real engine and measures **186 KB gzipped**, ~6% of the 3 MB limit. All 50 recordings replay through it in workerd via `POST /replay`, closing the Kotlin/JS-versus-JVM risk. Engine cost is ~0.9 ms per action warm (local `wrangler dev`, wide spread — treat as an order of magnitude, not a benchmark), against a 30 s Durable Object budget **per request**; a whole 278-action game replays in ~250 ms. **MCTS is still unmeasured** — it needs the bot port (phase 6)
- [ ] 2a.2 Hello-world Compose/Wasm page: measure bundle size and cold-load time on a mid-range phone browser; Compose for web is the least mature Compose target
- [x] 2a.3 End-to-end smoke: **PASS** — `kmp/worker` is now a real Worker with a `Room` Durable Object over Kotlin room logic; two clients join, exchange actions, resync from the log cursor, and reconnect to the same seat. Sockets use the hibernation API (`ctx.acceptWebSocket`), proven by messages arriving at `webSocketMessage()`, which only fires for hibernatable sockets; no authoritative state is held in memory. Resume verified by destroying every instance (restarting `wrangler dev`) and re-reading the room. Eviction with live sockets attached cannot be forced locally and needs a deployed Worker — the one open sliver. Results in `docs/kotlin/PLATFORM-GATE.md`
- [ ] 2a.4 Record the measurements in `docs/kotlin/PLATFORM-GATE.md`; if either bundle does not fit, decide and record the fallback (thinner server-side bot, or a non-Compose web client) before proceeding

## 3. Shared shapes, PRNG, recording

- [x] 3.1 Port `shapes`: **done** — `Card`, `Rank`, `Pile` (immutable, since the Kotlin engine is a pure reducer), `GameState`, `PlayerState`, `PendingAction`, `ActiveTossIn`, history types, all enums carrying their TypeScript string values, `CARD_CONFIGS` and helpers. TypeScript's `field?: T` versus `field: T | null` distinction is reproduced with `@EncodeDefault(NEVER)` versus a plain nullable, because the canonical form preserves it and therefore so does the hash. Verified against all 50 recordings in `fixtures/recordings/` (see 3.4)
- [x] 3.2 `GameAction` sealed hierarchy + `{ type, payload }` serializer: **done** — all 25 action types with payloads shared where TypeScript shares them, plus the `SELECT_ACTION_TARGET` payload union discriminated on its literal `rank` field (`'A'` versus `'Any'`, which is not a card rank despite the name). kotlinx's built-in polymorphism writes its discriminator beside the payload's own fields, so the two-level shape is built by hand. Verified far beyond "JSON samples": every one of the **13,900** recorded actions in `fixtures/recordings/` decodes and re-encodes to the same canonical form. The corpus covers 17 of the 25 types, so the rest — including the `rank: 'A'` variant, which appears in no recording — are pinned by unit tests in `commonTest` that run on all five targets
- [ ] 3.3 `Prng` (mulberry32) passing the published test vectors
- [~] 3.4 Canonical JSON writer, multiplatform SHA-256 and `hashGameState`: **done**; the `GameRecording` v1 model itself is not yet ported. **Deviation from D1**: these live in `shared/shapes`, not `shared/recording`, because that is where TypeScript keeps them (`packages/shapes/src/lib/canonical-json.ts`, `prng.ts`) and the port is file-for-file (D3). Verified more strongly than "byte-equal to samples": all 50 recordings carry a `finalStateHash` from TypeScript, and the Kotlin model decodes each state, re-encodes it, canonicalises and hashes it to the same value — one number covering lossless decode, correct optional-field handling, byte-identical canonical form and SHA-256 agreement. Negative controls confirmed the check is not vacuous
- [ ] 3.5 Purity guard test for `shared/engine` sources (no clocks/random/uuid)

## 4. Engine port (parity gate #1)

- [x] 4.1 `ActionValidator` + `GameEngine.reduce` + `ReduceResult`: **done**. The validator is ported in full and is the anti-cheat boundary design D9 depends on. The corpus cannot test it — every recorded action was legal, so an always-`Valid` validator replays all 13,900 identically — so it is covered two other ways: replaying the corpus with it live proves nothing legal is rejected, and `ValidatorImpersonationTest` re-attributes every seat-bound action to all three other players across every real game position (**18,066 attempts, none accepted**). Rule-specific cases — the coalition may not target the Vinto caller, a failed toss-in ends participation, setup peek limits — are posed against corpus states in `ValidatorRulesTest`, with negative controls confirming each bites
- [x] 4.2 Port `utils/`: **done** — toss-in flow (`getAutomaticallyReadyPlayers`, `advanceTurnAfterTossIn` with seeded reshuffle, `clearTossInAfterActionableCard`, `addTossInCard`, `queuedTossInCardId`), action-utils and scoring
- [x] 4.3 Port every case handler file-for-file: **all 25 done**. Original note: 13 of 25 — draw, play-discard, swap-card (with declaration), discard, use-card, confirm/skip peek, set-coalition-leader, peek-setup-card, finish-setup, process-ai-turn, update-difficulty, empty. Remaining, in the order the parity harness asks for them: select-action-target, player-toss-in-finished, participate-in-toss, finish-toss-in, jack/queen execute+skip, declare-king-action, call-vinto, set-next-draw-card, swap-hand-with-deck
- [x] 4.4 Port TypeScript engine tests: **done — all fifteen files**. Eight card actions, rules, scenarios, toss-in state, determinism, replay, the `replay-fixtures` corpus-coverage block, and the purity guard. They live in `commonTest` wherever they need nothing from disk, so they run on all six targets rather than only the JVM. The port found a real hole: **taking from the discard was never validated** — the rule allows only an unused action card, and neither implementation enforced it, so a client could lift a Joker off the pile. Three more TypeScript cases assert nothing at all (a comment reading "Test documents expected behavior" and no assertion) and are written properly here
- [x] 4.5 `projectView(state, playerId): PlayerView`: **done**. Not a port — it exists in no TypeScript file, because the web app is local single-player where the client legitimately owns the whole state. Written against the online-multiplayer spec, with the coalition-leader rule taken from `player-area-logic.ts` (a member is anyone with a coalition list who is not the caller, which includes the leader's own hand). **Hidden cards carry no id**: ids are `7_0`, `K_2`, so shipping them would leak every hand while looking redacted. Leak test serialises each seat's view over every corpus state and greps for forbidden ids — ~56,000 projections. One web-app rule deliberately dropped: the Vinto caller seeing bot-known cards is a display affordance for all-bot opponents and would hand a human caller other humans' cards
- [x] 4.6 `replayRecording()` in Kotlin with divergence report: **done**, and used from the start rather than at the end — it is what tells the port which handler to write next
- [x] 4.7 Replay the full corpus on JVM: **PASS, and it is now a hard gate rather than a ratchet.** All 50 recordings and all **13,900 actions** replay with canonical state hashes matching TypeScript's, per action, plus final-state verification. Confirmed non-vacuous: removing one line of knowledge tracking from the Jack swap fails it. Not yet wired into CI
- [ ] 4.8 Run the corpus on Android emulator + iOS simulator (CI job, nightly/pre-release) — green

## 5. Bot port (the single winner from phase 1)

- [x] 5.1 Port deterministic components: **done** — constants, shapes, Vinto call rule, opponent modeler, memory, heuristics, evaluation helpers, state evaluator, score estimator, outcome simulator, Vinto round solver, action planning. Two deliberate fixes: `BotMemory` injects `Random` and replaces `Date.now()` with an internal tick counter (a wall clock is not replayable), and `shouldUseAceAction` no longer sums opponents' real cards — it estimates from belief, because the bot may not read hidden hands (`docs/bot/BOT-ENGINE-DECISION.md`)
- [x] 5.2 Port `coalition-planner`: **done**, with its TypeScript scenario suite ported case-for-case (14 tests). Those tests assert *specific decisions*, so they check the Kotlin planner finds the same winning lines rather than merely a legal one — every scenario is a position where the coalition is losing and one line saves it. The rule that the coalition may not touch the caller's cards is structural: the caller is never among the search's hands, so no index can name one
- [x] 5.3 Port the search/decision core: **done** — types and node, move generator, state transition, determinization, rollout policy, evaluators, decision service. `Random` is injected and threaded through determinization, rollouts and expansion; the search runs on its iteration budget alone unless a caller opts into a time limit, since a clock-bounded search returns different moves on different machines (design D4)
- [x] 5.4 `BotDecisionServiceFactory.create(difficulty)`: **done**. The `suspend` wrapper on `Dispatchers.Default` is deferred to 6.5, where the caller that needs it lives; the decisions themselves are synchronous and pure, which is what makes them testable
- [x] 5.5 **Rule-following gate, done — and it is the gate that matters.** Decision parity with TypeScript was explicitly not required (user direction: the bot need not follow exactly, but must follow the rules), and is unverifiable in any case. `SelfPlayGateTest` checks what is checkable: four Kotlin bots play whole games through the real `GameEngine`, every proposed action passes `ActionValidator` first, and games must reach `scoring`. It also requires some game to end on a Vinto call — a game that only ends when the deck runs dry is one the bots never took charge of. It found five real defects no unit test would have (stale memory positions reaching the move generator, a dead end for tossed-in Jacks and Queens, stale cached action plans, target selection answering the wrong question once the engine had committed a card, and a search that could not see the deck run out)
- [ ] 5.6 Strength comparison against the TypeScript baseline (aggregate final scores, coalition win rate, decision latency over the same seeded games). Separate from 5.5 on purpose: that gate is about legality, this is about quality, and only the first is a release blocker There is no TypeScript tournament report to compare against — phase 1 was decided on code evidence, not a tournament (see `docs/bot/BOT-ENGINE-DECISION.md`). Instead, generate a TypeScript baseline with `npm run recordings:generate` and compare the Kotlin bot's aggregate results (final scores, coalition win rate, decision latency) against it over the same number of seeded games

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
- [ ] 6.4 `GameRecorder` + export + local auto-save (`expect/actual` storage)
- [ ] 6.5 `BotAIAdapter` coroutine port (sequential queue, injectable delays, all phases, coalition planner routing, leader auto-selection, opponent tracking) usable by `LocalGameSession` and the server
- [ ] 6.6 Port `bot-tossin` and `coalition-final-round` integration tests under `runTest` against `LocalGameSession`
- [ ] 6.7 Kotlin self-play recording generator; CI job `parity-roundtrip` replays 20 fresh Kotlin recordings with `tools/replay-recording.ts` — green
- [ ] 6.8 Koin modules; sample headless JVM runner playing a full bot game

## 7. Compose Multiplatform UI (single player first)

- [~] 7.1 Prototype gate: **decided — an overlay layer**, and it is built and running on an
      Android emulator: a card leaves the deck, crosses the table and lands on the pile, drawn
      above the felt rather than by rearranging it. `LookaheadScope` was the alternative and is
      the wrong shape here — a card going from a hand to the discard passes over three other
      hands, and animating it in place means making room for it in every one of them. Not yet
      run on a physical phone or on iOS
- [~] 7.2 Design tokens (light/dark), typography, theme: **done**, and deliberately *not*
      Material-looking. Material's own button is a stadium, and it was the single thing that
      made the screen read as an Android app rather than a game. The controls are now the web
      app's: four-pixel corners, a solid colour, a small shadow, on a dark rail that stays dark
      in both themes. The colours carry meaning, ported from `BUTTON_ACTION_VARIANTS` — green
      gets on with the turn, blue puts a card in a hand, slate declines, orange ends the round,
      amber names a rank — so a player who learned them on the web does not learn them twice.
      Reduced-motion is **not** honoured yet
- [x] 7.9 Help: a "?" on the panel opens what the rules say about whatever is happening — the
      card in hand, or the phase — followed by every rank and what it does. The words are
      `CARD_CONFIGS`, ported with the engine, so the web app and this teach the same game. A
      drawn action card also explains itself inline, without being asked
- [~] 7.3 Screens/navigation: opening, home (with **Continue** when there is a game to come
      back to), game table, and an end-of-round score sheet with hand / round / game columns.
      Lobby, settings and the debug screens are not started
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
- [~] 7.5 Animation: card flight, card flip on reveal, a pulse on what can be tapped and a glow
      on the seat whose turn it is. The flights are derived by a pure `flightsFor` and tested;
      the overlay draws them. **Bots do not yet wait for animations** — they play immediately
      and the flight catches up
- [ ] 7.6 Accessibility semantics for cards/controls, touch targets, large fonts, landscape
- [ ] 7.7 Export/share recording; restore last local game on relaunch
- [ ] 7.8 UI tests: Compose UI tests for a scripted full game (bots with zero delay); screenshot tests for key screens light/dark

## 8. Delivery (single player)

- [ ] 8.1 Android: signing, Play internal track from CI on tags; iOS: bundle id, TestFlight from CI
- [ ] 8.2 Sentry Kotlin Multiplatform SDK in `composeApp` (breadcrumbs: game id + action index), privacy manifest/permissions review
- [ ] 8.3 Release gate checklist: `kmp-jvm` parity green, device parity green, `parity-roundtrip` green, tournament within 5 pp, UI tests green
- [ ] 8.4 Docs: `docs/kotlin/ARCHITECTURE.md`; update `README.md`/`CLAUDE.md` with the Kotlin workspace and the "rules change must update both engines + fixtures" policy

## 9. Online multiplayer

- [ ] 9.1 `shared/protocol`: message types (`join`, `action`, `event{index, action, view}`, `resync`, `error`) with serializers shared by client and server; protocol doc `docs/kotlin/PROTOCOL.md`
- [ ] 9.2 `worker` (Cloudflare Worker + Durable Object per room, Kotlin/JS): rooms (create/join by code, always 4 seats, host start, bot fill), the Durable Object owning `GameState`, authoritative validate/apply via `shared/engine`, `BotAIAdapter` running in the object (never on a client — a client cannot decide a bot move without that seat’s hidden cards), per-seat `projectView` broadcast, `GameRecorder` per room, WebSocket Hibernation so idle rooms cost no duration
- [ ] 9.3 Reconnection/resync by recording index; idempotent (seat, index) handling; grace period → bot takeover of a disconnected seat
- [ ] 9.4 Human pacing: toss-in ready timeout, coalition-leader selection timeout (configurable per room)
- [ ] 9.5 Guest identity (device-bound id + nickname), room codes/links
- [ ] 9.6 `RemoteGameSession` (Ktor client WebSocket, or platform WebSocket on Wasm) implementing `GameSession`; offline detection and reconnect UX
- [ ] 9.7 Lobby UI (create/join/seats/start), in-game connection indicators; seat-agnostic game screen verified with 2 humans + 2 bots and 4 humans
- [ ] 9.8 Multi-client test harness (scripted human clients + bots vs a local server) producing recordings; add to `parity-roundtrip`
- [ ] 9.9 Server observability: Sentry JVM (game id + action index), recordings persisted for finished games; container build + deployment (provider decided here); load test with 100 concurrent rooms
- [ ] 9.10 Store releases with multiplayer enabled

## 10. Follow-up: one engine

- [ ] 10.1 Open the web follow-up change: retire `packages/engine`/`packages/bot` in favour of the Kotlin engine (Kotlin/JS-backed web or Compose Web), with a decision record — until then the parity gate keeps both engines identical
