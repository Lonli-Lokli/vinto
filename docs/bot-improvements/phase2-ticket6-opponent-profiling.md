# Phase 2 - Ticket #6: Deep Opponent Profiling

**Priority:** MEDIUM
**Complexity:** MEDIUM
**Estimated Time:** 3-4 days

## Overview

Extend the existing `OpponentModeler` to track play style patterns and behavioral characteristics. This enables the bot to adapt its strategy based on opponent tendencies, exploiting predictable players and adjusting against adaptive ones.

## Current State

The bot has basic opponent modeling:
- `OpponentModeler` tracks Vinto readiness (0-1 score)
- Infers card beliefs from swaps
- Updates based on observed actions

**Gap:** No behavioral profiling. The bot doesn't track or adapt to:
- How aggressively opponents call Vinto
- Risk tolerance (swap unknown vs known cards)
- Action card usage patterns
- Coalition cooperation tendencies

## Goals

1. Track behavioral patterns per opponent
2. Build opponent profiles (aggression, risk tolerance, patterns)
3. Use profiles to predict opponent actions
4. Adapt bot strategy based on opponent profile
5. Maintain performance (profiling should be lightweight)

## Technical Design

### 1. Define Opponent Profile

```typescript
// packages/bot/src/lib/opponent-profile-types.ts

/**
 * Behavioral profile of an opponent
 */
export interface OpponentProfile {
  playerId: string;

  // Playstyle metrics (0-1 range, updated from observations)
  aggression: number;         // How quickly they call Vinto
  riskTolerance: number;      // Willingness to swap unknown cards
  cooperativeness: number;    // Coalition coordination (final round)

  // Behavioral patterns (frequencies)
  patterns: {
    // "When they peek own card, they swap X% of the time"
    peekOwnThenSwap: number;

    // "When they have action card, they use it X% vs swap"
    actionCardUsageRate: number;

    // "Average turns before calling Vinto"
    vintoCallSpeed: number;

    // "They prefer removing cards over point reduction" (0-1)
    removalVsPointsPriority: number;

    // "They toss in X% of opportunities"
    tossInParticipation: number;
  };

  // Statistical tracking
  stats: {
    gamesObserved: number;
    actionsObserved: number;
    vintoCallsObserved: number;
    averageVintoScore: number;
  };

  // Confidence in profile (0-1, increases with observations)
  confidence: number;
}
```

### 2. Implement Opponent Profiler

