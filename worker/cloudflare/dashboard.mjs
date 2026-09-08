import { keyMatches } from './secrets.mjs';

export { keyMatches };

/**
 * The dashboard: six questions about the audience, answered from Workers Analytics Engine.
 *
 * The questions are "how much was played, by how many people at once, for how long, and did
 * they finish" — offline and online kept apart, because they are different products with
 * different costs. Nothing here counts *people*: there is no identifier to count them with and
 * deliberately so (`AnalyticsPrivacyTest`), so every row is plays rather than players, and the
 * closest thing to an audience number is [together] — how many humans sat at one table.
 *
 * Server-side by design (§A6). The API token that can read the account's analytics never
 * reaches a browser, and there is no client-side querying.
 *
 * ### This Worker answers with NUMBERS; `stats.kupalinka.app` draws them
 *
 * There is one dashboard for the whole portfolio and it is not here. What is here is
 * [serveStats] — `GET /stats.json`, the same `{ tiles, panels }` the page renders, as JSON.
 *
 * **The split is by what each side actually knows.** These queries encode this dataset's own
 * layout — which double is a duration, which blob is a difficulty — and that layout is decided
 * by `shared/protocol/.../Analytics.kt` in *this* repository. Queries living next to the schema
 * they read cannot drift from it silently; the same queries in the dashboard's repository can,
 * and the drift would surface as a chart of plausible wrong numbers rather than as an error.
 * Drawing, on the other hand, is nothing to do with Vinto — so the page, the chart library and
 * the period picker all belong to the dashboard, once, for every game.
 *
 * It also means the dashboard needs no list of games and no per-game code: it reads
 * `kupalinka.app/games.json`, fetches whatever `stats` URL a game publishes, and renders what
 * comes back. A game that has nothing to say simply has no such URL.
 *
 * **Absent-safe like everything else on this Worker.** With no `ANALYTICS_TOKEN`,
 * `ANALYTICS_ACCOUNT_ID` or `STATS_KEY` the route answers 404 and behaves as if it were not
 * there, so `wrangler dev` and every gate script run without a Cloudflare account.
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

/**
 * The periods the page offers, and the one it opens on.
 *
 * A month is what the free plan retains, so there is nothing to show beyond it and offering it
 * would be offering an empty chart. Days rather than hours because every series here is grouped
 * by `toDate`: an hour axis would need a date function this cannot be tested against, since the
 * Analytics Engine SQL API is the one part `wrangler dev` does not emulate.
 */
export const PERIODS = [7, 14, 30];
export const WINDOW_DAYS = 30;

/** The asked-for period, or the default — never a number a caller invented. */
export function periodFrom(value) {
  const days = Number(value);
  return PERIODS.includes(days) ? days : WINDOW_DAYS;
}

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
 * The six questions worth having (task 5.2), over whatever period was asked for.
 *
 * A function rather than a constant because the page has a period picker now, and a window
 * baked into the SQL is a window nobody can change without a deploy. Each is one statement,
 * because the SQL API takes one; they are data rather than spread through the renderer so the
 * set can be read, reviewed and tested without a network. `gate-dashboard.mjs` asserts every
 * one of them names the dataset, bounds its window and weights its counts, which are the three
 * ways one of these goes quietly wrong.
 */
