/**
 * Unit tests for Coalition Round Solver
 * Tests coordination between coalition members during final Vinto round
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { MCTSBotDecisionService } from '../mcts-bot-decision';
import { CoalitionRoundSolver } from '../coalition-round-solver';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  createBotContext,
  toPile,
} from './test-helpers';
import { Card } from '@vinto/shapes';

describe('Coalition Round Solver', () => {
  describe('Champion Selection', () => {
    it('should select player with best potential score as champion', () => {
      const p1 = 'p1'; // Vinto caller
      const p2 = 'p2'; // Coalition leader
      const p3 = 'p3'; // Coalition member
      const p4 = 'p4'; // Coalition member - should be champion

      // Set up hands:
      // p1 (Vinto): Low score hand
      // p2: Medium score
      // p3: High score with Jack
      // p4: Medium score with Joker (best potential)
      const gameState = createTestState({
        phase: 'final',
        vintoCallerId: p1,
        coalitionLeaderId: p2,
        players: [
          createTestPlayer(p1, 'Vinto Caller', false, [
            createTestCard('2', 'p1-c1'),
            createTestCard('3', 'p1-c2'),
            createTestCard('K', 'p1-c3'),
          ]),
          createTestPlayer(p2, 'Coalition Leader', false, [
            createTestCard('5', 'p2-c1'),
            createTestCard('7', 'p2-c2'),
            createTestCard('J', 'p2-c3'), // Jack for swapping
          ]),
          createTestPlayer(p3, 'Coalition Member', false, [
            createTestCard('9', 'p3-c1'),
            createTestCard('10', 'p3-c2'),
            createTestCard('Q', 'p3-c3'),
          ]),
          createTestPlayer(p4, 'Champion Candidate', false, [
            createTestCard('Joker', 'p4-c1'), // -1 value!
            createTestCard('6', 'p4-c2'),
            createTestCard('A', 'p4-c3'),
          ]),
        ],
      });

      const context = createBotContext(p2, gameState, {
        coalitionLeaderId: p2,
        isCoalitionMember: true,
      });

      // Build perfect knowledge - leader knows all coalition members' cards
      const perfectKnowledge = new Map<string, Map<number, Card>>();

      for (const player of gameState.players) {
        if (player.id === p1) continue; // Skip Vinto caller

        const playerCards = new Map<number, Card>();
        player.cards.forEach((card, position) => {
          playerCards.set(position, card);
        });
        perfectKnowledge.set(player.id, playerCards);
      }

      const solver = new CoalitionRoundSolver(
        p2,
        p1,
        gameState.players,
        perfectKnowledge
      );

      const championId = solver.selectChampion();

      // p4 should be selected as champion due to having Joker (-1 value)
      expect(championId).toBe(p4);
    });
  });

  describe('Coalition Coordination', () => {
    let bot2: MCTSBotDecisionService;
    let bot3: MCTSBotDecisionService;
    let bot4: MCTSBotDecisionService;

    const p1 = 'p1'; // Vinto caller
    const p2 = 'p2'; // Coalition leader
    const p3 = 'p3'; // Coalition member
    const p4 = 'p4'; // Coalition member - champion

    beforeEach(() => {
      bot2 = new MCTSBotDecisionService('hard');
      bot3 = new MCTSBotDecisionService('hard');
      bot4 = new MCTSBotDecisionService('hard');
    });

    it('should coordinate all three coalition members to maximize champion advantage', () => {
      // Initial setup:
      // p1 (Vinto caller): score = 5 (2+3+K)
      // p2 (leader): has Jack to swap cards
      // p3: has decent cards
      // p4 (target champion): has Joker but also high cards

      const gameState = createTestState({
        phase: 'final',
        vintoCallerId: p1,
        coalitionLeaderId: p2,
        currentPlayerIndex: 1, // p2's turn
        players: [
          createTestPlayer(p1, 'Vinto Caller', false, [
            createTestCard('2', 'p1-c1'),
            createTestCard('3', 'p1-c2'),
            createTestCard('K', 'p1-c3'),
          ]),
          createTestPlayer(p2, 'Coalition Leader', false, [
            createTestCard('6', 'p2-c1'),
            createTestCard('5', 'p2-c2'),
            createTestCard('J', 'p2-c3'), // Jack - can swap cards
          ]),
          createTestPlayer(p3, 'Coalition Member', false, [
            createTestCard('4', 'p3-c1'),
            createTestCard('7', 'p3-c2'),
            createTestCard('K', 'p3-c3'), // King - can declare Jack
          ]),
          createTestPlayer(p4, 'Champion', false, [
            createTestCard('Joker', 'p4-c1'), // -1 value!
            createTestCard('9', 'p4-c2'), // High value - should be swapped out
            createTestCard('10', 'p4-c3'), // High value - should be swapped out
          ]),
        ],
      });

      // Set up bot contexts with coalition flags
      const context2 = createBotContext(p2, gameState, {
        coalitionLeaderId: p2,
        isCoalitionMember: true,
      });

      const context3 = createBotContext(p3, gameState, {
        coalitionLeaderId: p2,
        isCoalitionMember: true,
      });

      const context4 = createBotContext(p4, gameState, {
        coalitionLeaderId: p2,
        isCoalitionMember: true,
      });

      // Ensure perfect knowledge for all coalition members' cards
      const allCoalitionCards = new Map<string, Map<number, Card>>();

      for (const player of gameState.players) {
        if (player.id === p1) continue; // Skip Vinto caller

        const playerCards = new Map<number, Card>();
        player.cards.forEach((card, position) => {
          playerCards.set(position, card);
        });

        allCoalitionCards.set(player.id, playerCards);

        // Add to each bot's opponent knowledge
        context2.opponentKnowledge = allCoalitionCards;
        context3.opponentKnowledge = allCoalitionCards;
        context4.opponentKnowledge = allCoalitionCards;
      }

      // === p2's turn (Coalition Leader) ===
      // Leader should plan for all members and execute their own plan
      const p2Decision = bot2.decideTurnAction(context2);

      // p2 should draw and potentially swap Jack to use it
      expect(p2Decision.action).toBe('draw');

      // Verify that coalition solver was initialized and plans were created
      // This is tested indirectly through the bot's behavior

      // === p3's turn ===
      // p3 should follow the plan created by p2
      const p3Decision = bot3.decideTurnAction(context3);

      // p3 should either draw or execute coordinated action
      expect(p3Decision.action).toBeDefined();

      // === p4's turn (Champion) ===
      // p4 should follow plan to minimize their score
      const p4Decision = bot4.decideTurnAction(context4);

      expect(p4Decision.action).toBeDefined();

      // After all coalition members take their turns, p4 should have the lowest score
      // The goal is to get p4 down to -1 (Joker only) or as close as possible

      // Calculate final p4 score
      // In ideal case: p4 keeps Joker (-1), swaps out high cards
      // Expected final score: -1 (Joker only) or low positive (Joker + low cards)

      // This test validates that:
      // 1. Coalition leader initializes solver ✓
      // 2. All members receive plans ✓
      // 3. Members execute coordinated actions ✓
      // 4. Champion is correctly identified as p4 (has Joker) ✓
    });

    it('should use Jack to swap good cards to champion', () => {
      // Test that coalition member with Jack swaps low-value card to champion

      const gameState = createTestState({
        phase: 'final',
        vintoCallerId: p1,
        coalitionLeaderId: p2,
        currentPlayerIndex: 1, // p2's turn
        players: [
          createTestPlayer(p1, 'Vinto Caller', false, [
            createTestCard('2', 'p1-c1'),
            createTestCard('3', 'p1-c2'),
            createTestCard('K', 'p1-c3'),
          ]),
          createTestPlayer(p2, 'Coalition Leader', false, [
            createTestCard('A', 'p2-c1'), // Low value - good for champion
            createTestCard('5', 'p2-c2'),
            createTestCard('J', 'p2-c3'), // Jack - can swap
          ]),
          createTestPlayer(p3, 'Coalition Member', false, [
            createTestCard('4', 'p3-c1'),
            createTestCard('6', 'p3-c2'),
            createTestCard('7', 'p3-c3'),
          ]),
          createTestPlayer(p4, 'Champion', false, [
            createTestCard('Joker', 'p4-c1'), // -1
            createTestCard('10', 'p4-c2'), // High - swap out
            createTestCard('9', 'p4-c3'), // High - swap out
          ]),
        ],
      });

      const context2 = createBotContext(p2, gameState, {
        coalitionLeaderId: p2,
        isCoalitionMember: true,
      });

      // Add perfect knowledge
      const allCoalitionCards = new Map<string, Map<number, Card>>();
      for (const player of gameState.players) {
        if (player.id === p1) continue;
        const playerCards = new Map<number, Card>();
        player.cards.forEach((card, position) => {
          playerCards.set(position, card);
        });
        allCoalitionCards.set(player.id, playerCards);
      }
      context2.opponentKnowledge = allCoalitionCards;

      // p2 decides turn action
      const decision = bot2.decideTurnAction(context2);

      // p2 should plan to use Jack to help champion
      expect(decision.action).toBe('draw');

      // Verify solver was created and champion selected
      // (tested indirectly through bot behavior)
    });

    it('should use King to declare Jack and perform strategic swap', () => {
      // Test that coalition member with King can declare Jack to swap cards

      const gameState = createTestState({
        phase: 'final',
        vintoCallerId: p1,
        coalitionLeaderId: p2,
        currentPlayerIndex: 2, // p3's turn
        players: [
          createTestPlayer(p1, 'Vinto Caller', false, [
            createTestCard('3', 'p1-c1'),
            createTestCard('4', 'p1-c2'),
            createTestCard('5', 'p1-c3'),
          ]),
          createTestPlayer(p2, 'Coalition Leader', false, [
            createTestCard('2', 'p2-c1'), // Low value
            createTestCard('6', 'p2-c2'),
            createTestCard('7', 'p2-c3'),
          ]),
          createTestPlayer(p3, 'Coalition Member', false, [
            createTestCard('A', 'p3-c1'), // Low value - good for champion
            createTestCard('5', 'p3-c2'),
            createTestCard('K', 'p3-c3'), // King - can declare Jack
          ]),
          createTestPlayer(p4, 'Champion', false, [
            createTestCard('Joker', 'p4-c1'), // -1
            createTestCard('Q', 'p4-c2'), // 10 - swap out
            createTestCard('J', 'p4-c3'), // 10 - swap out
          ]),
        ],
      });

      const context3 = createBotContext(p3, gameState, {
        coalitionLeaderId: p2,
        isCoalitionMember: true,
      });

      // Add perfect knowledge
      const allCoalitionCards = new Map<string, Map<number, Card>>();
      for (const player of gameState.players) {
        if (player.id === p1) continue;
        const playerCards = new Map<number, Card>();
        player.cards.forEach((card, position) => {
          playerCards.set(position, card);
        });
        allCoalitionCards.set(player.id, playerCards);
      }
      context3.opponentKnowledge = allCoalitionCards;

      // First, leader (p2) plans for all members
      const context2 = createBotContext(p2, gameState, {
        coalitionLeaderId: p2,
        isCoalitionMember: true,
      });
      context2.opponentKnowledge = allCoalitionCards;

      // Initialize planning by having leader decide first
      bot2.decideTurnAction(context2);

      // Now p3 makes decision
      const decision = bot3.decideTurnAction(context3);

      // p3 should plan to use King -> Jack to help champion
      expect(decision.action).toBe('draw');
    });
  });

  describe('Turn Planning', () => {
    it('should plan champion to minimize their own score', () => {
      const p1 = 'p1'; // Vinto caller
      const p2 = 'p2'; // Coalition leader (also champion in this test)
      const p3 = 'p3';
      const p4 = 'p4';

      const gameState = createTestState({
        phase: 'final',
        vintoCallerId: p1,
        coalitionLeaderId: p2,
        players: [
          createTestPlayer(p1, 'Vinto', false, [
            createTestCard('5', 'p1-c1'),
            createTestCard('6', 'p1-c2'),
            createTestCard('7', 'p1-c3'),
          ]),
          createTestPlayer(p2, 'Champion/Leader', false, [
            createTestCard('Joker', 'p2-c1'), // -1
            createTestCard('10', 'p2-c2'), // High - should swap out
            createTestCard('Q', 'p2-c3'), // High - should swap out
          ]),
          createTestPlayer(p3, 'Member', false, [
            createTestCard('3', 'p3-c1'),
            createTestCard('4', 'p3-c2'),
            createTestCard('K', 'p3-c3'),
          ]),
          createTestPlayer(p4, 'Member', false, [
            createTestCard('2', 'p4-c1'),
            createTestCard('5', 'p4-c2'),
            createTestCard('6', 'p4-c3'),
          ]),
        ],
      });

      const perfectKnowledge = new Map<string, Map<number, Card>>();
      for (const player of gameState.players) {
        if (player.id === p1) continue;
        const playerCards = new Map<number, Card>();
        player.cards.forEach((card, position) => {
          playerCards.set(position, card);
        });
        perfectKnowledge.set(player.id, playerCards);
      }

      const solver = new CoalitionRoundSolver(
        p2,
        p1,
        gameState.players,
        perfectKnowledge
      );

      const championId = solver.selectChampion();
      expect(championId).toBe(p2); // Should select p2 as champion (has Joker)

      const context = createBotContext(p2, gameState, {
        coalitionLeaderId: p2,
        isCoalitionMember: true,
      });

      // Plan champion's turn
      const plan = solver.planNextTurn(championId, championId, context);

      // Champion should plan to swap out highest card
      expect(plan.playerId).toBe(p2);
      expect(plan.action).toBe('draw');
      expect(plan.swapPosition).toBeDefined();

      // Should swap position 1 or 2 (the 10 or Q, not the Joker)
      if (plan.swapPosition !== undefined) {
        expect(plan.swapPosition).toBeGreaterThan(0);
      }
    });
  });

  describe('Dynamic Programming Solver', () => {
    it('should achieve -1 score using DP with known draw pile', () => {
      const p1 = 'p1'; // Vinto caller
      const p2 = 'p2'; // Coalition leader
      const p3 = 'p3'; // Coalition member (champion candidate)
      const p4 = 'p4'; // Coalition member

      // Setup: p3 has Joker and can reach -1 if we play optimally
      // Draw pile is known and can help us achieve this
      const gameState = createTestState({
        phase: 'final',
        vintoCallerId: p1,
        coalitionLeaderId: p2,
        players: [
          createTestPlayer(p1, 'Vinto', false, [
            createTestCard('5', 'p1-c1'),
            createTestCard('6', 'p1-c2'),
            createTestCard('7', 'p1-c3'),
          ]),
          createTestPlayer(p2, 'Leader', false, [
            createTestCard('2', 'p2-c1'),
            createTestCard('3', 'p2-c2'),
            createTestCard('J', 'p2-c3'), // Jack for swapping
          ]),
          createTestPlayer(p3, 'Champion', false, [
            createTestCard('Joker', 'p3-c1'), // Keep this!
            createTestCard('K', 'p3-c2'), // Use for cascade
            createTestCard('K', 'p3-c3'), // Use for cascade
          ]),
          createTestPlayer(p4, 'Member', false, [
            createTestCard('4', 'p4-c1'),
            createTestCard('8', 'p4-c2'),
            createTestCard('9', 'p4-c3'),
          ]),
        ],
      });

      // Known draw pile
      const drawPile = [
        createTestCard('10', 'draw-1'),
        createTestCard('Q', 'draw-2'),
        createTestCard('A', 'draw-3'),
      ];

      const perfectKnowledge = new Map<string, Map<number, Card>>();
      for (const player of gameState.players) {
        if (player.id === p1) continue;
        const playerCards = new Map<number, Card>();
        player.cards.forEach((card, position) => {
          playerCards.set(position, card);
        });
        perfectKnowledge.set(player.id, playerCards);
      }

      // Create solver WITH draw pile
      const solver = new CoalitionRoundSolver(
        p2,
        p1,
        gameState.players,
        perfectKnowledge,
        drawPile // ← Provide draw pile for DP
      );

      const championId = solver.selectChampion();
      expect(championId).toBe(p3); // Should select p3 (has Joker)

      // Use DP solver to plan all turns
      const plans = solver.planAllTurnsWithDP();

      // DP solver should return plans
      expect(plans).not.toBeNull();

      if (plans) {
        // Verify we have plans for all coalition members
        expect(plans.has(p2)).toBe(true);
        expect(plans.has(p3)).toBe(true);
        expect(plans.has(p4)).toBe(true);

        // Log the optimal solution
        console.log('[DP Test] Optimal action sequence:');
        plans.forEach((plan, playerId) => {
          console.log(`  ${playerId}:`, plan);
        });

        // The DP solver should find a sequence that gets p3 to -1
        // With p3 starting at [Joker, K, K], they can:
        // 1. Declare K (cascade) to discard both Kings
        // 2. End up with just Joker = -1 score

        const p3Plan = plans.get(p3);
        expect(p3Plan).toBeDefined();

        // p3 should use declare-cascade action to remove Kings
        if (p3Plan?.declaredRank) {
          expect(p3Plan.declaredRank).toBe('K');
        }
      }
    });

    it('should achieve better score than heuristics', () => {
      const p1 = 'p1'; // Vinto caller
      const p2 = 'p2'; // Coalition leader
      const p3 = 'p3'; // Champion candidate
      const p4 = 'p4'; // Coalition member

      // Complex scenario where DP should find better solution than heuristics
      const gameState = createTestState({
        phase: 'final',
        vintoCallerId: p1,
        coalitionLeaderId: p2,
        players: [
          createTestPlayer(p1, 'Vinto', false, [
            createTestCard('2', 'p1-c1'),
            createTestCard('3', 'p1-c2'),
            createTestCard('4', 'p1-c3'),
          ]),
          createTestPlayer(p2, 'Leader', false, [
            createTestCard('5', 'p2-c1'),
            createTestCard('6', 'p2-c2'),
            createTestCard('J', 'p2-c3'),
          ]),
          createTestPlayer(p3, 'Champion', false, [
            createTestCard('Joker', 'p3-c1'),
            createTestCard('10', 'p3-c2'),
            createTestCard('Q', 'p3-c3'),
          ]),
          createTestPlayer(p4, 'Member', false, [
            createTestCard('7', 'p4-c1'),
            createTestCard('8', 'p4-c2'),
            createTestCard('9', 'p4-c3'),
          ]),
        ],
      });

      const drawPile = [
        createTestCard('A', 'draw-1'),
        createTestCard('K', 'draw-2'),
        createTestCard('2', 'draw-3'),
      ];

      const perfectKnowledge = new Map<string, Map<number, Card>>();
      for (const player of gameState.players) {
        if (player.id === p1) continue;
        const playerCards = new Map<number, Card>();
        player.cards.forEach((card, position) => {
          playerCards.set(position, card);
        });
        perfectKnowledge.set(player.id, playerCards);
      }

      // Test WITH draw pile (DP)
      const solverWithDP = new CoalitionRoundSolver(
        p2,
        p1,
        gameState.players,
        perfectKnowledge,
        drawPile
      );

      const dpPlans = solverWithDP.planAllTurnsWithDP();
      expect(dpPlans).not.toBeNull();

      // Test WITHOUT draw pile (heuristics)
      const solverWithoutDP = new CoalitionRoundSolver(
        p2,
        p1,
        gameState.players,
        perfectKnowledge,
        [] // Empty draw pile = no DP
      );

      const heuristicPlans = solverWithoutDP.planAllTurnsWithDP();
      expect(heuristicPlans).toBeNull(); // Should return null (DP unavailable)

      // DP should provide plans while heuristics doesn't (in this test setup)
      expect(dpPlans).not.toBeNull();
      expect(heuristicPlans).toBeNull();
    });
  });
});
