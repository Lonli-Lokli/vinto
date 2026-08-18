import {
  GameAction,
  GameRecording,
  GameState,
  assertRecordingVersion,
  canonicalizeGameState,
  hashGameState,
  rehydrateGameState,
} from '@vinto/shapes';
import { GameEngine } from './game-engine';

/**
 * Replays a `GameRecording` through the engine.
 *
 * This is the cross-implementation parity harness: the Kotlin engine runs the same
 * recordings and must reach byte-identical canonical states after every action. A
 * divergence report names the first action where the two disagree, which localises a
 * porting mistake to a single handler.
 *
 * Note that replay reconstructs *engine* state only. `turnActions`/`roundActions` are
 * written by the client, not by the reducer, so a replayed state legitimately has no
 * history. Comparison is therefore by canonical hash, which excludes history by design.
 */

export type ReplayDivergenceReason =
  | 'action-rejected'
  | 'hash-mismatch'
  | 'final-state-mismatch';

export interface ReplayDivergence {
  /** Index into `recording.actions`; -1 for a final-state mismatch. */
  index: number;
  reason: ReplayDivergenceReason;
  action?: GameAction;
  detail: string;
  expectedHash?: string;
  actualHash?: string;
}

export interface ReplayResult {
  ok: boolean;
  /** Number of actions successfully applied. */
  steps: number;
  finalState: GameState;
  /** State after each applied action; used to fill hashes when writing fixtures. */
  states: GameState[];
  divergence?: ReplayDivergence;
}

export interface ReplayOptions {
  /**
   * Verify `finalState` against the recording. Off when replaying a recording whose
   * `finalState` was captured by a different implementation's client.
   */
  verifyFinalState?: boolean;
}

export async function replayRecording(
  recording: GameRecording,
  options: ReplayOptions = {},
): Promise<ReplayResult> {
  const { verifyFinalState = true } = options;

  // Throws on an unknown version rather than producing a confusing divergence.
  assertRecordingVersion(recording);

  let state = rehydrateGameState(recording.initialState);
  const states: GameState[] = [];

  for (let index = 0; index < recording.actions.length; index++) {
    const entry = recording.actions[index];
    const result = GameEngine.reduce(state, entry.action);

    if (!result.success) {
      return {
        ok: false,
        steps: index,
        finalState: state,
        states,
        divergence: {
          index,
          reason: 'action-rejected',
          action: entry.action,
          detail: `Engine rejected ${entry.action.type}: ${result.reason}`,
        },
      };
    }

    state = result.state;
    states.push(state);

    if (entry.stateHash) {
      const actualHash = await hashGameState(state);
      if (actualHash !== entry.stateHash) {
        return {
          ok: false,
          steps: index + 1,
          finalState: state,
          states,
          divergence: {
            index,
            reason: 'hash-mismatch',
            action: entry.action,
            detail: `State after ${entry.action.type} does not match the recorded hash`,
            expectedHash: entry.stateHash,
            actualHash,
          },
        };
      }
    }
  }

  if (verifyFinalState && recording.finalState) {
    const expectedHash = await hashGameState(
      rehydrateGameState(recording.finalState),
    );
    const actualHash = await hashGameState(state);

    if (expectedHash !== actualHash) {
      return {
        ok: false,
        steps: recording.actions.length,
        finalState: state,
        states,
        divergence: {
          index: -1,
          reason: 'final-state-mismatch',
          detail:
            'Replayed final state does not match the recorded final state',
          expectedHash,
          actualHash,
        },
      };
    }
  }

  return {
    ok: true,
    steps: recording.actions.length,
    finalState: state,
    states,
  };
}

/** Human-readable divergence report for the CLI and CI logs. */
export function formatDivergence(
  divergence: ReplayDivergence,
  finalState?: GameState,
): string {
  const lines = [
    `Divergence at action index ${divergence.index} (${divergence.reason})`,
    `  ${divergence.detail}`,
  ];

  if (divergence.action) {
    lines.push(`  action: ${JSON.stringify(divergence.action)}`);
  }
  if (divergence.expectedHash) {
    lines.push(`  expected: ${divergence.expectedHash}`);
    lines.push(`  actual:   ${divergence.actualHash}`);
  }
  if (finalState) {
    lines.push(`  state at divergence: ${canonicalizeGameState(finalState)}`);
  }

  return lines.join('\n');
}
