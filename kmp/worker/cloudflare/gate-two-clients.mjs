// Platform gate 2a.3 — two WebSocket clients through one Durable Object.
//
// Run against a local `wrangler dev` (see docs/kotlin/PLATFORM-GATE.md):
//   npx wrangler dev --port 8787 --local     # in kmp/worker/cloudflare
//   node gate-two-clients.mjs
//
// The room seed is 12345 because `fixtures/prng/vectors.json` publishes the bounded
// sequence for seed 12345 / bound 54. Each accepted action draws from that sequence, so
// asserting the Durable Object's events against the fixture checks the Kotlin engine code
// running inside a Durable Object against the same committed file the TypeScript and
// Kotlin unit tests read. The gate therefore doubles as a cross-language check.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.GATE_URL ?? 'http://localhost:8787';
const SEED = 12345;

// `--verify <room>` re-checks an existing room without touching it. Used to prove the room
// is rebuilt from storage after the object is gone: run the gate, restart `wrangler dev`
// (which destroys every instance), then verify. State that survives a process restart is
// state that survives hibernation, which keeps no memory either.
const verifyIdx = process.argv.indexOf('--verify');
const VERIFY_ONLY = verifyIdx !== -1;
const ROOM = VERIFY_ONLY ? process.argv[verifyIdx + 1] : (process.env.GATE_ROOM ?? `gate-${Date.now()}`);

const vectors = JSON.parse(
  readFileSync(resolve(HERE, '../../../fixtures/prng/vectors.json'), 'utf8'),
);
const expected = vectors.boundedSequences.find((b) => b.seed === SEED && b.bound === 54);
if (!expected) throw new Error('fixture lacks the seed 12345 / bound 54 sequence');

let failures = 0;
function check(label, actual, wanted) {
  const ok = JSON.stringify(actual) === JSON.stringify(wanted);
  console.log(`${ok ? '  ok  ' : '  FAIL'} ${label}`);
  if (!ok) {
    failures++;
    console.log(`        expected ${JSON.stringify(wanted)}`);
    console.log(`        actual   ${JSON.stringify(actual)}`);
  }
}

/** A socket with a queue, so tests can await specific messages without racing. */
function open(label) {
  const url = `${BASE}/?room=${ROOM}&seed=${SEED}`.replace('http', 'ws');
  const ws = new WebSocket(url);
  const queue = [];
  const waiters = [];

  ws.addEventListener('message', (e) => {
    const msg = JSON.parse(e.data);
    const waiter = waiters.findIndex((w) => w.match(msg));
    if (waiter >= 0) waiters.splice(waiter, 1)[0].resolve(msg);
    else queue.push(msg);
  });

  return {
    label,
    ws,
    ready: new Promise((res, rej) => {
      ws.addEventListener('open', res);
      ws.addEventListener('error', rej);
    }),
    send: (msg) => ws.send(JSON.stringify(msg)),
    close: () => ws.close(),
    next(match = () => true, timeoutMs = 5000) {
      const found = queue.findIndex(match);
      if (found >= 0) return Promise.resolve(queue.splice(found, 1)[0]);
      return new Promise((resolve, reject) => {
        const w = { match, resolve };
        waiters.push(w);
        setTimeout(() => {
          const i = waiters.indexOf(w);
          if (i >= 0) {
            waiters.splice(i, 1);
            reject(new Error(`${label}: timed out waiting for a message`));
          }
        }, timeoutMs);
      });
    },
  };
}

const isEvent = (m) => m.type === 'event';

console.log(`\nroom ${ROOM}, seed ${SEED}\n`);

if (VERIFY_ONLY) {
  const room = await (await fetch(`${BASE}/?room=${ROOM}&seed=${SEED}`)).json();
  console.log('rebuilt from storage after every instance was destroyed');
  check('log survived', room.log.length, 6);
  check('seats survived', room.seats.filter((s) => s.clientId !== null).length, 2);
  check('generator state survived', room.rngState, room.log[5].rngState);
  check('replays to the published sequence', room.log.map((e) => e.value), expected.values.slice(0, 6));
  console.log(`\n${failures === 0 ? 'RESUME CHECK PASS' : `RESUME CHECK FAIL (${failures})`}\n`);
  process.exit(failures === 0 ? 0 : 1);
}

// --- two clients join one room -------------------------------------------------
const alice = open('alice');
const bob = open('bob');
await Promise.all([alice.ready, bob.ready]);

alice.send({ type: 'join', clientId: 'alice', nickname: 'Alice' });
const aliceJoined = await alice.next((m) => m.type === 'joined');
bob.send({ type: 'join', clientId: 'bob', nickname: 'Bob' });
const bobJoined = await bob.next((m) => m.type === 'joined');

console.log('two clients through one Durable Object');
check('alice takes seat 0', aliceJoined.seat, 0);
check('bob takes seat 1', bobJoined.seat, 1);
check('room has exactly 4 seats', aliceJoined.state.seats.length, 4);

// --- they exchange actions, both see every one ---------------------------------
const aliceEvents = [];
const bobEvents = [];

for (let i = 0; i < 6; i++) {
  const actor = i % 2 === 0 ? alice : bob;
  actor.send({ type: 'action', action: `draw-${i}` });
  aliceEvents.push((await alice.next(isEvent)).event);
  bobEvents.push((await bob.next(isEvent)).event);
}

console.log('\naction exchange');
check('alice saw 6 events', aliceEvents.length, 6);
check('both clients saw identical events', aliceEvents, bobEvents);
check('indices are monotonic from 0', aliceEvents.map((e) => e.index), [0, 1, 2, 3, 4, 5]);
check('seats alternate', aliceEvents.map((e) => e.seat), [0, 1, 0, 1, 0, 1]);

console.log('\ncross-language determinism (vs fixtures/prng/vectors.json)');
check(
  'action values match the published sequence',
  aliceEvents.map((e) => e.value),
  expected.values.slice(0, 6),
);

// --- reconnect: same seat, and resync from the cursor ---------------------------
alice.close();
const aliceAgain = open('alice-reconnected');
await aliceAgain.ready;
aliceAgain.send({ type: 'join', clientId: 'alice', nickname: 'Alice' });
const rejoined = await aliceAgain.next((m) => m.type === 'joined');

console.log('\nreconnect');
check('same clientId returns to the same seat', rejoined.seat, 0);
check('room state survived the disconnect', rejoined.state.log.length, 6);

aliceAgain.send({ type: 'resync', sinceIndex: 4 });
const sync = await aliceAgain.next((m) => m.type === 'sync');
check('resync returns only unseen events', sync.events.map((e) => e.index), [4, 5]);
check('resync reports the next cursor', sync.nextIndex, 6);

// --- state lives in storage, not memory ----------------------------------------
aliceAgain.close();
bob.close();
const persisted = await (await fetch(`${BASE}/?room=${ROOM}&seed=${SEED}`)).json();

console.log('\ndurability');
check('log persisted after every socket closed', persisted.log.length, 6);
check('generator state advanced deterministically', persisted.rngState, aliceEvents[5].rngState);

console.log(`\n${failures === 0 ? 'GATE 2a.3 PASS' : `GATE 2a.3 FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
