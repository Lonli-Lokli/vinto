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
        // COALITION: Don't target Vinto caller if bot is in coalition
        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          // COALITION FILTER: Skip Vinto caller if bot is coalition member
          const isCoalitionMember =
            state.vintoCallerId &&
            state.coalitionLeaderId &&
            currentPlayer.id !== state.vintoCallerId;
          if (isCoalitionMember && opponent.id === state.vintoCallerId) {
            continue; // Coalition cannot peek Vinto caller
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
        // COALITION: Don't target Vinto caller if bot is in coalition
        for (const opponent of state.players) {
          if (opponent.id === currentPlayer.id) continue;

          // COALITION FILTER: Skip Vinto caller if bot is coalition member
          const isCoalitionMember =
            state.vintoCallerId &&
            state.coalitionLeaderId &&
            currentPlayer.id !== state.vintoCallerId;
          if (isCoalitionMember && opponent.id === state.vintoCallerId) {
            continue; // Coalition cannot use Ace on Vinto caller
          }

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
   * Key insight: King is EXTREMELY powerful for removing OUR OWN cards, not opponents!
   *
   * Priority:
   * 1. TOSS-IN CASCADES: Declare ranks we have 2+ of (removes multiple cards at once)
   * 2. HIGH VALUE REMOVAL: Declare our single high-value cards (10, J, Q)
   * 3. STRATEGIC SYNERGY: Declare ranks that match other players' cards (force them to toss in too)
   *
   * NEVER declare opponent cards unless in coalition (helps them, not us)
   */
  static generateImprovedKingMoves(
    state: MCTSGameState,
    currentPlayerId: string
  ): MCTSMove[] {
    const moves: MCTSMove[] = [];
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer || currentPlayer.id !== currentPlayerId) return moves;

    console.log(
      `[King Generator] Player ${currentPlayerId} has ${currentPlayer.cardCount} cards, ${currentPlayer.knownCards.size} known cards in memory`
    );

    // Build map: rank → positions we have
    const ourRankMap = new Map<Rank, number[]>();

    for (let pos = 0; pos < currentPlayer.cardCount; pos++) {
      // ONLY use known cards from the player's memory (high confidence)
      // King declaration should NEVER use determinized/sampled cards
      const memory = currentPlayer.knownCards.get(pos);
      
      if (memory && memory.confidence > 0.5 && memory.card) {
        const card = memory.card;
        const rank = card.rank;
        const positions = ourRankMap.get(rank) || [];
        positions.push(pos);
        ourRankMap.set(rank, positions);
        
        console.log(
          `[King Generator] Position ${pos}: ${rank} (confidence: ${memory.confidence.toFixed(2)})`
        );
      } else {
        console.log(
          `[King Generator] Position ${pos}: UNKNOWN (confidence: ${memory?.confidence?.toFixed(2) || 'N/A'})`
        );
      }
    }
    
    console.log(
      `[King Generator] Found ${ourRankMap.size} distinct ranks in known cards`
    );

    // Calculate strategic value for each possible King declaration
    const declarations: Array<{
      playerId: string;
      position: number;
      rank: Rank;
      strategicValue: number;
      reason: string;
    }> = [];

    // Evaluate each card we could declare
    ourRankMap.forEach((positions, rank) => {
      // Get card from memory (which we already used to build ourRankMap)
      // Do NOT use hiddenCards here - it's empty until determinize() is called
      const memory = currentPlayer.knownCards.get(positions[0]);
      if (!memory || !memory.card) {
        logger.warn(
          `[King Generator] No card memory for position ${positions[0]} - this should not happen`
        );
        return;
      }
      
      const card = memory.card;
      
      console.log(
        `[King Generator] Evaluating declaration of ${rank} at position ${positions[0]}`
      );

      let strategicValue = 0;
      let reason = '';

      // FACTOR 1: Toss-in cascade value (HIGHEST priority)
      if (positions.length >= 2) {
        // Multiple cards = cascade removal
        const cascadeValue = positions.length * card.value;
        strategicValue += cascadeValue * 10; // HUGE multiplier for cascades
        reason = `CASCADE: Remove ${positions.length} ${rank}s at once (${cascadeValue} points)`;

        // BONUS: If it's Kings, even better (King toss-ins are rare and powerful)
        if (rank === 'K') {
          strategicValue += 50;
          reason += ' [KING CASCADE!]';
        }
      } else {
        // Single card removal
        strategicValue += card.value * 3; // Base value for removing high card
        reason = `Remove single ${rank} (${card.value} points)`;
      }

      // FACTOR 2: Card value (prefer removing high-value cards)
      if (card.value >= 10) {
        strategicValue += 20; // Extra bonus for 10, J, Q, K
        reason += ` [HIGH VALUE]`;
      }

      // FACTOR 3: Enable future plays (e.g., declaring 7 leaves us with peeking capability)
      if (rank === '7' || rank === '8' || rank === 'Q' || rank === 'J') {
        // Action cards have extra utility if kept
        strategicValue -= 5; // Small penalty (might want to keep them)
        reason += ` [action card - consider keeping]`;
      }

      // FACTOR 4: Opponent synergy (if others have matching cards, force them to toss in too)
      let opponentMatchCount = 0;
      state.players.forEach((opponent) => {
        if (opponent.id === currentPlayer.id) return;

        opponent.knownCards.forEach((memory) => {
          if (memory.card && memory.card.rank === rank) {
            opponentMatchCount++;
          }
        });
      });

      if (opponentMatchCount > 0) {
        strategicValue += opponentMatchCount * 15; // Bonus for forcing opponent toss-ins
        reason += ` [+${opponentMatchCount} opponent toss-ins]`;
      }

      declarations.push({
        playerId: currentPlayer.id,
        position: positions[0],
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

    // Step 1: Categorize all positions by strategic value
    // NOTE: categorizePositionsForSwap already filters out Vinto caller for coalition members
    const prioritizedPositions = this.categorizePositionsForSwap(state);

    // Step 2: Generate ALL strategic swaps prioritized by value
    // No arbitrary limits - generate all valuable combinations and let MCTS explore them

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

    // High priority: My known high cards with opponent's known low cards
    const opponentLowCards = prioritizedPositions.filter(
      (p) =>
        p.target.playerId !== currentPlayer.id &&
        p.priority === SwapPriority.CRITICAL &&
        (p.value ?? 10) < 3 // Known low value
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

    // NOTE: categorizePositionsForSwap already filters out Vinto caller for coalition members
    const prioritizedPositions = this.categorizePositionsForSwap(state);

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
    const opponentKnownGoodCards = opponentPositions.filter((p) => {
      const player = state.players.find((pl) => pl.id === p.target.playerId);
      if (!player) return false;
      const memory = player.knownCards.get(p.target.position);
      const isKnown = memory && memory.confidence > 0.5 && memory.card;
      if (!isKnown || !memory.card) return false;

      const card = memory.card;

      // 1. Joker or King - always steal
      if (card.value === -1 || card.value === 0) return true;

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

    // Strategy 0 (HIGHEST PRIORITY): Peek bot's card and opponent's known GOOD card to steal it
    // This includes: Jokers, Kings, low cards (2-5), toss-in matches, and multi-toss-in enablers
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
    const isCoalitionMember =
      state.vintoCallerId &&
      state.coalitionLeaderId &&
      currentPlayer.id !== state.vintoCallerId;

    for (const player of state.players) {
      const isBot = player.id === currentPlayer.id;

      // COALITION FILTER: Skip Vinto caller if bot is coalition member
      if (isCoalitionMember && player.id === state.vintoCallerId) {
        continue; // Coalition cannot target Vinto caller for swaps
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
