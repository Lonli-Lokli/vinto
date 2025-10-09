import { GameState, SwapCardAction } from "../types";
import copy from 'fast-copy';

 /**
   * SWAP_CARD Handler
   *
   * Flow:
   * 1. Get the card from pendingAction
   * 2. Swap it with the card at the specified position in player's hand
   * 3. Update player's known cards if declaration was made
   * 4. Clear pending action
   * 5. Transition to idle (ready for next action: discard or use card action)
   */
  export function handleSwapCard(
    state: GameState,
    action: SwapCardAction
  ): GameState {
    const { playerId, position, declaredRank } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Get the pending card (from draw)
    const pendingCard = newState.pendingAction!.card;

    // Find the player
    const playerIndex = newState.players.findIndex((p) => p.id === playerId);
    if (playerIndex === -1) {
      return state; // Should never happen due to validation
    }

    const player = newState.players[playerIndex];

    // Swap: take card from hand, put pending card in its place
    const cardFromHand = player.cards[position];
    player.cards[position] = pendingCard;

    // TODO: verify cases with J & Q for cases when he is swapping known or unknown cards from players
    // If player declared a rank, track it in known positions
    if (declaredRank) {
      // Update known card positions
      const existingKnown = player.knownCardPositions.includes(position);
      if (!existingKnown) {
        player.knownCardPositions.push(position);
      }
    }

    // Update pending action: the removed card is now what we're deciding about
    // Player will either discard it or use its action
    newState.pendingAction = {
      card: cardFromHand,
      playerId,
      actionPhase: 'choosing-action',
      targets: [],
    };

    // Transition to idle (player must now choose: discard or use action)
    // Actually, we need a different phase here - player is now choosing what to do with drawn card
    // Let's use 'selecting' to indicate they're deciding between discard/use-action
    newState.subPhase = 'selecting';

    return newState;
  }