```typescript
// packages/bot/src/lib/opponent-profiler.ts

export class OpponentProfiler {
  private profiles: Map<string, OpponentProfile> = new Map();

  /**
   * Get or create profile for opponent
   */
  getProfile(playerId: string): OpponentProfile {
    if (!this.profiles.has(playerId)) {
      this.profiles.set(playerId, this.createDefaultProfile(playerId));
    }
    return this.profiles.get(playerId)!;
  }

  /**
   * Update profile based on observed action
   */
  updateProfile(
    playerId: string,
    action: GameAction,
    context: ProfileContext
  ): void {
    const profile = this.getProfile(playerId);

    // Update relevant patterns based on action type
    switch (action.type) {
      case 'DRAW_CARD':
        this.updateDrawPatterns(profile, action, context);
        break;

      case 'SWAP_CARD':
        this.updateSwapPatterns(profile, action, context);
        break;

      case 'USE_CARD_ACTION':
        this.updateActionPatterns(profile, action, context);
        break;

      case 'CALL_VINTO':
        this.updateVintoPatterns(profile, action, context);
        break;

      case 'TOSS_IN_CARD':
        this.updateTossInPatterns(profile, action, context);
        break;
    }

    // Update stats
    profile.stats.actionsObserved++;

    // Recalculate derived metrics
    this.updateDerivedMetrics(profile);

    // Update confidence based on observations
    profile.confidence = Math.min(
      1.0,
      profile.stats.actionsObserved / 50
    );
  }

  /**
   * Predict likely action based on profile
   */
  predictAction(
    playerId: string,
    context: PredictionContext
  ): ActionPrediction {
    const profile = this.getProfile(playerId);

    // Low confidence? Return uncertain
    if (profile.confidence < 0.3) {
      return {
        likelyAction: 'unknown',
        confidence: 0.0,
        distribution: this.uniformDistribution(),
      };
    }

    // Build probability distribution over actions
    const distribution = this.buildActionDistribution(profile, context);

    // Select most likely action
    const likelyAction = this.selectMaxProbability(distribution);

    return {
      likelyAction,
      confidence: profile.confidence * distribution.get(likelyAction)!,
      distribution,
    };
  }

  /**
   * Detect if opponent is exploitable
   */
  findExploits(playerId: string): Exploit[] {
    const profile = this.getProfile(playerId);
    const exploits: Exploit[] = [];

    // Exploit 1: Predictable Vinto timing
    if (
      profile.patterns.vintoCallSpeed < 10 &&
      profile.stats.vintoCallsObserved > 2
    ) {
      exploits.push({
        type: 'predictable_vinto',
        description: `Opponent calls Vinto quickly (avg turn ${profile.patterns.vintoCallSpeed})`,
        counterStrategy: 'prevent_vinto',
      });
    }

    // Exploit 2: Overly risk-averse
    if (profile.riskTolerance < 0.3 && profile.confidence > 0.6) {
      exploits.push({
        type: 'risk_averse',
        description: 'Opponent rarely swaps unknown cards',
        counterStrategy: 'aggressive_play',
      });
    }

    // Exploit 3: Low cooperation
    if (profile.cooperativeness < 0.4 && profile.confidence > 0.5) {
      exploits.push({
        type: 'low_cooperation',
        description: 'Opponent does not coordinate well in coalitions',
        counterStrategy: 'expect_competition',
      });
    }

    // Exploit 4: Action card hoarding
    if (
      profile.patterns.actionCardUsageRate < 0.3 &&
      profile.stats.actionsObserved > 20
    ) {
      exploits.push({
        type: 'action_hoarding',
        description: 'Opponent rarely uses action cards immediately',
        counterStrategy: 'force_hand_size',
      });
    }

    return exploits;
  }

  /**
   * Update patterns from swap actions
   */
  private updateSwapPatterns(
    profile: OpponentProfile,
    action: GameAction,
    context: ProfileContext
  ): void {
    // Track swap behavior: known vs unknown cards
    const swapPosition = action.payload.position;
    const wasKnownCard = context.knownPositions?.includes(swapPosition);

    if (wasKnownCard) {
      // Swapping known card: safe play
      profile.riskTolerance = this.exponentialAverage(
        profile.riskTolerance,
        0.3,
        0.1
      );
    } else {
      // Swapping unknown card: risky play
      profile.riskTolerance = this.exponentialAverage(
        profile.riskTolerance,
        0.7,
        0.1
      );
    }

    // Check if swap followed a peek
    if (context.lastAction === 'PEEK_OWN') {
      // Update peek-then-swap pattern
      profile.patterns.peekOwnThenSwap = this.updateRate(
        profile.patterns.peekOwnThenSwap,
        1.0,
        context.peekThenSwapCount || 0
      );
    }

    // Analyze swap motivation: score vs hand size
    const drawnCardValue = getCardValue(action.payload.drawnCard.rank);
    if (drawnCardValue < 4) {
      // Swapped for low card: prioritizes points
      profile.patterns.removalVsPointsPriority = this.exponentialAverage(
        profile.patterns.removalVsPointsPriority,
        0.3,
        0.1
      );
    }
  }

  /**
   * Update patterns from action card usage
   */
  private updateActionPatterns(
    profile: OpponentProfile,
    action: GameAction,
    context: ProfileContext
  ): void {
    // Opponent used action card
    profile.patterns.actionCardUsageRate = this.updateRate(
      profile.patterns.actionCardUsageRate,
      1.0,
      context.actionOpportunities || 0
    );
  }

  /**
   * Update patterns from Vinto calls
   */
  private updateVintoPatterns(
    profile: OpponentProfile,
    action: GameAction,
    context: ProfileContext
  ): void {
    profile.stats.vintoCallsObserved++;

    // Update average Vinto call speed (turn number)
    const turnNumber = context.turnNumber || 0;
    profile.patterns.vintoCallSpeed = this.exponentialAverage(
      profile.patterns.vintoCallSpeed,
      turnNumber,
      0.3
    );

    // Update average Vinto score
    const score = context.playerScore || 0;
    profile.stats.averageVintoScore = this.exponentialAverage(
      profile.stats.averageVintoScore,
      score,
      0.3
    );

    // Derive aggression from Vinto timing
    // Early Vinto (< 8 turns) = aggressive
    // Late Vinto (> 15 turns) = cautious
    if (turnNumber < 8) {
      profile.aggression = Math.min(1.0, profile.aggression + 0.2);
    } else if (turnNumber > 15) {
      profile.aggression = Math.max(0.0, profile.aggression - 0.1);
    }
  }

  /**
   * Update patterns from toss-in participation
   */
  private updateTossInPatterns(
    profile: OpponentProfile,
    action: GameAction,
    context: ProfileContext
  ): void {
    // Toss-in opportunity taken
    profile.patterns.tossInParticipation = this.updateRate(
      profile.patterns.tossInParticipation,
      1.0,
      context.tossInOpportunities || 0
    );

    // Toss-in suggests aggressive play
    profile.aggression = this.exponentialAverage(
      profile.aggression,
      0.7,
      0.1
    );
  }

  /**
   * Update derived metrics from patterns
   */
  private updateDerivedMetrics(profile: OpponentProfile): void {
    // Aggression: derived from Vinto call speed
    if (profile.patterns.vintoCallSpeed < 10) {
      profile.aggression = Math.max(profile.aggression, 0.7);
    } else if (profile.patterns.vintoCallSpeed > 15) {
      profile.aggression = Math.min(profile.aggression, 0.4);
    }

    // Cooperativeness: observed in coalition scenarios
    // (Will be updated from coalition signaling in Ticket #5)
  }

  /**
   * Build action probability distribution
   */
  private buildActionDistribution(
    profile: OpponentProfile,
    context: PredictionContext
  ): Map<string, number> {
    const distribution = new Map<string, number>();

    // Base probabilities
    distribution.set('draw', 0.5);
    distribution.set('take_discard', 0.5);

    // Adjust based on profile
    if (context.discardIsAction) {
      // Action cards are attractive
      const takeProbability = 0.3 + profile.patterns.actionCardUsageRate * 0.5;
      distribution.set('take_discard', takeProbability);
      distribution.set('draw', 1.0 - takeProbability);
    }

    // Normalize
    return this.normalize(distribution);
  }

  /**
   * Exponential moving average for smooth updates
   */
  private exponentialAverage(
    current: number,
    newValue: number,
    alpha: number
  ): number {
    return alpha * newValue + (1 - alpha) * current;
  }

  /**
   * Update rate (frequency) metric
   */
  private updateRate(
    currentRate: number,
    outcome: number,
    totalOpportunities: number
  ): number {
    if (totalOpportunities === 0) return currentRate;
    return (currentRate * totalOpportunities + outcome) / (totalOpportunities + 1);
  }

  /**
   * Create default profile with neutral values
   */
  private createDefaultProfile(playerId: string): OpponentProfile {
    return {
      playerId,
      aggression: 0.5,
      riskTolerance: 0.5,
      cooperativeness: 0.5,
      patterns: {
        peekOwnThenSwap: 0.5,
        actionCardUsageRate: 0.5,
        vintoCallSpeed: 12,
        removalVsPointsPriority: 0.5,
        tossInParticipation: 0.5,
      },
      stats: {
        gamesObserved: 0,
        actionsObserved: 0,
        vintoCallsObserved: 0,
        averageVintoScore: 10,
      },
      confidence: 0.0,
    };
  }

  private selectMaxProbability(distribution: Map<string, number>): string {
    let maxProb = 0;
    let maxAction = 'unknown';

    for (const [action, prob] of distribution) {
      if (prob > maxProb) {
        maxProb = prob;
        maxAction = action;
      }
    }

    return maxAction;
  }

  private uniformDistribution(): Map<string, number> {
    return new Map([
      ['draw', 0.5],
      ['take_discard', 0.5],
    ]);
  }

  private normalize(distribution: Map<string, number>): Map<string, number> {
    const total = Array.from(distribution.values()).reduce((a, b) => a + b, 0);
    const normalized = new Map<string, number>();

    for (const [key, value] of distribution) {
      normalized.set(key, value / total);
    }

    return normalized;
  }
}

export interface ProfileContext {
  turnNumber?: number;
  playerScore?: number;
  knownPositions?: number[];
  lastAction?: string;
  peekThenSwapCount?: number;
  actionOpportunities?: number;
  tossInOpportunities?: number;
}

export interface PredictionContext {
  turnNumber: number;
  discardIsAction: boolean;
  gamePhase: 'early' | 'mid' | 'late' | 'final';
}

export interface ActionPrediction {
  likelyAction: string;
  confidence: number;
  distribution: Map<string, number>;
}

export interface Exploit {
  type: string;
  description: string;
  counterStrategy: string;
}
```

