# Phase 1 - Ticket #3: Hierarchical Strategy Formalization

**Priority:** MEDIUM
**Complexity:** LOW-MEDIUM
**Estimated Time:** 2-3 days

## Overview

Formalize the implicit strategic decision-making already present in the codebase into an explicit hierarchical system. This separates high-level strategic goals from tactical execution, making the bot more maintainable and easier to enhance.

## Current State

Strategic thinking is currently embedded throughout the codebase:
- Coalition logic in `mcts-coalition-evaluator.ts`
- Vinto timing in `vinto-round-solver.ts`
- Heuristics in `mcts-bot-heuristics.ts`
- Action planning in `mcts-action-planning.ts`

**Problem:** Strategy is implicit and scattered. There's no clear separation between "what we're trying to achieve" (strategy) and "how to do it" (tactics).

## Goals

1. Define explicit strategy types (minimize_score, prevent_vinto, coalition_support, etc.)
2. Create a strategy selection layer that evaluates current game state
3. Connect strategies to MCTS evaluation functions
4. Make strategy visible in explanations and debugging
5. Prepare foundation for advanced strategy coordination (Phase 2)

## Technical Design

### 1. Define Strategy Types

```typescript
// packages/bot/src/lib/strategy-types.ts

/**
 * High-level strategic goals
 */
export type StrategyGoal =
  | 'minimize_score'        // Standard play: reduce own score
  | 'rush_vinto'           // Aggressive: call Vinto ASAP
  | 'control_game'         // Accumulate info and action cards
  | 'prevent_vinto'        // Defensive: block opponent's Vinto
  | 'coalition_champion'   // Final round: I need to win
  | 'coalition_support'    // Final round: help the champion
  | 'stall'               // Defensive: delay the game
  | 'kingmaker';          // Strategic: position a specific player to win

export interface GameStrategy {
  goal: StrategyGoal;
  confidence: number;      // How confident we are in this strategy
  horizon: number;         // How many turns to plan ahead
  priority: string[];      // Ordered list of tactical priorities

  // Context
  targetPlayer?: string;   // Relevant for prevent_vinto, kingmaker
  vintoThreshold?: number; // For rush_vinto, prevent_vinto
}

/**
 * Strategy configuration defines how to pursue each goal
 */
export interface StrategyConfig {
  goal: StrategyGoal;
  description: string;

  // Tactical priorities
  priorities: string[];

  // Evaluation weights
  evaluationWeights: {
    scoreReduction: number;
    informationGain: number;
    handSizeReduction: number;
    opponentDisruption: number;
    coalitionCooperation: number;
  };

  // Vinto timing
  vintoThreshold: number;
  vintoMinTurns: number;
}
```

### 2. Define Strategy Presets