export function queriesFor(days = WINDOW_DAYS) {
  return [
  {
    id: 'solo_daily',
    chart: { x: 'day', y: 'games', of: 'finished', overTime: true },
    title: 'Games played offline, per day',
    note: 'One row per day. A solo round is one game of Vinto against three bots; `finished` is the ones played through to the score sheet. Thirty played and none finished is a worse sign than thirty not played.',
    sql: `SELECT toDate(timestamp) AS day,
                 sum(${WEIGHT}) AS games,
                 sum(${WEIGHT} * double2) AS finished,
                 avg(double4) / 60000 AS avg_minutes
          FROM ${DATASET}
          WHERE index1 = 'solo_round'
            AND timestamp > now() - INTERVAL '${days}' DAY
          GROUP BY day ORDER BY day DESC`,
  },
  {
    id: 'online_daily',
    chart: { x: 'day', y: 'rounds', overTime: true },
    title: 'Games played online, per day',
    note: 'One row per day. A round the room dealt and finished, its length on the clock, and what it cost the Durable Object to host — the number that decides whether online play can stay free.',
    sql: `SELECT toDate(timestamp) AS day,
                 sum(${WEIGHT}) AS rounds,
                 avg(double3) / 60000 AS avg_minutes,
                 avg(double5) / 1000 AS avg_cpu_seconds,
                 avg(double6) AS avg_requests
          FROM ${DATASET}
          WHERE index1 = 'round_end'
            AND timestamp > now() - INTERVAL '${days}' DAY
          GROUP BY day ORDER BY day DESC`,
  },
  {
    id: 'together',
    chart: { x: 'humans', y: 'rounds' },
    title: 'How many people played online together',
    note: 'Every dealt round by the size of the table it was dealt to: four people, or two people and two bots, and so on. This is the one number that says whether online play is doing what it exists for, and no identity is involved — the room counts the seats in front of it.',
    sql: `SELECT double2 AS humans, double3 AS bots, sum(${WEIGHT}) AS rounds
          FROM ${DATASET}
          WHERE index1 = 'round_start'
            AND timestamp > now() - INTERVAL '${days}' DAY
          GROUP BY humans, bots ORDER BY rounds DESC`,
  },
  {
    id: 'sessions_daily',
    chart: { x: 'day', y: 'sessions', overTime: true },
    title: 'Online sessions per day, and how long they last',
    note: 'A session is one room from its first deal to its last round, so this is the count of games-with-friends rather than of rounds. Rounds per session is how many they stayed for.',
    sql: `SELECT toDate(timestamp) AS day,
                 sum(${WEIGHT}) AS sessions,
                 avg(double2) AS avg_rounds,
                 avg(double3) / 60000 AS avg_minutes
          FROM ${DATASET}
          WHERE index1 = 'session_ended'
            AND timestamp > now() - INTERVAL '${days}' DAY
          GROUP BY day ORDER BY day DESC`,
  },
  {
    id: 'solo_finishing',
    chart: { x: 'difficulty', y: 'games', of: 'finished' },
    title: 'Offline: finished against walked away from',
    note: 'The whole window rather than per day, split by difficulty, because the ratio is the point and a day is too few games to read one from. A difficulty people start and never finish is a difficulty that is wrong.',
    sql: `SELECT blob1 AS difficulty,
                 sum(${WEIGHT}) AS games,
                 sum(${WEIGHT} * double2) AS finished,
                 sum(${WEIGHT} * (1 - double2)) AS abandoned,
                 avg(double4) / 60000 AS avg_minutes
          FROM ${DATASET}
          WHERE index1 = 'solo_round'
            AND timestamp > now() - INTERVAL '${days}' DAY
          GROUP BY difficulty ORDER BY games DESC`,
  },
  {
    id: 'session_endings',
    chart: { x: 'ended_by', y: 'sessions' },
    title: 'Online: how sessions end',
    note: 'Played out is success. Too-few-humans and everybody-left at round one are not, and they are different problems: one is nobody arriving, the other is people arriving and leaving.',
    sql: `SELECT blob1 AS ended_by,
                 sum(${WEIGHT}) AS sessions,
                 avg(double2) AS avg_rounds,
                 avg(double3) / 60000 AS avg_minutes
          FROM ${DATASET}
          WHERE index1 = 'session_ended'
            AND timestamp > now() - INTERVAL '${days}' DAY
          GROUP BY ended_by ORDER BY sessions DESC`,
  },
];
}

/** The default set, for anything that wants the shape without choosing a period. */
export const QUERIES = queriesFor();

/**
 * Whether this deployment can read the counts at all.
 *
 * Two secrets, not three. `DASHBOARD_KEY` used to be required, which was right when a key in the
 * URL was the only door; on a host behind Cloudflare Access it is a second lock on a locked door,
 * and demanding it would mean a correctly-protected deployment answering 404 for want of a
 * password nobody needs. It is still honoured when set — see [serveDashboard].
 */
export function dashboardConfigured(env) {
  return Boolean(env?.ANALYTICS_TOKEN && env?.ANALYTICS_ACCOUNT_ID);
}

/**
 * Length-independent comparison of the key in the URL against the secret.
 *
 * Not because timing is a plausible attack on a read-only page of aggregate counts, but
 * because `===` on a secret is the habit that matters in the next place it is written.
 */
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

/** Numbers the browser will format; the server only decides which are rows and which are labels. */
function format(value) {
  if (typeof value !== 'number') return value ?? '';
  return Number.isInteger(value) ? value : Number(value.toFixed(2));
}

/**
 * Every section's rows, as JSON, for the page to draw.
 *
 * The split is the security property: the SQL and the account's read token stay on the server,
 * and what crosses to the browser is aggregate rows with no identity in them — which is all the
 * dataset holds in the first place ([AnalyticsPrivacyTest] makes it so). A browser that could
 * query would be a browser holding a token that can read the whole account's analytics.
 */
