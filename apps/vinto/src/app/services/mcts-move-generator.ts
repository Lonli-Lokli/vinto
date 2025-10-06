// services/mcts-move-generator.ts
import { Rank } from '../shapes';
import { MCTSGameState, MCTSMove, MCTSActionTarget } from './mcts-types';

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

      // Generate toss-in moves for matching cards
      const discardRank = state.discardPileTop?.rank;
      if (discardRank) {
        for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
          const card = state.hiddenCards.get(`${currentPlayer.id}-${pos}`);
          // Include card if known to match OR unknown (uncertainty about matching)
          if (card && card.rank === discardRank) {
            moves.push({
              type: 'toss-in',
              playerId: currentPlayer.id,
              tossInPosition: pos,
            });
          }
        }
      }
      return moves;
    }

    // Always possible: draw from deck (if deck not empty)
    if (state.deckSize > 0) {
      moves.push({
        type: 'draw',
        playerId: currentPlayer.id,
      });
    }

    // If discard pile has action card, can take it
    if (state.discardPileTop && state.discardPileTop.action) {
      moves.push({
        type: 'take-discard',
        playerId: currentPlayer.id,
        actionCard: state.discardPileTop,
      });
    }

    // If discard pile has non-action card, can take it too (but less desirable)
    if (state.discardPileTop && !state.discardPileTop.action) {
      moves.push({
        type: 'take-discard',
        playerId: currentPlayer.id,
        actionCard: state.discardPileTop,
      });
    }

    // Can call Vinto if late game and bot has low score
    if (state.turnCount >= state.players.length * 2) {
      const botScore = currentPlayer.score;
      const opponentScores = state.players
        .filter((p) => p.id !== currentPlayer.id)
        .map((p) => p.score);
      const avgOpponentScore =
        opponentScores.reduce((a, b) => a + b, 0) / opponentScores.length;

      // Only consider Vinto if bot's score is competitive
      if (botScore <= avgOpponentScore + 5) {
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
    actionType: string
  ): MCTSMove[] {
    const moves: MCTSMove[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];

    if (!currentPlayer) return moves;

    switch (actionType) {
      case 'peek-own':
        // Generate peek moves for each own card
        for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [{ playerId: currentPlayer.id, position: pos }],
          });
        }
        break;

      case 'peek-opponent':
        // Generate peek moves for each opponent card
        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          for (let pos = 0; pos < opponent.cardCount; pos++) {
            moves.push({
              type: 'use-action',
              playerId: currentPlayer.id,
              targets: [{ playerId: opponent.id, position: pos }],
            });
          }
        }
        break;

      case 'swap':
      case 'blind-swap':
        // Generate swap moves (any two cards)
        const allPositions: MCTSActionTarget[] = [];

        for (const player of state.players) {
          for (let pos = 0; pos < player.cardCount; pos++) {
            allPositions.push({ playerId: player.id, position: pos });
          }
        }

        // Generate pairs (limit to reasonable subset to avoid combinatorial explosion)
        const maxSwapMoves = 50;
        for (let i = 0; i < Math.min(allPositions.length - 1, 10); i++) {
          for (let j = i + 1; j < Math.min(allPositions.length, i + 10); j++) {
            if (moves.length >= maxSwapMoves) break;

            // Don't swap card with itself
            if (
              allPositions[i].playerId === allPositions[j].playerId &&
              allPositions[i].position === allPositions[j].position
            ) {
              continue;
            }

            moves.push({
              type: 'use-action',
              playerId: currentPlayer.id,
              targets: [allPositions[i], allPositions[j]],
            });
          }
        }
        break;

      case 'peek-and-swap':
        // For Queen: peek two cards, then optionally swap
        // Simplified: just peek random pairs
        const opponentPositions: MCTSActionTarget[] = [];

        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          for (let pos = 0; pos < opponent.cardCount; pos++) {
            opponentPositions.push({ playerId: opponent.id, position: pos });
          }
        }

        // Generate peek pairs (limit to avoid explosion)
        const maxPeekSwaps = 30;
        for (let i = 0; i < Math.min(opponentPositions.length - 1, 10); i++) {
          for (
            let j = i + 1;
            j < Math.min(opponentPositions.length, i + 5);
            j++
          ) {
            if (moves.length >= maxPeekSwaps) break;

            moves.push({
              type: 'use-action',
              playerId: currentPlayer.id,
              targets: [opponentPositions[i], opponentPositions[j]],
            });
          }
        }
        break;

      case 'force-draw':
        // Force each opponent to draw
        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [{ playerId: opponent.id, position: -1 }],
          });
        }
        break;

      case 'declare':
        // King: declare each possible action rank
        const actionRanks: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'A'];

        for (const rank of actionRanks) {
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            declaredRank: rank,
          });
        }
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
}
