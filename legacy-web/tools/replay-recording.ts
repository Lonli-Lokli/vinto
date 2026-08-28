/**
 * Replays recordings and reports divergences.
 *
 *   npx vite-node tools/replay-recording.ts -- fixtures/recordings
 *   npx vite-node tools/replay-recording.ts -- path/to/game.json --report out.txt
 *
 * Exits non-zero if any recording diverges, so CI and the Kotlin round-trip job can use
 * it as a gate.
 */

import {
  existsSync,
  readFileSync,
  readdirSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { join } from 'node:path';
import { GameRecording } from '@vinto/shapes';
import { formatDivergence, replayRecording } from '@vinto/engine';

function collectFiles(target: string): string[] {
  if (!existsSync(target)) {
    throw new Error(`No such file or directory: ${target}`);
  }

  if (statSync(target).isDirectory()) {
    return readdirSync(target)
      .filter((name) => name.endsWith('.json'))
      .sort()
      .map((name) => join(target, name));
  }

  return [target];
}

async function main(): Promise<void> {
  const argv = process.argv.slice(2);
  const target = argv.find((arg) => !arg.startsWith('--'));

  if (!target) {
    console.error(
      'usage: vite-node tools/replay-recording.ts -- <file|directory> [--report <path>]',
    );
    process.exit(2);
  }

  const reportIndex = argv.indexOf('--report');
  const reportPath =
    reportIndex >= 0 && argv[reportIndex + 1]
      ? argv[reportIndex + 1]
      : undefined;

  const files = collectFiles(target);
  if (files.length === 0) {
    // An empty directory must not read as success; it is the difference between
    // "everything replays" and "nothing was checked".
    console.error(`FAIL no recordings found in ${target}`);
    process.exit(1);
  }

  const reports: string[] = [];
  let failures = 0;

  for (const file of files) {
    let recording: GameRecording;
    try {
      recording = JSON.parse(readFileSync(file, 'utf8'));
    } catch (error) {
      failures++;
      const detail = `FAIL ${file}\n  unreadable: ${(error as Error).message}`;
      console.error(detail);
      reports.push(detail);
      continue;
    }

    try {
      const result = await replayRecording(recording);

      if (result.ok) {
        console.log(`PASS ${file} (${result.steps} actions)`);
      } else {
        failures++;
        const detail = `FAIL ${file}\n${formatDivergence(result.divergence!)}`;
        console.error(detail);
        reports.push(detail);
      }
    } catch (error) {
      // A version mismatch or malformed state throws rather than diverging.
      failures++;
      const detail = `FAIL ${file}\n  ${(error as Error).message}`;
      console.error(detail);
      reports.push(detail);
    }
  }

  console.log(
    `\n${files.length - failures}/${files.length} recording(s) replayed cleanly`,
  );

  if (reportPath && reports.length > 0) {
    writeFileSync(reportPath, reports.join('\n\n'));
    console.log(`Divergence report written to ${reportPath}`);
  }

  process.exit(failures > 0 ? 1 : 0);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
