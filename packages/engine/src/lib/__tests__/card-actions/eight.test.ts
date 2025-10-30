/**
 * 8 Card Action Tests
 *
 * Rules:
 * - Value: 8
 * - Action: Peek one of your own cards
 * - After action completes, triggers toss-in period for 8 rank
 *
 * Tests cover:
 * 1. Normal flow - peeking own card
 * 2. Toss-in flow - tossing in 8 during toss-in period
 * 3. Edge cases and validations
 */

import { describe, it, expect } from 'vitest';
import { GameActions } from '../../game-actions';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  markPlayersReady,
  toPile,
  unsafeReduce,
} from '../test-helpers';

describe('8 Card Action', () => {
  describe('Normal Flow - Peek Own Card', () => {
    it('should allow peeking own card', () => {
      const eightCard = createTestCard('8', 'eight1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: eightCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Select own card to peek
      let newState = unsafeReduce(
        state,
        GameActions.selectActionTarget('p1', 'p1', 1)
      );

      // Confirm peek
      newState = unsafeReduce(newState, GameActions.confirmPeek('p1'));

      // Should trigger toss-in
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.ranks).toContain('8');
      expect(newState.discardPile.peekTop()?.id).toBe('eight1');
      expect(newState.discardPile.peekTop()?.played).toBe(true);
    });

    it('should allow swapping 8 into hand instead of using action', () => {
      const eightCard = createTestCard('8', 'eight1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: eightCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const newState = unsafeReduce(state, GameActions.swapCard('p1', 0));

      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.players[0].cards[0].id).toBe('eight1');
    });
  });

  describe('Toss-In Flow', () => {
    it('should allow player to toss in 8 during toss-in period', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        turnNumber: 1,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('8', 'p2e1'), // Has matching 8
            createTestCard('7', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['8'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      const newState = unsafeReduce(
        state,
        GameActions.participateInTossIn('p2', [0])
      );

      expect(newState.players[1].cards.length).toBe(1);
      expect(newState.activeTossIn?.queuedActions.length).toBe(1);
      expect(newState.activeTossIn?.queuedActions[0].rank).toBe('8');
    });

    it('should process queued 8 action requiring peek target', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        turnNumber: 1,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('A', 'p2c1'),
            createTestCard('K', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['8'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: ['p2'],
          queuedActions: [
            {
              playerId: 'p2',
              rank: '8',
              position: 0,
            },
          ],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // All players mark ready
      let newState = markPlayersReady(state, ['p1', 'p2']);

      newState = unsafeReduce(newState, GameActions.playCardAction('p2'));

      // Queued 8 action should start
      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.rank).toBe('8');

      // P2 peeks own card
      newState = unsafeReduce(
        newState,
        GameActions.selectActionTarget('p2', 'p2', 0)
      );

      // Confirm peek
      newState = unsafeReduce(newState, GameActions.confirmPeek('p2'));

      // Should return to toss-in
      expect(newState.subPhase).toBe('toss_queue_active'); // next player is bot so ai_thinking
      expect(newState.activeTossIn?.queuedActions.length).toBe(0);
    });
  });

  describe('Edge Cases', () => {
    it('should reject when not player turn', () => {
      const eightCard = createTestCard('8', 'eight1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0, // P1's turn
        pendingAction: {
          card: eightCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      expect(() =>
        unsafeReduce(state, GameActions.selectActionTarget('p2', 'p2', 0))
      ).toThrow();
    });
  });
});
