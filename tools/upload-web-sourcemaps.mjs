#!/usr/bin/env node
/**
 * Send the web bundle's symbols to Sentry: the JavaScript source map, and the WebAssembly debug file.
 *
 *     node tools/upload-web-sourcemaps.mjs [dist-dir]
 *
 * Run after `./gradlew :composeApp:wasmJsBrowserDistribution` and `tools/wasm-symbols.mjs`. It
 * publishes nothing itself, and it is only half the job: **`strip-web-sourcemaps.mjs` has to run
 * after it**, because a map that reaches Sentry must not also reach the public asset store.
 *
 * ## The two kinds of frame
 *
 * A web crash reaches Sentry with two kinds of frame, and each needs its own upload.
 *
 * The **JavaScript glue** webpack emits — `composeApp.js`, one line, half a megabyte — is read
 * through its source map, matched by release and file name.
 *
 * The **wasm** frames hold our code. They carry Kotlin function names by themselves (the module
 * keeps its name section), and a file and line only through a debug file: Sentry symbolicates
 * WebAssembly from DWARF, filed under the module's `build_id`, and does not read wasm source maps.
 * `wasm-symbols.mjs` stamps that id into the module and writes the DWARF from the map `wasm-opt`
 * keeps for it; this uploads the result, with the Kotlin sources it names, so a frame shows the
 * line itself. `--wait` holds the upload until Sentry has processed it, so a file it cannot use
 * fails the deploy here rather than in the first crash report.
 *
 * ## Credentials
 *
 * `sentry-cli` reads its token from `SENTRY_AUTH_TOKEN` or from `~/.sentryclirc`, whichever it
 * finds. So a developer keeps using the rc file, CI sets the environment variable, and neither
 * needs a branch here — the same rule the iOS dSYM upload follows.
 *
 * **Absent credentials are a failure.** A deploy nobody can read a stack trace from is worse than
 * a deploy that did not happen: the build goes out, people play it, it crashes, and every report
 * is unreadable — with a green pipeline behind it saying nothing went wrong. **There is no
 * waiver**: a client build whose symbols cannot reach Sentry fails, whatever the reason, and the
 * Android and iOS halves follow the same rule.
 *
 * ## Upload it under the name that will actually be served
 *
 * Sentry matches an artifact to a frame by the frame's file name, so this uploads whatever the
 * bundle is called on disk rather than assuming `composeApp.js`. That is not a nicety: the
 * deploy workflow renames the script to `composeApp.<hash>.js` before publishing it, so a
 * production frame says `https://vinto.kupalinka.app/composeApp.<hash>.js` and an upload made
 * under the plain name matches nothing at all — symbols that key on something no event carries,
 * which is the same way an unused dSYM upload fails. **Run this after the content-addressing
 * step, not before it.**
 *
 * A happy consequence: because the deployed name carries a content hash, two builds of the same
 * release cannot collide, though they share a release string (`vinto@` and `WEB_VERSION` in
 * `Version.kt`, which moves only at a release). Uploading from a local dist, where the file is still plain
 * `composeApp.js`, does overwrite the previous local upload — which is what you want there.
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';
import { spawnSync } from 'node:child_process';

const DIST = process.argv[2] ?? 'composeApp/build/dist/wasmJs/productionExecutable';
const ORG = 'echo-xl';
const PROJECT = 'vinto';

/**
 * Must equal what `Crashes` sends as the release from a browser: `vinto@${appVersion()}`, and on
 * the web `appVersion()` is `WEB_VERSION`. It was one `VERSION` for every build until each store
 * got its own number; this went on reading the old name, and every deploy stopped here.
 */
function release() {
  const kt = readFileSync('composeApp/src/commonMain/kotlin/game/vinto/app/Version.kt', 'utf8');
  const m = kt.match(/const\s+val\s+WEB_VERSION\s*=\s*"([^"]+)"/);
  if (!m) throw new Error('Version.kt no longer declares WEB_VERSION — the release name is a guess without it');
  return `vinto@${m[1]}`;
}

