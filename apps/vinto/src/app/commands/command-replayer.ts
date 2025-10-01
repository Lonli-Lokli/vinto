// commands/command-replayer.ts
/**
 * Command Replayer
 * Replays commands from serialized data to restore game state
 */

import { CommandData } from './command';
import { CommandFactory } from './command-factory';
import { CommandHistory } from './command-history';
import { PlayerStore, DeckStore } from '../stores';
import { Card } from '../shapes';

/**
 * CommandReplayer class
 */
export class CommandReplayer {
  constructor(
    private commandFactory: CommandFactory,
    private commandHistory: CommandHistory,
    private playerStore: PlayerStore,
    private deckStore: DeckStore
  ) {}

  /**
   * Replay a single command from serialized data
   */
  async replayCommand(data: CommandData): Promise<boolean> {
    let command;

    switch (data.type) {
      case 'INITIALIZE_GAME':
        // This should already be handled by the serializer's restoreInitialState
        console.log('Skipping INITIALIZE_GAME replay - already restored');
        return true;

      case 'DRAW_CARD':
        command = this.commandFactory.drawCard(data.payload.playerId);
        break;

      case 'SWAP_CARDS':
        command = this.commandFactory.swapCards(
          data.payload.player1Id,
          data.payload.position1,
          data.payload.player2Id,
          data.payload.position2
        );
        break;

      case 'PEEK_CARD':
        command = this.commandFactory.peekCard(
          data.payload.playerId,
          data.payload.position,
          data.payload.isPermanent
        );
        break;

      case 'DISCARD_CARD':
        // Reconstruct the card from serialized data
        const discardCard: Card = {
          id: data.payload.cardId,
          rank: data.payload.rank,
          value: data.payload.value,
          action: data.payload.action,
          played: data.payload.played || false,
        };
        command = this.commandFactory.discardCard(discardCard);
        break;

      case 'REPLACE_CARD':
        // Reconstruct the new card from serialized data
        const newCard: Card = {
          id: data.payload.newCard.id,
          rank: data.payload.newCard.rank,
          value: data.payload.newCard.value,
          action: data.payload.newCard.action,
          played: data.payload.newCard.played || false,
        };
        command = this.commandFactory.replaceCard(
          data.payload.playerId,
          data.payload.position,
          newCard
        );
        break;

      case 'ADVANCE_TURN':
        command = this.commandFactory.advanceTurn(data.payload.fromPlayerId);
        break;

      case 'DECLARE_KING_ACTION':
        command = this.commandFactory.declareKingAction(
          data.payload.declaredRank
        );
        break;

      case 'TOSS_IN':
        command = this.commandFactory.tossInCard(
          data.payload.playerId,
          data.payload.position,
          data.payload.matchingRank
        );
        break;

      case 'ADD_PENALTY_CARD':
        command = this.commandFactory.addPenaltyCard(data.payload.playerId);
        break;

      default:
        console.warn(`Unknown command type: ${data.type}`);
        return false;
    }

    // Execute the command through the history manager
    const result = await this.commandHistory.executeCommand(command);
    return result.success;
  }

  /**
   * Replay multiple commands in sequence
   */
  async replayCommands(commands: CommandData[]): Promise<{
    successful: number;
    failed: number;
    errors: Array<{ commandType: string; error: string }>;
  }> {
    let successful = 0;
    let failed = 0;
    const errors: Array<{ commandType: string; error: string }> = [];

    for (const commandData of commands) {
      try {
        const success = await this.replayCommand(commandData);
        if (success) {
          successful++;
        } else {
          failed++;
          errors.push({
            commandType: commandData.type,
            error: 'Command execution failed',
          });
        }
      } catch (error) {
        failed++;
        errors.push({
          commandType: commandData.type,
          error: error instanceof Error ? error.message : String(error),
        });
      }
    }

    return { successful, failed, errors };
  }

  /**
   * Replay commands with validation
   * Ensures that the game state is consistent after each command
   */
  async replayCommandsWithValidation(commands: CommandData[]): Promise<{
    successful: number;
    failed: number;
    errors: Array<{ commandType: string; index: number; error: string }>;
  }> {
    let successful = 0;
    let failed = 0;
    const errors: Array<{ commandType: string; index: number; error: string }> =
      [];

    for (let i = 0; i < commands.length; i++) {
      const commandData = commands[i];

      try {
        // Validate state before executing command
        if (!this.validateGameState()) {
          throw new Error('Invalid game state before command execution');
        }

        const success = await this.replayCommand(commandData);

        if (success) {
          // Validate state after executing command
          if (!this.validateGameState()) {
            throw new Error('Invalid game state after command execution');
          }
          successful++;
        } else {
          failed++;
          errors.push({
            commandType: commandData.type,
            index: i,
            error: 'Command execution failed',
          });
        }
      } catch (error) {
        failed++;
        errors.push({
          commandType: commandData.type,
          index: i,
          error: error instanceof Error ? error.message : String(error),
        });

        // Stop replay on validation errors
        console.error(`Replay stopped at command ${i} due to error:`, error);
        break;
      }
    }

    return { successful, failed, errors };
  }

  /**
   * Validate current game state
   */
  private validateGameState(): boolean {
    try {
      // Validate players
      if (this.playerStore.players.length === 0) {
        console.error('Validation failed: No players');
        return false;
      }

      // Validate current player index
      if (
        this.playerStore.currentPlayerIndex < 0 ||
        this.playerStore.currentPlayerIndex >= this.playerStore.players.length
      ) {
        console.error('Validation failed: Invalid current player index');
        return false;
      }

      // Validate deck integrity
      if (!this.deckStore.validateDeckIntegrity()) {
        console.error('Validation failed: Deck integrity check failed');
        return false;
      }

      // All validations passed
      return true;
    } catch (error) {
      console.error('Validation error:', error);
      return false;
    }
  }

  /**
   * Get replay progress
   */
  getReplayProgress(): {
    totalCommands: number;
    executedCommands: number;
    successRate: number;
  } {
    const stats = this.commandHistory.getStats();
    return {
      totalCommands: stats.total,
      executedCommands: stats.successful,
      successRate: stats.total > 0 ? stats.successful / stats.total : 0,
    };
  }
}

/**
 * Create replayer instance
 */
export function createCommandReplayer(
  commandFactory: CommandFactory,
  commandHistory: CommandHistory,
  playerStore: PlayerStore,
  deckStore: DeckStore
): CommandReplayer {
  return new CommandReplayer(
    commandFactory,
    commandHistory,
    playerStore,
    deckStore
  );
}
