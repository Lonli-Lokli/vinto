# Design: Kotlin Multiplatform migration (option 1: KMP logic + Compose Multiplatform UI)

## Context

Reference implementation: TypeScript packages `shapes`, `engine`, `bot`, `local-client`
and the Next.js app. Contract for the port: `docs/game-engine/VINTO_RULES.md`,
`SCENARIOS.md`, `RECORDING.md` (format v1, canonical JSON, SHA-256 hashing, mulberry32
PRNG + test vectors) and the committed corpus `fixtures/recordings/`.

The port is done **bottom-up behind a parity gate**: nothing above the engine starts
until the engine replays the whole corpus with identical hashes; nothing ships until the
gate is green in CI in both directions.

## Goals / Non-Goals

- Goals: one Kotlin codebase for Android, iOS **and web**; exact engine parity; exactly one bot
  engine, at least as strong as the better of today's two; game feel (animations) at least
  equal to the web app; online play with humans (always 4 seats, bots fill) on a
  server-authoritative Cloudflare Durable Object per room that runs the same shared engine.
- Non-goals (this change): accounts, monetisation, hot-seat on one device, spectators,
  tablets/desktop layouts beyond "works". Web in Kotlin is now **in** scope (D1, D6), so
  there is no separate follow-up change.

## Decisions

### D1. Repository layout — Gradle workspace inside the monorepo

```
kmp/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
  shared/shapes      # Card, Rank, GameState, PlayerState, Pile, GameAction, card configs
  shared/engine      # GameEngine.reduce, ActionValidator, cases/*, utils/*, replay
  shared/recording   # GameRecording v1, canonical JSON, SHA-256, Prng test vectors
  shared/bot         # heuristics, opponent modeler, coalition planner, MCTS
  shared/client      # GameSession (Local/Remote), BotAIAdapter, GameRecorder, initializeGame
  shared/protocol    # WebSocket message types (join/action/event/resync/error)
  worker             # Cloudflare Worker + Durable Object room (Kotlin/JS bundle)
  parity-tests       # JVM tests replaying ../fixtures/recordings + TS↔KT round trips
  composeApp         # Compose Multiplatform UI (androidMain, iosMain, wasmJsMain, commonMain)
  iosApp             # Xcode project embedding composeApp framework
```

- Kept in the same repo so fixtures, docs and CI live next to the reference. Nx may wrap
  Gradle via `@nx/gradle` later; not required.
- Kotlin 2.x, KMP targets: `androidTarget`, `iosArm64`, `iosSimulatorArm64`,
  `js(IR)` (the Cloudflare Worker), `wasmJs` (Compose web client) and `jvm`
  (tests and tooling only). Compose Multiplatform ≥ 1.8 (iOS stable).
- **There is no JVM server target.** The deployment platform is Cloudflare's free tier,
  which runs JavaScript and WebAssembly, not a JVM — so the authoritative room is a
  Durable Object fed by a Kotlin/JS bundle (D9), not a Ktor container.
- The engine therefore runs in **four** places from one source: Android native, iOS
  native, the browser (Kotlin/Wasm, inside `composeApp`) and the server (Kotlin/JS,
  inside the Durable Object). That is what lets web, Android and iOS share one game, and
  it removes the previously planned "retire the TypeScript engine later" follow-up: there
  is only ever one engine.
- Note the two distinct web toolchains: the **Worker** is Kotlin/JS (`js(IR)`), while the
  **browser client** is Kotlin/Wasm. They are separate build outputs with separate size
  budgets, and both are validated by the prototype gate before any port work begins.
- Libraries: kotlinx.serialization-json, kotlinx.coroutines, kotlinx.datetime, Koin,
  kotlin.test (+ Turbine for flows), Okio (files) or platform APIs behind `expect/actual`,
  a small SHA-256 (e.g. `org.kotlincrypto` or hand-written) so hashing is identical on
  every target.

### D2. State and action model — JSON-identical to TypeScript

- `data class GameState`, `PlayerState`, `Card`, `PendingAction`, `ActiveTossIn`, … with
  the **same field names**; `Pile` is a value class over `List<Card>` serialised as an
  array top-first; `Rank` is an enum with `@SerialName("2")…("Joker")`; `GamePhase`,
  `GameSubPhase`, `TargetType`, `ActionPhase` enums with TS string values.
