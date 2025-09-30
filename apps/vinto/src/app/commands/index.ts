// commands/index.ts
/**
 * Command Pattern for Game State Management
 *
 * Export all command-related functionality
 */

export { Command } from './command';
export type { ICommand, CommandData, CommandResult } from './command';
export { CommandHistory, getCommandHistory, resetCommandHistory } from './command-history';
export { CommandFactory } from './command-factory';
export {
  DrawCardCommand,
  SwapCardsCommand,
  PeekCardCommand,
  DiscardCardCommand,
  ReplaceCardCommand,
  AdvanceTurnCommand,
  DeclareKingActionCommand,
  TossInCardCommand,
  AddPenaltyCardCommand,
} from './game-commands';
