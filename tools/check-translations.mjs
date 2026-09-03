#!/usr/bin/env node
/**
 * Every locale's strings.xml, held against the English one.
 *
 *     node tools/check-translations.mjs
 *
 * Run it after translating, and before believing a locale is done. It exits non-zero and prints
 * every problem it found rather than the first, because a translator fixing one file wants the
 * whole list.
 *
 * WHAT IT CHECKS, and why each is here rather than left to review.
 *
 *   * **Key set.** A missing key falls back to English silently — the app looks translated until
 *     somebody reaches the one screen that is not. An EXTRA key is worse than useless: it is a
 *     string nothing renders, kept in step by hand forever.
 *   * **Placeholders.** `%1$s` and friends are positional, and a translator reordering a sentence
 *     naturally will sometimes drop one or invent one. A missing placeholder is a crash on some
 *     targets and a literal "%2$d" on others, and it only happens for the one language nobody
 *     testing the app happens to read.
 *   * **Quote escapes.** compose-resources does NOT process Android's `\'`, so the backslash is
 *     drawn on screen. `StringEscapeTest` already fails the build on this; it is repeated here so
 *     a translator gets told by the tool they are running rather than by a Kotlin test suite.
 *   * **Double-escaped entities.** `&amp;amp;` renders as "&amp;" — the classic result of escaping
 *     text that was already escaped, which is exactly what happens when a translation is copied
 *     out of the source file rather than written from it.
 *   * **Well-formedness**, loosely: a stray bare `&` or an unclosed tag fails the resource
 *     compiler with a message that names a byte offset and not a string.
 *
 * It deliberately does NOT check that anything is actually translated. A locale may legitimately
 * keep an English word — "Vinto" is one — and a tool that guessed about that would cry wolf.
 */

import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const RES = 'composeApp/src/commonMain/composeResources';
const SOURCE = join(RES, 'values/strings.xml');

/** `name` → raw value text, in file order. */
function parse(file) {
  const xml = readFileSync(file, 'utf8');
  const out = new Map();
  for (const m of xml.matchAll(/<string\s+name="([^"]+)"\s*>([\s\S]*?)<\/string>/g)) {
    out.set(m[1], m[2]);
  }
  return out;
}

/** The positional specifiers in one value, as a sorted multiset. */
function placeholders(value) {
  return [...value.matchAll(/%(\d+)\$[a-zA-Z]/g)].map((m) => m[0]).sort();
}

const source = parse(SOURCE);
const locales = readdirSync(RES)
  .filter((d) => d.startsWith('values-'))
  .sort();

let failed = false;
const rows = [];

for (const dir of locales) {
  const file = join(RES, dir, 'strings.xml');
  const problems = [];
  if (!existsSync(file)) {
    console.error(`${dir}: no strings.xml`);
    failed = true;
    continue;
  }
  const raw = readFileSync(file, 'utf8');
  const target = parse(file);

  if (!raw.includes('<resources') || !raw.includes('</resources>')) {
    problems.push('not wrapped in <resources>');
  }

  const missing = [...source.keys()].filter((k) => !target.has(k));
  const extra = [...target.keys()].filter((k) => !source.has(k));
  if (missing.length) problems.push(`${missing.length} missing: ${missing.slice(0, 5).join(', ')}${missing.length > 5 ? '…' : ''}`);
  if (extra.length) problems.push(`${extra.length} unknown: ${extra.slice(0, 5).join(', ')}${extra.length > 5 ? '…' : ''}`);

  for (const [key, value] of target) {
    if (!source.has(key)) continue;
    const want = placeholders(source.get(key)).join(',');
    const got = placeholders(value).join(',');
    if (want !== got) problems.push(`${key}: placeholders [${got}] should be [${want}]`);
    if (/\\['"]/.test(value)) problems.push(`${key}: escapes a quote as \\' or \\" — compose-resources draws the backslash`);
    if (/&amp;(amp|lt|gt|quot|apos);/.test(value)) problems.push(`${key}: double-escaped entity`);
    // A bare `&` that is not the start of an entity fails the resource compiler.
    if (/&(?!(amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)/.test(value)) problems.push(`${key}: bare & (write &amp;)`);
  }

  if (problems.length) {
    failed = true;
    console.error(`\n${dir} — ${problems.length} problem(s)`);
    for (const p of problems.slice(0, 20)) console.error(`  ${p}`);
    if (problems.length > 20) console.error(`  …and ${problems.length - 20} more`);
  }
  rows.push([dir.replace('values-', ''), target.size, problems.length]);
}

console.log(`\n${source.size} strings in the source (${SOURCE})\n`);
console.log('locale  strings  problems');
for (const [loc, n, p] of rows) {
  console.log(`${loc.padEnd(7)} ${String(n).padStart(6)}  ${p === 0 ? 'ok' : p}`);
}
console.log(`\n${rows.length} locales`);

process.exit(failed ? 1 : 0);
