// opponent-modeler.test.ts
// Unit tests for OpponentModeler service

import { describe, it, expect, beforeEach } from 'vitest';
import { OpponentModeler, ObservedAction } from '../opponent-modeler';
import { Card } from '@vinto/shapes';

describe('OpponentModeler', () => {
  let modeler: OpponentModeler;
  const playerId = 'player-1';

  beforeEach(() => {
    modeler = new OpponentModeler();
    modeler.initializePlayer(playerId);
  });

  describe('initialization', () => {
    it('should initialize a player with default beliefs', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      expect(beliefs).toBeDefined();
      expect(beliefs?.playerId).toBe(playerId);
      expect(beliefs?.estimatedScore).toBe(25); // Default average
      expect(beliefs?.vintoReadiness).toBe(0.0);
      expect(beliefs?.cardBeliefs.size).toBe(0);
    });

    it('should not reinitialize existing player', () => {
      modeler.initializePlayer(playerId);
      const beliefs1 = modeler.getPlayerBeliefs(playerId);
      modeler.initializePlayer(playerId);
      const beliefs2 = modeler.getPlayerBeliefs(playerId);
      expect(beliefs1).toBe(beliefs2); // Same reference
    });
  });

  describe('swap-from-discard inference', () => {
    it('should infer minimum value when player swaps from discard', () => {
      const action: ObservedAction = {
        type: 'swap-from-discard',
        playerId,
        card: { rank: '5', suit: 'hearts', id: 'card-1' } as Card,
        position: 2,
      };

      modeler.handleObservedAction(action);

      const belief = modeler.getBelief(playerId, 2);
      expect(belief).toBeDefined();
      expect(belief?.minValue).toBe(6); // Card value 5 + 1
      expect(belief?.confidence).toBe(0.8);
      expect(belief?.reason).toContain('5');
    });

    it('should handle swap with action card (value 10)', () => {
      const action: ObservedAction = {
        type: 'swap-from-discard',
        playerId,
        card: { rank: 'Q', suit: 'hearts', id: 'card-1' } as Card,
        position: 0,
      };

      modeler.handleObservedAction(action);

      const belief = modeler.getBelief(playerId, 0);
      expect(belief).toBeDefined();
      expect(belief?.minValue).toBe(11); // Queen value 10 + 1
    });

    it('should handle missing position gracefully', () => {
      const action: ObservedAction = {
        type: 'swap-from-discard',
        playerId,
        card: { rank: '5', suit: 'hearts', id: 'card-1' } as Card,
        // position undefined
      };

      expect(() => modeler.handleObservedAction(action)).not.toThrow();
      const beliefs = modeler.getPlayerBeliefs(playerId);
      expect(beliefs?.cardBeliefs.size).toBe(0);
    });
  });

  describe('discard-drawn inference', () => {
    it('should reduce estimated score when player discards drawn card', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      const initialScore = beliefs!.estimatedScore;

      const action: ObservedAction = {
        type: 'discard-drawn',
        playerId,
        card: { rank: '6', suit: 'hearts', id: 'card-1' } as Card,
      };

      modeler.handleObservedAction(action);

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      expect(updatedBeliefs!.estimatedScore).toBe(initialScore - 2);
    });

    it('should not go below 0 estimated score', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      beliefs!.estimatedScore = 1;

      const action: ObservedAction = {
        type: 'discard-drawn',
        playerId,
        card: { rank: '6', suit: 'hearts', id: 'card-1' } as Card,
      };

      modeler.handleObservedAction(action);

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      expect(updatedBeliefs!.estimatedScore).toBe(0);
    });
  });

  describe('action usage inference', () => {
    it('should decrease readiness for peek actions', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      beliefs!.vintoReadiness = 0.5;

      const action: ObservedAction = {
        type: 'use-action',
        playerId,
        card: { rank: '7', suit: 'hearts', id: 'card-1' } as Card,
      };

      modeler.handleObservedAction(action);

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      expect(updatedBeliefs!.vintoReadiness).toBeLessThan(0.5);
    });

    it('should increase readiness for swap actions (Jack)', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      beliefs!.vintoReadiness = 0.3;

      const action: ObservedAction = {
        type: 'use-action',
        playerId,
        card: { rank: 'J', suit: 'hearts', id: 'card-1' } as Card,
      };

      modeler.handleObservedAction(action);

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      expect(updatedBeliefs!.vintoReadiness).toBeGreaterThan(0.3);
    });

    it('should increase readiness for swap actions (Queen)', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      beliefs!.vintoReadiness = 0.3;

      const action: ObservedAction = {
        type: 'use-action',
        playerId,
        card: { rank: 'Q', suit: 'hearts', id: 'card-1' } as Card,
      };

      modeler.handleObservedAction(action);

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      expect(updatedBeliefs!.vintoReadiness).toBeGreaterThan(0.3);
    });
  });

  describe('toss-in inference', () => {
    it('should record exact rank knowledge from toss-in', () => {
      const action: ObservedAction = {
        type: 'toss-in',
        playerId,
        card: { rank: '5', suit: 'hearts', id: 'card-1' } as Card,
        position: 3,
      };

      modeler.handleObservedAction(action);

      const belief = modeler.getBelief(playerId, 3);
      expect(belief).toBeDefined();
      expect(belief?.likelyRanks).toEqual(['5']);
      expect(belief?.confidence).toBe(1.0); // Perfect knowledge
      expect(belief?.reason).toContain('Tossed in 5');
    });
  });

  describe('peek-own inference', () => {
    it('should decrease readiness when player peeks own cards', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      beliefs!.vintoReadiness = 0.5;

      const action: ObservedAction = {
        type: 'peek-own',
        playerId,
        card: { rank: '7', suit: 'hearts', id: 'card-1' } as Card,
      };

      modeler.handleObservedAction(action);

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      expect(updatedBeliefs!.vintoReadiness).toBeLessThan(0.5);
    });
  });

  describe('swap-own inference', () => {
    it('should increase readiness when player swaps own cards', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      beliefs!.vintoReadiness = 0.3;

      const action: ObservedAction = {
        type: 'swap-own',
        playerId,
      };

      modeler.handleObservedAction(action);

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      expect(updatedBeliefs!.vintoReadiness).toBeGreaterThan(0.3);
    });
  });

  describe('Vinto readiness tracking', () => {
    it('should calculate readiness based on estimated score', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      beliefs!.estimatedScore = 10; // Low score = high readiness
      beliefs!.vintoReadiness = 0; // Start at 0

      // Trigger readiness update multiple times to build up readiness
      for (let i = 0; i < 5; i++) {
        modeler.handleObservedAction({
          type: 'swap-own',
          playerId,
        });
      }

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      // After multiple swaps with low estimated score, readiness should be significant
      expect(updatedBeliefs!.vintoReadiness).toBeGreaterThan(0.4);
    });

    it('should have low readiness for high estimated score', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);
      beliefs!.estimatedScore = 30; // High score = low readiness

      // Trigger readiness update
      modeler.handleObservedAction({
        type: 'peek-own',
        playerId,
      });

      const updatedBeliefs = modeler.getPlayerBeliefs(playerId);
      expect(updatedBeliefs!.vintoReadiness).toBeLessThan(0.5);
    });
  });

  describe('getMostLikelyVintoCaller', () => {
    it('should return null when no players tracked', () => {
      const freshModeler = new OpponentModeler();
      expect(freshModeler.getMostLikelyVintoCaller()).toBeNull();
    });

    it('should return player with highest readiness', () => {
      const player2 = 'player-2';
      const player3 = 'player-3';

      modeler.initializePlayer(player2);
      modeler.initializePlayer(player3);

      // Set different readiness levels
      modeler.getPlayerBeliefs(playerId)!.vintoReadiness = 0.3;
      modeler.getPlayerBeliefs(player2)!.vintoReadiness = 0.7;
      modeler.getPlayerBeliefs(player3)!.vintoReadiness = 0.5;

      expect(modeler.getMostLikelyVintoCaller()).toBe(player2);
    });
  });

  describe('reset', () => {
    it('should clear all beliefs', () => {
      // Add some beliefs
      modeler.handleObservedAction({
        type: 'swap-from-discard',
        playerId,
        card: { rank: '5', suit: 'hearts', id: 'card-1' } as Card,
        position: 2,
      });

      expect(modeler.getAllBeliefs().size).toBe(1);

      modeler.reset();

      expect(modeler.getAllBeliefs().size).toBe(0);
      expect(modeler.getPlayerBeliefs(playerId)).toBeUndefined();
    });
  });

  describe('edge cases', () => {
    it('should handle actions without cards gracefully', () => {
      const action: ObservedAction = {
        type: 'use-action',
        playerId,
        // card undefined
      };

      expect(() => modeler.handleObservedAction(action)).not.toThrow();
    });

    it('should auto-initialize player on first action', () => {
      const newPlayer = 'player-new';
      expect(modeler.getPlayerBeliefs(newPlayer)).toBeUndefined();

      modeler.handleObservedAction({
        type: 'peek-own',
        playerId: newPlayer,
      });

      expect(modeler.getPlayerBeliefs(newPlayer)).toBeDefined();
    });

    it('should handle readiness bounds (0-1)', () => {
      const beliefs = modeler.getPlayerBeliefs(playerId);

      // Try to push below 0
      beliefs!.vintoReadiness = 0.05;
      modeler.handleObservedAction({
        type: 'peek-own',
        playerId,
      });
      expect(modeler.getPlayerBeliefs(playerId)!.vintoReadiness).toBeGreaterThanOrEqual(0);

      // Try to push above 1
      beliefs!.vintoReadiness = 0.95;
      modeler.handleObservedAction({
        type: 'swap-own',
        playerId,
      });
      expect(modeler.getPlayerBeliefs(playerId)!.vintoReadiness).toBeLessThanOrEqual(1);
    });
  });
});
