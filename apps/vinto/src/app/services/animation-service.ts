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
  getCardAction,
  ParticipateInTossInAction,
  PlayDiscardAction,
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
import { logger } from '@sentry/nextjs';
import { canSeePlayerCard } from '../components/logic/player-area-logic';
import { UIStore } from '../stores';

@injectable()
export class AnimationService {
  private _stateUpdateCallback?: (
    oldState: GameState,
    newState: GameState,
    action: GameAction
  ) => void;
  private _unregisterCallback?: () => void;
  private animationStore: CardAnimationStore;
  private uiStore: UIStore;
  private gameClient?: GameClient;

  constructor(@inject(CardAnimationStore) animationStore: CardAnimationStore, @inject(UIStore) uiStore: UIStore) {
    this.animationStore = animationStore;
    this.uiStore = uiStore;
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

      case 'PLAY_DISCARD':
        this.handlePlayDiscardCardAction(oldState, newState, action);
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
      drawnCard.rank,
      { type: 'draw' },
      { type: 'drawn' },
      1500,
      true, // revealed for human, hidden for bot (will be handled by card component)
      false
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
      discardedCard.rank,
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
            rank: newCard.rank,
            from: { type: 'drawn' },
            to: { type: 'player', playerId, position },
            duration: 1500,
            revealed: false,
          },
          {
            type: 'swap',
            rank: declaredCard.rank,
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
          rank: newCard.rank,
          from: { type: 'drawn' },
          to: { type: 'player', playerId, position },
          duration: 1500,
          revealed: player.isHuman,
        },
      ];

      if (oldCard) {
        steps.push({
          type: 'discard',
          rank: oldCard.rank,
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
          rank: penaltyCard.rank,
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
          rank: newCard.rank,
          from: { type: 'drawn' },
          to: { type: 'player', playerId, position },
          duration: 3_000,
          revealed: player.isHuman,
        },
      ];

      if (oldCard) {
        steps.push({
          type: 'discard',
          rank: oldCard.rank,
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
   */
  private handleUseCardAction(
    oldState: GameState,
    _newState: GameState,
    _action: UseCardActionAction
  ): void {
    const rank = oldState.pendingAction?.card?.rank;

    if (!rank) {
      logger.warn(
        '[AnimationService] No card rank found in pending action for USE_CARD_ACTION'
      );
      return;
    }
    // parallel animation: play-action effect, then move to discard
    // important: do not use sequential here as it will render old controls once
    this.animationStore.startAnimationSequence('parallel', [
      {
        type: 'play-action',
        rank: rank,
        from: { type: 'drawn' },
        duration: 2000,
      },
    ]);
  }

  /**
   * Handle USE_CARD_ACTION action animation
   * - Shows card with special play-action effect
   */
  private handlePlayDiscardCardAction(
    _oldState: GameState,
    newState: GameState,
    _action: PlayDiscardAction
  ): void {
    const card = newState.pendingAction?.card;
    if (!card) return;

    // Sequential animation: play-action effect, then move to discard
    this.animationStore.startAnimationSequence('parallel', [
      {
        type: 'play-action',
        rank: card.rank,
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
    const player = newState.players.find((p) => p.id === playerId);
    if (!oldPlayer || !player) return;

    // Check if any positions were failed attempts
    const failedAttempts = newState.activeTossIn?.failedAttempts || [];

    const wasFailedAttempt = failedAttempts.some(
      (attempt) => attempt.playerId === playerId
    );

    if (wasFailedAttempt) {
      // Failed toss-in - don't animate card to discard pile
      // Card stays in hand, penalty animation handled separately

      if (player.cards.length > oldPlayer.cards.length) {
        const penaltyCardPosition = player.cards.length - 1;
        const penaltyCard = player.cards[penaltyCardPosition];

        if (penaltyCard) {
          // Create a sequence with a dummy first step to give React time to render
          // This matches the pattern used in swap card incorrect declaration
          const steps: AnimationStep[] = [
            {
              type: 'draw',
              rank: penaltyCard.rank,
              from: { type: 'draw' },
              to: {
                type: 'player',
                playerId,
                position: penaltyCardPosition,
              },
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

      return;
    }
    // Animate each tossed-in card separately
    for (const position of positions) {
      // Valid toss-in - get the card from old state at its original position
      const tossedCard = oldPlayer.cards[position];
      if (!tossedCard) continue;

      // Animate from player position to Drawn pile or discard pile
      this.animationStore.startDiscardAnimation(
        tossedCard.rank,
        { type: 'player', playerId, position },
        { type: 'discard' },
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
      peekCard.rank,
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
    if (isCorrect) {
      // CORRECT DECLARATION
      // Sequential animation: King → discard, then selected card → discard
      // Second card is in pending action
      const steps: AnimationStep[] = [];

      // Step 1: King card to discard

      steps.push({
        type: 'discard',
        rank: 'K',
        from: { type: 'drawn' },
        to: { type: 'discard' },
        duration: 3_000,
        revealed: true,
      });

      steps.push({
        type: 'swap',
        rank: declaredRank,
        from: { type: 'player', playerId: targetPlayerId, position },
        to:
          getCardAction(declaredRank) !== undefined
            ? { type: 'drawn' }
            : { type: 'discard' },
        duration: 1_500,
        revealed: true,
      });

      this.animationStore.startAnimationSequence('parallel', steps);
      console.log(
        '[AnimationService] Correct King declaration - King and selected card to discard'
      );
    } else {
      // INCORRECT DECLARATION
      // Sequential animation:
      // 1. King → discard
      // 2. Selected card revealed briefly in hand (handled by state)
      // 3. Penalty card: draw → hand
      const penaltyCardPosition = player.cards.length - 1;
      const penaltyCard = player.cards[penaltyCardPosition];

      const steps: AnimationStep[] = [];

      // Step 1: King card to discard
      steps.push({
        type: 'discard',
        rank: 'K',
        from: { type: 'drawn' },
        to: { type: 'discard' },
        duration: 2_000,
        revealed: true,
      });

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
          rank: penaltyCard.rank,
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

    // Handle peek actions (7, 8, 9, 10, Q, J, K)
    // Card shifts away from player toward draw pile with glow - all players can see it
    if (
      actionCard.rank === '7' ||
      actionCard.rank === '8' ||
      actionCard.rank === '9' ||
      actionCard.rank === '10' ||
      actionCard.rank === 'Q' ||
      actionCard.rank === 'J' ||
      actionCard.rank === 'K'
    ) {
      const targetPlayer = newState.players.find(
        (p) => p.id === targetPlayerId
      );
      if (!targetPlayer || position >= targetPlayer.cards.length) return;

      const peekedCard = targetPlayer.cards[position];
      if (!peekedCard) return;

      // Start highlight animation to draw attention to the peeked card
      // The border is shown via isPeeked prop (added in headless-service), animation adds extra visual flair
      this.animationStore.startHighlightAnimation(
        peekedCard.rank,
        { type: 'player', playerId: targetPlayerId, position },
        canSeePlayerCard({
          cardIndex: position,
          coalitionLeaderId: newState.coalitionLeaderId,
          targetPlayer: targetPlayer,
          temporarilyVisibleCards: this.uiStore.getTemporarilyVisibleCards(targetPlayerId),
          gamePhase: newState.phase,
          observingPlayer: newState.players.find((p) => p.id === playerId)!,
        }),
        this.getPlayerPosition(targetPlayerId, newState),
        2000 // 2 second highlight animation
      );

      logger.info(
        `[AnimationService] Peek action (${actionCard.rank}) - highlighting card at ${targetPlayerId} position ${position}`
      );
      return;
    }

    // Only animate if the action is complete (card went to discard)
    const wasCompleted = oldState.pendingAction && !newState.pendingAction;

    if (!wasCompleted) return;

    const discardCard = newState.discardPile.peekTop();
    if (!discardCard) return;

    // Only handle A cards (they complete on SELECT_ACTION_TARGET)
    if (discardCard.rank !== 'A') return;

    // Check if this card came from a swap declaration (has swapPosition)
    const swapPosition = oldState.pendingAction?.swapPosition;

    // For Ace action, show penalty indicator and animate the penalty card being drawn to target player's hand
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

            // Step 1: Show penalty indicator (pulsing red border) on target player's card area
            steps.push({
              type: 'penalty-indicator',
              targetPlayerId,
              duration: 1500,
            });

            // Step 2: Ace card to discard (parallel with indicator)
            steps.push({
              type: 'discard',
              rank: actionCard.rank,
              from: { type: 'drawn' },
              to: { type: 'discard' },
              duration: 1500,
              revealed: true,
            });

            // Start indicator and discard in parallel, then penalty card
            this.animationStore.startAnimationSequence('parallel', steps);

            // After penalty indicator completes, draw the penalty card
            setTimeout(() => {
              this.animationStore.startDrawAnimation(
                penaltyCard.rank,
                { type: 'draw' },
                {
                  type: 'player',
                  playerId: targetPlayerId,
                  position: penaltyCardPosition,
                },
                1500,
                false, // Penalty cards are never revealed
                false
              );
            }, 1500);

            console.log(
              '[AnimationService] Ace action complete - penalty indicator, Ace to discard, then penalty card to target player'
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
        actionCard.rank,
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
        actionCard.rank,
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
    // - Only reveal if HUMAN is the acting player (the one who used Queen)
    // - If bot used Queen, cards should NOT be revealed to human
    const humanPlayer = newState.players.find((p) => p.isHuman);
    const actingPlayerId = oldState.pendingAction?.playerId;
    const isHumanActing = humanPlayer?.id === actingPlayerId;

    const revealCard1 = isHumanActing;
    const revealCard2 = isHumanActing;

    // Animate both swaps in parallel
    this.animationStore.startAnimationSequence('parallel', [
      {
        type: 'swap',
        rank: card1AfterSwap.rank,
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
        rank: card2AfterSwap.rank,
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
      {
        type: 'discard',
        rank: oldState.pendingAction!.card.rank,
        from: { type: 'drawn' },
        to: { type: 'discard' },
        duration: 1500,
        revealed: true,
      },
    ]);

    console.log('[AnimationService] Queen swap animation started');
  }

  /**
   * Handle SKIP_QUEEN_SWAP animation
   * Shows shake animation on selected cards (if any), then moves Queen to discard
   */
  private handleSkipQueenSwap(
    oldState: GameState,
    _newState: GameState,
    _action: SkipQueenSwapAction
  ): void {
    // Get the selected targets (if any) to show they weren't swapped
    const targets = oldState.pendingAction?.targets;

    const steps: AnimationStep[] = [];

    // If cards were selected, show a "shake" animation on them to indicate cancellation
    if (targets && targets.length === 2) {
      targets.forEach((target) => {
        if (target.card) {
          steps.push({
            type: 'shake',
            rank: target.card.rank,
            target: {
              type: 'player',
              playerId: target.playerId,
              position: target.position,
            },
            duration: 1_500,
          });
        }
      });
    }

    // Then discard the Queen
    steps.push({
      type: 'discard',
      rank: 'Q',
      from: { type: 'drawn' },
      to: { type: 'discard' },
      duration: 1_500,
    });

    this.animationStore.startAnimationSequence('parallel', steps);
    console.log(
      '[AnimationService] Queen swap skipped - showing shake animation on selected cards'
    );
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
        rank: card1AfterSwap.rank,
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
        rank: card2AfterSwap.rank,
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
      {
        type: 'discard',
        rank: 'J',
        from: { type: 'drawn' },
        to: { type: 'discard' },
        duration: 1500,
        revealed: true,
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
    _newState: GameState,
    _action: SkipJackSwapAction
  ): void {
    // Get the selected targets (if any) to show they weren't swapped
    const targets = oldState.pendingAction?.targets;

    const steps: AnimationStep[] = [];

    // If cards were selected, show a "shake" animation on them to indicate cancellation
    if (targets && targets.length === 2) {
      targets.forEach((target) => {
        if (target.card) {
          steps.push({
            type: 'shake',
            rank: target.card.rank,
            target: {
              type: 'player',
              playerId: target.playerId,
              position: target.position,
            },
            duration: 1_500,
          });
        }
      });
    }

    // Then discard the Queen
    steps.push({
      type: 'discard',
      rank: 'J',
      from: { type: 'drawn' },
      to: { type: 'discard' },
      duration: 1_500,
    });

    this.animationStore.startAnimationSequence('parallel', steps);
    console.log(
      '[AnimationService] Jack swap skipped - showing shake animation on selected cards'
    );
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
