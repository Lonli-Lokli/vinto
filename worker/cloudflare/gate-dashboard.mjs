/**
 * The dashboard: its queries, its refusals, and its escaping.
 *
 *   node worker/cloudflare/gate-dashboard.mjs
 *
 * What this gate cannot do is see a number. The Analytics Engine SQL API is the one part of
 * WAE that `wrangler dev` does not emulate — local `writeDataPoint` calls go nowhere
 * queryable — so a deployment with real traffic is the only place the six queries can be run
 * against actual rows. Task 5.1 is therefore built and **not ticked**; §1f says so.
 *
 * What it can do is the three ways one of these goes quietly wrong without anybody noticing:
 * a query that forgets to weight its counts and under-reports, one that forgets its window
 * and reads the whole retention, and a route that hands the page to somebody without the key.
 */
import {
  QUERIES,
  DATASET,
  WINDOW_DAYS,
  dashboardConfigured,
  keyMatches,
  escapeHtml,
  renderPage,
  serveDashboard,
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
check('two of three secrets is still no dashboard',
  dashboardConfigured({ ANALYTICS_TOKEN: 'a', ANALYTICS_ACCOUNT_ID: 'b' }) === false);
check('all three configures it',
  dashboardConfigured({ ANALYTICS_TOKEN: 'a', ANALYTICS_ACCOUNT_ID: 'b', DASHBOARD_KEY: 'c' }) === true);

check('the right key matches', keyMatches('s3cret', 's3cret'));
check('a wrong key of the same length does not', keyMatches('s3cret', 's3crXt') === false);
check('a prefix does not', keyMatches('s3cre', 's3cret') === false);
check('a missing key does not', keyMatches(null, 's3cret') === false);
check('nothing matches an unset secret', keyMatches('anything', undefined) === false);

const configured = {
  ANALYTICS_TOKEN: 'token', ANALYTICS_ACCOUNT_ID: 'account', DASHBOARD_KEY: 'letmein',
};
const at = (path) => new URL(`https://vinto-room.example${path}`);

check(
  'a path that is not the dashboard is not answered here',
  (await serveDashboard(new Request('https://x/health'), configured, at('/health'))) === null,
);

const noKey = await serveDashboard(new Request('https://x/counts'), configured, at('/counts'));
check('no key is a 404, not a 401', noKey.status === 404, String(noKey?.status));

const wrongKey = await serveDashboard(new Request('https://x/counts'), configured, at('/counts?key=nope'));
check('a wrong key is the same 404', wrongKey.status === 404, String(wrongKey?.status));

const unconfigured = await serveDashboard(new Request('https://x/counts'), {}, at('/counts?key=letmein'));
check(
  'an unconfigured deployment is indistinguishable from one without the route',
  unconfigured.status === 404,
  String(unconfigured?.status),
);

console.log('\ndashboard: the page');

const page = renderPage([
  { title: 'A section', note: 'What it is for.', rows: [{ day: '2026-08-29', opens: 12.5 }] },
  { title: 'An empty one', note: 'Nothing yet.', rows: [] },
  { title: 'A broken one', note: 'It failed.', error: 'the SQL API answered 403' },
]);

check('rows render', page.includes('<td>2026-08-29</td>') && page.includes('<td>12.50</td>'));
check('an empty section says so', page.includes('Nothing yet.'));
check('an error is shown rather than swallowed', page.includes('the SQL API answered 403'));
check('the page asks not to be indexed', page.includes('noindex'));

// The values are enum labels and numbers today, so nothing here can carry markup. The
// escaping is still asserted, because "the data is safe" is a property of a schema somebody
// can change and not of this renderer.
check('markup in a value is escaped', escapeHtml('<script>x</script>') === '&lt;script&gt;x&lt;/script&gt;');
check('quotes are escaped', escapeHtml('a "b" & c') === 'a &quot;b&quot; &amp; c');

console.log(failures === 0 ? '\ndashboard gate: ok\n' : `\ndashboard gate: ${failures} FAILED\n`);
process.exit(failures === 0 ? 0 : 1);
