// client/initializeGame.ts
// Utility functions to create initial game state

import {
  Card,
  GameRecordingSettings,
  GameState,
  Pile,
  Prng,
  getCardConfig,
  PlayerState,
  Rank,
  shuffleCards,
} from '@vinto/shapes';

/**
 * Game initialization settings.
 *
 * Every Vinto game has exactly 4 players (1 human + 3 bots), so there is no
 * player-count option.
 */
export interface GameSettings {
  humanPlayerName: string;
  difficulty: GameState['difficulty'];
  /**
   * Seed for the deal (unsigned 32-bit). Omit to have one generated; the resulting
   * `rngState` and `gameId` are derived from it, so the same seed always reproduces
   * the same game.
   */
  seed?: number;
}

/** Prefix of the deterministic `gameId`; the remainder is the seed. */
const GAME_ID_PREFIX = 'vinto-';

/**
 * Reconstructs the settings a recording needs from a state. `gameId` is defined as
 * `vinto-<seed>` (see `initializeGame`), so the seed round-trips through the state
 * without needing a separate field. The seed is informational — a recording embeds the
 * full `initialState`, so replay never depends on re-running the deal.
 */
export function recordingSettingsFromState(
  state: GameState,
): GameRecordingSettings {
  const human = state.players.find((player) => player.isHuman);
  const seed = Number(state.gameId.slice(GAME_ID_PREFIX.length));

  return {
    humanPlayerName: human?.nickname ?? 'You',
    difficulty: state.difficulty,
    seed:
      state.gameId.startsWith(GAME_ID_PREFIX) && Number.isInteger(seed)
        ? seed
        : 0,
  };
}

/**
 * Picks a seed outside the engine. The engine itself never touches ambient randomness —
 * that is what makes a game replayable from `(seed, actions[])`.
 */
export function generateSeed(): number {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return bytes[0] >>> 0;
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
    { id: 'Joker2', rank: 'Joker', value: jokerConfig.value, played: false },
  );

  return deck;
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
    nickname: 'You',
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
      name: `Raphael`,
      nickname: 'Raph',
      isHuman: false,
      isBot: true,
      cards: [],
      knownCardPositions: [0, 1],
      isVintoCaller: false,
      coalitionWith: [],
    },
    {
      id: `bot-2`,
      name: `Michelangelo`,
      nickname: 'Mikey',

      isHuman: false,
      isBot: true,
      cards: [],
      knownCardPositions: [0, 1],
      isVintoCaller: false,
      coalitionWith: [],
    },
    {
      id: `bot-3`,
      name: `Donatello`,
      nickname: 'Don',

      isHuman: false,
      isBot: true,
      cards: [],
      knownCardPositions: [0, 1],
      isVintoCaller: false,
      coalitionWith: [],
    },
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
  players: PlayerState[],
  rngState: number,
): { players: PlayerState[]; drawPile: Card[]; rngState: number } {
  const cardsPerPlayer = 5;
  const shuffled = shuffleCards(deck, rngState);

  let cardIndex = 0;

  // Deal cards to each player
  for (const player of players) {
    player.cards = shuffled.deck.slice(cardIndex, cardIndex + cardsPerPlayer);
    cardIndex += cardsPerPlayer;
  }

  // Remaining cards go to draw pile
  const drawPile = shuffled.deck.slice(cardIndex);

  return { players, drawPile, rngState: shuffled.rngState };
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
  const seed = Prng.seed(settings.seed ?? generateSeed());

  // Create players — always exactly 4 (1 human + 3 bots)
  const players = createPlayers(settings);

  // Create the deck in fixed order, then shuffle it with the seeded generator
  const deck = createDeck();

  // Deal cards
  const {
    players: dealtPlayers,
    drawPile,
    rngState,
  } = dealCards(deck, players, seed);

  // Create initial game state
  const gameState: GameState = {
    gameId: `vinto-${seed}`,
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
    turnActions: [],
    roundActions: [],
    difficulty: settings.difficulty,
    roundFailedAttempts: [],
    rngState,
  };

  return gameState;
}

/**
 * Initialize a 4-player game (1 human vs 3 bots).
 *
 * Pass `seed` to reproduce a specific game; omit it for a fresh one.
 */
export function fourPlayerGame(
  playerName = 'You',
  difficulty: GameState['difficulty'] = 'moderate',
  seed?: number,
): GameState {
  return initializeGame({
    humanPlayerName: playerName,
    difficulty,
    seed,
  });
}
