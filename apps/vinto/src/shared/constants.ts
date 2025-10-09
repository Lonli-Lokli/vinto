// constants/game-setup.ts
// Shared game configuration and constants

import { CardAction, Rank } from './domain-types';

export interface CardConfig {
  rank: Rank;
  name: string;
  value: number;
  shortDescription: string;
  longDescription: string;
  helpText: string;
  action?: CardAction;
}

export const ALL_RANKS: Rank[] = [
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
  'Joker',
];

export const CARD_CONFIGS: Record<Rank, CardConfig> = {
  '2': {
    rank: '2',
    name: 'Two',
    value: 2,
    shortDescription: '',
    longDescription: '',
    helpText: 'Number card with value 2',
  },
  '3': {
    rank: '3',
    name: 'Three',
    value: 3,
    shortDescription: '',
    longDescription: '',
    helpText: 'Number card with value 3',
  },
  '4': {
    rank: '4',
    name: 'Four',
    value: 4,
    shortDescription: '',
    longDescription: '',
    helpText: 'Number card with value 4',
  },
  '5': {
    rank: '5',
    name: 'Five',
    value: 5,
    shortDescription: '',
    longDescription: '',
    helpText: 'Number card with value 5',
  },
  '6': {
    rank: '6',
    name: 'Six',
    value: 6,
    shortDescription: '',
    longDescription: '',
    helpText: 'Number card with value 6',
  },
  '7': {
    rank: '7',
    name: 'Seven',
    value: 7,
    action: 'peek-own',
    shortDescription: 'Peek 1 of your cards',
    longDescription: 'Peek at one of your own cards',
    helpText:
      'Click on one of your cards to temporarily reveal it and memorize its value. You can skip this action if you prefer.',
  },
  '8': {
    rank: '8',
    name: 'Eight',
    value: 8,
    action: 'peek-own',
    shortDescription: 'Peek 1 of your cards',
    longDescription: 'Peek at one of your own cards',
    helpText:
      'Click on one of your cards to temporarily reveal it and memorize its value. You can skip this action if you prefer.',
  },
  '9': {
    rank: '9',
    name: 'Nine',
    value: 9,
    action: 'peek-opponent',
    shortDescription: 'Peek 1 opponent card',
    longDescription: 'Peek at one card of another player',
    helpText:
      "Click on an opponent's card to temporarily reveal it and see its value. You can skip this action if you prefer.",
  },
  '10': {
    rank: '10',
    name: 'Ten',
    value: 10,
    action: 'peek-opponent',
    shortDescription: 'Peek 1 opponent card',
    longDescription: 'Peek at one card of another player',
    helpText:
      "Click on an opponent's card to temporarily reveal it and see its value. You can skip this action if you prefer.",
  },
  J: {
    rank: 'J',
    name: 'Jack',
    value: 10,
    action: 'swap-cards',
    shortDescription: 'Swap 2 face-down cards',
    longDescription: 'Swap any two facedown cards on the table',
    helpText:
      'Select any two cards to swap their positions - they can be from any players, including yourself. You can reset your selection or skip this action.',
  },
  Q: {
    rank: 'Q',
    name: 'Queen',
    value: 10,
    action: 'peek-and-swap',
    shortDescription: 'Peek 2 cards, swap optional',
    longDescription:
      'Peek at any two cards from two different players, then optionally swap them',
    helpText:
      '1. Peek at two cards from different players (one may be yours, one must be from another player)\n2. After peeking both cards, decide whether to swap them',
  },
  K: {
    rank: 'K',
    name: 'King',
    value: 0,
    action: 'declare-action',
    shortDescription: "Declare any card's action",
    longDescription: 'Declare another card action from any player (7-A)',
    helpText:
      'Choose which card action to execute: 7 (peek own), 8 (peek own), 9/10 (peek opponent), J (swap), Q (peek & swap), A (force draw). You can also declare non-action cards (2-6, K, Joker).',
  },
  A: {
    rank: 'A',
    name: 'Ace',
    value: 1,
    action: 'force-draw',
    shortDescription: 'Force opponent to draw',
    longDescription: 'Force an opponent to draw a penalty card',
    helpText:
      'Select an opponent to force them to draw a penalty card from the deck. This increases their hand size and card total. You can skip this action if you prefer.',
  },
  Joker: {
    rank: 'Joker',
    name: 'Joker',
    value: -1,
    shortDescription: '',
    longDescription: '',
    helpText: 'Joker with value -1',
  },
};

// Helper functions
export function getCardConfig(rank: Rank): CardConfig {
  return CARD_CONFIGS[rank];
}

export function getCardShortDescription(rank: Rank): string {
  return CARD_CONFIGS[rank].shortDescription;
}

export function getCardLongDescription(rank: Rank): string {
  return CARD_CONFIGS[rank].longDescription;
}

export function getCardHelpText(rank: Rank): string {
  return CARD_CONFIGS[rank].helpText;
}

export function getCardValue(rank: Rank): number {
  return CARD_CONFIGS[rank].value;
}

export function getCardName(rank: Rank): string {
  return CARD_CONFIGS[rank].name;
}
export function hasAction(rank: Rank): boolean {
  return CARD_CONFIGS[rank].shortDescription !== '';
}
