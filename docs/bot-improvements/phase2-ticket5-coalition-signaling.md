# Phase 2 - Ticket #5: Coalition Signaling

**Priority:** HIGH
**Complexity:** MEDIUM
**Estimated Time:** 4-5 days

## Overview

Implement implicit communication between coalition members during the final round. Since players can't explicitly communicate, they signal intentions through action choices. This significantly improves coalition coordination and win rate against Vinto callers.

## Current State

The bot has coalition logic:
- `mcts-coalition-evaluator.ts` evaluates coalition scenarios
- Coalition members act independently during final round
- No coordination between coalition members

**Problem:** Coalition members don't coordinate strategy. They might both attack the Vinto caller inefficiently, or both try to be the champion.

**Example:** In final round with 3 coalition members:
- Member A (score 8) attacks Vinto caller
- Member B (score 6) also attacks Vinto caller
- Member C (score 7) tries to minimize own score

**Result:** Member B should be the champion (lowest score), but others don't know to support them.

## Goals

1. Define a signaling protocol for coalition coordination
2. Implement signal encoding (intention → action)
3. Implement signal decoding (action → intention)
4. Coordinate coalition strategy based on signals
5. Maintain game rule compliance (no explicit communication)

## Technical Design

### 1. Define Signaling Protocol

```typescript
// packages/bot/src/lib/coalition-signaling-types.ts

/**
 * Intentions that can be signaled through actions
 */
export type CoalitionSignal =
  | 'champion'           // "I'm best positioned to win, support me"
  | 'support'           // "I'm supporting the champion"
  | 'attack_vinto'      // "I'm attacking the Vinto caller"
  | 'transfer_resource' // "I'm giving away my good cards"
  | 'uncertain';        // No clear signal

export interface SignalIntent {
  signal: CoalitionSignal;
  confidence: number;
  targetPlayer?: string; // For support/attack signals
}

/**
 * Signal evidence from observed actions
 */
export interface SignalEvidence {
  playerId: string;
  turnNumber: number;
  action: string;
  context: SignalContext;
}

export interface SignalContext {
  playerScore: number;
  coalitionScores: number[];
  vintoCallerScore: number;
  isFirstCoalitionTurn: boolean;
}
```

### 2. Implement Signal Encoder

