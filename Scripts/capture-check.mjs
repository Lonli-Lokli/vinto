#!/usr/bin/env node
// A COPY of the portfolio's capture check, `tools/capture-check.mjs` in gulnya, as of gulnya commit 21330e2 (2026-10-20).
// Vinto must build without ../gulnya checked out, so its build calls this copy and reads no gulnya file. Change it
// there, then copy it again: gulnya's test fails when this file, after this header, differs from the original.
// Everything below is the original, byte for byte, from the blank line after this header.
//
// capture-check.mjs: does a built app hold store-capture code, and should it?
//
//   node tools/capture-check.mjs <path to .aab | .apk | .app | .xcarchive | .ipa>
//
// The portfolio's rule (kirunak task gulnya/capture-debug-only, App Store guideline 2.5.19): no build that goes to a
// store holds capture code, neither the handle zdymak opens a scene with nor any marketing scene. Only each app's
// capture build carries it. Every capture file of every app carries one shared id, SHARED_ID below, which is also the
// handle key (the Android intent extra, the iOS launch argument), and this searches the built file for it.
//
// WHICH VERDICT a build needs comes from the build itself, from its own id (the manifest's package, which is the
// applicationId; the iOS or macOS CFBundleIdentifier):
//   - an id ending in `.capture` is a capture build: SHARED_ID must sit in its code (a .dex, a Mach-O), so every
//     capture build proves the search sees the id in that app's real binaries. A search that finds nothing passes
//     every build, a blind one too.
//   - any other id is a store build: SHARED_ID must sit in none of its files.
// There is no option, flag or environment variable: the file is the only input, so nothing can switch the check off
// or choose the lenient verdict. The upload tools refuse a `.capture` id, so a build that holds the id never ships.
//
// HOW IT SEARCHES. Every file of the build, byte for byte: an .aab or .apk is unzipped in memory (every entry, nested
// zips too), an .app is walked whole (frameworks, plug-ins, a Watch app). The id is ASCII, so one byte pattern covers
// UTF-8, Latin-1 and dex's MUTF-8, and a second and third cover UTF-16 in both byte orders (Kotlin/Native keeps a
// String's characters as UTF-16 unless it stores them as Latin-1; Objective-C keeps some literals as UTF-16). R8 and
// the linkers rename and strip code but keep a string the code still uses whole.
//
// Exit 0: the verdict holds. 1: it does not. 2: the file cannot be read. A caller fails on anything but 0, so a
// build that cannot run this never passes without it. Reading the iOS id asks plutil, so .app, .xcarchive and .ipa
// need macOS; .aab and .apk need only Node.

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { aabPackage, apkPackage, zipEntries } from './android-archive.mjs';

export const SHARED_ID = 'kupalinka-capture-a47d267ea8c1795fbb7df224';
export const CAPTURE_SUFFIX = '.capture';

const PATTERNS = [
  { encoding: 'UTF-8', bytes: Buffer.from(SHARED_ID, 'latin1') },
  { encoding: 'UTF-16LE', bytes: Buffer.from(SHARED_ID, 'utf16le') },
  { encoding: 'UTF-16BE', bytes: Buffer.from(SHARED_ID, 'utf16le').swap16() },
];

/** The encodings in which `data` holds SHARED_ID, with the first offset of each. */
export function findId(data) {
  const hits = [];
  for (const { encoding, bytes } of PATTERNS) {
    const at = data.indexOf(bytes);
    if (at >= 0) hits.push({ encoding, at });
  }
  return hits;
}

// ---- Android (.aab, .apk) ----

/** An .aab or .apk: its applicationId and every file in it, nested zips opened too. */
export function readAndroid(file) {
  const label = path.basename(file);
  const entries = zipEntries(fs.readFileSync(file), label);
  const bundle = file.endsWith('.aab');
  const manifestName = bundle ? 'base/manifest/AndroidManifest.xml' : 'AndroidManifest.xml';
  const manifest = entries.find((e) => e.name === manifestName);
  if (!manifest) throw new Error(`${label} has no ${manifestName}`);
  const id = bundle ? aabPackage(manifest.data) : apkPackage(manifest.data);
  const files = [];
  const add = (prefix, list) => {
    for (const e of list) {
      const name = prefix + e.name;
      files.push({ name, data: e.data, code: /\.dex$/.test(e.name) });
      if (e.data.length >= 4 && e.data.readUInt32LE(0) === 0x04034b50) add(`${name}!/`, zipEntries(e.data, name));
    }
  };
  add('', entries);
  return { id, files };
}

// ---- Apple (.app, .xcarchive, .ipa) ----

const MACH_O = new Set([0xfeedface, 0xfeedfacf, 0xcefaedfe, 0xcffaedfe, 0xcafebabe, 0xbebafeca]);

