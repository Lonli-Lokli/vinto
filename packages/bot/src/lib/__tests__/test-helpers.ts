/**
 * Shared test helpers for Bot tests
 */

import {
  Card,
  GameState,
  getCardShortDescription,
  Pile,
  PlayerState,
} from '@vinto/shapes';
import { BotDecisionContext } from '../shapes';

/**
 * Create a test card with proper value mapping
 */
export function createTestCard(rank: Card['rank'], id: string): Card {
  const values: Record<Card['rank'], number> = {
    '2': 2,
    '3': 3,
    '4': 4,
    '5': 5,
    '6': 6,
    '7': 7,
    '8': 8,
    '9': 9,
    '10': 10,
    J: 10,
    Q: 10,
    K: 0,
    A: 1,
    Joker: -1,
  };

  return {
    id,
    rank,
    value: values[rank],
    played: false,
    actionText: getCardShortDescription(rank),
  };
}

/**
 * Create a test player
 */
export function createTestPlayer(
  id: string,
  name: string,
  isHuman: boolean,
  cards: Card[] = [],
  // For bot players in tests, mark all cards as known so they can make decisions
  knownCardPositions: number[] = !isHuman ? cards.map((_, index) => index) : []
): PlayerState {
  // For bot players in tests, mark all cards as known so they can make decisions

  return {
    id,
    name,
    nickname: name,
    isHuman,
    isBot: !isHuman,
    cards: [...cards],
    knownCardPositions,
    isVintoCaller: false,
    coalitionWith: [],
  };
}

/**
 * Convert cards array to Pile
 */
export const toPile = (cards: Card[] | Pile = []): Pile =>
  Pile.fromCards(cards);

/**
 * Create a test game state with sensible defaults
 */
export function createTestState(overrides?: Partial<GameState>): GameState {
  const baseState: GameState = {
    gameId: 'test-game',
    roundNumber: 1,
    turnNumber: 0,
    phase: 'playing',
    subPhase: 'idle',
    finalTurnTriggered: false,
    players: [
      createTestPlayer('p1', 'Player 1', false),
      createTestPlayer('p2', 'Player 2', false),
      createTestPlayer('p3', 'Player 3', false),
      createTestPlayer('p4', 'Player 4', false),
    ],
    currentPlayerIndex: 0,
    vintoCallerId: null,
    coalitionLeaderId: null,
    drawPile: toPile(),
    discardPile: toPile(),
    pendingAction: null,
    activeTossIn: null,
    turnActions: [],
    roundActions: [],
    difficulty: 'hard',
    botVersion: 'v1',
    roundFailedAttempts: [],
  };

  return {
    ...baseState,
    ...overrides,
    players: overrides?.players ?? baseState.players,
  };
}

/**
 * Create a bot decision context for testing
 *
 * IMPORTANT: For test reliability, this ensures the bot knows ALL its own cards
 * with 100% confidence by adding them to opponentKnowledge (which bypasses
 * the probabilistic memory system and ensures deterministic test behavior).
 */
export function createBotContext(
  botId: string,
  gameState: GameState,
  overrides?: Partial<BotDecisionContext>
): BotDecisionContext {
  const botPlayer = gameState.players.find((p) => p.id === botId);
  if (!botPlayer) {
    throw new Error(`Bot player ${botId} not found in game state`);
  }

  // For test reliability: Create opponent knowledge map that includes the bot's own cards
  // This ensures the bot knows its cards with 100% confidence in tests
  const opponentKnowledge = new Map<string, Map<number, Card>>();
  const botCardsMap = new Map<number, Card>();

  // Add all bot's known cards to the knowledge map
  botPlayer.cards.forEach((card, position) => {
    if (botPlayer.knownCardPositions.includes(position)) {
      botCardsMap.set(position, card);
    }
  });

  opponentKnowledge.set(botId, botCardsMap);

  return {
    botId,
    botPlayer,
    gameState,
    allPlayers: gameState.players,
    discardTop: gameState.discardPile.peekTop() || undefined,
    discardPile: gameState.discardPile,
    pendingCard: undefined,
    opponentKnowledge, // Use the knowledge map with bot's own cards
    ...overrides,
  };
}