export async function dashboardData(env, days = WINDOW_DAYS) {
  const panels = await Promise.all(
    queriesFor(days).map(async (query) => {
      const answer = await runQuery(env, query.sql);
      return {
        id: query.id,
        title: query.title,
        note: query.note,
        chart: query.chart ?? null,
        rows: (answer.rows ?? []).map((row) => {
          const out = {};
          for (const [key, value] of Object.entries(row)) out[key] = format(value);
          return out;
        }),
        error: answer.error ?? null,
      };
    }),
  );

  return { days, tiles: tilesFrom(panels), panels };
}

/**
 * The headline row, computed here rather than in the page.
 *
 * A dashboard opens with the four or five numbers somebody came for, and only then shows the
 * shapes behind them — the notes under every panel were doing that job in prose, which is a
 * thing to read rather than a thing to see. Each of these is an exact total over the window, not
 * an average of averages: a mean of daily means is a number that looks right and is not.
 */
function tilesFrom(panels) {
  const rowsOf = (id) => panels.find((p) => p.id === id)?.rows ?? [];
  const total = (rows, field) => rows.reduce((sum, row) => sum + (Number(row[field]) || 0), 0);

  const solo = rowsOf('solo_finishing');
  const played = total(solo, 'games');
  const finished = total(solo, 'finished');
  const together = rowsOf('together');
  const commonest = together.reduce(
    (best, row) => (best == null || Number(row.rounds) > Number(best.rounds) ? row : best),
    null,
  );

  return [
    { label: 'Games offline', value: format(played) },
    {
      label: 'Finished',
      value: played ? `${Math.round((finished / played) * 100)}%` : '—',
      hint: played ? `${format(finished)} of ${format(played)}` : 'nothing played yet',
    },
    { label: 'Rounds online', value: format(total(rowsOf('online_daily'), 'rounds')) },
    { label: 'Sessions', value: format(total(rowsOf('sessions_daily'), 'sessions')) },
    {
      label: 'Usual table',
      value: commonest ? `${format(commonest.humans)}H` : '—',
      hint: commonest ? `${format(commonest.humans)} people, ${format(commonest.bots)} bots` : 'no rounds yet',
    },
  ];
}

/**
 * `GET /stats.json` — this game's contribution to the one dashboard.
 *
 * ## The contract, which every game in the portfolio answers the same way
 *
 *     GET /stats.json?days=<7|14|30>          x-stats-key: <STATS_KEY>
 *     -> { game, days, tiles: [{label, value, hint?}], panels: [{id, title, note, chart, rows, error}] }
 *
 * `tiles` are the handful of numbers somebody came for; `panels` are the shapes behind them, each
 * a list of already-formatted rows plus the chart type to draw them as. Nothing here is Vinto's
 * vocabulary — a dashboard can render this without knowing what a coalition is, which is the
 * whole point of the split.
 *
 * ## Why it is a key and not Cloudflare Access
 *
 * Access guards a *browser* reaching `stats.kupalinka.app`; this is the dashboard's Worker
 * reaching this one, server to server, where there is no human to challenge and no session to
 * carry. One shared `STATS_KEY` across the portfolio rather than one per game, because a secret
 * per game is a registry the dashboard would have to maintain — and `px`'s own config says at
 * length why a hand-maintained list of games is wrong exactly when something is happening.
 *
 * **Unset means closed, never open.** No `STATS_KEY` and the route is 404 — the same answer as a
 * path that does not exist, so a prober cannot tell a game that is missing its secret from one
 * that never published stats. The counts are aggregate and carry no identifier
 * (`AnalyticsPrivacyTest`), so this is not protecting people; it is refusing to publish the
 * business's own numbers to anyone who guesses the path.
 *
 * Returns null when this is not that route, so the caller's router reads as a list of routes
 * rather than a nest of conditions.
 */
export async function serveStats(request, env, url, path = '/stats.json') {
  if (url.pathname !== path) return null;

  // An unconfigured deployment has no numbers and says exactly that.
  if (!dashboardConfigured(env)) return new Response('not found', { status: 404 });
  if (!env.STATS_KEY || !keyMatches(request.headers.get('x-stats-key'), env.STATS_KEY)) {
    return new Response('not found', { status: 404 });
  }

  // The period is read here and validated by [periodFrom], so a number somebody typed into the
  // address bar cannot reach the SQL — the window is interpolated into a statement, and the one
  // rule for that is that it is never a value a caller chose.
  const days = periodFrom(url.searchParams.get('days'));
  const data = await dashboardData(env, days);

  // Never cached and never indexed: a stale number is worse than none, because it is the one
  // somebody will act on.
  return new Response(JSON.stringify({ game: GAME, ...data }), {
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-robots-tag': 'noindex, nofollow',
    },
  });
}

/** Which game these numbers are, so the dashboard can label the section without being told. */
export const GAME = 'vinto';
