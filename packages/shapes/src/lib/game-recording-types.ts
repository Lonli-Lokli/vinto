import { GameAction } from './action-types';
import { Difficulty } from './domain-types';
import { GameState } from './game-state-types';

/**
 * GameRecording format v1 — the interchange format between engine implementations.
 *
 * A recording fully describes a game: `initialState` plus every accepted action in
 * order. Replaying it in any implementation must reproduce identical states. The shape
 * and its canonicalisation rules are documented in `docs/game-engine/RECORDING.md`,
 * which is the contract the Kotlin port implements against.
 */

export const GAME_RECORDING_FORMAT_VERSION = 1;

/**
 * Settings needed to reproduce a game. `seed` is required here even though it is
 * optional when starting a game: by recording time it has been resolved.
 */
export interface GameRecordingSettings {
  humanPlayerName: string;
  difficulty: Difficulty;
  seed: number;
}

export interface GameRecordingMeta {
  /** ISO timestamp. Informational only — never part of the canonical hash. */
  recordedAt: string;
  /** Which implementation produced this, e.g. `vinto-ts@<version>`. */
  producer: string;
  label?: string;
}

export interface RecordedAction {
  action: GameAction;
  /** Canonical hash of the state *after* this action. Optional; replay recomputes it. */
  stateHash?: string;
}

export interface GameRecording {
  formatVersion: number;
  meta: GameRecordingMeta;
  settings: GameRecordingSettings;
  /** Full state after dealing, before any action. */
  initialState: GameState;
  actions: RecordedAction[];
  /** State when the recording was exported; may be mid-game. */
  finalState: GameState;
  finalStateHash?: string;
}

export class UnsupportedRecordingVersionError extends Error {
  constructor(readonly received: unknown) {
    super(
      `Unsupported GameRecording formatVersion: ${String(received)}. ` +
        `This build understands version ${GAME_RECORDING_FORMAT_VERSION}.`,
    );
    this.name = 'UnsupportedRecordingVersionError';
  }
}

/**
 * Rejects recordings this build cannot faithfully replay. Called before any replay so a
 * version mismatch surfaces as a clear error rather than a confusing divergence.
 */
export function assertRecordingVersion(
  recording: Pick<GameRecording, 'formatVersion'>,
): void {
  if (recording?.formatVersion !== GAME_RECORDING_FORMAT_VERSION) {
    throw new UnsupportedRecordingVersionError(recording?.formatVersion);
  }
}
