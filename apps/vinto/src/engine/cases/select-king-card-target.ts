import { GameState, SelectKingCardTargetAction } from '@/shared';
import copy from 'fast-copy';

/**
 * SELECT_KING_CARD_TARGET Handler
 *
 * Flow:
 * 1. Player has used a King card (via USE_CARD_ACTION)
 * 2. Player selects a card from their hand or an opponent's hand (without revealing)
 * 3. Store the selected card in pendingAction.selectedCardForKing
 * 4. Transition to 'declaring-rank' phase for rank selection
 * 5. Next: Player will DECLARE_KING_ACTION with the rank they think the card is
 *
 * Note: The card is not revealed yet - it will be validated when declaring the rank
 */
export function handleSelectKingCardTarget(
  state: GameState,
  action: SelectKingCardTargetAction
): GameState {
  const { playerId, targetPlayerId, position } = action.payload;

  console.log('[handleSelectKingCardTarget] Selecting card for King action:', {
    playerId,
    targetPlayerId,
    position,
  });

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Find the target player
  const targetPlayer = newState.players.find((p) => p.id === targetPlayerId);
  if (!targetPlayer) {
    console.error('[handleSelectKingCardTarget] Target player not found');
    return state;
  }

  // Get the card at the specified position (without revealing to UI yet)
  const selectedCard = targetPlayer.cards[position];
  if (!selectedCard) {
    console.error('[handleSelectKingCardTarget] Card not found at position');
    return state;
  }

  // Store the selected card info in pendingAction
  if (newState.pendingAction) {
    newState.pendingAction.selectedCardForKing = {
      playerId: targetPlayerId,
      position,
      card: selectedCard,
    };

    // Transition to declaring-rank phase
    newState.pendingAction.actionPhase = 'declaring-rank';
  }

  console.log(
    '[handleSelectKingCardTarget] Card selected, ready for rank declaration:',
    {
      targetPlayerId,
      position,
      cardRank: selectedCard.rank,
    }
  );

  return newState;
}
