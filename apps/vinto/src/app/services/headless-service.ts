import { inject, injectable } from 'tsyringe';
import { UIStore } from '../stores';
import {
  GameAction,
  GameState,
  SwapCardAction,
  DeclareKingActionAction,
  SelectActionTargetAction,
} from '@vinto/shapes';
import {
  registerStateErrorCallback,
  registerStateUpdateCallback,
  unregisterStateErrorCallback,
  unregisterStateUpdateCallback,
} from '@vinto/local-client';
import { GameToastService } from './toast-service';

@injectable()
export class HeadlessService {
  private _stateUpdateCallback?: (
    oldState: GameState,
    newState: GameState,
    action: GameAction
  ) => void;
  private _unregisterUpdateCallback?: () => void;

  private _stateErrorCallback?: (reason: string) => void;
  private _unregisterCallback?: () => void;
  private uiStore: UIStore;

  constructor(@inject(UIStore) uiStore: UIStore) {
    this.uiStore = uiStore;
    // Register state update callback
    this._stateUpdateCallback = this.handleStateUpdate.bind(this);
    this._stateErrorCallback = this.handleStateError.bind(this);
    registerStateUpdateCallback(this._stateUpdateCallback);
    registerStateErrorCallback(this._stateErrorCallback);
    this._unregisterCallback = () => {
      if (this._stateUpdateCallback) {
        unregisterStateUpdateCallback(this._stateUpdateCallback);
      }
      if (this._stateErrorCallback) {
        unregisterStateErrorCallback(this._stateErrorCallback);
      }
    };
  }

  handleStateError(reason: string): void {
    GameToastService.error(reason);
  }

  /**
   * Handle game state updates and do appropriate actions
   * This is called from GameClientContext after each action
   */
  handleStateUpdate(
    oldState: GameState,
    newState: GameState,
    action: GameAction
  ): void {
    const isSetupPhaseEnded =
      oldState.phase === 'setup' && newState.phase === 'playing';
    const isQueenActionCompleted =
      action.type === 'EXECUTE_QUEEN_SWAP' || action.type === 'SKIP_QUEEN_SWAP';
    const isJackActionCompleted =
      action.type === 'EXECUTE_JACK_SWAP' || action.type === 'SKIP_JACK_SWAP';
    const isPeekActionCompleted = action.type === 'CONFIRM_PEEK';

    if (isSetupPhaseEnded || isQueenActionCompleted || isPeekActionCompleted) {
      this.uiStore.clearTemporaryCardVisibility();
    }

    // Clear highlights when peek/swap actions complete
    if (
      isQueenActionCompleted ||
      isJackActionCompleted ||
      isPeekActionCompleted
    ) {
      this.uiStore.clearHighlightedCards();
    }

    // Handle rank declaration visual feedback
    if (action.type === 'SWAP_CARD') {
      this.handleRankDeclarationFeedback(oldState, newState, action);
    }

    // Handle King declaration visual feedback
    if (action.type === 'DECLARE_KING_ACTION') {
      this.handleKingDeclarationFeedback(oldState, newState, action);
    }

    // Handle failed toss-in visual feedback
    if (action.type === 'PARTICIPATE_IN_TOSS_IN') {
      this.handleFailedTossInFeedback(newState, action);
    }

    // Handle peek action card reveals (7, 8, 9, 10, Q, J)
    if (action.type === 'SELECT_ACTION_TARGET') {
      this.handlePeekActionCardReveal(oldState, newState, action);
    }
  }

  /**
   * Detect rank declarations and trigger visual feedback on DISCARD PILE
   *
   * When a rank is declared during swap:
   * 1. Old card from hand is swapped with new card from discard
   * 2. Old card goes to discard pile (BOTH correct and incorrect)
   * 3. If correct: card in discard pile shows GREEN (can be used for action)
   * 4. If incorrect: card in discard pile shows RED + penalty card added to hand
   *
   * The feedback should appear on the card in the DISCARD PILE to show all players
   * whether the declaration was correct or not.
   */
  private handleRankDeclarationFeedback(
    oldState: GameState,
    newState: GameState,
    action: SwapCardAction
  ): void {
    const { declaredRank } = action.payload;

    // Only process if a rank was declared
    if (!declaredRank) return;

    // Check if declaration was correct or incorrect
    // Correct: subPhase becomes 'awaiting_action' (card action available, card in pendingAction/drawn area)
    // Incorrect: subPhase becomes 'toss_queue_active' (penalty issued, card on discard pile)
    const declarationCorrect =
      newState.subPhase === 'awaiting_action' &&
      newState.pendingAction !== null;
    const declarationIncorrect =
      newState.subPhase === 'toss_queue_active' &&
      newState.pendingAction === null;

    if (declarationCorrect) {
      console.log(
        '[HeadlessService] Correct rank declaration - showing green feedback on drawn card'
      );
      // The card is in pendingAction (drawn area) - show green feedback there
      this.uiStore.setDrawnCardDeclarationFeedback(true);
    } else if (declarationIncorrect) {
      console.log(
        '[HeadlessService] Incorrect rank declaration - showing red feedback on discard pile'
      );
      // The card went directly to discard pile - show red feedback there
      this.uiStore.setDiscardPileDeclarationFeedback(false);
    }
  }

