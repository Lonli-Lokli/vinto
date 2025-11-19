import { describe, it, expect } from 'vitest';
import { createCoalitionPlan } from '../coalition-round-solver';
import type { GameState, Card, Rank } from '@vinto/shapes';
import { getCardValue, Pile } from '@vinto/shapes';

function createTestCard(rank: Rank, id: string): Card {
  return {
    id,
    rank: rank as any,
    value: getCardValue(rank),
    played: false,
    actionText: '',
  };
}

function createTestPlayer(id: string, name: string, cards: Card[]) {
  return {
    id,
    name,
    nickname: name,
    isHuman: false,
    isBot: true,
    cards,
    knownCardPositions: cards.map((_, idx) => idx),
    isVintoCaller: false,
    coalitionWith: [] as string[],
  };
}

describe('Coalition Solver - Champion Evaluation', () => {
  it('should evaluate all potential champions and calculate achievable scores', () => {
    // Test setup matching the integration test
    const state: GameState = {
      gameId: 'test-game',
      roundNumber: 1,
      phase: 'final',
      subPhase: 'idle',
      finalTurnTriggered: false,
      playersCompletedFinalTurn: [],
      currentPlayerIndex: 1, // p2's turn (leader)
      turnNumber: 5,
      vintoCallerId: 'p1',
      coalitionLeaderId: 'p2',
      players: [
        createTestPlayer('p1', 'Vinto Caller', [createTestCard('K', 'p1-0')]),
        createTestPlayer('p2', 'Leader', [
          createTestCard('Joker', 'p2-0'),
          createTestCard('2', 'p2-1'),
          createTestCard('3', 'p2-2'),
          createTestCard('4', 'p2-3'),
          createTestCard('5', 'p2-4'),
          createTestCard('9', 'p2-5'),
        ]),
        createTestPlayer('p3', 'Strategic Champion', [
          createTestCard('K', 'p3-0'),
          createTestCard('K', 'p3-1'),
          createTestCard('K', 'p3-2'),
          createTestCard('9', 'p3-3'),
          createTestCard('9', 'p3-4'),
          createTestCard('6', 'p3-5'),
          createTestCard('8', 'p3-6'),
        ]),
        createTestPlayer('p4', 'Member C', [
          createTestCard('7', 'p4-0'),
          createTestCard('7', 'p4-1'),
          createTestCard('10', 'p4-2'),
          createTestCard('6', 'p4-3'),
          createTestCard('6', 'p4-4'),
          createTestCard('6', 'p4-5'),
        ]),
        createTestPlayer('p5', 'Member D', [
          createTestCard('J', 'p5-0'),
          createTestCard('10', 'p5-1'),
          createTestCard('5', 'p5-2'),
          createTestCard('5', 'p5-3'),
          createTestCard('5', 'p5-4'),
          createTestCard('5', 'p5-5'),
        ]),
      ],
      drawPile: Pile.fromCards([]),
      discardPile: Pile.fromCards([]),
      pendingAction: null,
      activeTossIn: null,
      turnActions: [],
      roundActions: [],
      roundFailedAttempts: [],
      difficulty: 'hard' as const,
      botVersion: 'v1' as const,
    };

    // Create known cards map (all cards are known in this test)
    const knownCards = new Map<string, Card>();
    state.players.forEach((player) => {
      player.cards.forEach((card, idx) => {
        knownCards.set(`${player.id}[${idx}]`, card);
      });
    });

    const evaluations: { id: string; initial: number; predicted: number }[] =
      [];
    for (const playerId of ['p2', 'p3', 'p4', 'p5'] as const) {
      // Evaluate each player as champion
      const plan = createCoalitionPlan(state, knownCards, undefined, playerId);

      console.log(`Champion: ${playerId}`);
      console.log(`Initial score: ${
        state.players.find((p) => p.id === playerId)!.cards.reduce((sum, c) => sum + c.value, 0)
      }`);
      console.log(`Predicted achievable score: ${plan.targetScore}`);
      console.log(`Confidence: ${(plan.confidence * 100).toFixed(0)}%`);
      console.log('Action sequence:');
      plan.steps.forEach((step, idx) => {
        console.log(`  ${idx + 1}. ${step.description}`);
      });
      evaluations.push({
        id: playerId,
        initial: state.players
          .find((p) => p.id === playerId)!
          .cards.reduce((sum, c) => sum + c.value, 0),
        predicted: plan.targetScore,
      });
    }
    // Compare and select best champion
    console.log('\n=== Champion Comparison ===');

    evaluations.forEach((evaluation) => {
      console.log(
        `${evaluation.id}: ${evaluation.initial} → ${evaluation.predicted})`
      );
    });

    const bestChampion = evaluations.reduce((best, current) =>
      current.predicted < best.predicted ? current : best
    );
    console.log(
      `\nBest champion: ${bestChampion.id} with predicted score ${bestChampion.predicted}`
    );

    // Now evaluate without forced champion to see who solver picks
    console.log("\n=== Solver's Automatic Champion Selection ===");
    const automaticPlan = createCoalitionPlan(
      state,
      knownCards,
      undefined,
      undefined
    );
    console.log(`Selected champion: ${automaticPlan.championId}`);
    console.log(`Predicted score: ${automaticPlan.targetScore}`);
    console.log('Action sequence:');
    automaticPlan.steps.forEach((step, idx) => {
      console.log(`  ${idx + 1}. ${step.description}`);
    });

    // The solver should select the champion with the best achievable score
    // With cross-player cascades working: p2→6, p3→-1, p4→0, p5→10
    // Therefore p3 should be selected as champion (best score of -1!)
    // Strategy: Cascade 9s and 6s across players, then swap in Joker
    expect(automaticPlan.championId).toBe('p3');
    expect(automaticPlan.targetScore).toBe(-1);
  });

  it('should generate declare-king actions for cascading multiple card ranks', () => {
    // Simplified test to verify King cascade action generation
    const state: GameState = {
      gameId: 'test-game',
      roundNumber: 1,
      phase: 'final',
      subPhase: 'idle',
      finalTurnTriggered: false,
      playersCompletedFinalTurn: [],
      currentPlayerIndex: 2, // p3's turn
      turnNumber: 6,
      vintoCallerId: 'p1',
      coalitionLeaderId: 'p2',
      players: [
        createTestPlayer('p1', 'Vinto', [createTestCard('K', 'p1-0')]),
        createTestPlayer('p2', 'Leader', [
          createTestCard('Joker', 'p2-0'),
          createTestCard('10', 'p2-1'),
        ]),
        createTestPlayer('p3', 'Champion', [
          createTestCard('K', 'p3-0'), // Will use this King to declare
          createTestCard('K', 'p3-1'), // Cascade target
          createTestCard('K', 'p3-2'), // Cascade target
          createTestCard('9', 'p3-3'), // Cascade target
          createTestCard('9', 'p3-4'), // Cascade target
        ]),
        createTestPlayer('p4', 'Member', [createTestCard('7', 'p4-0')]),
        createTestPlayer('p5', 'Member', [createTestCard('J', 'p5-0')]),
      ],
      drawPile: Pile.fromCards([]),
      discardPile: Pile.fromCards([]),
      pendingAction: null,
      activeTossIn: null,
      turnActions: [],
      roundActions: [],
      roundFailedAttempts: [],
      difficulty: 'moderate' as const,
      botVersion: 'v1' as const,
    };

    const knownCards = new Map<string, Card>();
    state.players.forEach((player) => {
      player.cards.forEach((card, idx) => {
        knownCards.set(`${player.id}[${idx}]`, card);
      });
    });

    const plan = createCoalitionPlan(state, knownCards, undefined, 'p3');

    console.log('\n=== Testing King Cascade Actions ===');
    console.log(`p3 initial hand: K,K,K,9,9 (score: ${0 + 0 + 0 + 9 + 9})`);
    console.log(`Predicted final score: ${plan.targetScore}`);
    console.log('\nGenerated actions:');
    plan.steps.forEach((step, idx) => {
      console.log(`  ${idx + 1}. ${step.description}`);
      if (step.actionType === 'declare-king') {
        console.log(`     - Declares rank: ${step.declaredRank}`);
      }
    });

    // Should generate King cascade actions
    const kingActions = plan.steps.filter(
      (s) => s.actionType === 'declare-king'
    );
    expect(kingActions.length).toBeGreaterThan(0);

    // After cascading K and 9, should have 0 cards left, then get Joker = -1
    // Or at least significantly reduce the score
    expect(plan.targetScore).toBeLessThanOrEqual(5);
  });
});