```typescript
// packages/bot/src/lib/strategy-presets.ts

export const STRATEGY_CONFIGS: Record<StrategyGoal, StrategyConfig> = {
  minimize_score: {
    goal: 'minimize_score',
    description: 'Standard play: reduce score, gather information',
    priorities: ['remove_cards', 'reduce_points', 'gather_info'],
    evaluationWeights: {
      scoreReduction: 1.0,
      informationGain: 0.5,
      handSizeReduction: 0.3,
      opponentDisruption: 0.1,
      coalitionCooperation: 0.0,
    },
    vintoThreshold: 6,
    vintoMinTurns: 8,
  },

  rush_vinto: {
    goal: 'rush_vinto',
    description: 'Aggressive: minimize score and call Vinto early',
    priorities: ['remove_cards', 'minimize_points', 'call_vinto'],
    evaluationWeights: {
      scoreReduction: 1.5,
      informationGain: 0.2,
      handSizeReduction: 0.8,
      opponentDisruption: 0.0,
      coalitionCooperation: 0.0,
    },
    vintoThreshold: 8,
    vintoMinTurns: 6,
  },

  control_game: {
    goal: 'control_game',
    description: 'Methodical: accumulate information and action cards',
    priorities: ['gather_info', 'retain_action_cards', 'reduce_points'],
    evaluationWeights: {
      scoreReduction: 0.8,
      informationGain: 1.5,
      handSizeReduction: 0.2,
      opponentDisruption: 0.3,
      coalitionCooperation: 0.0,
    },
    vintoThreshold: 5,
    vintoMinTurns: 10,
  },

  prevent_vinto: {
    goal: 'prevent_vinto',
    description: 'Defensive: disrupt target player and delay Vinto',
    priorities: ['increase_target_score', 'deny_low_cards', 'use_attacks'],
    evaluationWeights: {
      scoreReduction: 0.5,
      informationGain: 0.8,
      handSizeReduction: 0.1,
      opponentDisruption: 1.5,
      coalitionCooperation: 0.3,
    },
    vintoThreshold: 4,
    vintoMinTurns: 12,
  },

  coalition_champion: {
    goal: 'coalition_champion',
    description: 'Final round: minimize score to beat Vinto caller',
    priorities: ['minimize_own_score', 'receive_support'],
    evaluationWeights: {
      scoreReduction: 2.0,
      informationGain: 0.3,
      handSizeReduction: 0.5,
      opponentDisruption: 0.0,
      coalitionCooperation: 1.0,
    },
    vintoThreshold: 0,
    vintoMinTurns: 0,
  },

  coalition_support: {
    goal: 'coalition_support',
    description: 'Final round: help coalition champion win',
    priorities: ['transfer_resources', 'attack_vinto_caller'],
    evaluationWeights: {
      scoreReduction: 0.2,
      informationGain: 0.5,
      handSizeReduction: 0.1,
      opponentDisruption: 0.5,
      coalitionCooperation: 2.0,
    },
    vintoThreshold: 0,
    vintoMinTurns: 0,
  },

  stall: {
    goal: 'stall',
    description: 'Defensive: delay game to gather information',
    priorities: ['gather_info', 'avoid_vinto', 'neutral_plays'],
    evaluationWeights: {
      scoreReduction: 0.5,
      informationGain: 1.0,
      handSizeReduction: 0.0,
      opponentDisruption: 0.3,
      coalitionCooperation: 0.0,
    },
    vintoThreshold: 3,
    vintoMinTurns: 15,
  },

  kingmaker: {
    goal: 'kingmaker',
    description: 'Strategic: position specific player to win',
    priorities: ['support_target', 'attack_others'],
    evaluationWeights: {
      scoreReduction: 0.3,
      informationGain: 0.5,
      handSizeReduction: 0.1,
      opponentDisruption: 1.0,
      coalitionCooperation: 1.5,
    },
    vintoThreshold: 4,
    vintoMinTurns: 10,
  },
};
```

### 3. Create Strategy Selector

