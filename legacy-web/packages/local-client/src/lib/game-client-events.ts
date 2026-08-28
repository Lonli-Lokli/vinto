// Global registry for game client state update callbacks
import { GameState, GameAction, logger } from '@vinto/shapes';

type StateUpdateCallback = (
  oldState: GameState,
  newState: GameState,
  action: GameAction
) => void;
type StateErrorCallback = (
  reason: string
) => void;
const stateUpdateCallbacks: StateUpdateCallback[] = [];
const stateErrorCallbacks: StateErrorCallback[] = [];

export function registerStateUpdateCallback(cb: StateUpdateCallback): void {
  if (!stateUpdateCallbacks.includes(cb)) {
    stateUpdateCallbacks.push(cb);
  }
}

export function registerStateErrorCallback(cb: StateErrorCallback): void {
  if (!stateErrorCallbacks.includes(cb)) {
    stateErrorCallbacks.push(cb);
  }
}

export function unregisterStateUpdateCallback(cb: StateUpdateCallback): void {
  const idx = stateUpdateCallbacks.indexOf(cb);
  if (idx !== -1) {
    stateUpdateCallbacks.splice(idx, 1);
  }
}

export function unregisterStateErrorCallback(cb: StateErrorCallback): void {
  const idx = stateErrorCallbacks.indexOf(cb);
  if (idx !== -1) {
    stateErrorCallbacks.splice(idx, 1);
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

export function triggerStateErrorCallbacks(
  reason: string
): void {
  stateErrorCallbacks.forEach((cb) => {
    try {
      cb(reason);
    } catch (err) {
      logger.error('StateErrorCallback error:', err);
    }
  });
}