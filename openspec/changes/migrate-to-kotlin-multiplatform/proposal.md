# Change: Migrate Vinto to Kotlin Multiplatform + Compose Multiplatform (Android & iOS)

## Why

Vinto should become a native game on Android and iOS, with the existing web client joining
the same online games. The current stack (Next.js / React / MobX) is web-first; wrapping it
in a webview would give a poor game feel and no shared path to an authoritative multiplayer
server. Kotlin Multiplatform lets one Kotlin codebase provide the engine, bot and client
logic for **all four runtimes** — Android native, iOS native, the browser (Kotlin/Wasm) and
the server (Kotlin/JS inside a Cloudflare Durable Object) — and Compose Multiplatform lets
one UI target all three clients.

The existing architecture is well suited to the port: a pure reducer engine over an
immutable, serialisable `GameState`, actions as JSON data, bots dispatching through the
same path as humans, and a UI layer that only reacts to state. With change
`add-game-recording-replay` in place, we can _prove_ the Kotlin engine is behaviour-identical
by replaying recorded TypeScript games and comparing state hashes after every action.

## What Changes

- New Gradle multi-module Kotlin Multiplatform workspace under `kmp/` in this monorepo:
  `shared:shapes`, `shared:engine`, `shared:bot`, `shared:client`, `shared:recording`,
  `parity-tests`, `composeApp` (Android + iOS), `iosApp` (Xcode host).
- **Shared engine** ported 1:1 from `packages/engine` (+`shapes`): same state model,
  same action names/payloads, same validator and handlers, seeded PRNG identical to the
  TypeScript one, JSON-compatible with existing recordings.
- **Cross-implementation parity gate**: the Kotlin engine replays every fixture in
  `fixtures/recordings/` with identical per-action hashes; Kotlin-recorded games replay in
  the TypeScript engine. Both directions run in CI. This gate blocks any release.
- **One bot engine**: before porting, a TypeScript tournament decides between today's
  `v1` (MCTS) and `v2` (strategic) bots; the loser is removed from the TypeScript codebase
  and only the winner is ported (heuristics, opponent modeler, coalition planner exactly,
  with the same unit tests; search with injectable RNG/iteration budget, validated
  statistically, running off the main thread).
- **Shared client** built around a `GameSession` abstraction: `LocalGameSession`
  (in-process engine + bots, offline single player) and `RemoteGameSession` (online);
  per-seat redacted `PlayerView`s, logical + visual state as `StateFlow`, `BotAIAdapter`
  as coroutine, `GameRecorder` writing v1 recordings, seeded `initializeGame`.
- **Compose Multiplatform app** with feature parity to the web game: setup peeks, turn
  actions, all card actions, toss-in, Vinto, coalition final round, scoring, settings,
  help, light/dark theme, accessibility semantics; export/share recordings; local
  persistence of last game.
- **Platform delivery**: Android (Play internal track), iOS (TestFlight), Sentry crash
  reporting, CI on GitHub Actions (JVM tests, Android build, iOS build + simulator tests,
  parity).
- **Online multiplayer with humans** (every game is exactly 4 players): 1 human vs 3 bots
  offline; 2–4 humans in a 4-seat room with bots filling empty seats; all-human tables of 4.
  A server-authoritative **Cloudflare Durable Object per room** runs the _same_ shared
  engine and bots (as a Kotlin/JS bundle), validates every action, records the game, and
  sends each seat only what it may see (redacted views). Guest identity only. There is no
  JVM server — the target is Cloudflare's free tier, which runs JS/Wasm — and bots must run
  in the room process, since a client cannot decide a bot's move without being handed that
  seat's hidden cards.
- **Web**: rewritten as a Compose Multiplatform (Kotlin/Wasm) client, so Android, iOS and
  the browser share one UI codebase and one engine and can play in the same game. The
  existing Next.js app keeps running until that client reaches parity, then it and
  `packages/engine`/`packages/bot` are retired **inside this change** — the previously
  planned follow-up change is no longer needed. Accepted cost: the current Playwright e2e
  suite and WCAG 2.1 AA work must be re-established in Compose.

## Impact

- Affected specs: `kmp-shared-engine`, `kmp-bot`, `kmp-game-client`, `mobile-app`,
  `cross-implementation-parity`, `online-multiplayer` (all new)
- Depends on: change `add-game-recording-replay` (recording format v1, fixtures, PRNG
  test vectors, `docs/game-engine/RECORDING.md`) — MUST be archived before **phase 2 and
  everything after it**. The one exception is **phase 1** (the bot tournament and the
  removal of `botVersion`): it needs only that change's determinism work (§1) and must
  land **before** its fixture corpus is committed (§3.5), because removing `botVersion`
  from `GameState` changes every canonical hash. This is the single point where the two
  changes interleave; see the prerequisite note in `tasks.md`.
- Affected code: new `kmp/` tree (incl. `server`); `fixtures/recordings/` consumed by
  both stacks; `.github/workflows` gains Kotlin jobs; `docs/` gains Kotlin developer docs;
  `tools/tournament.ts` added and the losing TypeScript bot deleted. Otherwise no removal
  of TypeScript packages in this change (the web follow-up does that).
- Non-goals: web UI in Compose (follow-up change), user accounts, monetisation,
  hot-seat/pass-and-play on one device, spectator mode.
