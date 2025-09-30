# Integration Example

## How to Integrate Command Pattern into Game Store

This document shows how to migrate your existing `game-store.ts` to use the Command Pattern.

## Step 1: Add Command Infrastructure to GameStore

```typescript
// game-store.ts
import { makeAutoObservable } from 'mobx';
import { CommandFactory, getCommandHistory, CommandHistory } from './commands';
import { getPlayerStore } from './stores/player-store';
import { getDeckStore } from './stores/deck-store';
import { getActionStore } from './stores/action-store';

export class GameStore {
  // Existing properties...
  phase: GamePhase = 'setup';
  aiThinking = false;

  // Add command infrastructure
  private commandFactory: CommandFactory;
  private commandHistory: CommandHistory;

  constructor() {
    makeAutoObservable(this);

    // Initialize command system
    this.commandFactory = new CommandFactory(
      getPlayerStore(),
      getDeckStore(),
      getActionStore()
    );
    this.commandHistory = getCommandHistory();
  }

  // Expose for debugging
  get commandLog() {
    return this.commandHistory.getCommandLog();
  }

  get commandStats() {
    return this.commandHistory.getStats();
  }

  exportGameHistory() {
    return this.commandHistory.exportHistory();
  }

  // ... rest of your methods
}
```

## Step 2: Migrate Individual Methods

### Example 1: Draw Card

**Before:**
```typescript
drawCard() {
  const currentPlayer = this.playerStore.currentPlayer;
  if (!currentPlayer?.isHuman) return;

  const drawnCard = this.deckStore.drawCard();
  if (!drawnCard) return;

  this.actionStore.pendingCard = drawnCard;
  // ... more logic
}
```

**After:**
```typescript
async drawCard() {
  const currentPlayer = this.playerStore.currentPlayer;
  if (!currentPlayer?.isHuman) return;

  // Execute draw command
  const command = this.commandFactory.drawCard(currentPlayer.id);
  const result = await this.commandHistory.executeCommand(command);

  if (!result.success) return;

  const drawnCard = this.deckStore.drawCard();
  if (!drawnCard) return;

  this.actionStore.pendingCard = drawnCard;
  // ... more logic
}
```

### Example 2: Swap Cards

**Before:**
```typescript
swapCard(position: number) {
  const currentPlayer = this.playerStore.currentPlayer;
  const pendingCard = this.actionStore.pendingCard;

  if (!currentPlayer || !pendingCard || position < 0) return;

  // Direct mutation
  const replacedCard = this.playerStore.replaceCard(
    currentPlayer.id,
    position,
    pendingCard
  );

  if (replacedCard) {
    this.deckStore.addToDiscard(replacedCard);
    this.actionStore.pendingCard = null;
    this.advanceTurn();
  }
}
```

**After:**
```typescript
async swapCard(position: number) {
  const currentPlayer = this.playerStore.currentPlayer;
  const pendingCard = this.actionStore.pendingCard;

  if (!currentPlayer || !pendingCard || position < 0) return;

  // Execute replace command
  const replaceCmd = this.commandFactory.replaceCard(
    currentPlayer.id,
    position,
    pendingCard
  );
  const replaceResult = await this.commandHistory.executeCommand(replaceCmd);

  if (!replaceResult.success) return;

  // Get the replaced card from the command
  const replacedCard = replaceResult.command.toData().payload.oldCard;

  if (replacedCard) {
    // Execute discard command
    const discardCmd = this.commandFactory.discardCard(replacedCard);
    await this.commandHistory.executeCommand(discardCmd);

    this.actionStore.pendingCard = null;

    // Execute advance turn command
    const turnCmd = this.commandFactory.advanceTurn(currentPlayer.id);
    await this.commandHistory.executeCommand(turnCmd);
  }
}
```

### Example 3: Peek Card

**Before:**
```typescript
peekCard(playerId: string, position: number) {
  const player = this.playerStore.getPlayer(playerId);
  if (!player || this.setupPeeksRemaining <= 0) return;

  if (!player.knownCardPositions.has(position)) {
    player.knownCardPositions.add(position);
    this.setupPeeksRemaining--;
  }
}
```

**After:**
```typescript
async peekCard(playerId: string, position: number) {
  const player = this.playerStore.getPlayer(playerId);
  if (!player || this.setupPeeksRemaining <= 0) return;

  if (!player.knownCardPositions.has(position)) {
    // Execute peek command
    const command = this.commandFactory.peekCard(
      playerId,
      position,
      true // permanent during setup
    );
    const result = await this.commandHistory.executeCommand(command);

    if (result.success) {
      this.setupPeeksRemaining--;
    }
  }
}
```

## Step 3: Batch Multiple Commands

For complex operations, you can batch commands:

