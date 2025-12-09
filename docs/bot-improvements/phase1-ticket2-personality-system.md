# Phase 1 - Ticket #2: Personality System

**Priority:** HIGH
**Complexity:** LOW
**Estimated Time:** 2-3 days

## Overview

Extend the existing difficulty system to create distinct bot personalities with behavioral quirks and play styles. This makes bots feel more human, varied, and engaging to play against.

## Current State

The bot has difficulty-based configurations (`MCTS_DIFFICULTY_CONFIGS`):
- **Easy**: 500 iterations, exploration=1.8
- **Moderate**: 2000 iterations, exploration=1.6
- **Hard**: 5000 iterations, exploration=1.4

**Problem:** All bots at the same difficulty level play identically. This makes the game feel mechanical and repetitive.

## Goals

1. Create named bot personalities with distinct characteristics
2. Add behavioral parameters (aggression, risk tolerance, quirks)
3. Implement "mistake rate" for realistic human-like play
4. Make personalities configurable and extensible
5. Maintain backward compatibility with difficulty levels

## Technical Design

### 1. Define Personality Interface

```typescript
// packages/bot/src/lib/personality-types.ts

export interface BotPersonality {
  name: string;
  description: string;

  // Core MCTS settings (inherit from difficulty)
  baseConfig: MCTSConfig;

  // Behavioral parameters (0-1 range)
  aggression: number;        // How quickly they call Vinto
  riskTolerance: number;     // Willingness to take uncertain swaps
  cooperativeness: number;   // Coalition coordination level

  // Skill modifiers
  mistakeRate: number;       // % chance to play 2nd/3rd best move
  beliefAccuracy: number;    // How well they track opponent cards

  // Behavioral quirks (optional)
  quirks?: {
    favoriteCard?: Rank;     // Slight preference for keeping this
    hatesToLose?: boolean;   // Takes risks when behind
    showsOff?: boolean;      // Prefers flashy plays
    cautious?: boolean;      // Avoids high-variance moves
  };
}
```

### 2. Create Personality Presets

```typescript
// packages/bot/src/lib/personality-presets.ts

export const BOT_PERSONALITIES: Record<string, BotPersonality> = {
  'cautious-carl': {
    name: 'Cautious Carl',
    description: 'Plays it safe, rarely takes risks',
    baseConfig: MCTS_DIFFICULTY_CONFIGS.moderate,
    aggression: 0.3,
    riskTolerance: 0.2,
    cooperativeness: 0.8,
    mistakeRate: 0.05,
    beliefAccuracy: 0.7,
    quirks: {
      cautious: true,
    },
  },

  'aggressive-anna': {
    name: 'Aggressive Anna',
    description: 'Fast-paced, high-risk player',
    baseConfig: MCTS_DIFFICULTY_CONFIGS.hard,
    aggression: 0.9,
    riskTolerance: 0.7,
    cooperativeness: 0.5,
    mistakeRate: 0.02,
    beliefAccuracy: 0.8,
    quirks: {
      hatesToLose: true,
    },
  },

  'strategic-sam': {
    name: 'Strategic Sam',
    description: 'Methodical, information-focused',
    baseConfig: MCTS_DIFFICULTY_CONFIGS.hard,
    aggression: 0.5,
    riskTolerance: 0.4,
    cooperativeness: 0.9,
    mistakeRate: 0.01,
    beliefAccuracy: 0.95,
    quirks: {},
  },

  'lucky-lucy': {
    name: 'Lucky Lucy',
    description: 'Unpredictable, follows intuition',
    baseConfig: MCTS_DIFFICULTY_CONFIGS.moderate,
    aggression: 0.6,
    riskTolerance: 0.8,
    cooperativeness: 0.6,
    mistakeRate: 0.15,
    beliefAccuracy: 0.6,
    quirks: {
      showsOff: true,
      favoriteCard: 'Q',
    },
  },

  'calculating-charlie': {
    name: 'Calculating Charlie',
    description: 'Optimal play, no nonsense',
    baseConfig: MCTS_DIFFICULTY_CONFIGS.hard,
    aggression: 0.6,
    riskTolerance: 0.5,
    cooperativeness: 0.7,
    mistakeRate: 0.0,
    beliefAccuracy: 1.0,
    quirks: {},
  },
};
```

### 3. Update MCTSBotDecisionService

```typescript
// packages/bot/src/lib/mcts-bot-decision.ts

export class MCTSBotDecisionService implements BotDecisionService {
  private personality: BotPersonality;

  constructor(
    difficulty: Difficulty,
    personalityName?: string
  ) {
    // Load personality or default based on difficulty
    this.personality = personalityName
      ? BOT_PERSONALITIES[personalityName]
      : this.getDefaultPersonality(difficulty);

    this.config = this.personality.baseConfig;
    this.botMemory = new BotMemory('', difficulty);
  }

  private getDefaultPersonality(difficulty: Difficulty): BotPersonality {
    // Map difficulty to default personalities
    const mapping = {
      easy: 'cautious-carl',
      moderate: 'strategic-sam',
      hard: 'calculating-charlie',
    };
    return BOT_PERSONALITIES[mapping[difficulty]];
  }
}
```

### 4. Implement Personality-Influenced Decisions

#### Mistake Rate
```typescript
// Apply mistakes after MCTS decision
private applyMistakeRate(bestMove: MCTSMove, allMoves: MCTSMove[]): MCTSMove {
  if (Math.random() < this.personality.mistakeRate) {
    // Play a suboptimal move
    const alternatives = allMoves
      .filter(m => m !== bestMove)
      .sort((a, b) => b.visits - a.visits);

    if (alternatives.length > 0) {
      // Pick 2nd or 3rd best move
      const index = Math.floor(Math.random() * Math.min(2, alternatives.length));
      console.log(`[Personality] ${this.personality.name} making a mistake (${this.personality.mistakeRate * 100}% chance)`);
      return alternatives[index];
    }
  }
  return bestMove;
}
```

