// services/animation-service.ts
/**
 * AnimationService - Centralized animation orchestration
 *
 * This service listens to game state updates and triggers appropriate animations
 * Completely isolated from UI components - reacts purely to store updates
 */

import { injectable, inject } from 'tsyringe';
import {
  AnimationStep,
  CardAnimationStore,
} from '../stores/card-animation-store';
import { GameAction, GameState, SwapCardAction } from '@/shared';

@injectable()
export class AnimationService {
  private animationStore: CardAnimationStore;

  constructor(@inject(CardAnimationStore) animationStore: CardAnimationStore) {
    this.animationStore = animationStore;
  }

  /**
   * Handle game state updates and trigger appropriate animations
   * This is called from GameClientContext after each action
   */
  handleStateUpdate(
    oldState: GameState,
    newState: GameState,
    action: GameAction
  ): void {
    console.log('[AnimationService] Handling action:', action.type);

    // Animation service only handles animations, not UI state management

    switch (action.type) {
      case 'DRAW_CARD':
        this.handleDrawCard(oldState, newState, action);
        break;

      case 'DISCARD_CARD':
        this.handleDiscardCard(oldState, newState, action);
        break;

      case 'SWAP_CARD':
        this.handleSwapCard(oldState, newState, action);
        break;

      case 'USE_CARD_ACTION':
        this.handleUseCardAction(oldState, newState, action);
        break;

      case 'PARTICIPATE_IN_TOSS_IN':
        this.handleTossIn(oldState, newState, action);
        break;

      case 'CONFIRM_PEEK':
        this.handleConfirmPeek(oldState, newState, action);
        break;

      case 'DECLARE_KING_ACTION':
        this.handleDeclareKingAction(oldState, newState, action);
        break;

      case 'SELECT_ACTION_TARGET':
        this.handleSelectActionTarget(oldState, newState, action);
        break;

      default:
        // No animation needed for this action
        break;
    }
  }

  /**
   * Handle DRAW_CARD action animation
   * - For human: Deck -> Drawn card area (pending card)
   * - For bot: Deck -> Bot player position with full rotation (no animation until swap/discard)
   */
  private handleDrawCard(
    oldState: GameState,
    newState: GameState,
    action: GameAction & { type: 'DRAW_CARD' }
  ): void {
    const playerId = action.payload.playerId;
    const player = newState.players.find((p) => p.id === playerId);

    if (!player) return;

    const drawnCard = newState.pendingAction?.card;
    if (!drawnCard) return;

    // Only animate draw for human players (card goes to pending area)
    // For bots, the card is handled internally - no animation until they swap/discard
    if (player.isHuman) {
      this.animationStore.startDrawAnimation(
        drawnCard,
        { type: 'draw' },
        { type: 'drawn' },
        1500,
        true,
        false
      );
    }
  }

  /**
   * Handle DISCARD_CARD action animation
   * - Drawn card area -> Discard pile
   */
  private handleDiscardCard(
    oldState: GameState,
    newState: GameState,
    _action: GameAction & { type: 'DISCARD_CARD' }
  ): void {
    // Get the card that was just discarded (top of discard pile)
    const discardedCard = newState.discardPile.peekTop();
    if (!discardedCard) return;

    // Card moves from pending/drawn area to discard pile
    this.animationStore.startDiscardAnimation(
      discardedCard,
      { type: 'drawn' },
      { type: 'discard' },
      1500
    );
  }

