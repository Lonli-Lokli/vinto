# Command Pattern Implementation

## Overview

This implementation provides a **Command Pattern** for managing all game state transitions. This enables:

- ✅ **Full History Tracking** - Every action is recorded
- ✅ **Replay Functionality** - Replay entire games from command history
- ✅ **Undo/Redo** - Reversible actions (optional per command)
- ✅ **Debugging** - Easy to track down bugs by examining command history
- ✅ **Testing** - Commands can be tested in isolation
- ✅ **Save/Restore** - Serialize game state and restore later

## Architecture

```
┌─────────────────┐
│   Game Store    │
│   (UI Layer)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐       ┌──────────────────┐
│ Command Factory │──────▶│ Command History  │
└────────┬────────┘       └──────────────────┘
         │
         ▼
┌─────────────────┐
│   Commands      │
│  (State Logic)  │
└─────────────────┘
```

## Basic Usage

### 1. Setup Command Factory and History

```typescript
import { CommandFactory, getCommandHistory } from './commands';
import { getPlayerStore } from './stores/player-store';
import { getDeckStore } from './stores/deck-store';
import { getActionStore } from './stores/action-store';

// Create command factory
const commandFactory = new CommandFactory(
  getPlayerStore(),
  getDeckStore(),
  getActionStore()
);

// Get command history singleton
const history = getCommandHistory();
```

### 2. Execute Commands

```typescript
// Instead of directly mutating state:
// playerStore.swapCards(p1, pos1, p2, pos2);  ❌

// Create and execute a command:
const command = commandFactory.swapCards(p1, pos1, p2, pos2);
const result = await history.executeCommand(command);

if (result.success) {
  console.log('Swap successful!');
} else {
  console.error('Swap failed:', result.error);
}
```

### 3. View History

```typescript
// Get command statistics
const stats = history.getStats();
console.log(`Total commands: ${stats.total}`);
console.log(`Successful: ${stats.successful}`);
console.log(`Failed: ${stats.failed}`);

// Get human-readable log
const log = history.getCommandLog();
console.log(log.join('\n'));

// Example output:
// 1. [10:23:45] ✓ Leonardo drew a card
// 2. [10:23:48] ✓ Leonardo replaced 7 with K[2]
// 3. [10:23:52] ✓ Turn: Leonardo → Michelangelo
```

### 4. Export/Debug History

```typescript
// Export as JSON for debugging
const json = history.exportHistory();
localStorage.setItem('game-history', json);

// Get recent commands
const recent = history.getRecentCommands(10);

// Get commands for specific player
const playerCommands = history.getPlayerCommands('human');
```

## Available Commands

### Core Game Commands

#### DrawCardCommand
```typescript
const cmd = commandFactory.drawCard(playerId);
```
Draws a card from the deck for a player.

#### SwapCardsCommand
```typescript
const cmd = commandFactory.swapCards(
  player1Id,
  position1,
  player2Id,
  position2
);
```
Swaps two cards between players (Jack action, Queen action).

#### PeekCardCommand
```typescript
const cmd = commandFactory.peekCard(
  playerId,
  position,
  isPermanent // true for setup phase, false for temporary
);
```
Reveals a card temporarily or permanently.

#### DiscardCardCommand
```typescript
const cmd = commandFactory.discardCard(card);
```
Adds a card to the discard pile.

#### ReplaceCardCommand
```typescript
const cmd = commandFactory.replaceCard(
  playerId,
  position,
  newCard
);
```
Replaces a card in a player's hand. **Supports undo**.

#### AdvanceTurnCommand
```typescript
const cmd = commandFactory.advanceTurn(fromPlayerId);
```
Advances to the next player's turn.

#### DeclareKingActionCommand
```typescript
const cmd = commandFactory.declareKingAction(rank);
```
Declares what action a King card will perform.

#### TossInCardCommand
```typescript
const cmd = commandFactory.tossInCard(
  playerId,
  position,
  matchingRank
);
```
Tosses in a card during toss-in period.

#### AddPenaltyCardCommand
```typescript
const cmd = commandFactory.addPenaltyCard(playerId);
```
Adds a penalty card to a player (wrong toss-in, Ace action).

## Integration Example

### Before (Direct State Mutation)
```typescript
// game-store.ts
swapCard(position: number) {
  const currentPlayer = this.playerStore.currentPlayer;
  const pendingCard = this.actionStore.pendingCard;

  if (!currentPlayer || !pendingCard) return;

  // Direct mutation ❌
  const oldCard = this.playerStore.replaceCard(
    currentPlayer.id,
    position,
    pendingCard
  );

  this.deckStore.addToDiscard(oldCard);
}
```

