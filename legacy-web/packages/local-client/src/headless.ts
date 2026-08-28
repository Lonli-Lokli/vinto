/**
 * Headless entry point — everything needed to drive a game without React.
 *
 * Used by the `tools/` CLIs (fixture generation, replay) and by any future JVM/server
 * equivalent. Kept separate from the main barrel so a command-line script does not pull
 * in React context modules.
 */

export { GameClient } from './lib/game-client';
export type { GameClientOptions } from './lib/game-client';
export { BotAIAdapter } from './lib/adapters/botAIAdapter';
export { GameRecorder } from './lib/game-recorder';
export {
  RecordingAutoSave,
  RECORDING_AUTOSAVE_KEY,
} from './lib/recording-auto-save';
export type {
  RecordingStorage,
  RecordingAutoSaveOptions,
} from './lib/recording-auto-save';
export {
  createDeck,
  fourPlayerGame,
  generateSeed,
  initializeGame,
  recordingSettingsFromState,
} from './lib/initializeGame';
export type { GameSettings } from './lib/initializeGame';
