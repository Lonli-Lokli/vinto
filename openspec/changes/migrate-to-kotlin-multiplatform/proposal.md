# Change: Migrate Vinto to Kotlin Multiplatform + Compose Multiplatform (Android & iOS)

## Why

Vinto should become a native mobile game on Android and iOS. The current stack (Next.js /
React / MobX) is web-first; wrapping it in a webview would give a poor game feel and no
shared path to a future authoritative multiplayer server. Kotlin Multiplatform lets one
Kotlin codebase provide the engine, bot and client logic for Android, iOS and (later) a
JVM server, and Compose Multiplatform lets one UI target both phones.

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
  offline; 2–4 humans in a 4-seat room with bots filling empty seats; all-human tables of 4. A server-authoritative Ktor service runs the
  *same* shared engine and bots on the JVM, validates every action, records the game, and
  sends each seat only what it may see (redacted views). Guest identity only.
- **Web**: stays on the TypeScript engine until the Kotlin engine passes the parity gate;
  a mandatory follow-up change retires the TypeScript engine so exactly one engine remains.

## Impact

- Affected specs: `kmp-shared-engine`, `kmp-bot`, `kmp-game-client`, `mobile-app`,
  `cross-implementation-parity`, `online-multiplayer` (all new)
- Depends on: change `add-game-recording-replay` (recording format v1, fixtures, PRNG
  test vectors, `docs/game-engine/RECORDING.md`) — MUST be archived first
- Affected code: new `kmp/` tree (incl. `server`); `fixtures/recordings/` consumed by
  both stacks; `.github/workflows` gains Kotlin jobs; `docs/` gains Kotlin developer docs;
  `tools/tournament.ts` added and the losing TypeScript bot deleted. Otherwise no removal
  of TypeScript packages in this change (the web follow-up does that).
- Non-goals: web UI in Compose (follow-up change), user accounts, monetisation,
  hot-seat/pass-and-play on one device, spectator mode.
