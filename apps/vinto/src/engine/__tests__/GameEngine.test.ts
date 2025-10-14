import { GameEngine } from '../game-engine';
import { GameActions } from '../game-actions';
import { Card, GameState, Pile, PlayerState } from '@/shared';

/**
 * Test helpers
 */
function createTestCard(rank: Card['rank'], id: string): Card {
  return {
    id,
    rank,
    value: rank === 'A' ? 1 : rank === 'K' ? 0 : 10,
    played: false,
  };
}

function createTestPlayer(
  id: string,
  name: string,
  isHuman: boolean
): PlayerState {
  return {
    id,
    name,
    isHuman,
    isBot: !isHuman,
    cards: [],
    knownCardPositions: [],
    isVintoCaller: false,
    coalitionWith: [],
  };
}

const toPile = (cards: Card[] | Pile = []): Pile => Pile.fromCards(cards);

function createTestState(overrides?: Partial<GameState>): GameState {
  const card1 = createTestCard('A', 'card1');
  const card2 = createTestCard('K', 'card2');
  const card3 = createTestCard('Q', 'card3');

  const baseState: GameState = {
    gameId: 'test-game',
    roundNumber: 1,
    turnCount: 0,
    phase: 'playing',
    subPhase: 'idle',
    finalTurnTriggered: false,
    players: [
      createTestPlayer('p1', 'Player 1', true),
      createTestPlayer('p2', 'Player 2', false),
    ],
    currentPlayerIndex: 0,
    vintoCallerId: null,
    coalitionLeaderId: null,
    drawPile: toPile([card1, card2, card3]),
    discardPile: toPile(),
    pendingAction: null,
    activeTossIn: null,
    recentActions: [],
    difficulty: 'moderate',
  };

  const mergedState = {
    ...baseState,
    ...overrides,
  } as GameState;

  return {
    ...mergedState,
    drawPile: toPile(mergedState.drawPile),
    discardPile: toPile(mergedState.discardPile),
  };
}

