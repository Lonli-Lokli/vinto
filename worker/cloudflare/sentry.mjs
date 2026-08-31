/**
 * Crash reporting for the room, without an SDK.
 *
 * Sentry's envelope endpoint takes a POST of newline-delimited JSON, which is a `fetch` and
 * about sixty lines. `@sentry/cloudflare` would bring a dependency, a bundler step and a
 * chunk of the Worker's size budget for the same three fields — and the size budget is real
 * here (`PLATFORM-GATE.md`: 295 KB gzipped against a 3 MB limit, and the client's wasm bundle
 * is the part with no headroom). This module is the same choice the rest of the repository
 * makes about HTTP frameworks and DI containers: use the platform.
 *
 * What it deliberately does not do is capture a crash the runtime never told us about. A
 * Worker has no signal handlers to install, so there is nothing an SDK would catch here that
 * a `try`/`catch` does not.
 *
 * **Absent-safe.** With no `SENTRY_DSN` this is a no-op, so `wrangler dev` and every gate
 * script run with no account and no network. Same rule as the analytics binding, for the same
 * reason: telemetry must never be a thing you need credentials to develop against.
 */

/**
 * Splits a DSN into the two things a POST needs.
 *
 * A DSN looks like `https://<key>@<host>/<projectId>`. The key is *write-only* — it can
 * submit events and cannot read them — which is why it may sit in a client binary at all.
 * Returns null for anything unparseable, so a typo disables reporting rather than throwing on
 * every request.
 */
