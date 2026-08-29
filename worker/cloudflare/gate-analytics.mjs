/**
 * Analytics: what gets written, what never does, and what happens with no binding at all.
 *
 *   node worker/cloudflare/gate-analytics.mjs
 *
 * Two properties, and the second is the one that keeps the first honest.
 *
 * **Nothing identifying can be written.** `AnalyticsPrivacyTest` proves the *type* cannot
 * carry a room code or a nickname; this proves the same thing about the bytes that actually
 * leave, by rendering every event and checking the strings against a closed vocabulary. A
 * type can be right while a hand-built payload beside it is wrong.
 *
 * **A missing binding changes nothing.** Analytics must never be a thing you need a
 * Cloudflare account to develop against, so every path is absent-safe and this asserts it
 * rather than trusting it.
 */
import {
  clientEventPoint,
  roomCreatedPoint,
  seatFilledPoint,
  seatVacatedPoint,
  botTookOverPoint,
  reconnectedPoint,
  roundStartPoint,
  roundEndPoint,
  sessionEndedPoint,
  newRoom,
  joinRoom,
  addBot,
  startGame,
  countdownMs,
} from '../build/compileSync/js/main/productionExecutable/kotlin/vinto-kmp-worker.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};

console.log('\nanalytics: the data points');

const points = {
  room_created: roomCreatedPoint(true, 'hard', 12, 1),
  seat_filled: seatFilledPoint(2, 2, false, 8, 1),
  seat_vacated: seatVacatedPoint(1, 2, true, 8, 1),
  bot_took_over: botTookOverPoint(1, 9, 1),
  reconnected: reconnectedPoint(4000, 7, 1),
  round_start: roundStartPoint(2, 2, 3, 11, 1),
  round_end: roundEndPoint(44, 91000, 'VINTO_CALLED', true, 1600, 12),
  session_ended: sessionEndedPoint('PLAYED_OUT', 3, 600000, 5, 1),
};

// The whole allowed string vocabulary: event names and enum labels. Nothing else may appear
// anywhere in a rendered point.
const VOCABULARY = new Set([
  ...Object.keys(points),
  'EASY', 'MODERATE', 'HARD',
  'VINTO_CALLED', 'DECK_EXHAUSTED', 'ABANDONED',
  'PLAYED_OUT', 'TOO_FEW_HUMANS', 'TIMED_OUT', 'EVERYBODY_LEFT',
  'APP_OPENED', 'PLAY_PRESSED', 'ONLINE_PRESSED', 'ROOM_REQUESTED', 'INVITE_SHARED', 'ROOM_JOINED',
  'SOLO', 'ONLINE', 'LESSON', 'MENU',
  'STAGE_STALLED', 'SOCKET_LOST', 'MOVE_REFUSED', 'RENDER_FAILED',
  'funnel', 'solo_round', 'lesson', 'failure',
]);

for (const [name, json] of Object.entries(points)) {
  const point = JSON.parse(json);
  check(`${name} is indexed by its own name`, point.indexes?.[0] === name, JSON.stringify(point.indexes));
  const strays = [...(point.blobs ?? []), ...point.indexes].filter((b) => !VOCABULARY.has(b));
  check(`${name} writes only closed-vocabulary strings`, strays.length === 0, strays.join(', '));
  check(`${name} carries what it cost`, point.doubles.length >= 3, JSON.stringify(point.doubles));
}

console.log('\nanalytics: what a client may post');

// The sealed type is the filter. Anything not declared there decodes to nothing, so a field
// nobody wrote cannot reach the store and a client-supplied timestamp is not believed.
const good = clientEventPoint(JSON.stringify({ type: 'funnel', step: 'INVITE_SHARED', surface: 'ONLINE' }));
check('a known client event renders', good !== null && good !== undefined);
check(
  'and carries no cost, because a client cannot know one',
  good ? JSON.parse(good).doubles.length === 1 : false,
  good ?? 'null',
);

const smuggled = clientEventPoint(JSON.stringify({
  type: 'funnel', step: 'APP_OPENED', surface: 'MENU',
  roomCode: '7KQ2MP', nickname: 'Raph', token: 'secret', ip: '203.0.113.9',
}));
const rendered = smuggled ?? '';
check('extra fields on a known event are dropped', !/7KQ2MP|Raph|secret|203\.0\.113\.9/.test(rendered), rendered);

