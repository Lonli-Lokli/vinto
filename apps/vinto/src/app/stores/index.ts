// stores/index.ts
/**
 * Store exports and compatibility layer
 *
 * This file provides both:
 * 1. Direct exports of store classes (for DI)
 * 2. Compatibility functions for legacy code
 */

// Export store classes for DI
export { CardAnimationStore } from './card-animation-store';
export { UIStore } from './ui-store';

// Export types
export type { AnimationType, CardAnimationState } from './card-animation-store';
