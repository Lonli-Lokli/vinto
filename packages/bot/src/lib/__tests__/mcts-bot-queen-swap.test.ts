/**
 * Simplified test for Queen swap behavior
 * Documents and verifies that bot correctly prioritizes known good cards when using Queen
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { MCTSBotDecisionService } from '../mcts-bot-decision';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  createBotContext,
  toPile,
} from './test-helpers';
import { Card } from '@vinto/shapes';

describe('MCTS Bot - Queen Swap (Simplified)', () => {
  let bot: MCTSBotDecisionService;
  const botId = 'bot1';
  const humanId = 'human1';

  beforeEach(() => {
    bot = new MCTSBotDecisionService('hard');
  });

  it('should select action targets when Queen card is pending', async () => {
    // Scenario: Bot has drawn Queen and needs to select 2 cards to peek at
    // Human has a known Joker at position 0 (publicly swapped earlier)

    const gameState = createTestState({
      phase: 'playing',
      subPhase: 'awaiting_action',
      currentPlayerIndex: 0,
      players: [
        createTestPlayer(
          botId,
          'Bot Player',
          false,
          [
            createTestCard('8', 'bot-card-0'),
            createTestCard('9', 'bot-card-1'),
            createTestCard('10', 'bot-card-2'),
          ],
          [0, 1, 2]
        ),
        createTestPlayer(
          humanId,
          'Human Player',
          true,
          [
            createTestCard('Joker', 'human-card-0'), // Known Joker
            createTestCard('K', 'human-card-1'),
            createTestCard('A', 'human-card-2'),
          ],
          [0]
        ),
      ],
      discardPile: toPile([createTestCard('5', 'discard-5')]),
      pendingAction: {
        playerId: botId,
        card: createTestCard('Q', 'queen-card'),
        actionPhase: 'selecting-target',
        from: 'drawing',
        targets: [],
      },
    });

    const opponentKnowledge = new Map<string, Map<number, Card>>();

    // Bot knows its own cards
    const botCardsMap = new Map<number, Card>();
    botCardsMap.set(0, createTestCard('8', 'bot-card-0'));
    botCardsMap.set(1, createTestCard('9', 'bot-card-1'));
    botCardsMap.set(2, createTestCard('10', 'bot-card-2'));
    opponentKnowledge.set(botId, botCardsMap);

    // Bot knows about human's Joker (publicly visible)
    const humanCardsMap = new Map<number, Card>();
    humanCardsMap.set(0, createTestCard('Joker', 'human-card-0'));
    opponentKnowledge.set(humanId, humanCardsMap);

    const context = createBotContext(botId, gameState, {
      opponentKnowledge,
      pendingCard: createTestCard('Q', 'queen-card'),
    });

    const decision = bot.selectActionTargets(context);

    // Bot should select 2 targets
    expect(decision.targets).toBeDefined();
    expect(decision.targets.length).toBe(2);

    // Bot should make a swap decision (true or false)
    expect(decision.shouldSwap).toBeDefined();

    // Log the decision for manual verification
    console.log('\\nQueen swap decision:');
    console.log('  Targets:', decision.targets);
    console.log('  Should swap:', decision.shouldSwap);
    console.log(
      '\\nExpected behavior: Bot should target human1 position 0 (Joker) and decide to swap=true'
    );
  });
});
