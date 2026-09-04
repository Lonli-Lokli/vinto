#!/usr/bin/env node
/**
 * Send the web bundle's JavaScript source map to Sentry.
 *
 *     node tools/upload-web-sourcemaps.mjs [dist-dir]
 *
 * Run after `./gradlew :composeApp:wasmJsBrowserDistribution`. It publishes nothing itself, and
 * it is only half the job: **`strip-web-sourcemaps.mjs` has to run after it**, because a map
 * that reaches Sentry must not also reach the public asset store.
 *
 * ## What this does and does not buy
 *
 * A web crash reaches Sentry with two kinds of frame. The **wasm** frames carry our Kotlin
 * function names already, because the build keeps the wasm name section (`binaryenArguments` in
 * `composeApp/build.gradle.kts`); they need no upload and this script does not touch them. What
 * is minified is the **JavaScript glue** webpack emits — `composeApp.js`, one line, half a
 * megabyte — and those frames are what this makes readable.
 *
 * There is no equivalent for `vinto-kmp-composeApp.wasm.map`: Sentry symbolicates WebAssembly
 * from DWARF debug files keyed by a `build_id` custom section, which Kotlin/Wasm does not emit,
 * and it does not read wasm source maps at all. That is why the name section is the mechanism
 * for the half of the stack that matters.
 *
 * ## Credentials
 *
 * `sentry-cli` reads its token from `SENTRY_AUTH_TOKEN` or from `~/.sentryclirc`, whichever it
 * finds. So a developer keeps using the rc file, CI sets the environment variable, and neither
 * needs a branch here — the same rule the iOS dSYM upload follows.
 *
 * **Absent credentials are a failure.** A deploy nobody can read a stack trace from is worse than
 * a deploy that did not happen: the build goes out, people play it, it crashes, and every report
 * is unreadable — with a green pipeline behind it saying nothing went wrong.
 * `VINTO_ALLOW_UNSYMBOLICATED=1` waives it, and has to be typed. The Android and iOS halves
 * follow the same rule.
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
 * release cannot collide even though the release string (`vinto@1.0`, a constant in
 * `Version.kt`) never changes. Uploading from a local dist, where the file is still plain
 * `composeApp.js`, does overwrite the previous local upload — which is what you want there.
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';
import { spawnSync } from 'node:child_process';

const DIST = process.argv[2] ?? 'composeApp/build/dist/wasmJs/productionExecutable';
const ORG = 'echo-xl';
const PROJECT = 'vinto';

/** Must equal `Version.kt`'s `VERSION`, which is what `Crashes.install` sends as the release. */
function release() {
  const kt = readFileSync('composeApp/src/commonMain/kotlin/game/vinto/app/Version.kt', 'utf8');
  const m = kt.match(/const\s+val\s+VERSION\s*=\s*"([^"]+)"/);
  if (!m) throw new Error('Version.kt no longer declares VERSION — the release name is a guess without it');
  return `vinto@${m[1]}`;
}

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

const waived = !!process.env.VINTO_ALLOW_UNSYMBOLICATED;
if (spawnSync('sentry-cli', ['--version'], { stdio: 'ignore' }).status !== 0) {
  if (waived) {
    console.warn('warning: VINTO_ALLOW_UNSYMBOLICATED is set and sentry-cli is absent — not uploaded');
    process.exit(0);
  }
  console.error('sentry-cli is not installed, so this bundle\'s JavaScript frames would stay minified.');
  console.error('Install it (npm i -g @sentry/cli), or set VINTO_ALLOW_UNSYMBOLICATED=1 to skip on purpose.');
  process.exit(1);
}
if (!process.env.SENTRY_AUTH_TOKEN && !existsSync(join(homedir(), '.sentryclirc'))) {
  if (waived) {
    console.warn('warning: VINTO_ALLOW_UNSYMBOLICATED is set and there are no credentials — not uploaded');
    process.exit(0);
  }
  console.error('No SENTRY_AUTH_TOKEN and no ~/.sentryclirc, so the source map cannot reach Sentry.');
  console.error('Set one of them, or set VINTO_ALLOW_UNSYMBOLICATED=1 to skip symbolication on purpose.');
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
  '--release', release(),
  '--url-prefix', '~/',
  bundle, map,
];
console.log(`sentry-cli ${args.join(' ')}`);
process.exit(spawnSync('sentry-cli', args, { stdio: 'inherit' }).status ?? 1);
