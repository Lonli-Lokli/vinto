'use client';

import { Rank } from '../shapes';
import { PlayerStore } from './player-store';
import { ActionStore } from './action-store';
import { GameToastService } from '../services/toast-service';
import { CommandFactory, CommandHistory } from '../commands';

export interface ActionHandlerDependencies {
  playerStore: PlayerStore;
  actionStore: ActionStore;
  commandFactory: CommandFactory;
  commandHistory: CommandHistory;
}

/**
 * Handles all human player action logic.
 * Responsible for:
 * - Waiting for human input/selection
 * - Validating human selections
 * - Showing appropriate feedback
 */
export class HumanActionHandler {
  private playerStore: PlayerStore;
  private actionStore: ActionStore;
  private commandFactory: CommandFactory;
  private commandHistory: CommandHistory;

  constructor(deps: ActionHandlerDependencies) {
    this.playerStore = deps.playerStore;
    this.actionStore = deps.actionStore;
    this.commandFactory = deps.commandFactory;
    this.commandHistory = deps.commandHistory;
  }

  // Action initiation - waits for user input
  handlePeekOwnCard(_playerId: string): boolean {
    // Human player selects which card to peek via UI
    GameToastService.info('Select one of your cards to peek');
    return true; // Action continues with user selection
  }

  handlePeekOpponentCard(_playerId: string): boolean {
    // Human player selects which opponent card to peek via UI
    GameToastService.info('Select an opponent card to peek');
    return true; // Action continues with user selection
  }

  handleSwapCards(_playerId: string): boolean {
    // Human player selects which cards to swap via UI
    GameToastService.info('Select first card to swap');
    return true; // Action continues with user selection
  }

  handlePeekAndSwap(_playerId: string): boolean {
    // Human player selects which cards to peek via UI
    GameToastService.info('Select first card to peek');
    return true; // Action continues with user selection
  }

  handleDeclareAction(_playerId: string): boolean {
    // Human player declares which action to execute via UI
    GameToastService.info('Declare which card action you want to execute');
    return true; // Action continues with user selection
  }

  handleForceDraw(_playerId: string): boolean {
    // Human player selects which opponent to force draw via UI
    GameToastService.info('Select an opponent to force draw a card');
    return true; // Action continues with user selection
  }

  // Target selection execution
  executePeekOwnCard(playerId: string, position: number): boolean {
    const player = this.playerStore.getPlayer(playerId);
    const context = this.actionStore.actionContext;

    if (!player || !context || context.playerId !== playerId) return false;

    if (position >= 0 && position < player.cards.length) {
      // For humans, make card temporarily visible (shown in UI)
      this.playerStore.makeCardTemporarilyVisible(playerId, position);
      // User must confirm to complete action - no automatic timeout
      return true;
    }

    return false;
  }

  executePeekOpponentCard(
    actionPlayerId: string,
    targetPlayerId: string,
    position: number
  ): boolean {
    const actionPlayer = this.playerStore.getPlayer(actionPlayerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer || targetPlayerId === actionPlayerId) {
      return false;
    }

    if (position >= 0 && position < targetPlayer.cards.length) {
      // Make the card temporarily visible
      this.playerStore.makeCardTemporarilyVisible(targetPlayerId, position);
      // User must confirm to complete action
      return true;
    }

    return false;
  }

  async handleSwapTargetSelection(
    actionPlayerId: string,
    targetPlayerId: string,
    position: number
  ): Promise<boolean> {
    const actionPlayer = this.playerStore.getPlayer(actionPlayerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer) return false;

    const selected = this.actionStore.addSwapTarget(targetPlayerId, position);

    if (!selected && this.actionStore.swapTargets.length >= 2) {
      GameToastService.info(
        'Already have 2 cards selected. Deselect one to choose a different card.'
      );
      return false;
    }

    // Allow swapping any two cards, including from the same player
    if (this.actionStore.hasCompleteSwapSelection) {
      return await this.executeSwapCards(actionPlayerId);
    } else {
      GameToastService.info(
        'Selected first card. Choose second card to swap with.'
      );
      return true;
    }
  }

