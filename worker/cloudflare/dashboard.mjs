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
 * ### This module is on its way to `stats.kupalinka.app`
 *
 * It is no longer routed from this Worker, and `vinto-room.kupalinka.app/counts` is gone. The
 * portfolio already has a stats host, it is behind Cloudflare Access, and a per-game URL with a
 * shared secret in its query string was the odd one out — SSO is a better door than `?key=`, and
 * one page for every game is a better place than one page per game.
 *
 * **The data does not move, because it never had to.** Analytics Engine datasets are scoped to
 * the *account*, so any Worker on it can read `vinto_events` with exactly the SQL below.
 *
 * What stays here is [QUERIES], and that is deliberate: they encode this dataset's own layout —
 * which double is a duration, which blob is a difficulty — and that layout is decided by
 * `shared/protocol/.../Analytics.kt` in this repository. Queries living next to the schema they
 * read cannot drift from it silently; queries living in another repository can, and the drift
 * would show up as a chart of plausible wrong numbers.
 *
 * [renderPage] and [renderChart] are the opposite: nothing about them is Vinto's, and the stats
 * Worker should own them once it has them. This module carries a copy until it does, which is
 * the one honest state between "here" and "there".
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
export async function dashboardData(env) {
  return Promise.all(
    QUERIES.map(async (query) => {
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
}

/**
 * The page: HTML, CSS and JavaScript, drawing the JSON above with Chart.js.
 *
 * It was hand-rolled SVG rendered on the server, which had the virtue of loading nothing and the
 * defect of looking it — no hover, no legend that means anything, no axis a reader can trust,
 * and every improvement paid for in geometry by hand. A dashboard is a thing somebody reads
 * every week; it should look like one.
 *
 * **The library is pinned and hashed.** A `<script>` from a CDN is a supply-chain hole unless the
 * browser is told exactly what it is allowed to run, so the tag carries `integrity` and the CSP
 * below names the two hosts and nothing else. If a byte of that file ever differs from the hash,
 * the browser refuses it and the charts do not draw — which is the failure you want, rather than
 * running somebody else's code on a page about your players. To drop the CDN entirely, serve the
 * file from this Worker and change the one `src`.
 */
export function renderShell() {
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src ${CHART_HOST} 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'">
<title>Vinto — counts</title>
<style>
  :root { color-scheme: dark; --ink: #e8e6e3; --dim: #9aa3ad; --line: #262c33; --panel: #191e24; }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px; background: #14181d; color: var(--ink);
         font: 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
  header { max-width: 1200px; margin: 0 auto 20px; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  .sub { color: var(--dim); font-size: 13px; margin: 0; }
  main { max-width: 1200px; margin: 0 auto; display: grid; gap: 18px;
         grid-template-columns: repeat(auto-fit, minmax(380px, 1fr)); }
  section { background: var(--panel); border: 1px solid var(--line); border-radius: 10px;
            padding: 16px; min-width: 0; }
  h2 { font-size: 15px; margin: 0 0 4px; }
  .note { color: var(--dim); font-size: 12.5px; margin: 0 0 12px; }
  .frame { position: relative; height: 220px; }
  table { border-collapse: collapse; width: 100%; margin-top: 12px; font-size: 13px; }
  th, td { text-align: left; padding: 5px 10px 5px 0; border-bottom: 1px solid var(--line);
           white-space: nowrap; }
  th { color: var(--dim); font-weight: 600; }
  .scroll { overflow-x: auto; }
  .empty { color: #6f7780; font-style: italic; margin: 0; }
  .error { color: #e0796b; margin: 0; }
</style></head>
<body>
<header>
  <h1>Vinto — counts</h1>
  <p class="sub">The last ${WINDOW_DAYS} days. Anonymous aggregates: there is nothing here that
  identifies a person, because there is nowhere in what is collected to put it.</p>
</header>
<main id="board"><p class="empty">Loading…</p></main>
<script src="${CHART_SRC}" integrity="${CHART_SRI}" crossorigin="anonymous"></script>
<script>
(async function () {
  var board = document.getElementById('board');
  var res = await fetch('?format=json', { headers: { accept: 'application/json' } });
  if (!res.ok) { board.innerHTML = '<p class="error">Could not read the counts.</p>'; return; }
  var sections = await res.json();
  board.innerHTML = '';

  var INK = '#e8e6e3', DIM = '#9aa3ad', LINE = '#262c33';
  var BAR = '#2c5f46', PART = '#3fd07a';

  sections.forEach(function (section) {
    var el = document.createElement('section');
    var head = '<h2></h2><p class="note"></p>';
    el.innerHTML = head;
    el.querySelector('h2').textContent = section.title;
    el.querySelector('.note').textContent = section.note;

    if (section.error) {
      var e = document.createElement('p');
      e.className = 'error';
      e.textContent = section.error;
      el.appendChild(e);
      board.appendChild(el);
      return;
    }
    if (!section.rows.length) {
      var n = document.createElement('p');
      n.className = 'empty';
      n.textContent = 'Nothing yet.';
      el.appendChild(n);
      board.appendChild(el);
      return;
    }

    if (section.chart) {
      var spec = section.chart;
      var rows = spec.overTime ? section.rows.slice().reverse() : section.rows;
      var frame = document.createElement('div');
      frame.className = 'frame';
      var canvas = document.createElement('canvas');
      frame.appendChild(canvas);
      el.appendChild(frame);

      var sets = [{ label: spec.y, data: rows.map(function (r) { return r[spec.y]; }),
                    backgroundColor: BAR, borderRadius: 3 }];
      if (spec.of) {
        sets.push({ label: spec.of, data: rows.map(function (r) { return r[spec.of]; }),
                    backgroundColor: PART, borderRadius: 3 });
      }
      new Chart(canvas, {
        type: 'bar',
        data: { labels: rows.map(function (r) { return String(r[spec.x]); }), datasets: sets },
        options: {
          responsive: true, maintainAspectRatio: false,
          scales: {
            x: { grid: { display: false }, ticks: { color: DIM, maxRotation: 0, autoSkip: true } },
            y: { beginAtZero: true, grid: { color: LINE }, ticks: { color: DIM, precision: 0 } }
          },
          plugins: {
            legend: { display: !!spec.of, labels: { color: INK, boxWidth: 12, boxHeight: 12 } },
            tooltip: { mode: 'index', intersect: false }
          }
        }
      });
    }

    var columns = Object.keys(section.rows[0]);
    var wrap = document.createElement('div');
    wrap.className = 'scroll';
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    columns.forEach(function (c) {
      var th = document.createElement('th');
      th.textContent = c;
      hr.appendChild(th);
    });
    thead.appendChild(hr);
    table.appendChild(thead);
    var tbody = document.createElement('tbody');
    section.rows.forEach(function (row) {
      var tr = document.createElement('tr');
      columns.forEach(function (c) {
        var td = document.createElement('td');
        td.textContent = row[c] == null ? '' : String(row[c]);
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    wrap.appendChild(table);
    el.appendChild(wrap);
    board.appendChild(el);
  });
})();
</script>
</body></html>`;
}

/** Pinned, hashed, and named in the CSP — the three things that make a CDN script safe to run. */
const CHART_HOST = 'https://cdn.jsdelivr.net';
const CHART_SRC = `${CHART_HOST}/npm/chart.js@4.4.6/dist/chart.umd.min.js`;
const CHART_SRI = 'sha384-Sse/HDqcypGpyTDpvZOJNnG0TT3feGQUkF9H+mnRvic+LjR+K1NhTt8f51KIQ3v3';

/**
 * The route, in two halves: the page, and the numbers it draws.
 *
 * `?format=json` answers the rows and anything else answers the shell that fetches them. Two
 * halves rather than one server-rendered page because the charting happens in the browser now,
 * and one URL rather than two because whatever is guarding this — Cloudflare Access on the
 * stats host — should guard both without anybody having to remember the second one.
 *
 * The key check stays for a deployment that is *not* behind Access, and does nothing when
 * `DASHBOARD_KEY` is unset. On the stats host, Access is the door and this is belt to its
 * braces; without either, the route answers 404 and is indistinguishable from absent.
 *
 * Returns null when this is not that route, so the caller's router reads as a list of routes
 * rather than a nest of conditions.
 */
export async function serveDashboard(request, env, url, path = '/counts') {
  if (url.pathname !== path) return null;

  // An unconfigured deployment does not have a dashboard, and says exactly that — the same
  // answer as a path that does not exist, so a prober cannot tell a service that is missing
  // its secret from one that never had this route.
  if (!dashboardConfigured(env)) return new Response('not found', { status: 404 });
  if (env.DASHBOARD_KEY && !keyMatches(url.searchParams.get('key'), env.DASHBOARD_KEY)) {
    return new Response('not found', { status: 404 });
  }

  // Never cached and never indexed: it is a private view, and a stale one is worse than none
  // because the number it shows is the one somebody will act on.
  const guard = {
    'cache-control': 'no-store',
    'x-robots-tag': 'noindex, nofollow',
  };

  if (url.searchParams.get('format') === 'json') {
    return new Response(JSON.stringify(await dashboardData(env)), {
      headers: { 'content-type': 'application/json; charset=utf-8', ...guard },
    });
  }

  return new Response(renderShell(), {
    headers: { 'content-type': 'text/html; charset=utf-8', ...guard },
  });
}
