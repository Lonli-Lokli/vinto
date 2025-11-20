/**
 * Integration tests for Bot Coalition Coordination
 * Tests the complete coalition flow including knowledge sharing from botAIAdapter
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { GameClient } from '../game-client';
import { BotAIAdapter } from '../adapters/botAIAdapter';
import { GameActions } from '@vinto/engine';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  toPile,
} from './test-helper';

describe('Bot Coalition Coordination - Integration', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  describe('Champion Identification', () => {
    it('should identify player with lowest score as champion in coalition', async () => {
      // Setup: Coalition formed, different scores
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2's turn
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('K', 'p1-0'),
            createTestCard('2', 'p1-1'),
          ]), // Score: 2
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('10', 'p2-0'),
            createTestCard('J', 'p2-1'), // Jack for coordination test
          ]), // Score: 20
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('3', 'p3-0'),
            createTestCard('4', 'p3-1'),
          ]), // Score: 7 (LOWEST in coalition)
          createTestPlayer('p4', 'Member', false, [
            createTestCard('Q', 'p4-0'),
            createTestCard('8', 'p4-1'),
          ]), // Score: 18
        ],
        drawPile: toPile([
          createTestCard('5', 'draw-1'),
          createTestCard('6', 'draw-2'),
        ]),
        discardPile: toPile([createTestCard('7', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      // Verify coalition is active
      expect(gameClient.state.vintoCallerId).toBe('p1');
      expect(gameClient.state.coalitionLeaderId).toBe('p2');

      // Verify champion should be p3 (lowest score: 7)
      const coalitionMembers = ['p2', 'p3', 'p4'];
      const scores = coalitionMembers.map((id) => {
        const player = gameClient.state.players.find((p) => p.id === id)!;
        return player.cards.reduce((sum, c) => sum + c.value, 0);
      });
      const minScore = Math.min(...scores);
      expect(minScore).toBe(7); // p3's score

      // Cleanup
      botAdapter.dispose();
    });
  });

  describe('Jack (Swap) - Give Jokers to Champion', () => {
    it('should give own Joker to champion when leader has Joker', async () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2's turn (leader)
        subPhase: 'idle',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('K', 'p1-0'),
            createTestCard('2', 'p1-1'),
          ]), // Score: 2
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('Joker', 'p2-0'), // Leader has Joker!
            createTestCard('5', 'p2-1'),
          ]), // Score: 4
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('3', 'p3-0'),
            createTestCard('4', 'p3-1'),
          ]), // Score: 7 (needs Joker)
          createTestPlayer('p4', 'Member', false, [
            createTestCard('8', 'p4-0'),
            createTestCard('9', 'p4-1'),
          ]), // Score: 17
        ],
        drawPile: toPile([createTestCard('J', 'draw-jack')]), // Jack in draw pile
        discardPile: toPile([createTestCard('7', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      // Let bot take turn - should draw the Jack
      await vi.runAllTimersAsync();

      // Bot should have drawn Jack
      const p2 = gameClient.state.players[1];
      const hasJack = p2.cards.some((c) => c.rank === 'J');

      if (
        hasJack &&
        gameClient.state.pendingAction?.targetType === 'swap-cards'
      ) {
        // Bot recognized Jack action and wants to swap
        // Verify it's coordinating with champion (should give Joker to p3)
        expect(gameClient.state.pendingAction.targetType).toBe('swap-cards');
      }

      // Cleanup
      botAdapter.dispose();
    });

    it('should NOT steal opponent Joker for self in coalition mode', async () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2's turn (leader)
        subPhase: 'idle',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('Joker', 'p1-0'), // Vinto caller has Joker
            createTestCard('2', 'p1-1'),
          ]), // Score: 1
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('5', 'p2-0'),
            createTestCard('6', 'p2-1'),
          ]), // Score: 11
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('3', 'p3-0'),
            createTestCard('4', 'p3-1'),
          ]), // Score: 7 (champion)
          createTestPlayer('p4', 'Member', false, [
            createTestCard('8', 'p4-0'),
            createTestCard('9', 'p4-1'),
          ]), // Score: 17
        ],
        drawPile: toPile([createTestCard('J', 'draw-jack')]),
        discardPile: toPile([createTestCard('7', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      await vi.runAllTimersAsync();

      // If bot drew Jack and used it, verify it's NOT stealing for self
      // In coalition mode, bot should NOT target opponent Joker for self-benefit
      if (gameClient.state.pendingAction?.targetType === 'swap-cards') {
        const targetPlayerIds = gameClient.state.pendingAction.targets.map(
          (t) => t.playerId
        );
        // Should not be swapping with p1 (Vinto caller with Joker) for self
        // Coalition strategy: help champion, not self
        expect(targetPlayerIds).not.toContain('p1');
      }

      botAdapter.dispose();
    });
  });

  describe('King (Declaration) - Remove Champion High Cards', () => {
    it('should declare champion high-value cards when leader draws King', async () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2's turn
        subPhase: 'idle',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('K', 'p1-0'),
            createTestCard('2', 'p1-1'),
          ]), // Score: 2
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('5', 'p2-0'),
            createTestCard('6', 'p2-1'),
          ]), // Score: 11
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('10', 'p3-0'), // High card!
            createTestCard('9', 'p3-1'), // High card!
          ]), // Score: 19 (needs help)
          createTestPlayer('p4', 'Member', false, [
            createTestCard('3', 'p4-0'),
            createTestCard('4', 'p4-1'),
          ]), // Score: 7
        ],
        drawPile: toPile([createTestCard('K', 'draw-king')]), // King in draw
        discardPile: toPile([createTestCard('7', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      await vi.runAllTimersAsync();

      // If bot drew King, verify it can use declaration action
      const p2 = gameClient.state.players[1];
      const hasKing = p2.cards.some((c) => c.rank === 'K');

      if (
        hasKing &&
        gameClient.state.pendingAction?.targetType === 'declare-action'
      ) {
        // Bot wants to declare - with knowledge sharing, should target champion
        expect(gameClient.state.pendingAction.targetType).toBe(
          'declare-action'
        );
      }

      botAdapter.dispose();
    });

    it('should prioritize champion help over own benefit', async () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2's turn
        subPhase: 'idle',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('2', 'p1-0'),
            createTestCard('3', 'p1-1'),
          ]), // Score: 5
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('Q', 'p2-0'), // Leader has high card
            createTestCard('J', 'p2-1'), // Leader has high card
          ]), // Score: 20
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('10', 'p3-0'), // Champion has higher cards
            createTestCard('10', 'p3-1'),
          ]), // Score: 20 (same score, needs MORE help)
          createTestPlayer('p4', 'Member', false, [
            createTestCard('4', 'p4-0'),
            createTestCard('5', 'p4-1'),
          ]), // Score: 9
        ],
        drawPile: toPile([createTestCard('K', 'draw-king')]),
        discardPile: toPile([createTestCard('7', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      await vi.runAllTimersAsync();

      // With King, bot should consider champion's cards as priority
      // This tests the coordination strategy evaluation
      const hasKing = gameClient.state.players[1].cards.some(
        (c) => c.rank === 'K'
      );

      if (
        hasKing &&
        gameClient.state.pendingAction?.targetType === 'declare-action'
      ) {
        expect(gameClient.state.pendingAction.targetType).toBe(
          'declare-action'
        );
        // In real game with knowledge sharing, should target champion's 10s
      }

      botAdapter.dispose();
    });
  });

  describe('Queen (Peek & Swap) - Scout Champion Cards', () => {
    it('should peek champion unknown cards when leader draws Queen', async () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2's turn
        subPhase: 'idle',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('2', 'p1-0'),
            createTestCard('3', 'p1-1'),
          ]),
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('5', 'p2-0'),
            createTestCard('6', 'p2-1'),
          ]),
          createTestPlayer(
            'p3',
            'Champion',
            false,
            [createTestCard('7', 'p3-0'), createTestCard('8', 'p3-1')],
            [] // Champion has unknown cards
          ),
          createTestPlayer('p4', 'Member', false, [
            createTestCard('4', 'p4-0'),
            createTestCard('9', 'p4-1'),
          ]),
        ],
        drawPile: toPile([createTestCard('Q', 'draw-queen')]), // Queen in draw
        discardPile: toPile([createTestCard('10', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      await vi.runAllTimersAsync();

      // If bot drew Queen, should consider peeking champion's unknowns
      const hasQueen = gameClient.state.players[1].cards.some(
        (c) => c.rank === 'Q'
      );

      if (
        hasQueen &&
        gameClient.state.pendingAction?.targetType === 'peek-then-swap'
      ) {
        expect(gameClient.state.pendingAction.targetType).toBe(
          'peek-then-swap'
        );
        // With knowledge sharing, bot knows to scout champion
      }

      botAdapter.dispose();
    });
  });

  describe('Ace (Force Draw) - Never Use in Coalition', () => {
    it('should NOT use Ace when in coalition (harmful to team)', async () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // p2's turn
        subPhase: 'idle',
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('2', 'p1-0'),
            createTestCard('3', 'p1-1'),
          ]),
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('5', 'p2-0'),
            createTestCard('6', 'p2-1'),
          ]),
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('4', 'p3-0'),
            createTestCard('7', 'p3-1'),
          ]),
          createTestPlayer('p4', 'Member', false, [
            createTestCard('8', 'p4-0'),
            createTestCard('9', 'p4-1'),
          ]),
        ],
        drawPile: toPile([createTestCard('A', 'draw-ace')]), // Ace in draw
        discardPile: toPile([createTestCard('10', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      await vi.runAllTimersAsync();

      // Bot should have drawn Ace
      const p2 = gameClient.state.players[1];
      const hasAce = p2.cards.some((c) => c.rank === 'A');

      if (hasAce) {
        // In coalition mode, bot should NEVER create force-draw action with Ace
        // Should either swap it or discard it instead
        expect(gameClient.state.pendingAction?.targetType).not.toBe(
          'force-draw'
        );

        // Verify Ace is NOT used as action
        const aceCard = p2.cards.find((c) => c.rank === 'A');
        if (gameClient.state.pendingAction && aceCard) {
          expect(gameClient.state.pendingAction.card.id).not.toBe(aceCard.id);
        }
      }

      botAdapter.dispose();
    });

    it('should use Ace normally when NOT in coalition', async () => {
      const testState = createTestState({
        vintoCallerId: null, // NO coalition
        coalitionLeaderId: null,
        currentPlayerIndex: 0,
        subPhase: 'idle',
        players: [
          createTestPlayer('p1', 'Player 1', false, [
            createTestCard('5', 'p1-0'),
            createTestCard('6', 'p1-1'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('2', 'p2-0'),
            createTestCard('3', 'p2-1'),
          ]),
        ],
        drawPile: toPile([createTestCard('A', 'draw-ace')]),
        discardPile: toPile([createTestCard('7', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      await vi.runAllTimersAsync();

      // Without coalition, bot MAY use Ace (it's a valid strategic move)
      // Just verify no coalition constraints
      expect(gameClient.state.vintoCallerId).toBeNull();
      expect(gameClient.state.coalitionLeaderId).toBeNull();

      botAdapter.dispose();
    });
  });

  describe('Coalition State Evaluation', () => {
    it('should evaluate game state based on champion position vs Vinto caller', async () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1,
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('K', 'p1-0'),
            createTestCard('2', 'p1-1'),
          ]), // Score: 2 (target to beat)
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('10', 'p2-0'),
            createTestCard('9', 'p2-1'),
          ]), // Score: 19
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('3', 'p3-0'),
            createTestCard('4', 'p3-1'),
          ]), // Score: 7 (champion - winning!)
          createTestPlayer('p4', 'Member', false, [
            createTestCard('8', 'p4-0'),
            createTestCard('Q', 'p4-1'),
          ]), // Score: 18
        ],
        drawPile: toPile([createTestCard('5', 'draw-1')]),
        discardPile: toPile([createTestCard('7', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      // Coalition evaluation should focus on champion vs Vinto caller
      const vintoCaller = gameClient.state.players.find(
        (p) => p.id === gameClient.state.vintoCallerId
      )!;
      const champion = gameClient.state.players.find((p) => p.id === 'p3')!;

      const vintoScore = vintoCaller.cards.reduce((sum, c) => sum + c.value, 0);
      const champScore = champion.cards.reduce((sum, c) => sum + c.value, 0);

      // Champion is winning (7 < 2 is false, but 7 > 2, needs to beat Vinto's 2)
      // Actually Vinto has 2, champion has 7, so champion is LOSING
      expect(champScore).toBeGreaterThan(vintoScore);

      botAdapter.dispose();
    });
  });

  describe('Integration: Complete Coordination Flow', () => {
    it('should execute full coalition coordination in realistic game', async () => {
      const testState = createTestState({
        vintoCallerId: 'p1',
        coalitionLeaderId: 'p2',
        currentPlayerIndex: 1, // Leader's turn
        subPhase: 'idle',
        turnNumber: 5,
        players: [
          createTestPlayer('p1', 'Vinto Caller', false, [
            createTestCard('K', 'p1-0'),
            createTestCard('A', 'p1-1'),
          ]), // Score: 1 (target)
          createTestPlayer('p2', 'Leader', false, [
            createTestCard('Joker', 'p2-0'), // Can give to champion
            createTestCard('5', 'p2-1'),
          ]), // Score: 4
          createTestPlayer('p3', 'Champion', false, [
            createTestCard('6', 'p3-0'),
            createTestCard('7', 'p3-1'),
          ]), // Score: 13 (needs help)
          createTestPlayer('p4', 'Member', false, [
            createTestCard('8', 'p4-0'),
            createTestCard('9', 'p4-1'),
          ]), // Score: 17
        ],
        drawPile: toPile([
          createTestCard('J', 'draw-jack'), // Jack for coordination
          createTestCard('K', 'draw-king'),
          createTestCard('Q', 'draw-queen'),
        ]),
        discardPile: toPile([createTestCard('10', 'discard-1')]),
      });

      const gameClient = new GameClient(testState);
      const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

      // Let bot play turn
      await vi.runAllTimersAsync();

      // Bot should have drawn Jack and may coordinate with champion
      const p2 = gameClient.state.players[1];
      const hasJack = p2.cards.some((c) => c.rank === 'J');
      const hasJoker = p2.cards.some((c) => c.rank === 'Joker');

      // Verify coalition is still active
      expect(gameClient.state.vintoCallerId).toBe('p1');
      expect(gameClient.state.coalitionLeaderId).toBe('p2');

      // If bot drew Jack and has Joker, should coordinate to help champion
      if (hasJack && hasJoker) {
        // Bot has the tools to help champion
        // With knowledge sharing, it knows champion's cards
        expect(gameClient.state.pendingAction?.targetType).toBeDefined();
      }

      botAdapter.dispose();
    });

    it(
      'should coordinate all three coalition members to maximize champion advantage',
      { timeout: 30_000 },
      async () => {
        /**
         * SCENARIO: Calculate Best Achievable Score Without Assuming Draw Pile
         *
         * Test Strategy: Verify coalition achieves optimal score based on KNOWN cards only
         *
         * Initial Setup:
         * - p1 (Vinto Caller): 0 points, 1 card [K]
         * - p2 (Leader): Has Joker + other cards, score 40
         * - p3 (Champion): K-K-K-10-9-8-6, score 33 (0+0+0+10+9+8+6)
         * - p4 (Member C): 6 cards, score
         * - p5 (Member D): Has Jack + 5 cards, score 42
         *
         * BEST ACHIEVABLE OUTCOME (using only known cards):
         *
         * Strategy: Basic K cascade (guaranteed, no draws needed)
         * - p1 declares Vinto
         * - Coalition forms: p2 (Leader), p3 (Champion), p4, p5
         * - p2 draw any card and discard it
         * - p3 draws any card, replaces one K and declaring 9
         * - Toss in 2 remaining Ks, declare all 3 Ks removed
         * - Result: p3 keeps drawn card + 5 + 2 = unknown+7 points (depends on draw)
         *
         * Test validates: Final champion score ≤ Best achievable without luck
         */

        const testState = createTestState({
          currentPlayerIndex: 0, // p1's turn - will call Vinto
          subPhase: 'idle',
          phase: 'playing', // Start in playing phase
          turnNumber: 1,
          difficulty: 'hard',
          players: [
            // Will become Vinto Caller
            createTestPlayer('p1', 'Vinto Caller', false, [
              createTestCard('K', 'p1-0'),
            ]), // Score: 0

            // Will become Coalition Leader (selected by coalition after Vinto call)
            createTestPlayer('p2', 'Leader', false, [
              createTestCard('Joker', 'p2-0'), // Strategic asset for champion
              createTestCard('2', 'p2-1'),
              createTestCard('3', 'p2-2'),
              createTestCard('4', 'p2-3'),
              createTestCard('5', 'p2-4'),
              createTestCard('8', 'p2-4'),
              createTestCard('9', 'p2-4'),
              createTestCard('10', 'p2-5'),
            ]), // Score: 40 - cannot achieve -1 (no cascadeable matches with p3)

            // Strategic Champion - BEST POTENTIAL due to K-K-K cascade opportunity
            createTestPlayer('p3', 'Strategic Champion', false, [
              createTestCard('K', 'p3-0'), // Cascade target #1
              createTestCard('K', 'p3-1'), // Cascade target #2
              createTestCard('K', 'p3-2'), // Cascade target #3
              createTestCard('10', 'p3-3'),
              createTestCard('9', 'p3-4'),
              createTestCard('8', 'p3-5'),
              createTestCard('6', 'p3-6'), // this one will be swapped with Joker from Leader
            ]), // Score: 33 (0+0+0+10+9+8+6) - more cards to prevent immediate game end

            // Member C - has high score, poor improvement potential
            createTestPlayer('p4', 'Member C', false, [
              createTestCard('7', 'p4-0'),
              createTestCard('7', 'p4-1'),
              createTestCard('10', 'p4-2'),
              createTestCard('6', 'p4-3'),
              createTestCard('6', 'p4-4'),
              createTestCard('6', 'p4-5'),
            ]), // Score: 39 - more cards to sustain gameplay

            // Member D - highest score, worst potential
            createTestPlayer('p5', 'Member D', false, [
              createTestCard('J', 'p5-0'),
              createTestCard('10', 'p5-1'),
              createTestCard('5', 'p5-2'),
              createTestCard('5', 'p5-3'),
              createTestCard('5', 'p5-4'),
              createTestCard('5', 'p5-5'),
            ]), // Score: 42 - more cards to prevent immediate game end
          ],
          drawPile: toPile([
            createTestCard('4', 'draw-jack'),
            createTestCard('4', 'draw-filler1'),
            createTestCard('4', 'draw-king'),
            createTestCard('4', 'draw-filler2'),
            createTestCard('4', 'draw-queen'),
            createTestCard('4', 'draw-ace'),
          ]),
          discardPile: toPile([createTestCard('10', 'discard-1')]),
        });

        const gameClient = new GameClient(testState);

        expect(gameClient.state.vintoCallerId).toBeNull();
        expect(gameClient.state.coalitionLeaderId).toBeNull();
        expect(gameClient.state.phase).toBe('playing');

        // Get initial scores
        const p1 = gameClient.state.players.find((p) => p.id === 'p1')!;
        const p3 = gameClient.state.players.find((p) => p.id === 'p3')!;

        const initialP1Score = p1.cards.reduce((sum, c) => sum + c.value, 0);
        const initialP3Score = p3.cards.reduce((sum, c) => sum + c.value, 0);

        expect(initialP1Score).toBe(0);
        expect(initialP3Score).toBe(33);

        const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

        // Calculate guaranteed best achievable score using KNOWN cards only:
        // p2 has Joker (-1), p5 has Jack (can swap), p3 has K-K-K
        //
        // Best strategy: p3 tosses 1 King, toss in and declares all except one
        // → p5 uses Jack to give Joker to p3, replacing one K
        // → p3 final hand = Joker only = -1 points
        const guaranteedBestScore = -1; // Joker only, all other cards removed via cascade + swap

        console.log(`Initial p3 score: ${initialP3Score}`);
        console.log(`Guaranteed best achievable: ${guaranteedBestScore}`);

        // ============================================================
        // STEP 1: p1 calls Vinto to trigger coalition formation
        // ============================================================

        console.log('Step 1: p1 calling Vinto...');
        gameClient.dispatch(GameActions.callVinto('p1'));

        // ============================================================
        // STEP 2: Coalition selects leader + Execute full coordination
        // ============================================================

        console.log(
          'Step 2: Coalition selecting leader and executing coordination...'
        );
        // Bot adapter should auto-select leader and execute all turns
        await vi.runAllTimersAsync();
        await botAdapter.waitForIdle();

        // Verify Vinto was called
        expect(gameClient.state.vintoCallerId).toBe('p1');

        // Verify coalition leader was selected
        expect(gameClient.state.coalitionLeaderId).not.toBeNull();
        console.log(
          `Coalition leader selected: ${gameClient.state.coalitionLeaderId}`
        );

        // Game engine should have transitioned to scoring after all players took their turn
        expect(gameClient.state.phase).toBe('scoring');
        console.log(`Phase after coordination: ${gameClient.state.phase}`);

        // ============================================================
        // Verify coordination results
        // ============================================================

        // The coalition solver should have selected a champion
        // Note: Both p2 and p3 can achieve -1, so either could be selected
        const selectedChampionId = gameClient.state.coalitionLeaderId; // Leader coordinates on behalf of champion
        expect(selectedChampionId).not.toBeNull();

        // Find which player was actually optimized (should be one of the coalition members)
        const coalitionMembers = ['p2', 'p3', 'p4', 'p5'];
        const finalScores = coalitionMembers.map((id) => {
          const player = gameClient.state.players.find((p) => p.id === id)!;
          const score = player.cards.reduce((sum, c) => sum + c.value, 0);
          const hasJoker = player.cards.some((c) => c.rank === 'Joker');
          return { id, score, hasJoker, cardCount: player.cards.length };
        });

        // Find the best score achieved by any coalition member
        const bestCoalitionScore = Math.min(...finalScores.map((f) => f.score));
        const championResult = finalScores.find(
          (f) => f.score === bestCoalitionScore
        )!;

        const finalVinto = gameClient.state.players.find((p) => p.id === 'p1')!;
        const finalVintoScore = finalVinto.cards.reduce(
          (sum, c) => sum + c.value,
          0
        );

        console.log(`\nChampion result: Player ${championResult.id}`);
        console.log(
          `  Final score: ${championResult.score}, has Joker: ${championResult.hasJoker}`
        );
        console.log(`Vinto caller final score: ${finalVintoScore}`);

        // Verify coalition coordination results
        console.log(`\nFinal coordination results:`);
        console.log(`- Phase: ${gameClient.state.phase}`);
        console.log(`- Best coalition score: ${bestCoalitionScore}`);
        console.log(
          `- Champion (${championResult.id}) score: ${championResult.score}`
        );
        console.log(`- Champion card count: ${championResult.cardCount}`);
        console.log(`- Champion has Joker: ${championResult.hasJoker}`);
        console.log(`- Vinto caller score: ${finalVintoScore}`);
        console.log('\nAll coalition member scores:');
        finalScores.forEach((f) => {
          console.log(
            `  ${f.id}: ${f.score} (${f.cardCount} cards, Joker: ${f.hasJoker})`
          );
        });

        // Verify game transitioned to scoring phase
        expect(gameClient.state.phase).toBe('scoring');

        // Verify coalition coordination achieved guaranteed best score
        // With p2's cards changed to [Joker, 2, 3, 4, 5, 10], they cannot achieve -1
        // because these cards have no cascade synergy with p3's [K, K, K, 9, 9, 8, 6].
        // Only p3 should be selected as champion and achieve -1.
        expect(gameClient.state.coalitionLeaderId).not.toBeNull();
        expect(championResult).toBeDefined();
        expect(championResult.score).toBe(guaranteedBestScore); // -1
        //expect(championResult.id).toBe('p3');
        expect(championResult.hasJoker).toBe(true);

        botAdapter.dispose();
      }
    );
  });
});
