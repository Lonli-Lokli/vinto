#!/usr/bin/env node
/**
 * The web bundle still knows its own function names.
 *
 *     node tools/check-wasm-names.mjs [dist-dir]
 *
 * The other two halves of web symbolication fail loudly when they break, because both end in an
 * upload that can be refused. This one has nothing to refuse it: the Kotlin names in a wasm crash
 * frame come from the module's own **name section**, which is shipped rather than uploaded. Turn
 * it off and nothing anywhere complains — the build is smaller, the site works, and every crash
 * report reads `wasm-function[15659]` forever.
 *
 * It is one flag away from being off, and the flag is not obviously about crash reports:
 * `binaryenArguments.add("-g")` in `composeApp/build.gradle.kts`. Anybody trimming 432 KB off the
 * bundle would take it out, and would be right about the size and wrong about the cost. So the
 * property is checked where it can be seen — in the artefact, not in the build script, because
 * what matters is what shipped and not what was configured.
 *
 * Reads the wasm binary's section table directly. `wasm-opt` rewrites function indices, so
 * nothing about the pre-optimised module tells you whether the shipped one kept its names.
 */

import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const DIST = process.argv[2] ?? 'composeApp/build/dist/wasmJs/productionExecutable';
const OURS = 'game.vinto.';

/** Function names out of a wasm module's `name` custom section, or [] if it has none. */
function functionNames(buf) {
  let p = 8; // magic + version
  const u32 = () => {
    let r = 0, s = 0, b;
    do { b = buf[p++]; r |= (b & 0x7f) << s; s += 7; } while (b & 0x80);
    return r >>> 0;
  };
  const names = [];
  while (p < buf.length) {
    const id = buf[p++];
    const size = u32();
    const body = p;
    p += size;
    if (id !== 0) continue; // not a custom section
    const after = p;
    p = body;
    const len = u32();
    const sectionName = buf.slice(p, p + len).toString();
    if (sectionName === 'name') {
      let r = p + len;
      const end = body + size;
      while (r < end) {
        p = r;
        const sub = buf[p++];
        const subSize = u32();
        const subBody = p;
        if (sub === 1) { // the function-name subsection
          p = subBody;
          const count = u32();
          for (let i = 0; i < count; i++) {
            u32(); // index
            const l = u32();
            names.push(buf.slice(p, p + l).toString());
            p += l;
          }
        }
        r = subBody + subSize;
      }
    }
    p = after;
  }
  return names;
}

const wasms = readdirSync(DIST).filter((f) => f.endsWith('.wasm'));
if (wasms.length === 0) {
  console.error(`no .wasm in ${DIST} — run ./gradlew :composeApp:wasmJsBrowserDistribution first`);
  process.exit(1);
}

let best = null;
for (const f of wasms) {
  const names = functionNames(readFileSync(join(DIST, f)));
  const ours = names.filter((n) => n.includes(OURS)).length;
  console.log(`${f}: ${names.length} function names, ${ours} of them ours`);
  if (!best || ours > best.ours) best = { f, ours, total: names.length };
}

// Only one of the two modules is ours — the other is Skia, and it is not expected to mention us.
if (!best || best.ours === 0) {
  console.error('');
  console.error('No wasm module in this bundle carries Kotlin function names.');
  console.error('Every web crash would be filed under `wasm-function[N]` with nothing to read.');
  console.error('The cause is almost certainly a missing `binaryenArguments.add("-g")` in');
  console.error('composeApp/build.gradle.kts — wasm-opt strips the name section by default.');
  process.exit(1);
}

console.log(`ok: ${best.f} carries ${best.ours} of our function names`);
