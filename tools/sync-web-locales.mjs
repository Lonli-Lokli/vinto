#!/usr/bin/env node
/**
 * Keep the web shell's two locale lists in step with the locale directories.
 *
 *     node tools/sync-web-locales.mjs
 *
 * Run it after adding a `values-<loc>/` folder, then commit `_headers` and `index.html` with it.
 * `WebShellTest.everyStringTableIsRefetchedAndRepaired` fails if you forget, which is the safety
 * net; this is the thing that means you rarely need it.
 *
 * ## What the two lists are for, and why forgetting one is so bad
 *
 * Compose does not look a string up by key at runtime. The generated accessors carry a **byte
 * offset and length** into each locale's `strings.commonMain.cvr`, and those numbers are baked into
 * wasm. The wasm is content-hashed, so a browser always has this build's; the `.cvr` files are
 * not, so a browser can hold a previous build's. Pair a new offset table with an old file and
 * every string after the first changed entry is read from the wrong place — truncated mid-word,
 * decoded as garbage, or empty. Strings *before* it are perfect, which is why the home screen
 * looked fine and everything behind "Play online" did not.
 *
 * Two lines defend against that, and both are per locale:
 *
 *   * `_headers` — `no-store` on each table, so the browser stops caching it at all.
 *   * `index.html` — a one-time refetch, so a browser *already holding* a stale copy is repaired
 *     rather than left broken until it happens to evict it.
 *
 * A new locale missing from either is a silent return of that corruption, in that language only —
 * which is to say, in a language nobody testing the release reads.
 *
 * ## Why this is generated rather than typed
 *
 * WORDS.md §6h's whole objective was that adding a language becomes "a file and no code". It very
 * nearly was: one `strings.xml`, plus two lines in two files that nothing reminds you about until
 * a test fails. Nineteen locales landed at once and that arithmetic stopped being cute.
 */

import { readdirSync, readFileSync, writeFileSync } from 'node:fs';

const RES = 'composeApp/src/commonMain/composeResources';
const WEB = 'composeApp/src/wasmJsMain/resources';

const pkg = readFileSync('composeApp/build.gradle.kts', 'utf8')
  .match(/packageOfResClass\s*=\s*"([^"]+)"/)?.[1];
if (!pkg) throw new Error('packageOfResClass is not set in composeApp/build.gradle.kts');

// "values" first, then the locales in order — the same order the test reads them in, and the
// order a person scanning the file expects.
const locales = readdirSync(RES).filter((d) => d === 'values' || d.startsWith('values-')).sort(
  (a, b) => (a === 'values' ? -1 : b === 'values' ? 1 : a.localeCompare(b)),
);

// --- _headers ----------------------------------------------------------------------------
// Everything from the first table rule to the end of that block is replaced wholesale, so a
// locale that has been REMOVED loses its rule too.
const headersFile = `${WEB}/_headers`;
let headers = readFileSync(headersFile, 'utf8');
const rules = locales
  .map((l) => `/composeResources/${pkg}/${l}/strings.commonMain.cvr\n  Cache-Control: no-store`)
  .join('\n\n');

const first = headers.indexOf(`/composeResources/${pkg}/values/strings.commonMain.cvr`);
if (first === -1) throw new Error('_headers has no string-table block to replace');
const after = headers.indexOf('\n# ---', first);
const tail = after === -1 ? '\n' : headers.slice(after);
headers = headers.slice(0, first) + rules + tail;
writeFileSync(headersFile, headers);

// --- index.html --------------------------------------------------------------------------
const shellFile = `${WEB}/index.html`;
let shell = readFileSync(shellFile, 'utf8');
const list = locales.map((l) => `"${l}"`).join(', ');
if (!/var LOCALES = \[[^\]]*\]/.test(shell)) {
  throw new Error('index.html no longer carries the composeResources repair');
}
shell = shell.replace(/var LOCALES = \[[^\]]*\]/, `var LOCALES = [${list}]`);
writeFileSync(shellFile, shell);

console.log(`${locales.length} locale(s) synced into _headers and index.html:`);
console.log('  ' + locales.join(' '));
