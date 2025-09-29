'use client';

import { Card, Rank } from '../shapes';
import { PlayerStore } from './player-store';
import { ActionStore } from './action-store';
import { DeckStore } from './deck-store';
import { GamePhaseStore } from './game-phase-store';
import { GameToastService } from '../lib/toast-service';

export class CardActionHandler {
  constructor(
    private playerStore: PlayerStore,
    private actionStore: ActionStore,
    private deckStore: DeckStore,
    private phaseStore: GamePhaseStore
  ) {}

  // Main action execution entry point
  executeCardAction(card: Card, playerId: string): boolean {
    if (!card.action) return false;

    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    GameToastService.success(
      `${player.name} played ${card.rank} - ${card.action}`
    );

    // Start the action
    this.actionStore.startAction(card, playerId);
    this.deckStore.discardCard(card);

    // Set appropriate phase
    this.phaseStore.startAwaitingAction();

    // Execute based on card rank
    const executed = this.executeByRank(card.rank, playerId);

    if (!executed) {
      GameToastService.error(`Failed to execute ${card.rank} action`);
      this.cleanupAction();
    }

    return executed;
  }

  // Execute action based on card rank
  private executeByRank(rank: Rank, playerId: string): boolean {
    const handlers: Record<string, () => boolean> = {
      '7': () => this.handlePeekOwnCard(playerId),
      '8': () => this.handlePeekOwnCard(playerId),
      '9': () => this.handlePeekOpponentCard(playerId),
      '10': () => this.handlePeekOpponentCard(playerId),
      J: () => this.handleSwapCards(playerId),
      Q: () => this.handlePeekAndSwap(playerId),
      K: () => this.handleDeclareAction(playerId),
      A: () => this.handleForceDraw(playerId),
    };

    const handler = handlers[rank];
    return handler ? handler() : false;
  }

  // Individual action handlers
  private handlePeekOwnCard(playerId: string): boolean {
    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    if (player.isHuman) {
      // Human player selects which card to peek
      return true; // Action continues with user selection
    } else {
      // AI automatically peeks a random card
      setTimeout(() => {
        this.executeAIPeekOwnCard(playerId);
      }, 1500);
      return true;
    }
  }

  private handlePeekOpponentCard(playerId: string): boolean {
    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    if (player.isHuman) {
      // Human player selects which opponent card to peek
      return true; // Action continues with user selection
    } else {
      // AI automatically peeks a random opponent card
      setTimeout(() => {
        this.executeAIPeekOpponentCard(playerId);
      }, 1500);
      return true;
    }
  }

  private handleSwapCards(playerId: string): boolean {
    this.actionStore.clearSwapTargets();

    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    if (player.isHuman) {
      // Human player selects which cards to swap
      return true; // Action continues with user selection
    } else {
      // AI automatically selects cards to swap
      setTimeout(() => {
        this.executeAISwapCards(playerId);
      }, 1500);
      return true;
    }
  }

  private handlePeekAndSwap(playerId: string): boolean {
    this.actionStore.clearPeekTargets();

    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    if (player.isHuman) {
      // Human player selects which cards to peek
      return true; // Action continues with user selection
    } else {
      // AI automatically peeks and decides whether to swap
      setTimeout(() => {
        this.executeAIPeekAndSwap(playerId);
      }, 1500);
      return true;
    }
  }

  private handleDeclareAction(playerId: string): boolean {
    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    if (player.isHuman) {
      // Human player declares which action to execute
      return true; // Action continues with user selection
    } else {
      // AI automatically declares a random action
      setTimeout(() => {
        this.executeAIDeclareAction(playerId);
      }, 1500);
      return true;
    }
  }

  private handleForceDraw(playerId: string): boolean {
    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    if (player.isHuman) {
      // Human player selects which opponent to force draw
      return true; // Action continues with user selection
    } else {
      // AI automatically forces a random opponent to draw
      setTimeout(() => {
        this.executeAIForceDraw(playerId);
      }, 1500);
      return true;
    }
  }

