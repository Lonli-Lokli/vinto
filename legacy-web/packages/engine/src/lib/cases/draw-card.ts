import { GameState, DrawCardAction } from '@vinto/shapes';
import { copy } from 'fast-copy';

/**
 * DRAW_CARD Handler
 *
 * Flow:
 * 1. Transition to 'drawing' phase (for animation)
 * 2. Remove top card from draw pile
 * 3. Create pending action with drawn card
 * 4. Transition to 'choosing' phase
 */
export function handleDrawCard(
  state: GameState,
  action: DrawCardAction
): GameState {
  const { playerId } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Transition to drawing phase (skip if already in ai_thinking for bots)
  if (newState.subPhase !== 'ai_thinking') {
    newState.subPhase = 'drawing';
  }

  // Draw the top card
  const drawnCard = newState.drawPile.drawTop();
  if (!drawnCard) {
    // Should never happen due to validation, but be defensive
    return state;
  }

  // Create pending action
  newState.pendingAction = {
    card: drawnCard,    
    playerId,
    actionPhase: 'choosing-action',
    from: 'drawing',
    targets: [],
  };

  // Transition to choosing phase
  // (In real implementation, this would happen after animation completes)
  newState.subPhase = 'choosing';

  return newState;
}
