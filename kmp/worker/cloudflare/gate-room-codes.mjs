/**
 * Room codes through the real runtime.
 *
 *   npx wrangler dev --port 8787 --local --var ROOM_OPEN:true   # in kmp/worker/cloudflare
 *   node kmp/worker/cloudflare/gate-room-codes.mjs
 *
 * `gate-registry.mjs` checks the Kotlin resolver refuses an unissued code. This checks the
 * thing that actually matters and that no unit test can see: that a refused code **creates no
 * Durable Object**. It is asserted by counting objects with stored data through wrangler's
 * local explorer, before and after — not by reading a 404, which a broken build would also
 * return while quietly having created something first.
 */
const BASE = process.env.GATE_URL ?? 'http://localhost:8787';
const EXPLORER = `${BASE}/cdn-cgi/local/explorer/api/workers/durable_objects/namespaces`;

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};

/** How many Room objects currently hold storage. The number an invented code must not move. */
async function roomObjectCount() {
  const response = await fetch(`${EXPLORER}/vinto-room-Room/objects`);
  if (!response.ok) throw new Error(`explorer unavailable: ${response.status}`);
  const body = await response.json();
  return body.result.filter((o) => o.hasStoredData).length;
}

console.log('\nRoom codes through workerd\n');

// --- an invented code must reach nothing --------------------------------------------------
const before = await roomObjectCount();

const invented = await fetch(`${BASE}/?room=ZZZZZZ`);
check('an invented code is refused', invented.status === 404, `status ${invented.status}`);

// Node's fetch refuses to send an Upgrade header, so the socket path is tested with a real
// socket: connecting to a code the registry never issued must fail rather than open.
const socketRefused = await new Promise((resolve) => {
  const ws = new WebSocket(`${BASE.replace('http', 'ws')}/?room=QQQQQQ`);
  const settle = (opened) => { try { ws.close(); } catch { /* already gone */ } resolve(opened); };
  ws.addEventListener('open', () => settle(false));
  ws.addEventListener('error', () => settle(true));
  setTimeout(() => settle(false), 3000);
});
check('and a socket for one never opens', socketRefused);

const afterInvented = await roomObjectCount();
check(
  'and NO Durable Object was created by either attempt',
  afterInvented === before,
  `${before} → ${afterInvented}`,
);

const missing = await fetch(`${BASE}/`);
check('a request with no code at all is refused', missing.status === 400, `status ${missing.status}`);

// --- minting ------------------------------------------------------------------------------
const minted = await (await fetch(`${BASE}/rooms`, {
  method: 'POST',
  body: JSON.stringify({ isPublic: false, hostNickname: 'Ada' }),
})).json();

check('creating a room returns a code', typeof minted.code === 'string' && minted.code.length === 6, minted.code);
check(
  'minting alone creates no room object — a code is not a room yet',
  (await roomObjectCount()) === before,
);

// --- the code works -----------------------------------------------------------------------
const roomResponse = await fetch(`${BASE}/?room=${minted.code}`);
check('the minted code reaches a room', roomResponse.status === 200, `status ${roomResponse.status}`);

const room = await roomResponse.json();
// A room is a *lobby* now: reaching it does not deal a game, and nothing is dealt until two
// people and a full table have run the countdown down (design R2a).
check('which is a lobby with four seats', room.phase === 'LOBBY' && room.seats.length === 4);
check('and no game dealt yet', room.game === null);
check('the room object now exists', (await roomObjectCount()) === before + 1);

check(
  'the same code twice is the same room, not a second one',
  (await (await fetch(`${BASE}/?room=${minted.code}`)).json()).roomId === room.roomId,
);
check('and reaching it twice created only one object', (await roomObjectCount()) === before + 1);

// --- public and private -------------------------------------------------------------------
const publicRoom = await (await fetch(`${BASE}/rooms`, {
  method: 'POST',
  body: JSON.stringify({ isPublic: true, hostNickname: 'Bo' }),
})).json();

const listing = await (await fetch(`${BASE}/rooms`)).json();
check('a public room is listed', listing.rooms.some((r) => r.code === publicRoom.code));
check('a private room is not', !listing.rooms.some((r) => r.code === minted.code));
check(
  'but the private room is still reachable by its code',
  (await fetch(`${BASE}/?room=${minted.code}`)).status === 200,
);
// What a lobby browser needs and nothing else: how full, how soon, and how to get in. No
// tokens, no hashes, no game.
const LISTABLE = ['code', 'roomId', 'isPublic', 'hostNickname', 'humans', 'seatsFilled', 'startsAtEpochMs'];
check(
  'the listing carries nothing but the room',
  listing.rooms.every((r) => Object.keys(r).every((k) => LISTABLE.includes(k))),
  listing.rooms.length ? Object.keys(listing.rooms[0]).join(',') : 'empty',
);

console.log(`\n${failures === 0 ? 'ROOM CODE GATE PASS' : `ROOM CODE GATE FAIL (${failures})`}\n`);
process.exit(failures === 0 ? 0 : 1);
