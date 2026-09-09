/**
 * The one place a data point reaches the store.
 *
 * It lived in `index.mjs` and `gate-analytics.mjs` had a copy of it, introduced with the comment
 * "exactly the shim's helper" — which is how the two came apart without anybody noticing. The
 * copy was handed a stub `writeDataPoint` that accepted whatever it was given, so the gate could
 * only ever prove that a write *happened*, never that what was written was a data point. It was
 * not: `kupalinka_events` held zero rows for the life of the deployment. One module, imported by
 * both, is the only version of this that can be held by a test.
 */
import { reportError } from './sentry.mjs';

/**
 * Which faults have already been reported by this isolate.
 *
 * Bounded by construction — there are two members it can ever hold — and that bound is the point.
 * A sink refusing one point is refusing all of them, so reporting per call would send one Sentry
 * event per game action for as long as the fault lasted: a flood that costs money, buries every
 * other report, and says nothing the first one did not.
 *
 * Two entries rather than one because the two faults are answered by different people. "The sink
 * refuses us" is a quota or a binding; "we are building junk" is a bug in whatever made the point.
 */
const complained = new Set();

/**
 * Writes one data point, if there is anywhere to write it.
 *
 * **The parse is the whole of it.** Every builder in `AnalyticsExports.kt` returns a JSON
 * *string* — that is what crosses the Kotlin/JS boundary cleanly — and `writeDataPoint` takes an
 * **object** with `indexes`, `blobs` and `doubles`. Handing it the string wrote nothing at all,
 * silently, for the life of the deployment.
 *
 * **Absent-safe by design, not by accident.** With no `ANALYTICS` binding this is a no-op, so
 * `wrangler dev` and every gate script run identically without a Cloudflare account — which is
 * what keeps analytics from becoming a thing you need credentials to develop against.
 *
 * `writeDataPoint` does not count against the invocation's CPU time and does not return a promise
 * worth awaiting. That matters more here than anywhere: the thing being measured is a Durable
 * Object whose 30-second budget is already going on MCTS, and analytics that slowed the room down
 * would be measuring a room nobody wants.
 *
 * [ctx] is the request's or the object's, and only so a report can outlive the response that
 * triggered it. Everything works without it; the report is simply less certain to arrive.
 */
export function emit(env, point, ctx) {
  if (!env?.ANALYTICS || !point) return;

  let written = point;
  if (typeof point === 'string') {
    try {
      written = JSON.parse(point);
    } catch (error) {
      complain(env, ctx, error, 'built a point that is not JSON', point);
      return;
    }
  }

  try {
    env.ANALYTICS.writeDataPoint(written);
  } catch (error) {
    // Still swallowed, and that has not changed: a sink that refuses a point must never fail the
    // request that produced it, there is nothing to retry, and the count is gone either way.
    // What has changed is that it is no longer swallowed *in private*.
    complain(env, ctx, error, 'sink refused a point', written);
  }
}

/**
 * Says so, once per fault, where somebody is looking.
 *
 * The count is still lost — this reports, it does not recover. What it buys is the difference
 * between a dashboard that is empty because nobody played and a dashboard that is empty because
 * every write was being refused, which from the outside look exactly alike and did for weeks.
 *
 * The fault is remembered only if it was actually reported, so a deployment with no DSN is not
 * quietly marked as having complained: the day somebody adds one, it complains.
 *
 * Wrapped in its own catch for the reason `#report` in the shim is: a reporter that throws on an
 * error path turns one lost count into a failed request, which is the trade this whole file
 * refuses to make.
 */
function complain(env, ctx, cause, what, point) {
  if (complained.has(what)) return;
  try {
    // The event's own name, which is an enum on the Kotlin side and so carries nothing about
    // anybody — `AnalyticsPrivacyTest` is what makes that true rather than hopeful.
    const named = typeof point === 'object' && Array.isArray(point?.blobs) ? point.blobs[1] : 'unknown';
    const sent = reportError(
      env,
      new Error(`analytics ${what} (${named}): ${cause?.message ?? cause}`),
      { surface: 'analytics' },
    );
    if (!sent) return;
    complained.add(what);
    ctx?.waitUntil?.(sent);
  } catch {
    // Nothing left to do but not make it worse.
  }
}
