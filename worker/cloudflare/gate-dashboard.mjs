/**
 * This game's contribution to the dashboard: its queries, its refusals, and its shape.
 *
 *   node worker/cloudflare/gate-dashboard.mjs
 *
 * What this gate cannot do is see a number. The Analytics Engine SQL API is the one part of
 * WAE that `wrangler dev` does not emulate — local `writeDataPoint` calls go nowhere
 * queryable — so a deployment with real traffic is the only place the six queries can be run
 * against actual rows. Task 5.1 is therefore built and **not ticked**; §1f says so.
 *
 * What it can do is the three ways one of these goes quietly wrong without anybody noticing:
 * a query that forgets to weight its counts and under-reports, one that forgets its window and
 * reads the whole retention, and a route that hands the numbers to somebody without the key.
 *
 * The page itself is no longer here — `stats.kupalinka.app` draws every game, and this Worker
 * answers `/stats.json`. So what used to be asserted about the HTML (its CSP, its pinned chart
 * library, that rows reach the DOM as text) is asserted in that repository, once, rather than
 * once per game. What IS asserted here is the contract: the shape the dashboard renders.
 */
import {
  QUERIES,
  DATASET,
  WINDOW_DAYS,
  dashboardConfigured,
  keyMatches,
  PERIODS,
  periodFrom,
  queriesFor,
  serveStats,
  GAME,
} from './dashboard.mjs';

let failures = 0;
const check = (label, ok, detail = '') => {
  if (ok) console.log(`  pass  ${label}`);
  else { failures++; console.log(`  FAIL  ${label}${detail ? ` — ${detail}` : ''}`); }
};

console.log('\ndashboard: the six queries');

check('there are six of them (task 5.2)', QUERIES.length === 6, String(QUERIES.length));
check('every id is distinct', new Set(QUERIES.map((q) => q.id)).size === QUERIES.length);

for (const query of QUERIES) {
  const sql = query.sql;
  check(`${query.id} reads the declared dataset`, sql.includes(`FROM ${DATASET}`));
  check(
    `${query.id} bounds its window`,
    sql.includes(`INTERVAL '${WINDOW_DAYS}' DAY`),
    'an unbounded query reads the whole retention every load',
  );
  check(
    `${query.id} selects one event kind`,
    /index1 = '[a-z_]+'/.test(sql),
    'a query over every index1 mixes events whose doubles mean different things',
  );
  check(`${query.id} has a note saying what it is for`, (query.note ?? '').length > 40);

  // A chart names columns; the query produces them. Nothing at runtime would say otherwise —
  // a mistyped axis reads every row as zero and draws a flat chart, which looks exactly like a
  // quiet week. So the two are checked against each other here, where it costs nothing.
  if (query.chart) {
    const aliases = [...query.sql.matchAll(/AS ([a-z_]+)/g)].map((m) => m[1]);
    for (const axis of ['x', 'y', 'of']) {
      const column = query.chart[axis];
      if (column == null) continue;
      check(
        `${query.id}: the chart's ${axis} (${column}) is a column the query selects`,
        aliases.includes(column),
        `selects ${aliases.join(', ')}`,
      );
    }
  }
}

// Every aggregate that counts events has to carry both samplings: Analytics Engine's own
// `_sample_interval`, and the rate this app declares in double1 (§A8). Dropping either
// under-reports, and it under-reports *silently* — the number still looks like a number.
for (const query of QUERIES) {
  const sums = query.sql.match(/sum\([^)]*\)/g) ?? [];
  for (const sum of sums) {
    check(
      `${query.id}: ${sum} weights by both samplings`,
      sum.includes('_sample_interval') && sum.includes('double1'),
      'sum() without _sample_interval * double1 under-reports',
    );
  }
}

console.log('\ndashboard: who may read it');

check('an unconfigured deployment has no dashboard', dashboardConfigured({}) === false);
check('a token with no account is not enough',
  dashboardConfigured({ ANALYTICS_TOKEN: 'a' }) === false);
// Two, not three: on a host behind Access the key is a second lock on a locked door, and
// requiring it would make a correctly-protected deployment answer 404 for want of a password.
check('the two reading secrets configure it',
  dashboardConfigured({ ANALYTICS_TOKEN: 'a', ANALYTICS_ACCOUNT_ID: 'b' }) === true);
