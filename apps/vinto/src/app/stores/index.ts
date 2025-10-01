// stores/index.ts
/**
 * Store exports and compatibility layer
 *
 * This file provides both:
 * 1. Direct exports of store classes (for DI)
 * 2. Compatibility functions for legacy code
 */

// Export store classes for DI
export { PlayerStore } from './player-store';
export { DeckStore } from './deck-store';
export { GamePhaseStore } from './game-phase-store';
export { ActionStore } from './action-store';
export { TossInStore } from './toss-in-store';
export { ReplayStore } from './replay-store';
export { CardAnimationStore } from './card-animation-store';
export { GameStore } from './game-store';

// Export types
export type {
  GamePhase,
  GameSubPhase,
  FullGameState,
} from './game-phase-store';
export type {
  ActionContext,
  SwapTarget,
  PeekTarget,
  TargetType,
} from './action-store';
export type {
  TossInAction,
  TossInStoreCallbacks,
  TossInStoreDependencies,
} from './toss-in-store';
export type {
  AnimationType,
  CardAnimationState,
} from './card-animation-store';