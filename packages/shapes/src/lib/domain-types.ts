import { shuffleCards } from './utils';

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
export type BotVersion = 'v1' | 'v2';
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

export class Pile implements Iterable<Card> {
  private _cards: Card[];

  constructor(cards: Iterable<Card> = []) {
    this._cards = Array.from(cards);
  }

  static fromCards(cards: Pile | Iterable<Card> | null | undefined): Pile {
    if (!cards) {
      return new Pile();
    }

    if (cards instanceof Pile) {
      return new Pile(cards._cards);
    }

    return new Pile(cards);
  }

  get length(): number {
    return this._cards.length;
  }

  isEmpty(): boolean {
    return this._cards.length === 0;
  }

  at(index: number): Card | undefined {
    const normalized = index >= 0 ? index : this._cards.length + index;
    if (normalized < 0 || normalized >= this._cards.length) {
      return undefined;
    }
    return this._cards[normalized];
  }

  peekAt(index: number): Card | undefined {
    return this.at(index);
  }

  peekTop(): Card | undefined {
    return this.at(0);
  }

  drawTop(): Card | undefined {
    return this._cards.shift();
  }

  addToTop(card: Card): void {
    this._cards.unshift(card);
  }

  takeAt(index: number): Card | undefined {
    if (index < 0 || index >= this._cards.length) {
      return undefined;
    }
    const [removed] = this._cards.splice(index, 1);
    return removed;
  }

  toArray(): Card[] {
    return [...this._cards];
  }

  reshuffleFrom(otherPile: Pile): void {
    const [otherTopCard, ...cardsToShuffle] = otherPile.toArray();
    const thisTopCard = this.drawTop();
    this._cards = [
      ...(thisTopCard ? [thisTopCard] : []),
      ...shuffleCards([...cardsToShuffle, ...this._cards]),
    ];
    otherPile.replace([otherTopCard]);
  }

  replace(cards: Iterable<Card>): void {
    this._cards = Array.from(cards);
  }

  [Symbol.iterator](): Iterator<Card> {
    return this._cards[Symbol.iterator]();
  }

  toJSON(): Card[] {
    return this.toArray();
  }
}