```typescript
// packages/bot/src/lib/strategy-selector.ts

export class StrategySelector {
  /**
   * Determine the best strategy for current game state
   */
  static selectStrategy(
    state: MCTSGameState,
    personality: BotPersonality
  ): GameStrategy {
    // Final round: coalition strategies
    if (state.finalTurnTriggered) {
      return this.selectCoalitionStrategy(state);
    }

    // Check if opponent is close to Vinto
    const threateningOpponent = this.findThreateningOpponent(state);
    if (threateningOpponent) {
      return {
        goal: 'prevent_vinto',
        confidence: 0.8,
        horizon: 3,
        priority: STRATEGY_CONFIGS.prevent_vinto.priorities,
        targetPlayer: threateningOpponent,
      };
    }

    // Check if we have a strong position for rush
    if (this.shouldRushVinto(state, personality)) {
      return {
        goal: 'rush_vinto',
        confidence: 0.7,
        horizon: 2,
        priority: STRATEGY_CONFIGS.rush_vinto.priorities,
      };
    }

    // Default: standard play
    return {
      goal: 'minimize_score',
      confidence: 0.6,
      horizon: 4,
      priority: STRATEGY_CONFIGS.minimize_score.priorities,
    };
  }

  private static selectCoalitionStrategy(
    state: MCTSGameState
  ): GameStrategy {
    const bot = state.players[state.currentPlayerIndex];
    const coalition = state.players.filter(
      p => p.id !== state.vintoCallerId
    );

    // Am I the coalition leader (lowest score)?
    const isLeader = coalition.every(p => bot.score <= p.score);

    if (isLeader) {
      return {
        goal: 'coalition_champion',
        confidence: 0.9,
        horizon: 2,
        priority: STRATEGY_CONFIGS.coalition_champion.priorities,
      };
    }

    return {
      goal: 'coalition_support',
      confidence: 0.9,
      horizon: 2,
      priority: STRATEGY_CONFIGS.coalition_support.priorities,
      targetPlayer: state.coalitionLeaderId || bot.id,
    };
  }

  private static findThreateningOpponent(
    state: MCTSGameState
  ): string | null {
    const bot = state.players[state.currentPlayerIndex];
    const THREAT_THRESHOLD = 8;

    for (const opponent of state.players) {
      if (opponent.id === bot.id) continue;

      // Opponent has low score, might call Vinto soon
      if (opponent.score < THREAT_THRESHOLD && opponent.score < bot.score) {
        return opponent.id;
      }
    }

    return null;
  }

  private static shouldRushVinto(
    state: MCTSGameState,
    personality: BotPersonality
  ): boolean {
    const bot = state.players[state.currentPlayerIndex];

    // Need good score and aggressive personality
    if (bot.score > 10 || personality.aggression < 0.6) {
      return false;
    }

    // Check if we're leading
    const opponents = state.players.filter(p => p.id !== bot.id);
    const isLeading = opponents.every(o => bot.score < o.score);

    return isLeading && state.turnCount > 6;
  }
}
```

### 4. Integrate Strategy into MCTS

```typescript
// packages/bot/src/lib/mcts-bot-decision.ts

export class MCTSBotDecisionService implements BotDecisionService {
  private currentStrategy: GameStrategy | null = null;

  private runMCTS(rootState: MCTSGameState): MCTSMove {
    // Select strategy for this decision
    this.currentStrategy = StrategySelector.selectStrategy(
      rootState,
      this.personality
    );

    console.log(
      `[Strategy] ${this.currentStrategy.goal} (confidence: ${this.currentStrategy.confidence})`
    );

    // Run MCTS with strategy-aware evaluation
    // ... existing MCTS logic

    return bestMove;
  }

  // Expose strategy for explanations
  getCurrentStrategy(): GameStrategy | null {
    return this.currentStrategy;
  }
}
```

### 5. Update State Evaluator with Strategy Weights

```typescript
// packages/bot/src/lib/mcts-state-evaluator.ts

export function evaluateState(
  state: MCTSGameState,
  botId: string,
  strategy?: GameStrategy
): number {
  // ... existing evaluation logic

  // Apply strategy weights if provided
  if (strategy) {
    const config = STRATEGY_CONFIGS[strategy.goal];
    const weights = config.evaluationWeights;

    const weightedScore =
      scoreComponent * weights.scoreReduction +
      infoComponent * weights.informationGain +
      handSizeComponent * weights.handSizeReduction +
      disruptionComponent * weights.opponentDisruption +
      coalitionComponent * weights.coalitionCooperation;

    return weightedScore;
  }

  // ... return default evaluation
}
```

## Implementation Steps

### Step 1: Create Strategy Types
- [ ] Create `packages/bot/src/lib/strategy-types.ts`
- [ ] Define `StrategyGoal`, `GameStrategy`, `StrategyConfig` types
- [ ] Export from `packages/bot/src/lib/index.ts`

### Step 2: Define Strategy Presets
- [ ] Create `packages/bot/src/lib/strategy-presets.ts`
- [ ] Define 8 strategy configurations
- [ ] Balance evaluation weights
- [ ] Document each strategy's purpose

