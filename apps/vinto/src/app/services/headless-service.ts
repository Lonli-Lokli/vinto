import { inject, injectable } from 'tsyringe';
import { UIStore } from '../stores';
import {
  GameAction,
  GameState,
  SwapCardAction,
  DeclareKingActionAction,
} from '@/shared';

@injectable()
export class HeadlessService {
  private uiStore: UIStore;

  constructor(@inject(UIStore) uiStore: UIStore) {
    this.uiStore = uiStore;
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

    // Note: We no longer clear temporarily visible cards when toss-in is activated
    // because they now have timestamp-based expiration (3 seconds) and should
    // remain visible for the full duration even during toss-in phase
    if (isSetupPhaseEnded || isQueenActionCompleted) {
      this.uiStore.clearTemporaryCardVisibility();
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
    const selectedCardInfo = oldState.pendingAction?.selectedCardForKing;

    if (!selectedCardInfo) return;

    const {
      playerId: targetPlayerId,
      position,
      card: selectedCard,
    } = selectedCardInfo;
    const actualRank = selectedCard.rank;
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

    const { playerId, position } = action.payload;
    const failedAttempts = newState.activeTossIn?.failedAttempts || [];

    const wasFailedAttempt = failedAttempts.some(
      (attempt) =>
        attempt.playerId === playerId && attempt.position === position
    );

    if (wasFailedAttempt) {
      console.log(
        '[HeadlessService] Failed toss-in detected - adding visual feedback'
      );

      // Add failed toss-in feedback (shows error indicator)
      this.uiStore.addFailedTossInFeedback(playerId, position);

      // Make card temporarily visible (so player can see which card was wrong)
      this.uiStore.addTemporarilyVisibleCard(playerId, position);
    }
  }

  public dispose() {
    console.log('Disposed HeadlessService');
  }
}
