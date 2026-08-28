// opponent-modeler.ts
// Service for tracking beliefs about opponent hands and predicting Vinto calls

import { Card, Rank, getCardValue } from '@vinto/shapes';

/**
 * Constants for Vinto-readiness scoring
 * These values tune how different actions affect the prediction of when a player will call Vinto
 */
const READINESS_ADJUSTMENT = {
  // Peeking at cards suggests player is still gathering information (not ready)
  PEEK_ACTION_PENALTY: -0.1,
  // Using swap actions (J, Q) suggests player is optimizing their hand (getting ready)
  SWAP_ACTION_BOOST: 0.15,
  // Peeking at own cards suggests uncertainty about hand
  PEEK_OWN_PENALTY: -0.05,
  // Swapping cards within own hand suggests optimization
  SWAP_OWN_BOOST: 0.1,
  // When discarding a drawn card, reduce estimated score
  DISCARD_DRAWN_SCORE_REDUCTION: 2,
} as const;

/**
 * Constants for score-based readiness calculation
 */
const SCORE_READINESS = {
  // Score at or below this = maximum readiness (1.0)
  EXCELLENT_SCORE_THRESHOLD: 10,
  // Score at or above this = minimum readiness (0.0)
  POOR_SCORE_THRESHOLD: 30,
  // Weight given to existing readiness vs new score-based readiness
  EXISTING_READINESS_WEIGHT: 0.7,
  NEW_SCORE_WEIGHT: 0.3,
} as const;

/**
 * Default starting values for opponent beliefs
 */
const DEFAULT_BELIEFS = {
  // Average starting hand estimate (5 cards * 5 points average)
  INITIAL_ESTIMATED_SCORE: 25,
  // Starting Vinto-readiness (neutral)
  INITIAL_VINTO_READINESS: 0.0,
  // Minimum confidence to use a belief constraint
  MIN_SWAP_INFERENCE_CONFIDENCE: 0.8,
} as const;

/**
 * Belief about a specific hidden card
 */
export interface CardBelief {
  minValue?: number; // Inferred minimum point value
  maxValue?: number; // Inferred maximum point value
  likelyRanks?: Rank[]; // Most probable ranks
  reason?: string; // Debug info about why this belief was formed
  confidence?: number; // 0-1 confidence in this belief
}

/**
 * Beliefs about all cards in an opponent's hand
 */
export interface OpponentBeliefs {
  playerId: string;
  cardBeliefs: Map<number, CardBelief>; // position -> belief
  estimatedScore: number; // Best estimate of hand total
  vintoReadiness: number; // 0-1 score indicating likelihood of calling Vinto
}

/**
 * Observable action that can inform beliefs
 */
export interface ObservedAction {
  type:
    | 'swap-from-discard'
    | 'discard-drawn'
    | 'use-action'
    | 'toss-in'
    | 'peek-own'
    | 'swap-own';
  playerId: string;
  card?: Card; // The card involved (if visible)
  position?: number; // The position affected
  swappedOutCard?: Card; // The card that was replaced (if known)
}

/**
 * OpponentModeler - Tracks beliefs about opponent hands
 *
 * This service observes opponent actions and uses them to build
 * a "belief state" about their hidden cards. Key inferences:
 *
 * 1. Swap from discard: If opponent takes a 7 from discard and swaps,
 *    the card they replaced must be worth >= 8 points
 *
 * 2. Discard drawn card: If opponent draws and immediately discards,
 *    they likely have better cards in hand
 *
 * 3. Toss-in behavior: Confirms they have matching rank cards
 *
 * 4. Action card usage: Reveals strategic priorities
 */
export class OpponentModeler {
  private beliefs = new Map<string, OpponentBeliefs>();

  /**
   * Initialize beliefs for a player
   */
  public initializePlayer(playerId: string): void {
    if (!this.beliefs.has(playerId)) {
      this.beliefs.set(playerId, {
        playerId,
        cardBeliefs: new Map(),
        estimatedScore: DEFAULT_BELIEFS.INITIAL_ESTIMATED_SCORE,
        vintoReadiness: DEFAULT_BELIEFS.INITIAL_VINTO_READINESS,
      });
    }
  }

  /**
   * Get belief about a specific card
   */
  public getBelief(playerId: string, position: number): CardBelief | undefined {
    const playerBeliefs = this.beliefs.get(playerId);
    return playerBeliefs?.cardBeliefs.get(position);
  }

