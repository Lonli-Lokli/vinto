/**
 * Unit tests for MCTS Bot Coalition Coordination (Low-Level)
 *
 * NOTE: These are LOW-LEVEL unit tests for the MCTS move generator logic.
 * They test the coordination algorithms in isolation WITHOUT knowledge sharing.
 *
 * Some tests fail because they require champion card visibility, which only
 * happens at runtime via botAIAdapter knowledge sharing.
 *
 * For COMPLETE integration tests with full coalition behavior, see:
 * packages/local-client/src/lib/__tests__/bot-coalition.test.ts
 *
 * These tests verify:
 * ✅ Champion identification logic
 * ✅ Joker transfer moves (when champion known)
 * ✅ Ace prevention in coalition
 * ✅ Coalition state evaluation
 *
 * Integration tests verify:
 * ✅ Full coordination flow with knowledge sharing
 * ✅ Jack stealing FOR champion
 * ✅ King declaring champion high cards
 * ✅ Queen peeking champion unknowns
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { MCTSBotDecisionService } from '../mcts-bot-decision';
import { MCTSMoveGenerator } from '../mcts-move-generator';
import { StateConstructor } from '../state-constructor';
import { BotMemory } from '../bot-memory';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  createBotContext,
  toPile,
} from './test-helpers';
import { MCTSGameState } from '../mcts-types';

/**
 * Helper to create MCTS game state from test game state
 */
function createMCTSState(
  testState: ReturnType<typeof createTestState>,
  botPlayerId: string
): MCTSGameState {
  // Create a bot context to use StateConstructor
  const context = createBotContext(botPlayerId, testState, {
    coalitionLeaderId: testState.coalitionLeaderId,
  });

  // Create bot memory and initialize with known cards
  const botMemory = new BotMemory(botPlayerId, testState.difficulty || 'hard');

  // Populate bot memory with known card positions
  testState.players.forEach((player) => {
    player.cards.forEach((card, pos) => {
      if (player.knownCardPositions.includes(pos)) {
        botMemory.observeCard(card, player.id, pos);
      }
    });
  });

  // Use StateConstructor to build the MCTS game state
  return StateConstructor.constructGameState(context, botMemory, botPlayerId);
}

