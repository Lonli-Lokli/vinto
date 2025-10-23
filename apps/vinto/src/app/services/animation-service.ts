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
import {
  ConfirmPeekAction,
  DeclareKingActionAction,
  DiscardCardAction,
  DrawCardAction,
  ExecuteJackSwapAction,
  ExecuteQueenSwapAction,
  GameAction,
  GameState,
  ParticipateInTossInAction,
  SelectActionTargetAction,
  SkipJackSwapAction,
  SkipQueenSwapAction,
  SwapCardAction,
  UseCardActionAction,
} from '@vinto/shapes';
import {
  GameClient,
  registerStateUpdateCallback,
  unregisterStateUpdateCallback,
} from '@vinto/local-client';

@injectable()
export class AnimationService {
  private _stateUpdateCallback?: (
    oldState: GameState,
    newState: GameState,
    action: GameAction
  ) => void;
  private _unregisterCallback?: () => void;
  private animationStore: CardAnimationStore;
  private gameClient?: GameClient;

  constructor(@inject(CardAnimationStore) animationStore: CardAnimationStore) {
    this.animationStore = animationStore;
    // Register state update callback
    this._stateUpdateCallback = this.handleStateUpdate.bind(this);
    registerStateUpdateCallback(this._stateUpdateCallback);
    this._unregisterCallback = () => {
      if (this._stateUpdateCallback) {
        unregisterStateUpdateCallback(this._stateUpdateCallback);
      }
    };

    // Register callback to sync visual state after animations complete
    this.animationStore.onAllAnimationsComplete(() => {
      this.gameClient?.syncVisualState();
    });
  }

  /**
   * Register the GameClient instance
   * Called from GameClientProvider to enable visual state syncing
   */
  registerGameClient(gameClient: GameClient): void {
    this.gameClient = gameClient;
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
        // Check if this was a failed toss-in attempt
        this.handleFailedTossInAttempt(oldState, newState, action);
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

      case 'EXECUTE_QUEEN_SWAP':
        this.handleExecuteQueenSwap(oldState, newState, action);
        break;

      case 'SKIP_QUEEN_SWAP':
        this.handleSkipQueenSwap(oldState, newState, action);
        break;

      case 'EXECUTE_JACK_SWAP':
        this.handleExecuteJackSwap(oldState, newState, action);
        break;

      case 'SKIP_JACK_SWAP':
        this.handleSkipJackSwap(oldState, newState, action);
        break;

      default:
        // No animation needed for this action

        break;
    }

