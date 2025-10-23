// services/mcts-move-generator.ts

import { CardAction, getCardAction, Rank } from '@vinto/shapes';
import { MCTSGameState, MCTSMove, MCTSActionTarget } from './mcts-types';

/**
 * Priority levels for card positions in swap generation
 */
enum SwapPriority {
  CRITICAL = 0, // Highest priority
  HIGH = 1,
  MEDIUM = 2,
  LOW = 3,
}

/**
 * Card position with strategic priority
 */
interface PrioritizedPosition {
  target: MCTSActionTarget;
  priority: SwapPriority;
  reason: string; // For debugging
  value?: number; // Card value if known
}

/**
 * Move generator for MCTS
 * Generates all legal moves from a given game state
 */
export class MCTSMoveGenerator {
  /**
   * Generate all possible moves from current state
   */
  static generateMoves(state: MCTSGameState): MCTSMove[] {
    const moves: MCTSMove[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];

    if (!currentPlayer) return moves;

    // If in toss-in phase, can toss matching cards or pass
    if (state.isTossInPhase) {
      moves.push({
        type: 'pass',
        playerId: currentPlayer.id,
      });

      // Collect ALL matching card positions for multi-card toss-in
      // Check if card matches ANY of the valid toss-in ranks
      const validRanks: Rank[] =
        state.tossInRanks ||
        (state.discardPileTop ? [state.discardPileTop.rank] : []);

      if (validRanks.length > 0) {
        const matchingPositions: number[] = [];

        for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
          // First check if the card is known from the player's memory
          const knownCardMemory = currentPlayer.knownCards.get(pos);
          const card =
            knownCardMemory?.card ||
            state.hiddenCards.get(`${currentPlayer.id}-${pos}`);

          // Include card if known to match ANY of the valid ranks
          if (card && validRanks.includes(card.rank)) {
            matchingPositions.push(pos);
          }
        }

        // Create a single toss-in move with ALL matching positions
        // This ensures all matching cards are tossed in simultaneously
        if (matchingPositions.length > 0) {
          moves.push({
            type: 'toss-in',
            playerId: currentPlayer.id,
            tossInPositions: matchingPositions,
          });
        }
      }
      return moves;
    }

    // If we have a pending action card (in awaiting_action phase), generate action target moves
    if (state.pendingCard && state.pendingCard.actionText) {
      const actionType = getCardAction(state.pendingCard.rank);
      if (actionType) {
        return this.generateActionMoves(state, actionType);
      }
    }

    // Always possible: draw from deck (if deck not empty)
    if (state.deckSize > 0) {
      moves.push({
        type: 'draw',
        playerId: currentPlayer.id,
      });
    }

    // Can take from discard ONLY if top card has unused action (7-K, A)
    if (
      state.discardPileTop &&
      state.discardPileTop.actionText &&
      !state.discardPileTop.played
    ) {
      moves.push({
        type: 'take-discard',
        playerId: currentPlayer.id,
        actionCard: state.discardPileTop,
      });
    }

    // Can call Vinto if late game and bot has low score
    // Note: This generates Vinto as a candidate move with threat assessment.
    // The actual validation (worst-case analysis) happens in shouldCallVinto() via VintoRoundSolver.
    if (state.turnCount >= state.players.length * 2) {
      const shouldGenerateVinto = this.assessVintoThreat(state);
      if (shouldGenerateVinto) {
        moves.push({
          type: 'call-vinto',
          playerId: currentPlayer.id,
        });
      }
    }

