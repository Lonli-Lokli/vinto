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

// `--verify <room>` re-checks an existing room without touching it. Used to prove the room
// is rebuilt from storage after the object is gone: run the gate, restart `wrangler dev`
// (which destroys every instance), then verify. State that survives a process restart is
// state that survives hibernation, which keeps no memory either.
const verifyIdx = process.argv.indexOf('--verify');
const VERIFY_ONLY = verifyIdx !== -1;

/**
 * A room code has to be *minted* now — naming one into existence is exactly what the registry
 * exists to prevent (design R4), and that applies to harnesses as much as to strangers.
 */
async function mintRoom() {
  const response = await fetch(`${BASE}/rooms`, {
    method: 'POST',
    body: JSON.stringify({ isPublic: false, hostNickname: 'gate' }),
  });
  const body = await response.json();
  if (!body.code) {
    // Almost always the per-source cap, which every local run shares: rooms from earlier runs
    // stay live for ten minutes. That is the cap working, not the gate breaking — restart
    // `wrangler dev` on a clean `.wrangler/state`.
    throw new Error(
      `could not mint a room (${JSON.stringify(body)}). ` +
      'If this says a cap, clear .wrangler/state and restart wrangler dev.',
    );
  }
  return body.code;
}

const ROOM = VERIFY_ONLY ? process.argv[verifyIdx + 1] : (process.env.GATE_ROOM ?? await mintRoom());

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
  const url = `${BASE}/?room=${ROOM}`.replace('http', 'ws');
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

console.log(`\nroom ${ROOM}\n`);

if (VERIFY_ONLY) {
  const room = await (await fetch(`${BASE}/?room=${ROOM}`)).json();
  console.log('rebuilt from storage after every instance was destroyed');
  check('the log survived', room.log.length > 0, true);
  check('the seats survived', room.seats.filter((s) => s.tokenHash !== null).length >= 2, true);
  check('the dealt game survived', room.game.players.length, 4);
  check('the game moved past setup', room.game.phase, 'playing');
  console.log(`\n${failures === 0 ? 'RESUME CHECK PASS' : `RESUME CHECK FAIL (${failures})`}\n`);
  process.exit(failures === 0 ? 0 : 1);
}

// --- two clients join one room -------------------------------------------------
const alice = open('alice');
const bob = open('bob');
await Promise.all([alice.ready, bob.ready]);

alice.send({ type: 'join', nickname: 'Alice' });
const aliceJoined = await alice.next((m) => m.type === 'joined');
bob.send({ type: 'join', nickname: 'Bob' });
const bobJoined = await bob.next((m) => m.type === 'joined');

console.log('two clients through one Durable Object');
check('alice takes seat 0', aliceJoined.seat, 0);
check('bob takes seat 1', bobJoined.seat, 1);
check('room has exactly 4 seats', aliceJoined.seats.length, 4);
// No view in a lobby, and that is the honest answer rather than an empty one: the client
// cannot otherwise tell "not dealt" from "dealt, nothing to see", and those are two screens.
check('a lobby hands back no view, because there is no game', aliceJoined.view, null);
check('but it does hand back the lobby', aliceJoined.lobby.phase, 'LOBBY');

console.log('\ntokens');
check('each client is issued a token', [
  typeof aliceJoined.token === 'string' && aliceJoined.token.length >= 32,
  typeof bobJoined.token === 'string' && bobJoined.token.length >= 32,
], [true, true]);
check('the two tokens differ', aliceJoined.token !== bobJoined.token, true);
check(
  'no seat on the wire carries a token or its hash',
  aliceJoined.seats.every((s) => s.token === undefined && s.tokenHash === undefined),
  true,
);

// --- the lobby, and a countdown that is a real alarm ----------------------------------
//
// Two humans and two bots. The countdown is ten seconds of *wall clock* here, unlike the
// pure-Kotlin lobby gate where time is a parameter — because the thing being tested is
// different: there, that the rule is right; here, that a Durable Object alarm actually fires.
console.log('\nlobby');
check('the room starts as a lobby', aliceJoined.lobby.phase, 'LOBBY');
// Alice's snapshot is from before Bob arrived — a `joined` message reports the room at the
// instant it was sent, which is the correct thing for it to do.
check('alice sees herself alone in it', aliceJoined.lobby.humans, 1);
check('and bob, joining second, sees them both', bobJoined.lobby.humans, 2);
check('with two seats still empty', bobJoined.lobby.seats.filter((s) => !s.occupied).length, 2);

alice.send({ type: 'add-bot', token: aliceJoined.token });
await alice.next((m) => m.type === 'lobby');

