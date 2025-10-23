// services/mcts-state-transition.ts
import { getCardAction, Card } from '@vinto/shapes';
import copy from 'fast-copy';
import { MCTSGameState, MCTSMove } from './mcts-types';

/**
 * State transition logic for MCTS
 * Applies moves to game states
 */
export class MCTSStateTransition {
  /**
   * Apply a move to a state and return new state
   */
  static applyMove(state: MCTSGameState, move: MCTSMove): MCTSGameState {
    // Deep clone state
    const newState = this.cloneState(state);

    switch (move.type) {
      case 'draw':
        return this.applyDraw(newState, move);

      case 'take-discard':
        return this.applyTakeDiscard(newState, move);

      case 'use-action':
        return this.applyUseAction(newState, move);

      case 'swap':
        return this.applySwap(newState, move);

      case 'discard':
        return this.applyDiscard(newState, move);

      case 'toss-in':
        return this.applyTossIn(newState, move);

      case 'call-vinto':
        return this.applyCallVinto(newState, move);

      case 'pass':
        return this.applyPass(newState, move);

      default:
        return newState;
    }
  }

  /**
   * Apply draw move
   */
  private static applyDraw(
    state: MCTSGameState,
    _move: MCTSMove
  ): MCTSGameState {
    // Decrease deck size
    if (state.deckSize > 0) {
      state.deckSize--;
    }

    // Advance turn
    state.currentPlayerIndex =
      (state.currentPlayerIndex + 1) % state.players.length;
    state.turnCount++;

    return state;
  }

  /**
   * Apply take from discard
   */
  private static applyTakeDiscard(
    state: MCTSGameState,
    _move: MCTSMove
  ): MCTSGameState {
    // Card is now in player's hand (tracked elsewhere)
    // Discard pile top is removed
    state.discardPileTop = null;

    // Advance turn
    state.currentPlayerIndex =
      (state.currentPlayerIndex + 1) % state.players.length;
    state.turnCount++;

    return state;
  }

  /**
   * Apply use action move (action cards like peek, swap, etc.)
   */
  private static applyUseAction(
    state: MCTSGameState,
    move: MCTSMove
  ): MCTSGameState {
    // Action effects depend on the action type
    // Use state.pendingCard if move.actionCard is not set (which is the case for action moves generated in awaiting_action phase)
    const actionCard = move.actionCard || state.pendingCard;
    if (actionCard) {
      const actionType = getCardAction(actionCard.rank);

      switch (actionType) {
        case 'swap-cards':
          // Swap two cards
          if (move.targets && move.targets.length === 2) {
            const target1 = move.targets[0];
            const target2 = move.targets[1];

            const card1Key = `${target1.playerId}-${target1.position}`;
            const card2Key = `${target2.playerId}-${target2.position}`;

            const card1 = state.hiddenCards.get(card1Key);
            const card2 = state.hiddenCards.get(card2Key);

            if (card1 && card2) {
              // Swap the cards
              state.hiddenCards.set(card1Key, card2);
              state.hiddenCards.set(card2Key, card1);

              // Update player scores
              const player1 = state.players.find(
                (p) => p.id === target1.playerId
              );
              const player2 = state.players.find(
                (p) => p.id === target2.playerId
              );

              if (player1) {
                player1.score = player1.score - card1.value + card2.value;
              }
              if (player2 && player2.id !== player1?.id) {
                player2.score = player2.score - card2.value + card1.value;
              }
            }
          }
          break;

        case 'peek-own':
        case 'peek-opponent':
          // Peek actions just reveal information (already tracked in bot memory)
          // No state change needed for simulation
          break;

        case 'peek-and-swap':
          // Queen: Peek two cards, optionally swap them
          // Check if the move includes a swap decision
          if (move.shouldSwap && move.targets && move.targets.length === 2) {
            const target1 = move.targets[0];
            const target2 = move.targets[1];

            const card1Key = `${target1.playerId}-${target1.position}`;
            const card2Key = `${target2.playerId}-${target2.position}`;

            const card1 = state.hiddenCards.get(card1Key);
            const card2 = state.hiddenCards.get(card2Key);

            if (card1 && card2) {
              // Swap the cards
              state.hiddenCards.set(card1Key, card2);
              state.hiddenCards.set(card2Key, card1);

              // Update player scores
              const player1 = state.players.find(
                (p) => p.id === target1.playerId
              );
              const player2 = state.players.find(
                (p) => p.id === target2.playerId
              );

              if (player1) {
                player1.score = player1.score - card1.value + card2.value;
              }
              if (player2 && player2.id !== player1?.id) {
                player2.score = player2.score - card2.value + card1.value;
              }
            }
          }
          // If shouldSwap is false, just peek (no state change)
          break;

        case 'force-draw':
          // Opponent draws a card (increases hand size)
          if (move.targets && move.targets.length > 0) {
            const target = move.targets[0];
            const targetPlayer = state.players.find(
              (p) => p.id === target.playerId
            );

            if (targetPlayer && state.deckSize > 0) {
              targetPlayer.cardCount++;
              state.deckSize--;

              // Estimate the drawn card as average value
              targetPlayer.score += 6;
            }
          }
          break;

        case 'declare-action':
          // King - declare action (simulated as giving information)
          // No state change in simulation
          break;
      }
    }

    // Clear pending card since action is complete
    state.pendingCard = null;

    // Advance turn
    state.currentPlayerIndex =
      (state.currentPlayerIndex + 1) % state.players.length;
    state.turnCount++;

    return state;
  }

