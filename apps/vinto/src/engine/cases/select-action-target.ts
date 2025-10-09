import { GameState, SelectActionTargetAction } from "../types";
import copy from 'fast-copy';

  /**
   * SELECT_ACTION_TARGET Handler
   *
   * Flow:
   * 1. Player has chosen to use a card action (via USE_CARD_ACTION)
   * 2. Now selecting which card/player to target
   * 3. Add target to pending action's targets array
   * 4. Execute card-specific logic based on card rank:
   *    - Jack (J): Blind swap 2 cards (needs 2 targets)
   *    - 7/8/9/10: Peek at card (1 target, confirm needed)
   *    - Ace (A): Force opponent to draw (1 target)
   *
   * Note: Queen (Q) and King (K) have their own dedicated handlers
   */
  export function handleSelectActionTarget(
    state: GameState,
    action: SelectActionTargetAction
  ): GameState {
    const { targetPlayerId, position } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Add target to pending action
    if (newState.pendingAction) {
      newState.pendingAction.targets.push({
        playerId: targetPlayerId,
        position,
      });
    }

    const cardRank = newState.pendingAction?.card?.rank;
    const targets = newState.pendingAction?.targets || [];

    // Handle card-specific logic
    if (cardRank === 'J') {
      // Jack: Blind swap 2 cards
      if (targets.length === 2) {
        // We have both targets, execute the swap
        const [target1, target2] = targets;

        // Find the two target players
        const player1 = newState.players.find((p) => p.id === target1.playerId);
        const player2 = newState.players.find((p) => p.id === target2.playerId);

        if (player1 && player2) {
          // Swap the two cards (blind swap - no peeking)
          const card1 = player1.cards[target1.position];
          const card2 = player2.cards[target2.position];

          player1.cards[target1.position] = card2;
          player2.cards[target2.position] = card1;
        }

        // Move Jack card to discard pile
        if (newState.pendingAction?.card) {
          newState.discardPile.push(newState.pendingAction.card);
        }

        // Clear pending action
        newState.pendingAction = null;

        // Increment turn count
        newState.turnCount += 1;

        // Transition to idle (turn complete)
        newState.subPhase = 'idle';
      }
      // If we only have 1 target, stay in awaiting_action phase for second target
      // No state changes needed, just return current state with updated targets
    } else if (cardRank === '7' || cardRank === '8' || cardRank === '9' || cardRank === '10') {
      // Peek cards (7/8 peek own, 9/10 peek opponent)
      // The peek action is handled by UI (showing the card)
      // This handler just tracks the target
      // Player will then call CONFIRM_PEEK to complete the action
      // Stay in awaiting_action phase - no further action needed here
    } else if (cardRank === 'A') {
      // Ace: Force opponent to draw a penalty card
      if (targets.length === 1) {
        const target = targets[0];
        const targetPlayer = newState.players.find((p) => p.id === target.playerId);

        if (targetPlayer && newState.drawPile.length > 0) {
          // Draw a card from the pile
          const penaltyCard = newState.drawPile.shift();
          if (penaltyCard) {
            // Add penalty card to target player's hand
            targetPlayer.cards.push(penaltyCard);
          }
        }

        // Move Ace card to discard pile
        if (newState.pendingAction?.card) {
          newState.discardPile.push(newState.pendingAction.card);
        }

        // Clear pending action
        newState.pendingAction = null;

        // Increment turn count
        newState.turnCount += 1;

        // Transition to idle (turn complete)
        newState.subPhase = 'idle';
      }
    } else {
      // Default: Unknown card type or not implemented yet
      // Move card to discard, complete turn
      if (newState.pendingAction?.card) {
        newState.discardPile.push(newState.pendingAction.card);
      }

      // Clear pending action
      newState.pendingAction = null;

      // Increment turn count
      newState.turnCount += 1;

      // Transition to idle (turn complete)
      newState.subPhase = 'idle';
    }

    return newState;
  }
