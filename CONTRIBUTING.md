# Contributing to Vinto

Thank you for your interest in contributing to Vinto! This document provides guidelines and information for contributors.

## Table of Contents

- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Dependency Management](#dependency-management)
- [Code Style](#code-style)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)

## Getting Started

### Prerequisites

- Node.js 22 or higher
- npm (comes with Node.js)

### Setup

1. Fork and clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/vinto.git
   cd vinto
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start the development server:
   ```bash
   npm start
   ```

4. Visit [http://localhost:4200](http://localhost:4200) to see the app running.

## Development Workflow

### Project Structure

This is an Nx monorepo with the following structure:

```
apps/
  vinto/           # Main Next.js application
packages/
  engine/          # Game engine (pure functions)
  bot/             # AI bot logic (MCTS)
  local-client/    # Client-side state management
  shapes/          # Shared types and interfaces
```

### Available Commands

- `npm start` - Start development server
- `npm run build` - Build all packages
- `npm test` - Run all tests
- `npm run lint` - Lint and fix all code
- `npm run format` - Format code with Prettier
- `npm run test:coverage` - Run tests with coverage
- `npm run test:e2e` - Run end-to-end tests

### Nx Commands

- `npx nx graph` - View project dependency graph
- `npx nx show project <project-name>` - Show project details
- `npx nx <target> <project-name>` - Run specific target for a project

## Dependency Management

### Automated Updates with Dependabot

This project uses **Dependabot** to automatically manage npm dependencies and GitHub Actions. Dependabot helps keep the project secure and up-to-date by creating pull requests for dependency updates.

#### How Dependabot Works

- **Schedule**: Dependabot checks for updates every **Monday at 09:00 UTC**
- **Pull Request Limit**: Maximum of 5 npm PRs and 3 GitHub Actions PRs at a time
- **Grouping**: Dependencies are grouped logically to reduce PR noise:
  - Production dependencies (minor/patch updates)
  - Development dependencies (minor/patch updates)
  - Nx-related packages
  - React ecosystem packages
  - Next.js and related packages
- **Major Version Updates**: Automatically ignored to prevent breaking changes without review

#### Reviewing Dependabot PRs

When reviewing a Dependabot PR:

1. **Check CI Status**: Ensure all tests, linting, and builds pass
2. **Review Changelog**: Click through to the package's changelog to understand changes
3. **Test Locally** (for production dependencies):
   ```bash
   gh pr checkout <PR_NUMBER>
   npm install
   npm test
   npm run build
   ```
4. **Verify Functionality**: Run the app and test affected features
5. **Approve and Merge**: If everything looks good, approve and merge the PR

#### Dependabot PR Commit Messages

Dependabot follows the project's commit message convention:

- Production dependencies: `chore(deps): update <package-name>`
- Development dependencies: `chore(deps-dev): update <package-name>`
- GitHub Actions: `chore(ci): update <action-name>`

#### Auto-Merge Strategy

For low-risk updates (patch/minor versions of development dependencies), consider enabling auto-merge:

1. Review the PR
2. If CI passes and changes look safe, approve the PR
3. Enable auto-merge: `gh pr merge <PR_NUMBER> --auto --squash`

#### Handling Failed Dependabot PRs

If a Dependabot PR fails CI:

1. **Check the Logs**: Review the failed test/lint/build output
2. **Fix Locally**: Check out the PR branch and fix the issue
3. **Push the Fix**: Commit and push to the Dependabot branch
4. **Merge**: Once CI passes, merge the PR

#### Managing Dependabot

**Pause Dependabot** (if needed during major refactoring):
```bash
# Edit .github/dependabot.yml and change schedule to:
schedule:
  interval: "monthly"  # or comment out the entire update block
```

**Reopen Closed Dependabot PRs**:
- Comment on the closed PR with: `@dependabot reopen`

**Ignore Specific Updates**:
- Comment on the PR with: `@dependabot ignore this dependency`

**Configuration Location**: `.github/dependabot.yml`

### Manual Dependency Updates

For updates not handled by Dependabot (or major version upgrades):

1. Update `package.json` manually
2. Run `npm install` to update `package-lock.json`
3. Run tests: `npm test`
4. Run build: `npm run build`
5. Commit changes with message: `chore(deps): update <package-name> to v<version>`

## Code Style

### TypeScript

- Strict mode is enabled
- Prefer `interface` over `type` for object shapes
- Always export types and interfaces
- Use const for immutable values

### React

- Use functional components with hooks
- Prefer named exports for components
- Use React 19 features
- App Router (not Pages Router)

### File Naming

- Use kebab-case: `my-component.tsx`
- One component per file
- Organize by feature, not by type

### State Management

- `GameState` is immutable and authoritative
- UI state lives in MobX stores
- Never duplicate game state in UI stores

### Game Engine

- All game logic must be pure functions
- Actions must be serializable JSON
- Use `copy()` for state updates (from fast-copy)
- Maintain determinism for reproducibility

### Styling

- Use Tailwind CSS
- Component-scoped styles when needed
- Mobile-first responsive design

### Formatting

Run Prettier before committing:
```bash
npm run format
```

## Testing

### Unit Tests

- Framework: Vitest
- Location: `*.spec.ts` files next to source files
- Run tests: `npm test`
- Coverage: `npm run test:coverage`

### E2E Tests

- Framework: Playwright
- Location: `apps/vinto-e2e/`
- Run tests: `npm run test:e2e`

### Writing Tests

**Game Engine Tests** (pure functions):
```typescript
test('drawing card updates state correctly', () => {
  const initialState = createGameState();
  const action = { type: 'DRAW_CARD', payload: { playerId: 'player1' } };

  const newState = GameEngine.handleAction(initialState, action);

  expect(newState.drawPile.length).toBe(initialState.drawPile.length - 1);
});
```

**Component Tests**:
```typescript
import { render, screen } from '@testing-library/react';

test('renders button with correct text', () => {
  render(<MyButton>Click me</MyButton>);
  expect(screen.getByText('Click me')).toBeInTheDocument();
});
```

## Pull Request Process

### Before Submitting

1. **Create a branch**: Use descriptive names (e.g., `feature/coalition-mode`, `fix/scoring-bug`)
2. **Write tests**: Add tests for new features or bug fixes
3. **Run checks locally**:
   ```bash
   npm run lint
   npm test
   npm run build
   ```
4. **Update documentation**: Update README.md or other docs if needed

### PR Guidelines

- **Title**: Use conventional commit format:
  - `feat: add coalition mode`
  - `fix: correct scoring calculation`
  - `docs: update game rules`
  - `chore: update dependencies`
  - `refactor: simplify card action logic`
- **Description**:
  - Explain what and why (not just how)
  - Reference related issues: `Closes #123`
  - Include screenshots for UI changes
- **Small PRs**: Keep PRs focused and manageable
- **Draft PRs**: Use draft PRs for work-in-progress

### Review Process

1. CI must pass (tests, linting, build)
2. At least one maintainer approval required
3. Address review feedback
4. Squash commits when merging (if needed)

### Commit Messages

Follow conventional commit format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Test changes
- `chore`: Maintenance tasks

**Examples**:
```
feat(engine): add toss-in reaction mechanic

Implement the toss-in mechanic where players can play matching
cards after a discard. Includes validation and penalty handling.

Closes #45
```

```
fix(scoring): correct coalition comparison logic

The coalition scoring was comparing wrong player totals.
Fixed to compare Vinto caller vs lowest coalition total.

Fixes #67
```

## Architecture Principles

When contributing, keep these principles in mind:

1. **Single Source of Truth**: All game state lives in `GameState` (immutable)
2. **Pure Game Engine**: No side effects, no async operations, deterministic
3. **Actions as Data**: All interactions are serializable actions
4. **Cloud-Ready**: Game engine should be hostable remotely
5. **Bot AI Integration**: Bots use the same action dispatch path as humans

## Questions?

- Open an issue for bug reports or feature requests
- Start a discussion for questions or ideas
- Check existing issues before creating new ones

## License

By contributing to Vinto, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing! 🎮
