// Global registry for game client state update callbacks
import { GameState, GameAction, logger } from '@vinto/shapes';

type StateUpdateCallback = (
  oldState: GameState,
  newState: GameState,
  action: GameAction
) => void;
const stateUpdateCallbacks: StateUpdateCallback[] = [];

export function registerStateUpdateCallback(cb: StateUpdateCallback): void {
  if (!stateUpdateCallbacks.includes(cb)) {
    stateUpdateCallbacks.push(cb);
  }
}

export function unregisterStateUpdateCallback(cb: StateUpdateCallback): void {
  const idx = stateUpdateCallbacks.indexOf(cb);
  if (idx !== -1) {
    stateUpdateCallbacks.splice(idx, 1);
  }
}

export function triggerStateUpdateCallbacks(
  oldState: GameState,
  newState: GameState,
  action: GameAction
): void {
  stateUpdateCallbacks.forEach((cb) => {
    try {
      cb(oldState, newState, action);
    } catch (err) {
      logger.error('StateUpdateCallback error:', err);
    }
  });
}