    if (!this.animationStore.hasBlockingAnimations) {
      this.gameClient?.syncVisualState();
    }
  }

  /**
   * Handle DRAW_CARD action animation
   * - For human: Deck -> Drawn card area (pending card)
   * - For bot: Deck -> Drawn card area with full rotation
   */
  private handleDrawCard(
    _oldState: GameState,
    newState: GameState,
    action: DrawCardAction
  ): void {
    const playerId = action.payload.playerId;
    const player = newState.players.find((p) => p.id === playerId);

    if (!player) return;

    const drawnCard = newState.pendingAction?.card;
    if (!drawnCard) return;

    // Animate draw for both human and bot players
    // Card goes from deck to drawn/pending area
    this.animationStore.startDrawAnimation(
      drawnCard,
      { type: 'draw' },
      { type: 'drawn' },
      1500,
      true, // revealed for human, hidden for bot (will be handled by card component)
      !player.isHuman // fullRotation for bots to show card flip
    );
  }

  /**
   * Handle DISCARD_CARD action animation
   * - Drawn card area -> Discard pile
   */
  private handleDiscardCard(
    _oldState: GameState,
    newState: GameState,
    _action: DiscardCardAction
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
            revealed: false,
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
          duration: 3_000,
          revealed: player.isHuman,
        },
      ];

      if (oldCard) {
        steps.push({
          type: 'discard',
          card: oldCard,
          from: { type: 'player', playerId, position },
          to: { type: 'discard' },
          duration: 3_000,
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
   */
  private handleUseCardAction(
    _oldState: GameState,
    _newState: GameState,
    action: UseCardActionAction
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
    ]);
  }

  /**
   * Handle PARTICIPATE_IN_TOSS_IN action animation
   * - Player position -> Discard pile (only for valid toss-ins)
   */
  private handleTossIn(
    oldState: GameState,
    newState: GameState,
    action: ParticipateInTossInAction
  ): void {
    const playerId = action.payload.playerId;
    const positions = action.payload.positions;

    // Get the player from old state to access the cards before they were removed
    const oldPlayer = oldState.players.find((p) => p.id === playerId);
    if (!oldPlayer) return;

    // Check if any positions were failed attempts
    const failedAttempts = newState.activeTossIn?.failedAttempts || [];

    // Animate each tossed-in card separately
    for (const position of positions) {
      const wasFailedAttempt = failedAttempts.some(
        (attempt) =>
          attempt.playerId === playerId && attempt.position === position
      );

      if (wasFailedAttempt) {
        // Failed toss-in - don't animate card to discard pile
        // Card stays in hand, penalty animation handled separately
        continue;
      }

      // Valid toss-in - get the card from old state at its original position
      const tossedCard = oldPlayer.cards[position];
      if (!tossedCard) continue;

      // Animate from player position to Drawn pile or discard pile
      this.animationStore.startDiscardAnimation(
        tossedCard,
        { type: 'player', playerId, position },
        tossedCard.actionText ? { type: 'drawn' } : { type: 'discard' },
        1_500
      );
    }
  }

  /**
   * Handle CONFIRM_PEEK action animation
   * Card used for peek action (7, 8, 9, 10) animates to discard pile
   */
  private handleConfirmPeek(
    _oldState: GameState,
    newState: GameState,
    _action: ConfirmPeekAction
  ): void {
    const peekCard = newState.discardPile.peekTop();

    if (!peekCard) return;

    // Check if this card came from a swap declaration (has swapPosition)

    this.animationStore.startDiscardAnimation(
      peekCard,
      { type: 'drawn' },
      { type: 'discard' },
      1500
    );
  }

  /**
   * Handle DECLARE_KING_ACTION animation
   * Two scenarios:
   * 1. Correct declaration: King → discard, selected card → discard
   * 2. Incorrect declaration: King → discard, selected card stays (revealed briefly), penalty card drawn
   */
  private handleDeclareKingAction(
    oldState: GameState,
    newState: GameState,
    action: DeclareKingActionAction
  ): void {
    const playerId = action.payload.playerId;
    const declaredRank = action.payload.declaredRank;
    const selectedCardInfo = oldState.pendingAction?.targets?.[0];

    if (!selectedCardInfo) {
      console.warn('[AnimationService] No selected card for King action');
      return;
    }

    const {
      playerId: targetPlayerId,
      position,
      card: selectedCard,
    } = selectedCardInfo;
    const actualRank = selectedCard?.rank;
    const isCorrect = actualRank === declaredRank;

    // Get players
    const player = newState.players.find((p) => p.id === playerId);
    const targetPlayer = newState.players.find((p) => p.id === targetPlayerId);
    const oldTargetPlayer = oldState.players.find(
      (p) => p.id === targetPlayerId
    );

    if (!player || !targetPlayer || !oldTargetPlayer) return;

    // Check if this card came from a swap declaration (has swapPosition)
    const swapPosition = oldState.pendingAction?.swapPosition;

    if (isCorrect) {
      // CORRECT DECLARATION
      // Sequential animation: King → discard, then selected card → discard
      const kingCard = newState.discardPile.peekTop();
      // Second card is in pending action
      const selectedCardOnDiscard = newState.pendingAction?.card;

      const steps: AnimationStep[] = [];

      // Step 1: King card to discard
      if (kingCard && kingCard.rank === 'K') {
        steps.push({
          type: 'discard',
          card: kingCard,
          from:
            swapPosition !== undefined
              ? { type: 'player', playerId, position: swapPosition }
              : { type: 'drawn' },
          to: { type: 'discard' },
          duration: 3_000,
          revealed: true,
        });
      }

      // Step 2: Selected card to discard
      if (selectedCardOnDiscard) {
        steps.push({
          type: 'swap',
          card: selectedCardOnDiscard,
          from: { type: 'player', playerId: targetPlayerId, position },
          to: { type: 'drawn' },
          duration: 1_500,
          revealed: true,
        });
      }

      this.animationStore.startAnimationSequence('sequential', steps);
      console.log(
        '[AnimationService] Correct King declaration - King and selected card to discard'
      );
    } else {
      // INCORRECT DECLARATION
      // Sequential animation:
      // 1. King → discard
      // 2. Selected card revealed briefly in hand (handled by state)
      // 3. Penalty card: draw → hand
      const kingCard = newState.discardPile.peekTop();
      const penaltyCardPosition = player.cards.length - 1;
      const penaltyCard = player.cards[penaltyCardPosition];

      const steps: AnimationStep[] = [];

      // Step 1: King card to discard
      if (kingCard && kingCard.rank === 'K') {
        steps.push({
          type: 'discard',
          card: kingCard,
          from:
            swapPosition !== undefined
              ? { type: 'player', playerId, position: swapPosition }
              : { type: 'drawn' },
          to: { type: 'discard' },
          duration: 2_000,
          revealed: true,
        });
      }

      // Step 2: Penalty card from draw pile to hand
      // Note: The penalty card goes to the player who made the King declaration (playerId),
      // not necessarily the target player. The card should NOT be revealed.
      if (
        penaltyCard &&
        player.cards.length >
          oldState.players.find((p) => p.id === playerId)!.cards.length
      ) {
        // Use a timeout to ensure DOM is ready before starting animation
        steps.push({
          type: 'draw',
          card: penaltyCard,
          from: { type: 'draw' },
          to: { type: 'player', playerId, position: penaltyCardPosition },
          duration: 2_000,
          revealed: false, // Penalty cards are never revealed
          fullRotation: false,
        });
      }

      this.animationStore.startAnimationSequence('sequential', steps);
      console.log(
        '[AnimationService] Incorrect King declaration - King to discard, penalty card drawn'
      );
    }
  }

  /**
   * Handle SELECT_ACTION_TARGET animation
   * - For peek actions (7, 8, 9, 10): Highlight the peeked card
   * - For J (Jack) and A (Ace) actions that complete: Move card to discard
   */
  private handleSelectActionTarget(
    oldState: GameState,
    newState: GameState,
    action: SelectActionTargetAction
  ): void {
    const { playerId, targetPlayerId } = action.payload;
    const actionCard = oldState.pendingAction?.card;

    const position =
      action.payload.rank === 'Any'
        ? action.payload.position
        : newState.players.find((p) => p.id === targetPlayerId)!.cards.length -
          1;

    if (!actionCard) return;

    // Handle peek actions (7, 8, 9, 10, Q) - no highlight animation
    // The card will be revealed directly via temporarilyVisibleCards or opponentKnowledge
    // Spectators don't need to see "selection hints" - they just see the revealed card
    if (
      actionCard.rank === '7' ||
      actionCard.rank === '8' ||
      actionCard.rank === '9' ||
      actionCard.rank === '10' ||
      actionCard.rank === 'Q'
    ) {
      // Find the acting player (the one using the peek action)
      const actingPlayer = newState.players.find((p) => p.id === playerId);

      // Only start highlight animation if BOT is peeking
      // Human peeks should just reveal the card (handled by UIStore.temporarilyVisibleCards)
      if (actingPlayer && !actingPlayer.isHuman) {
        // Get the target player and card
        const targetPlayer = newState.players.find(
          (p) => p.id === targetPlayerId
        );
        if (!targetPlayer || position >= targetPlayer.cards.length) return;

        const peekedCard = targetPlayer.cards[position];
        if (!peekedCard) return;

        // Start highlight animation on the peeked card (bot action)
        this.animationStore.startHighlightAnimation(
          peekedCard,
          { type: 'player', playerId: targetPlayerId, position },
          2000 // 2 second highlight
        );

        console.log(
          `[AnimationService] Bot peek action - highlighting ${peekedCard.rank} at ${targetPlayerId} position ${position}`
        );
      } else {
        console.log(
          `[AnimationService] Human peek action - skipping highlight animation (card will reveal via temporarilyVisibleCards)`
        );
      }
      return;
    }

    // Only animate if the action is complete (card went to discard)
    const wasCompleted = oldState.pendingAction && !newState.pendingAction;

    if (!wasCompleted) return;

    const discardCard = newState.discardPile.peekTop();
    if (!discardCard) return;

    // Only handle J and A cards (they complete on SELECT_ACTION_TARGET)
    if (discardCard.rank !== 'J' && discardCard.rank !== 'A') return;

    // Check if this card came from a swap declaration (has swapPosition)
    const swapPosition = oldState.pendingAction?.swapPosition;

    // For Ace action, also animate the penalty card being drawn to target player's hand
    if (actionCard.rank === 'A') {
      const targetPlayerId = action.payload.targetPlayerId;
      const targetPlayer = newState.players.find(
        (p) => p.id === targetPlayerId
      );
      const oldTargetPlayer = oldState.players.find(
        (p) => p.id === targetPlayerId
      );

      if (targetPlayer && oldTargetPlayer) {
        // Check if target player received a penalty card
        if (targetPlayer.cards.length > oldTargetPlayer.cards.length) {
          const penaltyCardPosition = targetPlayer.cards.length - 1;
          const penaltyCard = targetPlayer.cards[penaltyCardPosition];

          if (penaltyCard) {
            const steps: AnimationStep[] = [];

            // Step 1: Ace card to discard
            steps.push({
              type: 'discard',
              card: actionCard,
              from: { type: 'drawn' },
              to: { type: 'discard' },
              duration: 1500,
              revealed: true,
            });

            // Step 2: Penalty card from draw pile to target player's hand
            steps.push({
              type: 'draw',
              card: penaltyCard,
              from: { type: 'draw' },
              to: {
                type: 'player',
                playerId: targetPlayerId,
                position: penaltyCardPosition,
              },
              duration: 1500,
              revealed: false, // Penalty cards are never revealed
              fullRotation: false,
            });

            this.animationStore.startAnimationSequence('parallel', steps);
            console.log(
              '[AnimationService] Ace action complete - Ace to discard, penalty card to target player'
            );
            return;
          }
        }
      }
    }

    // For Jack or Ace without penalty card, just animate action card to discard
    if (swapPosition !== undefined) {
      // Card came from hand position after correct declaration
      // Animate from hand position to discard pile
      this.animationStore.startDiscardAnimation(
        actionCard,
        { type: 'player', playerId, position: swapPosition },
        { type: 'discard' },
        1_500
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
   * Handle EXECUTE_QUEEN_SWAP animation
   * Swaps two cards between players with animation
   */
  private handleExecuteQueenSwap(
    oldState: GameState,
    newState: GameState,
    _action: ExecuteQueenSwapAction
  ): void {
    // Get the two targets from old state (before swap)
    const targets = oldState.pendingAction?.targets;
    if (!targets || targets.length !== 2) {
      console.warn('[AnimationService] No targets for Queen swap');
      return;
    }

    const [target1, target2] = targets;

    // Get the cards from the NEW state (after swap)
    const player1 = newState.players.find((p) => p.id === target1.playerId);
    const player2 = newState.players.find((p) => p.id === target2.playerId);

    if (!player1 || !player2) {
      console.warn('[AnimationService] Players not found for Queen swap');
      return;
    }

    // The cards at these positions are already swapped in newState
    // So card1 at target1.position is actually what WAS at target2.position (before swap)
    // And card2 at target2.position is actually what WAS at target1.position (before swap)
    const card1AfterSwap = player1.cards[target1.position];
    const card2AfterSwap = player2.cards[target2.position];

    // Get player positions for rotation
    const player1Position = this.getPlayerPosition(player1.id, newState);
    const player2Position = this.getPlayerPosition(player2.id, newState);

    // Determine if cards should be revealed during animation
    // - Reveal if human player is involved
    // - Always reveal during Queen swap since player peeked at both cards
    const humanPlayer = newState.players.find((p) => p.isHuman);
    const revealCard1 =
      humanPlayer?.id === target1.playerId ||
      humanPlayer?.id === target2.playerId;
    const revealCard2 = revealCard1;

    // Animate both swaps in parallel
    this.animationStore.startAnimationSequence('parallel', [
      {
        type: 'swap',
        card: card1AfterSwap,
        from: {
          type: 'player',
          playerId: target2.playerId,
          position: target2.position,
        },
        to: {
          type: 'player',
          playerId: target1.playerId,
          position: target1.position,
        },
        duration: 1500,
        revealed: revealCard1,
        targetPlayerPosition: player1Position,
      },
      {
        type: 'swap',
        card: card2AfterSwap,
        from: {
          type: 'player',
          playerId: target1.playerId,
          position: target1.position,
        },
        to: {
          type: 'player',
          playerId: target2.playerId,
          position: target2.position,
        },
        duration: 1500,
        revealed: revealCard2,
        targetPlayerPosition: player2Position,
      },
    ]);

    console.log('[AnimationService] Queen swap animation started');
  }

  /**
   * Handle SKIP_QUEEN_SWAP animation
   * Just moves the Queen card to discard pile
   */
  private handleSkipQueenSwap(
    oldState: GameState,
    newState: GameState,
    action: SkipQueenSwapAction
  ): void {
    // The Queen card should now be on top of discard pile
    const queenCard = newState.discardPile.peekTop();

    if (!queenCard || queenCard.rank !== 'Q') {
      console.warn('[AnimationService] No Queen card found on discard pile');
      return;
    }

    // Check if the Queen came from a swap declaration
    const swapPosition = oldState.pendingAction?.swapPosition;
    const playerId = action.payload.playerId;

    if (swapPosition !== undefined) {
      // Card came from hand position after correct declaration
      this.animationStore.startDiscardAnimation(
        queenCard,
        { type: 'player', playerId, position: swapPosition },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] Queen swap skipped - declared card to discard'
      );
    } else {
      // Card came from drawn position (normal flow)
      this.animationStore.startDiscardAnimation(
        queenCard,
        { type: 'drawn' },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] Queen swap skipped - drawn card to discard'
      );
    }
  }

  /**
   * Handle EXECUTE_JACK_SWAP animation
   * Swaps two cards between players with animation
   */
  private handleExecuteJackSwap(
    oldState: GameState,
    newState: GameState,
    _action: ExecuteJackSwapAction
  ): void {
    // Get the two targets from old state (before swap)
    const targets = oldState.pendingAction?.targets;
    if (!targets || targets.length !== 2) {
      console.warn('[AnimationService] No targets for Jack swap');
      return;
    }

    const [target1, target2] = targets;

    // Get the cards from the NEW state (after swap)
    const player1 = newState.players.find((p) => p.id === target1.playerId);
    const player2 = newState.players.find((p) => p.id === target2.playerId);

    if (!player1 || !player2) {
      console.warn('[AnimationService] Players not found for Jack swap');
      return;
    }

    // The cards at these positions are already swapped in newState
    // So card1 at target1.position is actually what WAS at target2.position (before swap)
    // And card2 at target2.position is actually what WAS at target1.position (before swap)
    const card1AfterSwap = player1.cards[target1.position];
    const card2AfterSwap = player2.cards[target2.position];

    // Get player positions for rotation
    const player1Position = this.getPlayerPosition(player1.id, newState);
    const player2Position = this.getPlayerPosition(player2.id, newState);

    // Animate both swaps in parallel
    this.animationStore.startAnimationSequence('parallel', [
      {
        type: 'swap',
        card: card1AfterSwap,
        from: {
          type: 'player',
          playerId: target2.playerId,
          position: target2.position,
        },
        to: {
          type: 'player',
          playerId: target1.playerId,
          position: target1.position,
        },
        duration: 1500,
        revealed: false,
        targetPlayerPosition: player1Position,
      },
      {
        type: 'swap',
        card: card2AfterSwap,
        from: {
          type: 'player',
          playerId: target1.playerId,
          position: target1.position,
        },
        to: {
          type: 'player',
          playerId: target2.playerId,
          position: target2.position,
        },
        duration: 1500,
        revealed: false,
        targetPlayerPosition: player2Position,
      },
    ]);

    console.log('[AnimationService] Jack swap animation started');
  }

  /**
   * Handle SKIP_JACK_SWAP animation
   * Just moves the Jack card to discard pile
   */
  private handleSkipJackSwap(
    oldState: GameState,
    newState: GameState,
    action: SkipJackSwapAction
  ): void {
    // The Jack card should now be on top of discard pile
    const jackCard = newState.discardPile.peekTop();

    if (!jackCard || jackCard.rank !== 'J') {
      console.warn('[AnimationService] No Jack card found on discard pile');
      return;
    }

    // Check if the Jack came from a swap declaration
    const swapPosition = oldState.pendingAction?.swapPosition;
    const playerId = action.payload.playerId;

    if (swapPosition !== undefined) {
      // Card came from hand position after correct declaration
      this.animationStore.startDiscardAnimation(
        jackCard,
        { type: 'player', playerId, position: swapPosition },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] Jack swap skipped - declared card to discard'
      );
    } else {
      // Card came from drawn position (normal flow)
      this.animationStore.startDiscardAnimation(
        jackCard,
        { type: 'drawn' },
        { type: 'discard' },
        1500
      );
      console.log(
        '[AnimationService] Jack swap skipped - drawn card to discard'
      );
    }
  }

  /**
   * Handle failed toss-in attempt
   * - Card stays in hand (no animation needed - it never left)
   * - Penalty card animates from draw pile to hand
   * - Visual feedback is handled by HeadlessService (error indicator on failed card)
   */
  private handleFailedTossInAttempt(
    oldState: GameState,
    newState: GameState,
    action: ParticipateInTossInAction
  ): void {
    const { playerId, positions } = action.payload;

    // Get the player
    const player = newState.players.find((p) => p.id === playerId);
    const oldPlayer = oldState.players.find((p) => p.id === playerId);

    if (!player || !oldPlayer) {
      console.warn(
        '[AnimationService] Player not found for failed toss-in animation'
      );
      return;
    }

    // Check if this was a failed attempt
    const failedAttempts = newState.activeTossIn?.failedAttempts || [];

    // Check each position for failed attempts
    for (const position of positions) {
      const wasFailedAttempt = failedAttempts.some(
        (attempt) =>
          attempt.playerId === playerId && attempt.position === position
      );

      if (wasFailedAttempt) {
        console.log(
          '[AnimationService] Failed toss-in detected - checking for penalty card:',
          {
            playerId,
            position,
          }
        );

        console.log('[AnimationService] Card count comparison:', {
          oldCount: oldPlayer.cards.length,
          newCount: player.cards.length,
        });

        // Check if a penalty card was added (new state has more cards than old state)
        if (player.cards.length > oldPlayer.cards.length) {
          const penaltyCardPosition = player.cards.length - 1;
          const penaltyCard = player.cards[penaltyCardPosition];

        if (penaltyCard) {
          // Create a sequence with a dummy first step to give React time to render
          // This matches the pattern used in swap card incorrect declaration
          const steps: AnimationStep[] = [
            // Dummy step: animate from draw to draw (no movement, just a delay)
            {
              type: 'draw',
              card: penaltyCard,
              from: { type: 'draw' },
              to: { type: 'drawn' },
              duration: 1, // Very short duration, just to trigger React render
              revealed: false,
            },
            // Actual animation: draw pile to hand
            {
              type: 'draw',
              card: penaltyCard,
              from: { type: 'draw' },
              to: { type: 'player', playerId, position: penaltyCardPosition },
              duration: 1500,
              revealed: false, // Never reveal penalty cards
              fullRotation: false,
            },
          ];

          this.animationStore.startAnimationSequence('sequential', steps);
          console.log(
            '[AnimationService] Failed toss-in penalty card animation started:',
            {
              card: penaltyCard.rank,
              toPosition: penaltyCardPosition,
            }
          );
        } else {
          console.warn(
            '[AnimationService] Penalty card not found at position',
            penaltyCardPosition
          );
        }
      } else {
        console.warn(
          '[AnimationService] No penalty card added - card counts are equal or decreased'
        );
      }
      }
    }
  }

  /**
   * Helper to get player position (top, bottom, left, right)
   */
  private getPlayerPosition(playerId: string, state: GameState): string {
    const playerIndex = state.players.findIndex((p) => p.id === playerId);
    const humanPlayerIndex = state.players.findIndex((p) => p.isHuman);

    if (playerIndex === humanPlayerIndex) return 'bottom';

    const playerCount = state.players.length;
    const relativeIndex =
      (playerIndex - humanPlayerIndex + playerCount) % playerCount;

    if (playerCount === 2) return 'top';
    if (playerCount === 3) {
      return relativeIndex === 1 ? 'left' : 'right';
    }
    // 4 players
    if (relativeIndex === 1) return 'left';
    if (relativeIndex === 2) return 'top';
    return 'right';
  }

  /**
   * Reset all animations (useful for new game)
   */
  reset(): void {
    this.animationStore.reset();
  }

  public dispose() {
    // Unregister callback
    if (this._unregisterCallback) {
      this._unregisterCallback();
    }
    console.log('Disposed AnimationService');
  }
}

/**
 * Helper function to register GameClient with AnimationService
 * Called from app initialization to wire up visual state syncing
 */
export function registerGameClientWithAnimations(
  animationService: AnimationService,
  gameClient: any
): void {
  animationService.registerGameClient(gameClient);
}