  /**
   * Handle SWAP_CARD action animation
   * - Drawn card -> Player position
   * - Old card at position -> Discard pile
   */
  private handleSwapCard(
    oldState: GameState,
    newState: GameState,
    action: SwapCardAction
  ): void {
    const playerId = action.payload.playerId;
    const position = action.payload.position;
    const declaredRank = action.payload.declaredRank;
    const player = newState.players.find((p) => p.id === playerId);

    if (!player) return;

    // Get the new card that's now at the position
    const newCard = player.cards[position];

    // Check if this was a rank declaration
    const hadDeclaration = declaredRank !== undefined;

    // Determine if declaration was correct by checking the new state
    // If correct: card action is pending (subPhase === 'awaiting_action')
    // If incorrect or no declaration: card is on discard pile
    const declarationCorrect =
      hadDeclaration && newState.subPhase === 'awaiting_action';
    const declarationIncorrect = hadDeclaration && !declarationCorrect;

    if (!newCard) return;

    // Handle based on declaration result
    if (declarationCorrect) {
      // Correct declaration: Sequential animation
      // 1. New card: drawn → hand
      // 2. Old card: hand → drawn area
      const declaredCard = newState.pendingAction?.card;
      if (declaredCard) {
        this.animationStore.startAnimationSequence('parallel', [
          {
            type: 'swap',
            card: newCard,
            from: { type: 'drawn' },
            to: { type: 'player', playerId, position },
            duration: 1500,
            revealed: player.isHuman,
          },
          {
            type: 'swap',
            card: declaredCard,
            from: { type: 'player', playerId, position },
            to: { type: 'drawn' },
            duration: 1500,
            revealed: true,
          },
        ]);
        console.log(
          '[AnimationService] Correct declaration - sequential: drawn→hand, then hand→drawn'
        );
      }
    } else if (declarationIncorrect) {
      // Incorrect declaration: Sequential animation
      // 1. New card: drawn → hand
      // 2. Old card: hand → discard
      // 3. Penalty card: draw pile → hand
      const oldCard = newState.discardPile.peekTop();
      const oldPlayer = oldState.players.find((p) => p.id === playerId);
      const penaltyCardPosition = player.cards.length - 1;
      const penaltyCard = player.cards[penaltyCardPosition];

      const steps: AnimationStep[] = [
        {
          type: 'swap',
          card: newCard,
          from: { type: 'drawn' },
          to: { type: 'player', playerId, position },
          duration: 1500,
          revealed: player.isHuman,
        },
      ];

      if (oldCard) {
        steps.push({
          type: 'discard',
          card: oldCard,
          from: { type: 'player', playerId, position },
          to: { type: 'discard' },
          duration: 1500,
          revealed: true,
        });
      }

      if (
        penaltyCard &&
        oldPlayer &&
        penaltyCardPosition >= oldPlayer.cards.length
      ) {
        steps.push({
          type: 'draw',
          card: penaltyCard,
          from: { type: 'draw' },
          to: { type: 'player', playerId, position: penaltyCardPosition },
          duration: 1500,
          revealed: player.isHuman,
          fullRotation: false,
        });
      }

      this.animationStore.startAnimationSequence('sequential', steps);
      console.log(
        '[AnimationService] Incorrect declaration - sequential: drawn→hand, hand→discard, draw→hand'
      );
    } else {
      // No declaration: Sequential animation
      // 1. New card: drawn → hand
      // 2. Old card: hand → discard
      const oldCard = newState.discardPile.peekTop();

      const steps: AnimationStep[] = [
        {
          type: 'swap',
          card: newCard,
          from: { type: 'drawn' },
          to: { type: 'player', playerId, position },
          duration: 1500,
          revealed: player.isHuman,
        },
      ];

      if (oldCard) {
        steps.push({
          type: 'discard',
          card: oldCard,
          from: { type: 'player', playerId, position },
          to: { type: 'discard' },
          duration: 1500,
          revealed: true,
        });
      }

      this.animationStore.startAnimationSequence('parallel', steps);
    }
  }

  /**
   * Handle USE_CARD_ACTION action animation
   * - Shows card with special play-action effect
   * - Moves to center of screen with glow
   * - Then to discard pile
   */
  private handleUseCardAction(
    oldState: GameState,
    newState: GameState,
    action: GameAction & { type: 'USE_CARD_ACTION' }
  ): void {
    const card = action.payload.card;

    // Sequential animation: play-action effect, then move to discard
    this.animationStore.startAnimationSequence('sequential', [
      {
        type: 'play-action',
        card,
        from: { type: 'drawn' },
        duration: 2000,
      },
      {
        type: 'discard',
        card,
        from: { type: 'drawn' },
        to: { type: 'discard' },
        duration: 1500,
      },
    ]);
  }

