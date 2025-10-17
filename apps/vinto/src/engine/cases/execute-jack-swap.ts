import { ExecuteJackSwapAction, GameState } from '@/shared';
import copy from 'fast-copy';
import {
  clearTossInReadyList,
  getAutomaticallyReadyPlayers,
} from '../utils/toss-in-utils';

/**
 * EXECUTE_JACK_SWAP Handler
 *
 * Flow:
 * 1. Player has used a Jack card action
 * 2. Player has selected 2 cards without peeking (stored in pendingAction.targets)
 * 3. Player chooses to swap those two cards (Jack's special ability)
 * 4. Swap the two target cards between players/positions
 * 5. Move Jack card to discard pile
 * 6. Complete turn (increment turn count, transition to idle)
 *
 * Note: Jack does not allow peeking at cards before swapping them
 * This handler executes the swap; SKIP_JACK_SWAP skips it
 */
export function handleExecuteJackSwap(
  state: GameState,
  _action: ExecuteJackSwapAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Get the two targets from pending action
  const targets = newState.pendingAction!.targets;
  const [target1, target2] = targets;

  // Find the two target players
  const player1 = newState.players.find((p) => p.id === target1.playerId);
  const player2 = newState.players.find((p) => p.id === target2.playerId);

  if (!player1 || !player2) {
    // Should never happen due to validation
    return state;
  }

  // Swap the two cards
  const card1 = player1.cards[target1.position];
  const card2 = player2.cards[target2.position];

  player1.cards[target1.position] = card2;
  player2.cards[target2.position] = card1;

  // Update known card positions after swap
  // Jack swap is blind: if a player knew a card's position before the swap, they now know its new position in the other player's hand
  // For both players, transfer knownCardPositions for swapped cards
  // If player1 knew their card at target1.position, they now know player2's card at target2.position
  // TODO: update as it's currently not correct
  if (player1.knownCardPositions.includes(target1.position)) {
    if (!player1.knownCardPositions.includes(target2.position)) {
      player1.knownCardPositions.push(target2.position);
    }
  }
  // Remove old position from player1
  player1.knownCardPositions = player1.knownCardPositions.filter(
    (pos) => pos !== target1.position
  );

  // If player2 knew their card at target2.position, they now know player1's card at target1.position
  if (player2.knownCardPositions.includes(target2.position)) {
    if (!player2.knownCardPositions.includes(target1.position)) {
      player2.knownCardPositions.push(target1.position);
    }
  }
  // Remove old position from player2
  player2.knownCardPositions = player2.knownCardPositions.filter(
    (pos) => pos !== target2.position
  );

  const jackCard = newState.pendingAction?.card;

  // Move Jack card to discard pile
  if (jackCard) {
    newState.discardPile.addToTop(jackCard);
  }

  // Clear pending action
  newState.pendingAction = null;

  // Check if we're processing a toss-in queue
  const isProcessingTossInQueue =
    newState.activeTossIn !== null &&
    newState.activeTossIn.queuedActions.length > 0;

  if (isProcessingTossInQueue) {
    // Remove the processed action from the queue
    newState.activeTossIn!.queuedActions.shift();

    console.log(
      '[handleExecuteJackSwap] Action completed during toss-in queue processing',
      {
        remainingActions: newState.activeTossIn!.queuedActions.length,
      }
    );

    // Check if there are more queued actions
    if (newState.activeTossIn!.queuedActions.length > 0) {
      // Process next queued action
      const nextAction = newState.activeTossIn!.queuedActions[0];

      newState.pendingAction = {
        card: nextAction.card,
        playerId: nextAction.playerId,
        actionPhase: 'choosing-action',
        targets: [],
      };

      newState.subPhase = 'awaiting_action';

      console.log('[handleExecuteJackSwap] Processing next queued action:', {
        playerId: nextAction.playerId,
        card: nextAction.card.rank,
      });
    } else {
      // No more queued actions, finish toss-in and advance turn
      const originalPlayerIndex = newState.activeTossIn!.originalPlayerIndex;
      newState.activeTossIn = null;

      // Advance to next player from original player
      newState.currentPlayerIndex =
        (originalPlayerIndex + 1) % newState.players.length;

      if (newState.currentPlayerIndex === 0) {
        newState.turnCount++;
      }

      // Check if game should end (after vinto call, when we return to the vinto caller)
      if (
        newState.phase === 'final' &&
        newState.players[newState.currentPlayerIndex].id ===
          newState.vintoCallerId
      ) {
        // Final round complete - end the game
        newState.phase = 'scoring';
        newState.subPhase = 'idle';

        console.log(
          '[handleExecuteJackSwap] Final round complete, game finished'
        );

        return newState;
      }

      const nextPlayer = newState.players[newState.currentPlayerIndex];
      newState.subPhase = nextPlayer.isBot ? 'ai_thinking' : 'idle';

      console.log(
        '[handleExecuteJackSwap] All toss-in actions processed, turn advanced'
      );
    }
  } else if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation but no queue)
    // Clear the ready list so players can confirm again for this new toss-in round
    clearTossInReadyList(newState);
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;
    console.log(
      '[handleExecuteJackSwap] Jack swap executed during toss-in, returning to toss-in phase (ready list cleared)'
    );
  } else {
    // Initialize new toss-in phase (normal turn flow)
    if (jackCard) {
      newState.activeTossIn = {
        rank: jackCard.rank,
        initiatorId: _action.payload.playerId,
        originalPlayerIndex: newState.currentPlayerIndex,
        participants: [],
        queuedActions: [],
        waitingForInput: true,
        playersReadyForNextTurn: getAutomaticallyReadyPlayers(newState.players),
      };
    }

    // Transition to toss-in phase
    newState.subPhase = 'toss_queue_active';

    console.log('[handleExecuteJackSwap] JJack swap executed, toss-in active');
  }

  return newState;
}
