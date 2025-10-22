// client/initializeGame.ts
// Utility functions to create initial game state

import {
  Card,
  GameState,
  Pile,
  getCardConfig,
  PlayerState,
  Rank,
} from '@vinto/shapes';
import { v4 as uuidv4 } from 'uuid';

/**
 * Game initialization settings
 */
export interface GameSettings {
  playerCount: number;
  botCount: number;
  humanPlayerName: string;
  difficulty: GameState['difficulty'];
}

/**
 * Create a shuffled deck of Vinto cards
 *
 * Vinto deck composition (for 4 players):
 * - 4 suits × 13 ranks
 * - 2 Jokers
 * - Ranks: A, 2, 3, 4, 5, 6, 7, 8, 9, 10, J, Q, K, Joker
 */
export const createDeck = (): Card[] => {
  const deck: Card[] = [];
  const cardSet = [0, 1, 2, 3];
  const noActionRanks = [2, 3, 4, 5, 6] as const;

  // Number cards 2-6
  for (const rank of noActionRanks) {
    const config = getCardConfig(`${rank}` as Rank);
    cardSet.forEach((no) => {
      deck.push({
        id: `${rank}_${no}`,
        rank: `${rank}`,
        value: config.value,
        played: false,
      });
    });
  }

  // Action cards
  const actionRanks: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'K', 'A'];

  actionRanks.forEach((rank) => {
    const config = getCardConfig(rank);
    cardSet.forEach((no) => {
      deck.push({
        id: `${rank}_${no}`,
        rank: rank,
        value: config.value,
        actionText: config.shortDescription,
        played: false,
      });
    });
  });

  // Jokers
  const jokerConfig = getCardConfig('Joker');
  deck.push(
    { id: 'Joker1', rank: 'Joker', value: jokerConfig.value, played: false },
    { id: 'Joker2', rank: 'Joker', value: jokerConfig.value, played: false }
  );

  return deck;
};

export const shuffleDeck = (deck: Card[]): Card[] => {
  const shuffled = [...deck];

  // Use crypto.getRandomValues for better entropy
  const randomBytes = new Uint32Array(shuffled.length);
  crypto.getRandomValues(randomBytes);

  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor((randomBytes[i] / 0x100000000) * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
};

/**
 * Create initial player states
 */
function createPlayers(_settings: GameSettings): PlayerState[] {
  const players: PlayerState[] = [];

  // Human player (always first)
  players.push({
    id: 'human-1',
    name: 'You',
    isHuman: true,
    isBot: false,
    cards: [],
    knownCardPositions: [],
    isVintoCaller: false,
    coalitionWith: [],
  });

  players.push(
    {
      id: `bot-1`,
      name: `Michelangelo`,
      isHuman: false,
      isBot: true,
      cards: [],
      knownCardPositions: [],
      isVintoCaller: false,
      coalitionWith: [],
    },
    {
      id: `bot-2`,
      name: `Donatello`,
      isHuman: false,
      isBot: true,
      cards: [],
      knownCardPositions: [],
      isVintoCaller: false,
      coalitionWith: [],
    },
    {
      id: `bot-3`,
      name: `Raphael`,
      isHuman: false,
      isBot: true,
      cards: [],
      knownCardPositions: [],
      isVintoCaller: false,
      coalitionWith: [],
    }
  );

  return players;
}

/**
 * Deal cards to players
 *
 * In Vinto:
 * - Each player gets 5 cards
 * - Cards are dealt face down
 * - Remaining cards become the draw pile
 */
function dealCards(
  deck: Card[],
  players: PlayerState[]
): { players: PlayerState[]; drawPile: Card[] } {
  const cardsPerPlayer = 5;
  const shuffledDeck = shuffleDeck(deck);

  let cardIndex = 0;

  // Deal cards to each player
  for (const player of players) {
    player.cards = shuffledDeck.slice(cardIndex, cardIndex + cardsPerPlayer);
    cardIndex += cardsPerPlayer;
  }

  // Remaining cards go to draw pile
  const drawPile = shuffledDeck.slice(cardIndex);

  return { players, drawPile };
}

/**
 * Initialize a new game with the given settings
 *
 * Flow:
 * 1. Create players (human + bots)
 * 2. Create and shuffle deck
 * 3. Deal cards to players
 * 4. Set up draw pile
 * 5. Return initial game state
 */
export function initializeGame(settings: GameSettings): GameState {
  // Validate settings
  const totalPlayers = 1 + settings.botCount; // 1 human + N bots
  if (totalPlayers < 4 || totalPlayers > 5) {
    throw new Error('Vinto requires 4-5 players');
  }

  // Create players
  const players = createPlayers(settings);

  // Create and shuffle deck
  const deck = createDeck();

  // Deal cards
  const { players: dealtPlayers, drawPile } = dealCards(deck, players);

  // Create initial game state
  const gameState: GameState = {
    gameId: uuidv4(),
    roundNumber: 1,
    turnNumber: 1,
    phase: 'setup', // Start in setup phase (players peek at 2 cards)
    subPhase: 'idle',
    finalTurnTriggered: false,
    players: dealtPlayers,
    currentPlayerIndex: 0, // Human player starts
    vintoCallerId: null,
    coalitionLeaderId: null,
    drawPile: new Pile(drawPile),
    discardPile: new Pile(),
    pendingAction: null,
    activeTossIn: null,
    recentActions: [],
    difficulty: settings.difficulty,
  };

  return gameState;
}

/**
 * Quick start: Initialize a standard 2-player game (1 human vs 1 bot)
 */
export function quickStartGame(playerName = 'You'): GameState {
  return initializeGame({
    playerCount: 2,
    botCount: 1,
    humanPlayerName: playerName,
    difficulty: 'moderate',
  });
}

/**
 * Initialize a 4-player game (1 human vs 3 bots)
 */
export function fourPlayerGame(
  playerName = 'You',
  difficulty: GameState['difficulty'] = 'moderate'
): GameState {
  return initializeGame({
    playerCount: 4,
    botCount: 3,
    humanPlayerName: playerName,
    difficulty,
  });
}
