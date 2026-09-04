#!/usr/bin/env node
/**
 * Take the source maps back out of what is about to be published.
 *
 *     node tools/strip-web-sourcemaps.mjs [dist-dir]
 *
 * A source map is the bundle's original source. Serving one publishes every Kotlin file the web
 * client was built from, to anyone who opens the network tab — and this repository may not stay
 * public. Sentry needs the map; the internet does not, and Sentry's own guidance is to upload it
 * and then not serve it.
 *
 * So this runs **after** `upload-web-sourcemaps.mjs` and **before** the deploy: the map goes to
 * Sentry, and then it goes away.
 *
 * Two things are removed, and both matter:
 *
 *   * **The `.map` files.** The obvious half.
 *   * **The `//# sourceMappingURL=…` comment in the bundle.** Leaving it behind points every
 *     visitor's DevTools at a file that 404s, and — worse for the reason this exists — it names
 *     the map, which tells a reader exactly what to go looking for in any older deploy still
 *     cached at the edge.
 *
 * It runs **unconditionally**, never gated on a token or a secret. The upload is allowed to be
 * skipped when there are no credentials; this is not, because the failure mode of skipping it is
 * publishing the source. Losing symbolication is a bad day, and publishing what you meant to keep
 * is not undoable.
 *
 * Then it checks its own work and exits non-zero if anything is left, so a deploy fails rather
 * than quietly shipping a map.
 */

import { readdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const DIST = process.argv[2] ?? 'composeApp/build/dist/wasmJs/productionExecutable';

/** Every file under the directory, so a map in a subdirectory cannot slip past. */
function walk(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)],
  );
}

const files = walk(DIST);
let removed = 0;
let stripped = 0;

for (const f of files) {
  if (f.endsWith('.map')) {
    rmSync(f);
    removed++;
    console.log(`removed ${f}`);
  }
}

// The comment is the last line of a webpack bundle, but it is matched anywhere rather than
// assumed to be last — a build tool is free to move it and this must not quietly stop working.
for (const f of files.filter((f) => f.endsWith('.js') || f.endsWith('.mjs'))) {
  const before = readFileSync(f, 'utf8');
  const after = before.replace(/^\s*\/\/[#@]\s*sourceMappingURL=.*$/gm, '');
  if (after !== before) {
    writeFileSync(f, after);
    stripped++;
    console.log(`stripped the sourceMappingURL comment from ${f}`);
  }
}

// Checked rather than assumed: this is the step whose whole job is that nothing is left.
const leftover = walk(DIST).filter((f) => f.endsWith('.map'));
const stillReferenced = walk(DIST)
  .filter((f) => f.endsWith('.js') || f.endsWith('.mjs'))
  .filter((f) => /\/\/[#@]\s*sourceMappingURL=/.test(readFileSync(f, 'utf8')));

if (leftover.length || stillReferenced.length) {
  console.error('source maps are still in the tree that is about to be published:');
  leftover.forEach((f) => console.error(`  map file: ${f}`));
  stillReferenced.forEach((f) => console.error(`  still referenced by: ${f}`));
  process.exit(1);
}

console.log(`${DIST} is clean: ${removed} map(s) removed, ${stripped} reference(s) stripped`);
