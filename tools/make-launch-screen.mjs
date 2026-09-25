#!/usr/bin/env node
/**
 * The launch screen: the icon's own V standing whole on the icon's own felt.
 *
 *     node tools/make-launch-screen.mjs      (from the repository root)
 *
 * WHY IT EXISTS. With no launch screen of its own, Android built one from the adaptive icon: the
 * felt tile cropped into a circle on a grey ground, which the owner called a "strange logo inside
 * border". iOS showed a blank screen (`UILaunchScreen` was an empty dict). Both now show the V
 * alone, with no disk and no frame, on the felt the whole game is played on.
 *
 * NOTHING HERE IS DRAWN. Everything is read from `brand/vinto-icon.svg`: its ground, the V's
 * outline (Cinzel Bold's capital) and where the icon places it. The dark appearance is
 * `znachok.config.mjs`'s own hex-for-hex swap, the one the dark app icon is made with. So the
 * launch mark cannot drift from the icon; re-run this after either file changes, and commit what
 * it writes:
 *
 *   brand/vinto-launch.svg                                    the mark on transparency (light)
 *   androidApp/src/main/res/drawable/launch_mark.xml          the splash icon, as a vector
 *   androidApp/src/main/res/values{,-night}/launch.xml        the felt and the V, per phone theme
 *   iosApp/iosApp/Assets.xcassets/LaunchMark.imageset         the mark at 250 pt, 1x-3x, light and dark
 *   iosApp/iosApp/Assets.xcassets/LaunchFelt.colorset         the felt, light and dark
 *
 * `Theme.Vinto.Launch` (values/themes.xml) and `UILaunchScreen` (Info.plist) name them.
 *
 * THE SIZE. A launch icon is shown on a square canvas (288 dp on Android 12+, 240 dp with an icon
 * background) and masked to a circle two thirds of its width; the compat library masks the same
 * way on older Android. The V's corners, the tips of its serifs, are what reach furthest, so the
 * scale is set on them rather than on its bounding box: the furthest point sits at 92% of that
 * circle's radius. The V stays where the icon puts it, centred on the canvas.
 *
 * THE GROUND. The felt, in the phone's theme: the icon's ground (`Felt`, #1B5E43) on a light phone
 * and the dark icon's (`FeltDark`, #0E3428) on a dark one. Those are the greens the first screen
 * draws its felt in (`feltGradient` in VintoTheme.kt), so the launch screen hands over to a table
 * of the same colour rather than to a different room.
 *
 * sharp renders the iOS PNGs. It is not a dependency of this repository: it comes with the store
 * tooling (`vydanne`, `zdymak`), and npm nests it under whichever of them it was installed for, so
 * it is looked for there as well as at the root.
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ICON = join(ROOT, 'brand', 'vinto-icon.svg');

/** The furthest point of the V, as a fraction of the radius of the circle a launch icon keeps. */
const SAFE_FRACTION = 0.92;
/** The iOS launch image, in points. The same size Hronka's is, so the portfolio launches alike. */
const IOS_POINTS = 250;

// ---- reading the icon -------------------------------------------------------------------------

/** Read `key="value"` pairs out of one tag's attribute text. */
function attrsOf(text) {
  const out = {};
  for (const m of text.matchAll(/([a-zA-Z:-]+)\s*=\s*"([^"]*)"/g)) out[m[1]] = m[2];
  return out;
}

/** An affine matrix [a, b, c, d, e, f]: x' = a x + c y + e, y' = b x + d y + f. */
const IDENTITY = [1, 0, 0, 1, 0, 0];

function multiply([a1, b1, c1, d1, e1, f1], [a2, b2, c2, d2, e2, f2]) {
  return [
    a1 * a2 + c1 * b2, b1 * a2 + d1 * b2,
    a1 * c2 + c1 * d2, b1 * c2 + d1 * d2,
    a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1,
  ];
}

const apply = ([a, b, c, d, e, f], [x, y]) => [a * x + c * y + e, b * x + d * y + f];

/**
 * `translate(...)` and `scale(...)`, the only two the icon uses. Anything else is refused rather
 * than approximated: a transform read wrongly is a mark in the wrong place that nobody notices.
 */