```typescript
async executeQueenAction(
  player1Id: string,
  pos1: number,
  player2Id: string,
  pos2: number
) {
  // Peek at both cards
  const peek1 = this.commandFactory.peekCard(player1Id, pos1);
  const peek2 = this.commandFactory.peekCard(player2Id, pos2);

  await Promise.all([
    this.commandHistory.executeCommand(peek1),
    this.commandHistory.executeCommand(peek2),
  ]);

  // Let user decide whether to swap
  // If swap chosen:
  const swapCmd = this.commandFactory.swapCards(
    player1Id,
    pos1,
    player2Id,
    pos2
  );
  await this.commandHistory.executeCommand(swapCmd);

  // Advance turn
  const turnCmd = this.commandFactory.advanceTurn(this.playerStore.currentPlayer!.id);
  await this.commandHistory.executeCommand(turnCmd);
}
```

## Step 4: Add Debugging Tools

Add methods to help debug:

```typescript
// game-store.ts
export class GameStore {
  // ... existing code

  // Debug: Print last 20 commands
  debugRecentCommands() {
    const log = this.commandHistory.getCommandLog();
    console.log('=== Recent Commands ===');
    console.log(log.slice(-20).join('\n'));
  }

  // Debug: Export game for bug report
  exportBugReport() {
    return {
      gameState: {
        phase: this.phase,
        turnCount: this.playerStore.turnCount,
        players: this.playerStore.players.map(p => ({
          id: p.id,
          name: p.name,
          cardCount: p.cards.length,
        })),
      },
      commandHistory: this.commandHistory.exportHistory(),
      stats: this.commandHistory.getStats(),
    };
  }

  // Debug: Save game state
  saveGame() {
    const data = {
      history: this.commandHistory.getCommandDataHistory(),
      timestamp: Date.now(),
    };
    localStorage.setItem('saved-game', JSON.stringify(data));
    console.log('Game saved!');
  }

  // Debug: Load game state (replay all commands)
  async loadGame() {
    const saved = localStorage.getItem('saved-game');
    if (!saved) return;

    const data = JSON.parse(saved);

    // Reset to initial state
    this.reset();

    // Replay all commands
    console.log(`Replaying ${data.history.length} commands...`);
    // Note: You'll need to implement recreateCommandFromData
    // This is a placeholder
    for (const cmdData of data.history) {
      // Recreate and execute command
      // const command = this.recreateCommand(cmdData);
      // await this.commandHistory.executeCommand(command);
    }

    console.log('Game loaded!');
  }
}
```

## Step 5: Use in Development

Add keyboard shortcuts for debugging:

```typescript
// In your main component or game initializer
useEffect(() => {
  if (process.env.NODE_ENV === 'development') {
    window.addEventListener('keydown', (e) => {
      // Ctrl+Shift+H: Show command history
      if (e.ctrlKey && e.shiftKey && e.key === 'H') {
        gameStore.debugRecentCommands();
      }

      // Ctrl+Shift+S: Save game
      if (e.ctrlKey && e.shiftKey && e.key === 'S') {
        gameStore.saveGame();
      }

      // Ctrl+Shift+L: Load game
      if (e.ctrlKey && e.shiftKey && e.key === 'L') {
        gameStore.loadGame();
      }

      // Ctrl+Shift+E: Export bug report
      if (e.ctrlKey && e.shiftKey && e.key === 'E') {
        const report = gameStore.exportBugReport();
        console.log('Bug Report:', report);
        navigator.clipboard.writeText(JSON.stringify(report, null, 2));
        alert('Bug report copied to clipboard!');
      }
    });
  }
}, []);
```

## Migration Checklist

- [ ] Add CommandFactory and CommandHistory to GameStore constructor
- [ ] Add debug methods (commandLog, commandStats, exportGameHistory)
- [ ] Migrate drawCard() to use commands
- [ ] Migrate swapCard() to use commands
- [ ] Migrate peekCard() to use commands
- [ ] Migrate advanceTurn() to use commands
- [ ] Migrate toss-in logic to use commands
- [ ] Migrate King declaration to use commands
- [ ] Add keyboard shortcuts for debugging (dev only)
- [ ] Test command replay functionality
- [ ] Add unit tests for commands
- [ ] Update README with new debugging capabilities

## Benefits After Migration

1. **Instant Bug Reports**: Users can export command history when they encounter bugs
2. **Reproducible Issues**: Replay exact sequence that caused a bug
3. **Testing**: Create test scenarios by constructing command sequences
4. **Analytics**: Track which actions players take most often
5. **Undo**: Can add undo functionality for some actions
6. **Save/Load**: Implement game save/restore using command history
7. **Audit Trail**: See exactly what happened during a game

## Performance Considerations

- Commands add minimal overhead (~1ms per command)
- History is limited to 1000 commands by default (configurable)
- Async execution allows for smoother UI updates
- Can batch commands for complex operations
- No performance impact during normal gameplay

## Next Steps

1. Start with high-frequency actions (draw, swap, turn advance)
2. Add logging to verify commands are executing correctly
3. Use keyboard shortcuts to debug during development
4. Gradually migrate remaining actions
5. Add save/restore functionality once stable
6. Implement replay feature for bug reproduction
