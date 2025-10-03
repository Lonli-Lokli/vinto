// commands/game-state-manager.ts
/**
 * Game State Manager
 * High-level API for integrating save/load functionality into the game
 */

import { injectable, inject } from 'tsyringe';
import { CommandFactory } from './command-factory';
import { CommandHistory } from './command-history';
import {
  GameStateSerializer,
  getGameStateSerializer,
} from './game-state-serializer';
import { CommandReplayer, createCommandReplayer } from './command-replayer';
import {
  PlayerStore,
  DeckStore,
  GamePhaseStore,
  ActionStore,
  TossInStore,
  ReplayStore,
} from '../stores';
import { Difficulty } from '../shapes';

/**
 * Complete game state manager
 * Encapsulates all command pattern functionality
 */
@injectable()
export class GameStateManager {
  private commandFactory: CommandFactory;
  private commandHistory: CommandHistory;
  private gameStateSerializer: GameStateSerializer;
  private commandReplayer: CommandReplayer;

  constructor(
    @inject(PlayerStore) private playerStore: PlayerStore,
    @inject(DeckStore) private deckStore: DeckStore,
    @inject(GamePhaseStore) private gamePhaseStore: GamePhaseStore,
    @inject(ActionStore) private actionStore: ActionStore,
    @inject(TossInStore) private tossInStore: TossInStore,
    @inject(ReplayStore) private replayStore: ReplayStore,
    @inject(CommandFactory) commandFactory: CommandFactory,
    @inject(CommandHistory) commandHistory: CommandHistory
  ) {
    this.commandHistory = commandHistory;
    this.commandFactory = commandFactory;
    this.gameStateSerializer = getGameStateSerializer(this.commandHistory);
    this.commandReplayer = createCommandReplayer(
      this.commandFactory,
      this.commandHistory,
      playerStore,
      deckStore
    );
  }

  /**
   * Initialize a new game and capture its state
   */
  async initializeGame(difficulty: Difficulty): Promise<boolean> {
    // Clear previous history
    this.commandHistory.clear();

    // Execute the initialize command to capture state
    const initCommand = this.commandFactory.initializeGame(difficulty);
    const result = await this.commandHistory.executeCommand(initCommand);

    return result.success;
  }

  /**
   * Get the command factory for executing game actions
   */
  getCommandFactory(): CommandFactory {
    return this.commandFactory;
  }

  /**
   * Get the command history
   */
  getCommandHistory(): CommandHistory {
    return this.commandHistory;
  }

  /**
   * Execute a command through the history
   */
  async executeCommand(command: any): Promise<boolean> {
    const result = await this.commandHistory.executeCommand(command);
    return result.success;
  }

  /**
   * Save current game state to JSON string
   */
  saveGame(): string {
    return this.gameStateSerializer.saveGameState();
  }

  /**
   * Save game state to downloadable file
   */
  saveGameToFile(filename?: string): void {
    this.gameStateSerializer.saveGameStateToFile(filename);
  }

  /**
   * Load game from JSON string
   */
  async loadGame(json: string): Promise<{
    success: boolean;
    commandsReplayed: number;
    errors: any[];
  }> {
    try {
      // Parse the saved state
      const savedState = this.gameStateSerializer.loadGameState(json);

      // Reset all stores
      this.resetAllStores();

      // Restore initial state
      await this.gameStateSerializer.restoreGameState(
        savedState,
        this.playerStore,
        this.deckStore,
        this.gamePhaseStore,
        this.actionStore,
        this.tossInStore
      );

      // Replay all commands
      const result = await this.commandReplayer.replayCommandsWithValidation(
        savedState.commands
      );

      return {
        success: result.failed === 0,
        commandsReplayed: result.successful,
        errors: result.errors,
      };
    } catch (error) {
      console.error('Failed to load game:', error);
      throw error;
    }
  }

  /**
   * Load game in replay mode (step-by-step)
   */
  async loadGameInReplayMode(json: string): Promise<{
    success: boolean;
    commandCount: number;
  }> {
    try {
      // Parse the saved state
      const savedState = this.gameStateSerializer.loadGameState(json);

      // Reset all stores
      this.resetAllStores();

      // Restore initial state
      await this.gameStateSerializer.restoreGameState(
        savedState,
        this.playerStore,
        this.deckStore,
        this.gamePhaseStore,
        this.actionStore,
        this.tossInStore
      );

      // Enter replay mode with commands
      this.replayStore.startReplay(savedState.commands);

      return {
        success: true,
        commandCount: savedState.commands.length,
      };
    } catch (error) {
      console.error('Failed to load game in replay mode:', error);
      throw error;
    }
  }

