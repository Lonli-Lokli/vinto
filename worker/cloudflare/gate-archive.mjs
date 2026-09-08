/**
 * The round archive: a game found later, by the time it was played.
 *
 *   npx wrangler dev --port 8787 --var ROOM_OPEN:true --var RECORDINGS_KEY:local-harness
 *   node gate-archive.mjs
 *
 * The question this exists for is not a crash report and not a statistic. It is a letter:
 * *"somebody used a King and nobody took a penalty, Tuesday evening"* — no room code, no logs,
 * no bug report, just a time. Answering it needs three things to be true at once, and none of
 * them was: a round has to be recorded even when it never finished, the recording has to
 * outlive the room that played it, and it has to be findable by the hour rather than by a code
 * nobody wrote down.
 *
 * So this plays a **whole round through the real Durable Object**, over sockets, exactly as two
 * phones would — and then asks the archive for the hour, the way a person would. It drives the
 * game rather than scripting it: what a seat does next is decided from the view the room just
 * sent, which is what makes it survive a change to the deal.
 */
const BASE = process.env.GATE_URL ?? 'http://localhost:8787';
const KEY = process.env.RECORDINGS_KEY ?? 'local-harness';
const PROTOCOL = 5;

let failures = 0;
const check = (name, ok, detail = '') => {
  if (ok) console.log(`  pass  ${name}`);
  else { failures += 1; console.log(`  FAIL  ${name}${detail ? ` — ${detail}` : ''}`); }
};

const keyed = { 'x-recordings-key': KEY };

console.log(`Round archive against ${BASE}\n`);

// --- the door ---------------------------------------------------------------------------------
//
// A 404 without the key, and the *same* 404 a wrong one gets: a service that answers 401 has
// told a stranger there is something here worth having a key for. An archived round is every
// hand at one table, which is exactly what a room code must never be enough to read.
{
  const bare = await fetch(`${BASE}/rounds`);
  check('the archive is not served without a key', bare.status === 404, `status ${bare.status}`);

  const wrong = await fetch(`${BASE}/rounds`, { headers: { 'x-recordings-key': 'not-the-key' } });
  check('nor with the wrong one', wrong.status === 404, `status ${wrong.status}`);
  check(
    'and a wrong key is told exactly what a missing one is',
    wrong.status === bare.status,
    `${wrong.status} against ${bare.status}`,
  );

  const nonsense = await fetch(`${BASE}/rounds?from=../etc`, { headers: keyed });
  check('a prefix that is not a clock is refused', nonsense.status === 400, `status ${nonsense.status}`);
}

// --- a round, played for real -----------------------------------------------------------------

/** A socket with a queue, so a driver can await the message it wants without racing. */
function open() {
  const ws = new WebSocket(`${BASE}/?room=${ROOM}`.replace('http', 'ws'));
  const queue = [];
  const waiters = [];
  // Kept here rather than read back out of the queue, because awaiting a message *removes* it:
  // a driver that looked for the latest view among what was left never saw the one it had just
  // waited for, and sat there deciding from the deal for ever.
  let view = null;

  ws.addEventListener('message', (e) => {
    const msg = JSON.parse(e.data);
    if (msg.view) view = msg.view;
    const waiter = waiters.findIndex((w) => w.match(msg));
    if (waiter >= 0) waiters.splice(waiter, 1)[0].resolve(msg);
    else queue.push(msg);
  });

  return {
    ready: new Promise((res, rej) => {
      ws.addEventListener('open', res);
      ws.addEventListener('error', rej);
    }),
    send: (msg) => ws.send(JSON.stringify(msg)),
    close: () => ws.close(),
    next(match = () => true, timeoutMs = 25_000) {
      const found = queue.findIndex(match);
      if (found >= 0) return Promise.resolve(queue.splice(found, 1)[0]);
      return new Promise((resolve, reject) => {
        const w = { match, resolve };
        waiters.push(w);
        setTimeout(() => {
          const i = waiters.indexOf(w);
          if (i >= 0) { waiters.splice(i, 1); reject(new Error('timed out waiting for the room')); }
        }, timeoutMs);
      });
    },
    /** The latest view this socket has been sent, whichever message carried it. */
    latest: () => view,
  };
}

/**
 * An address of its own, for the reason `gate-ratelimit` and `gate-bruteforce` have one.
 *
 * Rooms are capped per source, and every gate in a CI run shares one — so a gate that mints
 * from the common address spends an allowance the gates beside it need, and whichever runs
 * last is refused a room and fails for a reason that has nothing to do with what it tests.
 * This one runs last, and did. TEST-NET-3, never a real host.
 */