check('and a key on top is still allowed',
  dashboardConfigured({ ANALYTICS_TOKEN: 'a', ANALYTICS_ACCOUNT_ID: 'b', DASHBOARD_KEY: 'c' }) === true);

// The period is interpolated into a SQL statement, so it may only ever be one of the values
// this module chose. Anything else — a bigger number, a string, an injection — is the default.
console.log('\ndashboard: the period');
for (const junk of ["1' OR '1'='1", '999', '-7', '', null, undefined, '7.5', {}]) {
  check(`a period of ${JSON.stringify(junk)} falls back to the default`,
    PERIODS.includes(periodFrom(junk)) && periodFrom(junk) === WINDOW_DAYS,
    String(periodFrom(junk)));
}
for (const good of PERIODS) {
  check(`${good} days is offered and honoured`, periodFrom(String(good)) === good);
  check(`and ${good} days bounds every query`,
    queriesFor(good).every((q) => q.sql.includes(`INTERVAL '${good}' DAY`)));
}

check('the right key matches', keyMatches('s3cret', 's3cret'));
check('a wrong key of the same length does not', keyMatches('s3cret', 's3crXt') === false);
check('a prefix does not', keyMatches('s3cre', 's3cret') === false);
check('a missing key does not', keyMatches(null, 's3cret') === false);
check('nothing matches an unset secret', keyMatches('anything', undefined) === false);

const configured = {
  ANALYTICS_TOKEN: 'token', ANALYTICS_ACCOUNT_ID: 'account', STATS_KEY: 'letmein',
};
const at = (path) => new URL(`https://vinto-room.example${path}`);
const asking = (key) => new Request('https://x/stats.json', key ? { headers: { 'x-stats-key': key } } : {});

check(
  'a path that is not the stats route is not answered here',
  (await serveStats(new Request('https://x/health'), configured, at('/health'))) === null,
);

const noKey = await serveStats(asking(null), configured, at('/stats.json'));
check('no key is a 404, not a 401', noKey.status === 404, String(noKey?.status));

const wrongKey = await serveStats(asking('nope'), configured, at('/stats.json'));
check('a wrong key is the same 404', wrongKey.status === 404, String(wrongKey?.status));

const unconfigured = await serveStats(asking('letmein'), {}, at('/stats.json'));
check(
  'an unconfigured deployment is indistinguishable from one without the route',
  unconfigured.status === 404,
  String(unconfigured?.status),
);

// Unset means CLOSED. The opposite default would publish the business's own numbers to anyone
// who guessed the path, and would do it on exactly the deployments nobody had configured yet.
const noSecret = await serveStats(asking('letmein'), {
  ANALYTICS_TOKEN: 'token', ANALYTICS_ACCOUNT_ID: 'account',
}, at('/stats.json'));
check('a deployment with no STATS_KEY is closed, not open', noSecret.status === 404, String(noSecret?.status));

console.log('\nstats.json: the contract the dashboard renders');

check('the payload names its own game, so the dashboard needs no list', GAME === 'vinto');

// The tiles and panels are built from the queries above, so their shape can be checked without
// the SQL API: `dashboardData` is the only thing that reaches out, and these do not.
const shaped = queriesFor(7);
check('every panel declares an id the dashboard can key on', shaped.every((q) => typeof q.id === 'string' && q.id));
check('and a title a person can read', shaped.every((q) => typeof q.title === 'string' && q.title));
// A chart names the COLUMNS it plots, and a name that no column answers to draws an empty
// chart rather than an error — the dashboard has no way to tell the difference, so it is
// checked here, against the statement that produces the rows.
for (const q of shaped) {
  if (!q.chart) continue;
  const named = [q.chart.x, q.chart.y, q.chart.of].filter(Boolean);
  check(
    `${q.id}: every column its chart plots is one the query selects`,
    named.every((column) => new RegExp(`\\bAS ${column}\\b`, 'i').test(q.sql)),
    named.filter((column) => !new RegExp(`\\bAS ${column}\\b`, 'i').test(q.sql)).join(', '),
  );
}

console.log(failures === 0 ? '\ndashboard gate: ok\n' : `\ndashboard gate: ${failures} FAILED\n`);
process.exit(failures === 0 ? 0 : 1);
