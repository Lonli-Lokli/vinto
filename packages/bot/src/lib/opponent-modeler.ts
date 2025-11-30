// opponent-modeler.ts
// Service for tracking beliefs about opponent hands and predicting Vinto calls

import { Card, Rank, getCardValue } from '@vinto/shapes';

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
        estimatedScore: 25, // Average starting estimate (5 cards * 5 points)
        vintoReadiness: 0.0,
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
      confidence: 0.8, // High confidence in this inference
    };

    playerBeliefs.cardBeliefs.set(action.position, belief);

    console.log(
      `[OpponentModeler] ${action.playerId} swapped ${action.card.rank} at pos ${action.position} - inferred minValue=${belief.minValue}`
    );
  }

  /**
   * Inference: Opponent drew card and immediately discarded it
   *
   * This suggests they have better cards in their hand already
   */
  private handleDiscardDrawn(action: ObservedAction): void {
    if (!action.card) {
      return;
    }

    const playerBeliefs = this.beliefs.get(action.playerId)!;
    const discardedValue = getCardValue(action.card.rank);

    // Infer that their hand cards are likely better than what they discarded
    // Update global estimate slightly downward
    playerBeliefs.estimatedScore = Math.max(
      0,
      playerBeliefs.estimatedScore - 2
    );

    console.log(
      `[OpponentModeler] ${action.playerId} discarded drawn ${action.card.rank} - likely has better cards`
    );
  }

  /**
   * Inference: Opponent used an action card
   *
   * Using peek actions suggests they're gathering information (early game)
   * Using swap actions suggests they're optimizing (mid/late game)
   */
  private handleUseAction(action: ObservedAction): void {
    if (!action.card) {
      return;
    }

    const playerBeliefs = this.beliefs.get(action.playerId)!;

    // Peek actions (7, 8, 9, 10, Q) suggest information gathering
    if (['7', '8', '9', '10', 'Q'].includes(action.card.rank)) {
      // Player is learning about their hand - they're not ready for Vinto yet
      playerBeliefs.vintoReadiness = Math.max(
        0,
        playerBeliefs.vintoReadiness - 0.1
      );
    }

    // Swap actions (J, Q) suggest hand optimization
    if (['J', 'Q'].includes(action.card.rank)) {
      // Player is actively improving - getting closer to Vinto
      playerBeliefs.vintoReadiness = Math.min(
        1,
        playerBeliefs.vintoReadiness + 0.15
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
      playerBeliefs.vintoReadiness - 0.05
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
      playerBeliefs.vintoReadiness + 0.1
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
    // Score of 10 or less = 1.0 readiness
    // Score of 30 or more = 0.0 readiness
    const scoreReadiness = Math.max(
      0,
      Math.min(1, (30 - playerBeliefs.estimatedScore) / 20)
    );

    // Combine with existing readiness (weighted average)
    playerBeliefs.vintoReadiness =
      0.7 * playerBeliefs.vintoReadiness + 0.3 * scoreReadiness;

    console.log(
      `[OpponentModeler] ${playerId} Vinto readiness: ${playerBeliefs.vintoReadiness.toFixed(
        2
      )} (estimated score: ${playerBeliefs.estimatedScore})`
    );
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
