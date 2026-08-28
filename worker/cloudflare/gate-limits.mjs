/**
 * The limits that bound what abuse costs.
 *
 *   node worker/cloudflare/gate-limits.mjs
 *
 * The one that matters is the action budget, and the assertion that matters about it is not
 * "the request was refused" but "**no bot search ran**". A throttle that still does the work
 * and then declines to report it has cost exactly as much as no throttle at all.
 */
import {
  newRoom, joinRoom, addBot, startGame, applyAction, countdownMs,
  newRegistry, mintRoomCode, registrySize, maxLiveRooms, maxRoomsPerSource,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};
const parse = JSON.parse;

const T0 = 1_000_000;
const A = 'token-ada', B = 'token-bo';

function playingRoom() {
  let json = newRoom('limits', 7, 'moderate', T0);
  json = JSON.stringify(parse(joinRoom(json, A, 'Ada', T0)).state);
  json = JSON.stringify(parse(joinRoom(json, B, 'Bo', T0)).state);
  json = JSON.stringify(parse(addBot(json, A, T0)).state);
  json = JSON.stringify(parse(addBot(json, A, T0)).state);
  return JSON.stringify(parse(startGame(json, T0 + countdownMs())).state);
}

console.log('\nAbuse limits\n');

// --- the action budget -----------------------------------------------------------------------
let json = playingRoom();
const seat0 = parse(json).seats[0].playerId;
const draw = JSON.stringify({ type: 'DRAW_CARD', payload: { playerId: seat0 } });

// Fire a flood at a single instant, so nothing refills between attempts.
let accepted = 0;
let throttled = 0;
let firstRefusalAt = -1;
let current = json;

for (let i = 0; i < 40; i++) {
  const result = parse(applyAction(current, A, draw, T0));
  if (result.retryAfterMs) {
    throttled++;
    if (firstRefusalAt < 0) firstRefusalAt = i;
  } else if (!result.error) {
    accepted++;
  }
  current = JSON.stringify(result.state);
}

check('a flood is throttled rather than served', throttled > 0, `${throttled} of 40 refused`);
check(
  'the burst is about ten, not forty',
  firstRefusalAt > 0 && firstRefusalAt <= 11,
  `first refusal at attempt ${firstRefusalAt}`,
);
check('and a refusal says how long to wait', parse(applyAction(current, A, draw, T0)).retryAfterMs > 0);

// THE assertion: a throttled action must not have done the expensive part.
const beforeFlood = parse(json).log.length;
const afterFlood = parse(current).log.length;
const attemptsThatCouldHaveRun = 40 - throttled;
check(
  'a throttled action runs NO bot search — the log only grew for the ones that were served',
  afterFlood - beforeFlood <= attemptsThatCouldHaveRun * 20,
  `${afterFlood - beforeFlood} entries from ${attemptsThatCouldHaveRun} served`,
);

// The budget refills, and does so from elapsed time rather than a tick.
const laterOk = parse(applyAction(current, A, draw, T0 + 60_000));
check('the budget refills with time', !laterOk.retryAfterMs, `${laterOk.retryAfterMs}`);

// It is per seat: one player flooding must not lock the table.
const floodedThenBo = parse(applyAction(current, B, JSON.stringify({
  type: 'DRAW_CARD', payload: { playerId: parse(current).seats[1].playerId },
}), T0));
check(
  'one seat exhausting its budget does not spend anybody else’s',
  !floodedThenBo.retryAfterMs,
  `${floodedThenBo.retryAfterMs}`,
);

// And it survives eviction, because it lives in the room's state rather than in memory.
check(
  'the budget is stored, so an eviction does not refill it',
  parse(current).buckets['0'].tokens < 1,
  `${parse(current).buckets['0']?.tokens}`,
);

// --- registry caps ------------------------------------------------------------------------------
let registry = newRegistry();
const bytesFor = (n) => [n, n >> 3, n >> 5, n + 7, n + 11, n + 13].join(',');

for (let i = 0; i < maxRoomsPerSource(); i++) {
  const minted = parse(mintRoomCode(registry, bytesFor(i), false, '', 'source-a'));
  check(`room ${i + 1} for one source is allowed`, !minted.error, minted.error);
  registry = JSON.stringify(minted.state);
}
const overSource = parse(mintRoomCode(registry, bytesFor(99), false, '', 'source-a'));
check(
  'but one more from the same source is refused',
  Boolean(overSource.error),
  'a single source can open rooms without limit',
);
check(
  'while a different source is unaffected',
  !parse(mintRoomCode(registry, bytesFor(50), false, '', 'source-b')).error,
);

// The global cap: rate limits bound the slope, this bounds the total.
let big = newRegistry();
for (let i = 0; i < maxLiveRooms(); i++) {
  const minted = parse(mintRoomCode(big, bytesFor(i * 7 + 3), false, '', `source-${i}`));
  if (minted.error) break;
  big = JSON.stringify(minted.state);
}
check('the registry fills to its cap', registrySize(big) === maxLiveRooms(), `${registrySize(big)}`);
check(
  'and refuses beyond it, whoever is asking',
  Boolean(parse(mintRoomCode(big, bytesFor(999), false, '', 'somebody-new')).error),
);

console.log(`\n${failures === 0 ? 'LIMITS GATE PASS' : `LIMITS GATE FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
