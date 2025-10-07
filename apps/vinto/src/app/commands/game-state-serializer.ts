// commands/game-state-serializer.ts
/**
 * Game State Serializer
 * Handles saving and loading complete game state including initial deck and all commands
 */

import { CommandData } from './command';
import { Card } from '../shapes';
import {
  PlayerStore,
  DeckStore,
  GamePhaseStore,
  ActionStore,
  TossInStore,
} from '../stores';
import { CommandHistory } from './command-history';

/**
 * Complete serialized game state
 */
export interface SerializedGameState {
  version: string; // For future compatibility
  timestamp: number;
  initialState: CommandData; // The INITIALIZE_GAME command containing initial state
  commands: CommandData[]; // All subsequent commands
}

/**
 * GameStateSerializer class
 */
export class GameStateSerializer {
  private static readonly VERSION = '1.0.0';

  constructor(private commandHistory: CommandHistory) {}

  /**
   * Save complete game state to JSON string
   */
  saveGameState(): string {
    const history = this.commandHistory.getCommandDataHistory();

    console.log('[GameStateSerializer] Exporting game state:', {
      totalCommands: history.length,
      commandTypes: history.map((c) => c.type),
    });

    if (history.length === 0) {
      throw new Error('Cannot save game state: No commands in history');
    }

    // First command should be INITIALIZE_GAME
    const initialState = history[0];
    if (initialState.type !== 'INITIALIZE_GAME') {
      throw new Error(
        'Cannot save game state: Missing INITIALIZE_GAME command'
      );
    }

    const gameState: SerializedGameState = {
      version: GameStateSerializer.VERSION,
      timestamp: Date.now(),
      initialState,
      commands: history.slice(1), // All commands after initialization
    };

    console.log('[GameStateSerializer] Export complete:', {
      totalCommands: gameState.commands.length,
      commandTypes: gameState.commands.map((c) => c.type),
    });

    return JSON.stringify(gameState, null, 2);
  }