  /**
   * Get all beliefs for a player
   */
  public getPlayerBeliefs(playerId: string): OpponentBeliefs | undefined {
    return this.beliefs.get(playerId);
  }

  /**
   * Handle observed opponent action
   */
  public handleObservedAction(action: ObservedAction): void {
    this.initializePlayer(action.playerId);

    switch (action.type) {
      case 'swap-from-discard':
        this.handleSwapFromDiscard(action);
        break;
      case 'discard-drawn':
        this.handleDiscardDrawn(action);
        break;
      case 'use-action':
        this.handleUseAction(action);
        break;
      case 'toss-in':
        this.handleTossIn(action);
        break;
      case 'peek-own':
        this.handlePeekOwn(action);
        break;
      case 'swap-own':
        this.handleSwapOwn(action);
        break;
    }

    // Update Vinto readiness after each action
    this.updateVintoReadiness(action.playerId);
  }

  /**
   * Inference: Opponent swapped from discard pile
   *
   * If opponent takes card with value X from discard and swaps it at position P,
   * the card at position P must be worth MORE than X (otherwise swap makes no sense)
   */
  private handleSwapFromDiscard(action: ObservedAction): void {
    if (!action.card || action.position === undefined) {
      return;
    }

    const playerBeliefs = this.beliefs.get(action.playerId)!;
    const discardValue = getCardValue(action.card.rank);

    // Infer minimum value: the swapped card must be worse than what they took
    const belief: CardBelief = {
      minValue: discardValue + 1,
      reason: `Swapped out for ${action.card.rank} (${discardValue} pts)`,
      confidence: DEFAULT_BELIEFS.MIN_SWAP_INFERENCE_CONFIDENCE,
    };

    playerBeliefs.cardBeliefs.set(action.position, belief);
  }

  /**
   * Inference: Opponent drew card and immediately discarded it
   *
   * This suggests they have better cards in their hand already.
   * We reduce their estimated score more aggressively for high-value discards.
   */
  private handleDiscardDrawn(action: ObservedAction): void {
    if (!action.card) {
      return;
    }

    const playerBeliefs = this.beliefs.get(action.playerId)!;
    const discardedValue = getCardValue(action.card.rank);

    // Infer that their hand cards are likely better than what they discarded
    // Reduce estimate more for high-value discards (they must have good cards to discard a high value)
    const reduction = Math.max(
      READINESS_ADJUSTMENT.DISCARD_DRAWN_SCORE_REDUCTION,
      Math.floor(discardedValue / 3)
    );

    playerBeliefs.estimatedScore = Math.max(
      0,
      playerBeliefs.estimatedScore - reduction
    );
  }

  /**
   * Inference: Opponent used an action card
   *
   * Using peek actions suggests they're gathering information (early game)
   * Using swap actions suggests they're optimizing (mid/late game)
   *
   * CRITICAL: Queen is treated differently based on context:
   * - If used primarily for peeking → information gathering (penalty)
   * - If used with swap → optimization (boost)
   * We apply a moderate boost for Queen to reflect its dual nature
   */
  private handleUseAction(action: ObservedAction): void {
    if (!action.card) {
      return;
    }

    const playerBeliefs = this.beliefs.get(action.playerId)!;

    // Handle each card type with mutually exclusive logic
    if (['7', '8', '9', '10'].includes(action.card.rank)) {
      // Pure peek actions - player is still learning
      playerBeliefs.vintoReadiness = Math.max(
        0,
        playerBeliefs.vintoReadiness + READINESS_ADJUSTMENT.PEEK_ACTION_PENALTY
      );
    } else if (action.card.rank === 'Q') {
      // Queen: peek + optional swap (moderate boost reflecting dual capability)
      // Assumes most Queen usage includes swap for optimization
      playerBeliefs.vintoReadiness = Math.min(
        1,
        playerBeliefs.vintoReadiness + READINESS_ADJUSTMENT.SWAP_ACTION_BOOST * 0.7
      );
    } else if (action.card.rank === 'J') {
      // Jack: pure swap action - strong optimization signal
      playerBeliefs.vintoReadiness = Math.min(
        1,
        playerBeliefs.vintoReadiness + READINESS_ADJUSTMENT.SWAP_ACTION_BOOST
      );
    }
  }

