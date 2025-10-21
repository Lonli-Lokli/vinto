/**
 * Shared test helpers for GameEngine tests
 */

import {
  Card,
  GameState,
  getCardShortDescription,
  Pile,
  PlayerState,
} from '@vinto/shapes';
import { GameEngine } from '../game-engine';
import copy from 'fast-copy';
import { GameActions } from '../game-actions';

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
  cards: Card[] = []
): PlayerState {
  return {
    id,
    name,
    isHuman,
    isBot: !isHuman,
    cards: [...cards],
    knownCardPositions: [],
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
    turnCount: 0,
    phase: 'playing',
    subPhase: 'idle',
    finalTurnTriggered: false,
    players: [
      createTestPlayer('p1', 'Player 1', true),
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

export function markPlayersReady(
  state: GameState,
  playerIds: string[]
): GameState {
  let newState = copy(state);
  for (const playerId of playerIds) {
    newState = GameEngine.reduce(
      newState,
      GameActions.playerTossInFinished(playerId)
    );
  }

  return newState;
}
