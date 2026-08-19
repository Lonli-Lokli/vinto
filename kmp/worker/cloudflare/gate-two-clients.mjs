// Platform gate 2a.3 — two WebSocket clients through one Durable Object.
//
// Run against a local `wrangler dev` (see docs/kotlin/PLATFORM-GATE.md):
//   npx wrangler dev --port 8787 --local --var ROOM_OPEN:true   # in kmp/worker/cloudflare
//   node gate-two-clients.mjs
//
// ROOM_OPEN must be set: the room refuses every request while it is shut.
//
// What this gate is for is the *platform*: two sockets on one Durable Object, hibernation,
// reconnect to the same seat, resync from a cursor, and state that survives the object being
// destroyed. The game questions — redaction, the seat boundary, whether the bots play — are
// asked by `gate-real-room.mjs`, which needs no wrangler and is far quicker to run.
//
// The room now runs the real engine, so an action is a `GameAction` document rather than a
// string, and the events a client receives are the actions the room accepted — its own and
// the bots' that followed.

const BASE = process.env.GATE_URL ?? 'http://localhost:8787';
const SEED = 12345;

// `--verify <room>` re-checks an existing room without touching it. Used to prove the room
// is rebuilt from storage after the object is gone: run the gate, restart `wrangler dev`
// (which destroys every instance), then verify. State that survives a process restart is
// state that survives hibernation, which keeps no memory either.
const verifyIdx = process.argv.indexOf('--verify');
const VERIFY_ONLY = verifyIdx !== -1;
const ROOM = VERIFY_ONLY ? process.argv[verifyIdx + 1] : (process.env.GATE_ROOM ?? `gate-${Date.now()}`);

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

const isEvents = (m) => m.type === 'events';

console.log(`\nroom ${ROOM}, seed ${SEED}\n`);

if (VERIFY_ONLY) {
  const room = await (await fetch(`${BASE}/?room=${ROOM}&seed=${SEED}`)).json();
  console.log('rebuilt from storage after every instance was destroyed');
  check('the log survived', room.log.length > 0, true);
  check('the seats survived', room.seats.filter((s) => s.clientId !== null).length, 2);
  check('the dealt game survived', room.game.players.length, 4);
  check('the game moved past setup', room.game.phase, 'playing');
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
check('room has exactly 4 seats', aliceJoined.seats.length, 4);
check('alice is sent a view of her own', aliceJoined.view.viewerId, aliceJoined.seats[0].playerId);
check('bob is sent a different one', bobJoined.view.viewerId, bobJoined.seats[1].playerId);

// --- real actions, both sockets see every one ----------------------------------
//
// Setup: seat 0 peeks its two cards and finishes, which starts the game. These are chosen
// because they are the only actions available before play begins, and because the second
// peek is refused if the first was not applied — so the sequence proves the room is really
// reducing rather than echoing.
const alicePlayer = aliceJoined.seats[0].playerId;
const sent = [
  { type: 'PEEK_SETUP_CARD', payload: { playerId: alicePlayer, position: 0 } },
  { type: 'PEEK_SETUP_CARD', payload: { playerId: alicePlayer, position: 1 } },
  { type: 'FINISH_SETUP', payload: { playerId: alicePlayer } },
];

const aliceBatches = [];
const bobBatches = [];
for (const action of sent) {
  alice.send({ type: 'action', action });
  aliceBatches.push(await alice.next(isEvents));
  bobBatches.push(await bob.next(isEvents));
}

const flatten = (batches) => batches.flatMap((b) => b.events);
const aliceEvents = flatten(aliceBatches);
const bobEvents = flatten(bobBatches);

console.log('\naction exchange');
check('alice saw every action', aliceEvents.length >= sent.length, true);
check('both sockets saw the same events', aliceEvents, bobEvents);
check('indices are contiguous from 0', aliceEvents.map((e) => e.index), aliceEvents.map((_, i) => i));
check('the actions are the ones alice sent', aliceEvents.slice(0, 3).map((e) => e.action.type),
  sent.map((a) => a.type));
check('the game left setup', aliceBatches[2].view.phase, 'playing');

console.log('\nper-seat views');
const lastAlice = aliceBatches[2].view;
const lastBob = bobBatches[2].view;
check('each socket gets its own view', lastAlice.viewerId !== lastBob.viewerId, true);
check('alice sees the two cards she peeked',
  lastAlice.players[0].cards.filter((c) => c.type === 'visible').length, 2);
check('bob is shown none of them',
  lastBob.players[0].cards.filter((c) => c.type === 'visible').length, 0);

// --- reconnect: same seat, and resync from the cursor ---------------------------
alice.close();
const aliceAgain = open('alice-reconnected');
await aliceAgain.ready;
aliceAgain.send({ type: 'join', clientId: 'alice', nickname: 'Alice' });
const rejoined = await aliceAgain.next((m) => m.type === 'joined');

const logLength = aliceEvents.length;

console.log('\nreconnect');
check('same clientId returns to the same seat', rejoined.seat, 0);
check('the room survived the disconnect', rejoined.nextIndex, logLength);
check('and hands back a view, not the room', typeof rejoined.view.viewerId, 'string');

aliceAgain.send({ type: 'resync', sinceIndex: logLength - 2 });
const sync = await aliceAgain.next((m) => m.type === 'sync');
check('resync returns only unseen events', sync.events.map((e) => e.index),
  [logLength - 2, logLength - 1]);
check('resync reports the next cursor', sync.nextIndex, logLength);

// --- state lives in storage, not memory ----------------------------------------
aliceAgain.close();
bob.close();
const persisted = await (await fetch(`${BASE}/?room=${ROOM}&seed=${SEED}`)).json();

console.log('\ndurability');
check('the log persisted after every socket closed', persisted.log.length, logLength);
check('the dealt game persisted with it', persisted.game.players.length, 4);
check('and the seats did too', persisted.seats.filter((s) => s.clientId !== null).length, 2);

console.log(`\n${failures === 0 ? 'GATE 2a.3 PASS' : `GATE 2a.3 FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
