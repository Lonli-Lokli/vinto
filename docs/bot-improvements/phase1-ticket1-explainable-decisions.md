# Phase 1 - Ticket #1: Explainable Decisions

**Priority:** HIGH
**Complexity:** LOW-MEDIUM
**Estimated Time:** 2-3 days

## Overview

Structure the existing console logging infrastructure to provide user-facing explanations for bot decisions. This enhances user experience, aids debugging, and provides educational value for players learning strategy.

## Current State

The bot already logs extensive decision information to the console:
- MCTS best moves with visit counts and rewards (`mcts-bot-decision.ts:484-487`)
- Swap position evaluations (`mcts-bot-decision.ts:318-356`)
- Heuristic decisions (`mcts-bot-decision.ts:84-86, 116-119`)

**Problem:** These logs are unstructured, developer-focused, and not visible to users.

## Goals

1. Create a structured `DecisionExplanation` interface
2. Generate natural language explanations for all bot decisions
3. Make explanations available to the UI layer
4. Include confidence scores and alternative actions considered

## Technical Design

### 1. Create Explanation Types

```typescript
// packages/bot/src/lib/explanation-types.ts

export interface DecisionExplanation {
  action: string;
  confidence: number;

  reasoning: {
    primaryReason: string;
    alternatives: AlternativeAction[];
    factors: DecisionFactor[];
  };

  humanReadable: string;
}

export interface AlternativeAction {
  action: string;
  reason: string;
  whyRejected: string;
}

export interface DecisionFactor {
  name: string;
  value: number;
  impact: 'positive' | 'negative';
}
```

### 2. Create Explanation Generator Service

```typescript
// packages/bot/src/lib/decision-explainer.ts

export class DecisionExplainer {
  /**
   * Generate explanation for turn decision
   */
  static explainTurnDecision(
    decision: BotTurnDecision,
    context: BotDecisionContext,
    mctsResult?: MCTSResult
  ): DecisionExplanation;

  /**
   * Generate explanation for action usage decision
   */
  static explainActionDecision(
    shouldUse: boolean,
    card: Card,
    context: BotDecisionContext
  ): DecisionExplanation;

  /**
   * Generate explanation for swap position selection
   */
  static explainSwapDecision(
    position: number | null,
    drawnCard: Card,
    outcomes: SwapOutcome[]
  ): DecisionExplanation;

  /**
   * Generate explanation for Vinto call
   */
  static explainVintoDecision(
    shouldCall: boolean,
    validation: VintoValidation,
    context: BotDecisionContext
  ): DecisionExplanation;
}
```

### 3. Update BotDecisionService Interface

```typescript
// packages/bot/src/lib/shapes.ts

export interface BotDecisionService {
  // Existing methods...

  // Add explanation methods
  getLastDecisionExplanation(): DecisionExplanation | null;
}
```

### 4. Integrate into MCTSBotDecisionService

Update `MCTSBotDecisionService` to generate explanations:

```typescript
// packages/bot/src/lib/mcts-bot-decision.ts

export class MCTSBotDecisionService implements BotDecisionService {
  private lastExplanation: DecisionExplanation | null = null;

  decideTurnAction(context: BotDecisionContext): BotTurnDecision {
    // ... existing logic

    // Generate explanation
    this.lastExplanation = DecisionExplainer.explainTurnDecision(
      result,
      context,
      mctsResult
    );

    return result;
  }

  getLastDecisionExplanation(): DecisionExplanation | null {
    return this.lastExplanation;
  }
}
```

## Implementation Steps

### Step 1: Create Types and Interfaces
- [ ] Create `packages/bot/src/lib/explanation-types.ts`
- [ ] Define `DecisionExplanation`, `AlternativeAction`, `DecisionFactor` interfaces
- [ ] Export from `packages/bot/src/lib/index.ts`

### Step 2: Implement DecisionExplainer Service
- [ ] Create `packages/bot/src/lib/decision-explainer.ts`
- [ ] Implement `explainTurnDecision()` method
- [ ] Implement `explainActionDecision()` method
- [ ] Implement `explainSwapDecision()` method
- [ ] Implement `explainVintoDecision()` method
- [ ] Add helper methods for natural language generation

