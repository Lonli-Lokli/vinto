import { describe, expect, it } from 'vitest';
import { MCTSBotDecisionService } from '../mcts-bot-decision';
import { shouldCallVintoByScore } from '../vinto-call-rule';
import {
  createBotContext,
  createTestCard,
  createTestPlayer,
  createTestState,
} from './test-helpers';

/**
 * A Vinto call is the only way a game ends, so this rule decides whether games terminate
 * at all. The rule: call when your fully-known hand is worth zero or less.
 */

function contextFor(
  cards: { rank: Parameters<typeof createTestCard>[0]; id: string }[],
  options: { knownPositions?: number[]; turnNumber?: number } = {},
) {
  const botCards = cards.map((card) => createTestCard(card.rank, card.id));
  const bot = createTestPlayer('bot1', 'Bot 1', false, botCards);
  bot.knownCardPositions =
    options.knownPositions ?? botCards.map((_, index) => index);

  const state = createTestState({
    phase: 'playing',
    subPhase: 'toss_queue_active',
    turnNumber: options.turnNumber ?? 12,
    currentPlayerIndex: 0,
    players: [
      bot,
      createTestPlayer('p2', 'P2', false, [createTestCard('5', 'p2c1')]),
      createTestPlayer('p3', 'P3', false, [createTestCard('5', 'p3c1')]),
      createTestPlayer('p4', 'P4', false, [createTestCard('5', 'p4c1')]),
    ],
  });

  return createBotContext('bot1', state);
}

describe('shouldCallVintoByScore', () => {
  it('calls on a fully known hand worth zero or less', () => {
    // Joker (-1) + King (0) = -1
    expect(
      shouldCallVintoByScore(
        contextFor([
          { rank: 'Joker', id: 'j' },
          { rank: 'K', id: 'k' },
        ]),
      ),
    ).toBe(true);
  });

  it('calls on exactly zero — the coalition must beat the caller strictly', () => {
    expect(shouldCallVintoByScore(contextFor([{ rank: 'K', id: 'k' }]))).toBe(
      true,
    );
  });

  it('does not call on a positive score', () => {
    // Ace is 1, which is more than zero.
    expect(shouldCallVintoByScore(contextFor([{ rank: 'A', id: 'a' }]))).toBe(
      false,
    );
    expect(shouldCallVintoByScore(contextFor([{ rank: '5', id: '5' }]))).toBe(
      false,
    );
  });

  it('does not call when any own card is unknown', () => {
    // A known Joker and four unseen cards looks like -1 but is not: this is the case a
    // naive "sum the cards you know" rule gets confidently wrong.
    const context = contextFor(
      [
        { rank: 'Joker', id: 'j' },
        { rank: '5', id: 'c2' },
        { rank: '5', id: 'c3' },
        { rank: '5', id: 'c4' },
        { rank: '5', id: 'c5' },
      ],
      { knownPositions: [0] },
    );

    expect(shouldCallVintoByScore(context)).toBe(false);
  });

  it('does not call in the opening', () => {
    expect(
      shouldCallVintoByScore(
        contextFor([{ rank: 'K', id: 'k' }], { turnNumber: 3 }),
      ),
    ).toBe(false);
  });

  it('does not call once someone else has called', () => {
    const context = contextFor([{ rank: 'K', id: 'k' }]);
    const called = {
      ...context,
      gameState: { ...context.gameState, vintoCallerId: 'p2' },
    };

    expect(shouldCallVintoByScore(called)).toBe(false);
  });

  it('honours a custom threshold', () => {
    const context = contextFor([{ rank: '2', id: '2' }]); // score 2
    expect(shouldCallVintoByScore(context, 0)).toBe(false);
    expect(shouldCallVintoByScore(context, 2)).toBe(true);
  });
});

describe('MCTS bot Vinto decision', () => {
  const winning = () =>
    contextFor([
      { rank: 'Joker', id: 'j' },
      { rank: 'K', id: 'k' },
    ]);
  const losing = () => contextFor([{ rank: '10', id: 't' }]);

  it.each(['easy', 'moderate', 'hard'] as const)(
    'MCTS bot (%s) calls on a winning hand and not otherwise',
    (difficulty) => {
      const service = new MCTSBotDecisionService(difficulty);
      expect(service.shouldCallVinto(winning())).toBe(true);
      expect(service.shouldCallVinto(losing())).toBe(false);
    },
  );

});
