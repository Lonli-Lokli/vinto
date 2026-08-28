# Vinto Shapes

This package defines the shared types, interfaces, and constants used throughout the Vinto codebase. Its main responsibilities are:

- Providing TypeScript types and interfaces for all core game entities (cards, players, actions, state, etc.).
- Defining enums, constants, and utility types for game logic and UI layers.
- Ensuring type safety and consistency across all Vinto packages (engine, client, bot, etc.).
- Serving as the single source of truth for data structures and contracts between modules.
- Enabling code sharing and reducing duplication of type definitions.

## Usage

This package is intended to be imported by all other Vinto packages that need access to shared types and constants. It does not contain any logic or state, only type definitions and helpers.

## Development

- Written in TypeScript for maximum compatibility and safety.
- All types and interfaces are documented and maintained alongside the main game logic.
- See the main Vinto repo for integration and usage examples.

---

For more information, see the main [Vinto documentation](../../README.md).
