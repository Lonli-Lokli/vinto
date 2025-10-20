// GameEngine.scenarios.test.ts
// Test specific game scenarios that have caused issues

import { describe, it, expect } from 'vitest';
import { GameEngine } from '../game-engine';
import { GameActions } from '../game-actions';
import { GameState, Card, Pile } from '@vinto/shapes';

/**
 * Helper: Create a test game state with specific cards
 */
function createTestState(overrides?: Partial<GameState>): GameState {
  const baseState: GameState = {
    gameId: 'test-game',
    roundNumber: 1,
    turnCount: 1,
    phase: 'playing',
    subPhase: 'idle',
    finalTurnTriggered: false,
    players: [
      {
        id: 'human-1',
        name: 'Human',
        isHuman: true,
        isBot: false,
        cards: [],
        knownCardPositions: [],
        isVintoCaller: false,
        coalitionWith: [],
      },
      {
        id: 'bot-1',
        name: 'Bot 1',
        isHuman: false,
        isBot: true,
        cards: [],
        knownCardPositions: [],
        isVintoCaller: false,
        coalitionWith: [],
      },
      {
        id: 'bot-2',
        name: 'Bot 2',
        isHuman: false,
        isBot: true,
        cards: [],
        knownCardPositions: [],
        isVintoCaller: false,
        coalitionWith: [],
      },
      {
        id: 'bot-3',
        name: 'Bot 3',
        isHuman: false,
        isBot: true,
        cards: [],
        knownCardPositions: [],
        isVintoCaller: false,
        coalitionWith: [],
      },
    ],
    currentPlayerIndex: 0,
    vintoCallerId: null,
    coalitionLeaderId: null,
    drawPile: Pile.fromCards([]),
    discardPile: Pile.fromCards([]),
    pendingAction: null,
    activeTossIn: null,
    recentActions: [],
    difficulty: 'moderate',
    ...overrides,
  };

  return baseState;
}

/**
 * Helper: Create test cards
 */
function createCard(rank: string, id?: string): Card {
  return {
    id: id || `${rank}_test`,
    rank: rank as any,
    value: 0,
    played: false,
    actionText: ['7', '8', '9', '10', 'J', 'Q', 'K', 'A'].includes(rank)
      ? 'Action'
      : undefined,
  };
}

