# Project Context

## Purpose

Vinto is a strategic multiplayer card game: one human against three MCTS-driven bots
(4–5 players supported by the rules). Players minimise their hand total; a player who
believes they are lowest calls **Vinto**, the others form a **coalition** for one final
round, and the round is scored caller-vs-lowest-coalition-hand.

The long-term goal is a **native mobile game (Android + iOS)** built on **Kotlin
Multiplatform + Compose Multiplatform**, sharing one game engine between clients and a
future multiplayer server. The current TypeScript implementation is the reference: every
rule, edge case and reproducible game recorded from it must behave identically in Kotlin.

## Tech Stack (current, TypeScript)

- Nx 22 monorepo, Node 22, TypeScript 5.9 (strict), Vitest, ESLint, Prettier (CRLF, single quotes)
- `packages/shapes` — shared types (`GameState`, `PlayerState`, `Card`, `Rank`, `Pile`), card configs, `GameAction` union
- `packages/engine` — pure reducer engine: `GameEngine.reduce(state, action)`, `action-validator.ts`, one handler per action in `cases/`, `utils/` (toss-in flow, scoring)
- `packages/bot` — MCTS bot (`mcts-*.ts`), heuristics, opponent modeler, `coalition-planner.ts` (final-round joint planner)
- `packages/local-client` — MobX `GameClient` (logical + visual state, animation sync), `BotAIAdapter` (drives bots from MobX reactions), `initializeGame.ts`
- `apps/vinto` — Next.js 15 / React 19 / Tailwind UI, framer-motion animations, Sentry, Vercel
- Docs: `docs/game-engine/VINTO_RULES.md`, `SCENARIOS.md`, `README.md`

## Tech Stack (target, Kotlin — see change `migrate-to-kotlin-multiplatform`)

- Kotlin 2.x, Kotlin Multiplatform (androidTarget, iosArm64/iosSimulatorArm64, jvm for tests/server)
- Compose Multiplatform for the app UI (Android + iOS from one `composeApp`)
- kotlinx.serialization (JSON), kotlinx.coroutines, kotlinx.datetime, Koin (DI), kotlin.test
- Gradle (Kotlin DSL) multi-module under `kmp/`

## Project Conventions

### Code Style

- TypeScript: kebab-case files, named exports, one component per file, `interface` over `type`, `copy()` for state updates, feature-oriented folders.
- Kotlin: standard Kotlin style, `data class`/`sealed interface` for state and actions, pure functions in the engine, no platform APIs in shared modules.

### Architecture Patterns

- **Single source of truth**: `GameState` is immutable and authoritative; UI stores hold only UI state.
- **Pure engine**: reducers have no side effects, no async, no ambient randomness or clocks. All randomness comes from `GameState.rngState` (seeded PRNG); all ids/timestamps in state are derived deterministically.
- **Actions as data**: every interaction is a serialisable JSON `GameAction`; bots dispatch through the same path as humans.
- **Visual vs logical state**: bots react to _visual_ state (post-animation) so they never act mid-animation.
- **Reproducibility**: a game is fully described by `(initialState, actions[])`; a **GameRecording** JSON can be replayed by any implementation and must reproduce identical states.

### Testing Strategy

- Engine: unit tests per handler + rules/scenario tests (`SCENARIOS.md` is the spec).
- Bot: deterministic parts (heuristics, coalition planner) unit-tested exactly; MCTS statistically.
- Client: adapter integration tests with fake timers.
- Parity: replay committed recording fixtures through every engine implementation; state hashes after every action must match.

### Git Workflow

- `master` is main; issue branches `issue-{N}`; lefthook pre-commit runs lint `--fix` and `nx format:write`.
- Never bypass hooks; commit only when asked.

## Domain Context

- Deck: 52 cards + 2 Jokers. Values: 2–6 face, 7–Q = 10 (7=7, 8=8, 9=9, 10=10 as configured in `CARD_CONFIGS`), K = 0, A = 1, Joker = −1.
- Actions: 7/8 peek own, 9/10 peek opponent, J swap two cards of two players, Q peek two then optional swap, K declare a card's rank → that card leaves the hand and its action plays; A force-draw.
- Toss-in: after any discard, anyone may toss matching-rank cards; wrong rank → penalty. Vinto caller cannot toss in.
- Final round: each non-caller gets one turn; coalition may not target the caller; coalition wins iff lowest coalition total < caller total (tie → caller).

## Important Constraints

- Determinism of the engine is mandatory (replay/parity, future server authority).
- Bot search must not block the UI thread noticeably (mobile).
- Mobile UI must support both light/dark themes and reasonable accessibility (semantics/labels).
- The web app keeps working on the TypeScript engine until the Kotlin engine passes the parity gate.

## External Dependencies

- Sentry (error tracking), Vercel Analytics (web). Mobile equivalents to be selected during migration.
