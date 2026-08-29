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

## Tech Stack (current — Kotlin, and it is what ships)

- Kotlin 2.1, Kotlin Multiplatform. Targets: `jvm`, `android`, `js(IR)`, `wasmJs`, and
  `iosArm64`/`iosSimulatorArm64`/`iosX64` on macOS
- Compose Multiplatform for the UI — **one `commonMain` for Android, iOS and web**
- Gradle (Kotlin DSL) at the repository root, JDK 17 toolchain, conventions in `build-logic`
  (`vinto.kmp.library`), detekt + detekt-formatting at `maxIssues: 0`, Kover for coverage
- `shared/shapes` — `Card`, `Rank`, `GameState`, `GameAction`, canonical JSON, SHA-256, `Prng`
- `shared/engine` — `GameEngine.reduce`, `ActionValidator`, one handler per action, replay
- `shared/bot` — MCTS decision service, coalition planner, `BotRunner`
- `shared/client` — `GameSession`: `LocalGameSession` (solo, no socket) and `RemoteGameSession`
- `shared/protocol` — the wire, declared once
- `shared/room` — room and registry cores, testable off the Worker
- `composeApp` — the UI; `./gradlew :composeApp:run` is the desktop dev loop
- `worker` — Cloudflare Worker + one Durable Object per room (Kotlin/JS)
- **No DI framework.** Koin was in the original target stack and was never adopted;
  construction is explicit. Do not reintroduce it without a reason that is not habit
- **No JVM/Ktor server.** The authority is a Durable Object per room (design D1, D9)

## Tech Stack (retired, TypeScript — `legacy-web/`, being deleted)

The Next.js client is frozen and on its way out (`docs/kotlin/README.md` §1d). Its CI is
already removed. `fixtures/` stays at the repository root and the Kotlin engine keeps
replaying it, but once `legacy-web/` goes the corpus can no longer be *regenerated* — it
becomes a frozen gate against Kotlin drift rather than live evidence that two engines agree.
Anything below describing TypeScript as current is history, kept for the port's context.


- Nx 22 monorepo, Node 22, TypeScript 5.9 (strict), Vitest, ESLint, Prettier (CRLF, single quotes)
- `packages/shapes` — shared types (`GameState`, `PlayerState`, `Card`, `Rank`, `Pile`), card configs, `GameAction` union
- `packages/engine` — pure reducer engine: `GameEngine.reduce(state, action)`, `action-validator.ts`, one handler per action in `cases/`, `utils/` (toss-in flow, scoring)
- `packages/bot` — MCTS bot (`mcts-*.ts`), heuristics, opponent modeler, `coalition-planner.ts` (final-round joint planner)
- `packages/local-client` — MobX `GameClient` (logical + visual state, animation sync), `BotAIAdapter` (drives bots from MobX reactions), `initializeGame.ts`
- `apps/vinto` — Next.js 15 / React 19 / Tailwind UI, framer-motion animations, Sentry, Vercel
- Docs: `docs/game-engine/VINTO_RULES.md`, `SCENARIOS.md`, `README.md`

### Libraries actually in use

kotlinx.serialization (JSON — the wire and the recording format), kotlinx.coroutines
(`GameSession`, the bot queue, the room's pacing), Compose Multiplatform, kotlin.test.
That is the whole list: no DI container, no date-time library, no networking library beyond
the platform seam in `Net`.

## How planning works here

- `openspec/changes/<name>/` — a proposal in flight: `proposal.md`, `design.md`, `tasks.md`
  and delta specs under `specs/`.
- `openspec/specs/<capability>/spec.md` — **what the game is held to.** Deltas are merged here
  when a change archives, and not before.
- `openspec/changes/archive/` — finished changes, with a table of what each one synced.

A requirement inside a change folder is a proposal; a requirement in `openspec/specs/` is a
commitment. For most of this project's life nothing had been archived, so `specs/` was empty
and two complete changes still read as proposals — worth knowing if a spec seems to be missing.

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

- `master` is main; the Kotlin migration lives on `kotlin` (not yet merged); issue branches `issue-{N}`.
- lefthook pre-commit runs `./gradlew detekt` on Kotlin changes. The two npm hooks went with
  the web client's tooling.
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
- The Kotlin engine has passed the parity gate: all 50 recordings and 13,900 actions replay
  with matching per-action hashes, on the JVM and inside workerd.
- Cloudflare free tier is the budget. The client is a 3.7 MB gzipped wasm bundle — accepted,
  not comfortable — so a dependency added to `composeApp` is a decision, not a detail.
- No cookies, no identifiers, GPC/DNT honoured (`docs/kotlin/README.md` §6c). This binds
  analytics as much as anything else.

## External Dependencies

- **Cloudflare** — Workers + Durable Objects for rooms, Pages for the web client. The free
  tier is the budget.
- **Analytics**: none yet, and it is a release gate — `openspec/changes/add-live-analytics`
  must land before the room opens (§6i step 3).
- **Crash reporting**: none yet. Sentry is planned (tasks 8.2 and 9.9) and stays a separate
  pipe from analytics on purpose.
- Vercel hosted the retired web client and is not part of the Kotlin deployment.
