import {
  GameState,
  NeverError,
  SelectActionTargetAction,
  logger,
} from '@vinto/shapes';
import copy from 'fast-copy';
import {
  addTossInCard,
  clearTossInReadyList,
  getAutomaticallyReadyPlayers,
} from '../utils/toss-in-utils';

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
      // Jack: Do not peeking at 2 cards from 2 different players, optionally swap them
      // Allow swapping any cards (own or opponent), but must be from different players

      if (targets.length === 1) {
        // First card choosen - store the card data for later visibility
        const targetPlayer = newState.players.find(
          (p) => p.id === targetPlayerId
        );
        if (targetPlayer) {
          const choosenCard = targetPlayer.cards[position];
          // Store the card in the target so it can be revealed in multiplayer
          newState.pendingAction!.targets[0].card = choosenCard;
        }
        // Stay in awaiting_action phase for second card selection
      } else if (targets.length === 2) {
        // Validate: cards must be from different players
        const [target1, target2] = targets;

        if (target1.playerId === target2.playerId) {
          // Cannot choose two cards from the same player - this should be prevented by UI
          // but we handle it here for safety
          logger.warn('[Jack] Cannot choose two cards from the same player', {
            playerId: target1.playerId,
            position1: target1.position,
            position2: target2.position,
          });
          // Remove the invalid second target
          newState.pendingAction!.targets.pop();
          return newState;
        }

        // Second card choosen - store the card data
        const targetPlayer = newState.players.find(
          (p) => p.id === targetPlayerId
        );
        if (targetPlayer) {
          const choosenCard = targetPlayer.cards[position];
          // Store the card in the target so it can be revealed in multiplayer
          newState.pendingAction!.targets[1].card = choosenCard;
        }
        // Both cards selected, stay in awaiting_action phase
        // UI will handle swap/skip decision
        // The actual swap/skip is handled by EXECUTE_JACK_SWAP or SKIP_JACK_SWAP actions
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
          logger.warn('[Queen] Cannot peek two cards from the same player', {
            playerId: target1.playerId,
            position1: target1.position,
            position2: target2.position,
          });
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
      // King: Select a card, then declare rank
      // Step 1: Player selects a card (targets.length === 0 → 1)
      // Step 2: Player declares rank via DECLARE_KING_ACTION (handled separately)

      if (targets.length === 1) {
        // First target selected - store the card for later validation
        const targetPlayer = newState.players.find(
          (p) => p.id === targetPlayerId
        );
        if (targetPlayer) {
          const selectedCard = targetPlayer.cards[position];
          // Store the card in the target for validation when rank is declared
          newState.pendingAction!.targets[0].card = selectedCard;
        }
        // Stay in awaiting_action phase
        // Next: Player will call DECLARE_KING_ACTION with declaredRank
      }
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
          newState.discardPile.addToTop({
            ...copy(aceCard),
            played: true,
          });
        }

        // Clear pending action
        newState.pendingAction = null;

        // Check if this action was part of a toss-in
        if (newState.activeTossIn !== null) {
          // ADD or REPLACE this card's rank to toss-in ranks if not already present
          newState.activeTossIn.ranks = addTossInCard(
            newState.activeTossIn.ranks,
            aceCard?.rank
          );
          // Clear the ready list so players can confirm again for this new toss-in round
          clearTossInReadyList(newState);
          newState.subPhase = 'toss_queue_active';
          newState.activeTossIn.waitingForInput = true;
          console.log(
            '[handleSelectActionTarget] Ace action during toss-in complete, rank added, returning to toss-in phase (ready list cleared)',
            { ranks: newState.activeTossIn.ranks }
          );
        } else {
          // Initialize new toss-in phase (normal turn flow)
          if (aceCard) {
            // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
            newState.activeTossIn = {
              ranks: [aceCard.rank],
              initiatorId: action.payload.playerId,
              originalPlayerIndex: newState.currentPlayerIndex,
              participants: [],
              queuedActions: [],
              waitingForInput: true,
              playersReadyForNextTurn: getAutomaticallyReadyPlayers(
                newState.players
              ),
            };
          }

          // Transition to toss-in phase
          newState.subPhase = 'toss_queue_active';
        }
      } else {
        logger.warn('[Ace] Cannot force multiple players', {
          targetCount: newState.pendingAction?.targets.length ?? 0,
          targets:
            newState.pendingAction?.targets.map((t) => ({
              playerId: t.playerId,
              position: t.position,
            })) ?? [],
        });
        return newState;
      }
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
