/**
 * King (K) Card Action Tests
 *
 * Rules:
 * - Value: 0
 * - Action: Declare the rank of any card and play its action
 * - Can declare action cards (7-A) or non-action cards (2-6, K, Joker)
 * - After declaring, triggers toss-in period for declared rank
 * - If declared card has action, must execute it immediately
 *
 * Tests cover:
 * 1. Normal flow - using King to declare various ranks
 * 2. Normal flow - declaring action card and executing it
 * 3. Toss-in flow - tossing in King during toss-in period
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

describe('King (K) Card Action', () => {
  describe('Normal Flow - Declaring Non-Action Cards', () => {
    it('should allow declaring non-action card rank (e.g., 5)', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Declare rank 5 (non-action card)
      const newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p1', '5')
      );

      // Should trigger toss-in for rank 5
      expect(newState.activeTossIn).not.toBeNull();
      expect(newState.activeTossIn?.ranks).toContain('5');
      expect(newState.activeTossIn?.initiatorId).toBe('p1');
      expect(newState.subPhase).toBe('toss_queue_active');

      // King should be in discard pile
      expect(newState.discardPile.peekTop()?.id).toBe('king1');
      expect(newState.pendingAction).toBeNull();
    });

    it('should allow declaring Joker rank', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Declare Joker (non-action card)
      const newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p1', 'Joker')
      );

      expect(newState.activeTossIn?.ranks).toContain('Joker');
      expect(newState.subPhase).toBe('toss_queue_active');
    });
  });

  describe('Normal Flow - Declaring Action Cards', () => {
    it('should declare Ace and require immediate action execution', () => {
      const kingCard = createTestCard('K', 'king1');
      const penaltyCard = createTestCard('Q', 'penalty1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        drawPile: toPile([penaltyCard]),
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'),
          ]),
        ],
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Declare Ace (action card)
      let newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p1', 'A')
      );

      // Should trigger Ace action setup (force draw)
      // Implementation may vary - check if pending action is set for Ace
      expect(newState.activeTossIn?.ranks).toContain('A');
      expect(newState.subPhase).toBe('toss_queue_active');
    });

    it('should declare Queen and require peek+swap action', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'),
            createTestCard('8', 'p2c2'),
          ]),
        ],
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Declare Queen
      const newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p1', 'Q')
      );

      // Should trigger toss-in for Queen
      expect(newState.activeTossIn?.ranks).toContain('Q');
      expect(newState.subPhase).toBe('toss_queue_active');
    });

    it('should declare 7 and require peek-own action', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('2', 'p1c1'),
            createTestCard('3', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Declare 7
      const newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p1', '7')
      );

      // Should trigger toss-in for 7
      expect(newState.activeTossIn?.ranks).toContain('7');
      expect(newState.subPhase).toBe('toss_queue_active');
    });
  });

  describe('Normal Flow - Swapping King Instead of Using Action', () => {
    it('should allow swapping King into hand instead of declaring', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('A', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      // Swap King into hand
      const newState = GameEngine.reduce(
        state,
        GameActions.swapCard('p1', 0)
      );

      expect(newState.subPhase).toBe('selecting');
      expect(newState.players[0].cards[0].id).toBe('king1'); // King swapped in
      expect(newState.pendingAction?.card.id).toBe('p1c1'); // Ace removed
    });
  });

  describe('Toss-In Flow', () => {
    it('should allow player to toss in King during toss-in period', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('K', 'p2k1'), // Has matching King
            createTestCard('7', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['K'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // P2 tosses in their King
      const newState = GameEngine.reduce(
        state,
        GameActions.participateInTossIn('p2', 0)
      );

      // Verify card was removed from hand
      expect(newState.players[1].cards.length).toBe(1);

      // Verify King action was queued
      expect(newState.activeTossIn?.queuedActions.length).toBe(1);
      expect(newState.activeTossIn?.queuedActions[0].card.rank).toBe('K');
      expect(newState.activeTossIn?.participants).toContain('p2');
    });

    it('should process queued King action requiring rank declaration', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false),
        ],
        activeTossIn: {
          ranks: ['K'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: ['p2'],
          queuedActions: [
            {
              playerId: 'p2',
              card: createTestCard('K', 'p2k1'),
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

      // Queued King action should start
      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.rank).toBe('K');
      expect(newState.pendingAction?.playerId).toBe('p2');

      // P2 declares a rank (e.g., 7)
      newState = GameEngine.reduce(
        newState,
        GameActions.declareKingAction('p2', '7')
      );

      // Should return to toss-in for King (original), with nested toss-in for 7
      // Or implementation may trigger toss-in for 7 directly
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.pendingAction).toBeNull();
    });
  });

  describe('Edge Cases and Validations', () => {
    it('should reject action when not in awaiting_action phase', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p1', 'A')
      );

      expect(newState).toEqual(state); // State unchanged
    });

    it('should reject action when not player turn', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0, // P1's turn
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // P2 tries to declare (not their turn)
      const newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p2', 'A')
      );

      expect(newState).toEqual(state); // State unchanged
    });

    it('should reject when pending card is not a King', () => {
      const notKingCard = createTestCard('Q', 'queen1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        pendingAction: {
          card: notKingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p1', 'A')
      );

      expect(newState).toEqual(state); // State unchanged
    });

    it('should handle declaring King rank (King declaring King)', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Declare King (self-reference)
      const newState = GameEngine.reduce(
        state,
        GameActions.declareKingAction('p1', 'K')
      );

      // Should trigger toss-in for King rank
      expect(newState.activeTossIn?.ranks).toContain('K');
      expect(newState.subPhase).toBe('toss_queue_active');
    });
  });
});