  /**
   * Apply swap into hand
   */
  private static applySwap(
    state: MCTSGameState,
    move: MCTSMove
  ): MCTSGameState {
    const player = state.players[state.currentPlayerIndex];

    if (move.swapPosition !== undefined && player) {
      // Remove old card from position
      const oldCardKey = `${player.id}-${move.swapPosition}`;
      const oldCard = state.hiddenCards.get(oldCardKey);

      if (oldCard) {
        // Update player score
        player.score -= oldCard.value;

        // Remove old card
        state.hiddenCards.delete(oldCardKey);

        // Discarded card might trigger toss-in phase
        if (oldCard.rank !== 'K' && oldCard.rank !== 'A') {
          // Check if any other player could toss in
          let hasPotentialTossIn = false;

          for (const otherPlayer of state.players) {
            if (otherPlayer.id === player.id) continue;

            for (let pos = 0; pos < otherPlayer.cardCount; pos++) {
              const card = state.hiddenCards.get(`${otherPlayer.id}-${pos}`);
              if (card && card.rank === oldCard.rank) {
                hasPotentialTossIn = true;
                break;
              }
            }
            if (hasPotentialTossIn) break;
          }

          if (hasPotentialTossIn) {
            state.isTossInPhase = true;
            state.discardPileTop = oldCard;
            // Don't advance turn yet
            return state;
          }
        }
      }
    }

    // Advance turn
    state.currentPlayerIndex =
      (state.currentPlayerIndex + 1) % state.players.length;
    state.turnCount++;

    return state;
  }

  /**
   * Apply discard move (drawn card discarded without swapping)
   */
  private static applyDiscard(
    state: MCTSGameState,
    _move: MCTSMove
  ): MCTSGameState {
    // Card goes to discard pile (but we don't track it in simplified simulation)
    // Discarding means the player chose not to swap the drawn card

    // Advance turn
    state.currentPlayerIndex =
      (state.currentPlayerIndex + 1) % state.players.length;
    state.turnCount++;

    return state;
  }

  /**
   * Apply toss-in move (supports multiple cards)
   */
  private static applyTossIn(
    state: MCTSGameState,
    move: MCTSMove
  ): MCTSGameState {
    const player = state.players.find((p) => p.id === move.playerId);

    if (player && move.tossInPositions && move.tossInPositions.length > 0) {
      // Sort positions in descending order to avoid index shift issues
      const sortedPositions = [...move.tossInPositions].sort((a, b) => b - a);

      for (const position of sortedPositions) {
        // Remove card from hand
        const cardKey = `${player.id}-${position}`;
        const card = state.hiddenCards.get(cardKey);

        if (card) {
          state.hiddenCards.delete(cardKey);

          // Decrease card count
          player.cardCount--;

          // Update player score estimate
          player.score -= card.value;
        }

        // Update positions in hiddenCards map (shift down after removal)
        const updatedHiddenCards = new Map<string, Card>();
        for (const [key, cardValue] of state.hiddenCards.entries()) {
          if (key.startsWith(`${player.id}-`)) {
            const pos = parseInt(key.split('-')[1], 10);
            if (pos > position) {
              // Shift position down by 1
              updatedHiddenCards.set(`${player.id}-${pos - 1}`, cardValue);
            } else {
              // Keep same position
              updatedHiddenCards.set(key, cardValue);
            }
          } else {
            // Other players' cards remain unchanged
            updatedHiddenCards.set(key, cardValue);
          }
        }
        state.hiddenCards = updatedHiddenCards;

        // Update known cards positions (shift down after removal)
        const updatedKnownCards = new Map<
          number,
          { card: Card | null; confidence: number }
        >();
        for (const [pos, memory] of player.knownCards.entries()) {
          if (pos === position) {
            // Remove this position (card was tossed in)
            continue;
          } else if (pos > position) {
            // Shift down
            updatedKnownCards.set(pos - 1, memory);
          } else {
            // Keep same position
            updatedKnownCards.set(pos, memory);
          }
        }
        player.knownCards = updatedKnownCards;
      }
    }

    // Stay in toss-in phase
    return state;
  }