```typescript
// packages/bot/src/lib/coalition-signal-encoder.ts

export class CoalitionSignalEncoder {
  /**
   * Encode intention into action selection
   * Returns modified MCTS weights/priorities to signal intent
   */
  static encodeSignal(
    intent: SignalIntent,
    state: MCTSGameState,
    availableMoves: MCTSMove[]
  ): MCTSMove[] {
    switch (intent.signal) {
      case 'champion':
        return this.prioritizeChampionActions(availableMoves, state);

      case 'support':
        return this.prioritizeSupportActions(availableMoves, state, intent.targetPlayer);

      case 'attack_vinto':
        return this.prioritizeAttackActions(availableMoves, state);

      case 'transfer_resource':
        return this.prioritizeTransferActions(availableMoves, state);

      default:
        return availableMoves; // No signaling
    }
  }

  /**
   * Champion signal: Focus on minimizing own score
   * Signal pattern: Aggressive self-improvement actions
   */
  private static prioritizeChampionActions(
    moves: MCTSMove[],
    state: MCTSGameState
  ): MCTSMove[] {
    // Boost moves that reduce own score
    return moves.map(move => {
      if (this.isScoreReductionMove(move, state.botPlayerId)) {
        return { ...move, signalWeight: 2.0 };
      }
      return move;
    });
  }

  /**
   * Support signal: Use actions on coalition champion
   * Signal pattern: Using peek/swap actions on specific coalition member
   */
  private static prioritizeSupportActions(
    moves: MCTSMove[],
    state: MCTSGameState,
    championId?: string
  ): MCTSMove[] {
    if (!championId) return moves;

    return moves.map(move => {
      if (this.targetsPlayer(move, championId)) {
        return { ...move, signalWeight: 1.8 };
      }
      return move;
    });
  }

  /**
   * Attack signal: Use disruptive actions on Vinto caller
   * Signal pattern: Jack swaps, Ace draws targeting Vinto caller
   */
  private static prioritizeAttackActions(
    moves: MCTSMove[],
    state: MCTSGameState
  ): MCTSMove[] {
    const vintoCallerId = state.vintoCallerId;
    if (!vintoCallerId) return moves;

    return moves.map(move => {
      if (this.isDisruptiveMove(move, vintoCallerId)) {
        return { ...move, signalWeight: 1.5 };
      }
      return move;
    });
  }

  /**
   * Transfer signal: Give away good cards to coalition
   * Signal pattern: Using Jack to swap own good card to coalition member
   */
  private static prioritizeTransferActions(
    moves: MCTSMove[],
    state: MCTSGameState
  ): MCTSMove[] {
    return moves.map(move => {
      if (this.isResourceTransferMove(move, state)) {
        return { ...move, signalWeight: 1.3 };
      }
      return move;
    });
  }

  private static isScoreReductionMove(move: MCTSMove, playerId: string): boolean {
    // Check if move reduces player's own score
    return move.type === 'swap' || move.type === 'use-action';
  }

  private static targetsPlayer(move: MCTSMove, playerId: string): boolean {
    return move.targets?.some(t => t.playerId === playerId) || false;
  }

  private static isDisruptiveMove(move: MCTSMove, targetId: string): boolean {
    // Check if move disrupts target player (Jack swap, Ace draw, etc.)
    if (move.type !== 'use-action') return false;

    return move.targets?.some(t => t.playerId === targetId) || false;
  }

  private static isResourceTransferMove(move: MCTSMove, state: MCTSGameState): boolean {
    // Check if Jack swap moves bot's good card to coalition member
    if (move.type !== 'use-action' || !move.targets) return false;

    const fromTarget = move.targets[0];
    const toTarget = move.targets[1];

    // Is this moving from bot to coalition member?
    const isTransfer =
      fromTarget?.playerId === state.botPlayerId &&
      toTarget?.playerId !== state.botPlayerId &&
      toTarget?.playerId !== state.vintoCallerId;

    return isTransfer;
  }
}
```

### 3. Implement Signal Decoder

```typescript
// packages/bot/src/lib/coalition-signal-decoder.ts

export class CoalitionSignalDecoder {
  /**
   * Decode coalition member's intention from their action
   */
  static decodeSignal(
    evidence: SignalEvidence,
    history: SignalEvidence[]
  ): SignalIntent {
    const { action, context, playerId } = evidence;

    // Champion signal: Player consistently improving own position
    if (this.isChampionPattern(evidence, history)) {
      return {
        signal: 'champion',
        confidence: 0.8,
      };
    }

    // Support signal: Player using actions on same coalition member
    const supportTarget = this.detectSupportTarget(evidence, history);
    if (supportTarget) {
      return {
        signal: 'support',
        confidence: 0.7,
        targetPlayer: supportTarget,
      };
    }

    // Attack signal: Player disrupting Vinto caller
    if (this.isAttackPattern(evidence)) {
      return {
        signal: 'attack_vinto',
        confidence: 0.75,
        targetPlayer: evidence.context.vintoCallerScore.toString(),
      };
    }

    // Transfer signal: Player giving away resources
    if (this.isTransferPattern(evidence)) {
      return {
        signal: 'transfer_resource',
        confidence: 0.6,
      };
    }

    return {
      signal: 'uncertain',
      confidence: 0.0,
    };
  }

  /**
   * Detect champion pattern: focus on self-improvement
   */
  private static isChampionPattern(
    evidence: SignalEvidence,
    history: SignalEvidence[]
  ): boolean {
    const { action, playerId } = evidence;

    // Look for aggressive score reduction
    if (action.includes('swap') || action.includes('minimize')) {
      // Check if player has good score for champion role
      const isGoodScore = evidence.context.playerScore < 10;

      // Check if player is lowest in coalition
      const isLowestCoalition = evidence.context.coalitionScores.every(
        s => evidence.context.playerScore <= s
      );

      return isGoodScore && isLowestCoalition;
    }

    return false;
  }

  /**
   * Detect support pattern: helping specific coalition member
   */
  private static detectSupportTarget(
    evidence: SignalEvidence,
    history: SignalEvidence[]
  ): string | null {
    // Check if recent actions targeted same player
    const recentActions = history
      .filter(e => e.playerId === evidence.playerId)
      .slice(-3);

    if (recentActions.length < 2) return null;

    // Extract target from actions
    const targets = recentActions
      .map(e => this.extractActionTarget(e.action))
      .filter(t => t !== null);

    // If consistently targeting same player, that's support signal
    if (targets.length >= 2 && targets.every(t => t === targets[0])) {
      return targets[0];
    }

    return null;
  }

  /**
   * Detect attack pattern: disrupting Vinto caller
   */
  private static isAttackPattern(evidence: SignalEvidence): boolean {
    const { action } = evidence;

    // Check for disruptive actions
    const isDisruptive =
      action.includes('jack_swap') ||
      action.includes('ace_draw') ||
      action.includes('attack');

    // Check if targeting Vinto caller
    const targetingVinto = action.includes('vinto_caller');

    return isDisruptive && targetingVinto;
  }

  /**
   * Detect transfer pattern: giving away good cards
   */
  private static isTransferPattern(evidence: SignalEvidence): boolean {
    const { action, context } = evidence;

    // Check for Jack swap from self to coalition
    const isTransferAction = action.includes('jack_transfer');

    // Player should have higher score (not the champion)
    const notChampion = context.coalitionScores.some(
      s => s < context.playerScore
    );

    return isTransferAction && notChampion;
  }

  private static extractActionTarget(action: string): string | null {
    // Parse action string to extract target player ID
    // Implementation depends on action string format
    const match = action.match(/target:(\w+)/);
    return match ? match[1] : null;
  }
}
```

