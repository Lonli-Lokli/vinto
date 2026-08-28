import { Card, NeverError, Rank } from './domain-types';
import { Prng } from './prng';

/**
 * Shuffles with an explicit generator state so the result is reproducible.
 * Callers MUST store the returned `rngState` back into `GameState.rngState`.
 */
export const shuffleCards = (
  deck: Card[],
  rngState: number,
): { deck: Card[]; rngState: number } => {
  const shuffled = Prng.shuffle(deck, rngState);
  return { deck: shuffled.items, rngState: shuffled.state };
};

export const getEnvironment = () => {
  switch (
    process.env.NEXT_PUBLIC_VERCEL_ENV ??
    process.env.VERCEL_ENV ??
    process.env.NODE_ENV
  ) {
    case 'production':
      return 'production';
    default:
      return 'development';
  }
};

export const isRankActionable = (rank: Rank): boolean => {
  switch (rank) {
    case '2':
    case '3':
    case '4':
    case '5':
    case '6':
    case 'Joker':
      return false;
    case '7':
    case '8':
    case '9':
    case '10':
    case 'J':
    case 'Q':
    case 'K':
    case 'A':
      return true;
    default:
      throw new NeverError(rank);
  }
};
