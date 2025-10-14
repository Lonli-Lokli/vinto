import { GameState, NeverError, SelectActionTargetAction } from '@/shared';
import copy from 'fast-copy';

/**
 * SELECT_ACTION_TARGET Handler
 *
 * Flow:
 * 1. Player has chosen to use a card action (via USE_CARD_ACTION)
 * 2. Now selecting which card/player to target
 * 3. Add target to pending action's targets array
 * 4. Execute card-specific logic based on card rank:
 *    - Jack (J): Blind swap 2 cards from 2 different players (needs 2 targets)
 *    - Queen (Q): Peek 2 cards from 2 different players, then optionally swap
 *    - 7/8: Peek own card (1 target, confirm needed)
 *    - 9/10: Peek opponent card (1 target, confirm needed)
 *    - Ace (A): Force opponent to draw (1 target)
 *
 * Note: King (K) has its own dedicated handler
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
  switch (cardRank) {
    case '7':
    case '8':
    case '9':
    case '10': {
      // Peek cards (7/8 peek own, 9/10 peek opponent)
      // The peek action is handled by UI (showing the card)
      // This handler just tracks the target
      // Player will then call CONFIRM_PEEK to complete the action
      // Stay in awaiting_action phase - no further action needed here
      break;
    }

    case 'J': {
      // Jack: Blind swap 2 cards from 2 different players
      // Allow swapping any cards (own or opponent), but must be from different players
      if (targets.length === 2) {
        // Validate: cards must be from different players
        const [target1, target2] = targets;
        
        if (target1.playerId === target2.playerId) {
          // Cannot swap two cards from the same player - this should be prevented by UI
          // but we handle it here for safety
          console.warn('[Jack] Cannot swap two cards from the same player');
          return newState;
        }

        // Find the two target players
        const player1 = newState.players.find((p) => p.id === target1.playerId);
        const player2 = newState.players.find((p) => p.id === target2.playerId);

        if (player1 && player2) {
          // Swap the two cards (blind swap - no peeking)
          const card1 = player1.cards[target1.position];
          const card2 = player2.cards[target2.position];

          player1.cards[target1.position] = card2;
          player2.cards[target2.position] = card1;

          // Update known card positions after swap
          // If a player knew a card at the swapped position, they no longer know it after swap
          // This is a blind swap - neither player sees the cards being swapped
          const wasPlayer1Known = player1.knownCardPositions.includes(
            target1.position
          );
          const wasPlayer2Known = player2.knownCardPositions.includes(
            target2.position
          );

          // Remove known positions for both players at swapped locations
          if (wasPlayer1Known) {
            player1.knownCardPositions = player1.knownCardPositions.filter(
              (pos) => pos !== target1.position
            );
          }
          if (wasPlayer2Known) {
            player2.knownCardPositions = player2.knownCardPositions.filter(
              (pos) => pos !== target2.position
            );
          }
        }

        const jackCard = newState.pendingAction?.card;

        // Move Jack card to discard pile
        if (jackCard) {
          newState.discardPile.addToTop(jackCard);
        }

        // Clear pending action
        newState.pendingAction = null;

        // Initialize toss-in phase
        if (jackCard) {
          newState.activeTossIn = {
            rank: jackCard.rank,
            initiatorId: action.payload.playerId,
            participants: [],
            queuedActions: [],
            waitingForInput: true,
            playersReadyForNextTurn: [],
          };
        }

        // Transition to toss-in phase
        newState.subPhase = 'toss_queue_active';
      }
      // If we only have 1 target, stay in awaiting_action phase for second target
      // No state changes needed, just return current state with updated targets
      break;
    }

    case 'A': {
      // Ace: Force opponent to draw a penalty card
      if (targets.length === 1) {
        const target = targets[0];
        const targetPlayer = newState.players.find(
          (p) => p.id === target.playerId
        );

        if (targetPlayer && newState.drawPile.length > 0) {
          // Draw a card from the pile
          const penaltyCard = newState.drawPile.drawTop();
          if (penaltyCard) {
            // Add penalty card to target player's hand
            targetPlayer.cards.push(penaltyCard);
          }
        }

        const aceCard = newState.pendingAction?.card;

        // Move Ace card to discard pile
        if (aceCard) {
          newState.discardPile.addToTop(aceCard);
        }

        // Clear pending action
        newState.pendingAction = null;

        // Initialize toss-in phase
        if (aceCard) {
          newState.activeTossIn = {
            rank: aceCard.rank,
            initiatorId: action.payload.playerId,
            participants: [],
            queuedActions: [],
            waitingForInput: true,
            playersReadyForNextTurn: [],
          };
        }

        // Transition to toss-in phase
        newState.subPhase = 'toss_queue_active';
      } else {
          console.warn('[Ace] Cannot force multiple players');
          return newState;
      }
      break;
    }

    case 'Q': {
      // Queen: Peek at 2 cards from 2 different players, optionally swap them
      // Allow peeking any cards (own or opponent), but must be from different players
      
      if (targets.length === 1) {
        // First card peeked - store the card data for later visibility
        const targetPlayer = newState.players.find(
          (p) => p.id === targetPlayerId
        );
        if (targetPlayer) {
          const peekedCard = targetPlayer.cards[position];
          // Store the card in the target so it can be revealed in multiplayer
          newState.pendingAction!.targets[0].card = peekedCard;
        }
        // Stay in awaiting_action phase for second card selection
      } else if (targets.length === 2) {
        // Validate: cards must be from different players
        const [target1, target2] = targets;
        
        if (target1.playerId === target2.playerId) {
          // Cannot peek two cards from the same player - this should be prevented by UI
          // but we handle it here for safety
          console.warn('[Queen] Cannot peek two cards from the same player');
          // Remove the invalid second target
          newState.pendingAction!.targets.pop();
          return newState;
        }
        
        // Second card peeked - store the card data
        const targetPlayer = newState.players.find(
          (p) => p.id === targetPlayerId
        );
        if (targetPlayer) {
          const peekedCard = targetPlayer.cards[position];
          // Store the card in the target so it can be revealed in multiplayer
          newState.pendingAction!.targets[1].card = peekedCard;
        }
        // Both cards selected, stay in awaiting_action phase
        // UI will handle swap/skip decision
        // The actual swap/skip is handled by EXECUTE_QUEEN_SWAP or SKIP_QUEEN_SWAP actions
      }
      break;
    }

    case 'K': {
      break;
    }
    case 'Joker': {
      // Joker: Wild card, can be used as any rank
      // Stay in awaiting_action phase until player decides how to use it
      break;
    }

    case '2':
    case '3':
    case '4':
    case '5':
    case '6': {
      // No action cards - should not reach here
      break;
    }

    case undefined:
      // No card rank (should not happen), just return current state
      break;
    default:
      throw new NeverError(cardRank);
  }

  return newState;
}
