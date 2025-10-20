/**
 * Jack (J) Card Action Tests
 *
 * Rules:
 * - Value: 10
 * - Action: Swap any two facedown cards from two different players
 * - Note: Despite rules saying "two different players", implementation may allow same player
 * - After action completes, triggers toss-in period for Jack rank
 *
 * Tests cover:
 * 1. Normal flow - swapping two cards from different players
 * 2. Normal flow - swapping two cards from same player
 * 3. Toss-in flow - tossing in Jack during toss-in period
 * 4. Edge cases and validations
 */

import { describe, it, expect } from 'vitest';
import { GameEngine } from '../../game-engine';
import { GameActions } from '../../game-actions';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  toPile,
} from '../test-helpers';

describe('Jack (J) Card Action', () => {
  describe('Normal Flow - Swapping Cards from Different Players', () => {
    it('should allow swapping two cards from different players', () => {
      const jackCard = createTestCard('J', 'jack1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('A', 'p2c1'),
            createTestCard('2', 'p2c2'),
          ]),
        ],
        pendingAction: {
          card: jackCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p1', position: 0 }, // P1's King
            { playerId: 'p2', position: 1 }, // P2's 2
          ],
        },
      });

      // Execute Jack swap
      const newState = GameEngine.reduce(
        state,
        GameActions.executeQueenSwap('p1')
      );

      // Verify swap occurred
      expect(newState.players[0].cards[0].rank).toBe('2'); // P1 now has 2
      expect(newState.players[1].cards[1].rank).toBe('K'); // P2 now has K

      // Jack should be in discard pile
      expect(newState.discardPile.peekTop()?.id).toBe('jack1');

      // Should trigger toss-in
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.rank).toBe('J');
    });
  });

  describe('Normal Flow - Swapping Cards from Same Player', () => {
    it('should allow swapping two cards from same player', () => {
      const jackCard = createTestCard('J', 'jack1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('10', 'p2c1'),
            createTestCard('9', 'p2c2'),
            createTestCard('8', 'p2c3'),
            createTestCard('7', 'p2c4'),
          ]),
        ],
        pendingAction: {
          card: jackCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 }, // P2's 10
            { playerId: 'p2', position: 3 }, // P2's 7
          ],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.executeQueenSwap('p1')
      );

      // Verify swap within p2's hand
      expect(newState.players[1].cards[0].rank).toBe('7'); // Position 0 now has 7
      expect(newState.players[1].cards[3].rank).toBe('10'); // Position 3 now has 10

      // Other cards unchanged
      expect(newState.players[1].cards[1].rank).toBe('9');
      expect(newState.players[1].cards[2].rank).toBe('8');

      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.rank).toBe('J');
    });
  });

  describe('Normal Flow - Swapping Jack Instead of Using Action', () => {
    it('should allow swapping Jack into hand instead of using action', () => {
      const jackCard = createTestCard('J', 'jack1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('A', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: jackCard,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      // Swap Jack into hand
      const newState = GameEngine.reduce(
        state,
        GameActions.swapCard('p1', 0)
      );

      expect(newState.subPhase).toBe('selecting');
      expect(newState.players[0].cards[0].id).toBe('jack1'); // Jack swapped in
      expect(newState.pendingAction?.card.id).toBe('p1c1'); // King removed
    });
  });

  describe('Toss-In Flow', () => {
    it('should allow player to toss in Jack during toss-in period', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('J', 'p2j1'), // Has matching Jack
            createTestCard('7', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          rank: 'J',
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // P2 tosses in their Jack
      const newState = GameEngine.reduce(
        state,
        GameActions.participateInTossIn('p2', 0)
      );

      // Verify card was removed from hand
      expect(newState.players[1].cards.length).toBe(1);

      // Verify Jack action was queued
      expect(newState.activeTossIn?.queuedActions.length).toBe(1);
      expect(newState.activeTossIn?.queuedActions[0].card.rank).toBe('J');
      expect(newState.activeTossIn?.participants).toContain('p2');
    });

    it('should process queued Jack action requiring target selection', () => {
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
          rank: 'J',
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: ['p2'],
          queuedActions: [
            {
              playerId: 'p2',
              card: createTestCard('J', 'p2j1'),
              position: 0,
            },
          ],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // All players mark ready
      let newState = GameEngine.reduce(
        state,
        GameActions.playerTossInFinished('p1')
      );
      newState = GameEngine.reduce(
        newState,
        GameActions.playerTossInFinished('p2')
      );
      newState = GameEngine.reduce(
        newState,
        GameActions.playerTossInFinished('p3')
      );
      newState = GameEngine.reduce(
        newState,
        GameActions.playerTossInFinished('p4')
      );

      // Queued Jack action should start
      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.rank).toBe('J');
      expect(newState.pendingAction?.playerId).toBe('p2');

      // P2 selects two targets to swap
      newState = GameEngine.reduce(
        newState,
        GameActions.selectActionTarget('p2', 'p1', 0)
      );
      newState = GameEngine.reduce(
        newState,
        GameActions.selectActionTarget('p2', 'p3', 0)
      );

      // Execute swap
      newState = GameEngine.reduce(
        newState,
        GameActions.executeQueenSwap('p2')
      );

      // Should return to toss-in
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.rank).toBe('J');
      expect(newState.pendingAction).toBeNull();
    });
  });

  describe('Edge Cases and Validations', () => {
    it('should reject swap when not in awaiting_action phase', () => {
      const jackCard = createTestCard('J', 'jack1');

      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        pendingAction: {
          card: jackCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p1', position: 0 },
            { playerId: 'p2', position: 0 },
          ],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.executeQueenSwap('p1')
      );

      expect(newState).toEqual(state); // State unchanged
    });

    it('should reject when not player turn', () => {
      const jackCard = createTestCard('J', 'jack1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0, // P1's turn
        pendingAction: {
          card: jackCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p1', position: 0 },
            { playerId: 'p2', position: 0 },
          ],
        },
      });

      // P2 tries to execute swap (not their turn)
      const newState = GameEngine.reduce(
        state,
        GameActions.executeQueenSwap('p2')
      );

      expect(newState).toEqual(state); // State unchanged
    });

    it('should reject when not exactly 2 targets selected', () => {
      const jackCard = createTestCard('J', 'jack1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        pendingAction: {
          card: jackCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [{ playerId: 'p1', position: 0 }], // Only 1 target
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.executeQueenSwap('p1')
      );

      expect(newState).toEqual(state); // State unchanged
    });

    it('should handle swapping player own cards', () => {
      const jackCard = createTestCard('J', 'jack1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('A', 'p1c2'),
            createTestCard('7', 'p1c3'),
          ]),
        ],
        pendingAction: {
          card: jackCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p1', position: 0 }, // Own card
            { playerId: 'p1', position: 2 }, // Own card
          ],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.executeQueenSwap('p1')
      );

      // Cards should be swapped
      expect(newState.players[0].cards[0].rank).toBe('7'); // Was K
      expect(newState.players[0].cards[2].rank).toBe('K'); // Was 7
    });
  });
});