- `sealed interface GameAction` with `@SerialName` = TS `type` (`"DRAW_CARD"`, …) and a
  `payload` data class per action; a custom polymorphic serializer keeps the TS shape
  `{ "type": …, "payload": … }`.
- Serialisation config: `encodeDefaults = true`, `explicitNulls = false` (TS omits
  `undefined`), `ignoreUnknownKeys = false` in parity tests (strict) / `true` in the app.
- Canonical JSON for hashing is produced by a dedicated writer (sorted keys, no
  whitespace, excluding `botMemory`, `turnActions` and `roundActions`) — not by kotlinx's
  default encoder — to match `RECORDING.md` byte for byte. Because history is outside the
  hash, the Kotlin client is free to word its action descriptions however it likes;
  `opponentKnowledge` **is** hashed and must match exactly.
- Immutability via `copy()`; deep-copy semantics of `fast-copy` become explicit `copy`
  of nested lists (all state types are immutable, so structural sharing is safe).

### D3. Engine port — file-for-file

- `GameEngine.reduce(state, action): ReduceResult` (`Success(state)` / `Rejected(reason)`),
  `ActionValidator`, one file per case mirroring `packages/engine/src/lib/cases/*.ts`,
  utils mirroring `utils/*.ts` (toss-in flow, scoring, action-utils).
- PRNG: `Prng` object implementing mulberry32 exactly (verified against the published
  test vectors), `rngState` in `GameState`.
- Porting order per handler: (1) port TS unit tests for that handler to kotlin.test,
  (2) port the handler, (3) run the replay corpus — divergence reports name the action
  and both states, which localises mistakes to a handler.
- No platform APIs, no clocks, no randomness outside `rngState` (a test scans engine
  sources for `Clock`, `Random`, `System.currentTimeMillis`, `uuid`).

### D4. Bot port — exactly one bot engine

- **Only one bot decision engine exists — decided and done.** The planned v1-vs-v2
  tournament was cancelled because its premise failed: v2 (`strategic-bot-decision.ts`)
  read opponents' actual hidden hands in three places, so a head-to-head would have
  measured how much cheating is worth rather than which bot is stronger. v1 (MCTS, which
  estimates opponents from `BotMemory`) was kept; v2 and `botVersion` are deleted. Full
  reasoning in `docs/bot/BOT-ENGINE-DECISION.md`.
- Consequence for the port: there is exactly one `BotDecisionService` to port, and
  `BotDecisionServiceFactory.create` takes only a difficulty.
- The winner's deterministic components (heuristics, evaluation helpers, opponent modeler,
  Vinto round solver, `coalition-planner`) are ported exactly and their TS unit tests
  ported 1:1 (the coalition planner tests are pure input → decision).
- If the winner is MCTS: same algorithm; `Random` is injected
  (`kotlin.random.Random(seed)`) and the budget is `iterations` and/or `timeLimit`; tests
  use a fixed iteration budget so results are reproducible; production keeps a wall-clock
  cap per difficulty.
- Strength validation: there is no TypeScript tournament report to compare against, since
  phase 1 was decided on code evidence. Generate a TypeScript baseline with
  `npm run recordings:generate` and compare the Kotlin bot over the same number of seeded
  games (final scores, coalition win rate, decision latency). Budget the sample
  deliberately: a self-play game costs ~75 s on a developer machine.
- Threading: `BotDecisionService` methods are `suspend` and run on `Dispatchers.Default`
  on clients. Inside the Durable Object there is one thread and a 30 s CPU budget per
  request — which is precisely what makes server-side MCTS possible on the free tier — so
  the object awaits the decision inline rather than fanning out to a pool.

### D5. Client port — one `GameSession` abstraction for local and online play

- The UI never talks to the engine directly. It talks to a `GameSession`:
  `send(action)`, `events: Flow<SessionEvent>` (accepted action + resulting **player view**),
  `view: StateFlow<PlayerView>`, `localPlayerId`. Two implementations:
  - `LocalGameSession` — in-process: engine + recorder + bots (single player vs bots,
    offline). Holds the full `GameState`.
  - `RemoteGameSession` — WebSocket to the Vinto server (D9); receives redacted views and
    accepted actions, sends the local player's actions.
    Single-player therefore exercises the same code path as online play (an in-process
    "server"), which keeps the two modes from diverging.
