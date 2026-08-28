// services/mcts-state-transition.ts
import { Card, Rank } from '@vinto/shapes';
import { copy } from 'fast-copy';
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
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer || !state.pendingCard) return state;

    const actionCard = state.pendingCard;

    // Execute the action effect
    this.applyActionEffect(state, move, actionCard);

    // Action card goes to discard pile (played)
    const discardedCard = { ...actionCard, played: true };
    state.discardPileTop = discardedCard;
    state.pendingCard = null;

    // Simulate toss-in cascade
    const { tossedCards, finalState } = this.simulateTossInCascade(
      state,
      actionCard.rank,
      currentPlayer.id
    );

    console.log(
      `[MCTS Transition] Use ${actionCard.rank} action, ` +
        `toss-in ${tossedCards.length} matching cards`
    );

    // Advance turn
    finalState.currentPlayerIndex =
      (finalState.currentPlayerIndex + 1) % finalState.players.length;
    finalState.turnCount++;
    finalState.isTossInPhase = false;
    finalState.tossInRanks = undefined;

    return finalState;
  }

  /**
   * Apply swap into hand
   */
  private static applySwap(
    state: MCTSGameState,
    move: MCTSMove
  ): MCTSGameState {
    if (move.swapPosition === undefined) return state;

    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer) return state;

    // Step 1: Execute the swap
    const oldCard = state.hiddenCards.get(
      `${currentPlayer.id}-${move.swapPosition}`
    );
    const newCard = state.pendingCard;

    if (!oldCard || !newCard) return state;

    // Replace card in hand
    state.hiddenCards.set(`${currentPlayer.id}-${move.swapPosition}`, newCard);

    // Update player state
    currentPlayer.knownCards.set(move.swapPosition, {
      card: newCard,
      confidence: 1.0
    });

    // Card that was swapped out goes to discard
    const discardedCard = oldCard;
    state.discardPileTop = { ...discardedCard, played: false };
    state.pendingCard = null;

    // CRITICAL: Step 2 - Simulate toss-in cascade
    const tossInRank = discardedCard.rank;
    const { tossedCards, finalState } = this.simulateTossInCascade(
      state,
      tossInRank,
      currentPlayer.id
    );

    console.log(
      `[MCTS Transition] Swap at [${move.swapPosition}]: ` +
        `${oldCard.rank}(${oldCard.value}) → ${newCard.rank}(${newCard.value}), ` +
        `then toss-in ${tossedCards.length} ${tossInRank}s`
    );

    // Advance turn after toss-in completes
    finalState.currentPlayerIndex =
      (finalState.currentPlayerIndex + 1) % finalState.players.length;
    finalState.turnCount++;
    finalState.isTossInPhase = false;
    finalState.tossInRanks = undefined;

    return finalState;
  }

  /**
   * Apply discard move (drawn card discarded without swapping)
   */
  private static applyDiscard(
    state: MCTSGameState,
    _move: MCTSMove
  ): MCTSGameState {
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer || !state.pendingCard) return state;

    const discardedCard = state.pendingCard;
    state.discardPileTop = { ...discardedCard, played: false };
    state.pendingCard = null;

    // Simulate toss-in cascade
    const { tossedCards, finalState } = this.simulateTossInCascade(
      state,
      discardedCard.rank,
      currentPlayer.id
    );

    console.log(
      `[MCTS Transition] Discard ${discardedCard.rank}, ` +
        `toss-in ${tossedCards.length} matching cards`
    );

    // Advance turn
    finalState.currentPlayerIndex =
      (finalState.currentPlayerIndex + 1) % finalState.players.length;
    finalState.turnCount++;
    finalState.isTossInPhase = false;
    finalState.tossInRanks = undefined;

    return finalState;
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

  /**
   * CORE ALGORITHM: Simulate toss-in cascade
   *
   * This recursively finds ALL cards that match the discarded rank
   * and removes them from ALL players' hands
   *
   * Example:
   * - Bot discards 7
   * - Bot has two more 7s → toss them in (3 total 7s removed)
   * - Bot also has a King → declare King action
   * - King declares another 7 → triggers ANOTHER toss-in cascade!
   */
  private static simulateTossInCascade(
    state: MCTSGameState,
    discardedRank: Rank,
    _currentPlayerId: string
  ): { tossedCards: Card[]; finalState: MCTSGameState } {
    const tossedCards: Card[] = [];

    // Check each player for matching cards
    for (const player of state.players) {
      const cardsToRemove: number[] = [];

      // Find all matching cards this player knows about
      for (let pos = 0; pos < player.cardCount; pos++) {
        const memory = player.knownCards.get(pos);

        // Only toss in cards we KNOW about
        if (memory && memory.confidence > 0.5 && memory.card) {
          if (memory.card.rank === discardedRank) {
            cardsToRemove.push(pos);
            tossedCards.push(memory.card);
          }
        }
      }

      // Remove tossed cards from player's hand
      if (cardsToRemove.length > 0) {
        // Update card count
        player.cardCount -= cardsToRemove.length;

        // Update score (remove value of tossed cards)
        const scoreReduction = cardsToRemove.reduce((sum, pos) => {
          const card = state.hiddenCards.get(`${player.id}-${pos}`);
          return sum + (card?.value || 0);
        }, 0);
        player.score -= scoreReduction;

        // Remove cards from hiddenCards map
        cardsToRemove.forEach((pos) => {
          state.hiddenCards.delete(`${player.id}-${pos}`);
          player.knownCards.delete(pos);
        });

        // Reindex remaining cards (shift down)
        const newHiddenCards = new Map<string, Card>();
        const newKnownCards = new Map<number, any>();

        let newPos = 0;
        for (
          let oldPos = 0;
          oldPos < player.cardCount + cardsToRemove.length;
          oldPos++
        ) {
          if (!cardsToRemove.includes(oldPos)) {
            const card = state.hiddenCards.get(`${player.id}-${oldPos}`);
            if (card) {
              newHiddenCards.set(`${player.id}-${newPos}`, card);
            }

            const memory = player.knownCards.get(oldPos);
            if (memory) {
              newKnownCards.set(newPos, memory);
            }

            newPos++;
          }
        }

        // Update maps
        for (
          let pos = 0;
          pos < player.cardCount + cardsToRemove.length;
          pos++
        ) {
          state.hiddenCards.delete(`${player.id}-${pos}`);
        }

        newHiddenCards.forEach((card, key) => {
          state.hiddenCards.set(key, card);
        });

        player.knownCards = newKnownCards;
      }
    }

    return { tossedCards, finalState: state };
  }

  /**
   * Apply action card effects (peek, swap, etc.)
   */
  private static applyActionEffect(
    state: MCTSGameState,
    move: MCTSMove,
    actionCard: Card
  ): void {
    const rank = actionCard.rank;

    if (!move.targets || move.targets.length === 0) return;

    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer) return;

    switch (rank) {
      case '7':
      case '8':
        // Peek own card - update knowledge
        const target = move.targets[0];
        const card = state.hiddenCards.get(
          `${target.playerId}-${target.position}`
        );
        if (card) {
          currentPlayer.knownCards.set(target.position, {
            card,
            confidence: 1.0,
          });
        }
        break;

      case '9':
      case '10':
        // Peek opponent card - update bot's knowledge of opponent
        const oppTarget = move.targets[0];
        const oppCard = state.hiddenCards.get(
          `${oppTarget.playerId}-${oppTarget.position}`
        );
        if (oppCard) {
          const oppPlayer = state.players.find(
            (p) => p.id === oppTarget.playerId
          );
          if (oppPlayer) {
            oppPlayer.knownCards.set(oppTarget.position, {
              card: oppCard,
              confidence: 1.0,
            });
          }
        }
        break;

      case 'J':
        // Jack: Swap two cards
        if (move.targets.length >= 2) {
          const [target1, target2] = move.targets;
          const card1 = state.hiddenCards.get(
            `${target1.playerId}-${target1.position}`
          );
          const card2 = state.hiddenCards.get(
            `${target2.playerId}-${target2.position}`
          );

          if (card1 && card2) {
            // Swap in hidden cards
            state.hiddenCards.set(
              `${target1.playerId}-${target1.position}`,
              card2
            );
            state.hiddenCards.set(
              `${target2.playerId}-${target2.position}`,
              card1
            );

            // Update knowledge for bot
            const player1 = state.players.find(
              (p) => p.id === target1.playerId
            );
            const player2 = state.players.find(
              (p) => p.id === target2.playerId
            );

            if (player1) {
              player1.knownCards.set(target1.position, {
                card: card2,
                confidence: 1.0,
              });
            }

            if (player2) {
              player2.knownCards.set(target2.position, {
                card: card1,
                confidence: 1.0,
              });
            }

            // Update scores
            if (player1) {
              player1.score = player1.score - card1.value + card2.value;
            }
            if (player2) {
              player2.score = player2.score - card2.value + card1.value;
            }
          }
        }
        break;

      case 'Q':
        // Queen: Peek 2, optionally swap
        if (move.targets.length >= 2) {
          const [target1, target2] = move.targets;
          const card1 = state.hiddenCards.get(
            `${target1.playerId}-${target1.position}`
          );
          const card2 = state.hiddenCards.get(
            `${target2.playerId}-${target2.position}`
          );

          // Update knowledge
          const player1 = state.players.find((p) => p.id === target1.playerId);
          const player2 = state.players.find((p) => p.id === target2.playerId);

          if (card1 && player1) {
            player1.knownCards.set(target1.position, {
              card: card1,
              confidence: 1.0,
            });
          }

          if (card2 && player2) {
            player2.knownCards.set(target2.position, {
              card: card2,
              confidence: 1.0,
            });
          }

          // If shouldSwap is true, execute the swap
          if (move.shouldSwap && card1 && card2) {
            state.hiddenCards.set(
              `${target1.playerId}-${target1.position}`,
              card2
            );
            state.hiddenCards.set(
              `${target2.playerId}-${target2.position}`,
              card1
            );

            if (player1) {
              player1.score = player1.score - card1.value + card2.value;
            }
            if (player2) {
              player2.score = player2.score - card2.value + card1.value;
            }
          }
        }
        break;

      case 'K':
        // King: Declare rank (triggers toss-in for declared rank)
        if (move.declaredRank) {
          // This triggers a SECOND toss-in cascade in the caller
          // For now, just update knowledge
          const target = move.targets[0];
          const card = state.hiddenCards.get(
            `${target.playerId}-${target.position}`
          );
          if (card) {
            currentPlayer.knownCards.set(target.position, {
              card,
              confidence: 1.0,
            });
          }
        }
        break;

      case 'A':
        // Ace: Force opponent to draw (increase their hand size/score)
        const oppId = move.targets[0].playerId;
        const opponent = state.players.find((p) => p.id === oppId);
        if (opponent) {
          // Simulate opponent drawing a card (estimate value)
          opponent.cardCount++;
          opponent.score += 5; // Estimate average card value
        }
        break;
    }
  }
}
