/**
 * 9 Card Action Tests
 *
 * Rules:
 * - Value: 9
 * - Action: Peek one card of another player (not own card)
 * - After action completes, triggers toss-in period for 9 rank
 *
 * Tests cover:
 * 1. Normal flow - peeking opponent card
 * 2. Toss-in flow - tossing in 9 during toss-in period
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

describe('9 Card Action', () => {
  describe('Normal Flow - Peek Opponent Card', () => {
    it('should allow peeking one opponent card', () => {
      const nineCard = createTestCard('9', 'nine1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('K', 'p2c1'),
            createTestCard('A', 'p2c2'),
          ]),
        ],
        pendingAction: {
          card: nineCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Select opponent card to peek
      let newState = unsafeReduce(
        state,
        GameActions.selectActionTarget('p1', 'p2', 0)
      );

      // Confirm peek
      newState = unsafeReduce(newState, GameActions.confirmPeek('p1'));

      // Should trigger toss-in
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.ranks).toContain('9');
      expect(newState.discardPile.peekTop()?.id).toBe('nine1');
    });

    it('should allow swapping 9 into hand instead of using action', () => {
      const nineCard = createTestCard('9', 'nine1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: nineCard,
          from: 'drawing',
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const newState = unsafeReduce(state, GameActions.swapCard('p1', 0));

      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.players[0].cards[0].id).toBe('nine1');
      expect(newState.discardPile.peekTop()?.id).toBe('p1c1');
    });
  });

  describe('Toss-In Flow', () => {
    it('should allow player to toss in 9 during toss-in period', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        turnNumber: 1,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('9', 'p2n1'), // Has matching 9
            createTestCard('7', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['9'],
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
      expect(newState.activeTossIn?.queuedActions[0].card.rank).toBe('9');
    });

    it('should process queued 9 action requiring peek target', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        turnNumber: 1,
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('A', 'p2c1'),
          ]),
        ],
        activeTossIn: {
          ranks: ['9'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,

          participants: ['p2'],
          queuedActions: [
            {
              playerId: 'p2',
              card: createTestCard('9', 'p2n1'),
              position: 0,
            },
          ],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // All players mark ready
      let newState = markPlayersReady(state, ['p1', 'p2']);

      // Queued 9 action should start
      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.rank).toBe('9');

      // P2 peeks p1's card
      newState = unsafeReduce(
        newState,
        GameActions.selectActionTarget('p2', 'p1', 0)
      );

      // Confirm peek
      newState = unsafeReduce(newState, GameActions.confirmPeek('p2'));

      expect(newState.subPhase).toBe('ai_thinking'); // all already marked they are ready so auto-advance
      expect(newState.activeTossIn?.queuedActions.length).toBe(0);
    });
  });

  describe('Edge Cases', () => {
    it('should reject when not player turn', () => {
      const nineCard = createTestCard('9', 'nine1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0, // P1's turn
        pendingAction: {
          card: nineCard,
          playerId: 'p1',
          from: 'drawing',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      expect(() => unsafeReduce(
        state,
        GameActions.selectActionTarget('p2', 'p3', 0)
      )).toThrow();
    });
  });
});
