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
  markPlayersReady,
  toPile,
} from '../test-helpers';
import { mockLogger } from '../setup-tests';

describe('King (K) Card Action', () => {
  beforeEach(() => {
    // Reset all mock calls before each test
    mockLogger.log.mockClear();
    mockLogger.warn.mockClear();
    mockLogger.error.mockClear();
  });
  describe('Normal Flow - Declaring Non-Action Cards', () => {
    it('should allow declaring non-action card rank (e.g., 5)', () => {
      const kingCard = createTestCard('K', 'king1');

      let state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('5', 'p1c1'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'),
            createTestCard('8', 'p2c2'),
          ]),
        ],
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            {
              playerId: 'p1',
              position: 0,
              card: createTestCard('5', 'p1c1'),
            },
          ],
        },
      });

      let newState = GameEngine.reduce(
        state,
        GameActions.playCardAction('p1', kingCard)
      );
      expect(newState.subPhase).toBe('awaiting_action');

      // Declare rank 5 (non-action card)
      newState = GameEngine.reduce(
        newState,
        GameActions.declareKingAction('p1', '5')
      );

      // Should trigger toss-in for rank 5
      expect(newState.activeTossIn?.ranks).toContain('5');
      expect(newState.activeTossIn?.initiatorId).toBe('p1');
      expect(newState.subPhase).toBe('toss_queue_active');

      // King should be in discard pile
      expect(newState.discardPile.at(-1)?.id).toBe(kingCard.id);
      expect(newState.discardPile.peekTop()?.id).toBe('p1c1');
      expect(newState.pendingAction).toBeNull();
    });

    it('should allow declaring Joker rank', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('Joker', 'p1c1'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'),
            createTestCard('8', 'p2c2'),
          ]),
        ],
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            {
              playerId: 'p1',
              position: 0,
              card: createTestCard('Joker', 'p1c1'),
            },
          ],
        },
      });

      // first create tossin
      let newState = GameEngine.reduce(
        state,
        GameActions.playCardAction('p1', kingCard)
      );

      expect(newState.subPhase).toBe('awaiting_action');

      // Declare Joker (non-action card)
      newState = GameEngine.reduce(
        newState,
        GameActions.declareKingAction('p1', 'Joker')
      );

      expect(newState.activeTossIn).not.toBeNull();
      expect(newState.activeTossIn?.ranks).toContain('Joker');
      expect(newState.subPhase).toBe('toss_queue_active');
    });
  });

  describe('Normal Flow - Declaring Action Cards', () => {
    it('should correctly declare 7 and require peek-own action', () => {
      const kingCard = createTestCard('K', 'king1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('7', 'p1c1'),
            createTestCard('2', 'p1c2'),
            createTestCard('3', 'p1c3'),
          ]),
        ],
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            {
              playerId: 'p1',
              position: 0,
              card: createTestCard('7', 'p1c1'),
            },
          ],
        },
      });

      // Step 1: Player chooses to use Ace action
      let newState = GameEngine.reduce(
        state,
        GameActions.playCardAction('p1', kingCard)
      );
      expect(newState.subPhase).toBe('awaiting_action');

      // Declare 7
      newState = GameEngine.reduce(
        newState,
        GameActions.declareKingAction('p1', '7')
      );

      // Should trigger toss-in for 7
      expect(newState.activeTossIn?.ranks.length).toBe(2);
      expect(newState.activeTossIn?.ranks).toContain('7');
      expect(newState.activeTossIn?.ranks).toContain('K');
      expect(newState.subPhase).toBe('awaiting_action');
    });

    it('should incorrectly declare 7 and get penalty', () => {
      const kingCard = createTestCard('K', 'king1');

      const penaltyCard = createTestCard('Q', 'penalty1');

      const state = createTestState({
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('2', 'p1c1'),
            createTestCard('3', 'p1c2'),
          ]),
        ],
        drawPile: toPile([penaltyCard]),
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            {
              playerId: 'p1',
              position: 0,
              card: createTestCard('7', 'p1c1'),
            },
          ],
        },
      });

      // Step 1: Player chooses to use Ace action
      let newState = GameEngine.reduce(
        state,
        GameActions.playCardAction('p1', kingCard)
      );
      expect(newState.subPhase).toBe('awaiting_action');

      // Declare 7
      newState = GameEngine.reduce(
        newState,
        GameActions.declareKingAction('p1', '7')
      );

      expect(newState.activeTossIn?.ranks.length).toBe(1);
      expect(newState.activeTossIn?.ranks).toContain('K');
      expect(newState.players.find((p) => p.id === 'p1')?.cards.length).toBe(3); // Penalty card added
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
      const newState = GameEngine.reduce(state, GameActions.swapCard('p1', 0));

      expect(newState.subPhase).toBe('toss_queue_active');
      expect(newState.players[0].cards[0].id).toBe('king1'); // King swapped in
      expect(newState.pendingAction).toBeNull(); // Ace removed
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
      let newState = markPlayersReady(state, ['p1', 'p2', 'p3', 'p4']);

      // Queued King action should start
      expect(newState.subPhase).toBe('awaiting_action');
      expect(newState.pendingAction?.card.rank).toBe('K');
      expect(newState.pendingAction?.playerId).toBe('p2');

      newState = GameEngine.reduce(
        newState,
        GameActions.playCardAction('p1', createTestCard('K', 'p2k1'))
      );
      expect(newState.subPhase).toBe('awaiting_action');

      // P2 declares a rank (e.g., 7)
      newState = GameEngine.reduce(
        newState,
        GameActions.declareKingAction('p2', '7')
      );

      expect(newState.subPhase).toBe('awaiting_action');
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
        subPhase: 'choosing',
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('K', 'p1c1'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('7', 'p2c1'),
            createTestCard('8', 'p2c2'),
          ]),
        ],
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            {
              playerId: 'p1',
              position: 0,
              card: createTestCard('K', 'p1c1'),
            },
          ],
        },
      });

      let newState = GameEngine.reduce(
        state,
        GameActions.playCardAction('p1', kingCard)
      );
      expect(newState.subPhase).toBe('awaiting_action');

      // Declare King (self-reference)
      newState = GameEngine.reduce(
        newState,
        GameActions.declareKingAction('p1', 'K')
      );

      // Should trigger toss-in for King rank
      expect(newState.activeTossIn).toBeDefined();
      expect(newState.activeTossIn?.ranks).toContain('K');
      expect(newState.activeTossIn?.ranks.length).toBe(1);
      expect(newState.subPhase).toBe('awaiting_action');
    });
  });
});