  /**
   * Handle PARTICIPATE_IN_TOSS_IN action animation
   * - Player position -> Discard pile
   */
  private handleTossIn(
    oldState: GameState,
    newState: GameState,
    action: GameAction & { type: 'PARTICIPATE_IN_TOSS_IN' }
  ): void {
    const playerId = action.payload.playerId;
    const position = action.payload.position;

    // Get the card that was tossed in (should be on top of discard pile)
    const tossedCard = newState.discardPile.peekTop();
    if (!tossedCard) return;

    // Animate from player position to discard pile
    this.animationStore.startDiscardAnimation(
      tossedCard,
      { type: 'player', playerId, position },
      { type: 'discard' },
      1000 // Faster animation for toss-in
    );
  }

  /**
   * Handle CONFIRM_PEEK action animation
   * Card used for peek action (7, 8, 9, 10) animates to discard pile
   */
  private handleConfirmPeek(
    oldState: GameState,
    newState: GameState,
    action: GameAction & { type: 'CONFIRM_PEEK' }
  ): void {
    const playerId = action.payload.playerId;
    const peekCard = newState.discardPile.peekTop();

    if (!peekCard) return;

    // Check if this card came from a swap declaration (has swapPosition)
    const swapPosition = oldState.pendingAction?.swapPosition;

    if (swapPosition !== undefined) {
      // Card came from hand position after correct declaration
      // Animate from hand position to discard pile
      this.animationStore.startDiscardAnimation(
        peekCard,
        { type: 'player', playerId, position: swapPosition },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] Declared card action complete - animating from hand to discard'
      );
    } else {
      // Card came from draw/discard pile (normal flow)
      // Animate from drawn position to discard pile
      this.animationStore.startDiscardAnimation(
        peekCard,
        { type: 'drawn' },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] Peek action complete - animating from drawn to discard'
      );
    }
  }

  /**
   * Handle DECLARE_KING_ACTION animation
   * King card animates to discard pile after declaring rank for toss-in
   */
  private handleDeclareKingAction(
    oldState: GameState,
    newState: GameState,
    action: GameAction & { type: 'DECLARE_KING_ACTION' }
  ): void {
    const playerId = action.payload.playerId;
    const kingCard = newState.discardPile.peekTop();

    if (!kingCard || kingCard.rank !== 'K') return;

    // Check if this card came from a swap declaration (has swapPosition)
    const swapPosition = oldState.pendingAction?.swapPosition;

    if (swapPosition !== undefined) {
      // Card came from hand position after correct declaration
      // Animate from hand position to discard pile
      this.animationStore.startDiscardAnimation(
        kingCard,
        { type: 'player', playerId, position: swapPosition },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] Declared King action complete - animating from hand to discard'
      );
    } else {
      // Card came from draw/discard pile (normal flow)
      // Animate from drawn position to discard pile
      this.animationStore.startDiscardAnimation(
        kingCard,
        { type: 'drawn' },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] King action complete - animating from drawn to discard'
      );
    }
  }

  /**
   * Handle SELECT_ACTION_TARGET animation
   * Handles J (Jack) and A (Ace) actions that complete and move card to discard
   */
  private handleSelectActionTarget(
    oldState: GameState,
    newState: GameState,
    action: GameAction & { type: 'SELECT_ACTION_TARGET' }
  ): void {
    // Only animate if the action is complete (card went to discard)
    const wasCompleted = oldState.pendingAction && !newState.pendingAction;

    if (!wasCompleted) return;

    const playerId = action.payload.playerId;
    const actionCard = newState.discardPile.peekTop();

    if (!actionCard) return;

    // Only handle J and A cards (they complete on SELECT_ACTION_TARGET)
    if (actionCard.rank !== 'J' && actionCard.rank !== 'A') return;

    // Check if this card came from a swap declaration (has swapPosition)
    const swapPosition = oldState.pendingAction?.swapPosition;

    if (swapPosition !== undefined) {
      // Card came from hand position after correct declaration
      // Animate from hand position to discard pile
      this.animationStore.startDiscardAnimation(
        actionCard,
        { type: 'player', playerId, position: swapPosition },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] Declared J/A action complete - animating from hand to discard'
      );
    } else {
      // Card came from draw/discard pile (normal flow)
      // Animate from drawn position to discard pile
      this.animationStore.startDiscardAnimation(
        actionCard,
        { type: 'drawn' },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] J/A action complete - animating from drawn to discard'
      );
    }
  }

  /**
   * Reset all animations (useful for new game)
   */
  reset(): void {
    this.animationStore.reset();
  }
}