const MINE = { 'cf-connecting-ip': '203.0.113.30' };

async function mintRoom() {
  const response = await fetch(`${BASE}/rooms`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...MINE },
    body: JSON.stringify({ isPublic: false, difficulty: 'easy', nickname: 'Ada' }),
  });
  const body = await response.json();
  if (!body.code) {
    throw new Error(
      `could not mint a room (${JSON.stringify(body)}). ` +
      'If this says a cap, clear .wrangler/state and restart wrangler dev.',
    );
  }
  return body.code;
}

const ROOM = process.env.GATE_ROOM ?? await mintRoom();
console.log(`  ...playing room ${ROOM} out`);

const ada = open();
const bo = open();
await Promise.all([ada.ready, bo.ready]);

ada.send({ type: 'join', protocol: PROTOCOL, nickname: 'Ada' });
const adaJoined = await ada.next((m) => m.type === 'joined');
bo.send({ type: 'join', protocol: PROTOCOL, nickname: 'Bo' });
const boJoined = await bo.next((m) => m.type === 'joined');

ada.send({ type: 'add-bot', token: adaJoined.token });
await ada.next((m) => m.type === 'lobby');
bo.send({ type: 'add-bot', token: boJoined.token });
await bo.next((m) => m.type === 'lobby' && m.lobby.phase === 'STARTING');

// The countdown is a real Durable Object alarm, so this is ten seconds of wall clock.
const dealt = await ada.next((m) => m.type === 'started');
await bo.next((m) => m.type === 'started');
check('a room deals a round', Boolean(dealt.view), 'no view on the deal');

/**
 * What this seat does next, read off the view the room last sent.
 *
 * Deliberately a decision rather than a script: the deal is seeded, but which cards land where
 * is not this gate's business, and a fixed sequence would break the day the shuffle changes.
 * The one scripted thing is the Vinto call — the round has to *end* for a recording to be
 * filed, and calling at the first opportunity is the shortest legal way there.
 */
function nextMove(view, me, called) {
  if (!view || view.phase === 'scoring') return null;

  const seat = view.players.find((p) => p.id === me);
  if (!seat) return null;

  if (view.phase === 'setup') {
    if (seat.knownCardPositions.length < 2) {
      const position = seat.cards.findIndex((_, i) => !seat.knownCardPositions.includes(i));
      return { type: 'PEEK_SETUP_CARD', payload: { playerId: me, position } };
    }
    return { type: 'FINISH_SETUP', payload: { playerId: me } };
  }

  const toss = view.activeTossIn;
  if (toss?.waitingForInput && !toss.playersReadyForNextTurn.includes(me)) {
    // Vinto is declared at the end of your own turn, which is this window. One call ends the
    // round in one more turn each, which is what makes this gate a minute rather than ten.
    const mine = view.players[toss.originalPlayerIndex]?.id === me;
    if (mine && !view.vintoCallerId && !called.done) {
      called.done = true;
      return { type: 'CALL_VINTO', payload: { playerId: me } };
    }
    return { type: 'PLAYER_TOSS_IN_FINISHED', payload: { playerId: me } };
  }

  // The view carries the seat *index* whose turn it is, not an id: `turnHolderId` is a client
  // convenience that never crosses the wire.
  if (view.players[view.currentPlayerIndex]?.id !== me) return null;
  // A drawn card is put straight down: the point here is a round that ends, not a good one.
  return view.pendingAction
    ? { type: 'DISCARD_CARD', payload: { playerId: me } }
    : { type: 'DRAW_CARD', payload: { playerId: me } };
}

const seats = [
  { socket: ada, token: adaJoined.token, me: dealt.view.viewerId },
  { socket: bo, token: boJoined.token, me: null },
];
seats[1].me = dealt.view.players.find((p) => p.id !== seats[0].me && p.isHuman)?.id;

const called = { done: false };
const sleep = (ms) => new Promise((resolve) => { setTimeout(resolve, ms); });

