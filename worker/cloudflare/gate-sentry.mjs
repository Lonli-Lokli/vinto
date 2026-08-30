/**
 * Crash reporting: that it reports, that it stays quiet, and that it never leaks a room code.
 *
 *   node worker/cloudflare/gate-sentry.mjs
 *
 * The scrubbing case is the one that matters. A room code is a shared secret that travels in
 * a URL, and a stack trace is exactly where one ends up without anybody deciding it should.
 * §6c binds this zone to no identifiers; this is that rule applied to the pipe nobody thinks
 * of as telemetry.
 */
import { parseDsn, scrub, reportError } from './sentry.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};

console.log('\nsentry: reading a DSN');

const parsed = parseDsn('https://abc123@o1.ingest.us.sentry.io/456');
check('a DSN splits into key and endpoint', parsed?.key === 'abc123', JSON.stringify(parsed));
check(
  'the endpoint is the envelope API for that project',
  parsed?.url === 'https://o1.ingest.us.sentry.io/api/456/envelope/',
  parsed?.url,
);

for (const bad of [undefined, '', 'not-a-url', 'https://no-key@host/1'.replace('no-key@', ''), 'https://key@host/']) {
  check(`a malformed DSN (${JSON.stringify(bad)}) disables reporting instead of throwing`, parseDsn(bad) === null);
}

console.log('\nsentry: what never leaves');

// The case that actually happens: `reportError` scrubs `JSON.stringify(event)`, so by the
// time these patterns run, every quote in a message or a stack frame is escaped. The first
// version of `scrub` expected bare quotes, matched nothing, and reported clean — leaking both
// a room code and a seat token, silently. Raw *and* stringified, from here on.
const escapedLeak = JSON.stringify({
  message: 'join failed room: "7KQ2MP"',
  frame: 'index.mjs {"token":"aGVsbG8td29ybGQtc2VjcmV0"}',
});
const escapedClean = scrub(escapedLeak);
check(
  'a room code inside stringified JSON is scrubbed',
  !escapedClean.includes('7KQ2MP'),
  escapedClean,
);
check(
  'a seat token inside stringified JSON is scrubbed',
  !escapedClean.includes('aGVsbG8td29ybGQtc2VjcmV0'),
  escapedClean,
);
check(
  'and the message is still readable afterwards',
  escapedClean.includes('join failed') && escapedClean.includes('index.mjs'),
  escapedClean,
);


const leaky = [
  'GET wss://vinto-room.example/?room=7KQ2MP failed',
  'at handler (index.mjs) room: "7KQ2MP"',
  '{"token":"aGVsbG8td29ybGQtc2VjcmV0"}',
  'connection from 203.0.113.9 reset',
].join(' | ');
const clean = scrub(leaky);

check('a room code in a URL is redacted', !clean.includes('7KQ2MP'), clean);
check('a seat token is redacted', !clean.includes('aGVsbG8td29ybGQtc2VjcmV0'), clean);
check('an IP address is redacted', !clean.includes('203.0.113.9'), clean);
check('and something is left to read', clean.includes('<redacted>') && clean.includes('failed'), clean);

console.log('\nsentry: with no DSN configured');

let posted = 0;
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => { posted++; return new Response(null, { status: 200 }); };

try {
  check('no DSN sends nothing', reportError({}, new Error('boom')) === null && posted === 0);
  check('an empty DSN sends nothing', reportError({ SENTRY_DSN: '' }, new Error('boom')) === null && posted === 0);

  const sent = reportError(
    { SENTRY_DSN: 'https://abc123@o1.ingest.us.sentry.io/456' },
    new Error('boom'),
    { surface: 'room-socket', actionIndex: 12 },
  );
  await sent;
  check('a configured DSN posts one envelope', posted === 1, `posted ${posted}`);

  // The body is built and scrubbed before it is sent, so a leak here is a leak in production.
  let body = '';
  globalThis.fetch = async (url, init) => { body = init.body; return new Response(null, { status: 200 }); };
  const err = new Error('failed for ?room=7KQ2MP');
  await reportError({ SENTRY_DSN: 'https://abc123@o1.ingest.us.sentry.io/456' }, err, { surface: 'worker' });
  check('the sent envelope carries no room code', !body.includes('7KQ2MP'), body.slice(0, 200));
  check('the sent envelope names no user', !/"user"\s*:/.test(body));
  check('the sent envelope carries the surface', body.includes('worker'));

  globalThis.fetch = async () => { throw new Error('network down'); };
  await reportError({ SENTRY_DSN: 'https://abc123@o1.ingest.us.sentry.io/456' }, new Error('boom'));
  check('a reporter that cannot reach Sentry does not throw', true);
} finally {
  globalThis.fetch = realFetch;
}

console.log(failures === 0 ? '\nsentry gate: PASS\n' : `\nsentry gate: ${failures} FAILED\n`);
process.exit(failures === 0 ? 0 : 1);
