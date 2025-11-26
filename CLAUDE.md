# Vinto Project - Claude Code Guide

  ## Quick Reference
  See @README.md for project overview and architecture
  See @package.json for available npm scripts
  See @docs/game-engine/README.md for game engine documentation
  See @docs/game-engine/VINTO_RULES.md for complete game rules
  See @docs/game-engine/SCENARIOS.md for worked examples and edge cases

  ## Project Overview
  This is an NX monorepo containing a strategic multiplayer card game built with Next.js, TypeScript, and a pure reducer-based architecture.

  **Technology Stack:**
  - Framework: Next.js 15 (App Router)
  - Language: TypeScript (strict mode)
  - Game Engine: Pure reducer pattern (Redux-inspired)
  - State Management: MobX (UI stores only)
  - AI: Monte Carlo Tree Search (MCTS)
  - Build Tool: Nx monorepo (v22.0.3)
  - Node Version: 22

  ## Monorepo Structure

  apps/
    vinto/           # Main Next.js app (UI, integration, entrypoint)

  packages/
    engine/          # Core game logic (pure, deterministic, cloud-ready)
    bot/             # AI bot logic with MCTS decision-making
    local-client/    # Client-side state management
    shapes/          # Shared types and interfaces

  ## Development Commands

  **Start dev server:**
  ```bash
  npx nx dev vinto
  # or
  npm start

  Build:
  npx nx build vinto
  # or
  npm run build

  Testing:
  npx nx test <project-name>
  # or run all tests
  npm test

  Linting:
  npx nx lint <project-name> --fix
  # or lint all
  npm run lint

  Format code:
  npm run format

  View project graph:
  npx nx graph

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
  Development Branch: test
  Current Branch: test (as of this session)

  Pre-commit Hooks:
  Uses lefthook for git hooks

  Common Tasks

  Adding a New Game Action:
  1. Define action type in packages/engine/src/lib/types/GameAction.ts
  2. Create handler in packages/engine/src/lib/cases/
  3. Add to engine in packages/engine/src/lib/GameEngine.ts
  4. Dispatch from UI

  Testing Game Logic:
  GameEngine is pure, so tests are straightforward unit tests

  Working with NX:
  Use nx-mcp MCP server for NX-specific operations
  View available targets: npx nx show project <project-name>

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

  Game Logic:
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
  - Custom MCTS implementation in packages/bot/

  Testing

  Framework: Vitest
  React Testing: @testing-library/react
  Coverage: @vitest/coverage-v8

  Run tests silently with no cache: npm test

  Error Handling

  Client-Side:
  - react-error-boundary for component error boundaries
  - @sentry/nextjs for error tracking

  Deployment

  Target: Vercel (implied by @vercel/analytics)
  Build Command: npm run deploy (runs tests then build)

  Notes for Claude Code

  - This is a game project, so focus on game logic correctness and state immutability
  - Always maintain determinism in GameEngine
  - Bot AI uses MCTS, so performance matters for decision-making
  - The architecture is designed for future cloud/multiplayer support
  - When modifying game logic, consider edge cases documented in SCENARIOS.md