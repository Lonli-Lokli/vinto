# Vinto Local Client

This package provides the client-side logic and state management for local (non-networked) Vinto games. Its main responsibilities are:

- Managing the game client state for local games, including player actions, UI state, and game progress.
- Integrating with the Vinto game engine to apply game rules and state transitions.
- Handling user input, dispatching actions, and updating the UI in response to game events.
- Supporting local multiplayer (hotseat) and single-player (with bots) modes.
- Providing hooks and context providers for React-based UIs.
- Coordinating with services such as animation, headless logic, and bot AI.
- Exposing a clear API for the app layer to interact with the game client.

## Usage

This package is intended to be used as the client logic layer for local Vinto games. It is not a UI or engine, but a library for managing client-side game state and actions.

## Development

- Written in TypeScript and designed for integration with React apps.
- All client logic is covered by tests and follows best practices for state management.
- See the main Vinto repo for integration and usage examples.

---

For more information, see the main [Vinto documentation](../../README.md).
