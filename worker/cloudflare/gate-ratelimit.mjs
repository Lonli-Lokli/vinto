// Volume is bounded at the door.
//
//   npx wrangler dev --port 8787 --local --var ROOM_OPEN:true     # in worker/cloudflare
//   node gate-ratelimit.mjs
//
// Everything else in this service bounds a *total* — rooms per source, actions per seat,
// sockets per room, guesses per address. None of them bounds a *rate*, and the two cheapest
// requests here are the most expensive to answer: `POST /replay` runs a whole game through the
// engine (~250 ms of Durable Object CPU, the dimension Cloudflare bills), and `GET /rooms`
// wakes the single registry object, which is single-threaded and shared by every player.
//
// This gate exists because of what `wrangler.jsonc` used to say: that edge rate limiting was a
// dashboard rule which could not be expressed in the config and could not be exercised by
// `wrangler dev`, so writing it down would be "a claim nothing verifies". `ratelimits` is a
// first-class binding now. This is the something that verifies it.

const BASE = process.env.GATE_URL ?? 'http://localhost:8787';

// Its own address, for the same reason gate-bruteforce.mjs has one: the limiter is per source
// and every other gate shares one, so a gate that spends the shared allowance breaks the gate
// beside it. Cloudflare overwrites this header at the edge, so a real client cannot pick its
// own bucket. TEST-NET-3, never a real host.
const FLOODER = { 'cf-connecting-ip': '203.0.113.20' };
const BYSTANDER = { 'cf-connecting-ip': '203.0.113.21' };

let failures = 0;
const check = (name, ok, detail = '') => {
  if (ok) console.log(`  pass  ${name}`);
  else { failures += 1; console.log(`  FAIL  ${name}${detail ? ` — ${detail}` : ''}`); }
};

console.log(`Door volume against ${BASE}\n`);

// --- a liveness answer is never rate limited -------------------------------------------------
//
// Asked first and asked again at the end. `/health` sits above the limiter on purpose: it costs
// nothing to answer and it is the one thing a monitor must be able to ask while something is
// going wrong, which is exactly when a limiter is biting.
{
  const res = await fetch(`${BASE}/health`, { headers: FLOODER });
  check('health answers to begin with', res.status === 200, `status ${res.status}`);
}

// --- the flood ------------------------------------------------------------------------------
//
// 200 requests against a limit of 120 a minute. `GET /rooms` because it is the cheap-to-ask,
// expensive-to-answer one; the limiter does not care which path it is.
let firstThrottledAt = null;
let served = 0;
for (let i = 1; i <= 200; i++) {
  const res = await fetch(`${BASE}/rooms`, { headers: FLOODER });
  if (res.status === 429) { if (firstThrottledAt === null) firstThrottledAt = i; }
  else if (res.status === 200) served += 1;
}

check(
  'a flood is cut off',
  firstThrottledAt !== null,
  'never throttled in 200 requests — the binding is absent or not emulated',
);
check(
  'and not before a real player would have finished',
  firstThrottledAt === null || firstThrottledAt > 60,
  `first 429 at request ${firstThrottledAt}; anybody refreshing a lobby does under ten`,
);
check(
  'and the allowance is roughly the one configured',
  served >= 60 && served <= 150,
  `${served} served against a configured 120 a minute`,
);

// --- one flooder does not take the service down ----------------------------------------------
//
// The failure this rules out is a limiter keyed on nothing, where a single host throttles
// everybody — which is a worse outcome than the flood.
{
  const res = await fetch(`${BASE}/rooms`, { headers: BYSTANDER });
  check(
    'a different visitor is still served',
    res.status === 200,
    `status ${res.status} — the limiter is counting globally, not per source`,
  );
}

// --- and liveness is still answerable while the limiter bites ---------------------------------
{
  const res = await fetch(`${BASE}/health`, { headers: FLOODER });
  check('health still answers a throttled source', res.status === 200, `status ${res.status}`);
}

console.log(failures === 0 ? '\nRATE LIMIT GATE PASS' : `\nRATE LIMIT GATE FAIL (${failures})`);
process.exit(failures === 0 ? 0 : 1);
