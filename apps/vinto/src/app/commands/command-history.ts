// commands/command-history.ts
import { injectable } from 'tsyringe';
import { makeAutoObservable } from 'mobx';
import { ICommand, CommandResult, CommandData } from './command';

/**
 * Command History Manager
 * Tracks all executed commands and provides replay/undo functionality
 */
@injectable()
export class CommandHistory {
  private history: CommandResult[] = [];
  private maxHistorySize = 1000; // Prevent memory issues
  private currentTurnStartIndex = 0; // Track where the current turn starts

  constructor() {
    makeAutoObservable(this);
  }

  /**
   * Execute a command and add it to history
   */
  async executeCommand(command: ICommand): Promise<CommandResult> {
    const startTime = Date.now();
    let success = false;
    let error: Error | undefined;

    try {
      const result = await command.execute();
      success = result !== false;

      if (!success) {
        console.warn(
          '[CommandHistory] Command returned false:',
          command.getDescription()
        );
      }
    } catch (e) {
      success = false;
      error = e instanceof Error ? e : new Error(String(e));
      console.error(
        '[CommandHistory] Command execution failed:',
        command.getDescription(),
        error
      );
    }

    const result: CommandResult = {
      success,
      command,
      error,
      timestamp: startTime,
    };

    this.addToHistory(result);

    console.log(
      `[CommandHistory] ${
        success ? '✓' : '✗'
      } ${command.getDescription()} (total: ${this.history.length})`
    );

    return result;
  }

  /**
   * Add command result to history
   */
  private addToHistory(result: CommandResult) {
    this.history.push(result);

    // Limit history size
    if (this.history.length > this.maxHistorySize) {
      this.history.shift();
    }
  }

  /**
   * Get all command history
   */
  getHistory(): CommandResult[] {
    return [...this.history];
  }

  /**
   * Get serializable command data for save/replay
   */
  getCommandDataHistory(): CommandData[] {
    return this.history
      .filter((result) => result.success)
      .map((result) => result.command.toData());
  }

  /**
   * Get last N commands
   */
  getRecentCommands(count: number): CommandResult[] {
    return this.history.slice(-count);
  }

  /**
   * Get commands for a specific player
   */
  getPlayerCommands(playerId: string): CommandResult[] {
    return this.history.filter((result) => {
      const data = result.command.toData();
      return data.playerId === playerId;
    });
  }

  /**
   * Get command statistics
   */
  getStats() {
    const total = this.history.length;
    const successful = this.history.filter((r) => r.success).length;
    const failed = total - successful;

    const commandTypes = this.history.reduce((acc, result) => {
      const type = result.command.toData().type;
      acc[type] = (acc[type] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    return {
      total,
      successful,
      failed,
      commandTypes,
    };
  }

  /**
   * Clear history
   */
  clear() {
    this.history = [];
  }

  /**
   * Export history for debugging
   */
  exportHistory(): string {
    return JSON.stringify(this.getCommandDataHistory(), null, 2);
  }

  /**
   * Get human-readable command log
   */
  getCommandLog(): string[] {
    return this.history.map((result, index) => {
      const status = result.success ? '✓' : '✗';
      const time = new Date(result.timestamp).toLocaleTimeString();
      const desc = result.command.getDescription();
      return `${index + 1}. [${time}] ${status} ${desc}`;
    });
  }

  /**
   * Get player actions from the current turn (since last turn boundary)
   * Returns actions with playerId and description so component can filter by bot/human
   */
  getRecentPlayerActions(): Array<{
    playerId: string | undefined;
    description: string;
  }> {
    console.log('[CommandHistory] Getting actions from turn start:', {
      currentTurnStartIndex: this.currentTurnStartIndex,
      totalHistory: this.history.length,
    });

    // Get commands from current turn start to end
    const currentTurnCommands = this.history.slice(this.currentTurnStartIndex);

    const filtered = currentTurnCommands.filter((result) => {
      if (!result.success) {
        return false;
      }
      const data = result.command.toData();
      // Check both top-level playerId and payload.playerId
      const playerId = data.playerId || data.payload?.playerId;
      const hasPlayerId = !!playerId;
      console.log('[CommandHistory] Checking command:', {
        type: data.type,
        playerId,
        hasPlayerId,
      });
      // Only include commands that have a playerId (player-initiated actions)
      return hasPlayerId;
    });

    const actions = filtered.map((result) => {
      const data = result.command.toData();
      // Check both top-level playerId and payload.playerId
      const playerId = data.playerId || data.payload?.playerId;

      // Use detailed description if available, otherwise fall back to basic description
      const description = result.command.getDetailedDescription
        ? result.command.getDetailedDescription()
        : result.command.getDescription();

      console.log('[CommandHistory] Action from current turn:', {
        playerId,
        description,
      });

      return {
        playerId,
        description,
      };
    });

    console.log(
      '[CommandHistory] Returning actions from current turn:',
      actions
    );
    return actions;
  }

  /**
   * Mark turn boundary - called when a new turn starts
   * Sets the index where the current turn begins in history
   */
  markTurnBoundary() {
    this.currentTurnStartIndex = this.history.length;
    console.log(
      '[CommandHistory] Marked turn boundary at index:',
      this.currentTurnStartIndex
    );

    // Keep only the last 50 commands to prevent memory bloat
    if (this.history.length > 100) {
      const removed = this.history.length - 50;
      this.history = this.history.slice(-50);
      this.currentTurnStartIndex = Math.max(
        0,
        this.currentTurnStartIndex - removed
      );
      console.log(
        '[CommandHistory] Trimmed history, new turn start index:',
        this.currentTurnStartIndex
      );
    }
  }

  /**
   * @deprecated Use markTurnBoundary() instead
   */
  clearRecentActions() {
    this.markTurnBoundary();
  }
}