  // Target selection handlers
  selectActionTarget(targetPlayerId: string, position: number): boolean {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer) return false;

    switch (context.targetType) {
      case 'own-card':
        return this.executePeekOwnCard(targetPlayerId, position);
      case 'opponent-card':
        return this.executePeekOpponentCard(targetPlayerId, position);
      case 'swap-cards':
        return this.handleSwapTargetSelection(targetPlayerId, position);
      case 'peek-then-swap':
        return this.handlePeekTargetSelection(targetPlayerId, position);
      case 'force-draw':
        return this.executeForceDraw(targetPlayerId);
      default:
        return false;
    }
  }

  // Specific execution methods
  private executePeekOwnCard(playerId: string, position: number): boolean {
    const player = this.playerStore.getPlayer(playerId);
    const context = this.actionStore.actionContext;

    if (!player || !context || context.playerId !== playerId) return false;

    if (position >= 0 && position < player.cards.length) {
      if (player.isHuman) {
        // For humans, make card temporarily visible (shown in UI)
        this.playerStore.makeCardTemporarilyVisible(playerId, position);

        const card = player.cards[position];
        GameToastService.success(
          `You peeked at position ${position + 1}: ${card.rank} (value ${
            card.value
          })`
        );

        // User must confirm to complete action - no automatic timeout
      } else {
        // For bots, permanently add to known cards for AI decision-making
        this.playerStore.addKnownCardPosition(playerId, position);
        this.completeAction();
      }

      return true;
    }

    return false;
  }

  private executePeekOpponentCard(
    targetPlayerId: string,
    position: number
  ): boolean {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer || targetPlayerId === context.playerId)
      return false;

    if (position >= 0 && position < targetPlayer.cards.length) {
      const peekedCard = targetPlayer.cards[position];

      // Make the card temporarily visible
      if (actionPlayer.isHuman) {
        this.playerStore.makeCardTemporarilyVisible(targetPlayerId, position);
      }

      GameToastService.success(
        `${actionPlayer.name} peeked at ${targetPlayer.name}'s position ${
          position + 1
        }: ${peekedCard.rank} (value ${peekedCard.value})`
      );

      // User must confirm to complete action - no automatic timeout
      if (!actionPlayer.isHuman) {
        this.completeAction();
      }

      return true;
    }

    return false;
  }

  private handleSwapTargetSelection(
    targetPlayerId: string,
    position: number
  ): boolean {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer) return false;

    const selected = this.actionStore.addSwapTarget(targetPlayerId, position);

    if (!selected && this.actionStore.swapTargets.length >= 2) {
      GameToastService.info(
        'Already have 2 cards selected. Deselect one to choose a different card.'
      );
      return false;
    }

    if (
      this.actionStore.swapTargets.length === 1 &&
      this.actionStore.swapTargets[0].playerId === targetPlayerId
    ) {
      GameToastService.warning('Cannot swap two cards from the same player!');
      this.actionStore.clearSwapTargets();
      return false;
    }

    if (this.actionStore.hasCompleteSwapSelection) {
      return this.executeSwapCards();
    } else {
      GameToastService.info(
        `${actionPlayer.name} selected first card. Choose second card to swap with.`
      );
      return true;
    }
  }

  private executeSwapCards(): boolean {
    const targets = this.actionStore.swapTargets;
    if (targets.length !== 2) return false;

    const [target1, target2] = targets;
    const player1 = this.playerStore.getPlayer(target1.playerId);
    const player2 = this.playerStore.getPlayer(target2.playerId);
    const context = this.actionStore.actionContext;

    if (!player1 || !player2 || !context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    if (!actionPlayer) return false;

    const success = this.playerStore.swapCards(
      target1.playerId,
      target1.position,
      target2.playerId,
      target2.position
    );

    if (success) {
      GameToastService.success(
        `${actionPlayer.name} swapped ${player1.name}'s card ${
          target1.position + 1
        } with ${player2.name}'s card ${target2.position + 1}`
      );
      this.completeAction();
      return true;
    }

    return false;
  }

  private handlePeekTargetSelection(
    targetPlayerId: string,
    position: number
  ): boolean {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer) return false;

    if (position >= 0 && position < targetPlayer.cards.length) {
      const peekedCard = targetPlayer.cards[position];
      const selected = this.actionStore.addPeekTarget(
        targetPlayerId,
        position,
        peekedCard
      );

      if (!selected && this.actionStore.peekTargets.length >= 2) {
        GameToastService.info(
          'Already have 2 cards selected. Deselect one to choose a different card.'
        );
        return false;
      }

      // For Queen actions (peek-then-swap), prevent selecting cards from same player
      if (
        this.actionStore.peekTargets.length === 1 &&
        this.actionStore.peekTargets[0].playerId === targetPlayerId &&
        context.targetType === 'peek-then-swap'
      ) {
        GameToastService.warning('Cannot peek two cards from the same player!');
        this.actionStore.clearPeekTargets();
        return false;
      }

      // Make the peeked card temporarily visible to human players
      if (actionPlayer.isHuman) {
        this.playerStore.makeCardTemporarilyVisible(targetPlayerId, position);
      }

      // Only show toast if peeking at opponent's card OR if it's a bot action
      // (Human peeking own cards can see them visually)
      if (targetPlayerId !== context.playerId || !actionPlayer.isHuman) {
        GameToastService.success(
          `${actionPlayer.name} peeked at ${targetPlayer.name}'s position ${
            position + 1
          }`
        );
      }

      if (this.actionStore.hasCompletePeekSelection) {
        const [peek1, peek2] = this.actionStore.peekTargets;
        GameToastService.info(
          `Cards peeked: ${peek1.card!.rank} (${peek1.card!.value}) and ${
            peek2.card!.rank
          } (${peek2.card!.value}). Choose to swap them or skip.`
        );
        // UI will show swap/skip buttons
        return true;
      } else {
        GameToastService.info(
          `${actionPlayer.name} peeked at first card. Choose second card to peek at.`
        );
        return true;
      }
    }

    return false;
  }

  private executeForceDraw(targetPlayerId: string): boolean {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer || targetPlayerId === context.playerId)
      return false;

    if (!this.deckStore.hasDrawCards) {
      this.deckStore.ensureDrawCards();
    }

    const drawnCard = this.deckStore.drawCard();
    if (drawnCard) {
      this.playerStore.addCardToPlayer(targetPlayerId, drawnCard);

      GameToastService.success(
        `${actionPlayer.name} forced ${targetPlayer.name} to draw a card. ${targetPlayer.name} now has ${targetPlayer.cards.length} cards.`
      );

      this.completeAction();
      return true;
    }

    return false;
  }

  // Queen-specific methods
  executeQueenSwap(): boolean {
    const targets = this.actionStore.peekTargets;
    if (targets.length !== 2) return false;

    const [target1, target2] = targets;
    const success = this.playerStore.swapCards(
      target1.playerId,
      target1.position,
      target2.playerId,
      target2.position
    );

    if (success) {
      GameToastService.success(
        `Queen action: Swapped ${target1.card!.rank} with ${target2.card!.rank}`
      );
    }

    this.completeAction();
    return success;
  }

  skipQueenSwap(): boolean {
    GameToastService.info('Queen action: Chose not to swap the peeked cards');
    this.completeAction();
    return true;
  }

  // King action declaration
  declareKingAction(rank: Rank): boolean {
    const context = this.actionStore.actionContext;
    if (!context || context.targetType !== 'declare-action') return false;

    this.actionStore.declareKingAction(rank);

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    if (!actionPlayer) return false;

    const declaredAction =
      this.actionStore.actionContext?.action || 'Unknown action';

    GameToastService.success(
      `${actionPlayer.name} declared King as ${rank} - ${declaredAction}`
    );

    // Execute the declared action
    return this.executeByRank(rank, context.playerId);
  }

  // Alias for consistency with bot calls
  handleKingDeclaration(rank: Rank): boolean {
    return this.declareKingAction(rank);
  }

  // AI execution methods
  private executeAIPeekOwnCard(playerId: string) {
    const player = this.playerStore.getPlayer(playerId);
    if (!player || player.isHuman) return;

    const randomPosition = Math.floor(Math.random() * player.cards.length);
    this.selectActionTarget(playerId, randomPosition);
  }

  private executeAIPeekOpponentCard(playerId: string) {
    const opponents = this.playerStore.getOpponents(playerId);
    if (opponents.length === 0) return;

    const randomOpponent =
      opponents[Math.floor(Math.random() * opponents.length)];
    const randomPosition = Math.floor(
      Math.random() * randomOpponent.cards.length
    );
    this.selectActionTarget(randomOpponent.id, randomPosition);
  }

  private executeAISwapCards(playerId: string) {
    const allPlayers = this.playerStore.players;
    const availableTargets: Array<{ playerId: string; position: number }> = [];

    allPlayers.forEach((p) => {
      for (let i = 0; i < p.cards.length; i++) {
        availableTargets.push({ playerId: p.id, position: i });
      }
    });

    if (availableTargets.length >= 2) {
      const shuffled = [...availableTargets].sort(() => Math.random() - 0.5);
      this.selectActionTarget(shuffled[0].playerId, shuffled[0].position);

      setTimeout(() => {
        this.selectActionTarget(shuffled[1].playerId, shuffled[1].position);
      }, 500);
    }
  }

  private executeAIPeekAndSwap(playerId: string) {
    const allPlayers = this.playerStore.players;
    const availableTargets: Array<{ playerId: string; position: number }> = [];

    allPlayers.forEach((p) => {
      for (let i = 0; i < p.cards.length; i++) {
        availableTargets.push({ playerId: p.id, position: i });
      }
    });

    if (availableTargets.length >= 2) {
      const shuffled = [...availableTargets].sort(() => Math.random() - 0.5);
      this.selectActionTarget(shuffled[0].playerId, shuffled[0].position);

      setTimeout(() => {
        this.selectActionTarget(shuffled[1].playerId, shuffled[1].position);

        // AI decides randomly whether to swap after peeking
        setTimeout(() => {
          if (Math.random() < 0.5) {
            this.executeQueenSwap();
          } else {
            this.skipQueenSwap();
          }
        }, 500);
      }, 500);
    }
  }

  private executeAIDeclareAction(playerId: string) {
    const ranks: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'A'];
    const randomRank = ranks[Math.floor(Math.random() * ranks.length)];
    this.declareKingAction(randomRank);
  }

  private executeAIForceDraw(playerId: string) {
    const opponents = this.playerStore.getOpponents(playerId);
    if (opponents.length === 0) return;

    const randomOpponent =
      opponents[Math.floor(Math.random() * opponents.length)];
    this.selectActionTarget(randomOpponent.id, 0); // Position doesn't matter for force draw
  }

  // Action completion
  confirmPeekCompletion(): boolean {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    if (!actionPlayer?.isHuman) return false;

    // Only allow confirmation for peek actions that are waiting
    if (
      context.targetType !== 'own-card' &&
      context.targetType !== 'opponent-card'
    ) {
      return false;
    }

    // Clear visibility and action context, but don't return to idle yet
    // The caller (game-store) will handle the phase transition to toss-in
    this.playerStore.clearTemporaryCardVisibility();
    this.actionStore.clearAction();
    // Note: NOT calling phaseStore.returnToIdle() here - let caller handle it
    return true;
  }

  private completeAction() {
    this.playerStore.clearTemporaryCardVisibility();
    this.cleanupAction();
  }

  private cleanupAction() {
    this.actionStore.clearAction();
    this.phaseStore.returnToIdle();
  }
}