- `PlayerView` is a **redaction** of `GameState` for one seat, computed by a pure function
  `projectView(state, playerId)` in `shared/engine`: own cards visible only at
  `knownCardPositions`, opponents' cards visible only where `opponentKnowledge` says so
  (plus temporarily revealed cards for the current action), draw pile as a count, full
  discard pile, all public flags. Bots never use views (they run where the full state is).
- Visual vs logical state stays a client concern: `visualView: StateFlow<PlayerView>` is
  advanced by the animation layer via `syncVisualState()`; the local session's bots (and
  the server's) never wait on client animations — pacing is done with the same delays as
  today, and clients queue events until their animations catch up.
- `BotAIAdapter`: a coroutine collecting the (full-state) flow inside `LocalGameSession`
  and inside the server, `distinctUntilChanged` on today's snapshot fields, sequential
  processing (channel/mutex instead of the promise queue), injectable delays so tests use
  `runTest` virtual time — the local-client integration tests (`bot-tossin`,
  `coalition-final-round`) are ported as-is against `LocalGameSession`.
- `GameRecorder` writes format v1 wherever the full state lives (local session, server);
  the app can export/share its recording and auto-saves the last local game; the server's
  recording is the authoritative log of an online game.
- DI: Koin modules per layer (engine has none; bot factory by difficulty; session
  factory; persistence; network).

### D6. Compose Multiplatform UI

- Single `composeApp` for Android and iOS; Material 3 with the current colour tokens
  (light/dark), typography scaled for phones; portrait-first, landscape acceptable.
- Screens: home (single player / create room / join room), new-game settings
  (difficulty), lobby, game table,
  final scores, help/rules, settings, debug (recordings, replay stepper).
- Game table: player areas (bottom human, left/top/right bots), draw/discard piles,
  pending card, action target selection, toss-in bar, Vinto button, coalition status,
  final-round indicator — one composable per today's React component where sensible.
- Animations: card moves via `LookaheadScope`/`animateBounds` or an explicit overlay layer
  driven by an `AnimationService` port; visual state is only advanced when the animation
  completes (same contract as the web). Reduced-motion setting respected.
- Accessibility: `contentDescription`/`semantics` for every card and control, focus order,
  large-text support (mirrors the WCAG intent of the web app).
- Bot thinking never blocks composition (bot work on `Dispatchers.Default`).

### D7. Parity gate (both directions, in CI)

- `parity-tests` (JVM): replay every `fixtures/recordings/*.json` with the Kotlin engine;
  per-action hash equality; failure prints the divergence report.
- Kotlin self-play generator (`kmp` tool) writes recordings; a Node job replays them with
  the TypeScript engine (`tools/replay-recording.ts`) — proves the JSON contract holds in
  the other direction and that Kotlin-recorded games are reproducible.
- Also run the replay corpus once on an iOS simulator and an Android emulator in CI to
  catch platform-specific numeric/serialisation issues (they should be none — integer-only
  state).
- Gate policy: any divergence blocks merge; fixture regeneration requires a rules
  justification and updates both implementations in the same PR.
- **Shared fixtures reach filesystem-less targets by generation, never transcription.**
  Kotlin/Native has no filesystem for a test to read `fixtures/prng/vectors.json` from, and
  the same will apply to the recording corpus on iOS. The rule: a Gradle task takes the
  committed fixture as a declared input and emits it verbatim as a Kotlin constant (see
  `:shared:shapes:generatePrngVectorsSource`). Hand-copying fixture values into Kotlin is
  forbidden — a copy can drift from the file TypeScript reads, and the parity test would
  then pass while proving nothing. One file, one source of truth, in both languages.

### D8. Delivery

- GitHub Actions: `kmp-jvm` (unit + parity), `kmp-android` (assemble + instrumented
  smoke), `kmp-ios` (macos runner: build framework, simulator tests), `parity-roundtrip`
  (Kotlin recordings → TS replay). Caching Gradle/Konan.
- Distribution: Play internal testing + TestFlight from CI on tags.
- Crash reporting: **Sentry** (decided) — Sentry Kotlin Multiplatform SDK in `composeApp`
  (Android + iOS) and Sentry JVM in the server, same organisation/project family as the
  web app; breadcrumbs include game id + action index so a crash can be paired with the
  recording. Analytics optional.

