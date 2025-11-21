/**
 * Shared test helpers
 */

import {
  Card,
  GameState,
  getCardShortDescription,
  getCardValue,
  logger,
  Pile,
  PlayerState,
  Rank,
} from '@vinto/shapes';
import { GameClient } from '../game-client';
import { GameActions } from '@vinto/engine';
import { vi } from 'vitest';
import { BotAIAdapter } from '../adapters/botAIAdapter';

/**
 * Create a test card with proper value mapping
 */
export function createTestCard(rank: Rank, id: string): Card {
  return {
    id,
    rank,
    value: getCardValue(rank),
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
    drawPile: toPile(createTestDeck()),
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

export function createTestDeck(): Card[] {
  const ranks = [
    'A',
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
  ] as const;
  const suits = ['hearts', 'diamonds', 'clubs', 'spades'];
  const deck: Card[] = [];

  for (const suit of suits) {
    for (const rank of ranks) {
      deck.push(createTestCard(rank, suit));
    }
  }

  return deck;
}

/**
 * Helper: Setup a simpler scenario by injecting specific cards
 * This uses the swap-hand-with-deck action to inject specific cards
 */
export async function setupSimpleScenario(
  players: PlayerState[],
  currentPlayerIndex: number,
  stateOverrides?: Partial<GameState>,
  additionalSetup?: (gameClient: GameClient) => void
) {
  // Create initial state using GameEngine
  const initialState = createTestState({
    players: players,
    subPhase: 'ai_thinking',
  });

  // Create GameClient with initial state
  const gameClient = new GameClient({
    ...initialState,
    ...(stateOverrides ?? {}),
  });

  // Track any errors during state updates
  const errors: string[] = [];
  gameClient.onStateUpdateError((reason) => {
    logger.error(`[SETUP ERROR] State update failed: ${reason}`);
    errors.push(reason);
  });

  // Navigate to correct turn with safety limit
  let turnCount = 0;
  const maxTurns = 10;

  // Navigate to correct turn
  while (
    gameClient.state.currentPlayerIndex !== currentPlayerIndex &&
    turnCount < maxTurns
  ) {
    const currentPlayerId =
      gameClient.state.players[gameClient.state.currentPlayerIndex].id;
    gameClient.dispatch(GameActions.drawCard(currentPlayerId));
    await vi.runAllTimersAsync();
    if (errors.length > 0) {
      throw new Error(`Errors during drawCard: ${errors.join(', ')}`);
    }

    gameClient.dispatch(GameActions.discardCard(currentPlayerId));
    await vi.runAllTimersAsync();
    if (errors.length > 0) {
      throw new Error(`Errors during discardCard: ${errors.join(', ')}`);
    }

    for (const player of gameClient.state.players) {
      gameClient.dispatch(GameActions.playerTossInFinished(player.id));
    }
    await vi.runAllTimersAsync();

    turnCount++;
  }

  if (turnCount >= maxTurns) {
    throw new Error(
      `Failed to reach player index ${currentPlayerIndex} after ${maxTurns} turns`
    );
  }

  additionalSetup?.(gameClient);

  // bots start listening for game events
  const botAdapter = new BotAIAdapter(gameClient, { skipDelays: true });

  return { gameClient, botAdapter };
}
