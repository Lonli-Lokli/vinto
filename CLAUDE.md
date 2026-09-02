# Vinto Project - Claude Code Guide

  ## The repository is a Kotlin Multiplatform build

  **Read this before running anything.** The Gradle build is the repository root: `./gradlew`
  at the top level, `shared/*`, `composeApp/`, `androidApp/`, `worker/`, `iosApp/`. It is the
  build that ships the game — Android, iOS, Compose web, and the Cloudflare Worker that hosts
  a room.

  A Next.js client came first. It is **deleted**: it stopped shipping long ago, its CI went
  before it, and the last thing it was for — regenerating the parity corpus — is now a
  deliberate non-goal (`fixtures/recordings/README.md`).

  At the root beside the build: `fixtures/` (the frozen corpus and the PRNG vectors), `docs/`,
  `openspec/`.

  ## Quick Reference
  See @README.md for project overview and architecture
  See @docs/kotlin/ARCHITECTURE.md for the shape of the system: the invariants and why
  See @docs/kotlin/README.md for the Kotlin workspace: setup, module map, commands, state —
  its §0 says which companion file holds every other section (`CI.md`, `GATES.md`,
  `HOSTING.md`, `ROOM.md`, `UI.md`, `WORDS.md`, `RELIABILITY.md`, `TRAPS.md`, all in
  `docs/kotlin/`; the traps live in `TRAPS.md`)
  See @docs/game-engine/README.md for game engine documentation
  See @docs/game-engine/VINTO_RULES.md for complete game rules
  See @docs/game-engine/SCENARIOS.md for worked examples and edge cases

  ## Project Overview
  **This is an unofficial client for somebody else's game.** VINTO is a card game designed
  and published by other people; the official game is at <https://vinto.game>, and nothing
  here is affiliated with or endorsed by them. Every client says so on its first screen and
  links there (`HomeScreen.kt`, `Pages.OFFICIAL`, held by `AttributionTest`) — treat that
  line, and the two rows in Settings under About, as load-bearing rather than decoration.

  A strategic multiplayer card game. The rules live **once**, in Kotlin, as a pure reducer.
  They used to live twice; what survives that is a frozen replay corpus carrying the state
  hashes the other implementation computed.

  **Technology Stack:**
  - Language: Kotlin 2.4, Kotlin Multiplatform
  - UI: Compose Multiplatform 1.12 (Android, iOS, wasmJs)
  - Game Engine: Pure reducer (`GameEngine.reduce`), deterministic, seeded PRNG
  - Server: Cloudflare Worker + Durable Object per room (Kotlin/JS)
  - AI: Monte Carlo Tree Search (MCTS)
  - Build: Gradle 9.7, JDK 17 (every module sets `jvmTarget = 17`; a newer JDK breaks it)
  - Static analysis: detekt 2, all rules, `failOnSeverity = Info`

  ## Repository Structure

  shared/
    shapes/          # Card, Rank, GameState, GameAction, canonical JSON, SHA-256, Prng
    engine/          # GameEngine.reduce, ActionValidator, case handlers, replay
    bot/             # MCTS decision service, coalition planner, BotRunner
    client/          # GameSession: LocalGameSession and RemoteGameSession
    protocol/        # The wire, declared once (see docs/kotlin/PROTOCOL.md)
    room/            # Room and registry cores, testable off the Worker
  composeApp/        # Compose Multiplatform UI — one commonMain for all three clients
  androidApp/        # The Android application: manifest, MainActivity, icons, signing
  worker/            # Cloudflare Worker + Durable Object; JS shim in worker/cloudflare/
  iosApp/            # Xcode project embedding composeApp's framework (macOS only)
  build-logic/       # Convention plugins
  config/detekt/     # Ruleset and the per-module baselines
  tools/             # Icon and sound generators (Python); the mark in tools/brand/
  fixtures/          # The frozen corpus: 50 recordings + PRNG vectors

  ## Development Commands

  **Kotlin — from the repository root:**
  ```bash
  ./gradlew :shared:engine:jvmTest        # the parity gate for one module
  ./gradlew detekt                        # static analysis, every module
  ./gradlew :composeApp:assembleDebug     # Android APK
  ./gradlew :composeApp:jvmTest           # Compose UI suites, headless
  ./gradlew :composeApp:run               # the desktop window — fastest look at a UI change
  ./gradlew :worker:jsProductionExecutableCompileSync   # the Worker bundle
  ./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament   # bot strength
  ```

  Full command list, including iOS and the Worker gates: `docs/kotlin/README.md` §4.

  Coding Conventions

  Kotlin:
  - `data class` / `sealed interface` for state and actions; exhaustive `when` with no `else`,
    so a new case is a compile error rather than a screen that says nothing
  - Pure functions in the engine; no platform APIs in shared modules
  - Comments explain **decisions**, not signatures
  - Test names are sentences (`theWholeFinalRoundIsHandedToTheTable`) — backticked names with
    spaces are JVM-only and much of the suite is `commonTest`

  State Management:
  - `GameState` is immutable and authoritative — the single source of truth
  - A screen only ever sees a redacted `PlayerView`; never duplicate game state in UI state
  - Compose state holds presentation only: modals, highlights, the animation queue

  Game Engine:
  - All game logic is pure — no side effects, no async, no clock, no ambient randomness
  - Actions are serialisable; every one goes through `ActionValidator` before `reduce`, and
    bots take that same path rather than applying moves directly
  - Randomness comes from `GameState.rngState` alone
  - Purity is what lets the same code be the authority in a Durable Object and the simulator
    inside MCTS. Performance matters here: MCTS runs the reducer thousands of times a turn

  Static analysis:
  - detekt runs with every rule the tool ships. Where this project disagrees with a rule, the
    disagreement is written beside it in `config/detekt/detekt.yml` — argue there rather than
    adding a `@Suppress`
  - One baseline per module; fix an entry and delete its line, never regenerate to go green

  Git Workflow

  Main Branch: master
  Kotlin work: `kotlin` (the Kotlin Multiplatform migration; not yet merged to master)

  **Branch Naming for Issues:**
  - Pattern: `issue-{ISSUE_NUMBER}`
  - Example: Issue #10 → branch `issue-10`
  - Do NOT append timestamps or prefixes
  - Reuse existing issue branch if it exists

  **Invoking Claude Code (GitHub App):** comment on the issue with
  `@claude-code please work on this issue using branch issue-{ISSUE_NUMBER}`

  Pre-commit Hooks:
  Uses lefthook for git hooks

  Fixing a reported bug

  **Reproduce it with a failing test first, then fix it — whenever the bug can be reached by
  a test at all.** The order is the rule: the test is written against the report, watched go
  red on the code as it stands, and only then is the code changed until it goes green. A test
  written after the fix proves the fix compiles; one written before it proves the bug was
  understood. Pick the lowest layer that exhibits it — the engine for a rules fault, the
  client model for a prompt or a log line, the Compose suite for what the screen draws — and
  name the test for the report rather than for the code. The rare bug no test can reach (a
  phone's own keyboard, a store listing) is fixed with a note saying why not.

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

  Dependencies of Note

  Kotlin (see gradle/libs.versions.toml for the pinned versions):
  - kotlinx-serialization: the wire format and the recording format
  - kotlinx-coroutines: GameSession, the bot queue, the room's pacing
  - Compose Multiplatform: the UI, one commonMain for Android, iOS and web

  AI:
  - Custom MCTS in `shared/bot/`, verified by rule-following rather than decision parity

  Testing

  kotlin.test + coroutines-test, run through Gradle. The engine is pure, so its tests are
  ordinary unit tests. The parity gate is `:shared:engine:jvmTest`, which replays every
  recording in `fixtures/recordings/`.

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
  maintainer's runbook is `docs/kotlin/ROOM.md` §6i, and DEPLOYMENT.md is the plain-language
  version.
