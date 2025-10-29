// Test to verify toss-in state behavior, especially currentPlayerIndex
// This test verifies that during toss-in phase, currentPlayerIndex remains
// at the player who triggered the toss-in, not advancing until toss-in completes

import { describe, it, expect } from 'vitest';
import { GameEngine } from '../game-engine';
import { GameActions } from '../game-actions';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  toPile,
  unsafeReduce,
} from './test-helpers';

describe('GameEngine - Toss-In State Management', () => {
  it('should maintain currentPlayerIndex during toss-in phase until all players ready', () => {
    // Setup: Player 0 (human) has a 7, Player 1 (bot) has a 7
    const state = createTestState({
      currentPlayerIndex: 0,
      turnNumber: 1,
      subPhase: 'idle',
      players: [
        createTestPlayer('human-1', 'Human', true, [
          createTestCard('7', 'h1'),
          createTestCard('3', 'h2'),
        ]),
        createTestPlayer('bot-1', 'Bot 1', false, [
          createTestCard('7', 'b1'),
          createTestCard('4', 'b2'),
        ]),
        createTestPlayer('bot-2', 'Bot 2', false, [
          createTestCard('5', 'b3'),
          createTestCard('6', 'b4'),
        ]),
      ],
      drawPile: toPile([
        createTestCard('2', 'd1'),
        createTestCard('8', 'd2'),
        createTestCard('9', 'd3'),
      ]),
    });

    // Human draws a card
    let newState = unsafeReduce(state, GameActions.drawCard('human-1'));
    expect(newState.subPhase).toBe('choosing');
    expect(newState.currentPlayerIndex).toBe(0); // Still human's turn

    // Human discards the drawn card (triggers toss-in)
    newState = unsafeReduce(newState, GameActions.discardCard('human-1'));

    // Verify toss-in state
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn).not.toBeNull();
    expect(newState.currentPlayerIndex).toBe(0); // CRITICAL: Still pointing to human!

    // Bot 1 tosses in their 7
    newState = unsafeReduce(
      newState,
      GameActions.participateInTossIn('bot-1', [0])
    );
    expect(newState.currentPlayerIndex).toBe(0); // STILL pointing to human
    expect(newState.subPhase).toBe('toss_queue_active');

    // Bot 1 marks ready
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-1')
    );
    expect(newState.currentPlayerIndex).toBe(0); // STILL pointing to human

    // Bot 2 marks ready
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('bot-2')
    );
    expect(newState.currentPlayerIndex).toBe(0); // STILL pointing to human

    // Human marks ready - this should trigger turn advancement
    newState = unsafeReduce(
      newState,
      GameActions.playerTossInFinished('human-1')
    );

    // Now currentPlayerIndex should advance to next player (bot-1)
    expect(newState.currentPlayerIndex).toBe(1);
    expect(newState.subPhase).toBe('ai_thinking'); // Bot's turn
  });

  it('should allow bots to participate in toss-in when human triggers it', () => {
    // Setup: Player 0 (human) discards a King, Players 1-2 (bots) have Kings
    const state = createTestState({
      currentPlayerIndex: 0,
      turnNumber: 1,
      subPhase: 'toss_queue_active',
      players: [
        createTestPlayer('human-1', 'Human', true, [
          createTestCard('2', 'h1'),
          createTestCard('3', 'h2'),
        ]),
        createTestPlayer('bot-1', 'Bot 1', false, [
          createTestCard('K', 'b1k'),
          createTestCard('4', 'b2'),
        ]),
        createTestPlayer('bot-2', 'Bot 2', false, [
          createTestCard('K', 'b2k'),
          createTestCard('6', 'b4'),
        ]),
      ],
      activeTossIn: {
        ranks: ['K'],
        initiatorId: 'human-1',
        originalPlayerIndex: 0,
        participants: [],
        queuedActions: [],
        waitingForInput: true,
        playersReadyForNextTurn: [],
      },
    });

    // Verify initial state
    expect(state.currentPlayerIndex).toBe(0); // Human's turn
    expect(state.subPhase).toBe('toss_queue_active');

    // Bot 1 should be able to toss in
    let newState = unsafeReduce(
      state,
      GameActions.participateInTossIn('bot-1', [0])
    );
    expect(newState.activeTossIn?.queuedActions.length).toBe(1);
    expect(newState.activeTossIn?.queuedActions[0].playerId).toBe('bot-1');
    expect(newState.currentPlayerIndex).toBe(0); // Still human's turn

    // Bot 2 should be able to toss in
    newState = unsafeReduce(
      newState,
      GameActions.participateInTossIn('bot-2', [0])
    );
    expect(newState.activeTossIn?.queuedActions.length).toBe(2);
    expect(newState.activeTossIn?.queuedActions[1].playerId).toBe('bot-2');
    expect(newState.currentPlayerIndex).toBe(0); // STILL human's turn

    // All players mark ready
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
      GameActions.playerTossInFinished('human-1')
    );

    newState = unsafeReduce(newState, GameActions.playCardAction('bot-1'));

    // Should now be processing queued actions
    expect(newState.subPhase).toBe('awaiting_action');
    expect(newState.pendingAction).not.toBeNull();
    expect(newState.pendingAction?.playerId).toBe('bot-1'); // First queued action
  });

  it('should handle all-bot toss-in scenario correctly', () => {
    // This is the critical test: What happens when a bot triggers toss-in
    // and other bots need to participate?
    const state = createTestState({
      currentPlayerIndex: 0,
      turnNumber: 1,
      subPhase: 'ai_thinking', // Bot's turn
      players: [
        createTestPlayer('bot-1', 'Bot 1', false, [
          createTestCard('Q', 'b1q'),
          createTestCard('3', 'b1c2'),
        ]),
        createTestPlayer('bot-2', 'Bot 2', false, [
          createTestCard('2', 'b2c1'), // Has matching '2'
          createTestCard('4', 'b2c2'),
        ]),
        createTestPlayer('bot-3', 'Bot 3', false, [
          createTestCard('5', 'b3c1'),
          createTestCard('6', 'b3c2'),
        ]),
      ],
      drawPile: toPile([
        createTestCard('2', 'd1'), // This will be drawn
        createTestCard('8', 'd2'),
        createTestCard('9', 'd3'),
      ]),
    });

    // Bot 1 draws a '2'
    let newState = unsafeReduce(state, GameActions.drawCard('bot-1'));
    expect(newState.subPhase).toBe('choosing');
    expect(newState.currentPlayerIndex).toBe(0);

    // Bot 1 discards the '2' (triggers toss-in for rank '2')
    newState = unsafeReduce(newState, GameActions.discardCard('bot-1'));

    // Verify toss-in state
    expect(newState.subPhase).toBe('toss_queue_active');
    expect(newState.activeTossIn).not.toBeNull();
    expect(newState.activeTossIn?.ranks).toContain('2');
    expect(newState.currentPlayerIndex).toBe(0); // Still bot-1

    // Bot 2 tosses in their '2'
    newState = unsafeReduce(
      newState,
      GameActions.participateInTossIn('bot-2', [0])
    );
    expect(newState.activeTossIn?.queuedActions.length).toBe(0); // '2' is not an action card
    expect(newState.currentPlayerIndex).toBe(0); // STILL bot-1

    // All bots mark ready
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

    // Turn should advance to bot-2, no queued actions
    expect(newState.currentPlayerIndex).toBe(1);
    expect(newState.subPhase).toBe('ai_thinking'); // Bot-2's turn, no actions queued
  });
});