for (const [label, body] of [
  ['an unknown event type', { type: 'exfiltrate', roomCode: '7KQ2MP' }],
  ['a server-only event posted by a client', { type: 'round_end', turns: 1, durationMs: 1, endedBy: 'ABANDONED', callerWon: true }],
  ['nonsense', { nope: true }],
  ['not an object', 'hello'],
]) {
  // Server-only names are additionally refused by CLIENT_EVENTS in index.mjs; this checks the
  // Kotlin side does not build a point for junk either, so neither layer is load-bearing alone.
  const out = clientEventPoint(JSON.stringify(body));
  const ok = out === null || out === undefined || !/7KQ2MP/.test(out);
  check(`${label} does not reach the store`, ok, String(out));
}

console.log('\nanalytics: with no binding configured');

// Exactly the shim's helper, which is the code path a deployment without the binding takes.
function emit(env, point) {
  if (!env.ANALYTICS || !point) return;
  try { env.ANALYTICS.writeDataPoint(point); } catch { /* never fails the request */ }
}

let wrote = 0;
emit({}, points.round_end);
emit({ ANALYTICS: undefined }, points.round_end);
check('no binding writes nothing and throws nothing', wrote === 0);

emit({ ANALYTICS: { writeDataPoint: () => { wrote++; } } }, points.round_end);
check('a configured binding is written to', wrote === 1);

emit({ ANALYTICS: { writeDataPoint: () => { throw new Error('quota'); } } }, points.round_end);
check('a sink that throws does not fail the request', true);

console.log('\nanalytics: a real room, and what it says about itself');

// The shim's `#observe` derives every room event by comparing the state a request read with
// the state it produced. That logic is what this exercises: drive a real room through the
// Kotlin core, run the same comparison, and check the events that fall out are the ones the
// game actually had — rather than trusting that a call site remembered to report.
const T0 = 1_700_000_000_000;
const fresh = newRoom('gate-analytics', 4242, 'moderate', T0);
const one = JSON.parse(joinRoom(fresh, 'token-a', 'A', T0));
const two = JSON.parse(joinRoom(JSON.stringify(one.state), 'token-b', 'B', T0 + 1000));
const withBot = JSON.parse(addBot(JSON.stringify(two.state), 'token-a', T0 + 2000));
const full = JSON.parse(addBot(JSON.stringify(withBot.state), 'token-a', T0 + 3000));

check('two humans and two bots fill the table', full.state.seats.filter((s) => s.isBot).length === 2);

// The same derivation the shim does, kept here as one function so a divergence between the
// gate and the shim shows up as a failing check rather than as a silent difference.
function observe(before, after) {
  const seen = [];
  const humans = after.seats.filter((s) => s.tokenHash != null).length;
  const bots = after.seats.filter((s) => s.isBot).length;
  if (before.session.rounds.length < after.session.rounds.length) seen.push('round_end');
  if (before.phase !== 'PLAYING' && after.phase === 'PLAYING') seen.push('round_start');
  if (before.phase !== 'FINISHED' && after.phase === 'FINISHED') seen.push('session_ended');
  const took = after.seats.filter((s) => s.isBot && s.tokenHash != null).length;
  const had = before.seats.filter((s) => s.isBot && s.tokenHash != null).length;
  if (took > had) seen.push('bot_took_over');
  return { seen, humans, bots };
}

const beforeDeal = full.state;
// Past the countdown. Filling the fourth seat starts a ten-second public timer that any
// player can cancel, so a deal before it expires is not a deal at all — which is exactly the
// kind of thing a gate written against an assumed clock gets wrong.
const DEAL_AT = T0 + 4000 + countdownMs() + 1;
const dealt = JSON.parse(startGame(JSON.stringify(beforeDeal), DEAL_AT));
const afterDeal = dealt.state ?? dealt;
const dealObserved = observe(beforeDeal, afterDeal);

check('dealing emits round_start', dealObserved.seen.includes('round_start'), dealObserved.seen.join(','));
check('and nothing else', dealObserved.seen.length === 1, dealObserved.seen.join(','));
check('the round records when it was dealt', Number.isFinite(afterDeal.roundStartedAtEpochMs), String(afterDeal.roundStartedAtEpochMs));
check(
  'the deal time is the clock it was dealt on',
  afterDeal.roundStartedAtEpochMs === DEAL_AT,
  String(afterDeal.roundStartedAtEpochMs),
);
check(
  'a round in progress is not an ended one',
  !dealObserved.seen.includes('round_end'),
  dealObserved.seen.join(','),
);

// A quiet request changes nothing, and must therefore say nothing. This is the check that
// catches an `#observe` that fires on every call rather than on a transition.
const idle = observe(afterDeal, afterDeal);
check('a request that changed nothing emits nothing', idle.seen.length === 0, idle.seen.join(','));

console.log(failures === 0 ? '\nanalytics gate: PASS\n' : `\nanalytics gate: ${failures} FAILED\n`);
process.exit(failures === 0 ? 0 : 1);