### 4. Implement Coalition Coordinator

```typescript
// packages/bot/src/lib/coalition-coordinator.ts

export class CoalitionCoordinator {
  private signalHistory: Map<string, SignalEvidence[]> = new Map();

  /**
   * Determine coalition strategy based on signals
   */
  determineCoalitionStrategy(
    state: MCTSGameState,
    personality: BotPersonality
  ): CoalitionStrategy {
    if (!state.finalTurnTriggered) {
      throw new Error('Coalition strategy only for final round');
    }

    const coalition = state.players.filter(p => p.id !== state.vintoCallerId);
    const bot = state.players[state.currentPlayerIndex];

    // Step 1: Identify champion (lowest score coalition member)
    const champion = this.identifyChampion(coalition, state);

    // Step 2: Decode signals from coalition members
    const signals = this.decodeCoalitionSignals(coalition, state);

    // Step 3: Determine bot's role
    const botRole = this.determineBotRole(bot, champion, signals, personality);

    // Step 4: Create strategy
    return {
      role: botRole,
      champion: champion.id,
      signals: signals,
      coordination: this.getCoordinationPlan(botRole, champion, state),
    };
  }

  /**
   * Identify coalition champion (best positioned to win)
   */
  private identifyChampion(
    coalition: MCTSPlayerState[],
    state: MCTSGameState
  ): MCTSPlayerState {
    // Sort by score (lowest first)
    const sorted = [...coalition].sort((a, b) => a.score - b.score);

    // Check for explicit champion signal
    const signaled = sorted.find(player => {
      const signals = this.signalHistory.get(player.id);
      const latestSignal = signals?.[signals.length - 1];
      return latestSignal && this.isChampionSignal(latestSignal);
    });

    return signaled || sorted[0]; // Default to lowest score
  }

  /**
   * Decode signals from all coalition members
   */
  private decodeCoalitionSignals(
    coalition: MCTSPlayerState[],
    state: MCTSGameState
  ): Map<string, SignalIntent> {
    const signals = new Map<string, SignalIntent>();

    for (const player of coalition) {
      const history = this.signalHistory.get(player.id) || [];
      if (history.length === 0) continue;

      const latestEvidence = history[history.length - 1];
      const intent = CoalitionSignalDecoder.decodeSignal(latestEvidence, history);

      signals.set(player.id, intent);
    }

    return signals;
  }

  /**
   * Determine bot's role in coalition
   */
  private determineBotRole(
    bot: MCTSPlayerState,
    champion: MCTSPlayerState,
    signals: Map<string, SignalIntent>,
    personality: BotPersonality
  ): CoalitionRole {
    // If bot is the champion
    if (bot.id === champion.id) {
      return 'champion';
    }

    // If bot has decent score and aggressive, try to usurp champion
    if (
      bot.score < champion.score + 3 &&
      personality.aggression > 0.7 &&
      personality.cooperativeness < 0.5
    ) {
      return 'champion'; // Compete for champion
    }

    // If bot has high cooperativeness, support
    if (personality.cooperativeness > 0.7) {
      return 'support';
    }

    // Check if others are already attacking Vinto
    const othersAttacking = Array.from(signals.values()).filter(
      s => s.signal === 'attack_vinto'
    ).length;

    if (othersAttacking === 0) {
      return 'attack'; // Someone needs to attack
    }

    return 'support'; // Default: support champion
  }

  /**
   * Create coordination plan
   */
  private getCoordinationPlan(
    role: CoalitionRole,
    champion: MCTSPlayerState,
    state: MCTSGameState
  ): CoordinationPlan {
    switch (role) {
      case 'champion':
        return {
          priority: ['minimize_own_score', 'use_actions_on_self'],
          targetPlayer: state.botPlayerId,
          signal: 'champion',
        };

      case 'support':
        return {
          priority: ['help_champion', 'transfer_low_cards'],
          targetPlayer: champion.id,
          signal: 'support',
        };

      case 'attack':
        return {
          priority: ['disrupt_vinto', 'prevent_optimization'],
          targetPlayer: state.vintoCallerId || '',
          signal: 'attack_vinto',
        };

      default:
        return {
          priority: ['minimize_own_score'],
          targetPlayer: state.botPlayerId,
          signal: 'uncertain',
        };
    }
  }

  /**
   * Record signal for history
   */
  recordSignal(evidence: SignalEvidence): void {
    const history = this.signalHistory.get(evidence.playerId) || [];
    history.push(evidence);
    this.signalHistory.set(evidence.playerId, history);
  }

  private isChampionSignal(evidence: SignalEvidence): boolean {
    return evidence.action.includes('champion') || evidence.action.includes('self_improve');
  }
}

export type CoalitionRole = 'champion' | 'support' | 'attack';

export interface CoalitionStrategy {
  role: CoalitionRole;
  champion: string;
  signals: Map<string, SignalIntent>;
  coordination: CoordinationPlan;
}

export interface CoordinationPlan {
  priority: string[];
  targetPlayer: string;
  signal: CoalitionSignal;
}
```

