/**
 * 7 Card Action Tests
 *
 * Rules:
 * - Value: 7
 * - Action: Peek one of your own cards
 * - After action completes, triggers toss-in period for 7 rank
 *
 * Tests cover:
 * 1. Normal flow - peeking own card
 * 2. Toss-in flow - tossing in 7 during toss-in period
 * 3. Edge cases and validations
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

describe('7 Card Action', () => {
  describe('Normal Flow - Peek Own Card', () => {
    it('should allow peeking own card', () => {
      const sevenCard = createTestCard('7', 'seven1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: sevenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Select own card to peek
      let newState = GameEngine.reduce(
        state,
        GameActions.selectActionTarget('p1', 'p1', 1)
      );

      // Confirm peek
      newState = GameEngine.reduce(newState, GameActions.confirmPeek('p1'));

      // Should trigger toss-in
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.ranks).toContain('7');
      expect(newState.discardPile.peekTop()?.id).toBe('seven1');
    });

    it('should allow swapping 7 into hand instead of using action', () => {
      const sevenCard = createTestCard('7', 'seven1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: sevenCard,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.swapCard('p1', 0)
      );

      expect(newState.subPhase).toBe('selecting');
      expect(newState.players[0].cards[0].id).toBe('seven1');
    });
  });

  describe('Toss-In Flow', () => {
    it('should allow player to toss in 7 during toss-in period', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2s1'), // Has matching 7
            createTestCard('8', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['7'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.participateInTossIn('p2', 0)
      );

      expect(newState.players[1].cards.length).toBe(1);
      expect(newState.activeTossIn?.queuedActions.length).toBe(1);
      expect(newState.activeTossIn?.queuedActions[0].card.rank).toBe('7');
    });

    it('should process queued 7 action requiring peek target', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('A', 'p2c1'),
            createTestCard('K', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['7'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: ['p2'],
          queuedActions: [
            {
              playerId: 'p2',
              card: createTestCard('7', 'p2s1'),
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

      // Queued 7 action should start
      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.rank).toBe('7');

      // P2 peeks own card
      newState = GameEngine.reduce(
        newState,
        GameActions.selectActionTarget('p2', 'p2', 0)
      );

      // Confirm peek
      newState = GameEngine.reduce(newState, GameActions.confirmPeek('p2'));

      // Should return to toss-in
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.ranks).toContain('7');
    });
  });

  describe('Edge Cases', () => {
    it('should reject when not player turn', () => {
      const sevenCard = createTestCard('7', 'seven1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0, // P1's turn
        pendingAction: {
          card: sevenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.selectActionTarget('p2', 'p2', 0)
      );

      expect(newState).toEqual(state);
    });
  });
});