### D9. Online multiplayer — server-authoritative, same shared engine

- **Modes** (every game is exactly 4 players, no exceptions): 1 human vs 3 bots (offline,
  `LocalGameSession`); 2–4 humans in one 4-seat room with empty seats filled by bots
  (online, `RemoteGameSession`); all-human tables of 4. Room is
  created by a host, joined by code/link, seats are assigned, host starts. Guest identity
  (device id + nickname) only — no accounts in this change.
- **Server**: a **Cloudflare Durable Object per room**, reached through a thin Worker that
  does nothing but route (its 10 ms CPU budget allows nothing more). The Durable Object
  owns the full `GameState`, validates and applies every incoming `GameAction` with the
  _same_ `GameEngine.reduce` compiled to Kotlin/JS, records to a `GameRecording`
  (authoritative log), runs bots with the same `BotAIAdapter`, and broadcasts to each seat
  `{ acceptedAction, index, view = projectView(state, seat) }`. Clients never see other
  players' hidden cards.
- **Why a Durable Object rather than a Worker**: a plain Worker is capped at **10 ms CPU
  per invocation** on the free plan, which an MCTS decision cannot fit. A Durable Object
  gets **30 s of CPU per request** (raisable to 5 min via `limits.cpu_ms`), so bots run
  server-side. A Durable Object is also a single-threaded object with identity, which is
  exactly the "one coroutine owns one room" shape the design already wanted.
- **Bots must run server-side, not on a client.** A client cannot compute a bot's move for
  a seat it does not own without being handed that seat's hidden cards, which would break
  the redaction model and hand that player a cheat. This is a correctness constraint, not
  a preference.
- **WebSocket Hibernation** keeps idle rooms free: the object evicts from memory while its
  sockets stay open, so a room waiting on a slow player costs no duration.
- **Protocol**: WebSocket, JSON messages (`join`, `action`, `event`, `resync`, `error`)
  reusing the `GameAction`/`PlayerView` serialisers; the recording index is the sync
  cursor — a reconnecting client sends its last index and receives the missing events or
  a full view. Idempotent by (seat, index).
- **Real-time humans**: toss-in "ready" and coalition-leader selection keep today's
  mechanics with server-side timeouts (configurable, e.g. 15 s → auto-ready / auto-select);
  turn timers optional; a disconnected human's seat is played by a bot after a grace
  period (rules-neutral because bots use the same action path).
- **Fairness/anti-cheat**: hidden information exists only on the server; the client
  cannot ask for other cards; all validation is server-side (identical validator).
- **Deployment**: `wrangler deploy` from CI. Sharding is automatic — one Durable Object
  per room is the unit of isolation. Finished recordings persist to the object's SQLite
  storage (or R2 if they outgrow it) so every bug report can be replayed.
- **Free-tier budget** (verified against Cloudflare docs, 2026-08): 100k requests/day,
  13,000 GB-s/day duration, 5 GB total SQLite storage, 100k row writes/day. At ~300
  actions per game the binding limits are requests and row writes, which land at roughly
  **300 games/day**, with 5 GB holding about **55,000 recordings**. Ample for hobby scale;
  a paid plan is a pricing decision, not a re-architecture. Durable Objects on the free
  plan are SQLite-backed only.
- **Parity**: the Durable Object runs `shared/engine`, so the parity gate covers the server
  too; a scripted multi-client harness can drive it end-to-end with recorded human actions.

## Risks / Trade-offs

- **Compose on iOS**: stable but younger than SwiftUI; text input/scroll feel is fine for a
  card game with mostly custom drawing. Mitigation: prototype the game table + one card
  animation on an iPhone before committing to the full UI (task 5.1).
- **Serialisation drift**: any accidental field/order difference breaks parity — that is
  the point; the gate catches it immediately.
- **MCTS non-determinism** makes bot parity statistical, not exact; acceptable because
  recordings capture chosen actions and the engine is exact.
- **Two game-engine implementations for a while**: the web stays on TS until the Kotlin
  engine passes the parity gate; every rules change must land in both with fixtures
  updated (enforced by the gate). The end state is **one engine (Kotlin)** running in four
  places — Android, iOS, browser (Wasm) and the Durable Object (JS). There is no separate
  "retire the TypeScript engine" follow-up any more: the web rewrite in Compose is what
  retires it, inside this change.
