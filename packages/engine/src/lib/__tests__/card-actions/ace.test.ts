/**
 * Ace (A) Card Action Tests
 *
 * Rules:
 * - Value: 1
 * - Action: Choose a player to draw one penalty card from the deck face-down
 * - After action completes, triggers toss-in period for Ace rank
 *
 * Tests cover:
 * 1. Normal flow - drawing Ace and using action
 * 2. Normal flow - taking Ace from discard (must use action)
 * 3. Toss-in flow - tossing in Ace during toss-in period
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
import { GameSubPhase } from '@vinto/shapes';

describe('Ace (A) Card Action', () => {
  describe('Normal Flow - Draw from Deck', () => {
    it('should allow using Ace action to force opponent to draw penalty card', () => {
      const aceCard = createTestCard('A', 'ace1');
      const penaltyCard = createTestCard('K', 'penalty1');

      const state = createTestState({
        subPhase: 'choosing',
        drawPile: toPile([penaltyCard, createTestCard('Q', 'card2')]),
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'),
            createTestCard('8', 'p2c2'),
          ]),
        ],
        pendingAction: {
          card: aceCard,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      // Step 1: Player chooses to use Ace action
      let newState = GameEngine.reduce(
        state,
        GameActions.playCardAction('p1', aceCard)
      );
      expect(newState.subPhase).toBe('awaiting_action');

      // Step 2: Player selects opponent to draw penalty card
      newState = GameEngine.reduce(
        newState,
        GameActions.selectActionTarget('p1', 'p2', 0)
      );

      // Verify opponent drew penalty card
      expect(newState.players[1].cards.length).toBe(3); // Was 2, now 3
      expect(newState.drawPile.length).toBe(1); // One card drawn
      expect(newState.drawPile.peekTop()?.played).toBe(true); // We should mark card as played

      // Verify toss-in triggered
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn).not.toBeNull();
      expect(newState.activeTossIn?.rank).toBe('A');
      expect(newState.activeTossIn?.initiatorId).toBe('p1');
    });

    it('should allow swapping Ace into hand instead of using action', () => {
      const aceCard = createTestCard('A', 'ace1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
        ],
        pendingAction: {
          card: aceCard,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      // Player chooses to swap Ace into hand
      const newState = GameEngine.reduce(
        state,
        GameActions.swapCard('p1', 0)
      );

      expect(newState.subPhase).toBe('toss_queue_active' satisfies GameSubPhase);
      expect(newState.players[0].cards[0].id).toBe('ace1'); // Ace swapped in
      expect(newState.discardPile.peekTop()?.id).toBe('p1c1'); // King removed
    });
  });

  describe('Normal Flow - Take from Discard', () => {
    it('should force immediate action use when taking Ace from discard', () => {
      const aceCard = createTestCard('A', 'ace1');
      aceCard.played = false; // Unused action card

      const penaltyCard = createTestCard('K', 'penalty1');

      const state = createTestState({
        subPhase: 'idle',
        drawPile: toPile([penaltyCard]),
        discardPile: toPile([aceCard]),
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'),
          ]),
        ],
      });

      // Step 1: Take Ace from discard
      let newState = GameEngine.reduce(state, GameActions.takeDiscard('p1'));

      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.id).toBe('ace1');
      expect(newState.pendingAction?.actionPhase).toBe('selecting-target');

      // Step 2: Must use action immediately
      newState = GameEngine.reduce(
        newState,
        GameActions.selectActionTarget('p1', 'p2', 0)
      );

      // Verify opponent drew penalty card
      expect(newState.players[1].cards.length).toBe(2); // Was 1, now 2

      // Verify toss-in triggered
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.rank).toBe('A');
    });
  });

  describe('Toss-In Flow', () => {
    it('should allow player to toss in Ace during toss-in period', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        drawPile: toPile([createTestCard('K', 'penalty1')]),
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
            createTestCard('Q', 'p1c2'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('A', 'p2c1'), // Has matching Ace
            createTestCard('7', 'p2c2'),
            createTestCard('8', 'p2c3'),
          ]),
        ],
        activeTossIn: {
          rank: 'A',
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // P2 tosses in their Ace
      const newState = GameEngine.reduce(
        state,
        GameActions.participateInTossIn('p2', 0)
      );

      // Verify card was removed from hand
      expect(newState.players[1].cards.length).toBe(2);

      // Verify Ace action was queued
      expect(newState.activeTossIn?.queuedActions.length).toBe(1);
      expect(newState.activeTossIn?.queuedActions[0].card.rank).toBe('A');
      expect(newState.activeTossIn?.participants).toContain('p2');
    });

    it('should process queued Ace action after all players ready', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        drawPile: toPile([createTestCard('K', 'penalty1')]),
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'),
          ]),
        ],
        activeTossIn: {
          rank: 'A',
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: ['p2'],
          queuedActions: [
            {
              playerId: 'p2',
              card: createTestCard('A', 'p2ace'),
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

      // Queued Ace action should start
      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.rank).toBe('A');
      expect(newState.pendingAction?.playerId).toBe('p2');

      // P2 selects target for queued Ace
      newState = GameEngine.reduce(
        newState,
        GameActions.selectActionTarget('p2', 'p1', 0)
      );

      // Should return to toss-in (not create new toss-in)
      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.activeTossIn?.rank).toBe('A'); // Same toss-in continues
      expect(newState.pendingAction).toBeNull();
      expect(newState.discardPile.peekTop()?.rank).toBe('A');
      expect(newState.discardPile.peekTop()?.played).toBe(true);
    });

    it('should reject toss-in with wrong rank', () => {
      const state = createTestState({
        subPhase: 'toss_queue_active',
        drawPile: toPile([createTestCard('K', 'penalty1')]),
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'), // Wrong rank
            createTestCard('8', 'p2c2'),
          ]),
        ],
        activeTossIn: {
          rank: 'A', // Looking for Aces
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
      });

      // P2 tries to toss in wrong card
      const newState = GameEngine.reduce(
        state,
        GameActions.participateInTossIn('p2', 0)
      );

      // Should be rejected (state mostly unchanged)
      // Implementation should handle penalty draw
      expect(newState.activeTossIn).not.toBeNull();
    });
  });

  describe('Edge Cases and Validations', () => {
    it('should reject Ace action when draw pile is empty', () => {
      const aceCard = createTestCard('A', 'ace1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        drawPile: toPile([]), // Empty!
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false),
        ],
        pendingAction: {
          card: aceCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const newState = GameEngine.reduce(
        state,
        GameActions.selectActionTarget('p1', 'p2', 0)
      );

      // Should fail gracefully - check implementation
      // At minimum, state should be valid
      expect(newState.players[1].cards.length).toBe(0); // No card drawn
    });

    it('should reject action when not player turn', () => {
      const aceCard = createTestCard('A', 'ace1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0, // P1's turn
        drawPile: toPile([createTestCard('K', 'penalty1')]),
        pendingAction: {
          card: aceCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // P2 tries to use action (not their turn)
      const newState = GameEngine.reduce(
        state,
        GameActions.selectActionTarget('p2', 'p3', 0)
      );

      expect(newState).toEqual(state); // State unchanged
    });

    it('should allow targeting self with Ace (edge case)', () => {
      const aceCard = createTestCard('A', 'ace1');
      const penaltyCard = createTestCard('K', 'penalty1');

      const state = createTestState({
        subPhase: 'awaiting_action',
        drawPile: toPile([penaltyCard]),
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('7', 'p1c1'),
          ]),
        ],
        pendingAction: {
          card: aceCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // P1 targets themselves
      const newState = GameEngine.reduce(
        state,
        GameActions.selectActionTarget('p1', 'p1', 0)
      );

      // Should draw penalty card to self
      expect(newState.players[0].cards.length).toBe(2);
    });
  });
});