  /**
   * Save game state to file (browser download)
   */
  saveGameStateToFile(filename?: string): void {
    const json = this.saveGameState();
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.download = filename || `vinto-game-${Date.now()}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  /**
   * Load game state from JSON string
   */
  loadGameState(json: string): SerializedGameState {
    const state: SerializedGameState = JSON.parse(json);

    // Validate version compatibility
    if (state.version !== GameStateSerializer.VERSION) {
      console.warn(
        `Game state version mismatch: Expected ${GameStateSerializer.VERSION}, got ${state.version}`
      );
    }

    // Validate initial state
    if (!state.initialState || state.initialState.type !== 'INITIALIZE_GAME') {
      throw new Error(
        'Invalid game state: Missing or invalid INITIALIZE_GAME command'
      );
    }

    return state;
  }

  /**
   * Load game state from file
   */
  async loadGameStateFromFile(file: File): Promise<SerializedGameState> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();

      reader.onload = (e) => {
        try {
          const json = e.target?.result as string;
          const state = this.loadGameState(json);
          resolve(state);
        } catch (error) {
          reject(error);
        }
      };

      reader.onerror = () => reject(new Error('Failed to read file'));
      reader.readAsText(file);
    });
  }

  /**
   * Restore game state by replaying commands
   */
  restoreGameState(
    state: SerializedGameState,
    playerStore: PlayerStore,
    deckStore: DeckStore,
    gamePhaseStore: GamePhaseStore,
    actionStore: ActionStore,
    tossInStore: TossInStore
  ): void {
    // First, restore the initial state
    const initialData = state.initialState;
    this.restoreInitialState(
      initialData,
      playerStore,
      deckStore,
      gamePhaseStore,
      actionStore,
      tossInStore
    );

    console.log(
      `Game state restored: ${state.commands.length} commands to replay`
    );
  }

  /**
   * Restore initial game state from INITIALIZE_GAME command
   */
  private restoreInitialState(
    data: CommandData,
    playerStore: PlayerStore,
    deckStore: DeckStore,
    gamePhaseStore: GamePhaseStore,
    actionStore: ActionStore,
    tossInStore: TossInStore
  ): void {
    const payload = data.payload;

    // Restore players with complete state
    playerStore.players = payload.players.map((p: any) => ({
      id: p.id,
      name: p.name,
      cards: p.cards.map(this.deserializeCard),
      knownCardPositions: new Set(p.knownCardPositions),
      temporarilyVisibleCards: new Set(),
      highlightedCards: new Set(),
      isHuman: p.isHuman,
      isBot: !p.isHuman,
      position: p.position,
      coalitionWith: new Set(),
    }));

    // Restore deck state
    deckStore.drawPile = payload.drawPile.map(this.deserializeCard);
    deckStore.discardPile = payload.discardPile.map(this.deserializeCard);

    // Restore player store state
    playerStore.currentPlayerIndex = payload.currentPlayerIndex || 0;
    playerStore.setupPeeksRemaining = payload.setupPeeksRemaining || 2;
    playerStore.turnCount = payload.turnCount || 0;

    // Restore game phase state
    if (payload.gamePhase) {
      gamePhaseStore.phase = payload.gamePhase.phase;
      gamePhaseStore.subPhase = payload.gamePhase.subPhase;
      gamePhaseStore.finalTurnTriggered = payload.gamePhase.finalTurnTriggered;
    }

    // Restore action store state
    if (payload.actionState) {
      actionStore.pendingCard = payload.actionState.pendingCard
        ? this.deserializeCard(payload.actionState.pendingCard)
        : null;
      actionStore.actionContext = payload.actionState.actionContext;
      actionStore.selectedSwapPosition =
        payload.actionState.selectedSwapPosition;
      actionStore.swapPosition = payload.actionState.swapPosition;
      actionStore.swapTargets = [...(payload.actionState.swapTargets || [])];
      actionStore.peekTargets = (payload.actionState.peekTargets || []).map(
        (pt: any) => ({
          playerId: pt.playerId,
          position: pt.position,
          card: pt.card ? this.deserializeCard(pt.card) : undefined,
        })
      );
    }

    // Restore toss-in state
    if (payload.tossInState) {
      const tossData = payload.tossInState;
      tossInStore.queue = (tossData.queue || []).map((item: any) => ({
        playerId: item.playerId,
        card: this.deserializeCard(item.card),
      }));
      tossInStore.isActive = tossData.isActive || false;
      tossInStore.currentQueueIndex = tossData.currentQueueIndex || 0;
      tossInStore.originalCurrentPlayer = tossData.originalCurrentPlayer || '';
      // Note: playersWhoTossedIn is private, will be reconstructed during replay
    }
  }

  /**
   * Deserialize a card from serialized data
   */
  private deserializeCard(data: any): Card {
    return {
      id: data.id,
      rank: data.rank,
      value: data.value,
      actionText: data.action,
      played: data.played || false,
    };
  }

  /**
   * Export game statistics
   */
  exportGameStats(): string {
    const stats = this.commandHistory.getStats();
    const commandLog = this.commandHistory.getCommandLog();

    return JSON.stringify(
      {
        stats,
        commandLog,
        exportedAt: new Date().toISOString(),
      },
      null,
      2
    );
  }

  /**
   * Get game replay data for debugging
   */
  getReplayData(): {
    initialState: CommandData | null;
    commands: CommandData[];
    totalCommands: number;
  } {
    const history = this.commandHistory.getCommandDataHistory();

    return {
      initialState: history.length > 0 ? history[0] : null,
      commands: history.slice(1),
      totalCommands: history.length,
    };
  }
}

/**
 * Create a singleton instance for convenience
 */
let serializerInstance: GameStateSerializer | null = null;

export function getGameStateSerializer(
  commandHistory: CommandHistory
): GameStateSerializer {
  if (!serializerInstance) {
    serializerInstance = new GameStateSerializer(commandHistory);
  }
  return serializerInstance;
}

export function resetGameStateSerializer(): void {
  serializerInstance = null;
}
