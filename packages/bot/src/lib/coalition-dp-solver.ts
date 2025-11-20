/**
 * Dynamic Programming Coalition Round Solver
 *
 * Uses DP with memoization to find optimal action sequence for coalition members.
 * Much more efficient than brute force - computes each state only once.
 *
 * Complexity: O(states × actions) vs brute force O(actions^players)
 */

import { Card, Rank } from '@vinto/shapes';
import { calculateHandScore } from './mcts-bot-heuristics';

/**
 * Simplified game state for DP
 */
interface DPGameState {
  // Player hands (only cards that matter)
  playerHands: Map<string, Card[]>;

  // Available cards in draw pile (we know these from perfect knowledge)
  drawPile: Card[];

  // Current discard top (for toss-in opportunities)
  discardTop: Rank | null;

  // Whose turn is it (p2, p3, p4, p5)
  currentPlayerIndex: number;

  // Coalition member IDs in turn order
  playerOrder: string[];

  // Champion ID
  championId: string;
}

/**
 * Action a player can take
 */
interface DPAction {
  type: 'draw-swap' | 'draw-discard' | 'declare-cascade';

  // If draw-swap: which position to swap
  swapPosition?: number;

  // If declare-cascade: which rank to declare (for King)
  declareRank?: Rank;

  // Cards to toss in after discard
  tossInPositions?: number[];
}

/**
 * DP solution result
 */
interface DPSolution {
  championScore: number;
  actionSequence: DPAction[];
}

/**
 * Dynamic Programming Coalition Solver
 */
export class CoalitionDPSolver {
  // Memoization cache: stateHash -> best solution from that state
  private memo = new Map<string, DPSolution>();

  // Perfect knowledge of all coalition member cards
  private perfectKnowledge: Map<string, Map<number, Card>>;

  // Coalition member IDs
  private coalitionMembers: string[];

  // Champion ID
  private championId: string;

  // Vinto caller ID (excluded from coordination)
  private vintoCallerId: string;

  constructor(
    championId: string,
    vintoCallerId: string,
    coalitionMembers: string[],
    perfectKnowledge: Map<string, Map<number, Card>>
  ) {
    this.championId = championId;
    this.vintoCallerId = vintoCallerId;
    this.coalitionMembers = coalitionMembers;
    this.perfectKnowledge = perfectKnowledge;
  }

  /**
   * Find optimal action sequence using DP
   */
  findOptimalSequence(initialState: DPGameState): DPSolution {
    console.log(`[DP] Starting DP search from initial state`);
    this.memo.clear();

    const solution = this.dp(initialState);

    console.log(
      `[DP] Found optimal solution: championScore=${solution.championScore}, actions=${solution.actionSequence.length}`
    );
    console.log(`[DP] Memoization cache size: ${this.memo.size} states`);

    return solution;
  }