#### Aggression (Vinto Timing)
```typescript
shouldCallVinto(context: BotDecisionContext): boolean {
  // ... existing validation logic

  // Adjust threshold based on aggression
  const baseThreshold = validation.confidence > 0.4;
  const aggressiveThreshold = validation.confidence > (0.4 - this.personality.aggression * 0.2);

  return this.personality.aggression > 0.7 ? aggressiveThreshold : baseThreshold;
}
```

#### Risk Tolerance (Swap Decisions)
```typescript
selectBestSwapPosition(drawnCard: Card, context: BotDecisionContext): number | null {
  // ... existing outcome calculation

  // Apply risk tolerance to unknown card swaps
  if (!context.botPlayer.knownCardPositions.includes(bestPosition)) {
    const riskPenalty = (1 - this.personality.riskTolerance) * 50;
    bestScore -= riskPenalty;
  }

  // ... rest of logic
}
```

#### Favorite Card Quirk
```typescript
private applyFavoriteCardQuirk(outcome: SwapOutcome, card: Card): number {
  if (this.personality.quirks?.favoriteCard === card.rank) {
    // Small bonus for keeping favorite card
    return 10;
  }
  return 0;
}
```

## Implementation Steps

### Step 1: Create Personality Types
- [ ] Create `packages/bot/src/lib/personality-types.ts`
- [ ] Define `BotPersonality` interface
- [ ] Define personality quirks types
- [ ] Export from `packages/bot/src/lib/index.ts`

### Step 2: Create Personality Presets
- [ ] Create `packages/bot/src/lib/personality-presets.ts`
- [ ] Define 5+ distinct personalities
- [ ] Balance personalities for variety
- [ ] Document each personality's play style

### Step 3: Integrate Personality into MCTSBotDecisionService
- [ ] Add `personality` property
- [ ] Update constructor to accept personality name
- [ ] Implement `getDefaultPersonality()` mapping
- [ ] Load personality config

### Step 4: Implement Personality Behaviors

#### Mistake Rate
- [ ] Create `applyMistakeRate()` method
- [ ] Apply after MCTS decision in `runMCTS()`
- [ ] Test with different mistake rates

#### Aggression
- [ ] Modify `shouldCallVinto()` threshold based on aggression
- [ ] Adjust exploration constant based on aggression
- [ ] Test aggressive vs cautious personalities

#### Risk Tolerance
- [ ] Modify `selectBestSwapPosition()` to penalize unknown swaps
- [ ] Test risk-averse vs risk-seeking behaviors

#### Cooperativeness
- [ ] Will be used in Phase 2 (Coalition Signaling)
- [ ] Add placeholder for future use

#### Quirks
- [ ] Implement favorite card bonus
- [ ] Implement "hates to lose" behavior (take risks when behind)
- [ ] Implement "shows off" behavior (prefer flashy actions)

### Step 5: Update BotFactory
- [ ] Update `BotDecisionServiceFactory.create()` to accept personality
- [ ] Add personality selection to bot creation

### Step 6: Testing
- [ ] Write unit tests for personality loading
- [ ] Test mistake rate distribution
- [ ] Test aggression/risk tolerance modifiers
- [ ] Test quirk behaviors
- [ ] Benchmark bot vs bot with different personalities

### Step 7: Documentation
- [ ] Document personality system in `packages/bot/README.md`
- [ ] Add personality selection guide
- [ ] Document how to create custom personalities

## Example Usage

```typescript
// Create bot with specific personality
const bot = new MCTSBotDecisionService('moderate', 'aggressive-anna');

// Create bot with default personality for difficulty
const bot2 = new MCTSBotDecisionService('hard');

// Access personality info
console.log(bot.personality.name); // "Aggressive Anna"
console.log(bot.personality.description); // "Fast-paced, high-risk player"
```

## Success Criteria

- [ ] 5+ distinct personalities implemented
- [ ] Personalities exhibit observable behavioral differences
- [ ] Mistake rate produces realistic human-like errors
- [ ] Personalities maintain appropriate difficulty level
- [ ] Backward compatible with existing difficulty system
- [ ] Unit tests cover all personality behaviors
- [ ] Bot vs bot benchmarks show personality diversity

## Future Enhancements

### Adaptive Personalities
Allow personalities to "learn" and adapt during gameplay:
```typescript
interface AdaptivePersonality extends BotPersonality {
  adaptationRate: number;
  updateFromGame(gameResult: GameResult): void;
}
```

### Player-Facing Names
In UI, show bot personality:
```typescript
// "You are playing against Aggressive Anna"
// "Cautious Carl is thinking..."
```

## Integration Notes

### UI Integration (Future Work)
- Display bot personality name in player list
- Show personality description in bot profile
- Allow player to select opponent personalities
- Add personality icons/avatars

### Benchmarking
Use personality system to create diverse test scenarios:
```typescript
// Test how each personality performs
personalities.forEach(p => {
  runBenchmark(p);
});
```

## Dependencies

None - this ticket builds on existing infrastructure.

## Related Tickets

- Phase 1, Ticket #1: Explainable Decisions (can mention personality in explanations)
- Phase 2, Ticket #5: Coalition Signaling (uses cooperativeness parameter)

## References

- Current difficulty configs: `packages/bot/src/lib/mcts-types.ts:265-287`
- Bot factory: `packages/bot/src/lib/bot-factory.ts`
- MCTS decision service: `packages/bot/src/lib/mcts-bot-decision.ts`
