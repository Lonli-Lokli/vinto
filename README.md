# Vinto Card Game

A multiplayer card game implementation built with Next.js, TypeScript, and MobX. Vinto is a strategic card game where players compete to achieve the lowest score by managing their cards and using action cards tactically.

## Features

- **4-Player Game**: One human player vs three AI opponents
- **Strategic Gameplay**: Action cards (7-K) with special abilities
- **Command Pattern**: Complete game state replay and undo functionality
- **Responsive Design**: Optimized for both mobile and desktop
- **State Management**: MobX with dependency injection
- **Type-Safe**: Full TypeScript implementation

## Game Rules

### Setup
- Each player starts with 4 cards
- Players get to peek at 2 of their own cards
- Goal: Achieve the lowest total score

### Gameplay
On your turn, you can either:
1. **Draw from deck** - Draw a new card and choose to swap or play
2. **Take from discard** - Take an unplayed action card (7-K) from discard pile
3. **Call Vinto** - End the game when you think you have the lowest score

### Action Cards
- **7 (Peek Own)**: Look at one of your own cards
- **8 (Peek Own)**: Look at one of your own cards
- **9 (Peek Opponent)**: Look at one opponent's card
- **10 (Peek Opponent)**: Look at one opponent's card
- **Jack (Swap Cards)**: Swap any two cards between players
- **Queen (Peek & Swap)**: Peek at two cards, then optionally swap them
- **King (Declare Rank)**: All players must toss in cards of declared rank

### Scoring
- Number cards (1-6): Face value
- Action cards (7-K): 10 points each
- Pairs in same column: 0 points
- Game ends when someone calls Vinto - all cards revealed and scored

## Tech Stack

- **Framework**: Next.js 15 (App Router)
- **Language**: TypeScript
- **State Management**: MobX with decorators
- **Dependency Injection**: Inversify
- **Styling**: Tailwind CSS
- **Build Tool**: Nx monorepo

## Project Structure

```
apps/vinto/src/app/
├── commands/          # Command pattern implementation
│   ├── command-factory.ts
│   ├── command-history.ts
│   ├── command-replayer.ts
│   ├── game-commands.ts
│   ├── game-state-manager.ts
│   └── game-state-serializer.ts
├── components/        # React components
│   ├── action-types/  # Action card UI components
│   ├── di-provider.tsx
│   ├── game-layout.tsx
│   ├── game-table.tsx
│   └── ...
├── di/               # Dependency injection setup
│   ├── container.ts
│   └── setup.ts
├── stores/           # MobX stores
│   ├── game-store.ts
│   ├── player-store.ts
│   ├── deck-store.ts
│   ├── action-store.ts
│   └── ...
└── lib/              # Utilities and helpers
```

## Getting Started

### Prerequisites
- Node.js 18+
- npm or yarn

### Installation

```sh
npm install
```

### Development

Run the dev server:

```sh
npx nx dev vinto
```

Open [http://localhost:4200](http://localhost:4200) in your browser.

### Build

Create a production bundle:

```sh
npx nx build vinto
```

## Key Features

### Command Pattern Integration
The game implements a complete command pattern that allows:
- **Save/Load**: Export and import game state as JSON
- **Replay**: Step through entire game history
- **Undo/Redo**: Navigate through game states
- **Debugging**: Inspect game state at any point

### Dependency Injection
Uses Inversify for clean dependency management:
- All stores are registered as singletons
- Components use React hooks to access DI container
- Easy testing and mocking

### State Machine
Game phases managed through explicit state transitions:
- `setup`: Initial card memorization
- `playing`: Main game loop
- `final`: Final round after Vinto call
- `scoring`: Score calculation and display

### Responsive Design
- Mobile-first approach
- Different layouts for phone/tablet/desktop
- Touch-optimized controls
- Dynamic card sizing based on hand size

## Development Notes

### State Management
The app uses MobX for reactive state management with the following stores:
- `GameStore`: Main game logic and orchestration
- `PlayerStore`: Player data and turn management
- `DeckStore`: Draw pile and discard pile
- `ActionStore`: Action card execution state
- `GamePhaseStore`: Game phase state machine
- `TossInStore`: King action toss-in period

### Component Architecture
- Server components for static layout
- Client components with `'use client'` directive
- Observer pattern for reactive updates
- Hooks-based DI access via `di-provider.tsx`

## Nx Workspace

This project uses Nx for build optimization and task running.

View project graph:
```sh
npx nx graph
```

Show available tasks:
```sh
npx nx show project vinto
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

MIT

## Links

- [Nx Documentation](https://nx.dev)
- [Next.js Documentation](https://nextjs.org/docs)
- [MobX Documentation](https://mobx.js.org)
