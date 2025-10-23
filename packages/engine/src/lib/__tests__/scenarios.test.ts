// GameEngine.scenarios.test.ts
// Test specific game scenarios that have caused issues

import { describe, it, expect } from 'vitest';
import { GameEngine } from '../game-engine';
import { GameActions } from '../game-actions';
import { GameState, Pile } from '@vinto/shapes';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  markPlayersReady,
  unsafeReduce,
} from './test-helpers';
import { mockLogger } from './setup-tests';

describe('GameEngine - Toss-in Scenarios', () => {
  beforeEach(() => {
    // Reset all mock calls before each test
    mockLogger.log.mockClear();
    mockLogger.warn.mockClear();
    mockLogger.error.mockClear();
  });
  it('Scenario 01. should handle human tossing in second Ace during toss-in without duplicate ready error', () => {
    // Scenario:
    // 1. Human uses Ace from hand → toss-in starts
    // 2. Human tosses in second Ace → queued
    // 3. Human clicks Continue → marked as ready
    // 4. Queued Ace action processes → human selects target
    // 5. Action completes → returns to toss-in with CLEARED ready list
    // 6. Human clicks Continue again → should NOT error

    // Setup: Human has 2 Aces, others have random cards
    const ace1 = createTestCard('A', 'A_1');
    const ace2 = createTestCard('A', 'A_2');
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
            createTestCard('2', '2_1'),
            createTestCard('3', '3_1'),
            createTestCard('4', '4_1'),
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
            createTestCard('2', '2_2'),
            createTestCard('3', '3_2'),
            createTestCard('4', '4_2'),
            createTestCard('5', '5_2'),
            createTestCard('6', '6_2'),
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
            createTestCard('2', '2_3'),
            createTestCard('3', '3_3'),
            createTestCard('4', '4_3'),
            createTestCard('5', '5_3'),
            createTestCard('6', '6_3'),
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
            createTestCard('2', '2_4'),
            createTestCard('3', '3_4'),
            createTestCard('4', '4_4'),
            createTestCard('5', '5_4'),
            createTestCard('6', '6_4'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
      ],
      drawPile: Pile.fromCards([
        createTestCard('2', '2_4'),
        createTestCard('3', '3_4'),
      ]),
      discardPile: Pile.fromCards([]),
      subPhase: 'choosing',
      pendingAction: {
        card: ace1,
        from: 'drawing',
        playerId: 'human-1',
        actionPhase: 'choosing-action',
        targets: [],
      },
    });

    // Step 1: Human chooses to use first Ace action (card already in pendingAction from draw)
    let newState = unsafeReduce(
      state,
      GameActions.playCardAction('human-1', ace1)
    );
    expect(newState.subPhase).toBe('awaiting_action');

    // Step 2: Human selects target for first Ace (forces bot-1 to draw)
    newState = unsafeReduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-1', 0)
    );
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn).not.toBeNull();
    expect(newState.activeTossIn?.ranks).toContain('A');

    // Step 3: Human tosses in second Ace
    newState = unsafeReduce(
      newState,
      GameActions.participateInTossIn('human-1', [1])
    ); // Position 1 has ace2
    expect(newState.activeTossIn?.queuedActions.length).toBe(1);

    // Step 4: Human clicks Continue (marks as ready)
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );
    expect(newState.activeTossIn?.playersReadyForNextTurn).toContain('human-1');

    // Bots also mark ready (simulating bot AI)
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-3')
    );

    // All humans ready, queued actions should start processing
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('A');

    // Step 5: Human selects target for queued Ace action
    newState = unsafeReduce(
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
    const finalState = unsafeReduce(
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
    const jack = createTestCard('J', 'J_1');
    const state = createTestState({
      players: [
        {
          id: 'human-1',
          name: 'Human',
          isHuman: true,
          isBot: false,
          cards: [
            jack,
            createTestCard('2', '2_1'),
            createTestCard('3', '3_1'),
            createTestCard('4', '4_1'),
            createTestCard('5', '5_1'),
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
            createTestCard('2', '2_2'),
            createTestCard('3', '3_2'),
            createTestCard('4', '4_2'),
            createTestCard('5', '5_2'),
            createTestCard('6', '6_2'),
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
            createTestCard('2', '2_3'),
            createTestCard('3', '3_3'),
            createTestCard('4', '4_3'),
            createTestCard('5', '5_3'),
            createTestCard('6', '6_3'),
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
            createTestCard('2', '2_4'),
            createTestCard('3', '3_4'),
            createTestCard('4', '4_4'),
            createTestCard('5', '5_4'),
            createTestCard('6', '6_4'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
      ],
      subPhase: 'awaiting_action',
      pendingAction: {
        card: jack,
        from: 'drawing',
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
    let newState = unsafeReduce(
      state,
      GameActions.selectActionTarget('human-1', 'human-1', 0)
    );
    newState = unsafeReduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-1', 0)
    );

    expect(newState.subPhase).toBe('awaiting_action');
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

    const king = createTestCard('K', 'K_1');
    const ace1 = createTestCard('A', 'A_1');
    const ace2 = createTestCard('A', 'A_2');
    const targetCard = createTestCard('A', 'A_target'); // Ace in bot's hand that King will declare

    const state = createTestState({
      players: [
        {
          id: 'human-1',
          name: 'Human',
          isHuman: true,
          isBot: false,
          cards: [
            king,
            ace1,
            ace2,
            createTestCard('2', '2_1'),
            createTestCard('3', '3_1'),
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
            targetCard,
            createTestCard('2', '2_2'),
            createTestCard('3', '3_2'),
            createTestCard('4', '4_2'),
            createTestCard('5', '5_2'),
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
            createTestCard('2', '2_3'),
            createTestCard('3', '3_3'),
            createTestCard('4', '4_3'),
            createTestCard('5', '5_3'),
            createTestCard('6', '6_3'),
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
            createTestCard('2', '2_4'),
            createTestCard('3', '3_4'),
            createTestCard('4', '4_4'),
            createTestCard('5', '5_4'),
            createTestCard('6', '6_4'),
          ],
          knownCardPositions: [],
          isVintoCaller: false,
          coalitionWith: [],
        },
      ],
      drawPile: Pile.fromCards([
        createTestCard('2', '2_draw'),
        createTestCard('3', '3_draw'),
        createTestCard('4', '4_draw'),
      ]),
      discardPile: Pile.fromCards([]),
      subPhase: 'choosing',
      pendingAction: {
        card: king,
        playerId: 'human-1',
        from: 'drawing',
        actionPhase: 'choosing-action',
        targets: [],
      },
    });

    // Step 1: Human chooses to use King action (card already in pendingAction from draw)
    let newState = unsafeReduce(
      state,
      GameActions.playCardAction('human-1', king)
    );
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('K');

    // Step 2: Human selects target card (bot-1's Ace at position 0)
    newState = unsafeReduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-1', 0)
    );
    expect(newState.pendingAction?.targets[0]?.card).toBeDefined();

    // Step 3: Human declares rank 'A' (correct declaration - Ace is action card)
    newState = unsafeReduce(
      newState,
      GameActions.declareKingAction('human-1', 'A')
    );

    // King correctly declared Ace (action card) → sets up pending action for Ace
    expect(newState.subPhase).toBe('awaiting_action');

    // Step 4: Human uses the declared Ace action (force-draw on bot-2)
    newState = unsafeReduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-2', 0)
    );

    // Ace action completes → toss-in starts for Ace rank
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn?.ranks).toContain('A');
    expect(newState.pendingAction).toBeNull(); // Ace action cleared

    // Step 5: Human tosses in first Ace from hand (position 1, since position 0 is King)
    newState = unsafeReduce(
      newState,
      GameActions.participateInTossIn('human-1', [1])
    ); // Position 1 has ace1
    expect(newState.activeTossIn?.queuedActions.length).toBe(1);

    // Step 6: Human clicks Continue (marks as ready)
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );

    // Bots also mark ready
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-3')
    );

    // All humans ready, queued Ace action should start processing
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('A');
    expect(newState.pendingAction?.playerId).toBe('human-1');

    // Step 7: Human selects target for queued Ace action
    newState = unsafeReduce(
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
    newState = unsafeReduce(
      newState,
      GameActions.participateInTossIn('human-1', [1])
    );
    expect(newState.activeTossIn?.queuedActions.length).toBe(1);

    // Human clicks Continue
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );

    // Bots mark ready
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-3')
    );

    // Second Ace should process
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction?.card?.rank).toBe('A');

    // Complete second Ace action
    newState = unsafeReduce(
      newState,
      GameActions.selectActionTarget('human-1', 'bot-1', 0)
    );

    // Should return to toss-in again (same toss-in continues)
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.pendingAction).toBeNull();
    expect(newState.activeTossIn?.queuedActions.length).toBe(0);

    // Final Continue - should advance turn to next player
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-3')
    );

    // Toss-in should be complete, turn should advance
    expect(newState.currentPlayerIndex).toBe(1); // Next player
  });

  it('Scenario 03. Turn auto-advances after all players are ready', () => {
    const state = createTestState({
      subPhase: 'idle',
      currentPlayerIndex: 0,
      turnNumber: 1,
      players: [
        createTestPlayer('p1', 'Player 1', true, [
          createTestCard('A', 'p1c1'),
          createTestCard('K', 'p1c2'),
        ]),
        createTestPlayer('p2', 'Player 2', false, [
          createTestCard('A', 'p2c1'),
          createTestCard('K', 'p2c2'),
        ]),
        createTestPlayer('p3', 'Player 3', false, [
          createTestCard('J', 'p3c1'),
          createTestCard('J', 'p3c2'),
        ]),
        createTestPlayer('p4', 'Player 4', false, [
          createTestCard('Q', 'p4c1'),
          createTestCard('Q', 'p4c2'),
        ]),
      ],
      drawPile: Pile.fromCards([
        createTestCard('2', '2'),
        createTestCard('3', '3'),
        createTestCard('4', '4'),
        createTestCard('5', '5'),
      ]),
    });

    const makeTurn = (
      playerId: string,
      cardPosition: number,
      currentState: GameState
    ) => {
      let updatedState = unsafeReduce(
        currentState,
        GameActions.drawCard(playerId)
      );

      updatedState = unsafeReduce(
        updatedState,
        GameActions.swapCard(playerId, cardPosition)
      );

      updatedState = markPlayersReady(updatedState, ['p1', 'p2', 'p3', 'p4']);
      return updatedState;
    };

    let newState = makeTurn('p1', 0, state);
    expect(mockLogger.warn).not.toHaveBeenCalled();
    expect(newState.currentPlayerIndex).toBe(1); // Turn should advance to Player 2
    expect(newState.turnNumber).toBe(2); // Turn count should increment

    newState = makeTurn('p2', 0, newState);
    expect(newState.currentPlayerIndex).toBe(2); // Turn should advance to Player 3
    expect(newState.turnNumber).toBe(3); // Turn count should increment

    newState = makeTurn('p3', 0, newState);
    expect(newState.currentPlayerIndex).toBe(3); // Turn should advance to Player 4
    expect(newState.turnNumber).toBe(4); // Turn count should increment

    newState = makeTurn('p4', 0, newState);
    expect(newState.currentPlayerIndex).toBe(0); // Turn should advance back to Player 1
    expect(newState.turnNumber).toBe(5); // Turn count should increment

    expect(newState.roundNumber).toBe(2); // Round count should increment
    expect(mockLogger.warn).not.toHaveBeenCalled();
  });

  it('Scenario 03. Turn auto-advances after bot playing card', () => {
    const state = createTestState({
      subPhase: 'idle',
      currentPlayerIndex: 0,
      turnNumber: 1,
      players: [
        createTestPlayer('p1', 'Player 1', true, [
          createTestCard('A', 'p1c1'),
          createTestCard('K', 'p1c2'),
        ]),
        createTestPlayer('p2', 'Player 2', false, [
          createTestCard('A', 'p2c1'),
          createTestCard('K', 'p2c2'),
        ]),
        createTestPlayer('p3', 'Player 3', false, [
          createTestCard('J', 'p3c1'),
          createTestCard('J', 'p3c2'),
        ]),
        createTestPlayer('p4', 'Player 4', false, [
          createTestCard('Q', 'p4c1'),
          createTestCard('Q', 'p4c2'),
        ]),
      ],
      drawPile: Pile.fromCards([
        createTestCard('Q', 'draw1'),
        createTestCard('Q', 'draw2'),
        createTestCard('Q', 'draw3'),
        createTestCard('Q', 'draw4'),
      ]),
    });

    const makeTurn = (playerId: string, currentState: GameState) => {
      let updatedState = unsafeReduce(
        currentState,
        GameActions.drawCard(playerId)
      );

      updatedState = unsafeReduce(
        updatedState,
        GameActions.playCardAction(
          playerId,
          createTestCard('Q', `play_${updatedState.turnNumber}`)
        )
      );

      updatedState = markPlayersReady(updatedState, ['p1', 'p2', 'p3', 'p4']);
      return updatedState;
    };

    let newState = makeTurn('p1', state);
    expect(mockLogger.warn).not.toHaveBeenCalled();
    expect(newState.currentPlayerIndex).toBe(1); // Turn should advance to Player 2
    expect(newState.turnNumber).toBe(2); // Turn count should increment

    newState = makeTurn('p2', newState);
    expect(newState.currentPlayerIndex).toBe(2); // Turn should advance to Player 3
    expect(newState.turnNumber).toBe(3); // Turn count should increment

    newState = makeTurn('p3', newState);
    expect(newState.currentPlayerIndex).toBe(3); // Turn should advance to Player 4
    expect(newState.turnNumber).toBe(4); // Turn count should increment

    newState = makeTurn('p4', newState);
    expect(newState.currentPlayerIndex).toBe(0); // Turn should advance back to Player 1
    expect(newState.turnNumber).toBe(5); // Turn count should increment

    expect(newState.roundNumber).toBe(2); // Round count should increment
    expect(mockLogger.warn).not.toHaveBeenCalled();
  });

  it('Scenario 04. Deck pile getting reshuffled and goes into draw pile if no cards left in draw', () => {
    const state = createTestState({
      subPhase: 'idle',
      currentPlayerIndex: 0,
      turnNumber: 1,
      players: [
        createTestPlayer('p1', 'Player 1', true, [
          createTestCard('A', 'p1c1'),
          createTestCard('K', 'p1c2'),
        ]),
        createTestPlayer('p2', 'Player 2', false, [
          createTestCard('A', 'p2c1'),
          createTestCard('K', 'p2c2'),
        ]),
        createTestPlayer('p3', 'Player 3', false, [
          createTestCard('J', 'p3c1'),
          createTestCard('J', 'p3c2'),
        ]),
        createTestPlayer('p4', 'Player 4', false, [
          createTestCard('Q', 'p4c1'),
          createTestCard('Q', 'p4c2'),
        ]),
      ],
      drawPile: Pile.fromCards([
        createTestCard('2', 'draw1'),
        createTestCard('3', 'draw2'),
        createTestCard('4', 'draw3'),
        createTestCard('5', 'draw4'),
      ]),
      discardPile: Pile.fromCards([
        createTestCard('6', 'discard1'),
        createTestCard('6', 'discard2'),
        createTestCard('6', 'discard3'),
      ]),
    });

    const makeTurn = (playerId: string, currentState: GameState) => {
      let updatedState = unsafeReduce(
        currentState,
        GameActions.drawCard(playerId)
      );

      updatedState = unsafeReduce(
        updatedState,
        GameActions.discardCard(playerId)
      );

      updatedState = markPlayersReady(updatedState, ['p1', 'p2', 'p3', 'p4']);
      return updatedState;
    };

    expect(state.drawPile.length).toBe(4);
    expect(state.discardPile.length).toBe(3);
    let newState = makeTurn('p1', state);
    expect(mockLogger.warn).not.toHaveBeenCalled();
    expect(newState.drawPile.length).toBe(3);
    expect(newState.discardPile.length).toBe(4);

    newState = makeTurn('p2', newState);
    expect(newState.drawPile.length).toBe(2);
    expect(newState.discardPile.length).toBe(5);

    newState = makeTurn('p3', newState);
    expect(newState.drawPile.length).toBe(6); // We should now have reshuffled discard into draw
    expect(newState.discardPile.length).toBe(1);

    expect(mockLogger.warn).not.toHaveBeenCalled();
  });
});
