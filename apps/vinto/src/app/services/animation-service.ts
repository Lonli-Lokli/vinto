// services/animation-service.ts
/**
 * AnimationService - Centralized animation orchestration
 *
 * This service listens to game state updates and triggers appropriate animations
 * Completely isolated from UI components - reacts purely to store updates
 */

import { injectable, inject } from 'tsyringe';
import { CardAnimationStore } from '../stores/card-animation-store';
import { UIStore } from '../stores/ui-store';
import { GameAction, GameState } from '@/shared';

@injectable()
export class AnimationService {
  private animationStore: CardAnimationStore;
  private uiStore: UIStore;

  constructor(
    @inject(CardAnimationStore) animationStore: CardAnimationStore,
    @inject(UIStore) uiStore: UIStore
  ) {
    this.animationStore = animationStore;
    this.uiStore = uiStore;
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

    // Clear temporary visible cards when transitioning from setup to playing
    if (oldState.phase === 'setup' && newState.phase === 'playing') {
      console.log(
        '[AnimationService] Setup phase ended, clearing temporary visible cards'
      );
      this.uiStore.clearTemporaryCardVisibility();
    }

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
    action: GameAction & { type: 'SWAP_CARD' }
  ): void {
    const playerId = action.payload.playerId;
    const position = action.payload.position;
    const player = newState.players.find((p) => p.id === playerId);

    if (!player) return;

    // Get the new card that's now at the position
    const newCard = player.cards[position];
    // Get the old card that was discarded (top of discard pile)
  const oldCard = newState.discardPile.peekTop();

    if (!newCard || !oldCard) return;

    // Animate new card from drawn area to player position
    this.animationStore.startSwapAnimation(
      newCard,
      { type: 'drawn' },
      { type: 'player', playerId, position },
      1500,
      player.isHuman // Only reveal for human players
    );

    // Animate old card from player position to discard pile
    // Use a slight delay to make it clearer
    setTimeout(() => {
      this.animationStore.startDiscardAnimation(
        oldCard,
        { type: 'player', playerId, position },
        { type: 'discard' },
        1500
      );
    }, 200);
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

    // Play action animation from drawn card area
    this.animationStore.startPlayActionAnimation(card, { type: 'drawn' }, 2000);

    // After play action animation, move to discard pile
    setTimeout(() => {
      this.animationStore.startDiscardAnimation(
        card,
        { type: 'drawn' },
        { type: 'discard' },
        1500
      );
    }, 2000);
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
   * Reset all animations (useful for new game)
   */
  reset(): void {
    this.animationStore.reset();
  }
}