export function parseDsn(dsn) {
  if (typeof dsn !== 'string' || dsn.length === 0) return null;
  try {
    const url = new URL(dsn);
    const projectId = url.pathname.replace(/^\//, '');
    if (!url.username || !projectId) return null;
    return {
      key: url.username,
      url: `${url.protocol}//${url.host}/api/${projectId}/envelope/`,
    };
  } catch {
    return null;
  }
}

/**
 * Scrubs an event of anything that could identify a player.
 *
 * The same rule as analytics, and for the same reason: §6c binds this zone to no identifiers,
 * and a stack trace is a place secrets go to hide. Room codes are the specific hazard — they
 * are shared secrets that appear in URLs, so an unscrubbed request URL in an error report
 * publishes one into a store people can read.
 *
 * Applied to the whole serialised event rather than to named fields, because the field that
 * leaks is always the one nobody thought to name.
 *
 * The `\\?` before every quote is what makes that work. Scrubbing runs on the *output* of
 * `JSON.stringify`, so a room code that came from a message or a stack frame arrives already
 * escaped — `room: "7KQ2MP"` is `room: \\"7KQ2MP\\"` by then — and a pattern expecting a
 * bare quote matches nothing and reports clean, which is the worst way for a scrubber to
 * fail. The first version had that hole and leaked both a room code and a seat token;
 * `gate-sentry.mjs` now scrubs a stringified object as well as a raw string, which is the
 * case that actually happens in production.
 */
export function scrub(text) {
  return text
    // ?room=7KQ2MP and /?room=... in any URL that reached an error report
    .replace(/([?&]room=)[A-Za-z0-9]+/gi, '$1<redacted>')
    // A bare six-character room code sitting in a message
    .replace(/\b(room|code)\s*[:=]\s*\\?"?[A-Z0-9]{6}/gi, '$1=<redacted>')
    // Anything that looks like the seat token, which is a credential
    .replace(/([?&]|\\?["'])?(token\\?["']?\s*[:=]\s*\\?"?)[A-Za-z0-9_-]{8,}/gi, '$1$2<redacted>')
    // IPv4, in case a runtime put one in a message
    .replace(/\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/g, '<redacted>');
}

/**
 * Reports one error, if there is anywhere to report it.
 *
 * Fire-and-forget by design. It returns the promise so a caller inside a Durable Object can
 * hand it to `waitUntil`, but nothing awaits it on the request path: a crash reporter that
 * made a failing request slower would be making the failure worse.
 */
export function reportError(env, error, context = {}) {
  const dsn = parseDsn(env?.SENTRY_DSN);
  if (!dsn) return null;

  const event = {
    event_id: crypto.randomUUID().replace(/-/g, ''),
    timestamp: Date.now() / 1000,
    platform: 'javascript',
    level: 'error',
    logger: 'vinto-room',
    server_name: 'vinto-room',
    environment: env.SENTRY_ENV ?? 'production',
    // No `user`, ever. There is no account system and a seat is not a person.
    tags: {
      // Deliberately coarse: which entry point failed, not which room.
      surface: typeof context.surface === 'string' ? context.surface : 'worker',
    },
    // The breadcrumbs task 9.9 asks for: where in a game it went wrong, as numbers and one
    // id. Together they are an address — `gameId` names the deal, `round` names the stored
    // recording (`recording:<round>`), and `actionIndex` is the offset *into that round*, so
    // a report points at one action of one replayable document rather than at a room.
    extra: {
      gameId: typeof context.gameId === 'string' ? context.gameId : undefined,
      round: Number.isFinite(context.round) ? context.round : undefined,
      actionIndex: Number.isFinite(context.actionIndex) ? context.actionIndex : undefined,
      seatCount: Number.isFinite(context.seatCount) ? context.seatCount : undefined,
    },
    exception: {
      values: [{
        type: error?.name ?? 'Error',
        value: String(error?.message ?? error ?? 'unknown'),
        stacktrace: error?.stack ? { frames: framesOf(error.stack) } : undefined,
      }],
    },
  };

  const body = [
    JSON.stringify({ event_id: event.event_id, sent_at: new Date().toISOString() }),
    JSON.stringify({ type: 'event' }),
    scrub(JSON.stringify(event)),
  ].join('\n');

  return fetch(dsn.url, {
    method: 'POST',
    headers: {
      'content-type': 'application/x-sentry-envelope',
      'x-sentry-auth': `Sentry sentry_version=7, sentry_key=${dsn.key}, sentry_client=vinto-room/1`,
    },
    body,
  }).catch(() => {
    // A reporter that throws turns one failure into two. There is nothing to retry: the
    // request that failed has already failed, and this was only going to say so.
  });
}

/** Sentry wants newest frame last; a JS stack is newest first. */
function framesOf(stack) {
  return String(stack)
    .split('\n')
    .slice(1, 30)
    .map((line) => ({ filename: scrub(line.trim()) }))
    .reverse();
}

/**
 * What a room knows about where it was when it broke.
 *
 * Takes the room's parsed state and answers the context [reportError] wants. Kept here, and
 * pure, so `gate-sentry.mjs` can check what it says without standing a room up — the point of
 * this function is as much what it *omits* as what it includes.
 *
 * **It never carries the room code.** A code is a join credential that travels in a URL, and
 * §6c binds this zone to no identifiers; `scrub` would strip one that arrived by accident, and
 * this is the same rule applied on purpose. `gameId` is the deal's own id: it is not a
 * credential, it cannot be used to join anything, and it is what the stored recording carries,
 * which is the only reason to send an id at all.
 *
 * Tolerant of a room that has not been dealt yet, or of state that will not parse — a context
 * builder that throws on an error path turns one failure into two, and the failure it was
 * describing is the one that gets lost.
 */
export function roomContext(state, surface) {
  const context = { surface };
  if (!state || typeof state !== 'object') return context;

  const game = state.game;
  if (game && typeof game.gameId === 'string') context.gameId = game.gameId;

  const rounds = state.session?.rounds;
  // The round being played is one past the ones already filed.
  if (Array.isArray(rounds)) context.round = rounds.length + 1;

  const log = state.log;
  if (Array.isArray(log)) {
    const start = Number.isFinite(state.roundStartLogIndex) ? state.roundStartLogIndex : 0;
    context.actionIndex = Math.max(0, log.length - start);
  }

  const seats = state.seats;
  if (Array.isArray(seats)) context.seatCount = seats.length;

  return context;
}
