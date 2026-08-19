// Replays the whole corpus through the deployed engine — the check that the Kotlin engine
// behaves identically in the runtime that serves it, not just on the JVM.
//
//   npx wrangler dev --port 8787 --local     # in kmp/worker/cloudflare
//   node gate-engine-replay.mjs
//
// Against a real deployment, point GATE_URL at it:
//   GATE_URL=https://vinto-room.<subdomain>.workers.dev node gate-engine-replay.mjs
//
// Everything proving the port so far ran on the JVM. Kotlin/JS represents Long as a pair of
// Ints and uses a different serialiser backend, so agreement on one does not imply the
// other. This is what turns that assumption into a measurement.

import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.GATE_URL ?? 'http://localhost:8787';
const CORPUS = resolve(HERE, '../../../fixtures/recordings');

const files = readdirSync(CORPUS).filter((f) => f.endsWith('.json')).sort();
let failures = 0;
let totalActions = 0;
let throttled = 0;
const started = process.hrtime.bigint();

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * POST one recording, backing off on 503.
 *
 * A deployed Worker enforces CPU on a rolling average, and replaying a whole game is ~250 ms
 * of it. Fired back to back, a batch of 50 trips that and Cloudflare answers `error code:
 * 1102`; spaced out, every one of them succeeds. So a 503 here is the platform saying "too
 * fast", not the engine saying "wrong" — and this is a correctness check, not a load test.
 *
 * The retries are COUNTED and reported rather than hidden. A guard that quietly swallows what
 * it works around stops being able to tell you the thing has got slower.
 */
async function postWithBackoff(body, attempts = 6) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const response = await fetch(`${BASE}/replay`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body,
    });
    if (response.status !== 503) return response;

    throttled++;
    await sleep(250 * 2 ** attempt);
  }
  return null;
}

for (const file of files) {
  const body = readFileSync(join(CORPUS, file), 'utf8');
  const response = await postWithBackoff(body);

  if (!response) {
    failures++;
    console.log(`  FAIL ${file}: still 503 after retries — the endpoint is not keeping up`);
    continue;
  }

  if (!response.ok) {
    failures++;
    console.log(`  FAIL ${file}: HTTP ${response.status}`);
    continue;
  }

  const report = await response.json();
  totalActions += report.steps ?? 0;

  if (report.ok) continue;

  failures++;
  console.log(`  FAIL ${file}: ${report.error ?? ''}`);
  if (report.divergenceReason) {
    console.log(
      `       ${report.divergenceReason} at action ${report.divergenceIndex}` +
        ` (${report.divergenceAction}) after ${report.steps}/${report.actions}`,
    );
    if (report.expectedHash) {
      console.log(`       expected ${report.expectedHash}`);
      console.log(`       actual   ${report.actualHash}`);
    }
  }
}

const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6;
console.log(
  `\n${files.length - failures}/${files.length} recordings replayed in the Worker runtime, ` +
    `${totalActions} actions, ${elapsedMs.toFixed(0)} ms`,
);
if (throttled > 0) {
  console.log(
    `  (${throttled} request(s) were throttled with 503 and retried — CPU is rate-limited, ` +
      'which is a throughput property of the plan, not an engine fault)',
  );
}
console.log(failures === 0 ? 'ENGINE RUNTIME GATE PASS\n' : `ENGINE RUNTIME GATE FAIL (${failures})\n`);
process.exit(failures === 0 ? 0 : 1);
