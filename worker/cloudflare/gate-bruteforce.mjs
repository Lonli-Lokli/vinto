// A private room cannot be found by guessing.
//
//   npx wrangler dev --port 8787 --local --var ROOM_OPEN:true     # in worker/cloudflare
//   node gate-bruteforce.mjs
//
// A private room is reachable by its code and by nothing else — it is never listed, and
// `listPublicRooms` cannot name it. So the code is the only thing between a stranger and
// somebody's table, and the question is how expensive it is to guess.
//
// The keyspace is 31^6 = 887,503,681, and at most 200 rooms are alive at once, so one guess
// hits with probability about 2.3 in ten million. Even odds needs roughly three million
// guesses — hours from one host, well under an hour spread across a few. That is worth
// counting, which is what `CODE_ALPHABET`'s comment has always said and what nothing actually
// did: design R6 put the rate limit in a Cloudflare dashboard rule, `wrangler.jsonc` recorded
// that it was deliberately not half-configured there, and it was never configured at all.
//
// Two things a zone rule could not have done anyway, which is why the limiter is in the
// registry: it cannot see inside a WebSocket, which is how a room is joined, and it cannot
// tell a wrong code from a right one, so it would throttle real players at the same rate.

const BASE = process.env.GATE_URL ?? 'http://localhost:8787';

/**
 * This gate guesses from its **own** address, and that is not decoration.
 *
 * The limiter is per source, and every other gate shares one — so a run that spent the shared
 * allowance made `gate-room-codes` fail on "an invented code is refused — status 429", which
 * is a guard breaking the guard beside it for the second time in this session. Sending
 * `cf-connecting-ip` gives this gate a bucket of its own.
 *
 * Safe, and realistic: Cloudflare overwrites this header at the edge, so a real client cannot
 * choose its own source. `wrangler dev --local` passes it through, which is what lets a gate
 * stand in for two different visitors.
 */
const GUESSER = { 'cf-connecting-ip': '203.0.113.7' };   // TEST-NET-3, never a real host
const BYSTANDER = { 'cf-connecting-ip': '203.0.113.8' };

let failures = 0;
const check = (name, ok, detail = '') => {
  if (ok) console.log(`  pass  ${name}`);
  else { failures += 1; console.log(`  FAIL  ${name}${detail ? ` — ${detail}` : ''}`); }
};

console.log(`Brute force against ${BASE}\n`);

const ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
const wellFormed = (n) =>
  Array.from({ length: 6 }, (_, i) => ALPHABET[(n * 7 + i * 13) % ALPHABET.length]).join('');

// A real private room to hunt for, so "not found" means the limiter and not an empty registry.
const made = await (await fetch(`${BASE}/rooms`, {
  method: 'POST',
  body: JSON.stringify({ isPublic: false, hostNickname: 'quarry' }),
})).json();
check('a private room exists to be hunted', typeof made.code === 'string', JSON.stringify(made));

// --- guessing is answered, then stopped -----------------------------------------------------
//
// Every guess below is well-formed, so `looksLikeRoomCode` lets it through to the registry:
// this measures the limiter, not the shape check in front of it.
let firstThrottledAt = null;
let sawMiss = false;
for (let i = 1; i <= 30; i++) {
  const guess = wellFormed(i);
  if (guess === made.code) continue; // astronomically unlikely; correctness before luck
  const res = await fetch(`${BASE}/?room=${guess}`, { headers: GUESSER });
  if (res.status === 404) sawMiss = true;
  if (res.status === 429 && firstThrottledAt === null) firstThrottledAt = i;
}

check('a wrong code is refused', sawMiss);
check(
  'and guessing is cut off well before the keyspace matters',
  firstThrottledAt !== null && firstThrottledAt <= 25,
  firstThrottledAt === null ? 'never throttled in 30 guesses' : `first 429 at guess ${firstThrottledAt}`,
);

// --- the refusal is not an oracle -----------------------------------------------------------
//
// A throttled source is refused whether or not it guessed right. If the real code answered
// differently once the limit had bitten, the limiter would be a way to *confirm* a guess
// rather than a way to stop them.
{
  const res = await fetch(`${BASE}/?room=${made.code}`, { headers: GUESSER });
  check(
    'and a throttled source is refused even when it is right',
    res.status === 429,
    `status ${res.status} for the real code`,
  );
}

// --- and the shape check still costs the registry nothing ------------------------------------
{
  const res = await fetch(`${BASE}/?room=nope`, { headers: GUESSER });
  check('a malformed code is still refused by the stateless half', res.status === 404, `status ${res.status}`);
}

// --- and one guesser does not lock out everybody else ----------------------------------------
//
// The failure this rules out is a limiter that counts globally: throttle the service instead of
// the source and a single scanner takes online play down for everyone, which is a worse
// outcome than the scan.
{
  const res = await fetch(`${BASE}/?room=${made.code}`, { headers: BYSTANDER });
  check(
    'a different visitor still reaches the room',
    res.status !== 429,
    `status ${res.status} — the limiter is counting globally, not per source`,
  );
}

console.log(failures === 0 ? '\nBRUTE FORCE GATE PASS' : `\nBRUTE FORCE GATE FAIL (${failures})`);
process.exit(failures === 0 ? 0 : 1);