### Step 3: Integrate with MCTSBotDecisionService
- [ ] Add `lastExplanation` property
- [ ] Update `decideTurnAction()` to generate explanations
- [ ] Update `shouldUseAction()` to generate explanations
- [ ] Update `selectBestSwapPosition()` to generate explanations
- [ ] Update `shouldCallVinto()` to generate explanations
- [ ] Implement `getLastDecisionExplanation()` method

### Step 4: Update BotDecisionService Interface
- [ ] Add `getLastDecisionExplanation()` to interface
- [ ] Update `StrategicBotDecisionService` to implement new method (return null for now)

### Step 5: Testing
- [ ] Write unit tests for `DecisionExplainer`
- [ ] Test explanation generation for all decision types
- [ ] Verify human-readable strings are clear and informative
- [ ] Test with different difficulty levels

### Step 6: Documentation
- [ ] Update `packages/bot/README.md` with explanation feature
- [ ] Add JSDoc comments to all new interfaces and methods
- [ ] Create example usage documentation

## Example Outputs

### Turn Decision Explanation
```typescript
{
  action: "draw",
  confidence: 0.85,
  reasoning: {
    primaryReason: "Discard pile top is a played 5, no action value",
    alternatives: [
      {
        action: "take-discard",
        reason: "Get a known card",
        whyRejected: "Card has no action and low strategic value"
      }
    ],
    factors: [
      { name: "discard_action_value", value: 0, impact: "negative" },
      { name: "draw_pile_size", value: 35, impact: "positive" },
      { name: "information_value", value: 0.5, impact: "positive" }
    ]
  },
  humanReadable: "I'm drawing from the deck because the discard pile has a played 5 with no action. Drawing gives us a chance to get a better card or useful action."
}
```

### Swap Decision Explanation
```typescript
{
  action: "swap position 2",
  confidence: 0.92,
  reasoning: {
    primaryReason: "Drew a 3, which is lower than my known 7 at position 2",
    alternatives: [
      {
        action: "discard",
        reason: "Keep current hand",
        whyRejected: "7 is too high, reducing score is priority"
      },
      {
        action: "swap position 0",
        reason: "Swap unknown card",
        whyRejected: "Position 2 has known high card (7)"
      }
    ],
    factors: [
      { name: "point_reduction", value: 4, impact: "positive" },
      { name: "card_knowledge", value: 0.8, impact: "positive" },
      { name: "cascade_potential", value: 2, impact: "positive" }
    ]
  },
  humanReadable: "I swapped my 7 for the 3 I drew. This saves 4 points, and the 7 I discarded might trigger a toss-in cascade!"
}
```

## Success Criteria

- [ ] All bot decision types generate structured explanations
- [ ] Explanations include confidence scores (0-1 range)
- [ ] Human-readable strings are clear, concise (1-3 sentences)
- [ ] Alternative actions are listed with rejection reasons
- [ ] Key decision factors are quantified
- [ ] Unit tests achieve >90% coverage for explainer
- [ ] No performance degradation (explanations add <5ms per decision)

## Integration Notes

### UI Integration (Future Work)
This ticket focuses on the bot infrastructure. UI display will be a separate task:
- Add explanation display to game UI
- Show bot "thought process" during turns
- Add toggle for showing/hiding explanations
- Consider accessibility (screen readers)

### Logging
Keep existing console logs for debugging, but mark them as debug-level:
```typescript
if (process.env.NODE_ENV === 'development') {
  console.log('[MCTS Debug]', ...);
}
```

## Dependencies

None - this ticket builds on existing infrastructure.

## Related Tickets

- Phase 1, Ticket #2: Personality System (can use explanations to show personality)
- Phase 1, Ticket #3: Hierarchical Strategy (can explain strategic goals)

## References

- Current logging: `packages/bot/src/lib/mcts-bot-decision.ts`
- MCTS result structure: `packages/bot/src/lib/mcts-types.ts`
- Bot context: `packages/bot/src/lib/shapes.ts`
