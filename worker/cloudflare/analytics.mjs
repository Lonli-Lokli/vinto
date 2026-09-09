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

/**
 * Writes one data point, if there is anywhere to write it.
 *
 * **The parse is the whole of it.** Every builder in `AnalyticsExports.kt` returns a JSON
 * *string* — that is what crosses the Kotlin/JS boundary cleanly — and `writeDataPoint` takes an
 * **object** with `indexes`, `blobs` and `doubles`. Handing it the string wrote nothing at all,
 * silently, because the catch below is deliberately deaf.
 *
 * **Absent-safe by design, not by accident.** With no `ANALYTICS` binding this is a no-op, so
 * `wrangler dev` and every gate script run identically without a Cloudflare account — which is
 * what keeps analytics from becoming a thing you need credentials to develop against.
 *
 * `writeDataPoint` does not count against the invocation's CPU time and does not return a promise
 * worth awaiting. That matters more here than anywhere: the thing being measured is a Durable
 * Object whose 30-second budget is already going on MCTS, and analytics that slowed the room down
 * would be measuring a room nobody wants.
 */
export function emit(env, point) {
  if (!env.ANALYTICS || !point) return;
  try {
    // A point arrives as the JSON its builder returned; the sink takes the object it describes.
    env.ANALYTICS.writeDataPoint(typeof point === 'string' ? JSON.parse(point) : point);
  } catch {
    // A sink that refuses a point must never fail the request that produced it. There is nothing
    // to retry and nothing to report: the count is simply lost.
  }
}