    return moves;
  }

  /**
   * Generate action-specific moves (peek, swap, etc.)
   */
  static generateActionMoves(
    state: MCTSGameState,
    actionType: CardAction
  ): MCTSMove[] {
    const moves: MCTSMove[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];

    if (!currentPlayer) return moves;

    switch (actionType) {
      case 'peek-own':
        // Generate peek moves for own cards that are UNKNOWN
        // Don't peek cards we already know about
        for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
          const memory = currentPlayer.knownCards.get(pos);
          const isKnown = memory && memory.confidence > 0.5;

          // Only peek unknown cards
          if (!isKnown) {
            moves.push({
              type: 'use-action',
              playerId: currentPlayer.id,
              targets: [{ playerId: currentPlayer.id, position: pos }],
            });
          }
        }
        break;

      case 'peek-opponent':
        // Generate peek moves for opponent cards that are UNKNOWN
        // Don't peek cards we already know about
        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          for (let pos = 0; pos < opponent.cardCount; pos++) {
            const memory = opponent.knownCards.get(pos);
            const isKnown = memory && memory.confidence > 0.5;

            // Only peek unknown cards
            if (!isKnown) {
              moves.push({
                type: 'use-action',
                playerId: currentPlayer.id,
                targets: [{ playerId: opponent.id, position: pos }],
              });
            }
          }
        }
        break;

      case 'swap-cards':
        // Generate strategically prioritized swap moves
        return this.generateStrategicSwapMoves(state);
        break;

      case 'peek-and-swap':
        // For Queen: peek two cards, then optionally swap
        // Use strategic selection for which cards to peek
        return this.generateStrategicPeekAndSwapMoves(state);
        break;

      case 'force-draw':
        // Force each opponent to draw
        // Ace action requires selecting a target player (position doesn't matter, any valid position will do)
        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          // Select position 0 as a valid placeholder (Ace action targets player, not specific card)
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [{ playerId: opponent.id, position: 0 }],
          });
        }
        break;

      case 'declare-action':
        // King: Select a card position to declare
        // The actual rank will be determined at declaration time by looking at current game state
        // This ensures we always declare what's actually at the position, not stale memory
        const kingMoves: MCTSMove[] = [];

        // Consider own cards that we know about
        for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
          const memory = currentPlayer.knownCards.get(pos);
          if (memory && memory.confidence > 0.5) {
            // We know there's an action card here - target it
            // Rank will be determined at declaration time
            kingMoves.push({
              type: 'use-action',
              playerId: currentPlayer.id,
              targets: [{ playerId: currentPlayer.id, position: pos }],
            });
          }
        }

        // Also consider opponent cards that we know about
        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          for (let pos = 0; pos < opponent.cardCount; pos++) {
            const memory = opponent.knownCards.get(pos);
            if (memory && memory.confidence > 0.5 && memory.card) {
              // We know this opponent card has an action rank - target it
              // Rank will be determined at declaration time
              kingMoves.push({
                type: 'use-action',
                playerId: currentPlayer.id,
                targets: [{ playerId: opponent.id, position: pos }],
              });
            }
          }
        }

        // Limit number of King moves to prevent explosion
        const MAX_KING_MOVES = 20;
        moves.push(...kingMoves.slice(0, MAX_KING_MOVES));
        break;
    }

    return moves;
  }

  /**
   * Generate card swap position moves (when drawn card needs to be swapped into hand)
   */
  static generateSwapPositionMoves(state: MCTSGameState): MCTSMove[] {
    const moves: MCTSMove[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];

    if (!currentPlayer) return moves;

    // Can discard instead of swapping
    moves.push({
      type: 'discard',
      playerId: currentPlayer.id,
    });

    // Can swap into any position
    for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
      moves.push({
        type: 'swap',
        playerId: currentPlayer.id,
        swapPosition: pos,
      });
    }

    return moves;
  }

  /**
   * Prune moves based on heuristics (to reduce branching factor)
   */
  static pruneMoves(state: MCTSGameState, moves: MCTSMove[]): MCTSMove[] {
    // For now, just limit total number of moves
    const MAX_MOVES = 20;

    if (moves.length <= MAX_MOVES) {
      return moves;
    }

    // Prioritize certain move types
    const priorityOrder: Record<string, number> = {
      'call-vinto': 1,
      'use-action': 2,
      'take-discard': 3,
      'toss-in': 4,
      draw: 5,
      swap: 6,
      discard: 7,
      pass: 8,
    };

    const sorted = moves.sort((a, b) => {
      const aPriority = priorityOrder[a.type] || 99;
      const bPriority = priorityOrder[b.type] || 99;
      return aPriority - bPriority;
    });

    return sorted.slice(0, MAX_MOVES);
  }

  /**
   * Get move priority for progressive widening
   */
  static getMovePriority(move: MCTSMove): number {
    const priorities: Record<string, number> = {
      'call-vinto': 100,
      'use-action': 80,
      'take-discard': 70,
      'toss-in': 60,
      draw: 50,
      swap: 40,
      discard: 30,
      pass: 10,
    };

    return priorities[move.type] || 50;
  }

  /**
   * Check if move is legal in current state
   */
  static isLegalMove(state: MCTSGameState, move: MCTSMove): boolean {
    // Basic legality checks
    const currentPlayer = state.players[state.currentPlayerIndex];

    if (!currentPlayer || move.playerId !== currentPlayer.id) {
      return false;
    }

    // Check toss-in phase restrictions
    if (state.isTossInPhase) {
      return move.type === 'toss-in' || move.type === 'pass';
    }

    // Check if swap position is valid
    if (move.type === 'swap' && move.swapPosition !== undefined) {
      return (
        move.swapPosition >= 0 && move.swapPosition < currentPlayer.cardCount
      );
    }

    // Check if targets are valid
    if (move.targets) {
      for (const target of move.targets) {
        const targetPlayer = state.players.find(
          (p) => p.id === target.playerId
        );
        if (!targetPlayer) return false;

        if (target.position >= targetPlayer.cardCount) return false;
      }
    }

    return true;
  }

  // ========== Strategic Move Generation ==========

  /**
   * Generate strategically prioritized swap moves for Jack action
   * Instead of random pairs, prioritize swaps based on known information
   */
  private static generateStrategicSwapMoves(state: MCTSGameState): MCTSMove[] {
    const moves: MCTSMove[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer) return moves;

    // Step 1: Categorize all positions by strategic value
    const prioritizedPositions = this.categorizePositionsForSwap(state);

    // Step 2: Generate high-priority swaps first
    const MAX_SWAP_MOVES = 30; // Reduced from 50 since we're being smarter

    // Critical swaps: My known high cards with opponent's unknown cards
    const myHighCards = prioritizedPositions.filter(
      (p) =>
        p.target.playerId === currentPlayer.id &&
        p.priority === SwapPriority.CRITICAL
    );
    const opponentUnknown = prioritizedPositions.filter(
      (p) =>
        p.target.playerId !== currentPlayer.id &&
        (p.priority === SwapPriority.HIGH || p.priority === SwapPriority.MEDIUM)
    );

    for (const myCard of myHighCards) {
      for (const oppCard of opponentUnknown) {
        if (moves.length >= MAX_SWAP_MOVES) break;
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppCard.target],
        });
      }
      if (moves.length >= MAX_SWAP_MOVES) break;
    }

    // High priority: My known high cards with opponent's known low cards
    const opponentLowCards = prioritizedPositions.filter(
      (p) =>
        p.target.playerId !== currentPlayer.id &&
        p.priority === SwapPriority.CRITICAL &&
        (p.value ?? 10) < 3 // Known low value
    );

    for (const myCard of myHighCards) {
      for (const oppCard of opponentLowCards) {
        if (moves.length >= MAX_SWAP_MOVES) break;
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppCard.target],
        });
      }
      if (moves.length >= MAX_SWAP_MOVES) break;
    }

    // Medium priority: Disrupt opponent - swap their cards with each other
    if (moves.length < MAX_SWAP_MOVES) {
      const opponent1Positions = prioritizedPositions.filter(
        (p) =>
          p.target.playerId !== currentPlayer.id &&
          p.priority !== SwapPriority.LOW
      );

      for (let i = 0; i < Math.min(opponent1Positions.length - 1, 5); i++) {
        for (
          let j = i + 1;
          j < Math.min(opponent1Positions.length, i + 3);
          j++
        ) {
          if (moves.length >= MAX_SWAP_MOVES) break;
          // Jack rule: Cannot swap two cards from the same player
          if (
            opponent1Positions[i].target.playerId !==
            opponent1Positions[j].target.playerId
          ) {
            moves.push({
              type: 'use-action',
              playerId: currentPlayer.id,
              targets: [
                opponent1Positions[i].target,
                opponent1Positions[j].target,
              ],
            });
          }
        }
        if (moves.length >= MAX_SWAP_MOVES) break;
      }
    }

    // Low priority: Exploratory swaps with unknown cards
    if (moves.length < MAX_SWAP_MOVES / 2) {
      // Only fill remaining space
      const myUnknown = prioritizedPositions.filter(
        (p) =>
          p.target.playerId === currentPlayer.id &&
          p.priority === SwapPriority.LOW
      );

      for (const myCard of myUnknown.slice(0, 3)) {
        for (const oppCard of opponentUnknown.slice(0, 3)) {
          if (moves.length >= MAX_SWAP_MOVES) break;
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [myCard.target, oppCard.target],
          });
        }
        if (moves.length >= MAX_SWAP_MOVES) break;
      }
    }

    return moves;
  }

  /**
   * Generate strategic peek-and-swap moves for Queen action
   * Prioritizes peeking unknown cards to gain maximum information
   */
  private static generateStrategicPeekAndSwapMoves(
    state: MCTSGameState
  ): MCTSMove[] {
    const moves: MCTSMove[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer) return moves;

    const prioritizedPositions = this.categorizePositionsForSwap(state);
    const MAX_PEEK_MOVES = 25; // Focused set of strategic peeks

    // Filter to only include UNKNOWN cards (for gaining information)
    // For Queen, we can optionally swap after peeking, so we might want to
    // peek known high cards to swap them, but prioritize unknown cards
    const unknownPositions = prioritizedPositions.filter((p) => {
      const player = state.players.find((pl) => pl.id === p.target.playerId);
      if (!player) return false;

      const memory = player.knownCards.get(p.target.position);
      const isKnown = memory && memory.confidence > 0.5;
      return !isKnown; // Only unknown cards
    });

    // Separate own and opponent positions (unknown only)
    const myUnknownPositions = unknownPositions.filter(
      (p) => p.target.playerId === currentPlayer.id
    );
    const opponentUnknownPositions = unknownPositions.filter(
      (p) => p.target.playerId !== currentPlayer.id
    );

    // Strategy 1: Peek two opponent unknown cards (maximize information gain)
    for (let i = 0; i < Math.min(opponentUnknownPositions.length - 1, 8); i++) {
      for (
        let j = i + 1;
        j < Math.min(opponentUnknownPositions.length, i + 5);
        j++
      ) {
        if (moves.length >= MAX_PEEK_MOVES) break;
        // Queen rule: Cannot peek two cards from the same player
        if (
          opponentUnknownPositions[i].target.playerId !==
          opponentUnknownPositions[j].target.playerId
        ) {
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [
              opponentUnknownPositions[i].target,
              opponentUnknownPositions[j].target,
            ],
          });
        }
      }
      if (moves.length >= MAX_PEEK_MOVES) break;
    }

    // Strategy 2: Peek own unknown and opponent unknown (for potential swap)
    if (moves.length < MAX_PEEK_MOVES) {
      for (const myCard of myUnknownPositions.slice(0, 5)) {
        for (const oppCard of opponentUnknownPositions.slice(0, 5)) {
          if (moves.length >= MAX_PEEK_MOVES) break;
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [myCard.target, oppCard.target],
          });
        }
        if (moves.length >= MAX_PEEK_MOVES) break;
      }
    }

    return moves;
  }

  /**
   * Categorize all card positions by strategic priority
   */
  private static categorizePositionsForSwap(
    state: MCTSGameState
  ): PrioritizedPosition[] {
    const positions: PrioritizedPosition[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer) return positions;

    for (const player of state.players) {
      const isBot = player.id === currentPlayer.id;

      for (let pos = 0; pos < player.cardCount; pos++) {
        const memory = player.knownCards.get(pos);
        const isKnown = memory && memory.confidence > 0.5 && memory.card;

        let priority: SwapPriority;
        let reason: string;
        let value: number | undefined;

        if (isBot) {
          // Bot's own cards
          if (isKnown && memory.card) {
            value = memory.card.value;
            if (value >= 8) {
              priority = SwapPriority.CRITICAL;
              reason = 'My known high card (want to swap out)';
            } else if (value <= 2 || value === -1) {
              priority = SwapPriority.LOW;
              reason = 'My known low card (keep)';
            } else {
              priority = SwapPriority.MEDIUM;
              reason = 'My known medium card';
            }
          } else {
            priority = SwapPriority.LOW;
            reason = 'My unknown card (uncertain value)';
          }
        } else {
          // Opponent cards
          if (isKnown && memory.card) {
            value = memory.card.value;
            if (value <= 2 || value === -1) {
              priority = SwapPriority.CRITICAL;
              reason = 'Opponent known low card (want to steal)';
            } else if (value >= 8) {
              priority = SwapPriority.LOW;
              reason = 'Opponent known high card (avoid)';
            } else {
              priority = SwapPriority.MEDIUM;
              reason = 'Opponent known medium card';
            }
          } else {
            priority = SwapPriority.HIGH;
            reason = 'Opponent unknown card (could be low)';
          }
        }

        positions.push({
          target: { playerId: player.id, position: pos },
          priority,
          reason,
          value,
        });
      }
    }

    // Sort by priority (lower number = higher priority)
    positions.sort((a, b) => a.priority - b.priority);

    return positions;
  }

  /**
   * Assess threats before generating Vinto move
   * Performs smart heuristic check considering opponent capabilities
   */
  private static assessVintoThreat(state: MCTSGameState): boolean {
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer) return false;

    // Step 1: Basic score check
    const botScore = currentPlayer.score;
    const opponentScores = state.players
      .filter((p) => p.id !== currentPlayer.id)
      .map((p) => p.score);
    const avgOpponentScore =
      opponentScores.reduce((a, b) => a + b, 0) / (opponentScores.length || 1);
    const minOpponentScore = Math.min(...opponentScores);

    // Base threshold: bot should have advantage
    const baseThreshold = 5; // Bot score should be <= avg - 5
    if (botScore > avgOpponentScore - baseThreshold) {
      return false; // Not competitive enough
    }

    // Step 2: Threat assessment - check if opponents have dangerous action cards
    let threatLevel = 0;
    const dangerousActions: CardAction[] = [
      'swap-cards', // Jack - can swap cards
      'peek-and-swap', // Queen - can peek and swap
      'declare-action', // King - can use any action
    ];

    for (const opponent of state.players) {
      if (opponent.id === currentPlayer.id) continue;

      // Check known cards for action cards
      for (const [, memory] of opponent.knownCards) {
        if (memory && memory.confidence > 0.5 && memory.card) {
          const action = getCardAction(memory.card.rank);
          if (action && dangerousActions.includes(action)) {
            threatLevel++;

            // Kings and Queens are especially dangerous
            if (action === 'declare-action') threatLevel += 2;
            if (action === 'peek-and-swap') threatLevel += 1;
          }
        }
      }
    }

    // Step 3: Adjust threshold based on threat level
    // If threats exist, require larger advantage
    if (threatLevel === 0) {
      // No known threats - use base threshold
      return botScore <= avgOpponentScore - baseThreshold;
    } else if (threatLevel <= 2) {
      // Low threat - slightly more conservative
      const adjustedThreshold = baseThreshold + 3;
      return botScore <= avgOpponentScore - adjustedThreshold;
    } else {
      // High threat (3+ dangerous cards) - very conservative
      const adjustedThreshold = baseThreshold + 5;
      const hasLargeAdvantage =
        botScore <= avgOpponentScore - adjustedThreshold;

      // Also check against best opponent
      const hasClearLead = botScore < minOpponentScore - 3;

      return hasLargeAdvantage && hasClearLead;
    }
  }
}