describe('GameEngine', () => {
  describe('DRAW_CARD action', () => {
    it('should draw card from draw pile', () => {
      const initialState = createTestState();
      const action = GameActions.drawCard('p1');

      const newState = GameEngine.reduce(initialState, action);

      // Draw pile should have one less card
      expect(newState.drawPile).toHaveLength(2);
      expect(newState.drawPile.peekTop()?.id).toBe('card2'); // card1 was drawn

      // Original state should be unchanged (immutability)
      expect(initialState.drawPile).toHaveLength(3);
    });

    it('should create pending action with drawn card', () => {
      const initialState = createTestState();
      const action = GameActions.drawCard('p1');

      const newState = GameEngine.reduce(initialState, action);

      expect(newState.pendingAction).not.toBeNull();
      expect(newState.pendingAction?.card.id).toBe('card1');
      expect(newState.pendingAction?.playerId).toBe('p1');
      expect(newState.pendingAction?.actionPhase).toBe('choosing-action');
      expect(newState.pendingAction?.targets).toEqual([]);
    });

    it('should transition from idle to choosing phase', () => {
      const initialState = createTestState({
        subPhase: 'idle',
      });
      const action = GameActions.drawCard('p1');

      const newState = GameEngine.reduce(initialState, action);

      expect(newState.subPhase).toBe('choosing');
    });

    it('should handle bot drawing (from ai_thinking phase)', () => {
      const initialState = createTestState({
        subPhase: 'ai_thinking',
        currentPlayerIndex: 1, // Bot's turn
      });
      const action = GameActions.drawCard('p2');

      const newState = GameEngine.reduce(initialState, action);

      // Should transition to choosing, drawn card should be pending
      expect(newState.subPhase).toBe('choosing');
      expect(newState.pendingAction?.card.id).toBe('card1');
    });

    it('should reject draw when not player turn', () => {
      const initialState = createTestState({
        currentPlayerIndex: 0, // p1's turn
      });
      const action = GameActions.drawCard('p2'); // p2 trying to draw

      const newState = GameEngine.reduce(initialState, action);

      // State should be unchanged
      expect(newState).toEqual(initialState);
      expect(newState.drawPile).toHaveLength(3);
      expect(newState.pendingAction).toBeNull();
    });

    it('should reject draw when draw pile is empty', () => {
      const initialState = createTestState({
        drawPile: toPile(), // Empty draw pile
      });
      const action = GameActions.drawCard('p1');

      const newState = GameEngine.reduce(initialState, action);

      // State should be unchanged
      expect(newState).toEqual(initialState);
      expect(newState.pendingAction).toBeNull();
    });

    it('should reject draw when in wrong phase', () => {
      const initialState = createTestState({
        subPhase: 'selecting', // Wrong phase
      });
      const action = GameActions.drawCard('p1');

      const newState = GameEngine.reduce(initialState, action);

      // State should be unchanged
      expect(newState).toEqual(initialState);
      expect(newState.subPhase).toBe('selecting');
      expect(newState.pendingAction).toBeNull();
    });

    it('should not mutate original state', () => {
      const initialState = createTestState();
      const stateCopy = JSON.parse(JSON.stringify(initialState));
      const action = GameActions.drawCard('p1');

      GameEngine.reduce(initialState, action);

      // Original state should be completely unchanged
      expect(initialState).toEqual(stateCopy);
    });

    it('should handle multiple consecutive draws', () => {
      let state = createTestState();

      // First draw
      state = GameEngine.reduce(state, GameActions.drawCard('p1'));
      expect(state.drawPile).toHaveLength(2);
      expect(state.pendingAction?.card.id).toBe('card1');

      // Reset to idle for second draw (simulating turn completion)
      state = {
        ...state,
        subPhase: 'idle',
        currentPlayerIndex: 1,
        pendingAction: null,
      };

      // Second draw
      state = GameEngine.reduce(state, GameActions.drawCard('p2'));
      expect(state.drawPile).toHaveLength(1);
      expect(state.pendingAction?.card.id).toBe('card2');

      // Reset for third draw
      state = {
        ...state,
        subPhase: 'idle',
        currentPlayerIndex: 0,
        pendingAction: null,
      };

      // Third draw
      state = GameEngine.reduce(state, GameActions.drawCard('p1'));
      expect(state.drawPile).toHaveLength(0);
      expect(state.pendingAction?.card.id).toBe('card3');
    });
  });

  describe('Action validation', () => {
    it('should validate action creator helpers', () => {
      const action = GameActions.drawCard('p1');

      expect(action.type).toBe('DRAW_CARD');
      expect(action.payload.playerId).toBe('p1');
    });
  });

  describe('State immutability', () => {
    it('should never modify nested objects', () => {
      const initialState = createTestState();
      const originalDrawPile = initialState.drawPile;
      const originalPlayers = initialState.players;

      const action = GameActions.drawCard('p1');
      GameEngine.reduce(initialState, action);

      // References should be unchanged
      expect(initialState.drawPile).toBe(originalDrawPile);
      expect(initialState.players).toBe(originalPlayers);
    });
  });

  describe('SWAP_CARD action', () => {
    it('should swap pending card with card at position', () => {
      // Setup: Player has drawn a card
      let state = createTestState({
        subPhase: 'choosing',
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [
              createTestCard('K', 'hand1'),
              createTestCard('Q', 'hand2'),
              createTestCard('J', 'hand3'),
              createTestCard('10', 'hand4'),
            ],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      const drawnCard = createTestCard('A', 'drawn1');
      state.pendingAction = {
        card: drawnCard,
        playerId: 'p1',
        actionPhase: 'choosing-action',
        targets: [],
      };

      // Swap drawn card with position 1 (Queen)
      const action = GameActions.swapCard('p1', 1);
      const newState = GameEngine.reduce(state, action);

      // Card at position 1 should now be the drawn Ace
      expect(newState.players[0].cards[1].rank).toBe('A');
      expect(newState.players[0].cards[1].id).toBe('drawn1');

      // Pending action should contain the removed card (Queen)
      expect(newState.pendingAction).not.toBeNull();
      expect(newState.pendingAction?.card.rank).toBe('Q');
      expect(newState.pendingAction?.card.id).toBe('hand2');

      // Should transition to selecting phase
      expect(newState.subPhase).toBe('selecting');
    });

    it('should track declared rank when provided', () => {
      let state = createTestState({
        subPhase: 'choosing',
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [
              createTestCard('K', 'hand1'),
              createTestCard('Q', 'hand2'),
              createTestCard('J', 'hand3'),
              createTestCard('10', 'hand4'),
            ],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      state.pendingAction = {
        card: createTestCard('A', 'drawn1'),
        playerId: 'p1',
        actionPhase: 'choosing-action',
        targets: [],
      };

      // Swap and declare as King (bluff)
      const action = GameActions.swapCard('p1', 0, 'K');
      const newState = GameEngine.reduce(state, action);

      // Should have known card position
      expect(newState.players[0].knownCardPositions).toHaveLength(1);
      expect(newState.players[0].knownCardPositions[0]).toBe(0);
    });

    it('should update existing known position when swapping same position', () => {
      let state = createTestState({
        subPhase: 'choosing',
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [
              createTestCard('K', 'hand1'),
              createTestCard('Q', 'hand2'),
              createTestCard('J', 'hand3'),
              createTestCard('10', 'hand4'),
            ],
            knownCardPositions: [1],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      state.pendingAction = {
        card: createTestCard('7', 'drawn1'),
        playerId: 'p1',
        actionPhase: 'choosing-action',
        targets: [],
      };

      // Swap position 1 and declare as 7
      const action = GameActions.swapCard('p1', 1, '7');
      const newState = GameEngine.reduce(state, action);

      // Should still have only 1 known position, but updated
      expect(newState.players[0].knownCardPositions).toHaveLength(1);
    });

    it('should reject swap when not in choosing phase', () => {
      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [createTestCard('K', 'hand1')],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      const action = GameActions.swapCard('p1', 0);
      const newState = GameEngine.reduce(state, action);

      // State should be unchanged
      expect(newState).toEqual(state);
    });

    it('should reject swap when no pending action', () => {
      const state = createTestState({
        subPhase: 'choosing',
        pendingAction: null, // No pending action
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [createTestCard('K', 'hand1')],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      const action = GameActions.swapCard('p1', 0);
      const newState = GameEngine.reduce(state, action);

      // State should be unchanged
      expect(newState).toEqual(state);
    });

    it('should reject swap with invalid position', () => {
      let state = createTestState({
        subPhase: 'choosing',
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [createTestCard('K', 'hand1'), createTestCard('Q', 'hand2')],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      state.pendingAction = {
        card: createTestCard('A', 'drawn1'),
        playerId: 'p1',
        actionPhase: 'choosing-action',
        targets: [],
      };

      // Try to swap position 5 (invalid - only has positions 0-1)
      const action = GameActions.swapCard('p1', 5);
      const newState = GameEngine.reduce(state, action);

      // State should be unchanged
      expect(newState).toEqual(state);
    });

    it('should reject swap when not player turn', () => {
      let state = createTestState({
        subPhase: 'choosing',
        currentPlayerIndex: 0, // p1's turn
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [createTestCard('K', 'hand1')],
          },
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('Q', 'hand2')],
          },
        ],
      });

      state.pendingAction = {
        card: createTestCard('A', 'drawn1'),
        playerId: 'p1', // Pending for p1
        actionPhase: 'choosing-action',
        targets: [],
      };

      // p2 trying to swap (not their turn)
      const action = GameActions.swapCard('p2', 0);
      const newState = GameEngine.reduce(state, action);

      // State should be unchanged
      expect(newState).toEqual(state);
    });

    it('should not mutate original state', () => {
      let state = createTestState({
        subPhase: 'choosing',
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [createTestCard('K', 'hand1')],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      state.pendingAction = {
        card: createTestCard('A', 'drawn1'),
        playerId: 'p1',
        actionPhase: 'choosing-action',
        targets: [],
      };

      const stateCopy = JSON.parse(JSON.stringify(state));
      const action = GameActions.swapCard('p1', 0);

      GameEngine.reduce(state, action);

      // Original state should be unchanged
      expect(state).toEqual(stateCopy);
    });
  });

  describe('DISCARD_CARD action', () => {
    it('should discard pending card and complete turn', () => {
      // Setup: Player has swapped a card and is holding removed card
      const removedCard = createTestCard('K', 'removed1');
      let state = createTestState({
        subPhase: 'selecting',
        turnCount: 5,
        discardPile: toPile([createTestCard('Q', 'disc1')]),
        pendingAction: {
          card: removedCard,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const action = GameActions.discardCard('p1');
      const newState = GameEngine.reduce(state, action);

      // Card should be added to discard pile
      expect(newState.discardPile).toHaveLength(2);
      expect(newState.discardPile.peekAt(1)?.rank).toBe('K');
      expect(newState.discardPile.peekAt(1)?.id).toBe('removed1');

      // Pending action should be cleared
      expect(newState.pendingAction).toBeNull();

      // Should transition to idle
      expect(newState.subPhase).toBe('idle');

      // Turn count should increment
      expect(newState.turnCount).toBe(6);
    });

    it('should reject discard when not in selecting phase', () => {
      const state = createTestState({
        subPhase: 'choosing', // Wrong phase
        pendingAction: {
          card: createTestCard('K', 'card1'),
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const action = GameActions.discardCard('p1');
      const newState = GameEngine.reduce(state, action);

      // State should be unchanged
      expect(newState).toEqual(state);
    });

    it('should reject discard when not player turn', () => {
      const state = createTestState({
        subPhase: 'selecting',
        currentPlayerIndex: 0, // p1's turn
        pendingAction: {
          card: createTestCard('K', 'card1'),
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      // p2 trying to discard (not their turn)
      const action = GameActions.discardCard('p2');
      const newState = GameEngine.reduce(state, action);

      // State should be unchanged
      expect(newState).toEqual(state);
    });

    it('should handle discard even without pending action', () => {
      // Edge case: pendingAction is null (shouldn't happen in normal flow)
      let state = createTestState({
        subPhase: 'selecting',
        turnCount: 3,
        pendingAction: null,
      });

      const action = GameActions.discardCard('p1');
      const newState = GameEngine.reduce(state, action);

      // Should still transition and increment turn
      expect(newState.subPhase).toBe('idle');
      expect(newState.turnCount).toBe(4);
      expect(newState.discardPile).toHaveLength(0); // No card added
    });

    it('should not mutate original state', () => {
      let state = createTestState({
        subPhase: 'selecting',
        turnCount: 5,
        pendingAction: {
          card: createTestCard('K', 'card1'),
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const stateCopy = JSON.parse(JSON.stringify(state));
      const action = GameActions.discardCard('p1');

      GameEngine.reduce(state, action);

      // Original state should be unchanged
      expect(state).toEqual(stateCopy);
    });
  });

  describe('ADVANCE_TURN action', () => {
    it('should advance to next player', () => {
      const state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', true),
          createTestPlayer('p3', 'Player 3', true),
        ],
      });

      const action = GameActions.advanceTurn();
      const newState = GameEngine.reduce(state, action);

      // Should move to player 2 (index 1)
      expect(newState.currentPlayerIndex).toBe(1);

      // Should stay in idle (next player is human)
      expect(newState.subPhase).toBe('idle');
    });

    it('should wrap around to first player', () => {
      const state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 2, // Last player
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', false),
          createTestPlayer('p3', 'Player 3', false),
        ],
      });

      const action = GameActions.advanceTurn();
      const newState = GameEngine.reduce(state, action);

      // Should wrap to player 1 (index 0)
      expect(newState.currentPlayerIndex).toBe(0);
      expect(newState.subPhase).toBe('idle');
    });

    it('should transition to ai_thinking when next player is bot', () => {
      const state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Bot 1', false), // Bot
          createTestPlayer('p3', 'Player 2', true),
        ],
      });

      const action = GameActions.advanceTurn();
      const newState = GameEngine.reduce(state, action);

      // Should move to bot (index 1)
      expect(newState.currentPlayerIndex).toBe(1);

      // Should transition to ai_thinking
      expect(newState.subPhase).toBe('ai_thinking');
    });

    it('should handle all-bot game', () => {
      const state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer('b1', 'Bot 1', false),
          createTestPlayer('b2', 'Bot 2', false),
          createTestPlayer('b3', 'Bot 3', false),
        ],
      });

      const action = GameActions.advanceTurn();
      const newState = GameEngine.reduce(state, action);

      expect(newState.currentPlayerIndex).toBe(1);
      expect(newState.subPhase).toBe('ai_thinking');
    });

    it('should reject advance when not in idle phase', () => {
      const state = createTestState({
        subPhase: 'choosing', // Wrong phase
        currentPlayerIndex: 0,
      });

      const action = GameActions.advanceTurn();
      const newState = GameEngine.reduce(state, action);

      // State should be unchanged
      expect(newState).toEqual(state);
      expect(newState.currentPlayerIndex).toBe(0);
    });

    it('should work in 2-player game', () => {
      const state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          createTestPlayer('p2', 'Player 2', true),
        ],
      });

      const action = GameActions.advanceTurn();
      const newState = GameEngine.reduce(state, action);

      expect(newState.currentPlayerIndex).toBe(1);

      // Advance again, should wrap to player 1
      const newState2 = GameEngine.reduce(newState, action);
      expect(newState2.currentPlayerIndex).toBe(0);
    });

    it('should not mutate original state', () => {
      const state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
      });

      const stateCopy = JSON.parse(JSON.stringify(state));
      const action = GameActions.advanceTurn();

      GameEngine.reduce(state, action);

      // Original state should be unchanged
      expect(state).toEqual(stateCopy);
    });
  });

  describe('PLAY_DISCARD action', () => {
    it('should take card from discard pile', () => {
      const discardCard = createTestCard('A', 'disc1');
      const initialState = createTestState({
        discardPile: toPile([createTestCard('K', 'disc0'), discardCard]),
      });
      const action = GameActions.takeDiscard('p1');

      const newState = GameEngine.reduce(initialState, action);

      // Discard pile should have one less card
      expect(newState.discardPile).toHaveLength(1);
      expect(newState.discardPile.peekTop()?.id).toBe('disc0'); // First card remains

      // Taken card should be in pending action
      expect(newState.pendingAction).not.toBeNull();
      expect(newState.pendingAction?.card.rank).toBe('A');
      expect(newState.pendingAction?.card.id).toBe('disc1');

      // Original state should be unchanged (immutability)
      expect(initialState.discardPile).toHaveLength(2);
    });

    it('should create pending action with taken card', () => {
      const initialState = createTestState({
        discardPile: toPile([createTestCard('Q', 'disc1')]),
      });
      const action = GameActions.takeDiscard('p1');

      const newState = GameEngine.reduce(initialState, action);

      expect(newState.pendingAction).not.toBeNull();
      expect(newState.pendingAction?.card.id).toBe('disc1');
      expect(newState.pendingAction?.playerId).toBe('p1');
      expect(newState.pendingAction?.actionPhase).toBe('choosing-action');
      expect(newState.pendingAction?.targets).toEqual([]);
    });

    it('should transition from idle to choosing phase', () => {
      const initialState = createTestState({
        subPhase: 'idle',
        discardPile: toPile([createTestCard('K', 'disc1')]),
      });
      const action = GameActions.takeDiscard('p1');

      const newState = GameEngine.reduce(initialState, action);

      expect(newState.subPhase).toBe('choosing');
    });

    it('should handle bot taking discard (from ai_thinking phase)', () => {
      const initialState = createTestState({
        subPhase: 'ai_thinking',
        currentPlayerIndex: 1, // Bot's turn
        discardPile: toPile([createTestCard('J', 'disc1')]),
      });
      const action = GameActions.takeDiscard('p2');

      const newState = GameEngine.reduce(initialState, action);

      // Should transition to choosing
      expect(newState.subPhase).toBe('choosing');
      expect(newState.pendingAction?.card.id).toBe('disc1');
    });

    it('should reject take when not player turn', () => {
      const initialState = createTestState({
        currentPlayerIndex: 0, // p1's turn
        discardPile: toPile([createTestCard('K', 'disc1')]),
      });
      const action = GameActions.takeDiscard('p2'); // p2 trying to take

      const newState = GameEngine.reduce(initialState, action);

      // State should be unchanged
      expect(newState).toEqual(initialState);
      expect(newState.discardPile).toHaveLength(1);
      expect(newState.pendingAction).toBeNull();
    });

    it('should reject take when discard pile is empty', () => {
      const initialState = createTestState({
        discardPile: toPile(), // Empty discard pile
      });
      const action = GameActions.takeDiscard('p1');

      const newState = GameEngine.reduce(initialState, action);

      // State should be unchanged
      expect(newState).toEqual(initialState);
      expect(newState.pendingAction).toBeNull();
    });

    it('should reject take when in wrong phase', () => {
      const initialState = createTestState({
        subPhase: 'selecting', // Wrong phase
        discardPile: toPile([createTestCard('K', 'disc1')]),
      });
      const action = GameActions.takeDiscard('p1');

      const newState = GameEngine.reduce(initialState, action);

      // State should be unchanged
      expect(newState).toEqual(initialState);
      expect(newState.subPhase).toBe('selecting');
      expect(newState.pendingAction).toBeNull();
    });

    it('should not mutate original state', () => {
      const initialState = createTestState({
        discardPile: toPile([createTestCard('K', 'disc1')]),
      });
      const stateCopy = JSON.parse(JSON.stringify(initialState));
      const action = GameActions.takeDiscard('p1');

      GameEngine.reduce(initialState, action);

      // Original state should be completely unchanged
      expect(initialState).toEqual(stateCopy);
    });

    it('should work with multiple cards in discard pile', () => {
      let state = createTestState({
        discardPile: toPile([
          createTestCard('K', 'disc0'),
          createTestCard('K', 'disc1'),
          createTestCard('K', 'disc2'),
        ]),
      });

      // First take (takes J, the top card)
      state = GameEngine.reduce(state, GameActions.takeDiscard('p1'));
      expect(state.discardPile).toHaveLength(2);
      expect(state.pendingAction?.card.id).toBe('disc3');

      // Reset to idle for second take
      state = {
        ...state,
        subPhase: 'idle',
        currentPlayerIndex: 1,
        pendingAction: null,
      };

      // Second take (takes Q)
      state = GameEngine.reduce(state, GameActions.takeDiscard('p2'));
      expect(state.discardPile).toHaveLength(1);
      expect(state.pendingAction?.card.id).toBe('disc2');
    });
  });

  describe('USE_CARD_ACTION action', () => {
    it('should transition to awaiting_action phase', () => {
      const cardToUse = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'selecting',
        pendingAction: {
          card: cardToUse,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const action = GameActions.playCardAction('p1', cardToUse);
      const newState = GameEngine.reduce(state, action);

      expect(newState.subPhase).toBe('awaiting_action');
    });

    it('should update pending action phase to selecting-target', () => {
      const cardToUse = createTestCard('8', 'card1');
      const state = createTestState({
        subPhase: 'selecting',
        pendingAction: {
          card: cardToUse,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const action = GameActions.playCardAction('p1', cardToUse);
      const newState = GameEngine.reduce(state, action);

      expect(newState.pendingAction?.actionPhase).toBe('selecting-target');
    });

    it('should reject when not in selecting phase', () => {
      const cardToUse = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        pendingAction: {
          card: cardToUse,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const action = GameActions.playCardAction('p1', cardToUse);
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should reject when no pending action', () => {
      const cardToUse = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'selecting',
        pendingAction: null,
      });

      const action = GameActions.playCardAction('p1', cardToUse);
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should reject when not player turn', () => {
      const cardToUse = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'selecting',
        currentPlayerIndex: 0, // p1's turn
        pendingAction: {
          card: cardToUse,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      // p2 trying to use action
      const action = GameActions.playCardAction('p2', cardToUse);
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should not mutate original state', () => {
      const cardToUse = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'selecting',
        pendingAction: {
          card: cardToUse,
          playerId: 'p1',
          actionPhase: 'choosing-action',
          targets: [],
        },
      });

      const stateCopy = JSON.parse(JSON.stringify(state));
      const action = GameActions.playCardAction('p1', cardToUse);

      GameEngine.reduce(state, action);

      expect(state).toEqual(stateCopy);
    });
  });

  describe('SELECT_ACTION_TARGET action', () => {
    it('should add target to pending action', () => {
      const actionCard = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [
              createTestCard('K', 'target1'),
              createTestCard('Q', 'target2'),
            ],
          },
        ],
        pendingAction: {
          card: actionCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.selectActionTarget('p1', 'p2', 1);
      const newState = GameEngine.reduce(state, action);

      // Target should be added (before clearing)
      expect(newState.discardPile).toHaveLength(1);
      expect(newState.discardPile.peekTop()?.id).toBe('card1');
    });

    it('should transition to idle after target selection', () => {
      const actionCard = createTestCard('8', 'card1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        turnCount: 5,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('K', 'target1')],
          },
        ],
        pendingAction: {
          card: actionCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.selectActionTarget('p1', 'p2', 0);
      const newState = GameEngine.reduce(state, action);

      expect(newState.subPhase).toBe('idle');
      expect(newState.turnCount).toBe(6);
      expect(newState.pendingAction).toBeNull();
    });

    it('should move card to discard pile', () => {
      const actionCard = createTestCard('9', 'card1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        discardPile: toPile([createTestCard('J', 'disc1')]),
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('K', 'target1')],
          },
        ],
        pendingAction: {
          card: actionCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.selectActionTarget('p1', 'p2', 0);
      const newState = GameEngine.reduce(state, action);

      expect(newState.discardPile).toHaveLength(2);
      expect(newState.discardPile.peekAt(1)?.id).toBe('card1');
    });

    it('should reject when not in awaiting_action phase', () => {
      const actionCard = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('K', 'target1')],
          },
        ],
        pendingAction: {
          card: actionCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.selectActionTarget('p1', 'p2', 0);
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should reject when no pending action', () => {
      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('K', 'target1')],
          },
        ],
        pendingAction: null,
      });

      const action = GameActions.selectActionTarget('p1', 'p2', 0);
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should reject when target player not found', () => {
      const actionCard = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('K', 'target1')],
          },
        ],
        pendingAction: {
          card: actionCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Try to target non-existent player
      const action = GameActions.selectActionTarget('p1', 'p999', 0);
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should reject when position is invalid', () => {
      const actionCard = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('K', 'target1')], // Only 1 card (position 0)
          },
        ],
        pendingAction: {
          card: actionCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // Try to target position 5 (invalid)
      const action = GameActions.selectActionTarget('p1', 'p2', 5);
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should reject when not player turn', () => {
      const actionCard = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0, // p1's turn
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('K', 'target1')],
          },
        ],
        pendingAction: {
          card: actionCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      // p2 trying to select target
      const action = GameActions.selectActionTarget('p2', 'p1', 0);
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should not mutate original state', () => {
      const actionCard = createTestCard('7', 'card1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [createTestCard('K', 'target1')],
          },
        ],
        pendingAction: {
          card: actionCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const stateCopy = JSON.parse(JSON.stringify(state));
      const action = GameActions.selectActionTarget('p1', 'p2', 0);

      GameEngine.reduce(state, action);

      expect(state).toEqual(stateCopy);
    });
  });

  describe('CONFIRM_PEEK action', () => {
    it('should move card to discard and complete turn', () => {
      const peekCard = createTestCard('7', 'peek1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        turnCount: 10,
        discardPile: toPile([createTestCard('K', 'disc1')]),
        pendingAction: {
          card: peekCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [{ playerId: 'p2', position: 0 }],
        },
      });

      const action = GameActions.confirmPeek('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState.discardPile).toHaveLength(2);
      expect(newState.discardPile.peekAt(1)?.id).toBe('peek1');
      expect(newState.turnCount).toBe(11);
      expect(newState.subPhase).toBe('idle');
      expect(newState.pendingAction).toBeNull();
    });

    it('should work after peeking at opponent card', () => {
      const peekCard = createTestCard('8', 'peek1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        turnCount: 5,
        pendingAction: {
          card: peekCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [{ playerId: 'p2', position: 1 }],
        },
      });

      const action = GameActions.confirmPeek('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState.subPhase).toBe('idle');
      expect(newState.turnCount).toBe(6);
    });

    it('should reject when not in awaiting_action phase', () => {
      const state = createTestState({
        subPhase: 'idle',
        pendingAction: {
          card: createTestCard('7', 'peek1'),
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.confirmPeek('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should reject when not player turn', () => {
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: {
          card: createTestCard('7', 'peek1'),
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.confirmPeek('p2');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should not mutate original state', () => {
      const state = createTestState({
        subPhase: 'awaiting_action',
        pendingAction: {
          card: createTestCard('7', 'peek1'),
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const stateCopy = JSON.parse(JSON.stringify(state));
      const action = GameActions.confirmPeek('p1');

      GameEngine.reduce(state, action);

      expect(state).toEqual(stateCopy);
    });
  });

  describe('CALL_VINTO action', () => {
    it('should set vinto caller and trigger final turn', () => {
      const state = createTestState({
        vintoCallerId: null,
        finalTurnTriggered: false,
      });

      const action = GameActions.callVinto('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState.vintoCallerId).toBe('p1');
      expect(newState.finalTurnTriggered).toBe(true);
      expect(newState.players[0].isVintoCaller).toBe(true);
    });

    it('should reject when vinto already called', () => {
      const state = createTestState({
        vintoCallerId: 'p2',
        finalTurnTriggered: true,
      });

      const action = GameActions.callVinto('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });

    it('should reject when not player turn', () => {
      const state = createTestState({
        currentPlayerIndex: 0,
        vintoCallerId: null,
      });

      const action = GameActions.callVinto('p2');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toEqual(state);
    });
  });

  describe('Full turn sequence', () => {
    it('should complete a full player turn', () => {
      // Initial state: Player 1's turn, idle phase
      let state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
        turnCount: 10,
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [
              createTestCard('K', 'hand1'),
              createTestCard('Q', 'hand2'),
              createTestCard('J', 'hand3'),
              createTestCard('10', 'hand4'),
            ],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      // 1. DRAW_CARD
      state = GameEngine.reduce(state, GameActions.drawCard('p1'));
      expect(state.subPhase).toBe('choosing');
      expect(state.pendingAction?.card.id).toBe('card1');

      // 2. SWAP_CARD
      state = GameEngine.reduce(state, GameActions.swapCard('p1', 0, 'A'));
      expect(state.subPhase).toBe('selecting');
      expect(state.players[0].cards[0].id).toBe('card1'); // Swapped in
      expect(state.pendingAction?.card.id).toBe('hand1'); // Removed card

      // 3. DISCARD_CARD
      state = GameEngine.reduce(state, GameActions.discardCard('p1'));
      expect(state.subPhase).toBe('idle');
      expect(state.discardPile).toHaveLength(1);
      expect(state.discardPile.peekTop()?.id).toBe('hand1');
      expect(state.turnCount).toBe(11);

      // 4. ADVANCE_TURN
      state = GameEngine.reduce(state, GameActions.advanceTurn());
      expect(state.currentPlayerIndex).toBe(1);
      expect(state.subPhase).toBe('ai_thinking'); // Next player is bot
    });

    it('should complete a full turn using PLAY_DISCARD instead of DRAW', () => {
      // Initial state: Player 1's turn, has a card in discard pile
      let state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
        turnCount: 5,
        discardPile: toPile([createTestCard('A', 'disc1')]), // Card available to take
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [
              createTestCard('K', 'hand1'),
              createTestCard('Q', 'hand2'),
              createTestCard('J', 'hand3'),
              createTestCard('10', 'hand4'),
            ],
          },
          createTestPlayer('p2', 'Player 2', false),
        ],
      });

      // 1. PLAY_DISCARD instead of DRAW_CARD
      state = GameEngine.reduce(state, GameActions.takeDiscard('p1'));
      expect(state.subPhase).toBe('choosing');
      expect(state.pendingAction?.card.id).toBe('disc1');
      expect(state.discardPile).toHaveLength(0); // Taken from discard

      // 2. SWAP_CARD
      state = GameEngine.reduce(state, GameActions.swapCard('p1', 2, 'J'));
      expect(state.subPhase).toBe('selecting');
      expect(state.players[0].cards[2].id).toBe('disc1'); // Ace swapped in
      expect(state.pendingAction?.card.id).toBe('hand3'); // J removed

      // 3. DISCARD_CARD
      state = GameEngine.reduce(state, GameActions.discardCard('p1'));
      expect(state.subPhase).toBe('idle');
      expect(state.discardPile).toHaveLength(1);
      expect(state.discardPile.peekTop()?.id).toBe('hand3'); // J discarded
      expect(state.turnCount).toBe(6);

      // 4. ADVANCE_TURN
      state = GameEngine.reduce(state, GameActions.advanceTurn());
      expect(state.currentPlayerIndex).toBe(1);
      expect(state.subPhase).toBe('ai_thinking'); // Next player is bot
    });

    it('should complete a full turn using card action', () => {
      // Initial state: Player 1's turn, will use card action
      let state = createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
        turnCount: 3,
        players: [
          {
            ...createTestPlayer('p1', 'Player 1', true),
            cards: [
              createTestCard('K', 'hand1'),
              createTestCard('Q', 'hand2'),
              createTestCard('J', 'hand3'),
              createTestCard('10', 'hand4'),
            ],
          },
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [
              createTestCard('A', 'p2card1'),
              createTestCard('7', 'p2card2'),
            ],
          },
        ],
      });

      // 1. DRAW_CARD
      state = GameEngine.reduce(state, GameActions.drawCard('p1'));
      expect(state.subPhase).toBe('choosing');
      expect(state.pendingAction?.card.id).toBe('card1');

      // 2. SWAP_CARD
      state = GameEngine.reduce(state, GameActions.swapCard('p1', 0));
      expect(state.subPhase).toBe('selecting');
      expect(state.pendingAction?.card.id).toBe('hand1'); // K removed

      // 3. USE_CARD_ACTION (instead of discarding)
      const removedCard = state.pendingAction!.card;
      state = GameEngine.reduce(
        state,
        GameActions.playCardAction('p1', removedCard)
      );
      expect(state.subPhase).toBe('awaiting_action');
      expect(state.pendingAction?.actionPhase).toBe('selecting-target');

      // 4. SELECT_ACTION_TARGET
      state = GameEngine.reduce(
        state,
        GameActions.selectActionTarget('p1', 'p2', 1)
      );
      expect(state.subPhase).toBe('idle');
      expect(state.discardPile).toHaveLength(1);
      expect(state.discardPile.peekTop()?.id).toBe('hand1'); // K discarded after action
      expect(state.turnCount).toBe(4);
      expect(state.pendingAction).toBeNull();

      // 5. ADVANCE_TURN
      state = GameEngine.reduce(state, GameActions.advanceTurn());
      expect(state.currentPlayerIndex).toBe(1);
      expect(state.subPhase).toBe('ai_thinking');
    });
  });

  describe('EXECUTE_QUEEN_SWAP action', () => {
    it('should swap two cards at target positions', () => {
      const queenCard = createTestCard('Q', 'queen1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        turnCount: 10,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [
              createTestCard('K', 'p2card1'),
              createTestCard('A', 'p2card2'),
            ],
          },
          {
            ...createTestPlayer('p3', 'Player 3', false),
            cards: [
              createTestCard('7', 'p3card1'),
              createTestCard('8', 'p3card2'),
            ],
          },
        ],
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 }, // K
            { playerId: 'p3', position: 1 }, // 8
          ],
        },
      });

      const action = GameActions.executeQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      // Cards should be swapped
      expect(newState.players[1].cards[0].rank).toBe('8'); // p2[0] now has 8
      expect(newState.players[2].cards[1].rank).toBe('K'); // p3[1] now has K

      // Queen card should be in discard pile
      expect(newState.discardPile).toHaveLength(1);
      expect(newState.discardPile.peekTop()?.id).toBe('queen1');

      // Turn should be complete
      expect(newState.pendingAction).toBeNull();
      expect(newState.turnCount).toBe(11);
      expect(newState.subPhase).toBe('idle');
    });

    it('should swap cards within same player hand', () => {
      const queenCard = createTestCard('Q', 'queen1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [
              createTestCard('K', 'p2card1'),
              createTestCard('A', 'p2card2'),
              createTestCard('7', 'p2card3'),
              createTestCard('8', 'p2card4'),
            ],
          },
        ],
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 }, // K
            { playerId: 'p2', position: 2 }, // 7
          ],
        },
      });

      const action = GameActions.executeQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      // Cards within p2's hand should be swapped
      expect(newState.players[1].cards[0].rank).toBe('7'); // position 0 now has 7
      expect(newState.players[1].cards[2].rank).toBe('K'); // position 2 now has K

      // Other cards unchanged
      expect(newState.players[1].cards[1].rank).toBe('A');
      expect(newState.players[1].cards[3].rank).toBe('8');
    });

    it('should fail if not player turn', () => {
      const queenCard = createTestCard('Q', 'queen1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 1, // Not p1's turn
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 },
            { playerId: 'p3', position: 1 },
          ],
        },
      });

      const action = GameActions.executeQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should fail if not in awaiting_action phase', () => {
      const queenCard = createTestCard('Q', 'queen1');
      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        currentPlayerIndex: 0,
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 },
            { playerId: 'p3', position: 1 },
          ],
        },
      });

      const action = GameActions.executeQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should fail if no pending action', () => {
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: null, // No pending action
      });

      const action = GameActions.executeQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should fail if not exactly 2 targets', () => {
      const queenCard = createTestCard('Q', 'queen1');

      // Test with 1 target
      let state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [{ playerId: 'p2', position: 0 }], // Only 1 target
        },
      });

      let action = GameActions.executeQueenSwap('p1');
      let newState = GameEngine.reduce(state, action);
      expect(newState).toBe(state); // State unchanged

      // Test with 3 targets
      state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 },
            { playerId: 'p2', position: 1 },
            { playerId: 'p2', position: 2 }, // 3 targets
          ],
        },
      });

      action = GameActions.executeQueenSwap('p1');
      newState = GameEngine.reduce(state, action);
      expect(newState).toBe(state); // State unchanged
    });

    it('should preserve immutability', () => {
      const queenCard = createTestCard('Q', 'queen1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [
              createTestCard('K', 'p2card1'),
              createTestCard('A', 'p2card2'),
            ],
          },
        ],
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p1', position: 0 },
            { playerId: 'p2', position: 1 },
          ],
        },
      });

      const originalPlayers = state.players;
      const originalPlayer1Cards = state.players[0].cards;
      const originalPlayer2Cards = state.players[1].cards;

      const action = GameActions.executeQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      // Original state should be unchanged
      expect(state.players).toBe(originalPlayers);
      expect(state.players[0].cards).toBe(originalPlayer1Cards);
      expect(state.players[1].cards).toBe(originalPlayer2Cards);
      expect(state.pendingAction).not.toBeNull();

      // New state should be different
      expect(newState.players).not.toBe(originalPlayers);
      expect(newState.pendingAction).toBeNull();
    });
  });

  describe('SKIP_QUEEN_SWAP action', () => {
    it('should skip swap and complete turn', () => {
      const queenCard = createTestCard('Q', 'queen1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        turnCount: 10,
        players: [
          createTestPlayer('p1', 'Player 1', true),
          {
            ...createTestPlayer('p2', 'Player 2', false),
            cards: [
              createTestCard('K', 'p2card1'),
              createTestCard('A', 'p2card2'),
            ],
          },
          {
            ...createTestPlayer('p3', 'Player 3', false),
            cards: [
              createTestCard('7', 'p3card1'),
              createTestCard('8', 'p3card2'),
            ],
          },
        ],
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 }, // K
            { playerId: 'p3', position: 1 }, // 8
          ],
        },
      });

      const action = GameActions.skipQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      // Cards should NOT be swapped
      expect(newState.players[1].cards[0].rank).toBe('K'); // p2[0] still has K
      expect(newState.players[2].cards[1].rank).toBe('8'); // p3[1] still has 8

      // Queen card should be in discard pile
      expect(newState.discardPile).toHaveLength(1);
      expect(newState.discardPile.peekTop()?.id).toBe('queen1');

      // Turn should be complete
      expect(newState.pendingAction).toBeNull();
      expect(newState.turnCount).toBe(11);
      expect(newState.subPhase).toBe('idle');
    });

    it('should fail if not player turn', () => {
      const queenCard = createTestCard('Q', 'queen1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 1, // Not p1's turn
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 },
            { playerId: 'p3', position: 1 },
          ],
        },
      });

      const action = GameActions.skipQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should fail if not in awaiting_action phase', () => {
      const queenCard = createTestCard('Q', 'queen1');
      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        currentPlayerIndex: 0,
        pendingAction: {
          card: queenCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [
            { playerId: 'p2', position: 0 },
            { playerId: 'p3', position: 1 },
          ],
        },
      });

      const action = GameActions.skipQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should fail if no pending action', () => {
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: null, // No pending action
      });

      const action = GameActions.skipQueenSwap('p1');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });
  });

  describe('DECLARE_KING_ACTION action', () => {
    it('should declare rank and trigger toss-in', () => {
      const kingCard = createTestCard('K', 'king1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        turnCount: 10,
        activeTossIn: null,
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.declareKingAction('p1', 'A');
      const newState = GameEngine.reduce(state, action);

      // Toss-in should be triggered
      expect(newState.activeTossIn).not.toBeNull();
      expect(newState.activeTossIn?.initiatorId).toBe('p1');
      expect(newState.activeTossIn?.rank).toBe('A');
      expect(newState.activeTossIn?.participants).toEqual([]);

      // King card should be in discard pile
      expect(newState.discardPile).toHaveLength(1);
      expect(newState.discardPile.peekTop()?.id).toBe('king1');

      // Turn should be complete
      expect(newState.pendingAction).toBeNull();
      expect(newState.turnCount).toBe(11);
      expect(newState.subPhase).toBe('idle');
    });

    it('should work with different ranks', () => {
      const kingCard = createTestCard('K', 'king1');

      // Test with rank 7
      let state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      let action = GameActions.declareKingAction('p1', '7');
      let newState = GameEngine.reduce(state, action);
      expect(newState.activeTossIn?.rank).toBe('7');

      // Test with rank Q
      state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      action = GameActions.declareKingAction('p1', 'Q');
      newState = GameEngine.reduce(state, action);
      expect(newState.activeTossIn?.rank).toBe('Q');
    });

    it('should fail if not player turn', () => {
      const kingCard = createTestCard('K', 'king1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 1, // Not p1's turn
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.declareKingAction('p1', 'A');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should fail if not in awaiting_action phase', () => {
      const kingCard = createTestCard('K', 'king1');
      const state = createTestState({
        subPhase: 'idle', // Wrong phase
        currentPlayerIndex: 0,
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.declareKingAction('p1', 'A');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should fail if no pending action', () => {
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: null, // No pending action
      });

      const action = GameActions.declareKingAction('p1', 'A');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should fail if pending card is not a King', () => {
      const notKingCard = createTestCard('Q', 'queen1'); // Queen, not King
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        pendingAction: {
          card: notKingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const action = GameActions.declareKingAction('p1', 'A');
      const newState = GameEngine.reduce(state, action);

      expect(newState).toBe(state); // State unchanged
    });

    it('should preserve immutability', () => {
      const kingCard = createTestCard('K', 'king1');
      const state = createTestState({
        subPhase: 'awaiting_action',
        currentPlayerIndex: 0,
        activeTossIn: null,
        pendingAction: {
          card: kingCard,
          playerId: 'p1',
          actionPhase: 'selecting-target',
          targets: [],
        },
      });

      const originalPendingAction = state.pendingAction;
      const originalActiveTossIn = state.activeTossIn;

      const action = GameActions.declareKingAction('p1', 'J');
      const newState = GameEngine.reduce(state, action);

      // Original state should be unchanged
      expect(state.pendingAction).toBe(originalPendingAction);
      expect(state.activeTossIn).toBe(originalActiveTossIn);
      expect(state.pendingAction).not.toBeNull();

      // New state should be different
      expect(newState.pendingAction).toBeNull();
      expect(newState.activeTossIn).not.toBeNull();
    });
  });
});
