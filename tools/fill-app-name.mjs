#!/usr/bin/env node
/**
 * Give every locale the one string that is never translated.
 *
 *     node tools/fill-app-name.mjs
 *
 * `app_name` is "VINTO" — a brand, so identical in all twenty languages. Every translator
 * therefore, quite reasonably, leaves it out, and every locale then differs from the source by
 * exactly one key. `tools/check-translations.mjs` calls that missing, because a checker that
 * made an exception for "keys whose value looks like a brand" would be guessing.
 *
 * Writing it explicitly is the honest fix: the key sets match, the checker stays strict, and
 * nothing depends on a fallback quietly doing the right thing. Run it after translating.
 */

import { readdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const RES = 'composeApp/src/commonMain/composeResources';
const source = readFileSync(join(RES, 'values/strings.xml'), 'utf8');

const line = source.split('\n').find((l) => l.includes('name="app_name"'));
if (!line) throw new Error('the source has no app_name');

let filled = 0;
for (const dir of readdirSync(RES).filter((d) => d.startsWith('values-')).sort()) {
  const file = join(RES, dir, 'strings.xml');
  if (!existsSync(file)) continue;
  let xml = readFileSync(file, 'utf8');
  if (xml.includes('name="app_name"')) continue;

  // In at the top of the block, which is where the source keeps it — the two files stay
  // readable side by side, and a diff of them stays about words rather than about order.
  xml = xml.replace(/(<resources[^>]*>\n)/, `$1${line}\n`);
  writeFileSync(file, xml);
  console.log(`${dir}: added app_name`);
  filled += 1;
}
console.log(filled === 0 ? 'every locale already had it' : `${filled} locale(s) filled`);
