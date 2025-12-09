# Phase 2 - Ticket #4: Enhanced Belief Propagation

**Priority:** HIGH
**Complexity:** MEDIUM-HIGH
**Estimated Time:** 4-5 days

## Overview

Enhance the existing belief state system to update card probability distributions based on observed actions, not just direct observations. This improves the quality of MCTS determinization, leading to better decision-making under uncertainty.

## Current State

The bot maintains beliefs about hidden cards:
- `BotMemory` tracks observed cards with confidence levels
- `OpponentModeler` tracks opponent readiness and beliefs
- `determinize()` uses weighted sampling based on strategic value

**Gap:** Beliefs are only updated from direct observations (peeks, swaps). The bot doesn't infer information from opponent actions.

**Example:** If an opponent draws a card and immediately swaps it, this is strong evidence that:
1. The drawn card is better than their current card
2. Their swapped card was likely high value

Currently, the bot doesn't update beliefs based on this observation.

## Goals

1. Update belief distributions based on opponent actions
2. Implement Bayesian belief updates
3. Improve determinization quality using updated beliefs
4. Track evidence history for debugging
5. Maintain performance (belief updates should be fast)

## Technical Design

### 1. Enhance Belief State Representation

```typescript
// packages/bot/src/lib/belief-types.ts

/**
 * Probability distribution over card ranks
 */
export interface CardDistribution {
  // Probability for each rank (sum to 1.0)
  probabilities: Map<Rank, number>;

  // Metadata
  lastUpdated: number;        // Turn number
  observationCount: number;   // How many observations influenced this
  entropy: number;            // Shannon entropy (uncertainty measure)
}

/**
 * Evidence that influences beliefs
 */
export interface Evidence {
  type: EvidenceType;
  turnNumber: number;
  playerId: string;
  position?: number;

  // Evidence-specific data
  action?: string;
  drawnCard?: Card;
  swappedPosition?: number;
  declaredRank?: Rank;
}

export type EvidenceType =
  | 'direct_observation'      // Peeked at card
  | 'swap_behavior'           // Player swapped/discarded
  | 'action_usage'            // Player used/didn't use action
  | 'king_declaration'        // King declaration revealed info
  | 'toss_in_failure'        // Wrong toss-in reveals what card ISN'T
  | 'vinto_call'             // Vinto call suggests low score
  | 'no_toss_in';            // Player didn't toss in (doesn't have rank)

/**
 * Enhanced belief state for a card location
 */
export interface CardBelief {
  playerId: string;
  position: number;

  // Direct observation (if available)
  observedCard: Card | null;
  confidence: number;

  // Probability distribution (if not directly observed)
  distribution: CardDistribution | null;

  // Evidence history
  evidence: Evidence[];
}
```

### 2. Implement Bayesian Belief Updater