### 3. Integrate with OpponentModeler

```typescript
// packages/bot/src/lib/opponent-modeler.ts

export class OpponentModeler {
  private profiler: OpponentProfiler;

  constructor() {
    this.profiler = new OpponentProfiler();
  }

  /**
   * Update opponent model from observed action
   */
  observeAction(
    playerId: string,
    action: GameAction,
    context: ObservationContext
  ): void {
    // Update behavioral profile
    this.profiler.updateProfile(playerId, action, {
      turnNumber: context.turnNumber,
      playerScore: context.playerScore,
      knownPositions: context.knownPositions,
    });

    // ... existing belief updates
  }

  /**
   * Get opponent's behavioral profile
   */
  getProfile(playerId: string): OpponentProfile {
    return this.profiler.getProfile(playerId);
  }

  /**
   * Predict opponent's next action
   */
  predictNextAction(
    playerId: string,
    context: PredictionContext
  ): ActionPrediction {
    return this.profiler.predictAction(playerId, context);
  }

  /**
   * Find exploitable patterns
   */
  findExploits(playerId: string): Exploit[] {
    return this.profiler.findExploits(playerId);
  }
}
```

### 4. Use Profiles for Strategy Adaptation

```typescript
// packages/bot/src/lib/strategy-selector.ts (enhancement)

export class StrategySelector {
  static selectStrategy(
    state: MCTSGameState,
    personality: BotPersonality
  ): GameStrategy {
    // ... existing strategy selection

    // Check for exploitable opponents
    const exploits = this.findExploitableOpponents(state);

    if (exploits.length > 0) {
      const exploit = exploits[0]; // Target most exploitable

      if (exploit.type === 'predictable_vinto') {
        return {
          goal: 'prevent_vinto',
          confidence: 0.9,
          horizon: 3,
          priority: ['disrupt_target', 'delay_game'],
          targetPlayer: exploit.playerId,
        };
      }

      if (exploit.type === 'risk_averse') {
        return {
          goal: 'aggressive_play',
          confidence: 0.8,
          horizon: 2,
          priority: ['force_swaps', 'create_uncertainty'],
        };
      }
    }

    // ... default strategy
  }

  private static findExploitableOpponents(
    state: MCTSGameState
  ): Array<{ playerId: string; type: string }> {
    const exploitable: Array<{ playerId: string; type: string }> = [];

    for (const player of state.players) {
      if (player.id === state.botPlayerId) continue;

      const exploits = state.opponentModeler?.findExploits(player.id) || [];

      for (const exploit of exploits) {
        exploitable.push({
          playerId: player.id,
          type: exploit.type,
        });
      }
    }

    return exploitable;
  }
}
```

