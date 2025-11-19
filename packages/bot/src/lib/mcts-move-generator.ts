// services/mcts-move-generator.ts

import {
  Card,
  CardAction,
  getCardAction,
  isRankActionable,
  Rank,
} from '@vinto/shapes';
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
   * COALITION HELPER: Identify the coalition champion (member with best chance to win)
   * Returns player with lowest score (excluding Vinto caller)
   */
  private static getCoalitionChampion(state: MCTSGameState): string | null {
    if (!state.vintoCallerId || !state.coalitionLeaderId) return null;

    const coalitionMembers = state.players.filter(
      (p) => p.id !== state.vintoCallerId
    );

    if (coalitionMembers.length === 0) return null;

    let champion = coalitionMembers[0];
    for (const member of coalitionMembers) {
      if (member.score < champion.score) {
        champion = member;
      }
    }

    console.log(
      `[Coalition] Champion identified: ${champion.id} (score: ${champion.score})`
    );
    return champion.id;
  }

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
    if (state.pendingCard && isRankActionable(state.pendingCard.rank)) {
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
        // COALITION: Don't target Vinto caller if bot is in coalition
        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          // COALITION FILTER: Skip Vinto caller if bot is coalition member
          // Game rule: "no one may interact with the Vinto caller's cards" during final round
          const isCoalitionMember =
            state.vintoCallerId &&
            state.coalitionLeaderId &&
            currentPlayer.id !== state.vintoCallerId;
          if (isCoalitionMember && opponent.id === state.vintoCallerId) {
            continue; // Coalition cannot peek/interact with Vinto caller's cards (game rule)
          }

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
        // GAME RULE: "no one may interact with the Vinto caller's cards" during final round
        const currentPlayerIsInCoalition =
          state.vintoCallerId &&
          state.coalitionLeaderId &&
          currentPlayer.id !== state.vintoCallerId;

        // COALITION COORDINATION: Don't use Ace at all if in coalition!
        // Using Ace on ANY coalition member is harmful (makes them draw and increase score)
        // Using Ace on Vinto caller violates game rules
        // Result: Coalition members should NEVER generate Ace moves - swap or discard instead
        if (currentPlayerIsInCoalition) {
          console.log(
            `[Coalition Ace] Skipping ALL Ace moves - harmful to coalition, should swap/discard instead`
          );
          break; // Generate NO moves for Ace when in coalition
        }

        // Normal mode (not in coalition): Can target all opponents
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
        // King: Use improved strategy
        const kingMoves = this.generateImprovedKingMoves(
          state,
          currentPlayer.id
        );
        moves.push(...kingMoves);
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
   * IMPROVED King Declaration Strategy
   *
   * King can declare ANY card (own or opponent's) to trigger toss-in cascade for that rank.
   *
   * Strategy priorities:
   * 1. OWN TOSS-IN CASCADES: Declare ranks we have 2+ of (removes multiple OUR cards at once)
   * 2. OWN HIGH VALUE REMOVAL: Declare our single high-value cards (10, J, Q)
   * 3. OPPONENT HARASSMENT: Declare opponent's high-value cards (force them to toss in, hurt them)
   * 4. COALITION ATTACK: If in coalition, declare Vinto caller's high cards to harm them
   *
   * Coalition rules:
   * - Coalition members should prioritize declaring Vinto caller's cards (harm enemy)
   * - Coalition members should avoid declaring own or other coalition members' cards (friendly fire)
   */
  static generateImprovedKingMoves(
    state: MCTSGameState,
    currentPlayerId: string
  ): MCTSMove[] {
    const moves: MCTSMove[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer || currentPlayer.id !== currentPlayerId) return moves;

    // Check coalition status
    const isInCoalition =
      state.vintoCallerId &&
      state.coalitionLeaderId &&
      currentPlayer.id !== state.vintoCallerId;

    console.log(
      `[King Generator] Player ${currentPlayerId} has ${currentPlayer.cardCount} cards, ${currentPlayer.knownCards.size} known cards in memory, coalition: ${isInCoalition}`
    );

    // Build map: rank → card locations (playerId, position)
    type CardLocation = { playerId: string; position: number; card: Card };
    const rankMap = new Map<Rank, CardLocation[]>();

    // Collect bot's own known cards
    for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
      const memory = currentPlayer.knownCards.get(pos);

      if (memory && memory.confidence > 0.5 && memory.card) {
        const card = memory.card;
        const rank = card.rank;
        const locations = rankMap.get(rank) || [];
        locations.push({ playerId: currentPlayer.id, position: pos, card });
        rankMap.set(rank, locations);

        console.log(
          `[King Generator] Own card [${pos}]: ${rank} (confidence: ${memory.confidence.toFixed(
            2
          )})`
        );
      }
    }

    // Collect opponent's known cards
    for (const opponent of state.players) {
      if (opponent.id === currentPlayer.id) continue;

      // COALITION FILTER: Skip Vinto caller (cannot interact with their cards - game rule)
      // Also skip other coalition members (strategic - they're allies)
      if (isInCoalition) {
        continue; // Coalition cannot declare ANY opponent cards (Vinto caller = game rule, others = strategic)
      }

      for (let pos = 0; pos < opponent.cardCount; pos++) {
        const memory = opponent.knownCards.get(pos);

        if (memory && memory.confidence > 0.5 && memory.card) {
          const card = memory.card;
          const rank = card.rank;
          const locations = rankMap.get(rank) || [];
          locations.push({ playerId: opponent.id, position: pos, card });
          rankMap.set(rank, locations);

          console.log(
            `[King Generator] Opponent ${opponent.id} card [${pos}]: ${rank}`
          );
        }
      }
    }

    console.log(
      `[King Generator] Found ${rankMap.size} distinct ranks across all known cards`
    );

    // Calculate strategic value for each possible King declaration
    const declarations: Array<{
      playerId: string;
      position: number;
      rank: Rank;
      strategicValue: number;
      reason: string;
    }> = [];

    // Evaluate each rank we could declare
    rankMap.forEach((locations, rank) => {
      // Analyze the locations for this rank
      const ownLocations = locations.filter(
        (loc) => loc.playerId === currentPlayer.id
      );
      const opponentLocations = locations.filter(
        (loc) => loc.playerId !== currentPlayer.id
      );

      const card = locations[0].card; // Get card properties (value, etc.)

      console.log(
        `[King Generator] Evaluating ${rank}: ${ownLocations.length} own, ${opponentLocations.length} opponent`
      );

      // COALITION COORDINATION: If champion exists and has this rank, prioritize declaring it
      const championId = this.getCoalitionChampion(state);
      const championHasRank = locations.some(
        (loc) => loc.playerId === championId
      );

      // Strategy: STRONGLY prefer declaring OWN cards
      // COALITION: Also prioritize declaring champion's high cards to help them
      // Only declare opponent cards in very specific beneficial cases:
      // 1. Coalition attack on Vinto caller (worth the risk)
      // 2. We have MORE of that rank than opponents (we benefit more from cascade)
      let targetLocation: CardLocation;
      let strategicValue = 0;
      let reason = '';

      // COALITION SPECIAL CASE: If champion has this rank and it's high value, strongly favor declaring
      if (
        championId &&
        championHasRank &&
        currentPlayer.id !== championId &&
        card.value >= 9
      ) {
        // High priority: Remove champion's high-value cards
        for (const location of locations) {
          if (location.playerId === championId) {
            declarations.push({
              playerId: location.playerId,
              position: location.position,
              rank,
              strategicValue: 90 + card.value, // Very high priority
              reason: `COALITION: Remove champion's ${rank} (value ${card.value})`,
            });
            console.log(
              `[Coalition King] HIGH PRIORITY - Remove champion ${championId}'s ${rank} (value ${card.value})`
            );
          }
        }
      }

      if (ownLocations.length >= 2) {
        // OWN CASCADE: Multiple own cards (ALWAYS prioritize this)
        targetLocation = ownLocations[0];
        const cascadeValue = ownLocations.reduce(
          (sum, loc) => sum + loc.card.value,
          0
        );
        strategicValue += cascadeValue * 10; // HUGE multiplier for own cascades
        reason = `OWN CASCADE: Remove ${ownLocations.length} ${rank}s (${cascadeValue} points)`;

        if (rank === 'K') {
          strategicValue += 50; // Bonus for King cascades
          reason += ' [KING CASCADE!]';
        }

        // Bonus if opponents also toss in
        if (opponentLocations.length > 0) {
          strategicValue += opponentLocations.length * 15;
          reason += ` [+${opponentLocations.length} opp]`;
        }
      } else if (ownLocations.length === 1 && opponentLocations.length === 0) {
        // SINGLE OWN CARD, no opponents have it: Good for high-value cards
        targetLocation = ownLocations[0];
        strategicValue += card.value * 3;
        reason = `Remove own ${rank} (${card.value} points)`;

        if (card.value >= 10) {
          strategicValue += 20; // Extra bonus for high cards
          reason += ` [HIGH VALUE]`;
        }

        // Penalty for action cards (might want to keep them)
        if (rank === '7' || rank === '8' || rank === 'Q' || rank === 'J') {
          strategicValue -= 5;
          reason += ` [action card]`;
        }
      } else if (ownLocations.length === 1 && opponentLocations.length > 0) {
        // Note: Coalition members won't reach this point (no opponent cards collected due to filter above)
        // OWN + OPPONENT have same rank: Only declare if beneficial
        // This is RARELY good because it helps opponent reduce hand
        // Only do it if opponents have MORE of this rank (they lose more)
        if (opponentLocations.length > 1) {
          targetLocation = ownLocations[0];
          const netBenefit =
            opponentLocations.reduce((sum, loc) => sum + loc.card.value, 0) -
            card.value;
          strategicValue += netBenefit * 2; // Lower multiplier (risky move)
          reason = `Cascade: We lose 1, opponents lose ${opponentLocations.length} (net +${netBenefit})`;
        } else {
          // Equal count - DON'T declare (helps opponent as much as us)
          return;
        }
      } else {
        // No own cards, only opponent cards - SKIP THIS
        // Declaring opponent-only ranks helps them, not us!
        return;
      }

      declarations.push({
        playerId: targetLocation.playerId,
        position: targetLocation.position,
        rank,
        strategicValue,
        reason,
      });
    });

    // Sort by strategic value (highest first)
    declarations.sort((a, b) => b.strategicValue - a.strategicValue);

    // Generate moves for top declarations (limit to top 5 to avoid too many branches)
    declarations.slice(0, 5).forEach((decl) => {
      console.log(
        `[King Strategy] ${decl.rank} @ [${decl.position}]: ` +
          `value=${decl.strategicValue.toFixed(0)} | ${decl.reason}`
      );

      moves.push({
        type: 'use-action',
        playerId: currentPlayer.id,
        targets: [{ playerId: decl.playerId, position: decl.position }],
        declaredRank: decl.rank,
      });
    });

    return moves;
  }

  /**
   * Prune moves based on heuristics (to reduce branching factor)
   *
   * NOTE: With strategic move generation, we no longer need arbitrary pruning.
   * All generated moves have strategic value. Let MCTS explore them all.
   */
  static pruneMoves(state: MCTSGameState, moves: MCTSMove[]): MCTSMove[] {
    // Return all moves - strategic generation already filters useless moves
    // King declarations filter opponent cards if not coalition
    // Jack/Queen swaps prioritize high-value combinations
    // Peek actions only target unknown cards
    return moves;
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

    // COALITION COORDINATION: Identify champion if in coalition mode
    const championId = this.getCoalitionChampion(state);
    const isCoordinatingForChampion =
      championId && championId !== currentPlayer.id;

    // Step 1: Categorize all positions by strategic value
    // NOTE: categorizePositionsForSwap already filters out Vinto caller for coalition members
    const prioritizedPositions = this.categorizePositionsForSwap(state);

    // Step 2: Generate ALL strategic swaps prioritized by value
    // COALITION: If we're not the champion, prioritize moves that help champion

    const myCards = prioritizedPositions.filter(
      (p) => p.target.playerId === currentPlayer.id
    );

    // COALITION PRIORITY 0: Give champion our Jokers (if we have them and champion doesn't)
    if (isCoordinatingForChampion) {
      const myJokers = prioritizedPositions.filter(
        (p) => p.target.playerId === currentPlayer.id && p.value === -1
      );
      const championCards = prioritizedPositions.filter(
        (p) => p.target.playerId === championId
      );

      for (const myJoker of myJokers) {
        for (const champCard of championCards) {
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [myJoker.target, champCard.target],
            shouldSwap: true,
          });
          console.log(
            `[Coalition] Generated move: Give Joker to champion ${championId}`
          );
        }
      }
    }

    // PRIORITY 1: Steal opponent's Jokers (value = -1, best card in game)
    const opponentJokers = prioritizedPositions.filter(
      (p) =>
        p.target.playerId !== currentPlayer.id &&
        p.target.playerId !== championId && // Don't steal from champion!
        p.value === -1 // Joker
    );

    // COALITION: If champion exists, steal Jokers FOR the champion
    if (isCoordinatingForChampion) {
      const championCards = prioritizedPositions.filter(
        (p) => p.target.playerId === championId
      );

      for (const champCard of championCards) {
        for (const joker of opponentJokers) {
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [champCard.target, joker.target],
            shouldSwap: true,
          });
          console.log(
            `[Coalition] Generated move: Steal Joker FOR champion ${championId}`
          );
        }
      }
    }

    // Normal mode: Steal Jokers for ourselves
    for (const myCard of myCards) {
      for (const joker of opponentJokers) {
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, joker.target],
          shouldSwap: true,
        });
      }
    }

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
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppCard.target],
          shouldSwap: true,
        });
      }
    }

    // High priority: My known high cards with opponent's known low cards (excluding Jokers)
    const opponentLowCards = prioritizedPositions.filter(
      (p) =>
        p.target.playerId !== currentPlayer.id &&
        p.priority === SwapPriority.CRITICAL &&
        p.value !== -1 && // Exclude Jokers (already prioritized above)
        (p.value ?? 10) < 3 // Known low value (2s, etc.)
    );

    for (const myCard of myHighCards) {
      for (const oppCard of opponentLowCards) {
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppCard.target],
          shouldSwap: true,
        });
      }
    }

    // Medium priority: Disrupt opponent - swap their cards with each other
    // Limited to top 5 positions to avoid combinatorial explosion
    const opponent1Positions = prioritizedPositions.filter(
      (p) =>
        p.target.playerId !== currentPlayer.id &&
        p.priority !== SwapPriority.LOW
    );

    for (let i = 0; i < Math.min(opponent1Positions.length - 1, 5); i++) {
      for (let j = i + 1; j < Math.min(opponent1Positions.length, i + 3); j++) {
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
            shouldSwap: true,
          });
        }
      }
    }

    // Low priority: Limited exploratory swaps with unknown cards
    // These are lower value, so limit to avoid too many exploratory moves
    const myUnknown = prioritizedPositions.filter(
      (p) =>
        p.target.playerId === currentPlayer.id &&
        p.priority === SwapPriority.LOW
    );

    for (const myCard of myUnknown.slice(0, 3)) {
      for (const oppCard of opponentUnknown.slice(0, 3)) {
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppCard.target],
          shouldSwap: true,
        });
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

    // COALITION COORDINATION: Identify champion
    const championId = this.getCoalitionChampion(state);
    const isCoordinatingForChampion =
      championId && championId !== currentPlayer.id;

    // NOTE: categorizePositionsForSwap already filters out Vinto caller for coalition members
    const prioritizedPositions = this.categorizePositionsForSwap(state);

    // COALITION STRATEGY 0a: Peek champion's unknown cards to plan better
    if (isCoordinatingForChampion) {
      const championUnknown = prioritizedPositions.filter(
        (p) =>
          p.target.playerId === championId && p.priority === SwapPriority.LOW // Unknown cards
      );

      // Peek champion's cards to understand their hand better
      for (let i = 0; i < Math.min(championUnknown.length - 1, 3); i++) {
        for (let j = i + 1; j < Math.min(championUnknown.length, i + 3); j++) {
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [championUnknown[i].target, championUnknown[j].target],
            shouldSwap: false,
          });
          console.log(
            `[Coalition] Generated Queen peek: Scout champion ${championId}'s cards`
          );
        }
      }
    }

    // For Queen, we want BOTH known and unknown cards:
    // - Known opponent low cards (Jokers, 2s) to steal
    // - Unknown cards for information gain
    // Prioritize known good cards first, then unknown cards
    // No arbitrary limits - generate all strategic peek combinations

    // Separate by player
    const myPositions = prioritizedPositions.filter(
      (p) => p.target.playerId === currentPlayer.id
    );
    const opponentPositions = prioritizedPositions.filter(
      (p) => p.target.playerId !== currentPlayer.id
    );

    // For opponents: separate known GOOD cards (worth stealing) from unknown/other cards
    // CRITICAL: Prioritize Jokers first (value = -1, best card in game)
    const opponentJokers = opponentPositions.filter((p) => {
      const player = state.players.find((pl) => pl.id === p.target.playerId);
      if (!player) return false;
      const memory = player.knownCards.get(p.target.position);
      const isKnown = memory && memory.confidence > 0.5 && memory.card;
      return isKnown && memory.card && memory.card.value === -1; // Joker only
    });

    const opponentKnownGoodCards = opponentPositions.filter((p) => {
      const player = state.players.find((pl) => pl.id === p.target.playerId);
      if (!player) return false;
      const memory = player.knownCards.get(p.target.position);
      const isKnown = memory && memory.confidence > 0.5 && memory.card;
      if (!isKnown || !memory.card) return false;

      const card = memory.card;

      // Skip Jokers (handled separately with highest priority)
      if (card.value === -1) return false;

      // 1. King - always steal
      if (card.value === 0) return true;

      // 2. Low-value cards (2-5) - good to steal
      if (card.value <= 5) return true;

      // 3. Cards matching current discard (enables immediate toss-in)
      const tossInRanks = state.tossInRanks
        ? state.tossInRanks
        : state.discardPileTop?.rank
        ? [state.discardPileTop.rank]
        : [];
      if (tossInRanks.includes(card.rank)) return true;

      // 4. Cards matching what bot already has (enables multi-card toss-in)
      for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
        const botCard = state.hiddenCards.get(`${currentPlayer.id}-${pos}`);
        if (botCard && botCard.rank === card.rank) return true;
      }

      return false;
    });

    const opponentUnknownOrOther = opponentPositions.filter((p) => {
      const player = state.players.find((pl) => pl.id === p.target.playerId);
      if (!player) return false;
      const memory = player.knownCards.get(p.target.position);
      const isKnown = memory && memory.confidence > 0.5 && memory.card;

      if (!isKnown || !memory.card) return true; // Unknown cards

      const card = memory.card;
      // Exclude good cards (already in opponentKnownGoodCards)
      // Include high-value cards that aren't useful
      if (card.value === -1 || card.value === 0) return false; // Joker/King - in good cards
      if (card.value <= 5) return false; // Low cards - in good cards
      if (state.discardPileTop && card.rank === state.discardPileTop.rank)
        return false; // Toss-in match - in good cards

      // Check if matches bot's cards (multi-toss-in potential)
      for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
        const botCard = state.hiddenCards.get(`${currentPlayer.id}-${pos}`);
        if (botCard && botCard.rank === card.rank) return false; // In good cards
      }

      return true; // High-value cards with no strategic benefit
    });

    // Strategy 0a (ABSOLUTE HIGHEST PRIORITY): Steal Jokers!
    // Joker is -1 value, the best card in the game
    // Generate ALL combinations of stealing Jokers
    for (const myCard of myPositions) {
      for (const joker of opponentJokers) {
        // Generate peek-only move
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, joker.target],
          shouldSwap: false,
        });

        // Generate peek-and-swap move (HIGHLY recommended)
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, joker.target],
          shouldSwap: true,
        });
      }
    }

    // Strategy 0b (HIGH PRIORITY): Peek bot's card and opponent's known GOOD card to steal it
    // This includes: Kings, low cards (2-5), toss-in matches, and multi-toss-in enablers
    // Generate ALL combinations - these are high-value tactical moves
    for (const myCard of myPositions) {
      for (const oppGoodCard of opponentKnownGoodCards) {
        // Generate peek-only move
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppGoodCard.target],
          shouldSwap: false,
        });

        // Generate peek-and-swap move (highly likely to be chosen by MCTS)
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppGoodCard.target],
          shouldSwap: true,
        });
      }
    }

    // Strategy 1: Peek two opponent unknown/other cards (maximize information gain)
    // For these, we generate both "peek-only" and "peek-and-swap" variants
    // Swapping two opponent cards CAN be strategic (disrupts their hands)
    // Limited to top 8 positions to avoid combinatorial explosion with unknown cards
    for (let i = 0; i < Math.min(opponentUnknownOrOther.length - 1, 8); i++) {
      for (
        let j = i + 1;
        j < Math.min(opponentUnknownOrOther.length, i + 5);
        j++
      ) {
        // Queen rule: Cannot peek two cards from the same player
        if (
          opponentUnknownOrOther[i].target.playerId !==
          opponentUnknownOrOther[j].target.playerId
        ) {
          // Generate peek-only move (don't swap)
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [
              opponentUnknownOrOther[i].target,
              opponentUnknownOrOther[j].target,
            ],
            shouldSwap: false,
          });

          // Generate peek-and-swap move (swap after peeking)
          // Swapping two opponent cards can be strategic (disrupt their hands)
          moves.push({
            type: 'use-action',
            playerId: currentPlayer.id,
            targets: [
              opponentUnknownOrOther[i].target,
              opponentUnknownOrOther[j].target,
            ],
            shouldSwap: true,
          });
        }
      }
    }

    // Strategy 2: Peek own and opponent unknown/other (for potential swap)
    // These are more likely to benefit from swapping
    // Limited to top 5 of each category to keep move count reasonable
    for (const myCard of myPositions.slice(0, 5)) {
      for (const oppCard of opponentUnknownOrOther.slice(0, 5)) {
        // Generate peek-only move
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppCard.target],
          shouldSwap: false,
        });

        // Generate peek-and-swap move
        moves.push({
          type: 'use-action',
          playerId: currentPlayer.id,
          targets: [myCard.target, oppCard.target],
          shouldSwap: true,
        });
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

    // Check if bot is coalition member (needs to filter Vinto caller)
    // Game rule: "no one may interact with the Vinto caller's cards" during final round
    const isCoalitionMember =
      state.vintoCallerId &&
      state.coalitionLeaderId &&
      currentPlayer.id !== state.vintoCallerId;

    for (const player of state.players) {
      const isBot = player.id === currentPlayer.id;

      // COALITION FILTER: Skip Vinto caller if bot is coalition member
      // This prevents coalition from peeking/swapping Vinto caller's cards (game rule)
      if (isCoalitionMember && player.id === state.vintoCallerId) {
        continue; // Coalition cannot interact with Vinto caller's cards (game rule)
      }

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