```typescript
// packages/bot/src/lib/belief-updater.ts

export class BeliefUpdater {
  /**
   * Update belief distribution based on new evidence
   */
  static updateBelief(
    currentBelief: CardBelief,
    evidence: Evidence,
    gameContext: BeliefContext
  ): CardBelief {
    // If we have direct observation, no need for distribution
    if (currentBelief.observedCard) {
      return currentBelief;
    }

    // Initialize distribution if needed
    const distribution = currentBelief.distribution || this.uniformDistribution(gameContext);

    // Apply Bayesian update based on evidence type
    const updatedDistribution = this.applyEvidence(distribution, evidence, gameContext);

    return {
      ...currentBelief,
      distribution: updatedDistribution,
      evidence: [...currentBelief.evidence, evidence],
    };
  }

  /**
   * Apply evidence to update probability distribution
   */
  private static applyEvidence(
    prior: CardDistribution,
    evidence: Evidence,
    context: BeliefContext
  ): CardDistribution {
    const likelihood = this.computeLikelihood(evidence, context);

    // Bayes' rule: P(rank|evidence) ∝ P(evidence|rank) * P(rank)
    const posterior = new Map<Rank, number>();
    let totalProbability = 0;

    for (const [rank, priorProb] of prior.probabilities) {
      const likelihoodProb = likelihood.get(rank) || 0;
      const unnormalizedProb = likelihoodProb * priorProb;
      posterior.set(rank, unnormalizedProb);
      totalProbability += unnormalizedProb;
    }

    // Normalize
    if (totalProbability > 0) {
      for (const [rank, prob] of posterior) {
        posterior.set(rank, prob / totalProbability);
      }
    }

    return {
      probabilities: posterior,
      lastUpdated: evidence.turnNumber,
      observationCount: prior.observationCount + 1,
      entropy: this.computeEntropy(posterior),
    };
  }

  /**
   * Compute likelihood P(evidence | rank)
   * "Given this rank, how likely is this evidence?"
   */
  private static computeLikelihood(
    evidence: Evidence,
    context: BeliefContext
  ): Map<Rank, number> {
    switch (evidence.type) {
      case 'swap_behavior':
        return this.likelihoodFromSwapBehavior(evidence, context);
      case 'action_usage':
        return this.likelihoodFromActionUsage(evidence, context);
      case 'king_declaration':
        return this.likelihoodFromKingDeclaration(evidence, context);
      case 'toss_in_failure':
        return this.likelihoodFromTossInFailure(evidence, context);
      case 'no_toss_in':
        return this.likelihoodFromNoTossIn(evidence, context);
      default:
        return this.uniformLikelihood(context);
    }
  }

  /**
   * Likelihood from swap behavior
   * Example: Player drew 3, swapped at position 2
   * → Position 2 likely had high card (7-10)
   */
  private static likelihoodFromSwapBehavior(
    evidence: Evidence,
    context: BeliefContext
  ): Map<Rank, number> {
    const likelihood = new Map<Rank, number>();

    if (!evidence.drawnCard || evidence.swappedPosition === undefined) {
      return this.uniformLikelihood(context);
    }

    const drawnValue = getCardValue(evidence.drawnCard.rank);

    // If they swapped, the card at swapped position is likely higher value than drawn card
    for (const rank of context.availableRanks) {
      const value = getCardValue(rank);

      if (value > drawnValue) {
        // Higher value cards are more likely
        likelihood.set(rank, 0.8);
      } else if (value === drawnValue) {
        // Same value less likely (why swap?)
        likelihood.set(rank, 0.3);
      } else {
        // Lower value very unlikely (bad swap)
        likelihood.set(rank, 0.1);
      }
    }

    return this.normalizeLikelihood(likelihood);
  }

  /**
   * Likelihood from action usage
   * Example: Player drew 8, used action immediately
   * → They probably have high cards (prefer info over swap)
   */
  private static likelihoodFromActionUsage(
    evidence: Evidence,
    context: BeliefContext
  ): Map<Rank, number> {
    // This is a soft signal - affects beliefs about OTHER cards in hand
    // Implementation depends on which action was used and game state
    // For simplicity, return uniform (can be enhanced later)
    return this.uniformLikelihood(context);
  }

  /**
   * Likelihood from King declaration
   * Example: Player declared "Q", card wasn't Q
   * → Card definitely NOT Q
   */
  private static likelihoodFromKingDeclaration(
    evidence: Evidence,
    context: BeliefContext
  ): Map<Rank, number> {
    const likelihood = new Map<Rank, number>();

    if (!evidence.declaredRank) {
      return this.uniformLikelihood(context);
    }

    // Failed declaration: card is NOT the declared rank
    for (const rank of context.availableRanks) {
      if (rank === evidence.declaredRank) {
        likelihood.set(rank, 0.0); // Definitely not this
      } else {
        likelihood.set(rank, 1.0); // Could be any other
      }
    }

    return this.normalizeLikelihood(likelihood);
  }

  /**
   * Likelihood from failed toss-in
   * Example: Player tried to toss in, but it was wrong rank
   * → Their card is NOT the discard rank
   */
  private static likelihoodFromTossInFailure(
    evidence: Evidence,
    context: BeliefContext
  ): Map<Rank, number> {
    const likelihood = new Map<Rank, number>();
    const tossInRank = context.lastDiscardRank;

    if (!tossInRank) {
      return this.uniformLikelihood(context);
    }

    // Failed toss-in: card is NOT the discard rank
    for (const rank of context.availableRanks) {
      if (rank === tossInRank) {
        likelihood.set(rank, 0.0);
      } else {
        likelihood.set(rank, 1.0);
      }
    }

    return this.normalizeLikelihood(likelihood);
  }

  /**
   * Likelihood from NOT tossing in
   * Example: Discard was 7, player didn't toss in
   * → Probably doesn't have 7 (weak signal, could be strategic)
   */
  private static likelihoodFromNoTossIn(
    evidence: Evidence,
    context: BeliefContext
  ): Map<Rank, number> {
    const likelihood = new Map<Rank, number>();
    const tossInRank = context.lastDiscardRank;

    if (!tossInRank) {
      return this.uniformLikelihood(context);
    }

    // Weak signal: slightly less likely to have the discard rank
    for (const rank of context.availableRanks) {
      if (rank === tossInRank) {
        likelihood.set(rank, 0.5); // Somewhat unlikely
      } else {
        likelihood.set(rank, 1.0); // Normal
      }
    }

    return this.normalizeLikelihood(likelihood);
  }

  /**
   * Compute Shannon entropy of distribution
   * High entropy = uncertain, low entropy = confident
   */
  private static computeEntropy(probabilities: Map<Rank, number>): number {
    let entropy = 0;
    for (const prob of probabilities.values()) {
      if (prob > 0) {
        entropy -= prob * Math.log2(prob);
      }
    }
    return entropy;
  }

  private static uniformDistribution(context: BeliefContext): CardDistribution {
    const probabilities = new Map<Rank, number>();
    const uniformProb = 1.0 / context.availableRanks.length;

    for (const rank of context.availableRanks) {
      probabilities.set(rank, uniformProb);
    }

    return {
      probabilities,
      lastUpdated: context.currentTurn,
      observationCount: 0,
      entropy: Math.log2(context.availableRanks.length),
    };
  }

  private static uniformLikelihood(context: BeliefContext): Map<Rank, number> {
    const likelihood = new Map<Rank, number>();
    for (const rank of context.availableRanks) {
      likelihood.set(rank, 1.0);
    }
    return likelihood;
  }

  private static normalizeLikelihood(likelihood: Map<Rank, number>): Map<Rank, number> {
    const total = Array.from(likelihood.values()).reduce((a, b) => a + b, 0);
    if (total === 0) return likelihood;

    const normalized = new Map<Rank, number>();
    for (const [rank, prob] of likelihood) {
      normalized.set(rank, prob / total);
    }
    return normalized;
  }
}

export interface BeliefContext {
  currentTurn: number;
  availableRanks: Rank[];
  lastDiscardRank?: Rank;
}
```