## Implementation Steps

### Step 1: Create Profile Types
- [ ] Create `packages/bot/src/lib/opponent-profile-types.ts`
- [ ] Define `OpponentProfile`, contexts, predictions
- [ ] Export from `packages/bot/src/lib/index.ts`

### Step 2: Implement Opponent Profiler
- [ ] Create `packages/bot/src/lib/opponent-profiler.ts`
- [ ] Implement profile tracking and updates
- [ ] Implement pattern detection methods
- [ ] Implement derived metric calculations
- [ ] Add exploit detection

### Step 3: Integrate with OpponentModeler
- [ ] Add `OpponentProfiler` to `OpponentModeler`
- [ ] Update `observeAction()` to profile opponents
- [ ] Implement profile accessor methods
- [ ] Test integration

### Step 4: Use Profiles in Strategy Selection
- [ ] Enhance `StrategySelector` with exploit detection
- [ ] Add adaptive strategies based on profiles
- [ ] Test strategy adaptation

### Step 5: Testing
- [ ] Unit tests for `OpponentProfiler`
- [ ] Test pattern updates from various actions
- [ ] Test exploit detection
- [ ] Integration tests with full game scenarios
- [ ] Benchmark profile overhead

### Step 6: Documentation
- [ ] Document profiling system
- [ ] Add examples of profile usage
- [ ] Document exploit types
- [ ] Update `packages/bot/README.md`

