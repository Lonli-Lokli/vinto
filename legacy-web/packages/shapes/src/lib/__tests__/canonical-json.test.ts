import { describe, expect, it } from 'vitest';
import {
  canonicalizeGameState,
  hashCanonicalString,
  hashGameState,
} from '../canonical-json';
import { Pile } from '../domain-types';
import { GameState, PlayerState } from '../game-state-types';

function card(
  id: string,
  rank: PlayerState['cards'][number]['rank'],
  value: number,
) {
  return { id, rank, value, played: false };
}

function player(id: string, overrides: Partial<PlayerState> = {}): PlayerState {
  return {
    id,
    name: id,
    nickname: id,
    isHuman: false,
    isBot: true,
    cards: [card(`${id}-c1`, '5', 5)],
    knownCardPositions: [0],
    isVintoCaller: false,
    coalitionWith: [],
    ...overrides,
  };
}

function state(overrides: Partial<GameState> = {}): GameState {
  return {
    gameId: 'vinto-1',
    roundNumber: 1,
    turnNumber: 1,
    phase: 'playing',
    subPhase: 'idle',
    finalTurnTriggered: false,
    players: [player('p1'), player('p2'), player('p3'), player('p4')],
    currentPlayerIndex: 0,
    vintoCallerId: null,
    coalitionLeaderId: null,
    drawPile: Pile.fromCards([card('d1', '7', 7)]),
    discardPile: Pile.fromCards([card('x1', '9', 9)]),
    pendingAction: null,
    activeTossIn: null,
    turnActions: [],
    roundActions: [],
    roundFailedAttempts: [],
    difficulty: 'moderate',
    rngState: 12345,
    ...overrides,
  };
}

const historyEntry = {
  playerId: 'p1',
  playerName: 'p1',
  description: 'Player 1 drew a card',
  timestamp: 0,
  turnNumber: 1,
  roundNumber: 1,
};