  /**
   * Inference: Opponent participated in toss-in
   *
   * Confirms they had the matching rank card
   */
  private handleTossIn(action: ObservedAction): void {
    if (!action.card || action.position === undefined) {
      return;
    }

    const playerBeliefs = this.beliefs.get(action.playerId)!;

    // We now know this card's rank with certainty
    const belief: CardBelief = {
      likelyRanks: [action.card.rank],
      reason: `Tossed in ${action.card.rank}`,
      confidence: 1.0, // Perfect knowledge
    };

    playerBeliefs.cardBeliefs.set(action.position, belief);
  }

  /**
   * Inference: Opponent used peek on their own cards
   *
   * They're learning about their hand
   */
  private handlePeekOwn(action: ObservedAction): void {
    const playerBeliefs = this.beliefs.get(action.playerId)!;

    // Peeking suggests they don't know their full hand yet
    playerBeliefs.vintoReadiness = Math.max(
      0,
      playerBeliefs.vintoReadiness + READINESS_ADJUSTMENT.PEEK_OWN_PENALTY
    );
  }

  /**
   * Inference: Opponent swapped cards within their own hand
   *
   * They're optimizing, getting closer to calling Vinto
   */
  private handleSwapOwn(action: ObservedAction): void {
    const playerBeliefs = this.beliefs.get(action.playerId)!;

    // Swapping suggests they're optimizing their hand
    playerBeliefs.vintoReadiness = Math.min(
      1,
      playerBeliefs.vintoReadiness + READINESS_ADJUSTMENT.SWAP_OWN_BOOST
    );
  }

  /**
   * Update Vinto-readiness score for a player
   *
   * Vinto-readiness is a 0-1 score indicating how likely the player is
   * to call Vinto soon. It's based on:
   *
   * 1. Estimated hand score (lower = more ready)
   * 2. Recent action patterns (swapping > peeking)
   * 3. Number of turns taken (more turns = more ready)
   */
  private updateVintoReadiness(playerId: string): void {
    const playerBeliefs = this.beliefs.get(playerId);
    if (!playerBeliefs) {
      return;
    }

    // Factor 1: Estimated score (lower score = higher readiness)
    const scoreRange = SCORE_READINESS.POOR_SCORE_THRESHOLD - SCORE_READINESS.EXCELLENT_SCORE_THRESHOLD;
    const scoreReadiness = Math.max(
      0,
      Math.min(1, (SCORE_READINESS.POOR_SCORE_THRESHOLD - playerBeliefs.estimatedScore) / scoreRange)
    );

    // Combine with existing readiness (weighted average)
    playerBeliefs.vintoReadiness =
      SCORE_READINESS.EXISTING_READINESS_WEIGHT * playerBeliefs.vintoReadiness +
      SCORE_READINESS.NEW_SCORE_WEIGHT * scoreReadiness;
  }

  /**
   * Get most likely Vinto caller among all opponents
   */
  public getMostLikelyVintoCaller(): string | null {
    let maxReadiness = -1;
    let mostLikelyPlayer: string | null = null;

    for (const [playerId, beliefs] of this.beliefs.entries()) {
      if (beliefs.vintoReadiness > maxReadiness) {
        maxReadiness = beliefs.vintoReadiness;
        mostLikelyPlayer = playerId;
      }
    }

    return mostLikelyPlayer;
  }

  /**
   * Remove belief about a specific card position
   * Call this when a card is removed from a player's hand (e.g., toss-in)
   * to prevent stale beliefs pointing to wrong cards
   */
  public removeCardBelief(playerId: string, position: number): void {
    const beliefs = this.beliefs.get(playerId);
    if (beliefs) {
      beliefs.cardBeliefs.delete(position);
    }
  }

  /**
   * Shift card beliefs after a card is removed at a position
   * When a card at position N is removed, all cards at positions > N shift down
   */
  public shiftCardBeliefs(playerId: string, removedPosition: number): void {
    const beliefs = this.beliefs.get(playerId);
    if (!beliefs) return;

    const newBeliefs = new Map<number, CardBelief>();

    // Keep beliefs before removed position unchanged
    for (const [pos, belief] of beliefs.cardBeliefs.entries()) {
      if (pos < removedPosition) {
        newBeliefs.set(pos, belief);
      } else if (pos > removedPosition) {
        // Shift down by 1
        newBeliefs.set(pos - 1, belief);
      }
      // Skip belief at removedPosition (it's gone)
    }

    beliefs.cardBeliefs = newBeliefs;
  }

  /**
   * Reset beliefs for a new round
   */
  public reset(): void {
    this.beliefs.clear();
  }

  /**
   * Debug: Get all beliefs
   */
  public getAllBeliefs(): Map<string, OpponentBeliefs> {
    return this.beliefs;
  }
}
