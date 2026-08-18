import {
  GAME_RECORDING_FORMAT_VERSION,
  GameAction,
  GameRecording,
  GameRecordingSettings,
  GameState,
  RecordedAction,
  hashGameState,
} from '@vinto/shapes';

/**
 * Accumulates a replayable `GameRecording` as a game is played.
 *
 * Only actions the engine *accepted* are recorded: a rejected action never mutated
 * state, so replaying it would diverge.
 */
export class GameRecorder {
  private readonly actions: RecordedAction[] = [];
  /**
   * The state each action produced, captured inside `dispatch` before any observer can
   * react. Snapshotting `client.state` after `dispatch()` returns is not equivalent:
   * `dispatch` is a MobX action, so reactions fire as it completes and the bot adapter
   * can dispatch again re-entrantly, leaving `client.state` several actions ahead.
   */
  private readonly statesAfter: GameState[] = [];
  private latestState: GameState;

  constructor(
    private readonly settings: GameRecordingSettings,
    private readonly initialState: GameState,
    /** Injectable so tests and fixtures can pin the informational timestamp. */
    private readonly now: () => string = () => new Date().toISOString(),
    private readonly producer = 'vinto-ts',
  ) {
    this.latestState = initialState;
  }

  get actionCount(): number {
    return this.actions.length;
  }

  record(action: GameAction, stateAfter: GameState): void {
    this.actions.push({ action });
    this.statesAfter.push(stateAfter);
    this.latestState = stateAfter;
  }

  toRecording(finalState: GameState = this.latestState): GameRecording {
    return {
      formatVersion: GAME_RECORDING_FORMAT_VERSION,
      meta: {
        recordedAt: this.now(),
        producer: this.producer,
      },
      settings: this.settings,
      initialState: this.initialState,
      actions: this.actions.map((entry) => ({ ...entry })),
      finalState,
    };
  }

  /**
   * Recording with per-action and final hashes filled in. Hashing is async (WebCrypto),
   * so this cannot happen inline during dispatch; fixtures and exports call it
   * explicitly. Replay recomputes hashes regardless, so they are an integrity check
   * rather than required data.
   */
  async toRecordingWithHashes(
    finalState: GameState = this.latestState,
  ): Promise<GameRecording> {
    const recording = this.toRecording(finalState);

    recording.actions = await Promise.all(
      recording.actions.map(async (entry, index) => ({
        ...entry,
        stateHash: await hashGameState(this.statesAfter[index]),
      })),
    );
    recording.finalStateHash = await hashGameState(finalState);

    return recording;
  }

  toJSON(finalState?: GameState): string {
    return JSON.stringify(this.toRecording(finalState));
  }
}