### 5. Integrate with MCTSBotDecisionService

```typescript
// packages/bot/src/lib/mcts-bot-decision.ts

export class MCTSBotDecisionService implements BotDecisionService {
  private coalitionCoordinator: CoalitionCoordinator;

  constructor(difficulty: Difficulty, personalityName?: string) {
    // ... existing initialization
    this.coalitionCoordinator = new CoalitionCoordinator();
  }

  private runMCTS(rootState: MCTSGameState): MCTSMove {
    // ... existing code

    // If in final round, use coalition coordination
    if (rootState.finalTurnTriggered) {
      const strategy = this.coalitionCoordinator.determineCoalitionStrategy(
        rootState,
        this.personality
      );

      console.log(
        `[Coalition] Role: ${strategy.role}, Champion: ${strategy.champion}`
      );

      // Encode signal into move selection
      const signalIntent: SignalIntent = {
        signal: strategy.coordination.signal,
        confidence: 0.8,
        targetPlayer: strategy.coordination.targetPlayer,
      };

      // Filter and reweight moves based on signal
      root.untriedMoves = CoalitionSignalEncoder.encodeSignal(
        signalIntent,
        rootState,
        root.untriedMoves
      );
    }

    // ... rest of MCTS
  }
}
```

## Implementation Steps

