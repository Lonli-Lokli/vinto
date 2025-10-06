// commands/command.ts
/**
 * Command Pattern Implementation for Game State Management
 *
 * This allows:
 * - Full game history tracking
 * - Replay functionality
 * - Undo/Redo capabilities
 * - Easy debugging and testing
 * - Save/Restore game state
 */

/**
 * Union type of all possible command types
 */
export type CommandKind =
  | 'INITIALIZE_GAME'
  | 'DRAW_CARD'
  | 'SWAP_CARDS'
  | 'PEEK_CARD'
  | 'DISCARD_CARD'
  | 'REPLACE_CARD'
  | 'ADVANCE_TURN'
  | 'DECLARE_KING_ACTION'
  | 'TOSS_IN'
  | 'ADD_PENALTY_CARD'
  | 'PLAY_ACTION_CARD';

/**
 * Serializable command data that can be saved/restored
 */
export interface CommandData {
  type: CommandKind;
  timestamp: number;
  playerId?: string;
  payload: Record<string, any>;
}

/**
 * Base command interface
 */
export interface ICommand {
  /**
   * Execute the command
   * @returns true if command executed successfully
   */
  execute(): boolean | Promise<boolean>;

  /**
   * Undo the command (optional - for undo/redo functionality)
   * @returns true if undo was successful
   */
  undo?(): boolean | Promise<boolean>;

  /**
   * Serialize command to data for save/replay
   */
  toData(): CommandData;

  /**
   * Get human-readable description for debugging
   */
  getDescription(): string;

  /**
   * Get detailed human-readable description for UI display
   * Includes player names, targets, and action results
   */
  getDetailedDescription?(): string;
}

/**
 * Abstract base command class with common functionality
 */
export abstract class Command implements ICommand {
  protected timestamp: number;

  constructor() {
    this.timestamp = Date.now();
  }

  abstract execute(): boolean | Promise<boolean>;

  undo?(): boolean | Promise<boolean>;

  abstract toData(): CommandData;

  abstract getDescription(): string;

  protected createCommandData(
    type: CommandKind,
    payload: Record<string, any>
  ): CommandData {
    return {
      type,
      timestamp: this.timestamp,
      payload,
    };
  }
}

/**
 * Command result for tracking execution
 */
export interface CommandResult {
  success: boolean;
  command: ICommand;
  error?: Error;
  timestamp: number;
}
