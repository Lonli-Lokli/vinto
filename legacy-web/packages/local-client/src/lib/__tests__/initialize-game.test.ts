import { describe, expect, it } from 'vitest';
import { fourPlayerGame, initializeGame } from '../initializeGame';

/**
 * A recorded game is reproduced from (seed, actions[]). That only holds if the deal
 * itself is a pure function of the seed.
 */
describe('initializeGame', () => {
  const settings = {
    humanPlayerName: 'You',
    difficulty: 'moderate' as const,
  };

  it('produces structurally identical states for the same seed', () => {
    const a = initializeGame({ ...settings, seed: 42 });
    const b = initializeGame({ ...settings, seed: 42 });

    expect(JSON.stringify(b)).toBe(JSON.stringify(a));
    expect(b.gameId).toBe(a.gameId);
    expect(b.rngState).toBe(a.rngState);
    expect(b.drawPile.toArray()).toEqual(a.drawPile.toArray());
    expect(b.players.map((p) => p.cards)).toEqual(
      a.players.map((p) => p.cards),
    );
  });

  it('produces different deals for different seeds', () => {
    const a = initializeGame({ ...settings, seed: 1 });
    const b = initializeGame({ ...settings, seed: 2 });

    expect(b.drawPile.toArray()).not.toEqual(a.drawPile.toArray());
    expect(b.gameId).not.toBe(a.gameId);
  });

  it('derives gameId from the seed', () => {
    expect(initializeGame({ ...settings, seed: 7 }).gameId).toBe('vinto-7');
  });

  it('always deals exactly 4 players with 5 cards each', () => {
    const state = initializeGame({ ...settings, seed: 99 });

    expect(state.players).toHaveLength(4);
    for (const player of state.players) {
      expect(player.cards).toHaveLength(5);
    }
    // 54 cards - 20 dealt = 34 in the draw pile
    expect(state.drawPile.length).toBe(34);
  });

  it('contains no wall-clock or uuid values in the initial state', () => {
    const serialised = JSON.stringify(initializeGame({ ...settings, seed: 3 }));

    expect(serialised).not.toMatch(/\d{13}/); // epoch millis
    expect(serialised).not.toMatch(
      /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i,
    );
  });

  it('generates a seed when none is supplied', () => {
    const a = initializeGame(settings);
    const b = initializeGame(settings);

    expect(a.gameId).toMatch(/^vinto-\d+$/);
    // Practically certain to differ; a fixed seed here would mean every game is identical.
    expect(b.gameId).not.toBe(a.gameId);
  });

  it('fourPlayerGame forwards its seed', () => {
    const viaWrapper = fourPlayerGame('You', 'moderate', 123);
    const direct = initializeGame({ ...settings, seed: 123 });

    expect(JSON.stringify(viaWrapper)).toBe(JSON.stringify(direct));
  });
});
