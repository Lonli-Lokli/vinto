/**
 * Queen (Q) Card Action Tests
 *
 * Rules:
 * - Value: 10
 * - Action: Peek any two cards from two different players, then optionally swap them
 * - After action completes, triggers toss-in period for Queen rank
 *
 * Tests cover:
 * 1. Normal flow - peeking two cards without swapping
 * 2. Normal flow - peeking and then swapping the two cards
 * 3. Toss-in flow - tossing in Queen during toss-in period
 * 4. Edge cases and validations
 */

import { describe, it, expect } from 'vitest';
import { GameEngine } from '../../game-engine';
import { GameActions } from '../../game-actions';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  markPlayersReady,
  toPile,
  unsafeReduce,
} from '../test-helpers';

describe('Queen (Q) Card Action', () => {
  describe('Normal Flow - Peek Without Swapping', () => {
    it('should allow peeking two cards from different players without swapping', () => {
      const queenCard = createTestCard('Q', 'queen1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('K', 'p2c1'),
            createTestCard('A', 'p2c2'),
          ]),
          createTestPlayer('p3', 'Player 3', false, [
            createTestCard('7', 'p3c1'),
            createTestCard('8', 'p3c2'),
          ]),
        ],
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 }, // K
            { playerId: 'p3', position: 1 }, // 8
          ],
        },
      });

      // Skip the swap
      const newState = unsafeReduce(state, GameActions.skipQueenSwap('p1'));

      // Cards should remain unchanged
      expect(newState.players[1].cards[0].rank).toBe('K');
      expect(newState.players[2].cards[1].rank).toBe('8');

      // Queen should be in discard pile
      expect(newState.discardPile.peekTop()?.id).toBe('queen1');

      // Should trigger toss-in
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.ranks).toContain('Q');
      expect(newState.activeTossIn?.initiatorId).toBe('p1');
    });

    it('should reject peeking two cards from same player', () => {
      const queenCard = createTestCard('Q', 'queen1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('K', 'p2c1'),
            createTestCard('A', 'p2c2'),
            createTestCard('7', 'p2c3'),
          ]),
        ],
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 }, // K
            { playerId: 'p2', position: 2 }, // 7
          ],
        },
      });

      expect(() =>
        unsafeReduce(state, GameActions.skipQueenSwap('p1'))
      ).toThrow();
    });
  });

  describe('Normal Flow - Peek and Swap', () => {
    it('should allow swapping two peeked cards from different players', () => {
      const queenCard = createTestCard('Q', 'queen1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('K', 'p2c1'),
            createTestCard('A', 'p2c2'),
          ]),
          createTestPlayer('p3', 'Player 3', false, [
            createTestCard('7', 'p3c1'),
            createTestCard('8', 'p3c2'),
          ]),
        ],
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 }, // K
            { playerId: 'p3', position: 1 }, // 8
          ],
        },
      });

      // Execute the swap
      const newState = unsafeReduce(state, GameActions.executeQueenSwap('p1'));

      // Cards should be swapped
      expect(newState.players[1].cards[0].rank).toBe('8'); // Was K, now 8
      expect(newState.players[2].cards[1].rank).toBe('K'); // Was 8, now K

      // Queen should be in discard pile
      expect(newState.discardPile.peekTop()?.id).toBe('queen1');

      // Should trigger toss-in
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.ranks).toContain('Q');
    });

    it('should reject swapping two cards from same player', () => {
      const queenCard = createTestCard('Q', 'queen1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('K', 'p2c1'),
            createTestCard('A', 'p2c2'),
            createTestCard('7', 'p2c3'),
            createTestCard('8', 'p2c4'),
          ]),
        ],
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 }, // K
            { playerId: 'p2', position: 3 }, // 8
          ],
        },
      });

      expect(() =>
        unsafeReduce(state, GameActions.executeQueenSwap('p1'))
      ).toThrow();
    });
  });

  describe('Normal Flow - Swapping Queen Instead of Using Action', () => {
    it('should allow swapping Queen into hand instead of using action', () => {
      const queenCard = createTestCard('Q', 'queen1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('A', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      // Swap Queen into hand
      const newState = unsafeReduce(state, GameActions.swapCard('p1', 0));

      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.players[0].cards[0].id).toBe('queen1'); // Queen swapped in
      expect(newState.pendingAction).toBeNull(); // No pending action

      // Previous first card should be on discard pile
      expect(newState.discardPile.peekTop()?.id).toBe('p1c1');
    });
  });

  describe('Toss-In Flow', () => {
    it('should allow player to toss in Queen during toss-in period', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        turnNumber: 1,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('Q', 'p2q1'), // Has matching Queen
            createTestCard('7', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['Q'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,

          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // P2 tosses in their Queen
      const newState = unsafeReduce(
        state,
        GameActions.participateInTossIn('p2', [0])
      );

      // Verify card was removed from hand
      expect(newState.players[1].cards.length).toBe(1);

      // Verify Queen action was queued
      expect(newState.activeTossIn?.queuedActions.length).toBe(1);
      expect(newState.activeTossIn?.queuedActions[0].rank).toBe('Q');
      expect(newState.activeTossIn?.participants).toContain('p2');
    });

    it('should process queued Queen action requiring target selection', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('A', 'p2c1'),
          ]),
          createTestPlayer('p3', 'Player 3', false, [
            createTestCard('7', 'p3c1'),
          ]),
        ],
        activeTossIn: {
          ranks: ['Q'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,

          participants: ['p2'],
          queuedActions: [
            {
              playerId: 'p2',
              rank: 'Q',
              position: 0,
            },
          ],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // All players mark ready
      let newState = markPlayersReady(state, ['p1', 'p2', 'p3']);

      newState = unsafeReduce(newState, GameActions.playCardAction('p2'));

      // Queued Queen action should start
      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.rank).toBe('Q');
      expect(newState.pendingAction?.playerId).toBe('p2');

      // P2 selects two targets to peek
      newState = unsafeReduce(
        newState,
        GameActions.selectActionTarget('p2', 'p1', 0)
      );
      newState = unsafeReduce(
        newState,
        GameActions.selectActionTarget('p2', 'p3', 0)
      );

      // P2 decides to swap
      newState = unsafeReduce(newState, GameActions.executeQueenSwap('p2'));

      // Should return to toss-in (not create new toss-in)
      expect(newState.subPhase).toBe('ai_thinking');
      expect(newState.activeTossIn?.ranks).toContain('Q'); // Same toss-in continues
      expect(newState.pendingAction).toBeNull();
    });
  });

  describe('Edge Cases and Validations', () => {
    it('should reject swap when not in awaiting_action phase', () => {
      const queenCard = createTestCard('Q', 'queen1');

      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 },
            { playerId: 'p3', position: 1 },
          ],
        },
      });

      expect(() =>
        unsafeReduce(state, GameActions.executeQueenSwap('p1'))
      ).toThrow();
    });

    it('should reject when not player turn', () => {
      const queenCard = createTestCard('Q', 'queen1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0, // P1's turn
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 },
            { playerId: 'p3', position: 1 },
          ],
        },
      });

      // P2 tries to execute swap (not their turn)
      expect(() =>
        unsafeReduce(state, GameActions.executeQueenSwap('p2'))
      ).toThrow();
    });

    it('should reject when not exactly 2 targets selected', () => {
      const queenCard = createTestCard('Q', 'queen1');

      // Test with 1 target
      const state1 = createTestState({
        subPhase: 'awaiting_action',
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [{ playerId: 'p2', position: 0 }], // Only 1 target
        },
      });

      expect(() =>
        unsafeReduce(state1, GameActions.executeQueenSwap('p1'))
      ).toThrow();

      // Test with 3 targets
      const state3 = createTestState({
        subPhase: 'awaiting_action',
        pendingAction: {
          card: queenCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 },
            { playerId: 'p2', position: 1 },
            { playerId: 'p2', position: 2 }, // 3 targets
          ],
        },
      });

      expect(() =>
        unsafeReduce(state3, GameActions.executeQueenSwap('p1'))
      ).toThrow();
    });
  });
});
