/**
 * The dashboard: six questions, answered from Workers Analytics Engine, rendered here.
 *
 * Server-side by design (§A6). The API token that can read the account's analytics never
 * reaches a browser, there is no client-side querying, and there is no second app to deploy —
 * the thing that already holds the data serves the page about it.
 *
 * **Absent-safe like everything else on this Worker.** With no `ANALYTICS_TOKEN`,
 * `ANALYTICS_ACCOUNT_ID` or `DASHBOARD_KEY` the route answers 404 and behaves as if it were
 * not there, so `wrangler dev` and every gate script run without a Cloudflare account.
 *
 * The SQL API is the one part of Analytics Engine that `wrangler dev` does not emulate: local
 * `writeDataPoint` calls go nowhere queryable. So the queries below are gated for their
 * *shape* (`gate-dashboard.mjs`) and the route for its refusals, and the numbers themselves
 * cannot be seen until a deployment has traffic. That is recorded in tasks.md rather than
 * ticked.
 */

/** Where the SQL API lives. One statement per request, plain text in, JSON out. */
const SQL_API = (accountId) =>
  `https://api.cloudflare.com/client/v4/accounts/${accountId}/analytics_engine/sql`;

/** The dataset named by the `ANALYTICS` binding in wrangler.jsonc. */
export const DATASET = 'vinto_events';

/** How far back every query looks. A month is what the free plan retains. */
export const WINDOW_DAYS = 30;

/**
 * Weighting, and why every sum has two factors in it.
 *
 * There are **two** samplings in play and they are not the same thing. Analytics Engine may
 * sample on the way in under load and reports what it did as `_sample_interval`; this app
 * samples high-frequency events on the way out and declares its own rate in `double1` (§A8).
 * A count that ignores either under-reports, so every aggregate below multiplies by both.
 * `double1` is 1.0 for every event that is never sampled, which is most of them.
 */
const WEIGHT = '_sample_interval * double1';

/**
 * The six questions worth having (task 5.2).
 *
 * Each is one statement, because the SQL API takes one. They are here as data rather than
 * spread through the renderer so the set can be read, reviewed and tested without a network:
 * `gate-dashboard.mjs` asserts every one of them names the dataset, bounds its window and
 * weights its counts, which are the three ways one of these goes quietly wrong.
 */
export const QUERIES = [
  {
    id: 'acquisition',
    title: 'People opening the app',
    note: 'One row per day. Counts the app being opened, not a device — nothing here can tell a returning player from a new one, which is the trade HOSTING.md §6c makes on purpose.',
    sql: `SELECT toDate(timestamp) AS day, sum(${WEIGHT}) AS opens
          FROM ${DATASET}
          WHERE index1 = 'funnel' AND blob1 = 'APP_OPENED'
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
          GROUP BY day ORDER BY day DESC`,
  },
  {
    id: 'activation',
    title: 'Rounds actually finished',
    note: 'A round played to the score sheet, against one walked out of. The ratio is the activation number: an app people open and abandon mid-round is a different problem from one they do not open.',
    sql: `SELECT blob1 AS difficulty,
                 sum(${WEIGHT} * double2) AS finished,
                 sum(${WEIGHT} * (1 - double2)) AS abandoned,
                 avg(double4) / 1000 AS avg_seconds
          FROM ${DATASET}
          WHERE index1 = 'solo_round'
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
          GROUP BY difficulty ORDER BY finished DESC`,
  },
  {
    id: 'funnel',
    title: 'The online funnel',
    note: 'Opened → pressed Online → asked for a room → shared the invite → somebody joined. The step that loses the most people is the one worth working on, and the invite step is why deep links are next.',
    sql: `SELECT blob1 AS step, sum(${WEIGHT}) AS reached
          FROM ${DATASET}
          WHERE index1 = 'funnel'
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
          GROUP BY step ORDER BY reached DESC`,
  },
  {
    id: 'sessions',
    title: 'Rounds per online session, and how sessions end',
    note: 'A session is a room from its deal to its last round. How it ended matters as much as how long it was: played out is success, everybody-left at round one is not.',
    sql: `SELECT blob1 AS ended_by,
                 sum(${WEIGHT}) AS sessions,
                 avg(double2) AS avg_rounds,
                 avg(double3) / 60000 AS avg_minutes
          FROM ${DATASET}
          WHERE index1 = 'session_ended'
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
          GROUP BY ended_by ORDER BY sessions DESC`,
  },
  {
    id: 'failures',
    title: 'What broke, and where',
    note: 'Only the client reports these: a stalled stage, a lost socket, a refused move. The room cannot see any of them, and each is something a player experienced and nobody would otherwise hear about.',
    sql: `SELECT blob1 AS kind, blob2 AS surface, sum(${WEIGHT}) AS failures
          FROM ${DATASET}
          WHERE index1 = 'failure'
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
          GROUP BY kind, surface ORDER BY failures DESC`,
  },
  {
    id: 'cost',
    title: 'What a round of online play costs',
    note: 'Durable Object wall time and requests, stamped on every round the room finished. This is the number that decides whether online play can stay free, and it is measured rather than estimated.',
    sql: `SELECT toDate(timestamp) AS day,
                 sum(${WEIGHT}) AS rounds,
                 avg(double5) / 1000 AS avg_cpu_seconds,
                 avg(double6) AS avg_requests
          FROM ${DATASET}
          WHERE index1 = 'round_end'
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
          GROUP BY day ORDER BY day DESC`,
  },
];