  async executeSwapCards(_actionPlayerId: string): Promise<boolean> {
    const targets = this.actionStore.swapTargets;
    if (targets.length !== 2) return false;

    const [target1, target2] = targets;
    const player1 = this.playerStore.getPlayer(target1.playerId);
    const player2 = this.playerStore.getPlayer(target2.playerId);

    if (!player1 || !player2) return false;

    // Execute swap using command
    const swapCommand = this.commandFactory.swapCards(
      target1.playerId,
      target1.position,
      target2.playerId,
      target2.position
    );
    const result = await this.commandHistory.executeCommand(swapCommand);

    return result.success;
  }

  handlePeekTargetSelection(
    actionPlayerId: string,
    targetPlayerId: string,
    position: number
  ): boolean {
    const actionPlayer = this.playerStore.getPlayer(actionPlayerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);
    const context = this.actionStore.actionContext;

    if (!actionPlayer || !targetPlayer || !context) return false;

    if (position >= 0 && position < targetPlayer.cards.length) {
      const peekedCard = targetPlayer.cards[position];
      const selected = this.actionStore.addPeekTarget(
        targetPlayerId,
        position,
        peekedCard
      );

      // Handle selection failure
      if (!selected) {
        if (this.actionStore.peekTargets.length >= 2) {
          GameToastService.info(
            'You have already selected 2 cards. Use the buttons below to swap or skip.'
          );
        } else if (
          this.actionStore.peekTargets.length === 1 &&
          this.actionStore.peekTargets[0].playerId === targetPlayerId
        ) {
          // Don't clear - the validation in action-store already prevents adding
          // Just show the warning
          GameToastService.warning(
            'Cannot peek two cards from the same player! Please choose from a different player.'
          );
        }
        return false;
      }

      // Make the peeked card temporarily visible
      this.playerStore.makeCardTemporarilyVisible(targetPlayerId, position);

      if (this.actionStore.hasCompletePeekSelection) {
        const [peek1, peek2] = this.actionStore.peekTargets;
        GameToastService.info(
          `Cards peeked: ${peek1.card!.rank} (${peek1.card!.value}) and ${
            peek2.card!.rank
          } (${peek2.card!.value}). Choose to swap them or skip.`
        );
        return true;
      } else {
        GameToastService.info(
          'Peeked at first card. Choose second card to peek at.'
        );
        return true;
      }
    }

    return false;
  }

  async executeQueenSwap(): Promise<boolean> {
    const targets = this.actionStore.peekTargets;
    if (targets.length !== 2) return false;

    const [target1, target2] = targets;

    // Execute swap using command - revealed since Queen action shows the cards
    const swapCommand = this.commandFactory.swapCards(
      target1.playerId,
      target1.position,
      target2.playerId,
      target2.position,
      true // Reveal cards during animation for Queen action
    );
    const result = await this.commandHistory.executeCommand(swapCommand);
    return result.success;
  }

  skipQueenSwap(): boolean {
    GameToastService.info('Queen action: Chose not to swap the peeked cards');
    return true;
  }

  executeForceDraw(actionPlayerId: string, targetPlayerId: string): boolean {
    const actionPlayer = this.playerStore.getPlayer(actionPlayerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer || targetPlayerId === actionPlayerId)
      return false;

    return true;
  }

  declareKingAction(playerId: string, rank: Rank): boolean {
    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    this.actionStore.declareKingAction(rank);
    return true;
  }

  confirmPeekCompletion(playerId: string): boolean {
    const context = this.actionStore.actionContext;
    if (!context || context.playerId !== playerId) return false;

    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    // Only allow confirmation for peek actions and force-draw that are waiting
    if (
      context.targetType !== 'own-card' &&
      context.targetType !== 'opponent-card' &&
      context.targetType !== 'force-draw'
    ) {
      return false;
    }

    // Clear visibility - caller will handle phase transition
    this.playerStore.clearTemporaryCardVisibility();
    return true;
  }
}