  /**
   * DP recursive function with memoization
   *
   * Returns best solution (minimum champion score) from given state
   */
  private dp(state: DPGameState): DPSolution {
    // Base case: all players have taken their turn
    if (state.currentPlayerIndex >= state.playerOrder.length) {
      const championHand = state.playerHands.get(this.championId) || [];
      const championScore = calculateHandScore(championHand);

      console.log(
        `[DP] Base case reached: championHand=[${championHand
          .map((c) => c.rank)
          .join(',')}], score=${championScore}`
      );

      return {
        championScore,
        actionSequence: [],
      };
    }

    // Check memoization cache
    const stateHash = this.hashState(state);
    const cached = this.memo.get(stateHash);
    if (cached) {
      return cached;
    }

    // Generate all possible actions for current player
    const currentPlayerId = state.playerOrder[state.currentPlayerIndex];
    const actions = this.generateActions(state, currentPlayerId);

    const currentHand = state.playerHands.get(currentPlayerId) || [];
    const isChampion = currentPlayerId === this.championId;
    console.log(
      `[DP] Player ${currentPlayerId}${
        isChampion ? ' (CHAMPION)' : ''
      } (index ${state.currentPlayerIndex}): hand=[${currentHand
        .map((c) => c.rank)
        .join(',')}], ${actions.length} actions, championId=${this.championId}`
    );

    // Try each action and find the one with minimum champion score
    let bestSolution: DPSolution = {
      championScore: Infinity,
      actionSequence: [],
    };

    for (const action of actions) {
      // Simulate action and get new state
      const newState = this.applyAction(state, currentPlayerId, action);

      const newChampionHand = newState.playerHands.get(this.championId) || [];
      const newPlayerHand = newState.playerHands.get(currentPlayerId) || [];

      // Recursively solve from new state
      const subSolution = this.dp(newState);

      console.log(
        `[DP]   Action ${action.type} ${
          action.swapPosition !== undefined ? `pos=${action.swapPosition}` : ''
        } ${action.declareRank ? `declare=${action.declareRank}` : ''} toss=[${
          action.tossInPositions?.join(',') || ''
        }] → playerHand=[${newPlayerHand
          .map((c) => c.rank)
          .join(',')}], championHand=[${newChampionHand
          .map((c) => c.rank)
          .join(',')}], score=${subSolution.championScore}`
      );

      // If this leads to better champion score, use it
      if (subSolution.championScore < bestSolution.championScore) {
        bestSolution = {
          championScore: subSolution.championScore,
          actionSequence: [action, ...subSolution.actionSequence],
        };
      }
    }

    console.log(
      `[DP] Player ${currentPlayerId} best: score=${bestSolution.championScore}`
    );

    // Memoize and return
    this.memo.set(stateHash, bestSolution);
    return bestSolution;
  }

  /**
   * Generate all valid actions for a player in current state
   */
  private generateActions(state: DPGameState, playerId: string): DPAction[] {
    const actions: DPAction[] = [];
    const hand = state.playerHands.get(playerId) || [];

    if (hand.length === 0) {
      return actions; // No cards, no actions
    }

    // Draw from pile is always available (if pile not empty)
    if (state.drawPile.length > 0) {
      const drawnCard = state.drawPile[0]; // Peek at top card

      // Option 1: Draw and swap into each position
      for (let pos = 0; pos < hand.length; pos++) {
        const action: DPAction = {
          type: 'draw-swap',
          swapPosition: pos,
          // Toss-ins should match the card being SWAPPED OUT (becomes new discard)
          tossInPositions: this.findTossInOpportunities(
            hand,
            hand[pos].rank,
            pos
          ),
        };
        actions.push(action);
      }

      // Option 2: Draw and discard (don't swap)
      const action: DPAction = {
        type: 'draw-discard',
        tossInPositions: this.findTossInOpportunities(hand, drawnCard.rank),
      };
      actions.push(action);
    }

    // Special: If player has King, can declare any rank in hand for cascade
    const hasKing = hand.some((c) => c.rank === 'K');
    if (hasKing) {
      // Count ranks in hand
      const rankCounts = new Map<Rank, number>();
      hand.forEach((card) => {
        rankCounts.set(card.rank, (rankCounts.get(card.rank) || 0) + 1);
      });

      // For each rank with 2+ cards, can declare it for cascade
      rankCounts.forEach((count, rank) => {
        if (count >= 2) {
          actions.push({
            type: 'declare-cascade',
            declareRank: rank,
            // Toss in ALL cards of this rank (cascade)
            tossInPositions: hand
              .map((c, i) => ({ card: c, index: i }))
              .filter(({ card }) => card.rank === rank)
              .map(({ index }) => index),
          });
        }
      });
    }

    return actions;
  }

  /**
   * Find which cards can be tossed in given the new discard
   */
  private findTossInOpportunities(
    hand: Card[],
    newDiscardRank: Rank | null,
    excludePosition?: number
  ): number[] {
    if (!newDiscardRank) return [];

    const tossIns: number[] = [];

    hand.forEach((card, pos) => {
      if (pos === excludePosition) return; // Card being swapped out
      if (card.rank === newDiscardRank) {
        tossIns.push(pos);
      }
    });

    return tossIns;
  }

