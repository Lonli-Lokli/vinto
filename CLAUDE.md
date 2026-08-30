# Vinto Project - Claude Code Guide

  ## The repository is a Kotlin Multiplatform build

  **Read this before running anything.** The Gradle build is the repository root: `./gradlew`
  at the top level, `shared/*`, `composeApp/`, `worker/`, `iosApp/`. It is the build that
  ships the game — Android, iOS, Compose web, and the Cloudflare Worker that hosts a room.

  A Next.js client came first. It is **deleted**: it stopped shipping long ago, its CI went
  before it, and the last thing it was for — regenerating the parity corpus — is now a
  deliberate non-goal (`fixtures/recordings/README.md`).

  At the root beside the build: `fixtures/` (the frozen corpus and the PRNG vectors), `docs/`,
  `openspec/`.

  ## Quick Reference
  See @README.md for project overview and architecture
  See @docs/kotlin/ARCHITECTURE.md for the shape of the system: the invariants and why
  See @docs/kotlin/README.md for the Kotlin workspace: setup, module map, commands, traps
  See @docs/game-engine/README.md for game engine documentation
  See @docs/game-engine/VINTO_RULES.md for complete game rules
  See @docs/game-engine/SCENARIOS.md for worked examples and edge cases

  ## Project Overview
  A strategic multiplayer card game. The rules live **once**, in Kotlin, as a pure reducer.
  They used to live twice; what survives that is a frozen replay corpus carrying the state
  hashes the other implementation computed.

  **Technology Stack (Kotlin, the shipping one):**
  - Language: Kotlin 2.1, Kotlin Multiplatform
  - UI: Compose Multiplatform (Android, iOS, wasmJs)
  - Game Engine: Pure reducer (`GameEngine.reduce`), deterministic, seeded PRNG
  - Server: Cloudflare Worker + Durable Object per room (Kotlin/JS)
  - AI: Monte Carlo Tree Search (MCTS)
  - Build: Gradle 8.14, JDK 17 (every module sets `jvmTarget = 17`; a newer JDK breaks it)
  - Static analysis: detekt, `maxIssues: 0`

  ## Repository Structure

  shared/
    shapes/          # Card, Rank, GameState, GameAction, canonical JSON, SHA-256, Prng
    engine/          # GameEngine.reduce, ActionValidator, case handlers, replay
    bot/             # MCTS decision service, coalition planner, BotRunner
    client/          # GameSession: LocalGameSession and RemoteGameSession
    protocol/        # The wire, declared once (see docs/kotlin/PROTOCOL.md)
    room/            # Room and registry cores, testable off the Worker
  composeApp/        # Compose Multiplatform UI — one commonMain for all three clients
  worker/            # Cloudflare Worker + Durable Object; JS shim in worker/cloudflare/
  iosApp/            # Xcode project embedding composeApp's framework (macOS only)
  tools/             # Icon and sound generators (Python)
  fixtures/          # The cross-implementation corpus: 50 recordings + PRNG vectors

  ## Development Commands

  **Kotlin — from the repository root:**
  ```bash
  ./gradlew :shared:engine:jvmTest        # the parity gate for one module
  ./gradlew detekt                        # static analysis, every module
  ./gradlew :composeApp:assembleDebug     # Android APK
  ./gradlew :composeApp:jvmTest           # Compose UI suites, headless
  ./gradlew :worker:jsProductionExecutableCompileSync   # the Worker bundle
  ```

  Full command list, including iOS and the Worker gates: `docs/kotlin/README.md` §4.

  ```bash
  ./gradlew :composeApp:run               # the desktop window — fastest look at a UI change
  ./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament   # bot strength
  ```

  Coding Conventions

  Kotlin:
  - `data class` / `sealed interface` for state and actions; exhaustive `when` with no `else`,
    so a new case is a compile error rather than a screen that says nothing
  - Pure functions in the engine; no platform APIs in shared modules
  - Comments explain **decisions**, not signatures
  - Test names are sentences (`theWholeFinalRoundIsHandedToTheTable`) — backticked names with
    spaces are JVM-only and much of the suite is `commonTest`

  State Management:
  - `GameState` is immutable and authoritative
  - A screen only ever sees a redacted `PlayerView`; never duplicate game state in UI state
  - Compose state holds presentation only: modals, highlights, the animation queue

  Game Engine:
  - All game logic is pure — no side effects, no clock, no ambient randomness
  - Actions are serialisable; every one goes through `ActionValidator` before `reduce`
  - Randomness comes from `GameState.rngState` alone

  Static analysis:
  - detekt runs with every rule the tool ships. Where this project disagrees with a rule, the
    disagreement is written beside it in `config/detekt/detekt.yml` — argue there rather than
    adding a `@Suppress`
  - One baseline per module; fix an entry and delete its line, never regenerate to go green

  Architecture Principles

  1. Single Source of Truth

  All game state lives in GameState (immutable). No parallel state in stores.

  2. Pure Game Engine

  GameEngine contains only pure functions (reducers):
  - No side effects
  - No async operations
  - Deterministic
  - Cloud-ready

  3. Actions as Data

  All interactions are serializable actions dispatched to GameEngine

  4. Bot AI Integration

  Bots use the same action dispatch path as humans via MCTS algorithm

  Git Workflow

  Main Branch: master
  Kotlin work: `kotlin` (the Kotlin Multiplatform migration; not yet merged to master)

  **Branch Naming for Issues:**
  - Pattern: `issue-{ISSUE_NUMBER}`
  - Example: Issue #10 → branch `issue-10`
  - Do NOT append timestamps or prefixes
  - Reuse existing issue branch if it exists

  **Invoking Claude Code (GitHub App):**
  When commenting on an issue to invoke Claude Code:
  ```
  @claude-code please work on this issue using branch issue-{ISSUE_NUMBER}
  ```

  Example for issue #15:
  ```
  @claude-code please fix this bug using branch issue-15
  ```

  Pre-commit Hooks:
  Uses lefthook for git hooks

  Common Tasks

  Adding a New Game Action — **one engine, and a corpus that is not regenerated**:
  1. Add the type to `shared/shapes/.../GameAction.kt`
  2. Add a handler under `shared/engine/src/commonMain/.../cases/`, wired into
     `GameEngine.reduce`
  3. Teach `ActionValidator` when it is legal — **including who may send it**; that check is
     the whole anti-cheat boundary
  4. Dispatch it from the UI

  If the change moves any recorded state, `CorpusReplayTest` goes red. The corpus is almost
  certainly the thing that was right: it carries hashes a second implementation computed, and
  it is **frozen** — `CorpusIsFrozenTest` fails if a recording changes, and
  `fixtures/recordings/README.md` says what to do instead of rewriting it.

  Testing Game Logic:
  GameEngine is pure, so tests are straightforward unit tests

  Coalition Analysis Files (Untracked)

  Currently in the repo but not committed:
  - COALITION_EVIDENCE.md
  - COALITION_MODE_ANALYSIS.md
  - COALITION_TEST_RESULTS.md

  These appear to be analysis/test documentation for coalition game mode features.

  Dependencies of Note

  Kotlin (see gradle/libs.versions.toml for the pinned versions):
  - kotlinx-serialization: the wire format and the recording format
  - kotlinx-coroutines: GameSession, the bot queue, the room's pacing
  - Compose Multiplatform: the UI, one commonMain for Android, iOS and web
  - detekt 2: static analysis, all rules, failOnSeverity = Info

  AI:
  - Custom MCTS in `shared/bot/`, verified by rule-following rather than decision parity

  Testing

  Kotlin: kotlin.test + coroutines-test, run through Gradle. The parity gate is
  `:shared:engine:jvmTest`, which replays every recording in `fixtures/recordings/`.

  Error Handling

  Client-Side:
  - `Crashes` (`composeApp/.../crash/`) installs a process-level handler **before the first
    composition** on all four targets, and posts a hand-built Sentry envelope. The crashing
    thread waits for the send, and the envelope is written to the vault first so a report
    survives the process dying
  - Errors that cross the wire are **values, not exceptions**: `RoomAnswer`, `SendOutcome` and
    `RoomTrouble` are sealed, so a call site that forgets a failure is a compile error

  Deployment

  Two `workflow_dispatch` workflows publish, and neither runs on a push — deploying is a
  decision rather than a consequence of merging:

  - `deploy-room.yml` — the room Worker at `vinto-room.kupalinka.app` (live and open)
  - `deploy-web.yml` — the website Worker at `vinto.kupalinka.app`

  Both claim their hostname from `routes` in their own `wrangler.jsonc`, so there is no
  dashboard step. Android and iOS go through the stores (phase 8, not yet set up). The
  maintainer's runbook is `docs/kotlin/README.md` §6i, and DEPLOYMENT.md is the plain-language
  version.

  Notes for Claude Code

  - This is a game project, so focus on game logic correctness and state immutability
  - Always maintain determinism in GameEngine
  - Bot AI uses MCTS, so performance matters for decision-making
  - The architecture is designed for future cloud/multiplayer support
  - When modifying game logic, consider edge cases documented in SCENARIOS.md