function parseTransform(text) {
  let m = IDENTITY;
  for (const [, fn, args] of text.matchAll(/(\w+)\s*\(([^)]*)\)/g)) {
    const n = args.trim().split(/[\s,]+/).map(Number);
    if (fn === 'translate') m = multiply(m, [1, 0, 0, 1, n[0], n[1] ?? 0]);
    else if (fn === 'scale') m = multiply(m, [n[0], 0, 0, n[1] ?? n[0], 0, 0]);
    else throw new Error(`${ICON}: transform ${fn}() is not understood; teach this script or change the icon`);
  }
  return m;
}

/**
 * The icon's one ground and one path, with the transforms that place the path.
 *
 * The master is a `<rect>` for the ground and a `<path>` inside nested `<g transform>`s for the
 * V. Anything more is refused, for the reason `svg-to-drawable.mjs` gives: a converter that
 * quietly skips what it does not know produces a picture that is wrong where nobody looks.
 */
function readIcon() {
  const svg = readFileSync(ICON, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
  const root = attrsOf(svg.match(/<svg\b([^>]*)>/)[1]);
  const [x0, y0, width, height] = root.viewBox.trim().split(/[\s,]+/).map(Number);
  if (x0 !== 0 || y0 !== 0 || width !== height) throw new Error(`${ICON}: expected a square viewBox at 0 0`);

  const stack = [IDENTITY];
  let ground;
  const paths = [];
  for (const [, close, tag, rest] of svg.matchAll(/<(\/?)([a-zA-Z]+)\b([^>]*)>/g)) {
    if (tag === 'svg') continue;
    if (tag === 'g') {
      if (close) stack.pop();
      else stack.push(multiply(stack.at(-1), parseTransform(attrsOf(rest).transform ?? '')));
      continue;
    }
    const a = attrsOf(rest);
    if (tag === 'rect') {
      if (Number(a.width) !== width || Number(a.height) !== height) throw new Error(`${ICON}: the ground is not full bleed`);
      ground = hex(a.fill);
    } else if (tag === 'path') {
      paths.push({ d: a.d, fill: hex(a.fill), matrix: stack.at(-1) });
    } else {
      throw new Error(`${ICON}: <${tag}> is not understood; teach this script or change the icon`);
    }
  }
  if (!ground || paths.length !== 1) throw new Error(`${ICON}: expected one ground and one path (the V)`);
  return { size: width, ground, mark: paths[0] };
}

function hex(value) {
  const m = /^#([0-9a-fA-F]{6})$/.exec(value ?? '');
  if (!m) throw new Error(`${ICON}: colour "${value}" is not a #RRGGBB hex`);
  return m[1].toUpperCase();
}

/**
 * SVG path data as absolute subpaths: { from, segs: [{ c: [control points], to }] }. A segment
 * with no control points is a line, one is a quadratic, two a cubic. M/L/H/V/Q/C/Z in either
 * case; the arc and the smooth forms are refused (a glyph outline has none of them).
 */
function parsePath(d) {
  const tokens = d.match(/[a-zA-Z]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g);
  const subpaths = [];
  let i = 0;
  let cmd = null;
  let cur = [0, 0];
  let current = null;
  const num = () => Number(tokens[i++]);
  const isNum = () => i < tokens.length && !/^[a-zA-Z]$/.test(tokens[i]);
  while (i < tokens.length) {
    if (!isNum()) cmd = tokens[i++];
    else if (cmd === null) throw new Error(`path data starts with a number: ${d.slice(0, 40)}`);
    const rel = cmd === cmd.toLowerCase();
    const pt = () => {
      const x = num();
      const y = num();
      return rel ? [cur[0] + x, cur[1] + y] : [x, y];
    };
    switch (cmd.toUpperCase()) {
      case 'M':
        cur = pt();
        current = { from: cur, segs: [] };
        subpaths.push(current);
        cmd = rel ? 'l' : 'L'; // pairs after a moveto are linetos
        break;
      case 'L':
        cur = pt();
        current.segs.push({ c: [], to: cur });
        break;
      case 'H': {
        const x = num();
        cur = [rel ? cur[0] + x : x, cur[1]];
        current.segs.push({ c: [], to: cur });
        break;
      }
      case 'V': {
        const y = num();
        cur = [cur[0], rel ? cur[1] + y : y];
        current.segs.push({ c: [], to: cur });
        break;
      }
      case 'Q': {
        const c1 = pt();
        const to = pt();
        current.segs.push({ c: [c1], to });
        cur = to;
        break;
      }
      case 'C': {
        const c1 = pt();
        const c2 = pt();
        const to = pt();
        current.segs.push({ c: [c1, c2], to });
        cur = to;
        break;
      }
      case 'Z':
        cur = current.from;
        break;
      default:
        throw new Error(`path command ${cmd} is not understood; teach this script or change the icon`);
    }
  }
  return subpaths;
}

/** Every point of a subpath list moved by [f]. Affine maps keep Béziers Béziers, so this is exact. */
const mapPath = (subpaths, f) =>
  subpaths.map(({ from, segs }) => ({ from: f(from), segs: segs.map(({ c, to }) => ({ c: c.map(f), to: f(to) })) }));

/** Points along the outline, curves included, for measuring how far the V reaches. */
function outlinePoints(subpaths, steps = 64) {
  const out = [];
  for (const { from, segs } of subpaths) {
    let p0 = from;
    out.push(p0);
    for (const { c, to } of segs) {
      for (let k = 1; k <= steps; k++) {
        const t = k / steps;
        const u = 1 - t;
        if (c.length === 0) out.push([u * p0[0] + t * to[0], u * p0[1] + t * to[1]]);
        else if (c.length === 1) {
          out.push([0, 1].map((j) => u * u * p0[j] + 2 * u * t * c[0][j] + t * t * to[j]));
        } else {
          out.push([0, 1].map((j) =>
            u * u * u * p0[j] + 3 * u * u * t * c[0][j] + 3 * u * t * t * c[1][j] + t * t * t * to[j]));
        }
      }
      p0 = to;
    }
  }
  return out;
}

const fmt = (n) => {
  const r = Math.round(n * 100) / 100;
  return String(Object.is(r, -0) ? 0 : r);
};
const pair = ([x, y]) => `${fmt(x)} ${fmt(y)}`;

function pathData(subpaths) {
  return subpaths
    .map(({ from, segs }) =>
      [`M ${pair(from)}`, ...segs.map(({ c, to }) =>
        (c.length === 0 ? 'L ' : c.length === 1 ? 'Q ' : 'C ') + [...c, to].map(pair).join(' ')), 'Z'].join(' '))
    .join(' ');
}

// ---- the mark ---------------------------------------------------------------------------------

const icon = readIcon();
const { default: znachok } = await import(pathToFileURL(join(ROOT, 'znachok.config.mjs')).href);
const darkSwap = Object.fromEntries(
  Object.entries(znachok.themes?.dark ?? {}).map(([from, to]) => [from.toUpperCase(), to.toUpperCase()]),
);

/** Per phone theme: the ground and the V. Dark is znachok's swap, and must name both. */
const COLOURS = {
  light: { felt: icon.ground, v: icon.mark.fill },
  dark: { felt: darkSwap[icon.ground], v: darkSwap[icon.mark.fill] },
};
if (!COLOURS.dark.felt || !COLOURS.dark.v) {
  throw new Error('znachok.config.mjs has no dark swap for the ground or the V; a dark launch screen would be a guess');
}

const N = icon.size;
const centre = [N / 2, N / 2];
const inIcon = mapPath(parsePath(icon.mark.d), (p) => apply(icon.mark.matrix, p));
const reach = Math.max(...outlinePoints(inIcon).map(([x, y]) => Math.hypot(x - centre[0], y - centre[1])));
const safeRadius = N / 3;
const scale = (SAFE_FRACTION * safeRadius) / reach;
const launch = mapPath(inIcon, ([x, y]) => [centre[0] + scale * (x - centre[0]), centre[1] + scale * (y - centre[1])]);
const d = pathData(launch);

const xs = outlinePoints(launch).map((p) => p[0]);
const ys = outlinePoints(launch).map((p) => p[1]);
const box = [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)];

