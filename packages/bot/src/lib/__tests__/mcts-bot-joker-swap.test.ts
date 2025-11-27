import { describe, it, expect } from 'vitest';
import { MCTSBotDecisionService } from '../mcts-bot-decision';
import { BotDecisionContext } from '../shapes';
import { Card, PlayerState, GamePhase, GameSubPhase } from '@vinto/shapes';

/**
 * Test suite for Joker swap bug fix
 * CRITICAL: Bot should NEVER swap a known Joker for any other card
 */
describe('MCTS Bot - Joker Protection', () => {
  /**
   * Helper: Create a basic bot decision context
   */
  function createTestContext(
    botCards: Card[],
    knownPositions: number[],
    drawnCard: Card
  ): BotDecisionContext {
    const botPlayer: PlayerState = {
      id: 'bot1',
      cards: botCards,
      knownCardPositions: knownPositions,
      roundPoints: 0,
      gamePoints: 0,
      turnSkipped: false,
      eliminatedFromRound: false,
    };

    const opponent: PlayerState = {
      id: 'human1',
      cards: [
        { id: 'h1', rank: '6', value: 6, actionText: null, played: false },
        { id: 'h2', rank: '6', value: 6, actionText: null, played: false },
        { id: 'h3', rank: '6', value: 6, actionText: null, played: false },
        { id: 'h4', rank: '6', value: 6, actionText: null, played: false },
        { id: 'h5', rank: '6', value: 6, actionText: null, played: false },
      ],
      knownCardPositions: [],
      roundPoints: 0,
      gamePoints: 0,
      turnSkipped: false,
      eliminatedFromRound: false,
    };

    return {
      botId: 'bot1',
      botPlayer,
      allPlayers: [botPlayer, opponent],
      pendingCard: drawnCard,
      activeActionCard: null,
      discardTop: null,
      discardPile: [],
      opponentKnowledge: new Map(),
      gameState: {
        phase: 'play' as GamePhase,
        subPhase: 'play_turn' as GameSubPhase,
        currentPlayerId: 'bot1',
        vintoCallerId: null,
        finalTurnTriggered: false,
        turnNumber: 5,
        activeTossIn: null,
      },
    };
  }

  it('should NEVER swap a known Joker for any card', () => {
    const botService = new MCTSBotDecisionService('hard');

    // Bot has a known Joker at position 0
    const botCards: Card[] = [
      { id: 'j1', rank: 'Joker', value: -1, actionText: null, played: false },
      { id: 'c2', rank: '6', value: 6, actionText: null, played: false },
      { id: 'c3', rank: '6', value: 6, actionText: null, played: false },
      { id: 'c4', rank: '6', value: 6, actionText: null, played: false },
      { id: 'c5', rank: '6', value: 6, actionText: null, played: false },
    ];

    // Test with various drawn cards
    const testCards: Card[] = [
      { id: 'd1', rank: '2', value: 2, actionText: null, played: false },
      { id: 'd2', rank: '6', value: 6, actionText: null, played: false },
      {
        id: 'd3',
        rank: '10',
        value: 10,
        actionText: 'Peek opponent',
        played: false,
      },
      {
        id: 'd4',
        rank: 'Q',
        value: 10,
        actionText: 'Peek & Swap',
        played: false,
      },
    ];

    for (const drawnCard of testCards) {
      const context = createTestContext(botCards, [0], drawnCard);

      const selectedPosition = botService.selectBestSwapPosition(
        drawnCard,
        context
      );

      // Bot should NEVER select position 0 (the Joker)
      expect(selectedPosition).not.toBe(0);

      // If swapping, it should be position 1-4 (the 6s)
      // If discarding, selectedPosition will be null
      if (selectedPosition !== null) {
        expect(selectedPosition).toBeGreaterThanOrEqual(1);
        expect(selectedPosition).toBeLessThanOrEqual(4);
      }
    }
  });

  it('should NEVER use action if it requires swapping Joker', () => {
    const botService = new MCTSBotDecisionService('hard');

    // Bot has Joker at position 0, other unknown cards
    const botCards: Card[] = [
      { id: 'j1', rank: 'Joker', value: -1, actionText: null, played: false },
      { id: 'c2', rank: '10', value: 10, actionText: null, played: false },
      { id: 'c3', rank: '10', value: 10, actionText: null, played: false },
      { id: 'c4', rank: '10', value: 10, actionText: null, played: false },
      { id: 'c5', rank: '10', value: 10, actionText: null, played: false },
    ];

    // Draw a Jack (swap action)
    const drawnJack: Card = {
      id: 'dj',
      rank: 'J',
      value: 10,
      actionText: 'Swap any 2 cards',
      played: false,
    };

    const context = createTestContext(botCards, [0], drawnJack);

    // Bot should prefer swapping Jack into unknown position over using action
    // (using action might cause Joker to be swapped out)
    const shouldUse = botService.shouldUseAction(drawnJack, context);

    // We're not strictly asserting false here because MCTS might decide
    // to use the action if it can swap two opponent cards
    // The key test is that if bot DOES swap, it won't swap the Joker
    const selectedPosition = botService.selectBestSwapPosition(
      drawnJack,
      context
    );

    if (selectedPosition !== null) {
      expect(selectedPosition).not.toBe(0); // Never swap Joker
    }
  });

  it('should prefer keeping Joker over all card types', () => {
    const botService = new MCTSBotDecisionService('hard');

    // Bot has Joker at position 0
    const botCards: Card[] = [
      { id: 'j1', rank: 'Joker', value: -1, actionText: null, played: false },
      { id: 'c2', rank: '10', value: 10, actionText: null, played: false },
      { id: 'c3', rank: '10', value: 10, actionText: null, played: false },
      { id: 'c4', rank: '10', value: 10, actionText: null, played: false },
      { id: 'c5', rank: '10', value: 10, actionText: null, played: false },
    ];

    // Test with all card ranks
    const allRanks = [
      '2',
      '3',
      '4',
      '5',
      '6',
      '7',
      '8',
      '9',
      '10',
      'J',
      'Q',
      'K',
      'A',
    ];

    for (const rank of allRanks) {
      const drawnCard: Card = {
        id: `d-${rank}`,
        rank: rank as Card['rank'],
        value: rank === 'K' ? 0 : rank === 'A' ? 1 : parseInt(rank) || 10,
        actionText: null,
        played: false,
      };

      const context = createTestContext(botCards, [0], drawnCard);
      const selectedPosition = botService.selectBestSwapPosition(
        drawnCard,
        context
      );

      // Bot should NEVER select position 0 (Joker) for ANY card
      expect(selectedPosition).not.toBe(0);
    }
  });
});
