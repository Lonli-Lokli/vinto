/**
 * Generates seeded, headless bot-vs-bot recordings for the parity corpus.
 *
 *   npx vite-node tools/generate-recordings.ts -- --games 50 --seed 1
 *
 * Every game is 4 players, all driven by the BotAIAdapter. The output is written to
 * fixtures/recordings/ with per-action hashes filled in, which is what makes the corpus
 * usable as a cross-implementation gate rather than a crash test.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  GAME_RECORDING_FORMAT_VERSION,
  GameAction,
  GameRecording,
  GameState,
  hashGameState,
} from '@vinto/shapes';
import { GameActions } from '@vinto/engine';
import {
  BotAIAdapter,
  GameClient,
  fourPlayerGame,
  recordingSettingsFromState,
} from '@vinto/local-client/headless';

const FIXTURES_DIR = join(process.cwd(), 'fixtures', 'recordings');

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
  const initialState = allBots(fourPlayerGame('Bot 0', difficulty, 'v1', seed));
  const client = new GameClient(initialState);

  // The AnimationService normally advances visual state once an animation finishes;
  // headless, we advance it immediately so bots react without waiting.
  const dispatch = client.dispatch.bind(client);
  const actions: GameAction[] = [];
  const states: GameState[] = [];

  client.dispatch = (action: GameAction) => {
    const before = client.recordedActionCount;
    dispatch(action);
    if (client.recordedActionCount > before) {
      actions.push(action);
      states.push(client.state);
    }
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
    actions.length < maxActions &&
    Date.now() < deadline
  ) {
    await sleep(2);
  }

  const finished = client.state.phase === 'scoring';
  const stalled = !finished && actions.length < maxActions;
  adapter.dispose();

  // Snapshot the action list first and derive finalState from it, rather than reading
  // client.state: an in-flight bot sequence can still dispatch after dispose(), and a
  // finalState that included an action missing from the list would fail its own replay.
  const recordedActions = [...actions];
  const recordedStates = states.slice(0, recordedActions.length);
  const finalState = recordedStates.at(-1) ?? initialState;

  const recording: GameRecording = {
    formatVersion: GAME_RECORDING_FORMAT_VERSION,
    meta: {
      recordedAt: new Date().toISOString(),
      producer: 'vinto-ts/generate-recordings',
      label: `selfplay seed=${seed} difficulty=${difficulty}`,
    },
    settings: recordingSettingsFromState(initialState),
    initialState,
    actions: await Promise.all(
      recordedActions.map(async (action, index) => ({
        action,
        stateHash: await hashGameState(recordedStates[index]),
      })),
    ),
    finalState,
    finalStateHash: await hashGameState(finalState),
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
      `NOTE: ${unfinished}/${written} game(s) did not reach 'scoring'. Bots do not ` +
        `currently call Vinto, and a Vinto call is the only way a game ends, so all-bot ` +
        `self-play runs indefinitely. These are valid mid-game recordings (capped at ` +
        `--max-actions ${options.maxActions}) and replay fine, but the corpus contains ` +
        `no scoring phase and no coalition final round until that is addressed.`,
    );
  }
  if (stalledGames > 0) {
    console.warn(
      `WARNING: ${stalledGames} game(s) stopped on the ${options.timeoutMs}ms wall-clock ` +
        `timeout before reaching --max-actions. Raise --timeout; a time-based cut makes ` +
        `output depend on machine speed.`,
    );
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