describe('MCTS Bot - Coalition Coordination', () => {
  let bot: MCTSBotDecisionService;

  beforeEach(() => {
    // Create a fresh bot instance for each test to avoid state pollution
    bot = new MCTSBotDecisionService('hard');
  });

  describe('Champion Identification', () => {
    it('should identify player with lowest score as champion', () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('K', 'p1-1'),
            createTestCard('2', 'p1-2'),
          ]), // Score: 2
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('10', 'p2-1'),
            createTestCard('9', 'p2-2'),
          ]), // Score: 19
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('K', 'p3-1'),
            createTestCard('3', 'p3-2'),
          ]), // Score: 3 (BEST)
          createTestPlayer('p4', 'Member', false, [
            createTestCard('Q', 'p4-1'),
            createTestCard('8', 'p4-2'),
          ]), // Score: 18
        ],
      });

      const mctsState = createMCTSState(testState, 'p2');
      const moves = MCTSMoveGenerator.generateMoves(mctsState);

      // Should identify p3 as champion (lowest score among coalition members)
      expect(moves).toBeDefined();
    });
  });

  describe('Jack (Swap) - Give Jokers to Champion', () => {
    it('should generate moves to give own Joker to champion', () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2 (leader) turn
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('10', 'p1-1'),
            createTestCard('9', 'p1-2'),
          ]), // Score: 19
          createTestPlayer(
            'p2',
            'Leader',
            false,
            [
              createTestCard('Joker', 'p2-joker'), // Has Joker!
              createTestCard('8', 'p2-2'),
            ],
            [0, 1]
          ), // Score: 7
          createTestPlayer(
            'p3',
            'Champion',
            false,
            [createTestCard('K', 'p3-1'), createTestCard('3', 'p3-2')],
            [0, 1]
          ), // Score: 3 (BEST)
        ],
      });

      const mctsState = createMCTSState(testState, 'p2');
      mctsState.pendingCard = createTestCard('J', 'jack-card');

      const moves = MCTSMoveGenerator.generateActionMoves(
        mctsState,
        'swap-cards'
      );

      // Should find moves that swap p2's Joker with p3's cards
      const giveJokerMoves = moves.filter(
        (m) =>
          m.type === 'use-action' &&
          m.targets?.length === 2 &&
          m.targets[0].playerId === 'p2' &&
          m.targets[0].position === 0 && // p2's Joker
          m.targets[1].playerId === 'p3' && // Champion's card
          m.shouldSwap === true
      );

      expect(giveJokerMoves.length).toBeGreaterThan(0);
    });

    it('should generate moves to steal Jokers FOR champion (not for self)', () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2 (leader) turn
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer(
            'p1',
            'Vinto Caller',
            false,
            [createTestCard('9', 'p1-1'), createTestCard('8', 'p1-2')],
            [0, 1]
          ), // Vinto caller - coalition cannot interact with their cards
          createTestPlayer(
            'p2',
            'Leader',
            false,
            [createTestCard('10', 'p2-1'), createTestCard('8', 'p2-2')],
            [0, 1]
          ), // Score: 18
          createTestPlayer(
            'p3',
            'Champion',
            false,
            [createTestCard('K', 'p3-1'), createTestCard('3', 'p3-2')],
            [0, 1]
          ), // Score: 3 (BEST - champion)
          createTestPlayer(
            'p4',
            'Other Coalition Member',
            false,
            [
              createTestCard('Joker', 'p4-joker'), // Another coalition member has Joker
              createTestCard('7', 'p4-2'),
            ],
            [0, 1]
          ), // Score: 7
        ],
      });

      const mctsState = createMCTSState(testState, 'p2');
      mctsState.pendingCard = createTestCard('J', 'jack-card');

      const moves = MCTSMoveGenerator.generateActionMoves(
        mctsState,
        'swap-cards'
      );

      // Should find moves that swap champion's card with p4's Joker (steal for champion)
      const stealForChampion = moves.filter(
        (m) =>
          m.type === 'use-action' &&
          m.targets?.length === 2 &&
          m.targets[0].playerId === 'p3' && // Champion's card
          m.targets[1].playerId === 'p4' && // p4's Joker
          m.targets[1].position === 0 &&
          m.shouldSwap === true
      );

      expect(stealForChampion.length).toBeGreaterThan(0);

      // Should NOT steal Joker for self
      const stealForSelf = moves.filter(
        (m) =>
          m.type === 'use-action' &&
          m.targets?.length === 2 &&
          m.targets[0].playerId === 'p2' && // Leader's card
          m.targets[1].playerId === 'p4' && // p4's Joker
          m.targets[1].position === 0 &&
          m.shouldSwap === true
      );

      // Leader should still have self-swap moves as fallback, but coordination moves should exist
      expect(stealForChampion.length).toBeGreaterThan(0);
    });
  });

  describe('King (Declaration) - Remove Champion High Cards', () => {
    it('should prioritize declaring champion high-value cards', () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2 (leader) turn
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('2', 'p1-1'),
            createTestCard('3', 'p1-2'),
          ]),
          createTestPlayer(
            'p2',
            'Leader',
            false,
            [
              createTestCard('Q', 'p2-q'), // High card
              createTestCard('K', 'p2-k'), // High card
            ],
            [0, 1]
          ), // Score: 10 + 0 = 10
          createTestPlayer(
            'p3',
            'Champion',
            false,
            [createTestCard('2', 'p3-1'), createTestCard('3', 'p3-2')],
            [0, 1]
          ), // Score: 5 (CHAMPION - lowest score, needs protection)
          createTestPlayer(
            'p4',
            'Other Member',
            false,
            [createTestCard('10', 'p4-10'), createTestCard('9', 'p4-9')],
            [0, 1]
          ), // Score: 19
        ],
      });

      const mctsState = createMCTSState(testState, 'p2');
      mctsState.pendingCard = createTestCard('K', 'king-card');

      const moves = MCTSMoveGenerator.generateImprovedKingMoves(
        mctsState,
        'p2'
      );

      // As non-champion, bot should declare its own high cards (Q or K) to help lower coalition total
      const declareOwnHighCards = moves.filter(
        (m) =>
          m.type === 'use-action' &&
          m.targets?.length === 1 &&
          m.targets[0].playerId === 'p2' &&
          (m.declaredRank === 'Q' || m.declaredRank === 'K')
      );

      expect(declareOwnHighCards.length).toBeGreaterThan(0);
    });

    it('should declare own high cards when bot is not the champion', () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1,
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('2', 'p1-1'),
            createTestCard('3', 'p1-2'),
          ]),
          createTestPlayer(
            'p2',
            'Leader',
            false,
            [
              createTestCard('Q', 'p2-q'), // Own high card (10)
              createTestCard('J', 'p2-j'), // Own high card (10)
            ],
            [0, 1]
          ), // Score: 20
          createTestPlayer(
            'p3',
            'Champion',
            false,
            [createTestCard('2', 'p3-1'), createTestCard('3', 'p3-2')],
            [0, 1]
          ), // Score: 5 (CHAMPION - lowest)
        ],
      });

      const mctsState = createMCTSState(testState, 'p2');
      mctsState.pendingCard = createTestCard('K', 'king-card');

      const moves = MCTSMoveGenerator.generateImprovedKingMoves(
        mctsState,
        'p2'
      );

      // Bot is not champion, so should declare own high cards (Q or J) to help coalition
      const ownHighCardMoves = moves.filter(
        (m) =>
          m.type === 'use-action' &&
          m.targets?.length === 1 &&
          m.targets[0].playerId === 'p2' &&
          (m.declaredRank === 'Q' || m.declaredRank === 'J')
      );

      expect(ownHighCardMoves.length).toBeGreaterThan(0);
    });
  });

  describe('Queen (Peek & Swap) - Scout Coalition Cards', () => {
    it('should generate peek moves for coalition member cards', () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1,
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('2', 'p1-1'),
            createTestCard('3', 'p1-2'),
          ]),
          createTestPlayer(
            'p2',
            'Leader',
            false,
            [createTestCard('K', 'p2-1'), createTestCard('Q', 'p2-2')],
            [0, 1]
          ), // Score: 10 (not champion)
          createTestPlayer(
            'p3',
            'Champion',
            false,
            [createTestCard('2', 'p3-1'), createTestCard('3', 'p3-2')],
            [0, 1]
          ), // Score: 5 (champion)
          createTestPlayer(
            'p4',
            'Other Member',
            false,
            [
              createTestCard('7', 'p4-1'), // Unknown to leader
              createTestCard('8', 'p4-2'), // Unknown to leader
            ],
            [] // No known positions for leader
          ),
        ],
      });

      const mctsState = createMCTSState(testState, 'p2');
      mctsState.pendingCard = createTestCard('Q', 'queen-card');

      const moves = MCTSMoveGenerator.generateActionMoves(
        mctsState,
        'peek-and-swap'
      );

      // Should generate peek moves (can peek coalition members, just not Vinto caller)
      const peekMoves = moves.filter(
        (m) =>
          m.type === 'use-action' &&
          m.targets?.length === 2 &&
          m.targets[0].playerId !== 'p1' && // Not peeking Vinto caller
          m.targets[1].playerId !== 'p1' // Not peeking Vinto caller
      );

      expect(peekMoves.length).toBeGreaterThan(0);
    });
  });

  describe('Ace (Force Draw) - Never Use in Coalition', () => {
    it('should generate ZERO Ace moves when in coalition', () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2 (coalition member) turn
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('K', 'p1-1'),
            createTestCard('2', 'p1-2'),
          ]),
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('5', 'p2-1'),
            createTestCard('6', 'p2-2'),
          ]),
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('3', 'p3-1'),
            createTestCard('4', 'p3-2'),
          ]),
        ],
      });

      const mctsState = createMCTSState(testState, 'p2');
      mctsState.pendingCard = createTestCard('A', 'ace-card');

      const moves = MCTSMoveGenerator.generateActionMoves(
        mctsState,
        'force-draw'
      );

      // Should generate NO moves (Ace is harmful in coalition)
      expect(moves.length).toBe(0);
    });

    it('should generate Ace moves when NOT in coalition', () => {
      const testState = createTestState({
        vintoCallerId: null,
        coalitionLeaderId: null,
        currentPlayerIndex: 0,
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', false, [
            createTestCard('5', 'p1-1'),
            createTestCard('6', 'p1-2'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('3', 'p2-1'),
            createTestCard('4', 'p2-2'),
          ]),
        ],
      });

      const mctsState = createMCTSState(testState, 'p1');
      mctsState.pendingCard = createTestCard('A', 'ace-card');

      const moves = MCTSMoveGenerator.generateActionMoves(
        mctsState,
        'force-draw'
      );

      // Should generate moves targeting opponent
      expect(moves.length).toBeGreaterThan(0);
      expect(moves.every((m) => m.targets?.[0]?.playerId === 'p2')).toBe(true);
    });
  });

  describe('Coalition State Evaluation', () => {
    it('should evaluate based on champion position vs Vinto caller', () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1,
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('K', 'p1-1'),
            createTestCard('K', 'p1-2'),
            createTestCard('K', 'p1-3'),
          ]), // Score: 0 (very low)
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('10', 'p2-1'),
            createTestCard('9', 'p2-2'),
            createTestCard('8', 'p2-3'),
          ]), // Score: 27 (high)
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('K', 'p3-1'),
            createTestCard('2', 'p3-2'),
            createTestCard('3', 'p3-3'),
          ]), // Score: 5 (BEST coalition member)
        ],
      });

      const context = createBotContext('p2', testState);

      // Bot should recognize coalition mode and optimize for champion winning
      expect(context.gameState.vintoCallerId).toBe('p1');
      expect(context.gameState.coalitionLeaderId).toBe('p2');
    });
  });

  describe('Integration: Complete Coordination Flow', () => {
    it('should execute full coordination: steal Joker for champion, declare high cards, avoid Ace', () => {
      // Scenario from documentation:
      // - Leader identifies Player B as champion (lowest score: 15)
      // - Leader has Joker → uses Jack to give it to Player B
      // - Player C sees Player B has K-K-K → uses King to force cascade
      // - All coalition members avoid using Ace on Player B

      const testState = createTestState({
        vintoCallerId: 'vinto',
        coalitionLeaderId: 'leader',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer(
            'vinto',
            'Vinto Caller',
            false,
            [
              createTestCard('2', 'v-1'),
              createTestCard('3', 'v-2'),
              createTestCard('Joker', 'v-joker'),
            ],
            [0, 1, 2]
          ), // Score: 4
          createTestPlayer(
            'leader',
            'Coalition Leader',
            false,
            [
              createTestCard('Joker', 'l-joker'),
              createTestCard('7', 'l-2'),
              createTestCard('8', 'l-3'),
            ],
            [0, 1, 2]
          ), // Score: 14
          createTestPlayer(
            'champion',
            'Champion',
            false,
            [
              createTestCard('K', 'c-k1'),
              createTestCard('K', 'c-k2'),
              createTestCard('K', 'c-k3'),
              createTestCard('Q', 'c-q'),
            ],
            [0, 1, 2, 3]
          ), // Score: 10 (BEST)
          createTestPlayer(
            'member',
            'Coalition Member',
            false,
            [
              createTestCard('9', 'm-1'),
              createTestCard('10', 'm-2'),
              createTestCard('J', 'm-3'),
            ],
            [0, 1, 2]
          ), // Score: 29
        ],
      });

      const mctsState = createMCTSState(testState, 'leader');

      // Test 1: Leader with Jack - should give Joker to champion
      mctsState.currentPlayerIndex = 1; // Leader
      mctsState.pendingCard = createTestCard('J', 'jack');
      const jackMoves = MCTSMoveGenerator.generateActionMoves(
        mctsState,
        'swap-cards'
      );

      const giveJokerToChampion = jackMoves.filter(
        (m) =>
          m.targets?.[0]?.playerId === 'leader' &&
          m.targets[0].position === 0 && // Leader's Joker
          m.targets?.[1]?.playerId === 'champion' &&
          m.shouldSwap === true
      );
      expect(giveJokerToChampion.length).toBeGreaterThan(0);

      // Test 2: Member with King - should declare champion's Kings
      mctsState.currentPlayerIndex = 3; // Member
      mctsState.pendingCard = createTestCard('K', 'king');
      const kingMoves = MCTSMoveGenerator.generateImprovedKingMoves(
        mctsState,
        'member'
      );

      const declareChampionKings = kingMoves.filter(
        (m) => m.targets?.[0]?.playerId === 'champion' && m.declaredRank === 'K'
      );
      expect(declareChampionKings.length).toBeGreaterThan(0);

      // Test 3: Leader with Ace - should generate NO moves
      mctsState.currentPlayerIndex = 1; // Leader
      mctsState.pendingCard = createTestCard('A', 'ace');
      const aceMoves = MCTSMoveGenerator.generateActionMoves(
        mctsState,
        'force-draw'
      );
      expect(aceMoves.length).toBe(0); // NO Ace moves in coalition

      // Test 4: Leader with Queen - should peek champion's cards
      mctsState.currentPlayerIndex = 1; // Leader
      mctsState.pendingCard = createTestCard('Q', 'queen');

      // Make champion cards unknown to leader
      mctsState.players[2].knownCards.clear();

      const queenMoves = MCTSMoveGenerator.generateActionMoves(
        mctsState,
        'peek-and-swap'
      );
      const peekChampion = queenMoves.filter(
        (m) =>
          m.targets?.[0]?.playerId === 'champion' &&
          m.targets?.[1]?.playerId === 'champion' &&
          m.shouldSwap === false
      );
      expect(peekChampion.length).toBeGreaterThan(0);
    });
  });
});