  /**
   * Apply pass (end toss-in)
   */
  private static applyPass(
    state: MCTSGameState,
    _move: MCTSMove
  ): MCTSGameState {
    // Exit toss-in phase
    state.isTossInPhase = false;

    // Advance turn
    state.currentPlayerIndex =
      (state.currentPlayerIndex + 1) % state.players.length;
    state.turnCount++;

    return state;
  }

  /**
   * Apply call Vinto
   */
  private static applyCallVinto(
    state: MCTSGameState,
    _move: MCTSMove
  ): MCTSGameState {
    // Game ends
    state.isTerminal = true;
    state.finalTurnTriggered = true;

    // Determine winner (player with lowest score)
    let lowestScore = Infinity;
    let winner = '';

    for (const player of state.players) {
      if (player.score < lowestScore) {
        lowestScore = player.score;
        winner = player.id;
      }
    }

    state.winner = winner;

    return state;
  }

  /**
   * Clone game state (deep copy)
   * Uses fast-copy library to ensure complete state isolation and prevent reference bugs
   */
  private static cloneState(state: MCTSGameState): MCTSGameState {
    // Use fast-copy for robust deep cloning
    const cloned = copy(state);

    // Important: Keep botMemory as a shared reference (intentional - it's shared state)
    cloned.botMemory = state.botMemory;

    return cloned;
  }

  /**
   * Check if state is terminal
   */
  static isTerminal(state: MCTSGameState): boolean {
    // Game ends if:
    // 1. Vinto was called
    if (state.isTerminal) return true;

    // 2. Any player has 0 cards
    if (state.players.some((p) => p.cardCount === 0)) {
      return true;
    }

    // 3. Too many turns (prevent infinite loops)
    if (state.turnCount > 200) {
      return true;
    }

    // 4. Deck is exhausted
    if (state.deckSize <= 0) {
      return true;
    }

    return false;
  }

  /**
   * Evaluate terminal state and determine winner
   */
  static evaluateTerminal(state: MCTSGameState): {
    winner: string;
    scores: Map<string, number>;
  } {
    const scores = new Map<string, number>();

    for (const player of state.players) {
      scores.set(player.id, player.score);
    }

    // Winner is player with lowest score
    let lowestScore = Infinity;
    let winner = '';

    for (const [playerId, score] of scores) {
      if (score < lowestScore) {
        lowestScore = score;
        winner = playerId;
      }
    }

    return { winner, scores };
  }

  /**
   * Calculate actual score for a player based on hidden cards
   */
  static calculatePlayerScore(state: MCTSGameState, playerId: string): number {
    const player = state.players.find((p) => p.id === playerId);
    if (!player) return 50;

    let score = 0;

    for (let pos = 0; pos < player.cardCount; pos++) {
      const card = state.hiddenCards.get(`${playerId}-${pos}`);
      if (card) {
        score += card.value;
      } else {
        // Unknown card - estimate as 6
        score += 6;
      }
    }

    return score;
  }

  /**
   * Update player score estimates in state
   */
  static updateScoreEstimates(state: MCTSGameState): void {
    for (const player of state.players) {
      player.score = this.calculatePlayerScore(state, player.id);
    }
  }

  /**
   * Advance to next player
   */
  static advanceToNextPlayer(state: MCTSGameState): void {
    state.currentPlayerIndex =
      (state.currentPlayerIndex + 1) % state.players.length;
    state.turnCount++;
  }

  /**
   * Check if move would end the game
   */
  static wouldMoveEndGame(state: MCTSGameState, move: MCTSMove): boolean {
    if (move.type === 'call-vinto') return true;

    // Check if toss-in would reduce player to 0 cards
    if (move.type === 'toss-in') {
      const player = state.players.find((p) => p.id === move.playerId);
      if (player && player.cardCount === 1) return true;
    }

    return false;
  }
}
