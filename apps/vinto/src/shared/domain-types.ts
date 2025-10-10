// types/game.ts
export interface Card {
  id: string;
  rank: Rank;
  value: number;
  actionText?: string;
  played: boolean;
}

export type Rank =
  | '2'
  | '3'
  | '4'
  | '5'
  | '6'
  | '7'
  | '8'
  | '9'
  | '10'
  | 'J'
  | 'Q'
  | 'K'
  | 'A'
  | 'Joker';

export type Difficulty = 'easy' | 'moderate' | 'hard';
export type CardAction =
  | 'peek-own'
  | 'peek-opponent'
  | 'peek-and-swap'
  | 'swap-cards'
  | 'force-draw'
  | 'declare-action';

export class NeverError extends Error {
  constructor(value: never) {
    super(`NeverError: Unexpected value ${value}`);
  }
}
