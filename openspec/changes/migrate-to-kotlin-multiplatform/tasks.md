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

- [ ] 2.1 Create `kmp/` Gradle workspace (Kotlin 2.x, KMP plugin, Compose Multiplatform, version catalog); modules `shared:shapes`, `shared:engine`, `shared:recording`, `shared:bot`, `shared:client`, `shared:protocol`, `server`, `parity-tests`, `composeApp`, `iosApp`
- [ ] 2.2 Targets: `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `jvm`; kotlinx.serialization/coroutines/datetime, Koin, Ktor (client + server), kotlin.test, Turbine
- [ ] 2.3 GitHub Actions: `kmp-jvm` (unit + parity), `kmp-android` (assemble), `kmp-ios` (macos: build framework + simulator tests), `kmp-server` (tests + container build), Gradle/Konan caching
- [ ] 2.4 Developer docs: `docs/kotlin/README.md` (setup, module map, how to run parity, how to run the server locally)

## 3. Shared shapes, PRNG, recording

- [ ] 3.1 Port `shapes`: `Card`, `Rank`, `Pile`, `GameState`, `PlayerState`, `PendingAction`, `ActiveTossIn`, history types, enums with TS string values, `CARD_CONFIGS` and helpers
- [ ] 3.2 `GameAction` sealed hierarchy + polymorphic serializer producing `{ type, payload }`; round-trip tests against JSON samples exported from TypeScript
- [ ] 3.3 `Prng` (mulberry32) passing the published test vectors
- [ ] 3.4 `shared/recording`: `GameRecording` v1 model, canonical JSON writer, SHA-256 (multiplatform), `hashGameState`; tests: canonical strings byte-equal to TypeScript samples
- [ ] 3.5 Purity guard test for `shared/engine` sources (no clocks/random/uuid)

## 4. Engine port (parity gate #1)

- [ ] 4.1 `ActionValidator` + `GameEngine.reduce` + `ReduceResult`
- [ ] 4.2 Port `utils/` (toss-in flow incl. `getAutomaticallyReadyPlayers`, `advanceTurnAfterTossIn` with seeded reshuffle, `clearTossInAfterActionableCard`, action-utils, scoring)
- [ ] 4.3 Port every case handler file-for-file (draw, play-discard, swap-card w/ declaration, discard, use-card, select-action-target, confirm/skip peek, jack/queen execute/skip, declare-king-action, participate-in-toss, player-toss-in-finished, finish-toss-in, call-vinto, set-coalition-leader, peek-setup-card, finish-setup, process-ai-turn, set-next-draw-card, swap-hand-with-deck, update-difficulty)
- [ ] 4.4 Port TypeScript engine tests (rules, scenarios, card-actions, toss-in-state) to kotlin.test — all green
- [ ] 4.5 `projectView(state, playerId): PlayerView` redaction + property test "no hidden card leaks" over recorded states; coalition-leader visibility rule
- [ ] 4.6 `replayRecording()` in Kotlin with divergence report
- [ ] 4.7 `parity-tests`: replay full `fixtures/recordings/` corpus on JVM — green; wire into `kmp-jvm`
- [ ] 4.8 Run the corpus on Android emulator + iOS simulator (CI job, nightly/pre-release) — green

## 5. Bot port (the single winner from phase 1)

- [ ] 5.1 Port deterministic components (heuristics, evaluation helpers, opponent modeler, Vinto round solver, action planning, score estimator) with 1:1 tests
- [ ] 5.2 Port `coalition-planner` with its scenario tests (identical decisions)
- [ ] 5.3 Port the search/decision core (if MCTS: types, node, move generator, state transition, determinization, rollout policy, evaluators) with injected `Random` and iteration budget; port its tests with fixed seeds
- [ ] 5.4 `BotDecisionServiceFactory.create(difficulty)`; `suspend` decision API on `Dispatchers.Default`
- [ ] 5.5 Kotlin self-play strength check (JVM main). There is no TypeScript tournament report to compare against — phase 1 was decided on code evidence, not a tournament (see `docs/bot/BOT-ENGINE-DECISION.md`). Instead, generate a TypeScript baseline with `npm run recordings:generate` and compare the Kotlin bot's aggregate results (final scores, coalition win rate, decision latency) against it over the same number of seeded games

## 6. Client port: `GameSession` (parity gate #2, round trip)

- [ ] 6.1 `GameSession` interface, `SessionEvent`, `PlayerView` flows (`view`, `visualView`, `syncVisualState()`)
- [ ] 6.2 `LocalGameSession`: engine + recorder + bots in-process; history with deterministic sequence numbers
- [ ] 6.3 `initializeGame` seeded (same deck order/deal/`gameId` as TypeScript) — initial-state hash equality test vs TypeScript samples
- [ ] 6.4 `GameRecorder` + export + local auto-save (`expect/actual` storage)
- [ ] 6.5 `BotAIAdapter` coroutine port (sequential queue, injectable delays, all phases, coalition planner routing, leader auto-selection, opponent tracking) usable by `LocalGameSession` and the server
- [ ] 6.6 Port `bot-tossin` and `coalition-final-round` integration tests under `runTest` against `LocalGameSession`
- [ ] 6.7 Kotlin self-play recording generator; CI job `parity-roundtrip` replays 20 fresh Kotlin recordings with `tools/replay-recording.ts` — green
- [ ] 6.8 Koin modules; sample headless JVM runner playing a full bot game

## 7. Compose Multiplatform UI (single player first)

- [ ] 7.1 Prototype gate: game table layout + one animated card move + one bot turn on a physical iPhone and Android phone; decide animation approach (LookaheadScope vs overlay layer)
- [ ] 7.2 Design tokens (light/dark), typography, Material 3 theme; reduced-motion support
- [ ] 7.3 Screens/navigation: home (single player / create room / join room), new-game settings (difficulty), lobby, game table, final scores, help/rules, settings, debug (recordings list, replay stepper)
- [ ] 7.4 Game table composables, seat-agnostic (render for `localPlayerId`): player areas (local seat bottom, others around), piles, pending card, action-target selection, rank declaration, King declaration, Jack/Queen flows, toss-in bar with continue, Vinto button, coalition leader modal, coalition status/turn indicator, waiting indicators
- [ ] 7.5 Animation service port + `syncVisualState()` contract; bots proven to wait for animations (instrumented test)
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
- [ ] 9.2 `server` (Ktor): rooms (create/join by code, always 4 seats, host start, bot fill), room coroutine owning `GameState`, authoritative validate/apply via `shared/engine`, server-side `BotAIAdapter`, per-seat `projectView` broadcast, `GameRecorder` per room
- [ ] 9.3 Reconnection/resync by recording index; idempotent (seat, index) handling; grace period → bot takeover of a disconnected seat
- [ ] 9.4 Human pacing: toss-in ready timeout, coalition-leader selection timeout (configurable per room)
- [ ] 9.5 Guest identity (device-bound id + nickname), room codes/links
- [ ] 9.6 `RemoteGameSession` (Ktor client WebSocket) implementing `GameSession`; offline detection and reconnect UX
- [ ] 9.7 Lobby UI (create/join/seats/start), in-game connection indicators; seat-agnostic game screen verified with 2 humans + 2 bots and 4 humans
- [ ] 9.8 Multi-client test harness (scripted human clients + bots vs a local server) producing recordings; add to `parity-roundtrip`
- [ ] 9.9 Server observability: Sentry JVM (game id + action index), recordings persisted for finished games; container build + deployment (provider decided here); load test with 100 concurrent rooms
- [ ] 9.10 Store releases with multiplayer enabled

## 10. Follow-up: one engine

- [ ] 10.1 Open the web follow-up change: retire `packages/engine`/`packages/bot` in favour of the Kotlin engine (Kotlin/JS-backed web or Compose Web), with a decision record — until then the parity gate keeps both engines identical