- **Compose/Wasm bundle size: measured and accepted.** A hello-world Compose web build is
  10.2 MB raw / **3.7 MB gzipped** (8.4 MB of it the skiko renderer), against 2.0 MB /
  620 KB gzipped for the entire current Next.js client. The product owner accepted this
  cost on 2026-08-18 in exchange for one UI codebase across Android, iOS and web. Numbers
  and method in `docs/kotlin/PLATFORM-GATE.md`; do not reopen without new measurements.
  Note it is a floor, not a ceiling — skiko is fixed cost, so the real UI grows it
  sublinearly.
- **Kotlin-to-web bundle size is the biggest unknown, and it is load-bearing twice.** The
  Worker must fit a Kotlin/JS bundle containing the engine _and_ MCTS within Cloudflare's
  script-size limit, and the browser must ship a Compose/Wasm bundle users will actually
  wait for. Compose for web is the least mature Compose target. Both are measured by the
  prototype gate **before** any porting begins, because a failure there changes the design
  (a thinner server-side bot, or a different web strategy) rather than just the schedule.
- **Retiring the Next.js app has a cost worth naming**: the existing web UI, its Playwright
  e2e suite and the WCAG 2.1 AA work go with it. That is accepted in exchange for one UI
  codebase across all three clients, but the accessibility and e2e coverage should be
  re-established in Compose rather than quietly dropped.
- **Online multiplayer** adds a server, a protocol and hidden-information redaction; the
  session abstraction (D5) and the shared engine on the JVM keep this to "same engine, new
  transport", but it is real work and is scheduled after the single-player app is solid.
- Effort concentration: engine+bot+client ports are mechanical (weeks); the UI is the
  bulk (months). Ordering keeps risk front-loaded on the parts we can verify mechanically.

## Migration Plan (phases; see tasks.md)

0. Prerequisite: `add-game-recording-replay` archived; corpus + `RECORDING.md` exist.
1. ~~Bot engine decision by tournament~~ — **done**, decided on code evidence instead; see
   `docs/bot/BOT-ENGINE-DECISION.md`. One bot engine (v1/MCTS), `botVersion` removed.
2. Workspace + CI skeleton.
3. **Platform prototype gate — before any porting.** Build a trivial Kotlin/JS bundle
   containing `shared/engine` + MCTS, deploy it to a Durable Object, and measure: does it
   fit the Worker script-size limit, and can it complete an MCTS decision inside the
   Durable Object CPU budget? Separately, build a hello-world Compose/Wasm page and
   measure its bundle. **A failure here changes the design, so it must not be discovered
   after the UI is written.**
4. `shapes` + PRNG + recording/hashing (test vectors pass).
5. Engine port behind the parity gate (corpus replays green on JVM; then iOS/Android/JS),
   incl. `projectView` redaction.
6. Bot port (unit parity + statistical strength).
7. Client port: `GameSession`/`LocalGameSession` (adapter integration tests green under
   virtual time; Kotlin recordings replay in TS).
8. Compose UI, single player (device prototype → full feature parity → polish/a11y),
   targeting Android, iOS **and web** from one codebase.
9. Online multiplayer: Worker + Durable Object room, protocol, `RemoteGameSession`, lobby
   UI, reconnection, timeouts, `wrangler` deployment.
10. Retire the Next.js app and `packages/engine`/`packages/bot` once the Compose web
    client reaches parity — this replaces the previously separate follow-up change.

## Resolved Questions

- **One bot engine**: v1 (MCTS) vs v2 (strategic) is decided by tournament before the port
  (D4); only the winner exists afterwards, in both stacks.
- **Humans**: 1 human vs 3 bots offline; 2–4 humans online in a 4-seat room with bot fill;
  all-human tables of 4 (D9). **Every game is exactly 4 players** — the engine, server,
  lobby and UI enforce it; there is no player-count setting. Hot-seat/pass-and-play on one device is out of scope (hidden information makes it
  awkward) unless requested later.
- **Crash reporting**: Sentry (D8).

## Open Questions

- Server hosting provider and region(s) — decide in phase 8.
