import { EmptyAction, GameState } from '@vinto/shapes';
import copy from 'fast-copy';

/**
 * EMPTY Handler
 *
 */
export function handleEmpty(state: GameState, _action: EmptyAction): GameState {
  return copy(state);
}