### 3. Integrate with OpponentModeler

```typescript
// packages/bot/src/lib/opponent-modeler.ts

export class OpponentModeler {
  private beliefs: Map<string, CardBelief>; // playerId-position -> belief

  /**
   * Update beliefs based on observed action
   */
  updateFromAction(action: GameAction, context: BeliefContext): void {
    const evidence = this.actionToEvidence(action, context);

    if (!evidence) return;

    // Update beliefs for affected cards
    const affectedBeliefs = this.getAffectedBeliefs(action);

    for (const belief of affectedBeliefs) {
      const updated = BeliefUpdater.updateBelief(belief, evidence, context);
      this.beliefs.set(this.beliefKey(belief.playerId, belief.position), updated);
    }
  }

  /**
   * Convert game action to evidence
   */
  private actionToEvidence(action: GameAction, context: BeliefContext): Evidence | null {
    switch (action.type) {
      case 'SWAP_CARD':
        return {
          type: 'swap_behavior',
          turnNumber: context.currentTurn,
          playerId: action.payload.playerId,
          swappedPosition: action.payload.position,
          drawnCard: action.payload.drawnCard,
        };

      case 'TOSS_IN_CARD':
        // If toss-in succeeded, we know the card rank
        // If failed, we know what it ISN'T
        return action.payload.success
          ? {
              type: 'direct_observation',
              turnNumber: context.currentTurn,
              playerId: action.payload.playerId,
              position: action.payload.position,
            }
          : {
              type: 'toss_in_failure',
              turnNumber: context.currentTurn,
              playerId: action.payload.playerId,
              position: action.payload.position,
            };

      case 'DECLARE_KING_ACTION':
        return {
          type: 'king_declaration',
          turnNumber: context.currentTurn,
          playerId: action.payload.playerId,
          position: action.payload.targetPosition,
          declaredRank: action.payload.declaredRank,
        };

      default:
        return null;
    }
  }

  private getAffectedBeliefs(action: GameAction): CardBelief[] {
    // Determine which card beliefs are affected by this action
    // Implementation depends on action type
    return [];
  }

  private beliefKey(playerId: string, position: number): string {
    return `${playerId}-${position}`;
  }
}
```