/** Whether this deployment has everything the dashboard needs. */
export function dashboardConfigured(env) {
  return Boolean(env?.ANALYTICS_TOKEN && env?.ANALYTICS_ACCOUNT_ID && env?.DASHBOARD_KEY);
}

/**
 * Length-independent comparison of the key in the URL against the secret.
 *
 * Not because timing is a plausible attack on a read-only page of aggregate counts, but
 * because `===` on a secret is the habit that matters in the next place it is written.
 */
export function keyMatches(given, expected) {
  if (typeof given !== 'string' || typeof expected !== 'string') return false;
  if (given.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < given.length; i += 1) diff |= given.charCodeAt(i) ^ expected.charCodeAt(i);
  return diff === 0;
}

async function runQuery(env, sql) {
  const response = await fetch(SQL_API(env.ANALYTICS_ACCOUNT_ID), {
    method: 'POST',
    headers: { authorization: `Bearer ${env.ANALYTICS_TOKEN}`, 'content-type': 'text/plain' },
    body: sql,
  });

  if (!response.ok) return { error: `the SQL API answered ${response.status}` };
  const body = await response.json().catch(() => null);
  if (!body || !Array.isArray(body.data)) return { error: 'the SQL API answered something unreadable' };
  return { rows: body.data };
}

/** Anything reaching the page goes through this, including column names. */
export function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function renderRows(rows) {
  if (!rows.length) return '<p class="empty">Nothing yet.</p>';
  const columns = Object.keys(rows[0]);
  const head = columns.map((c) => `<th>${escapeHtml(c)}</th>`).join('');
  const body = rows
    .map((row) => `<tr>${columns.map((c) => `<td>${escapeHtml(format(row[c]))}</td>`).join('')}</tr>`)
    .join('');
  return `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
}

function format(value) {
  if (typeof value !== 'number') return value ?? '';
  return Number.isInteger(value) ? value : value.toFixed(2);
}

/** The page. Plain HTML and one inline stylesheet — no build step, no framework, no fetch. */
export function renderPage(sections) {
  const body = sections
    .map(
      (section) => `<section>
        <h2>${escapeHtml(section.title)}</h2>
        <p class="note">${escapeHtml(section.note)}</p>
        ${section.error ? `<p class="error">${escapeHtml(section.error)}</p>` : renderRows(section.rows)}
      </section>`,
    )
    .join('');

  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Vinto — counts</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; padding: 24px; background: #14181d; color: #e8e6e3;
         font: 15px/1.5 system-ui, sans-serif; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  h2 { font-size: 16px; margin: 32px 0 4px; }
  .note, .sub { color: #9aa3ad; font-size: 13px; margin: 0 0 12px; }
  .error { color: #e0796b; }
  .empty { color: #6f7780; font-style: italic; }
  table { border-collapse: collapse; width: 100%; max-width: 760px; }
  th, td { text-align: left; padding: 6px 12px 6px 0; border-bottom: 1px solid #262c33; }
  th { color: #9aa3ad; font-weight: 600; }
</style></head>
<body>
<h1>Vinto — counts</h1>
<p class="sub">The last ${WINDOW_DAYS} days. Anonymous aggregates: there is nothing here that
identifies a person, because there is nowhere in what is collected to put it.</p>
${body}
</body></html>`;
}

/**
 * `GET /counts?key=…`.
 *
 * Returns null when this is not that route, so the caller's router reads as a list of routes
 * rather than a nest of conditions.
 */
export async function serveDashboard(request, env, url) {
  if (url.pathname !== '/counts') return null;

  // An unconfigured deployment does not have a dashboard, and says exactly that — the same
  // answer as a path that does not exist, so a prober cannot tell a service that is missing
  // its secret from one that never had this route.
  if (!dashboardConfigured(env)) return new Response('not found', { status: 404 });
  if (!keyMatches(url.searchParams.get('key'), env.DASHBOARD_KEY)) {
    return new Response('not found', { status: 404 });
  }

  const sections = await Promise.all(
    QUERIES.map(async (query) => ({
      title: query.title,
      note: query.note,
      ...(await runQuery(env, query.sql)),
    })),
  );

  return new Response(renderPage(sections), {
    headers: {
      'content-type': 'text/html; charset=utf-8',
      // Never cached and never indexed: it is a private view, and a stale one is worse than
      // none because the number it shows is the one somebody will act on.
      'cache-control': 'no-store',
      'x-robots-tag': 'noindex, nofollow',
    },
  });
}