  /**
   * Apply action to state and return new state
   */
  private applyAction(
    state: DPGameState,
    playerId: string,
    action: DPAction
  ): DPGameState {
    // Clone state
    const newPlayerHands = new Map<string, Card[]>();
    state.playerHands.forEach((hand, id) => {
      newPlayerHands.set(id, [...hand]);
    });

    const newDrawPile = [...state.drawPile];
    const playerHand = newPlayerHands.get(playerId) || [];

    if (playerHand.length === 0 && state.playerHands.has(playerId)) {
      console.log(
        `[DP BUG] Player ${playerId} has empty hand after cloning! Original had ${
          state.playerHands.get(playerId)?.length
        } cards`
      );
    }

    if (!newPlayerHands.has(playerId)) {
      console.log(
        `[DP BUG] Player ${playerId} not in newPlayerHands! Available: ${Array.from(
          newPlayerHands.keys()
        ).join(', ')}`
      );
    }

    let newDiscardTop = state.discardTop;

    if (action.type === 'draw-swap' && action.swapPosition !== undefined) {
      // Draw card from pile
      const drawnCard = newDrawPile.shift();
      if (!drawnCard) return state; // Should not happen

      // Swap into hand
      const swappedOut = playerHand[action.swapPosition];
      playerHand[action.swapPosition] = drawnCard;

      newDiscardTop = swappedOut.rank;

      // Apply toss-ins
      if (action.tossInPositions && action.tossInPositions.length > 0) {
        // Remove tossed-in cards (reverse order to maintain indices)
        const sorted = [...action.tossInPositions].sort((a, b) => b - a);
        sorted.forEach((pos) => {
          playerHand.splice(pos, 1);
        });
      }
    } else if (action.type === 'draw-discard') {
      // Draw and immediately discard
      const drawnCard = newDrawPile.shift();
      if (!drawnCard) return state;

      newDiscardTop = drawnCard.rank;

      // Apply toss-ins
      if (action.tossInPositions && action.tossInPositions.length > 0) {
        const sorted = [...action.tossInPositions].sort((a, b) => b - a);
        sorted.forEach((pos) => {
          playerHand.splice(pos, 1);
        });
      }
    } else if (action.type === 'declare-cascade' && action.declareRank) {
      // King declares rank, all matching cards are tossed in
      newDiscardTop = action.declareRank;

      // Remove all cards of that rank (cascade)
      if (action.tossInPositions && action.tossInPositions.length > 0) {
        const sorted = [...action.tossInPositions].sort((a, b) => b - a);
        sorted.forEach((pos) => {
          playerHand.splice(pos, 1);
        });
      }
    }

    newPlayerHands.set(playerId, playerHand);

    return {
      playerHands: newPlayerHands,
      drawPile: newDrawPile,
      discardTop: newDiscardTop,
      currentPlayerIndex: state.currentPlayerIndex + 1,
      playerOrder: state.playerOrder,
      championId: state.championId,
    };
  }

  /**
   * Hash state for memoization
   *
   * State is uniquely identified by:
   * - ALL players' hands (not just champion)
   * - Current player index
   * - Draw pile top cards
   * - Current discard top
   */
  private hashState(state: DPGameState): string {
    // Include ALL players' hands in hash (order matters for turn sequence)
    const allHandsHash = state.playerOrder
      .map((playerId) => {
        const hand = state.playerHands.get(playerId) || [];
        const cards = hand
          .map((c) => c.rank)
          .sort()
          .join(',');
        return `${playerId}:[${cards}]`;
      })
      .join('|');

    // Include draw pile (all cards matter for exact state)
    const drawPileHash = state.drawPile.map((c) => c.rank).join(',');

    // Include discard top (affects toss-in opportunities)
    const discardHash = state.discardTop || 'none';

    return `idx=${state.currentPlayerIndex}|hands=${allHandsHash}|draw=${drawPileHash}|discard=${discardHash}`;
  }
}
