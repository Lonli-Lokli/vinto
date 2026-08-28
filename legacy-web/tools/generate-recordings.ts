/**
 * Generates seeded, headless bot-vs-bot recordings for the parity corpus.
 *
 *   npx vite-node tools/generate-recordings.ts -- --games 50 --seed 1
 *
 * Every game is 4 players, all driven by the BotAIAdapter. The output is written to
 * the repository-root fixtures/recordings/ with per-action hashes filled in, which
 * is what makes the corpus usable as a cross-implementation gate rather than a
 * crash test.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { copy } from 'fast-copy';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { GameAction, GameRecording, GameState } from '@vinto/shapes';
import { GameActions } from '@vinto/engine';
import {
  BotAIAdapter,
  GameClient,
  fourPlayerGame,
} from '@vinto/local-client/headless';

// The corpus is shared with the Kotlin engine and so lives at the repository root,
// one level above this (legacy) npm workspace. Resolved from this file rather than
// from `process.cwd()`, which changed when the workspace moved under `legacy-web/`.
const FIXTURES_DIR = fileURLToPath(
  new URL('../../fixtures/recordings', import.meta.url)
);

interface GenerateOptions {
  games: number;
  seed: number;
  difficulty: GameState['difficulty'];
  timeoutMs: number;
  maxActions: number;
  outDir: string;
}

function parseArgs(argv: string[]): GenerateOptions {
  const get = (flag: string, fallback: string) => {
    const index = argv.indexOf(flag);
    return index >= 0 && argv[index + 1] ? argv[index + 1] : fallback;
  };

  return {
    games: Number(get('--games', '5')),
    seed: Number(get('--seed', '1')),
    difficulty: get('--difficulty', 'moderate') as GameState['difficulty'],
    timeoutMs: Number(get('--timeout', '120000')),
    maxActions: Number(get('--max-actions', '400')),
    outDir: get('--out', FIXTURES_DIR),
  };
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** All four seats become bots, so the adapter can drive the entire game. */
function allBots(state: GameState): GameState {
  return {
    ...state,
    players: state.players.map((player) => ({
      ...player,
      isHuman: false,
      isBot: true,
    })),
  };
}

interface PlayedGame {
  recording: GameRecording;
  finished: boolean;
  stalled: boolean;
  actionCount: number;
}

async function playGame(
  seed: number,
  difficulty: GameState['difficulty'],
  timeoutMs: number,
  maxActions: number,
): Promise<PlayedGame> {
  const initialState = allBots(fourPlayerGame('Bot 0', difficulty, seed));
  const client = new GameClient(initialState);

  // The AnimationService normally advances visual state once an animation finishes;
  // headless, we advance it immediately so bots react without waiting.
  const dispatch = client.dispatch.bind(client);

  client.dispatch = (action: GameAction) => {
    dispatch(action);
    client.syncVisualState();
  };

  // Attach the adapter *before* leaving setup: it drives bots from a MobX reaction, so
  // the FINISH_SETUP dispatch below is what kicks the game off. Constructed afterwards,
  // nothing would ever trigger it and the game would sit idle.
  const adapter = new BotAIAdapter(client, { skipDelays: true });

  // The adapter deliberately does nothing during 'setup', so drive it here. Seats dealt
  // without known cards peek two; FINISH_SETUP is a single global transition, so one
  // dispatch moves everyone to 'playing'.
  for (const player of client.state.players) {
    while (
      (client.state.players.find((p) => p.id === player.id)?.knownCardPositions
        .length ?? 0) < 2
    ) {
      const known =
        client.state.players.find((p) => p.id === player.id)
          ?.knownCardPositions ?? [];
      const next = [0, 1, 2, 3, 4].find((pos) => !known.includes(pos));
      if (next === undefined) break;
      client.dispatch(GameActions.peekSetupCard(player.id, next));
    }
  }
  client.dispatch(GameActions.finishSetup(client.state.players[0].id));

  // Stop on action count, not wall-clock: a time-based cut would make the corpus depend
  // on machine speed. A recording is valid mid-game (see RECORDING.md), so a capped
  // prefix is a legitimate fixture.
  const deadline = Date.now() + timeoutMs;
  while (
    client.state.phase !== 'scoring' &&
    client.recordedActionCount < maxActions &&
    Date.now() < deadline
  ) {
    await sleep(2);
  }

  const finished = client.state.phase === 'scoring';
  const stalled = !finished && client.recordedActionCount < maxActions;
  adapter.dispose();

  // The recorder captured the state each action produced inside dispatch, before any
  // observer could react, so its hashes are aligned with its actions by construction.
  const recording = await client.exportRecordingWithHashes();
  recording.meta = {
    recordedAt: new Date().toISOString(),
    producer: 'vinto-ts/generate-recordings',
    label: `selfplay seed=${seed} difficulty=${difficulty}`,
  };

  return {
    recording,
    finished,
    stalled,
    actionCount: recording.actions.length,
  };
}

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));
  mkdirSync(options.outDir, { recursive: true });

  let written = 0;
  let unfinished = 0;
  let stalledGames = 0;

  for (let index = 0; index < options.games; index++) {
    const seed = options.seed + index;
    const { recording, finished, stalled, actionCount } = await playGame(
      seed,
      options.difficulty,
      options.timeoutMs,
      options.maxActions,
    );

    const name = `selfplay-${options.difficulty}-${seed}.json`;
    writeFileSync(
      join(options.outDir, name),
      JSON.stringify(recording, null, 2),
    );
    written++;
    if (!finished) unfinished++;
    if (stalled) stalledGames++;

    console.log(
      `${finished ? 'DONE' : 'PARTIAL'} ${name} (${actionCount} actions)`,
    );
  }

  console.log(`\nWrote ${written} recording(s) to ${options.outDir}`);

  // Loud rather than silent: a corpus of half-played games would look like coverage
  // while testing far less than it appears to.
  if (unfinished > 0) {
    console.warn(
      `NOTE: ${unfinished}/${written} game(s) did not reach 'scoring'. These are valid ` +
        `mid-game recordings and replay fine, but they contribute no final scoring or ` +
        `coalition round to the corpus. A game ends only when a bot calls Vinto, which ` +
        `needs a fully known hand worth <= 0, so some games simply run long.`,
    );
  }
  if (stalledGames > 0) {
    console.warn(
      `WARNING: ${stalledGames} game(s) stopped on the ${options.timeoutMs}ms wall-clock ` +
        `timeout before reaching --max-actions ${options.maxActions}. Raise --timeout if ` +
        `you want them to finish; a time-based cut makes output depend on machine speed.`,
    );
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
