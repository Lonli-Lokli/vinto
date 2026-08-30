# Vinto Project - Claude Code Guide

  ## The repository is a Kotlin Multiplatform build

  **Read this before running anything.** The Gradle build is the repository root: `./gradlew`
  at the top level, `shared/*`, `composeApp/`, `worker/`, `iosApp/`. It is the build that
  ships the game — Android, iOS, Compose web, and the Cloudflare Worker that hosts a room.

  The original Next.js client is **retired** and lives under `legacy-web/`, untouched but
  frozen. Its npm scripts still work; they are just run from inside that directory now. It is
  kept because its engine and bot are the other half of the cross-implementation parity gate
  and because `fixtures/recordings` is generated from them — not because anything ships from
  it.

  Shared by both, at the root: `fixtures/` (the parity corpus and PRNG vectors), `docs/`,
  `openspec/`.

  ## Quick Reference
  See @README.md for project overview and architecture
  See @docs/kotlin/ARCHITECTURE.md for the shape of the system: the invariants and why
  See @docs/kotlin/README.md for the Kotlin workspace: setup, module map, commands, traps
  See @legacy-web/package.json for the retired web client's npm scripts
  See @docs/game-engine/README.md for game engine documentation
  See @docs/game-engine/VINTO_RULES.md for complete game rules
  See @docs/game-engine/SCENARIOS.md for worked examples and edge cases

  ## Project Overview
  A strategic multiplayer card game. The rules live twice — once in Kotlin, once in
  TypeScript — and the two are held identical by a replay corpus. Both are pure reducers.

  **Technology Stack (Kotlin, the shipping one):**
  - Language: Kotlin 2.1, Kotlin Multiplatform
  - UI: Compose Multiplatform (Android, iOS, wasmJs)
  - Game Engine: Pure reducer (`GameEngine.reduce`), deterministic, seeded PRNG
  - Server: Cloudflare Worker + Durable Object per room (Kotlin/JS)
  - AI: Monte Carlo Tree Search (MCTS)
  - Build: Gradle 8.14, JDK 17 (every module sets `jvmTarget = 17`; a newer JDK breaks it)
  - Static analysis: detekt, `maxIssues: 0`

  **Technology Stack (legacy-web, frozen):**
  - Next.js 15 (App Router), TypeScript strict, MobX, Nx, Node 22

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
  legacy-web/        # The retired Next.js client and its Nx workspace (frozen)

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

  **Legacy web — from `legacy-web/`:**
  ```bash
  npm test          # all 5 projects
  npm run lint
  npm run format
  npx nx graph
  ```

  Coding Conventions

  File Organization:
  - Use kebab-case for file names: my-component.tsx
  - Components: One component per file
  - Organize by feature, not by type

  TypeScript:
  - Strict mode enabled
  - Always export types/interfaces
  - Prefer interface over type for object shapes
  - Use const for immutable values

  React/Next.js:
  - Use functional components with hooks
  - Prefer named exports for components
  - Use React 19 features
  - App Router (not Pages Router)

  State Management:
  - GameState is immutable and authoritative
  - UI state lives in MobX stores (UIStore, CardAnimationStore)
  - Never duplicate game state in UI stores

  Game Engine:
  - All game logic must be pure functions (no side effects)
  - Actions must be serializable JSON
  - Use copy() for state updates (from fast-copy)
  - Maintain determinism for reproducibility

  Styling:
  - Use Tailwind CSS
  - Component-scoped styles when needed
  - Mobile-first responsive design

  Import Patterns:
  // Prefer named imports
  import { GameEngine } from '@vinto/engine';

  // Dependency injection
  import { inject, injectable } from 'tsyringe';

  // MobX
  import { makeAutoObservable } from 'mobx';
  import { observer } from 'mobx-react-lite';

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

  Adding a New Game Action — **both engines, one change**:
  1. Kotlin: add the type to `shared/shapes/.../GameAction.kt` and a handler under
     `shared/engine/src/commonMain/.../cases/`, wired into `GameEngine.reduce`
  2. TypeScript: the same, in `legacy-web/packages/engine/src/lib/`
  3. Regenerate the corpus (`cd legacy-web && npm run recordings:generate`) and re-run the
     Kotlin parity gate — a rules change that lands in one engine only is what the corpus
     exists to catch. **This step has an expiry date**: `legacy-web/` is being deleted, and
     with it the ability to *regenerate* the corpus. The 51 committed fixtures stay and
     `CorpusReplayTest` still replays them, so the gate against the Kotlin engine drifting
     survives — but it stops being evidence that two implementations agree today. See
     `docs/kotlin/README.md` §1d and ARCHITECTURE.md §7 before relying on the wording above
  4. Dispatch from the UI

  Testing Game Logic:
  GameEngine is pure, so tests are straightforward unit tests

  Working with NX (legacy-web only):
  Use nx-mcp MCP server for NX-specific operations
  View available targets: `cd legacy-web && npx nx show project <project-name>`

  Coalition Analysis Files (Untracked)

  Currently in the repo but not committed:
  - COALITION_EVIDENCE.md
  - COALITION_MODE_ANALYSIS.md
  - COALITION_TEST_RESULTS.md

  These appear to be analysis/test documentation for coalition game mode features.

  Permissions & Restrictions

  Allowed Operations:
  - NX build and test commands
  - NPM operations
  - ESLint

  Denied Operations:
  - TypeScript type checking (npx tsc, npm run typecheck)
  Reason: Performance optimization for Claude Code sessions

  Dependencies of Note

  Kotlin (see gradle/libs.versions.toml for the pinned versions):
  - kotlinx-serialization: the wire format and the recording format
  - kotlinx-coroutines: GameSession, the bot queue, the room's pacing
  - Compose Multiplatform: the UI, one commonMain for Android, iOS and web
  - detekt: static analysis, maxIssues 0

  Legacy web — Game Logic:
  - fast-copy: Immutable state updates
  - fast-equals: Deep equality checks
  - immer: Alternative immutability helper
  - reflect-metadata + tsyringe: Dependency injection

  UI:
  - MobX: UI state management
  - framer-motion: Animations
  - react-hot-toast: Notifications
  - next-themes: Dark mode support

  AI:
  - Custom MCTS, ported both ways: `shared/bot/` (Kotlin) and
    `legacy-web/packages/bot/` (TypeScript)

  Testing

  Kotlin: kotlin.test + coroutines-test, run through Gradle. The parity gate is
  `:shared:engine:jvmTest`, which replays every recording in `fixtures/recordings/`.

  Legacy web: Vitest, @testing-library/react, @vitest/coverage-v8.
  Run tests silently with no cache: `cd legacy-web && npm test`

  Error Handling

  Client-Side:
  - react-error-boundary for component error boundaries
  - @sentry/nextjs for error tracking

  Deployment

  Kotlin: Android and iOS through the stores (phase 8, not yet set up); the room Worker
  through `wrangler deploy` from `worker/cloudflare/`. The maintainer's runbook for taking
  the room live is `docs/kotlin/README.md` §6i.

  Legacy web: Vercel. Frozen; `nx build @vinto/game` is known-broken (see
  `docs/kotlin/README.md` §7).

  Notes for Claude Code

  - This is a game project, so focus on game logic correctness and state immutability
  - Always maintain determinism in GameEngine
  - Bot AI uses MCTS, so performance matters for decision-making
  - The architecture is designed for future cloud/multiplayer support
  - When modifying game logic, consider edge cases documented in SCENARIOS.md