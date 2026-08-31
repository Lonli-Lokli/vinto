// The browser is allowed to read the room service's answers.
//
//   npx wrangler dev --port 8787 --local --var ROOM_OPEN:true     # in worker/cloudflare
//   node gate-cors.mjs
//
// Against a real deployment:
//   GATE_URL=https://vinto-room.kupalinka.app node gate-cors.mjs
//
// What this exists for. The website and this service are different **origins** —
// `vinto.kupalinka.app` against `vinto-room.kupalinka.app` — so every `fetch` the browser
// client makes is cross-origin. Without `access-control-allow-origin` on the response the
// browser performs the request, receives the answer, and then refuses to hand it to the page:
// `TypeError: Failed to fetch`, with no detail whatsoever. A player sees "No connection to the
// room service. Check the network and try again." — about a call that reached the service and
// came back 200.
//
// It went unnoticed for the life of the project because nothing browser-shaped had ever called
// this service: Android, the JVM and iOS do not enforce CORS, and the web client had no
// deployment to be called from. The first person to open the lobby on the web found it.
//
// Same-site is not same-origin. The room having its own hostname keeps it first-party for
// cookies and CSP and makes no difference at all to CORS, which compares scheme, host and port
// exactly.

const BASE = process.env.GATE_URL ?? 'http://localhost:8787';
const SITE = 'https://vinto.kupalinka.app';

let failures = 0;
const check = (name, ok, detail = '') => {
  if (ok) {
    console.log(`  ok   ${name}`);
  } else {
    failures += 1;
    console.log(`  FAIL ${name}${detail ? ` — ${detail}` : ''}`);
  }
};

console.log(`gate-cors against ${BASE}`);

// --- the call the lobby actually makes ---------------------------------------------------
{
  const res = await fetch(`${BASE}/rooms`, { headers: { origin: SITE } });
  const allow = res.headers.get('access-control-allow-origin');
  check('GET /rooms answers', res.status === 200 || res.status === 503, `status ${res.status}`);
  check('GET /rooms allows the site to read it', allow === SITE, `allow-origin: ${allow}`);
  check(
    'and says the answer varies by Origin',
    (res.headers.get('vary') ?? '').toLowerCase().includes('origin'),
    `vary: ${res.headers.get('vary')}`,
  );
}

// --- a cross-origin POST ------------------------------------------------------------------
//
// `POST /replay` rather than `POST /rooms`, and the reason is worth writing down because the
// first version of this gate got it wrong. Every `POST /rooms` mints a room — there is no
// malformed body that gets refused first, checked — so running this gate consumed one of the
// registry's per-source room allowance and made `gate-room-codes`, which runs after it, fail
// on "creating a room returns a code". A guard that breaks the guard next to it is worse than
// no guard.
//
// `/replay` is a pure function of its argument and holds no state, so it can be POSTed to
// freely. The header is attached to every response by one wrapper, so any cross-origin POST
// proves the mechanism; the endpoint the lobby actually calls is covered by the GET above.
{
  const res = await fetch(`${BASE}/replay`, {
    method: 'POST',
    headers: { origin: SITE },
    body: '{"not":"a recording"}',
  });
  check(
    'a cross-origin POST allows the site to read it',
    res.headers.get('access-control-allow-origin') === SITE,
    `status ${res.status}, allow-origin: ${res.headers.get('access-control-allow-origin')}`,
  );
}

// --- preflight ---------------------------------------------------------------------------
//
// Not what was broken — today's calls are "simple" requests that never trigger one. Checked so
// that the first request to gain a JSON content type or an auth header does not bring the same
// outage back wearing a different hat.
{
  const res = await fetch(`${BASE}/rooms`, {
    method: 'OPTIONS',
    headers: {
      origin: SITE,
      'access-control-request-method': 'POST',
      'access-control-request-headers': 'content-type',
    },
  });
  check('OPTIONS is answered, not refused', res.status === 204, `status ${res.status}`);
  check(
    'preflight allows the site',
    res.headers.get('access-control-allow-origin') === SITE,
    `allow-origin: ${res.headers.get('access-control-allow-origin')}`,
  );
  check(
    'preflight allows POST',
    (res.headers.get('access-control-allow-methods') ?? '').includes('POST'),
    `allow-methods: ${res.headers.get('access-control-allow-methods')}`,
  );
  check(
    'preflight allows a content type',
    (res.headers.get('access-control-allow-headers') ?? '').includes('content-type'),
    `allow-headers: ${res.headers.get('access-control-allow-headers')}`,
  );
}

// --- and not to everybody ------------------------------------------------------------------
//
// `*` would not actually be a vulnerability here — this API carries no cookies and no ambient
// credentials, so a hostile page reading a room listing learns what it could have learned from
// its own server. The boundary is `ActionValidator` and the seat token, not this header. The
// check is here because "who is this for" is worth having written down and enforced, not
// because a wildcard would let anybody in.
{
  const evil = 'https://not-vinto.example';
  const res = await fetch(`${BASE}/rooms`, { headers: { origin: evil } });
  const allow = res.headers.get('access-control-allow-origin');
  check('a stranger is not named in allow-origin', allow !== evil && allow !== '*', `allow-origin: ${allow}`);

  const pre = await fetch(`${BASE}/rooms`, {
    method: 'OPTIONS',
    headers: { origin: evil, 'access-control-request-method': 'POST' },
  });
  check('and its preflight is refused', pre.status === 403, `status ${pre.status}`);
}

// --- a non-browser caller is unaffected ----------------------------------------------------
//
// The phones and the desktop send no Origin. They must keep working exactly as before, which
// means no CORS headers rather than restrictive ones.
{
  const res = await fetch(`${BASE}/health`);
  check('/health still answers without an Origin', res.status === 200, `status ${res.status}`);
  check(
    'and carries no allow-origin when nobody asked',
    res.headers.get('access-control-allow-origin') === null,
    `allow-origin: ${res.headers.get('access-control-allow-origin')}`,
  );
}

console.log(failures === 0 ? '\nGATE CORS PASS' : `\nGATE CORS FAIL (${failures})`);
process.exit(failures === 0 ? 0 : 1);