  /**
   * Handle King declaration visual feedback
   *
   * When a rank is declared with King action:
   * 1. King card goes to discard pile (ALWAYS)
   * 2. If correct: selected card also goes to discard pile, show GREEN feedback on discard
   * 3. If incorrect: selected card stays in hand (revealed), show RED feedback on the card in hand, penalty card added
   */
  private handleKingDeclarationFeedback(
    oldState: GameState,
    newState: GameState,
    action: DeclareKingActionAction
  ): void {
    const { declaredRank } = action.payload;
    const selectedCardInfo = oldState.pendingAction?.targets?.[0];

    if (!selectedCardInfo) return;

    const {
      playerId: targetPlayerId,
      position,
      card: selectedCard,
    } = selectedCardInfo;
    const actualRank = selectedCard?.rank;
    const isCorrect = actualRank === declaredRank;

    if (isCorrect) {
      console.log(
        '[HeadlessService] Correct King declaration - showing green feedback on discard pile'
      );
      // Both cards went to discard pile - show green feedback on discard
      this.uiStore.setDiscardPileDeclarationFeedback(true);
    } else {
      console.log(
        '[HeadlessService] Incorrect King declaration - showing red feedback on card in hand'
      );
      // Show red feedback on the incorrectly declared card in hand (not on King in discard)
      this.uiStore.addFailedTossInFeedback(targetPlayerId, position);

      // Make the incorrectly declared card temporarily visible (stays longer than usual)
      this.uiStore.addTemporarilyVisibleCard(targetPlayerId, position);
    }
  }

  /**
   * Handle failed toss-in attempt visual feedback
   * Show error indicator on card and make it temporarily visible
   */
  private handleFailedTossInFeedback(
    newState: GameState,
    action: GameAction
  ): void {
    if (action.type !== 'PARTICIPATE_IN_TOSS_IN') return;

    const { playerId, positions } = action.payload;
    const failedAttempts = newState.activeTossIn?.failedAttempts || [];

    // Check all positions for failed attempts
    for (const position of positions) {
      const wasFailedAttempt = failedAttempts.some(
        (attempt) =>
          attempt.playerId === playerId && attempt.position === position
      );

      if (wasFailedAttempt) {
        console.log(
          `[HeadlessService] Failed toss-in detected at position ${position} - adding visual feedback`
        );

        // Add failed toss-in feedback (shows error indicator)
        this.uiStore.addFailedTossInFeedback(playerId, position);
      }

      // Make card temporarily visible
      this.uiStore.addTemporarilyVisibleCard(playerId, position);
    }
  }

  /**
   * Handle peek action card reveals (7, 8, 9, 10, Q)
   *
   * Visibility Rules (local game with 1 human + bots):
   * - Peek actions (7, 8, 9, 10, Q) reveal cards ONLY to the acting player
   * - In local game: Only reveal if HUMAN is the acting player
   * - If bot is acting player: Don't reveal (bot's private information)
   * - Jack (J) is a blind swap: Never reveal cards
   *
   * When human selects peek targets, game-table-logic.ts already handles reveals.
   * This handler is for showing highlights/animations when BOT peeks, WITHOUT revealing card content.
   */
  private handlePeekActionCardReveal(
    oldState: GameState,
    newState: GameState,
    action: SelectActionTargetAction
  ): void {
    const { playerId, targetPlayerId } = action.payload;
    const actionCard = newState.pendingAction?.card;
    const position =
      action.payload.rank === 'Any'
        ? action.payload.position
        : newState.players.find((p) => p.id === targetPlayerId)!.cards.length -
          1;

    if (!actionCard) return;

    // Check if this is a peek action card (excluding Jack - blind swap)
    const isPeekAction =
      actionCard.rank === '7' ||
      actionCard.rank === '8' ||
      actionCard.rank === '9' ||
      actionCard.rank === '10' ||
      actionCard.rank === 'Q';

    if (!isPeekAction) return;

    // Find the acting player (the one using the action card)
    const actingPlayer = newState.players.find((p) => p.id === playerId);
    if (!actingPlayer) return;

    // Only reveal card if HUMAN is the acting player
    // (In local game, revealing to human = revealing to the only viewer)
    if (actingPlayer.isHuman) {
      this.uiStore.addTemporarilyVisibleCard(targetPlayerId, position);
      console.log(
        `[HeadlessService] Human peek action (${actionCard.rank}) - revealing card at ${targetPlayerId} position ${position}`
      );
    } else {
      // Bot is peeking - don't reveal card content, but add highlight for visual feedback
      // This shows the human WHICH cards the bot peeked at, without revealing the card value
      this.uiStore.addHighlightedCard(targetPlayerId, position);
      console.log(
        `[HeadlessService] Bot peek action (${actionCard.rank}) - highlighting (not revealing) card at ${targetPlayerId} position ${position}`
      );
    }
  }

  public dispose() {
    // Unregister callback
    if (this._unregisterCallback) {
      this._unregisterCallback();
    }
    console.log('Disposed HeadlessService');
  }
}
