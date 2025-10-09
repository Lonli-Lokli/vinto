// client/initializeGame.ts
// Utility functions to create initial game state

import { GameState, PlayerState } from '../engine/types';
import { Card } from '../app/shapes';
import { v4 as uuidv4 } from 'uuid';
import { CARD_CONFIGS } from '@/app/constants/game-setup';

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
 * - 4 suits × 13 ranks = 52 cards
 * - Ranks: A, 2, 3, 4, 5, 6, 7, 8, 9, 10, J, Q, K
 * - Each rank appears 4 times
 */
function createDeck(): Card[] {
  const ranks: Card['rank'][] = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
  const deck: Card[] = [];

  // Create 4 of each rank
  for (let i = 0; i < 4; i++) {
    for (const rank of ranks) {
      const value = getRankValue(rank);
      deck.push({
        id: uuidv4(),
        rank,
        value,
        played: false,
      });
    }
  }

  return deck;
}

/**
 * Get numeric value for a card rank
 */
function getRankValue(rank: Card['rank']): number {
  return CARD_CONFIGS[rank].value;  
}

/**
 * Shuffle an array using Fisher-Yates algorithm
 */
function shuffle<T>(array: T[]): T[] {
  const shuffled = [...array];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
}

/**
 * Create initial player states
 */
function createPlayers(settings: GameSettings): PlayerState[] {
  const players: PlayerState[] = [];

  // Human player (always first)
  players.push({
    id: 'human-1',
    name: settings.humanPlayerName || 'You',
    isHuman: true,
    isBot: false,
    cards: [],
    knownCardPositions: [],
    isVintoCaller: false,
    coalitionWith: [],
  });

  // Bot players
  for (let i = 0; i < settings.botCount; i++) {
    players.push({
      id: `bot-${i + 1}`,
      name: `Bot ${i + 1}`,
      isHuman: false,
      isBot: true,
      cards: [],
      knownCardPositions: [],
      isVintoCaller: false,
      coalitionWith: [],
    });
  }

  return players;
}

/**
 * Deal cards to players
 *
 * In Vinto:
 * - Each player gets 4 cards
 * - Cards are dealt face down
 * - Remaining cards become the draw pile
 */
function dealCards(deck: Card[], players: PlayerState[]): { players: PlayerState[]; drawPile: Card[] } {
  const cardsPerPlayer = 4;
  const shuffledDeck = shuffle(deck);

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
  if (totalPlayers < 2 || totalPlayers > 4) {
    throw new Error('Vinto requires 2-4 players');
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
    turnCount: 0,
    phase: 'setup', // Start in setup phase (players peek at 2 cards)
    subPhase: 'idle',
    finalTurnTriggered: false,
    players: dealtPlayers,
    currentPlayerIndex: 0, // Human player starts
    vintoCallerId: null,
    coalitionLeaderId: null,
    drawPile,
    discardPile: [],
    pendingAction: null,
    activeTossIn: null,
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
export function fourPlayerGame(playerName = 'You', difficulty: GameState['difficulty'] = 'moderate'): GameState {
  return initializeGame({
    playerCount: 4,
    botCount: 3,
    humanPlayerName: playerName,
    difficulty,
  });
}