/** The `.app` inside whatever was given, and a function that removes anything unpacked to find it. */
export function locateApp(input) {
  const firstApp = (dir) => {
    const app = fs.existsSync(dir) ? fs.readdirSync(dir).find((e) => e.endsWith('.app')) : null;
    return app ? path.join(dir, app) : null;
  };
  if (input.endsWith('.app')) return { app: input, cleanup: () => {} };
  if (input.endsWith('.xcarchive')) {
    const app = firstApp(path.join(input, 'Products', 'Applications'));
    if (!app) throw new Error(`${input} holds no Products/Applications/*.app`);
    return { app, cleanup: () => {} };
  }
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'capture-check-'));
  const cleanup = () => fs.rmSync(tmp, { recursive: true, force: true });
  try {
    execFileSync('unzip', ['-q', input, 'Payload/*', '-d', tmp]);
  } catch (e) {
    cleanup();
    throw e;
  }
  const app = firstApp(path.join(tmp, 'Payload'));
  if (!app) {
    cleanup();
    throw new Error(`${input} holds no Payload/*.app`);
  }
  return { app, cleanup };
}

/** An .app (iOS, or macOS with Contents/): its bundle id and every regular file in it. Symbolic links are skipped. */
export function readApple(app) {
  const plist = ['Info.plist', 'Contents/Info.plist'].map((p) => path.join(app, p)).find((p) => fs.existsSync(p));
  if (!plist) throw new Error(`${path.basename(app)} has no Info.plist`);
  const id = execFileSync('plutil', ['-extract', 'CFBundleIdentifier', 'raw', '-o', '-', plist], { encoding: 'utf8' }).trim();
  const files = [];
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.isFile()) {
        const data = fs.readFileSync(p);
        files.push({ name: path.relative(app, p), data, code: data.length >= 4 && MACH_O.has(data.readUInt32BE(0)) });
      }
    }
  };
  walk(app);
  return { id, files };
}

// ---- the verdict ----

/** The verdict for one build: { capture, problems, lines }. Pure, for the test. */
export function judge({ id, files }) {
  const capture = id.endsWith(CAPTURE_SUFFIX);
  const found = files.flatMap((f) => findId(f.data).map((h) => ({ ...h, name: f.name, code: f.code })));
  const lines = found.map((h) => `  ${h.name}: the shared id (${h.encoding}) at byte ${h.at}`);
  const problems = [];
  if (capture && !found.some((h) => h.code)) {
    problems.push(
      found.length
        ? `${id} is a capture build, but the shared id is in no .dex or Mach-O, only in other files: the capture code is ` +
            'missing, or this search is blind to how its binaries store strings.'
        : `${id} is a capture build, but no file holds the shared id: the capture code is missing, or this search is ` +
            'blind to how its binaries store strings.',
    );
  }
  if (!capture && found.length) {
    problems.push(
      `${id} is a store build, and ${found.length === 1 ? 'a file holds' : `${found.length} places hold`} the shared ` +
        'id: capture code is in it. It belongs only in the capture build (kirunak task gulnya/capture-debug-only).',
    );
  }
  return { capture, problems, lines };
}

/** Checks one build; prints the report and returns the exit code. */
export function main(argv) {
  const input = argv[0];
  if (argv.length !== 1 || input === '--help' || input === '-h') {
    console.log('usage: capture-check.mjs <path to .aab | .apk | .app | .xcarchive | .ipa>');
    return input === '--help' || input === '-h' ? 0 : 2;
  }
  const abs = path.resolve(input);
  let build;
  let cleanup = () => {};
  try {
    if (!fs.existsSync(abs)) throw new Error(`${input} does not exist`);
    if (/\.(aab|apk)$/.test(abs)) build = readAndroid(abs);
    else if (/\.(app|xcarchive|ipa)$/.test(abs)) {
      const located = locateApp(abs);
      cleanup = located.cleanup;
      build = readApple(located.app);
    } else {
      throw new Error(`${input} is not an .aab, .apk, .app, .xcarchive or .ipa`);
    }
  } catch (e) {
    cleanup();
    console.error(`capture-check: could not read ${input}: ${e.message}`);
    return 2;
  }
  cleanup();
  const { capture, problems, lines } = judge(build);
  const kind = capture ? 'capture build, must hold the shared id in its code' : 'store build, must hold no capture code';
  console.log(`capture-check: ${path.basename(abs)} (${build.id}, ${build.files.length} files): ${kind}`);
  if (lines.length) console.log(lines.join('\n'));
  if (!problems.length) {
    console.log(`capture-check: ${capture ? 'the search sees the shared id' : 'no capture code'}.`);
    return 0;
  }
  const ci = Boolean(process.env.GITHUB_ACTIONS);
  for (const p of problems) console.error(ci ? `::error title=Capture code::${p}` : `capture-check: ${p}`);
  return 1;
}

// Run, unless imported (by the test). realpath on both sides, or a symlinked path would skip the check and exit 0.
if (process.argv[1] && fs.realpathSync(process.argv[1]) === fs.realpathSync(new URL(import.meta.url).pathname)) {
  process.exitCode = main(process.argv.slice(2));
}