### Step 1: Create Signaling Types
- [ ] Create `packages/bot/src/lib/coalition-signaling-types.ts`
- [ ] Define signal types, contexts, evidence
- [ ] Export from `packages/bot/src/lib/index.ts`

### Step 2: Implement Signal Encoder
- [ ] Create `packages/bot/src/lib/coalition-signal-encoder.ts`
- [ ] Implement signal → action prioritization
- [ ] Implement champion/support/attack/transfer patterns
- [ ] Add tests for signal encoding

### Step 3: Implement Signal Decoder
- [ ] Create `packages/bot/src/lib/coalition-signal-decoder.ts`
- [ ] Implement action → signal detection
- [ ] Implement pattern recognition for each signal type
- [ ] Add tests for signal decoding

### Step 4: Implement Coalition Coordinator
- [ ] Create `packages/bot/src/lib/coalition-coordinator.ts`
- [ ] Implement champion identification
- [ ] Implement role determination
- [ ] Implement coordination plan generation
- [ ] Add signal history tracking

### Step 5: Integrate with MCTS
- [ ] Add `CoalitionCoordinator` to `MCTSBotDecisionService`
- [ ] Apply signaling in final round
- [ ] Record signals after actions
- [ ] Test coalition coordination

### Step 6: Testing
- [ ] Unit tests for encoder/decoder
- [ ] Test signal round-trip (encode → decode)
- [ ] Integration tests for coalition scenarios
- [ ] Test with multiple coalition members
- [ ] Verify improved win rate

### Step 7: Documentation
- [ ] Document signaling protocol
- [ ] Add examples of signal patterns
- [ ] Update `packages/bot/README.md`
- [ ] Create coordination strategy guide

## Success Criteria

- [ ] 4+ distinct signals implemented (champion, support, attack, transfer)
- [ ] Signals correctly encoded into action selection
- [ ] Signals correctly decoded from observed actions
- [ ] Coalition coordinator assigns appropriate roles
- [ ] Bot vs bot tests show improved coalition coordination
- [ ] Coalition win rate increases by 15-20%
- [ ] Unit tests achieve >85% coverage

## Example Scenarios

### Scenario 1: Champion Declaration
```
Turn 1 (Final Round):
- Player A (score 6): Uses 7 to peek own card, swaps for better card
- Signal decoded: "champion" (confidence: 0.8)

Turn 2:
- Player B (score 10): Uses Jack to transfer low card to Player A
- Signal decoded: "support" (confidence: 0.7, target: Player A)

Turn 3:
- Player C (score 9): Uses Ace to make Vinto caller draw
- Signal decoded: "attack_vinto" (confidence: 0.75)

Result: Coordinated strategy, Player A wins for coalition
```

### Scenario 2: Role Negotiation
```
Turn 1:
- Player A (score 7): Aggressive self-improvement
- Signal: "champion"

Turn 2:
- Player B (score 6): Also aggressive self-improvement
- Signal: "champion" (competing!)

Turn 3:
- Player A (score 7): Sees Player B's signal, switches to support
- Signal: "support" (target: Player B)

Result: Role negotiation through actions
```

## Integration Notes

### Personality Integration
- Cooperative personalities better at signaling
- Aggressive personalities may ignore signals
- Cautious personalities default to support role

### Explanation Integration
Show coalition coordination in explanations:
```typescript
humanReadable: "I'm using support role in the coalition. Player 2 signaled they're the champion (score 6), so I'm helping them with my Jack swap."
```

## Dependencies

- Phase 1, Ticket #2: Personality System (uses cooperativeness)
- Phase 2, Ticket #4: Belief Propagation (uses action interpretation)

## Related Tickets

- Phase 1, Ticket #3: Hierarchical Strategy (uses coalition strategies)
- Phase 2, Ticket #6: Deep Opponent Profiling (can detect coalition patterns)

## References

- Coalition evaluator: `packages/bot/src/lib/mcts-coalition-evaluator.ts`
- MCTS decision: `packages/bot/src/lib/mcts-bot-decision.ts`
- Game rules: `docs/game-engine/VINTO_RULES.md` (final round rules)