function write(rel, text) {
  const file = join(ROOT, rel);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, text);
  console.log('wrote', rel);
}

const markSvg = (v) => [
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${N} ${N}" width="${N}" height="${N}">`,
  '  <!-- Vinto\'s launch mark: the icon\'s V, whole, on transparency. GENERATED by tools/make-launch-screen.mjs',
  `       from brand/vinto-icon.svg; edit that, not this. The screen behind it is the felt (#${COLOURS.light.felt};`,
  `       #${COLOURS.dark.felt} on a dark phone, where the V is #${COLOURS.dark.v}). -->`,
  `  <path d="${d}" fill="#${v}"/>`,
  '</svg>',
  '',
].join('\n');

write('brand/vinto-launch.svg', markSvg(COLOURS.light.v));

// ---- Android ----------------------------------------------------------------------------------

write('androidApp/src/main/res/drawable/launch_mark.xml', [
  '<?xml version="1.0" encoding="utf-8"?>',
  '<!-- Vinto\'s launch mark, the splash screen\'s icon. GENERATED by tools/make-launch-screen.mjs from',
  '     brand/vinto-icon.svg: do not edit. The icon\'s V, whole, with its furthest point at',
  `     ${Math.round(SAFE_FRACTION * 100)}% of the circle Android keeps of a launch icon (two thirds of the canvas). Its colour`,
  '     is @color/launch_v, which values-night/ lifts for the dark felt as the dark app icon does. -->',
  '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
  '    android:width="288dp" android:height="288dp"',
  `    android:viewportWidth="${N}" android:viewportHeight="${N}">`,
  `    <path android:fillColor="@color/launch_v" android:pathData="${d}" />`,
  '</vector>',
  '',
].join('\n'));