### After (Command Pattern)
```typescript
// game-store.ts
async swapCard(position: number) {
  const currentPlayer = this.playerStore.currentPlayer;
  const pendingCard = this.actionStore.pendingCard;

  if (!currentPlayer || !pendingCard) return;

  // Execute replace command ✅
  const replaceCmd = this.commandFactory.replaceCard(
    currentPlayer.id,
    position,
    pendingCard
  );
  const replaceResult = await this.commandHistory.executeCommand(replaceCmd);

  if (!replaceResult.success) return;

  // Execute discard command ✅
  const discardCmd = this.commandFactory.discardCard(replaceResult.oldCard);
  await this.commandHistory.executeCommand(discardCmd);
}
```

## Replay Functionality

```typescript
// Save game state
const commandData = history.getCommandDataHistory();
localStorage.setItem('saved-game', JSON.stringify(commandData));

// Later, replay from saved state
const savedData = JSON.parse(localStorage.getItem('saved-game'));

// Reset game to initial state
gameStore.reset();

// Replay each command
for (const data of savedData) {
  const command = recreateCommandFromData(data);
  await history.executeCommand(command);
}
```

## Creating Custom Commands

```typescript
import { Command, CommandData } from './command';

export class MyCustomCommand extends Command {
  constructor(
    private myStore: MyStore,
    private myParam: string
  ) {
    super();
  }

  execute(): boolean {
    // Execute your logic
    return this.myStore.doSomething(this.myParam);
  }

  // Optional: implement undo
  undo(): boolean {
    return this.myStore.undoSomething(this.myParam);
  }

  toData(): CommandData {
    return this.createCommandData('MY_CUSTOM_ACTION', {
      param: this.myParam,
    });
  }

  getDescription(): string {
    return `Custom action with ${this.myParam}`;
  }
}
```

## Debugging Tips

### 1. Track Down Bugs
```typescript
// When a bug occurs, export the history
const history = getCommandHistory();
console.log('Commands that led to bug:');
console.log(history.getCommandLog().slice(-20).join('\n'));
```

### 2. Test Specific Scenarios
```typescript
// Create a test scenario
const testCommands = [
  commandFactory.drawCard('player1'),
  commandFactory.swapCards('player1', 0, 'player2', 0),
  commandFactory.advanceTurn('player1'),
];

// Execute sequence
for (const cmd of testCommands) {
  await history.executeCommand(cmd);
}
```

### 3. Performance Monitoring
```typescript
const stats = history.getStats();
const avgTime = stats.total > 0
  ? (Date.now() - history.getHistory()[0].timestamp) / stats.total
  : 0;
console.log(`Average command execution time: ${avgTime}ms`);
```

## Benefits

1. **Easier Debugging**: Every state change is tracked with timestamp and description
2. **Testing**: Commands can be unit tested in isolation
3. **Replay**: Reproduce bugs by replaying command sequences
4. **Save/Load**: Serialize game state as command sequence
5. **Undo/Redo**: Some commands support reversing (optional)
6. **Audit Trail**: Full history of what happened during a game
7. **Performance**: Can optimize by batching commands or async execution

## Migration Strategy

1. **Phase 1**: Add command infrastructure (✅ Done)
2. **Phase 2**: Wrap critical actions (swaps, draws, turn advances)
3. **Phase 3**: Gradually migrate all state mutations to commands
4. **Phase 4**: Add replay/save/load functionality
5. **Phase 5**: Remove direct state mutations

## Next Steps

To integrate this into your game:

1. Add `CommandFactory` and `CommandHistory` to `GameStore`
2. Replace direct state mutations with command execution
3. Add command logging to help debug issues
4. Implement save/restore using command serialization
5. Add replay functionality for testing

Example integration in `game-store.ts`:

```typescript
import { CommandFactory, getCommandHistory } from './commands';

export class GameStore {
  private commandFactory: CommandFactory;
  private commandHistory: CommandHistory;

  constructor() {
    this.commandFactory = new CommandFactory(
      getPlayerStore(),
      getDeckStore(),
      getActionStore()
    );
    this.commandHistory = getCommandHistory();
  }

  async drawCard() {
    const playerId = this.playerStore.currentPlayer?.id;
    if (!playerId) return;

    const command = this.commandFactory.drawCard(playerId);
    await this.commandHistory.executeCommand(command);
  }

  // ... rest of your methods using commands
}
```
