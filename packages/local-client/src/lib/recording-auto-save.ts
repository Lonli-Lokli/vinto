/**
 * Debounced persistence of the in-progress recording, so a crash or reload still leaves
 * a reproducible file behind.
 *
 * Storage and the timer are injected: the browser passes `localStorage`, tests pass a
 * stub, and headless tools pass nothing at all.
 */

/** The slice of the Storage API this needs — `localStorage` satisfies it structurally. */
export interface RecordingStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export const RECORDING_AUTOSAVE_KEY = 'vinto:recording:v1';

export interface RecordingAutoSaveOptions {
  storage: RecordingStorage;
  /** Debounce window in ms. Writes coalesce so a bot burst is one serialisation. */
  debounceMs?: number;
  key?: string;
  setTimer?: (fn: () => void, ms: number) => unknown;
  clearTimer?: (handle: unknown) => void;
}

export class RecordingAutoSave {
  private readonly storage: RecordingStorage;
  private readonly debounceMs: number;
  private readonly key: string;
  private readonly setTimer: (fn: () => void, ms: number) => unknown;
  private readonly clearTimer: (handle: unknown) => void;
  private handle: unknown;
  private pending?: () => string;

  constructor(options: RecordingAutoSaveOptions) {
    this.storage = options.storage;
    this.debounceMs = options.debounceMs ?? 500;
    this.key = options.key ?? RECORDING_AUTOSAVE_KEY;
    this.setTimer =
      options.setTimer ?? ((fn, ms) => setTimeout(fn, ms) as unknown);
    this.clearTimer =
      options.clearTimer ??
      ((handle) => clearTimeout(handle as ReturnType<typeof setTimeout>));
  }

  /**
   * `serialise` is a thunk rather than a string so that coalesced writes only pay for
   * one JSON serialisation of the whole recording, not one per action.
   */
  schedule(serialise: () => string): void {
    this.pending = serialise;

    if (this.handle !== undefined) {
      this.clearTimer(this.handle);
    }

    this.handle = this.setTimer(() => {
      this.handle = undefined;
      this.flush();
    }, this.debounceMs);
  }

  /** Writes any pending recording immediately. */
  flush(): void {
    const serialise = this.pending;
    if (!serialise) return;
    this.pending = undefined;

    try {
      this.storage.setItem(this.key, serialise());
    } catch {
      // A full or unavailable quota must never break the game; the recording is a
      // debugging aid, not game state.
    }
  }

  /** Drops the saved recording — called when a new game starts. */
  clear(): void {
    if (this.handle !== undefined) {
      this.clearTimer(this.handle);
      this.handle = undefined;
    }
    this.pending = undefined;

    try {
      this.storage.removeItem(this.key);
    } catch {
      // Ignore: see flush().
    }
  }

  load(): string | null {
    try {
      return this.storage.getItem(this.key);
    } catch {
      return null;
    }
  }
}
