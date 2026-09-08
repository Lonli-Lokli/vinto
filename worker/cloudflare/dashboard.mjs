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
 * The page: HTML, CSS and JavaScript, drawing the JSON above with Chart.js.
 *
 * **Self-explanatory, which mostly meant deleting.** Every panel carried a paragraph saying what
 * it was for, and a paragraph is a thing to read: five of them stacked down a page is an essay
 * with charts in it. A number under a heading needs no gloss, so the prose moved to the panel's
 * `title` — there for a hover, gone from the layout — and the headline row above says in five
 * numbers what the essay was saying in five paragraphs.
 *
 * **The period is part of the page**, not something to go and configure. Three buttons, the
 * choice kept in the URL so a link to this dashboard is a link to *this* view of it, and the
 * whole payload refetched — the window is in the SQL, so a period is a query rather than a
 * filter over rows already fetched.
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
  :root { color-scheme: dark; --ink: #e8e6e3; --dim: #8b939c; --line: #232a31;
          --panel: #181d23; --bg: #11151a; --accent: #3fd07a; }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--ink);
         font: 14px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
  .wrap { max-width: 1440px; margin: 0 auto; padding: 16px 20px 40px; }
  .top { display: flex; align-items: baseline; gap: 16px; flex-wrap: wrap; margin-bottom: 14px; }
  h1 { font-size: 17px; margin: 0; font-weight: 650; letter-spacing: .2px; }
  .when { color: var(--dim); font-size: 12px; margin-right: auto; }
  .periods { display: flex; gap: 4px; }
  .periods button { background: var(--panel); color: var(--dim); border: 1px solid var(--line);
                    border-radius: 6px; padding: 5px 12px; font: inherit; font-size: 13px;
                    cursor: pointer; }
  .periods button[aria-current="true"] { color: #0d1114; background: var(--accent);
                                         border-color: var(--accent); font-weight: 650; }
  .tiles { display: grid; gap: 10px; margin-bottom: 14px;
           grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); }
  .tile { background: var(--panel); border: 1px solid var(--line); border-radius: 8px;
          padding: 12px 14px; }
  .tile .k { color: var(--dim); font-size: 11.5px; text-transform: uppercase;
             letter-spacing: .6px; }
  .tile .v { font-size: 27px; font-weight: 650; line-height: 1.15; margin-top: 2px; }
  .tile .h { color: var(--dim); font-size: 11.5px; }
  main { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); }
  section { background: var(--panel); border: 1px solid var(--line); border-radius: 8px;
            padding: 12px 14px 10px; min-width: 0; }
  h2 { font-size: 13px; margin: 0 0 10px; font-weight: 600; color: #c9d1d9;
       display: flex; align-items: center; gap: 6px; }
  h2 .i { color: var(--dim); font-size: 11px; border: 1px solid var(--line);
          border-radius: 50%; width: 15px; height: 15px; display: inline-flex;
          align-items: center; justify-content: center; cursor: help; }
  .frame { position: relative; height: 210px; }
  details { margin-top: 8px; }
  summary { color: var(--dim); font-size: 11.5px; cursor: pointer; list-style: none; }
  summary::-webkit-details-marker { display: none; }
  summary::before { content: "▸ "; }
  details[open] summary::before { content: "▾ "; }
  table { border-collapse: collapse; width: 100%; margin-top: 8px; font-size: 12.5px; }
  th, td { text-align: right; padding: 4px 8px; border-bottom: 1px solid var(--line);
           white-space: nowrap; }
  th:first-child, td:first-child { text-align: left; }
  th { color: var(--dim); font-weight: 600; }
  .scroll { overflow-x: auto; max-height: 240px; overflow-y: auto; }
  .empty, .error { font-size: 12.5px; margin: 0; padding: 24px 0; text-align: center; }
  .empty { color: #6f7780; }
  .error { color: #e0796b; }
</style></head>
<body><div class="wrap">
<div class="top">
  <h1>Vinto</h1>
  <span class="when" id="when"></span>
  <div class="periods" id="periods"></div>
</div>
<div class="tiles" id="tiles"></div>
<main id="board"></main>
</div>
<script src="${CHART_SRC}" integrity="${CHART_SRI}" crossorigin="anonymous"></script>
<script>
(function () {
  var PERIODS = ${JSON.stringify(PERIODS)};
  var INK = '#e8e6e3', DIM = '#8b939c', LINE = '#232a31';
  var BAR = '#2f6f8f', PART = '#3fd07a';
  var board = document.getElementById('board');
  var tiles = document.getElementById('tiles');
  var picker = document.getElementById('periods');
  var drawn = [];

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function chosen() {
    var days = Number(new URLSearchParams(location.search).get('days'));
    return PERIODS.indexOf(days) >= 0 ? days : PERIODS[PERIODS.length - 1];
  }

  function picked(days) {
    picker.innerHTML = '';
    PERIODS.forEach(function (d) {
      var b = el('button', null, d + 'd');
      if (d === days) b.setAttribute('aria-current', 'true');
      b.onclick = function () {
        history.replaceState(null, '', '?days=' + d);
        load(d);
      };
      picker.appendChild(b);
    });
  }

  function panel(section) {
    var s = el('section');
    var h = el('h2');
    h.appendChild(document.createTextNode(section.title));
    if (section.note) {
      var i = el('span', 'i', 'i');
      i.title = section.note;
      h.appendChild(i);
    }
    s.appendChild(h);

    if (section.error) { s.appendChild(el('p', 'error', section.error)); return s; }
    if (!section.rows.length) { s.appendChild(el('p', 'empty', 'Nothing in this period.')); return s; }

    if (section.chart) {
      var spec = section.chart;
      var rows = spec.overTime ? section.rows.slice().reverse() : section.rows;
      var frame = el('div', 'frame');
      var canvas = document.createElement('canvas');
      frame.appendChild(canvas);
      s.appendChild(frame);
      var sets = [{ label: spec.y, data: rows.map(function (r) { return r[spec.y]; }),
                    backgroundColor: BAR, borderRadius: 2, borderWidth: 0 }];
      if (spec.of) {
        sets.push({ label: spec.of, data: rows.map(function (r) { return r[spec.of]; }),
                    backgroundColor: PART, borderRadius: 2, borderWidth: 0 });
      }
      drawn.push(new Chart(canvas, {
        type: 'bar',
        data: { labels: rows.map(function (r) {
          var v = String(r[spec.x]);
          return /^\d{4}-\d{2}-\d{2}$/.test(v) ? v.slice(5) : v;
        }), datasets: sets },
        options: {
          responsive: true, maintainAspectRatio: false,
          animation: false,
          scales: {
            x: { stacked: false, grid: { display: false },
                 ticks: { color: DIM, maxRotation: 0, autoSkip: true, font: { size: 11 } } },
            y: { beginAtZero: true, grid: { color: LINE, drawTicks: false },
                 border: { display: false },
                 ticks: { color: DIM, precision: 0, font: { size: 11 } } }
          },
          plugins: {
            legend: { display: !!spec.of, position: 'bottom',
                      labels: { color: DIM, boxWidth: 10, boxHeight: 10, font: { size: 11 } } },
            tooltip: { mode: 'index', intersect: false }
          }
        }
      }));
    }

    var columns = Object.keys(section.rows[0]);
    var d = el('details');
    d.appendChild(el('summary', null, 'numbers'));
    var wrap = el('div', 'scroll');
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    columns.forEach(function (c) { hr.appendChild(el('th', null, c)); });
    thead.appendChild(hr);
    table.appendChild(thead);
    var tbody = document.createElement('tbody');
    section.rows.forEach(function (row) {
      var tr = document.createElement('tr');
      columns.forEach(function (c) {
        tr.appendChild(el('td', null, row[c] == null ? '' : String(row[c])));
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    wrap.appendChild(table);
    d.appendChild(wrap);
    s.appendChild(d);
    return s;
  }

  async function load(days) {
    picked(days);
    document.getElementById('when').textContent = 'last ' + days + ' days';
    drawn.forEach(function (c) { c.destroy(); });
    drawn = [];
    tiles.innerHTML = '';
    board.innerHTML = '';
    var res = await fetch('?format=json&days=' + days, { headers: { accept: 'application/json' } });
    if (!res.ok) { board.appendChild(el('p', 'error', 'Could not read the counts.')); return; }
    var data = await res.json();
    data.tiles.forEach(function (t) {
      var card = el('div', 'tile');
      card.appendChild(el('div', 'k', t.label));
      card.appendChild(el('div', 'v', t.value));
      card.appendChild(el('div', 'h', t.hint || ''));
      tiles.appendChild(card);
    });
    data.panels.forEach(function (section) { board.appendChild(panel(section)); });
  }

  load(chosen());
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
    // The period is read here and validated by [periodFrom], so a number somebody typed into the
    // address bar cannot reach the SQL — the window is interpolated into a statement, and the one
    // rule for that is that it is never a value a caller chose.
    const days = periodFrom(url.searchParams.get('days'));
    return new Response(JSON.stringify(await dashboardData(env, days)), {
      headers: { 'content-type': 'application/json; charset=utf-8', ...guard },
    });
  }

  return new Response(renderShell(), {
    headers: { 'content-type': 'text/html; charset=utf-8', ...guard },
  });
}