### Step 3: Implement Strategy Selector
- [ ] Create `packages/bot/src/lib/strategy-selector.ts`
- [ ] Implement `selectStrategy()` method
- [ ] Implement coalition strategy selection
- [ ] Implement threat detection
- [ ] Implement rush_vinto conditions

### Step 4: Integrate into MCTSBotDecisionService
- [ ] Add `currentStrategy` property
- [ ] Call `StrategySelector.selectStrategy()` in `runMCTS()`
- [ ] Log selected strategy
- [ ] Implement `getCurrentStrategy()` method

### Step 5: Update State Evaluator
- [ ] Modify `evaluateState()` to accept strategy parameter
- [ ] Apply strategy evaluation weights
- [ ] Test with different strategies

### Step 6: Connect to Explainable Decisions (if Ticket #1 done)
- [ ] Include strategy in decision explanations
- [ ] Show strategy goal in human-readable format

### Step 7: Testing
- [ ] Write unit tests for `StrategySelector`
- [ ] Test strategy selection for various game states
- [ ] Test strategy weight application
- [ ] Verify coalition strategy selection
- [ ] Benchmark bot with explicit strategies

### Step 8: Documentation
- [ ] Document strategy system in `packages/bot/README.md`
- [ ] Add strategy selection guide
- [ ] Document how to create custom strategies

## Example Outputs

### Strategy Selection
```typescript
// Early game, leading
{
  goal: 'rush_vinto',
  confidence: 0.7,
  horizon: 2,
  priority: ['remove_cards', 'minimize_points', 'call_vinto'],
}

// Opponent threatening
{
  goal: 'prevent_vinto',
  confidence: 0.8,
  horizon: 3,
  priority: ['increase_target_score', 'deny_low_cards', 'use_attacks'],
  targetPlayer: 'player-2',
}

// Final round, coalition leader
{
  goal: 'coalition_champion',
  confidence: 0.9,
  horizon: 2,
  priority: ['minimize_own_score', 'receive_support'],
}
```

## Success Criteria

- [ ] 8+ distinct strategies defined
- [ ] Strategy selector chooses appropriate strategy for game state
- [ ] Strategy weights measurably affect bot behavior
- [ ] Coalition strategies activate in final round
- [ ] Threat detection identifies risky opponents
- [ ] Unit tests cover all strategy selection paths
- [ ] Integration tests verify strategy-aware play

## Future Enhancements (Phase 2)

### Multi-Turn Strategic Planning
```typescript
interface StrategicPlan {
  strategy: GameStrategy;
  turnPlans: Map<number, TacticalPlan>;
  contingencies: Map<string, GameStrategy>; // "if opponent does X, switch to Y"
}
```

### Strategy Communication
Coalition members coordinate strategies:
```typescript
// "I'm going for champion, you support"
// "I'm attacking Vinto caller, join me"
```

## Integration Notes

### Personality Integration
Personalities influence strategy selection:
```typescript
// Aggressive personalities favor rush_vinto
// Cooperative personalities excel at coalition_support
// Cautious personalities prefer control_game
```

### Explanation Integration
Strategies appear in explanations:
```typescript
humanReadable: "I'm using a 'prevent Vinto' strategy because Player 2 has a low score (6). My action targets them to disrupt their plan."
```

## Dependencies

None - this ticket builds on existing infrastructure.

## Related Tickets

- Phase 1, Ticket #1: Explainable Decisions (can include strategy in explanations)
- Phase 1, Ticket #2: Personality System (personalities influence strategy)
- Phase 2, Ticket #5: Coalition Signaling (uses coalition strategies)

## References

- Coalition evaluator: `packages/bot/src/lib/mcts-coalition-evaluator.ts`
- Vinto solver: `packages/bot/src/lib/vinto-round-solver.ts`
- State evaluator: `packages/bot/src/lib/mcts-state-evaluator.ts`
- Action planning: `packages/bot/src/lib/mcts-action-planning.ts`