### 4. Update Determinization with Belief-Weighted Sampling

```typescript
// packages/bot/src/lib/mcts-determinization.ts

export function determinize(state: MCTSGameState): MCTSGameState {
  // ... existing code

  // Use belief distributions for sampling
  for (const player of state.players) {
    for (let pos = 0; pos < player.cardCount; pos++) {
      const memory = player.knownCards.get(pos);

      if (memory?.card) {
        // Direct observation
        sampledCards.set(cardKey(player.id, pos), memory.card);
      } else {
        // Sample from belief distribution
        const belief = state.opponentModeler?.getBelief(player.id, pos);

        if (belief?.distribution) {
          // Belief-weighted sampling
          const sampledCard = sampleFromDistribution(
            belief.distribution.probabilities,
            availableCards
          );
          sampledCards.set(cardKey(player.id, pos), sampledCard);
        } else {
          // Fallback to uniform sampling
          const sampledCard = sampleUniform(availableCards);
          sampledCards.set(cardKey(player.id, pos), sampledCard);
        }
      }

      // Remove sampled card from pool
      availableCards = availableCards.filter(c => c.id !== sampledCard.id);
    }
  }

  // ... rest of determinization
}

/**
 * Sample card based on probability distribution
 */
function sampleFromDistribution(
  distribution: Map<Rank, number>,
  availableCards: Card[]
): Card {
  // Group available cards by rank
  const cardsByRank = new Map<Rank, Card[]>();
  for (const card of availableCards) {
    const cards = cardsByRank.get(card.rank) || [];
    cards.push(card);
    cardsByRank.set(card.rank, cards);
  }

  // Weighted random selection
  const rand = Math.random();
  let cumulative = 0;

  for (const [rank, probability] of distribution) {
    cumulative += probability;
    if (rand <= cumulative) {
      const cards = cardsByRank.get(rank);
      if (cards && cards.length > 0) {
        return cards[Math.floor(Math.random() * cards.length)];
      }
    }
  }

  // Fallback
  return availableCards[Math.floor(Math.random() * availableCards.length)];
}
```

## Implementation Steps

### Step 1: Create Belief Types
- [ ] Create `packages/bot/src/lib/belief-types.ts`
- [ ] Define `CardDistribution`, `Evidence`, `CardBelief` interfaces
- [ ] Define evidence types
- [ ] Export from `packages/bot/src/lib/index.ts`