// First, before the bundle is looked at or stamped: a release name that cannot be read is a
// failure of this script and not of the build, and it should not leave the bundle half-injected.
const RELEASE = release();

// Written by tools/wasm-symbols.mjs. Checked before anything is sent, so a deploy that skipped it
// fails with the reason rather than uploading half of what a crash needs.
const SYMBOLS = 'composeApp/build/wasm-symbols/symbols.json';
if (!existsSync(SYMBOLS)) {
  console.error(`no ${SYMBOLS}: run node tools/wasm-symbols.mjs first, or every wasm frame stays a bare name.`);
  process.exit(1);
}
const { debugFile, buildId } = JSON.parse(readFileSync(SYMBOLS, 'utf8'));

// `composeApp.js` locally, `composeApp.<hash>.js` once the deploy has content-addressed it.
const scripts = readdirSync(DIST).filter((f) => /^composeApp\..*js$/.test(f) && !f.endsWith('.map'));
if (scripts.length !== 1) {
  console.error(`expected exactly one composeApp script in ${DIST}, found: ${scripts.join(', ') || 'none'}`);
  process.exit(1);
}
const bundle = join(DIST, scripts[0]);
const map = `${bundle}.map`;
if (!existsSync(map)) {
  console.error(`${bundle} has no ${map} beside it — nothing to symbolicate through`);
  process.exit(1);
}

if (spawnSync('sentry-cli', ['--version'], { stdio: 'ignore' }).status !== 0) {
  console.error('sentry-cli is not installed, so this bundle\'s JavaScript frames would stay minified.');
  console.error('Install it (npm i -g @sentry/cli).');
  process.exit(1);
}
if (!process.env.SENTRY_AUTH_TOKEN && !existsSync(join(homedir(), '.sentryclirc'))) {
  console.error('No SENTRY_AUTH_TOKEN and no ~/.sentryclirc, so the source map cannot reach Sentry.');
  console.error('Set one of them.');
  process.exit(1);
}

// The official two-step flow: inject stamps a debug id into the bundle and its map so the pair
// is matched by content, then upload sends them. Sentry's own guidance leads with this rather
// than with release-only uploads, and it costs nothing to follow — the debug id is a comment and
// a few hundred bytes. It does not replace `--release` here: matching still happens by release
// and file name, because this app builds its Sentry envelope by hand and so sends no
// `debug_meta` for a sourcemap the way the JavaScript SDK would.
const inject = spawnSync('sentry-cli', ['sourcemaps', 'inject', bundle, map], { stdio: 'inherit' });
if (inject.status !== 0) {
  console.error('sentry-cli sourcemaps inject failed');
  process.exit(inject.status ?? 1);
}

// `~/` is how Sentry writes "whatever host served this". The frames carry an absolute URL
// (`https://vinto.kupalinka.app/composeApp.js`), which Sentry normalises to `~/composeApp.js`
// before matching — so uploading under the bare host would match nothing.
const args = [
  'sourcemaps', 'upload',
  '--org', ORG,
  '--project', PROJECT,
  '--release', RELEASE,
  '--url-prefix', '~/',
  bundle, map,
];
console.log(`sentry-cli ${args.join(' ')}`);
const uploaded = spawnSync('sentry-cli', args, { stdio: 'inherit' });
if (uploaded.status !== 0) process.exit(uploaded.status ?? 1);

// Matched by the build id inside the file, not by release, so it needs neither --release nor a
// URL. `--include-sources` bundles the Kotlin files the DWARF names, read from this checkout.
const wasm = [
  'debug-files', 'upload',
  '--org', ORG,
  '--project', PROJECT,
  '--include-sources',
  '--wait',
  debugFile,
];
console.log(`sentry-cli ${wasm.join(' ')}   # build id ${buildId}`);
process.exit(spawnSync('sentry-cli', wasm, { stdio: 'inherit' }).status ?? 1);
