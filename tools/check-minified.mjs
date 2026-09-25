#!/usr/bin/env node
/**
 * Refuses a Play bundle that R8 did not process.
 *
 *   node tools/check-minified.mjs                  # the bundle `vydanne prerelease` would upload
 *   node tools/check-minified.mjs <file.aab|dir>   # a particular one, or a directory's newest
 *
 * **R8 is not optional for a build that reaches a store.** It halves the download (10.30 MB against
 * 5.93 MB when it was measured) and it is what the Sentry mapping upload and Play's deobfuscation
 * both assume. `isMinifyEnabled = true` is set in `androidApp/build.gradle.kts`, unconditionally —
 * and a flag in a build script is exactly the kind of thing that is switched off "just for this
 * build" and stays off. So the rule is checked on the artifact, where it cannot be argued with: a
 * bundle R8 processed carries its mapping at `BUNDLE-METADATA/com.android.tools.build.obfuscation/
 * proguard.map`, and one it did not, does not.
 *
 * It runs in the two places a bundle is trusted: before every Play upload (`npm run play:closed`,
 * `play:internal`), and in CI after the release bundle is built (`kmp-android`), so a change that
 * quietly turns R8 off fails a pull request rather than a release.
 *
 * Plain Node and no `unzip`: an .aab is a zip, and the names in its central directory are all this
 * needs — read from the end of the file, where the zip format keeps them.
 */
import fs from 'node:fs';
import path from 'node:path';

/** Where R8 leaves its mapping inside a bundle. Its presence is the proof R8 ran. */
const MAPPING = 'BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map';

/** `google.aab` in vydanne.config.mjs: a directory, whose newest .aab is the one uploaded. */
const DEFAULT_DIR = 'androidApp/build/outputs/bundle/release';

function fail(lines) {
  console.error(`\x1b[31mrefusing the bundle: ${lines[0]}\x1b[0m`);
  for (const line of lines.slice(1)) console.error(`  ${line}`);
  process.exit(1);
}

/** The .aab at [target], or a directory's newest by modification time — vydanne's own rule. */
function bundleAt(target) {
  if (!fs.existsSync(target)) fail([`nothing at ${target}.`, 'Build it: ./gradlew :androidApp:bundleRelease']);
  if (!fs.statSync(target).isDirectory()) return target;
  const newest = fs.readdirSync(target)
    .filter((name) => name.endsWith('.aab'))
    .map((name) => path.join(target, name))
    .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs)[0];
  if (!newest) fail([`no .aab in ${target}.`, 'Build it: ./gradlew :androidApp:bundleRelease']);
  return newest;
}

/**
 * Every entry name in a zip, from its central directory.
 *
 * The end-of-central-directory record is the last thing in the file (22 bytes, plus a comment of up
 * to 64 KB), and it says where the directory starts and how many entries it holds. Zip64 is not
 * handled — a Play bundle is far below the 4 GB and 65,535-entry limits that would need it — and is
 * refused rather than misread.
 */
function entryNames(file) {
  const data = fs.readFileSync(file);
  const EOCD = 0x06054b50;
  let end = -1;
  for (let i = data.length - 22; i >= Math.max(0, data.length - 22 - 0xffff); i--) {
    if (data.readUInt32LE(i) === EOCD) {
      end = i;
      break;
    }
  }
  if (end < 0) fail([`${file} is not a zip.`]);

  const count = data.readUInt16LE(end + 10);
  let at = data.readUInt32LE(end + 16);
  if (count === 0xffff || at === 0xffffffff) fail([`${file} is a Zip64 archive, which this does not read.`]);

  const names = [];
  for (let n = 0; n < count; n++) {
    if (data.readUInt32LE(at) !== 0x02014b50) fail([`${file} has a damaged central directory.`]);
    const nameLength = data.readUInt16LE(at + 28);
    const extraLength = data.readUInt16LE(at + 30);
    const commentLength = data.readUInt16LE(at + 32);
    names.push(data.toString('utf8', at + 46, at + 46 + nameLength));
    at += 46 + nameLength + extraLength + commentLength;
  }
  return names;
}

const bundle = bundleAt(process.argv[2] ?? DEFAULT_DIR);
const shown = path.relative(process.cwd(), bundle) || bundle;

if (!entryNames(bundle).includes(MAPPING)) {
  fail([
    `${shown} was not minified by R8.`,
    `It carries no ${MAPPING}, which every R8-processed bundle does.`,
    '',
    'A bundle that reaches a store is minified, locally and in CI alike. Check that the release',
    'build type in androidApp/build.gradle.kts still sets isMinifyEnabled = true, rebuild with',
    './gradlew :androidApp:bundleRelease, and try again.',
  ]);
}

console.log(`\x1b[32mminified by R8\x1b[0m — ${shown}`);