  /**
   * Load game from uploaded file
   */
  async loadGameFromFile(file: File): Promise<{
    success: boolean;
    commandsReplayed: number;
    errors: any[];
  }> {
    try {
      const savedState = await this.gameStateSerializer.loadGameStateFromFile(
        file
      );

      // Reset all stores
      this.resetAllStores();

      // Restore initial state
      await this.gameStateSerializer.restoreGameState(
        savedState,
        this.playerStore,
        this.deckStore,
        this.gamePhaseStore,
        this.actionStore,
        this.tossInStore
      );

      // Replay all commands
      const result = await this.commandReplayer.replayCommandsWithValidation(
        savedState.commands
      );

      return {
        success: result.failed === 0,
        commandsReplayed: result.successful,
        errors: result.errors,
      };
    } catch (error) {
      console.error('Failed to load game from file:', error);
      throw error;
    }
  }

  /**
   * Load game from file in replay mode (step-by-step)
   */
  async loadGameFromFileInReplayMode(file: File): Promise<{
    success: boolean;
    commandCount: number;
  }> {
    try {
      const savedState = await this.gameStateSerializer.loadGameStateFromFile(
        file
      );

      // Reset all stores
      this.resetAllStores();

      // Restore initial state
      await this.gameStateSerializer.restoreGameState(
        savedState,
        this.playerStore,
        this.deckStore,
        this.gamePhaseStore,
        this.actionStore,
        this.tossInStore
      );

      // Enter replay mode with commands
      this.replayStore.startReplay(savedState.commands);

      return {
        success: true,
        commandCount: savedState.commands.length,
      };
    } catch (error) {
      console.error('Failed to load game from file in replay mode:', error);
      throw error;
    }
  }

  /**
   * Execute next command in replay mode
   */
  async executeNextReplayCommand(): Promise<{
    success: boolean;
    hasMore: boolean;
  }> {
    if (!this.replayStore.isReplayMode) {
      return { success: false, hasMore: false };
    }

    const commandData = this.replayStore.nextCommand();
    if (!commandData) {
      return { success: false, hasMore: false };
    }

    try {
      const command = this.commandFactory.fromCommandData(commandData);
      await command.execute();

      return {
        success: true,
        hasMore: this.replayStore.hasNextCommand,
      };
    } catch (error) {
      console.error('Failed to execute replay command:', error);
      return { success: false, hasMore: this.replayStore.hasNextCommand };
    }
  }

  /**
   * Exit replay mode
   */
  exitReplayMode(): void {
    this.replayStore.exitReplay();
  }

  /**
   * Get game statistics
   */
  getGameStats() {
    return this.commandHistory.getStats();
  }

  /**
   * Get command log
   */
  getCommandLog(): string[] {
    return this.commandHistory.getCommandLog();
  }

  /**
   * Export game history as JSON
   */
  exportHistory(): string {
    return this.commandHistory.exportHistory();
  }

  /**
   * Get replay data for debugging
   */
  getReplayData() {
    return this.gameStateSerializer.getReplayData();
  }

  /**
   * Validate current game state
   */
  validateGameState(): {
    valid: boolean;
    errors: string[];
  } {
    const errors: string[] = [];

    // Validate players
    if (this.playerStore.players.length === 0) {
      errors.push('No players in game');
    }

    // Validate current player index
    if (
      this.playerStore.currentPlayerIndex < 0 ||
      this.playerStore.currentPlayerIndex >= this.playerStore.players.length
    ) {
      errors.push('Invalid current player index');
    }

    // Validate deck integrity
    if (!this.deckStore.validateDeckIntegrity()) {
      errors.push('Deck integrity check failed');
    }

    // Validate phase
    if (!this.gamePhaseStore.phase) {
      errors.push('Invalid game phase');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Reset all stores to initial state
   */
  private resetAllStores(): void {
    this.commandHistory.clear();
    this.playerStore.reset();
    this.deckStore.reset();
    this.gamePhaseStore.reset();
    this.actionStore.reset();
    this.tossInStore.reset();
  }

  /**
   * Check if game can be saved
   */
  canSaveGame(): {
    canSave: boolean;
    reason?: string;
  } {
    const history = this.commandHistory.getCommandDataHistory();

    if (history.length === 0) {
      return { canSave: false, reason: 'No game history to save' };
    }

    if (history[0].type !== 'INITIALIZE_GAME') {
      return { canSave: false, reason: 'Missing INITIALIZE_GAME command' };
    }

    const validation = this.validateGameState();
    if (!validation.valid) {
      return {
        canSave: true,
        reason: `Invalid game state: ${validation.errors.join(', ')}`,
      };
    }

    return { canSave: true };
  }

  /**
   * Get current game state snapshot (for debugging)
   */
  getGameStateSnapshot() {
    return {
      players: this.playerStore.players.length,
      currentPlayerIndex: this.playerStore.currentPlayerIndex,
      turnCount: this.playerStore.turnCount,
      drawPileSize: this.deckStore.drawPileSize,
      discardPileSize: this.deckStore.discardPileSize,
      phase: this.gamePhaseStore.phase,
      subPhase: this.gamePhaseStore.subPhase,
      finalTurnTriggered: this.gamePhaseStore.finalTurnTriggered,
      hasAction: this.actionStore.isExecutingAction,
      tossInActive: this.tossInStore.isActive,
      commandCount: this.commandHistory.getHistory().length,
    };
  }
}