const androidColours = (theme, note) => [
  '<?xml version="1.0" encoding="utf-8"?>',
  '<!-- GENERATED by tools/make-launch-screen.mjs from brand/vinto-icon.svg and znachok.config.mjs: do not edit.',
  `     ${note} -->`,
  '<resources>',
  `    <color name="launch_felt">#FF${COLOURS[theme].felt}</color>`,
  `    <color name="launch_v">#FF${COLOURS[theme].v}</color>`,
  '</resources>',
  '',
].join('\n');

write('androidApp/src/main/res/values/launch.xml', androidColours('light',
  'The launch screen on a light phone: the app icon\'s own felt and V. values-night/ holds the dark icon\'s.'));
write('androidApp/src/main/res/values-night/launch.xml', androidColours('dark',
  'The launch screen on a dark phone: the dark app icon\'s felt and V (znachok\'s dark swap).'));

// ---- iOS --------------------------------------------------------------------------------------

const ASSETS = 'iosApp/iosApp/Assets.xcassets';
const DARK = [{ appearance: 'luminosity', value: 'dark' }];
const json = (value) => `${JSON.stringify(value, null, 2)}\n`;
const component = (h, at) => `0x${h.slice(at, at + 2)}`;
const colour = (h) => ({
  'color-space': 'srgb',
  components: { red: component(h, 0), green: component(h, 2), blue: component(h, 4), alpha: '1.000' },
});

write(`${ASSETS}/LaunchFelt.colorset/Contents.json`, json({
  colors: [
    { idiom: 'universal', color: colour(COLOURS.light.felt) },
    { appearances: DARK, idiom: 'universal', color: colour(COLOURS.dark.felt) },
  ],
  info: { author: 'xcode', version: 1 },
}));

/** sharp, from wherever npm put it: the root, or nested under the store tooling that brings it. */
async function loadSharp() {
  const require = createRequire(import.meta.url);
  for (const from of [ROOT, join(ROOT, 'node_modules', 'vydanne'), join(ROOT, 'node_modules', 'zdymak')]) {
    try {
      return (await import(pathToFileURL(require.resolve('sharp', { paths: [from] })).href)).default;
    } catch {
      // not under this one; try the next
    }
  }
  throw new Error('sharp is not installed: run `npm install` at the repository root (it comes with vydanne and zdymak)');
}

const sharp = await loadSharp();
const images = [];
for (const theme of ['light', 'dark']) {
  const svg = Buffer.from(markSvg(COLOURS[theme].v));
  for (const s of [1, 2, 3]) {
    const px = IOS_POINTS * s;
    const name = `launch-mark${theme === 'dark' ? '-dark' : ''}@${s}x.png`;
    // Rasterised at the size it is shown, so the serifs stay crisp: 72 dpi is one SVG unit a pixel.
    const png = await sharp(svg, { density: (72 * px) / N }).resize(px, px).png().toBuffer();
    write(`${ASSETS}/LaunchMark.imageset/${name}`, png);
    images.push({ ...(theme === 'dark' ? { appearances: DARK } : {}), idiom: 'universal', filename: name, scale: `${s}x` });
  }
}
write(`${ASSETS}/LaunchMark.imageset/Contents.json`, json({ images, info: { author: 'xcode', version: 1 } }));

console.log(
  `the V reaches ${reach.toFixed(1)} of ${N} from the icon's centre; drawn at ${scale.toFixed(4)} of its icon size,`,
  `its box is ${box.map(fmt).join(' ')} (centre ${fmt((box[0] + box[2]) / 2)}, ${fmt((box[1] + box[3]) / 2)})`,
);
