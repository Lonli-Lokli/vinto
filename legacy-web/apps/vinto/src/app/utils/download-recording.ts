/**
 * Saves a game recording to disk.
 *
 * A recording is the only way to reproduce a specific game exactly, so this is the
 * useful thing to attach to a bug report. Replay it with
 * `npm run recordings:replay -- <file>`.
 */
export function downloadRecording(json: string): void {
  const url = URL.createObjectURL(
    new Blob([json], { type: 'application/json' }),
  );
  const link = document.createElement('a');

  link.href = url;
  // gameId is `vinto-<seed>`, so the filename identifies the game it came from.
  link.download = `${JSON.parse(json).initialState.gameId}.json`;
  link.click();

  URL.revokeObjectURL(url);
}