// Bob adds the fourth, so the countdown is started by somebody who did not start the room.
bob.send({ type: 'add-bot', token: bobJoined.token });
const counting = await bob.next((m) => m.type === 'lobby' && m.lobby.phase === 'STARTING');
check('filling the fourth seat starts a countdown', counting.lobby.phase, 'STARTING');
check('which any player could have started', counting.lobby.humans, 2);
check('and is about ten seconds', counting.lobby.msUntilStart > 8000, true);

// Cancel it, to prove the proposal is reversible — then put it back.
const removable = counting.lobby.seats.find((s) => s.removable);
alice.send({ type: 'remove-bot', token: aliceJoined.token, seat: removable.index });
const cancelled = await alice.next((m) => m.type === 'lobby' && m.lobby.phase === 'LOBBY');
check('removing a bot cancels it', cancelled.lobby.phase, 'LOBBY');

alice.send({ type: 'add-bot', token: aliceJoined.token });
const restarted = await alice.next((m) => m.type === 'lobby' && m.lobby.phase === 'STARTING');
check('and refilling gives the full ten seconds again', restarted.lobby.msUntilStart > 8000, true);

// Wait for the alarm. Nothing here can make it fire early: this is the one place the
// countdown is proven to be a Durable Object alarm rather than something held in memory.
console.log('  ...waiting out the countdown (alarm, not a timer)');
const started = await alice.next((m) => m.type === 'started', 20000);
check('the alarm fires and the game is dealt', Boolean(started.view), true);
check('and every socket is told', Boolean((await bob.next((m) => m.type === 'started', 5000)).view), true);
check('alice is dealt a hand she has not seen', 
  started.view.players[started.view.viewerId === started.view.players[0].id ? 0 : 1]
    .cards.filter((c) => c.type === 'visible').length, 0);

// --- real actions, both sockets see every one ----------------------------------
const alicePlayer = aliceJoined.seats[0].playerId ?? started.view.players[0].id;
const sent = [
  { type: 'PEEK_SETUP_CARD', payload: { playerId: alicePlayer, position: 0 } },
];

const aliceBatches = [];
const bobBatches = [];
for (const action of sent) {
  alice.send({ type: 'action', token: aliceJoined.token, action });
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

console.log('\nper-seat views');
const lastAlice = aliceBatches[0].view;
const lastBob = bobBatches[0].view;
check('each socket gets its own view', lastAlice.viewerId !== lastBob.viewerId, true);

// --- reconnect: same seat, and resync from the cursor ---------------------------
alice.close();
const aliceAgain = open('alice-reconnected');
await aliceAgain.ready;
// Reconnecting means presenting the token you were issued, not asserting a name.
aliceAgain.send({ type: 'join', token: aliceJoined.token, nickname: 'Alice' });
const rejoined = await aliceAgain.next((m) => m.type === 'joined');

const logLength = aliceEvents.length;

console.log('\nreconnect');
check('the same token returns to the same seat', rejoined.seat, 0);
check('the room survived the disconnect', rejoined.nextIndex, logLength);
check('and hands back a view, not the room', typeof rejoined.view.viewerId, 'string');

// A stranger asserting alice's nickname gets nothing. Once the game has started the table is
// fixed, so the refusal here is even flatter than in a lobby — where they would simply have
// been given a different seat and their own token.
const impostor = open('impostor');
await impostor.ready;
impostor.send({ type: 'join', nickname: 'Alice' });
const refused = await impostor.next((m) => m.type === 'error' || m.type === 'joined');
check('a nickname does not reclaim a seat', refused.type, 'error');
check('and the reason is the table, not the name', refused.message, 'the game has already started');
impostor.close();

// One action is enough to prove the cursor: ask for everything after the first and get the
// tail. Sized from the log rather than hard-coded, since how many actions a turn produces is
// the engine's business and not this test's.
const from = Math.max(0, logLength - 1);
aliceAgain.send({ type: 'resync', sinceIndex: from });
const sync = await aliceAgain.next((m) => m.type === 'sync');
check(
  'resync returns only unseen events',
  sync.events.map((e) => e.index),
  Array.from({ length: logLength - from }, (_, i) => from + i),
);
check('resync reports the next cursor', sync.nextIndex, logLength);

// --- state lives in storage, not memory ----------------------------------------
aliceAgain.close();
bob.close();
const persisted = await (await fetch(`${BASE}/?room=${ROOM}`)).json();

console.log('\ndurability');
check('the log persisted after every socket closed', persisted.log.length, logLength);
check('the dealt game persisted with it', persisted.game.players.length, 4);
check('and the seats did too', persisted.seats.filter((s) => s.tokenHash !== null).length >= 2, true);

console.log(`\n${failures === 0 ? 'GATE 2a.3 PASS' : `GATE 2a.3 FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