## Success Criteria

- [ ] Profiles track 5+ behavioral patterns
- [ ] Profile confidence increases with observations
- [ ] Exploit detection identifies 3+ exploit types
- [ ] Strategy adapts based on opponent profiles
- [ ] Performance overhead <5ms per action
- [ ] Unit tests achieve >85% coverage
- [ ] Bot vs bot tests show adaptive behavior

## Expected Impact

### Before (Current)
```typescript
// Bot treats all opponents identically
// No adaptation to play styles
```

### After (Profiled)
```typescript
// Opponent A: Aggressive, calls Vinto at turn 7
profile: { aggression: 0.9, vintoCallSpeed: 7 }
botStrategy: 'prevent_vinto' (target: Opponent A)

// Opponent B: Risk-averse, never swaps unknown cards
profile: { riskTolerance: 0.2 }
botStrategy: 'aggressive_play' (exploit predictability)

// Opponent C: Cooperative in coalitions
profile: { cooperativeness: 0.85 }
botStrategy: 'coalition_champion' (expect support from C)
```

## Integration Notes

### Personality Integration
Bot personalities influence profiling:
- Analytical personalities rely more on profiles
- Aggressive personalities exploit weaknesses
- Cooperative personalities adapt to team dynamics

### Explanation Integration
Show profiling in explanations:
```typescript
humanReadable: "I'm targeting Player 3 because they're predictable - they always call Vinto around turn 8, and it's turn 7 now."
```

## Dependencies

- Current `OpponentModeler` infrastructure
- Phase 1, Ticket #2: Personality System (adaptive strategies)

## Related Tickets

- Phase 1, Ticket #3: Hierarchical Strategy (adaptive strategy selection)
- Phase 2, Ticket #4: Belief Propagation (belief tracking foundation)
- Phase 2, Ticket #5: Coalition Signaling (cooperativeness patterns)

## References

- Opponent modeler: `packages/bot/src/lib/opponent-modeler.ts`
- Strategy selector: `packages/bot/src/lib/strategy-selector.ts` (to be created in Phase 1)
- Game actions: `packages/engine/src/lib/types/GameAction.ts`
