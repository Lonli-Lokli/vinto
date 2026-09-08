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
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
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
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
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
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
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
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
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
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
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
            AND timestamp > now() - INTERVAL '${WINDOW_DAYS}' DAY
          GROUP BY ended_by ORDER BY sessions DESC`,
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


/**
 * A bar chart, drawn as SVG on the server.
 *
 * **No script, no library, no request.** A chart is a shape, and a shape is something HTML can
 * already say — so this is `<rect>` elements with numbers in them, computed here, in the same
 * response as the table under it. Nothing to load means nothing to block, nothing to go stale,
 * no third-party host on a page about our own players, and no reason for a reader's network tab
 * to name anybody but us. It also means the page works with JavaScript off, which is a strange
 * thing to care about until the one time it is the reason you can see the numbers.
 *
 * Where a row has a *part* of its total worth seeing — the games that were finished, of the
 * games that were played — it is drawn over the bar rather than beside it, because the question
 * is what share it is and a share is read by comparing two lengths from the same baseline.
 */
function renderChart(rows, chart) {
  if (!chart || !rows.length) return '';

  // Days arrive newest-first because that is how a table wants them, and a chart wants the
  // opposite: time runs left to right or it is not a time axis.
  const ordered = chart.overTime ? [...rows].reverse() : rows;
  const points = ordered.map((row) => ({
    label: String(row[chart.x] ?? ''),
    value: Number(row[chart.y]) || 0,
    part: chart.of == null ? null : Number(row[chart.of]) || 0,
  }));

  const top = Math.max(...points.map((p) => p.value), 1);
  const band = CHART_W / points.length;
  const width = Math.max(2, Math.min(band - CHART_GAP, CHART_BAR_MAX));
  const floor = CHART_H - CHART_FOOT;
  const room = floor - CHART_HEAD;

  const bar = (p, i) => {
    const x = i * band + (band - width) / 2;
    const h = (p.value / top) * room;
    const parts = [
      `<rect x="${x.toFixed(1)}" y="${(floor - h).toFixed(1)}"`
      + ` width="${width.toFixed(1)}" height="${h.toFixed(1)}" class="bar"/>`,
    ];
    if (p.part != null) {
      const ph = (p.part / top) * room;
      parts.push(
        `<rect x="${x.toFixed(1)}" y="${(floor - ph).toFixed(1)}"`
        + ` width="${width.toFixed(1)}" height="${ph.toFixed(1)}" class="part"/>`,
      );
    }
    return parts.join('');
  };

  // Enough ticks to read the axis, never so many they collide: a month of days at eight labels
  // is one every four, and a handful of categories gets all of them.
  const every = Math.ceil(points.length / CHART_TICKS);
  const tick = (p, i) =>
    i % every === 0
      ? `<text x="${(i * band + band / 2).toFixed(1)}" y="${CHART_H - 5}" class="tick">`
        + `${escapeHtml(shortLabel(p.label))}</text>`
      : '';

  const legend = chart.of == null
    ? ''
    : `<p class="legend"><span class="swatch bar"></span>${escapeHtml(chart.y)}`
      + `<span class="swatch part"></span>${escapeHtml(chart.of)}</p>`;

  return `<svg viewBox="0 0 ${CHART_W} ${CHART_H}" class="chart" role="img"`
    + ` aria-label="${escapeHtml(chart.y)} by ${escapeHtml(chart.x)}, highest ${format(top)}">`
    + `<line x1="0" y1="${floor}" x2="${CHART_W}" y2="${floor}" class="axis"/>`
    + points.map(bar).join('')
    + points.map(tick).join('')
    + `</svg><p class="peak">highest: ${format(top)}</p>${legend}`;
}

/** A date is read by its day, not by its century: "2026-09-08" is "09-08" on an axis. */
function shortLabel(label) {
  const date = /^\d{4}-(\d{2}-\d{2})/.exec(label);
  return date ? date[1] : label.length > LABEL_MAX ? `${label.slice(0, LABEL_MAX)}…` : label;
}

const CHART_W = 720;
const CHART_H = 170;
const CHART_HEAD = 8;
const CHART_FOOT = 24;
const CHART_GAP = 4;
const CHART_BAR_MAX = 44;
const CHART_TICKS = 8;
const LABEL_MAX = 12;

/** The page. Plain HTML and one inline stylesheet — no build step, no framework, no fetch. */
export function renderPage(sections) {
  const body = sections
    .map(
      (section) => `<section>
        <h2>${escapeHtml(section.title)}</h2>
        <p class="note">${escapeHtml(section.note)}</p>
        ${section.error
          ? `<p class="error">${escapeHtml(section.error)}</p>`
          : renderChart(section.rows, section.chart) + renderRows(section.rows)}
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
  .chart { width: 100%; max-width: 760px; height: auto; display: block; margin: 4px 0 2px; }
  .bar { fill: #2c5f46; }
  .part { fill: #3fd07a; }
  .axis { stroke: #2b323a; stroke-width: 1; }
  .tick { fill: #6f7780; font-size: 11px; text-anchor: middle;
          font-family: system-ui, sans-serif; }
  .peak, .legend { color: #6f7780; font-size: 12px; margin: 0 0 10px; }
  .legend { display: flex; align-items: center; gap: 6px; }
  .swatch { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
  .swatch.part { margin-left: 10px; }
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
      chart: query.chart,
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