describe('GameEngine - Toss-in Scenarios', () => {
  it('Scenario 01. should handle human tossing in second Ace during toss-in without duplicate ready error', () => {
    // Scenario:
    // 1. Human uses Ace from hand → toss-in starts
    // 2. Human tosses in second Ace → queued
    // 3. Human clicks Continue → marked as ready
    // 4. Queued Ace action processes → human selects target
    // 5. Action completes → returns to toss-in with CLEARED ready list
    // 6. Human clicks Continue again → should NOT error

    // Setup: Human has 2 Aces, others have random cards
    const ace1 = createCard('A', 'A_1');
    const ace2 = createCard('A', 'A_2');
    const state = createTestState({
      players: [
        {
          id: 'human-1',
          name: 'Human',
          isHuman: true,
          isBot: false,
          cards: [
            ace1,
            ace2,
            createCard('2'),
            createCard('3'),
            createCard('4'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-1',
          name: 'Bot 1',
          isHuman: false,
          isBot: true,
          cards: [
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
            createCard('6'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-2',
          name: 'Bot 2',
          isHuman: false,
          isBot: true,
          cards: [
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
            createCard('6'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-3',
          name: 'Bot 3',
          isHuman: false,
          isBot: true,
          cards: [
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
            createCard('6'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
      ],
      drawPile: Pile.fromCards([createCard('2'), createCard('3')]),
      discardPile: Pile.fromCards([]),
      subPhase: 'choosing',
      pendingAction: {
        card: ace1,
        playerId: 'human-1',
        actionPhase: 'choosing-action',
        targets: [],
      },
    });

    // Step 1: Human chooses to use first Ace action (card already in pendingAction from draw)
    let newState = GameEngine.reduce(
      state,
      GameActions.playCardAction('human-1', ace1)
    );
    expect(newState.subPhase).toBe('awaiting_action');

    // Step 2: Human selects target for first Ace (forces bot-1 to draw)
    newState = GameEngine.reduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-1', 0)
    );
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn).not.toBeNull();
    expect(newState.activeTossIn?.ranks).toContain('A');

    // Step 3: Human tosses in second Ace
    newState = GameEngine.reduce(
      newState,
      GameActions.participateInTossIn('human-1', 1)
    ); // Position 1 has ace2
    expect(newState.activeTossIn?.queuedActions.length).toBe(1);

    // Step 4: Human clicks Continue (marks as ready)
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );
    expect(newState.activeTossIn?.playersReadyForNextTurn).toContain('human-1');

    // Bots also mark ready (simulating bot AI)
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-3')
    );

    // All humans ready, queued actions should start processing
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('A');

    // Step 5: Human selects target for queued Ace action
    newState = GameEngine.reduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-2', 0)
    );

    // CRITICAL CHECK: After queued action completes, ready list should be CLEARED
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn).not.toBeNull();
    expect(newState.activeTossIn?.playersReadyForNextTurn).not.toContain(
      'human-1'
    );
    expect(newState.activeTossIn?.playersReadyForNextTurn.length).toBe(0); // Only Vinto callers should be in list

    // Step 6: Human clicks Continue again - should NOT error!
    const finalState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );

    // Should succeed without validation error
    expect(finalState.activeTossIn?.playersReadyForNextTurn).toContain(
      'human-1'
    );
  });

  it('Scenario 02. should clear ready list when any queued action card completes during toss-in', () => {
    // Test with different action cards (J, Q, K, 7-10) to ensure all handlers clear ready list

    // Test with Jack card
    const jack = createCard('J', 'J_1');
    const state = createTestState({
      players: [
        {
          id: 'human-1',
          name: 'Human',
          isHuman: true,
          isBot: false,
          cards: [
            jack,
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-1',
          name: 'Bot 1',
          isHuman: false,
          isBot: true,
          cards: [
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
            createCard('6'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-2',
          name: 'Bot 2',
          isHuman: false,
          isBot: true,
          cards: [
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
            createCard('6'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-3',
          name: 'Bot 3',
          isHuman: false,
          isBot: true,
          cards: [
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
            createCard('6'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
      ],
      subPhase: 'awaiting_action',
      pendingAction: {
        card: jack,
        playerId: 'human-1',
        actionPhase: 'selecting-target',
        targetType: 'swap-cards',
        targets: [],
      },
      activeTossIn: {
        ranks: ['J'],
        initiatorId: 'human-1',
        originalPlayerIndex: 0,
        participants: [],
        queuedActions: [],
        waitingForInput: false,
        playersReadyForNextTurn: ['human-1', 'bot-1', 'bot-2', 'bot-3'], // All marked ready from previous round
      },
    });

    // Jack action: Select 2 cards to swap
    let newState = GameEngine.reduce(
      state,
      GameActions.selectActionTarget('human-1', 'human-1', 0)
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-1', 0)
    );

    // After Jack action completes, ready list should be cleared
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn?.playersReadyForNextTurn.length).toBe(0);
  });

  it('should NOT get stuck in infinite Ace action loop after toss-in King action', () => {
    // Bug scenario:
    // 1. Human has 2 Aces in hand, draws King
    // 2. Human uses King action, declares Ace in bot's hand (correct)
    // 3. Declared Ace has action → human must use Ace action immediately
    // 4. After Ace action completes → toss-in starts for Ace rank
    // 5. Human tosses in first remaining Ace from hand → queued
    // 6. Human clicks Continue → queued Ace action starts
    // 7. BUG: After queued Ace completes, it should return to toss-in (not create ANOTHER Ace toss-in)
    // 8. Human should be able to toss in second Ace or continue to next turn

    const king = createCard('K', 'K_1');
    const ace1 = createCard('A', 'A_1');
    const ace2 = createCard('A', 'A_2');
    const targetCard = createCard('A', 'A_target'); // Ace in bot's hand that King will declare

    const state = createTestState({
      players: [
        {
          id: 'human-1',
          name: 'Human',
          isHuman: true,
          isBot: false,
          cards: [king, ace1, ace2, createCard('2'), createCard('3')],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-1',
          name: 'Bot 1',
          isHuman: false,
          isBot: true,
          cards: [
            targetCard,
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-2',
          name: 'Bot 2',
          isHuman: false,
          isBot: true,
          cards: [
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
            createCard('6'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
        {
          id: 'bot-3',
          name: 'Bot 3',
          isHuman: false,
          isBot: true,
          cards: [
            createCard('2'),
            createCard('3'),
            createCard('4'),
            createCard('5'),
            createCard('6'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
      ],
      drawPile: Pile.fromCards([createCard('2'), createCard('3'), createCard('4')]),
      discardPile: Pile.fromCards([]),
      subPhase: 'choosing',
      pendingAction: {
        card: king,
        playerId: 'human-1',
        actionPhase: 'choosing-action',
        targets: [],
      },
    });

    // Step 1: Human chooses to use King action (card already in pendingAction from draw)
    let newState = GameEngine.reduce(
      state,
      GameActions.playCardAction('human-1', king)
    );
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('K');

    // Step 2: Human selects target card (bot-1's Ace at position 0)
    newState = GameEngine.reduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-1', 0)
    );
    expect(newState.pendingAction?.targets[0]?.card).toBeDefined();

    // Step 3: Human declares rank 'A' (correct declaration - Ace is action card)
    newState = GameEngine.reduce(
      newState,
      GameActions.declareKingAction('human-1', 'A')
    );

    // King correctly declared Ace (action card) → sets up pending action for Ace
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('A'); // Declared Ace is now pending
    expect(newState.activeTossIn).toBeNull(); // No toss-in yet

    // Step 4: Human uses the declared Ace action (force-draw on bot-2)
    newState = GameEngine.reduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-2', 0)
    );

    // Ace action completes → toss-in starts for Ace rank
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn?.ranks).toContain('A');
    expect(newState.pendingAction).toBeNull(); // Ace action cleared

    // Step 5: Human tosses in first Ace from hand (position 1, since position 0 is King)
    newState = GameEngine.reduce(
      newState,
      GameActions.participateInTossIn('human-1', 1)
    ); // Position 1 has ace1
    expect(newState.activeTossIn?.queuedActions.length).toBe(1);

    // Step 6: Human clicks Continue (marks as ready)
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );

    // Bots also mark ready
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-3')
    );

    // All humans ready, queued Ace action should start processing
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('A');
    expect(newState.pendingAction?.playerId).toBe('human-1');

    // Step 7: Human selects target for queued Ace action
    newState = GameEngine.reduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-3', 0)
    );

    // CRITICAL: After queued Ace completes, should return to toss-in (NOT create another Ace toss-in!)
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn).not.toBeNull();
    expect(newState.activeTossIn?.ranks).toContain('A'); // Still toss-in for 'A' (same toss-in continues)
    expect(newState.pendingAction).toBeNull(); // Ace action should be CLEARED
    expect(newState.activeTossIn?.queuedActions.length).toBe(0); // Queue should be empty

    // Step 8: Human can now toss in second Ace or click Continue to finish
    // After ace1 was removed from position 1, ace2 is now at position 1 (was at position 2)
    newState = GameEngine.reduce(
      newState,
      GameActions.participateInTossIn('human-1', 1)
    );
    expect(newState.activeTossIn?.queuedActions.length).toBe(1);

    // Human clicks Continue
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );

    // Bots mark ready
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-3')
    );

    // Second Ace should process
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('A');

    // Complete second Ace action
    newState = GameEngine.reduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-1', 0)
    );

    // Should return to toss-in again (same toss-in continues)
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.pendingAction).toBeNull();
    expect(newState.activeTossIn?.queuedActions.length).toBe(0);

    // Final Continue - should advance turn to next player
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished('bot-3')
    );

    // Toss-in should be complete, turn should advance
    expect(newState.activeTossIn).toBeNull();
    expect(newState.currentPlayerIndex).toBe(1); // Next player
  });
});