describe('canonicalizeGameState', () => {
  it('is independent of property insertion order', () => {
    const a = state();
    // Rebuild with keys in a different order.
    const reordered = Object.fromEntries(
      Object.entries(a).reverse(),
    ) as unknown as GameState;

    expect(canonicalizeGameState(reordered)).toBe(canonicalizeGameState(a));
  });

  it('emits no whitespace and sorts keys', () => {
    const canonical = canonicalizeGameState(state());

    expect(canonical).not.toMatch(/\s/);
    expect(canonical.indexOf('"currentPlayerIndex"')).toBeLessThan(
      canonical.indexOf('"gameId"'),
    );
  });

  it('serialises a Pile as a plain array, top card first', () => {
    const canonical = canonicalizeGameState(
      state({
        drawPile: Pile.fromCards([card('top', '2', 2), card('next', '3', 3)]),
      }),
    );

    expect(canonical).toContain('"drawPile":[{"id":"top"');
    const topIndex = canonical.indexOf('"top"');
    const nextIndex = canonical.indexOf('"next"');
    expect(topIndex).toBeLessThan(nextIndex);
  });

  it('omits undefined but keeps null', () => {
    const canonical = canonicalizeGameState(
      state({
        vintoCallerId: null,
        players: [
          player('p1', {
            cards: [{ ...card('c', '5', 5), actionText: undefined }],
          }),
          player('p2'),
          player('p3'),
          player('p4'),
        ],
      }),
    );

    expect(canonical).toContain('"vintoCallerId":null');
    expect(canonical).not.toContain('actionText');
  });

  describe('sensitivity — game logic must change the hash', () => {
    it.each([
      ['rngState', state({ rngState: 999 })],
      [
        'a card id',
        state({
          players: [
            player('p1', { cards: [card('other', '5', 5)] }),
            player('p2'),
            player('p3'),
            player('p4'),
          ],
        }),
      ],
      [
        'knownCardPositions',
        state({
          players: [
            player('p1', { knownCardPositions: [0, 1] }),
            player('p2'),
            player('p3'),
            player('p4'),
          ],
        }),
      ],
      ['currentPlayerIndex', state({ currentPlayerIndex: 2 })],
      [
        'the draw pile',
        state({ drawPile: Pile.fromCards([card('zz', '4', 4)]) }),
      ],
    ])('%s changes the canonical form', (_label, mutated) => {
      expect(canonicalizeGameState(mutated)).not.toBe(
        canonicalizeGameState(state()),
      );
    });

    it('opponentKnowledge is included — the engine writes it deterministically', () => {
      const withKnowledge = state({
        players: [
          player('p1', {
            opponentKnowledge: { p2: { knownCards: { 0: card('k', '5', 5) } } },
          }),
          player('p2'),
          player('p3'),
          player('p4'),
        ],
      });

      expect(canonicalizeGameState(withKnowledge)).not.toBe(
        canonicalizeGameState(state()),
      );
      expect(canonicalizeGameState(withKnowledge)).toContain(
        'opponentKnowledge',
      );
    });
  });

  describe('exclusions — presentation must not affect the hash', () => {
    it('ignores turnActions and roundActions entirely', () => {
      const withHistory = state({
        turnActions: [historyEntry],
        roundActions: [historyEntry],
      });

      expect(canonicalizeGameState(withHistory)).toBe(
        canonicalizeGameState(state()),
      );
      expect(canonicalizeGameState(withHistory)).not.toContain('turnActions');
    });

    it('ignores differing description text, so UI copy is not part of the contract', () => {
      const english = state({ turnActions: [historyEntry] });
      const translated = state({
        turnActions: [
          { ...historyEntry, description: 'Spieler 1 zog eine Karte' },
        ],
      });

      expect(canonicalizeGameState(translated)).toBe(
        canonicalizeGameState(english),
      );
    });

    it('ignores botMemory', () => {
      const withMemory = state({
        players: [
          player('p1', {
            botMemory: {
              playerId: 'p1',
              difficulty: 'moderate',
              // Deliberately fractional: botMemory holds floats, which is exactly why
              // it cannot participate in an integer-only canonical form.
              knownCards: { 0: { confidence: 0.75 } },
            } as unknown as PlayerState['botMemory'],
          }),
          player('p2'),
          player('p3'),
          player('p4'),
        ],
      });

      expect(canonicalizeGameState(withMemory)).toBe(
        canonicalizeGameState(state()),
      );
    });
  });

  describe('integer-only assertion', () => {
    it('throws on a fractional number, naming the path', () => {
      const fractional = state({ rngState: 1.5 });

      expect(() => canonicalizeGameState(fractional)).toThrow(TypeError);
      expect(() => canonicalizeGameState(fractional)).toThrow(/rngState/);
    });

    it('throws on NaN and Infinity', () => {
      expect(() => canonicalizeGameState(state({ turnNumber: NaN }))).toThrow(
        TypeError,
      );
      expect(() =>
        canonicalizeGameState(state({ turnNumber: Infinity })),
      ).toThrow(TypeError);
    });

    it('accepts negative integers (Joker is -1)', () => {
      const withJoker = state({
        players: [
          player('p1', { cards: [card('j', 'Joker', -1)] }),
          player('p2'),
          player('p3'),
          player('p4'),
        ],
      });

      expect(() => canonicalizeGameState(withJoker)).not.toThrow();
      expect(canonicalizeGameState(withJoker)).toContain('"value":-1');
    });
  });
});

describe('hashGameState', () => {
  it('is a lowercase 64-character hex digest', async () => {
    const hash = await hashGameState(state());

    expect(hash).toMatch(/^[0-9a-f]{64}$/);
  });

  it('matches a known SHA-256 vector', async () => {
    // SHA-256 of the empty string — pins the digest itself, not just its shape.
    expect(await hashCanonicalString('')).toBe(
      'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    );
  });

  it('is stable across calls and sensitive to game logic', async () => {
    const [a, b, changed] = await Promise.all([
      hashGameState(state()),
      hashGameState(state()),
      hashGameState(state({ rngState: 7 })),
    ]);

    expect(b).toBe(a);
    expect(changed).not.toBe(a);
  });
});
