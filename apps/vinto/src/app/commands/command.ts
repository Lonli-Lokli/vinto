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
 * Serializable command data that can be saved/restored
 */
export interface CommandData {
  type: string;
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

  protected createCommandData(type: string, payload: Record<string, any>): CommandData {
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
