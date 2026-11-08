#!/usr/bin/env node
/**
 * The web page's `navigator.languages` shim, run against the languages a browser can report.
 *
 *     node tools/check-browser-languages.mjs
 *
 * Compose Multiplatform 1.12 reads the system locale on the web as `window.navigator.languages`
 * and hands every entry to `new Intl.Locale(tag)`, unguarded (`LocaleList.current` in ui-text's
 * `PlatformLocale.web.kt`). One entry Intl will not parse — a Java-style `en_US`, `*`, a POSIX
 * `en-us@posix` — throws `RangeError: Incorrect locale information provided` before the first
 * screen draws. That is Sentry VINTO-37 (web, 1.5 / 720, surface MENU), reported from an address
 * in Meta's crawler range, which is a browser nobody would think to test in.
 *
 * Compose's code cannot be changed from here, but what it reads can: `index.html` already shadows
 * `Navigator.prototype.languages` so the language picker works, and the same getter is where a tag
 * Intl refuses is mended or left out. This runs that script in a bare `vm` context with a stand-in
 * `Navigator`, then does exactly what Compose does with the answer.
 *
 * Exits non-zero and prints every case that fails.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const page = readFileSync(join(root, 'composeApp/src/wasmJsMain/resources/index.html'), 'utf8');

const shim = [...page.matchAll(/<script>([\s\S]*?)<\/script>/g)]
  .map((m) => m[1])
  .find((body) => body.includes("'languages'"));
if (!shim) {
  console.error('index.html no longer carries the navigator.languages shim');
  process.exit(1);
}

/** What the page would answer for a browser that reports [reported], after the shim ran. */
function answered(reported, chosen) {
  class Navigator {}
  Object.defineProperty(Navigator.prototype, 'languages', {
    configurable: true,
    enumerable: true,
    get() { return Object.freeze(reported.slice()); },
  });
  const window = {};
  const context = vm.createContext({ Navigator, Object, String, Array, Intl, window });
  vm.runInContext(shim, context);
  if (chosen !== undefined) window.__vintoLocale = chosen;
  return Reflect.get(Navigator.prototype, 'languages', new Navigator());
}

/** Compose's own reading: every entry through `new Intl.Locale`, and the first one is current. */
function composeReads(languages) {
  const locales = Array.from(languages, (tag) => new Intl.Locale(tag));
  if (locales.length === 0) throw new Error('an empty list, and Compose takes the first entry');
  return locales.map((l) => l.toString());
}

const cases = [
  // A browser in good order: passed through untouched, order kept.
  { reported: ['en-GB', 'de', 'zh-Hant-TW'], expect: ['en-GB', 'de', 'zh-Hant-TW'] },
  // Java and Facebook style, mended rather than dropped: it names a real language.
  { reported: ['en_US'], expect: ['en-US'] },
  { reported: ['pt_BR', 'en'], expect: ['pt-BR', 'en'] },
  // Nothing Intl can read: left out, and the rest kept.
  { reported: ['*', 'fr'], expect: ['fr'] },
  { reported: ['en-us@posix', 'de-DE'], expect: ['de-DE'] },
  { reported: ['', 'it'], expect: ['it'] },
  // Nothing usable at all: English, the app's own default, rather than an empty list.
  { reported: ['*'], expect: ['en'] },
  { reported: [], expect: ['en'] },
  // The language picker's choice still wins.
  { reported: ['en_US'], chosen: 'uk', expect: ['uk'] },
];

const failures = [];
for (const { reported, chosen, expect } of cases) {
  const label = JSON.stringify(reported) + (chosen ? ` with ${chosen} chosen` : '');
  try {
    const got = composeReads(answered(reported, chosen));
    if (JSON.stringify(got) !== JSON.stringify(expect)) {
      failures.push(`${label}: Compose reads ${JSON.stringify(got)}, expected ${JSON.stringify(expect)}`);
    }
  } catch (error) {
    failures.push(`${label}: ${error.name}: ${error.message}`);
  }
}

if (failures.length) {
  console.error(`navigator.languages shim: ${failures.length} of ${cases.length} cases fail\n  ` + failures.join('\n  '));
  process.exit(1);
}
console.log(`navigator.languages shim: all ${cases.length} cases read cleanly`);
