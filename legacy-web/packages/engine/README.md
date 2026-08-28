# Vinto Game Engine

This package contains the core game logic and rules for the Vinto card game. Its main responsibilities are:

- Defining and enforcing all game rules, phases, and actions.
- Managing the game state, including player hands, draw/discard piles, and turn order.
- Providing a pure, deterministic reducer (GameEngine) for all state transitions.
- Handling all card actions, special rules, and edge cases.
- Supporting both local and online multiplayer game modes.
- Exposing a clear API for the client, bot, and UI layers to interact with the game logic.
- Ensuring all state changes are testable and replayable for debugging and validation.

## Usage

This package is intended to be used as the authoritative source of truth for Vinto game state and rules. It is not a UI or network layer, but a library for game logic.

## Development

- Written in TypeScript for type safety and maintainability.
- All game rules and state transitions are covered by comprehensive tests.
- See the main Vinto repo for integration and usage examples.

---

For more information, see the main [Vinto documentation](../../README.md).