// A deadline rather than a step count, because most of what this waits for is not a step:
// the confer window is twenty seconds of the room's own clock, and three bots' turns arrive
// when they arrive. Counted in passes, the loop spent its budget in a second of doing nothing
// and reported a round that had not finished as a round that would not.
const deadline = Date.now() + 180_000;
let scored = false;
let step = 0;
while (Date.now() < deadline && !scored) {
  step += 1;
  let acted = false;

  for (const seat of seats) {
    // The coalition confers before the final round's first turn, and the bots hold while it is
    // open. A driver standing in for people has to do what people do: say it is done talking —
    // **once**. Said on every pass it is not an answer but a spin, and the loop burned its
    // whole budget in a second without ever letting the window's own clock run out. The caller
    // is not in the coalition and is refused, which is correct and also silent, so "did I say
    // it" has to be remembered here rather than read back off the room.
    const view = seat.socket.latest();
    if (view?.conferMsRemaining && !seat.conferred) {
      seat.conferred = true;
      seat.socket.send({ type: 'done-conferring', token: seat.token });
      continue;
    }

    const move = nextMove(view, seat.me, called);
    if (process.env.GATE_VERBOSE) {
      const holder = view?.players?.[view.currentPlayerIndex]?.id;
      console.log(`    step ${step} seat ${seat.me} phase=${view?.phase} turn=${holder} toss=${view?.activeTossIn?.waitingForInput} move=${move?.type}`);
    }
    if (!move) continue;
    seat.socket.send({ type: 'action', token: seat.token, action: move });
    try {
      // An `error` counts as an answer: a move decided from a view the other seat has already
      // moved past is refused, and the right response is to read again rather than to wait out
      // a timeout. Waiting on events alone made every overtaken move cost twenty seconds.
      await seat.socket.next(
        (m) => m.type === 'events' || m.type === 'between-rounds' || m.type === 'error',
        20_000,
      );
    } catch {
      // The room said nothing at all. Not a failure of this gate — the loop reads again.
    }
    acted = true;
  }

  scored = seats.some((seat) => seat.socket.latest()?.phase === 'scoring');
  // Always a beat, acted or not: the bots' turns arrive in their own time, and a driver that
  // spun on the view it already had would out-run the table it is sitting at.
  await sleep(acted ? 100 : 400);
}

check('the round is played out to scoring', scored, 'the round never reached scoring');
ada.close();
bo.close();

// --- and it is in the archive, under the hour it was played in --------------------------------
{
  const at = new Date();
  const hour = [
    at.getUTCFullYear(),
    String(at.getUTCMonth() + 1).padStart(2, '0'),
    String(at.getUTCDate()).padStart(2, '0'),
    String(at.getUTCHours()).padStart(2, '0'),
  ].join('/');

  const listed = await fetch(`${BASE}/rounds?from=${hour}`, { headers: keyed });
  const body = listed.status === 200 ? await listed.json() : { rounds: [] };
  check('this hour lists what was played in it', listed.status === 200, `status ${listed.status}`);

  const ours = body.rounds?.find((one) => one.room === ROOM);
  check('the round we just played is in it', Boolean(ours), JSON.stringify(body).slice(0, 200));

  if (ours) {
    check('a listing says which room and round each one is', Boolean(ours.room && ours.round), JSON.stringify(ours));
    check('and which deal, so a crash report can be matched to it', Boolean(ours.gameId), JSON.stringify(ours));

    const fetched = await fetch(`${BASE}/rounds?at=${encodeURIComponent(ours.at)}`, { headers: keyed });
    check('and one of them fetches', fetched.status === 200, `status ${fetched.status}`);

    if (fetched.status === 200) {
      const recording = await fetched.text();
      const replayed = await fetch(`${BASE}/replay`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: recording,
      });
      const verdict = await replayed.json();
      check(
        'and what comes back replays through the real engine',
        verdict.ok === true,
        `${verdict.error ?? ''} ${JSON.stringify(verdict.divergence ?? {})}`,
      );
    }

    // The other half of finding one: a crash report names the deal and never the room, so the
    // deal has to be enough on its own.
    const byGame = await fetch(`${BASE}/rounds?game=${encodeURIComponent(ours.gameId)}`, { headers: keyed });
    check('a round is findable by its deal alone', byGame.status === 200, `status ${byGame.status}`);
    if (byGame.status === 200) {
      const found = await byGame.json();
      check(
        'and that is the same round',
        found.rounds?.some((one) => one.at === ours.at),
        JSON.stringify(found).slice(0, 200),
      );
    }
  }

  const missing = await fetch(`${BASE}/rounds?at=${hour}/NOSUCH-9.json`, { headers: keyed });
  check('a key nobody archived is a 404', missing.status === 404, `status ${missing.status}`);
}

console.log(failures === 0 ? '\nARCHIVE GATE PASS' : `\nARCHIVE GATE FAIL (${failures})`);
process.exit(failures === 0 ? 0 : 1);