### Step 2: Implement Belief Updater
- [ ] Create `packages/bot/src/lib/belief-updater.ts`
- [ ] Implement `updateBelief()` core method
- [ ] Implement `applyEvidence()` with Bayesian update
- [ ] Implement likelihood functions for each evidence type
- [ ] Implement entropy calculation
- [ ] Add unit tests for belief updates

### Step 3: Integrate with OpponentModeler
- [ ] Add `beliefs` map to `OpponentModeler`
- [ ] Implement `updateFromAction()` method
- [ ] Implement `actionToEvidence()` conversion
- [ ] Implement `getBelief()` accessor
- [ ] Add tests for action-based belief updates

### Step 4: Update Determinization
- [ ] Modify `determinize()` to use belief distributions
- [ ] Implement `sampleFromDistribution()`
- [ ] Test belief-weighted sampling
- [ ] Benchmark performance impact

### Step 5: Evidence Collection
- [ ] Track all relevant actions as evidence
- [ ] Update beliefs after each action
- [ ] Store evidence history for debugging
- [ ] Add logging for belief updates

### Step 6: Testing
- [ ] Unit tests for `BeliefUpdater`
- [ ] Test each evidence type's likelihood function
- [ ] Test Bayesian update correctness
- [ ] Integration tests for action → belief pipeline
- [ ] Test determinization with beliefs
- [ ] Verify entropy calculations

### Step 7: Performance Optimization
- [ ] Profile belief update overhead
- [ ] Optimize hot paths
- [ ] Add caching if needed
- [ ] Ensure <10ms per belief update

### Step 8: Documentation
- [ ] Document belief propagation system
- [ ] Add examples of evidence → belief updates
- [ ] Document entropy interpretation
- [ ] Update `packages/bot/README.md`

## Success Criteria

- [ ] Beliefs update based on 5+ evidence types
- [ ] Bayesian updates produce sensible probability distributions
- [ ] Determinization uses belief-weighted sampling
- [ ] Evidence history tracked for debugging
- [ ] Entropy correctly measures uncertainty
- [ ] Performance impact <10ms per decision
- [ ] Unit tests achieve >90% coverage
- [ ] Integration tests verify improved decision quality

## Expected Impact

### Before (Current)
```typescript
// Opponent draws 3, swaps at position 2
// Bot assumes position 2 is random (uniform distribution)
beliefDistribution = { 2: 0.08, 3: 0.08, ..., K: 0.08 }
```

### After (Enhanced)
```typescript
// Same scenario
// Bot infers position 2 likely had high card
beliefDistribution = { 2: 0.02, 3: 0.02, ..., 8: 0.12, 9: 0.12, 10: 0.15, J: 0.15, Q: 0.15, K: 0.01 }
// Strong bias toward 8-Q (high value cards opponent would swap out)
```

## Integration Notes

### Phase 1 Integration
If explainable decisions are implemented, show belief updates:
```typescript
humanReadable: "Player 2 swapped a 3 for their card at position 2. I now believe that position 2 had a high card (80% chance of 7-10)."
```

### Phase 2 Integration
Belief propagation is a foundation for:
- Ticket #5: Coalition Signaling (interpret coalition member actions)
- Ticket #6: Deep Opponent Profiling (track behavioral patterns)

## Dependencies

- Current `BotMemory` and `OpponentModeler`
- Game action types from engine

## Related Tickets

- Phase 1, Ticket #1: Explainable Decisions (can explain belief updates)
- Phase 2, Ticket #5: Coalition Signaling (uses beliefs to interpret signals)
- Phase 2, Ticket #6: Deep Opponent Profiling (builds on belief tracking)

## References

- Current memory system: `packages/bot/src/lib/bot-memory.ts`
- Opponent modeler: `packages/bot/src/lib/opponent-modeler.ts`
- Determinization: `packages/bot/src/lib/mcts-determinization.ts`
- Game actions: `packages/engine/src/lib/types/GameAction.ts`
