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
 *   androidApp/src/main/res/drawable/launch_mark_animated.xml the same V, written in (Android 12+)
 *   androidApp/src/main/res/values{,-v31}/launch_animation.xml  which of the two, and how long
 *   androidApp/src/main/res/values{,-night}/launch.xml        the felt and the V, per phone theme
 *   iosApp/iosApp/Assets.xcassets/LaunchMark.imageset         the mark at 250 pt, 1x-3x, light and dark
 *   iosApp/iosApp/Assets.xcassets/LaunchFelt.colorset         the felt, light and dark
 *
 * `Theme.Vinto.Launch` (values/themes.xml) and `UILaunchScreen` (Info.plist) name them.
 *
 * THE ANIMATION (Android 12+; the owner: "animated icons on supported phones"). The V is written
 * in, in one stroke of a pen: down the thick left arm from its serif to the point, and up the thin
 * right arm into its serif, as a broad-nib V is drawn and as a serif capital's thick and thin come
 * from. Two clip paths do it, one per arm, each a front square to its arm that sweeps along it;
 * they meet on a line from the V's point through the notch between its arms. The first
 * accelerates and the second decelerates, with the time split in proportion to the distance each
 * front travels, so the pen does not change speed at the point: one gesture that sets down gently
 * and settles into the right serif. The fronts end clear of the V, so the last frame IS the still.
 * Older Android and the compat library show an animated vector's first frame, which here is an
 * empty felt, so they keep the still (`values-v31` alone picks the animation).
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

// ---- Android 12+: the V written in ------------------------------------------------------------

/** The whole stroke, both arms. Android caps a launch icon's animation at 1000 ms. */
const WRITE_MS = 700;
/** How far past the V each front starts and stops: the first frame is empty, the last is the still. */
const CLEAR = 16;
/** The clip paths' own box reaches this far beyond the canvas, so none of its edges is ever seen. */
const OUTER = 100;
/**
 * How far right of the hand-over line the LEFT clip's own edge sits. Were the two clips' edges on one line, a renderer
 * that anti-aliases clips would let each half-cover the same pixels, and their product would draw a faint seam from
 * the notch to the point before the pen got there. A few pixels apart at any density, they never touch; the sliver
 * between them is the right arm's, written by its front.
 */
const APART = 8;

const sub = (a, b) => [a[0] - b[0], a[1] - b[1]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1];
const unit = (a) => [a[0] / Math.hypot(...a), a[1] / Math.hypot(...a)];

/** The V as polygons, one per subpath, curves flattened. */
const polygons = launch.map((sp) => outlinePoints([sp]));

/** The V's cross-section at height y: its filled spans, left to right, by the non-zero rule both renderers use. */
function crossSection(y) {
  const hits = [];
  for (const poly of polygons) {
    poly.forEach(([x0, y0], i) => {
      const [x1, y1] = poly[(i + 1) % poly.length];
      if ((y0 <= y) !== (y1 <= y)) hits.push({ x: x0 + ((y - y0) / (y1 - y0)) * (x1 - x0), w: y1 > y0 ? 1 : -1 });
    });
  }
  hits.sort((a, b) => a.x - b.x);
  const spans = [];
  let winding = 0;
  for (const { x, w } of hits) {
    const was = winding;
    winding += w;
    if (was === 0) spans.push([x, x]);
    else if (winding === 0) spans.at(-1)[1] = x;
  }
  return spans;
}

// The point of the V, and the notch where its two arms part: found by walking up from the point until the
// cross-section splits in two. The line through both is where the left arm's stroke hands over to the right's.
const [top, bottom] = [box[1], box[3]];
const lowest = outlinePoints(launch).filter(([, y]) => y > bottom - 0.01);
const tip = [lowest.reduce((sum, [x]) => sum + x, 0) / lowest.length, bottom];
let notch;
for (let y = bottom - 0.05; y > top && !notch; y -= 0.05) {
  const spans = crossSection(y);
  if (spans.length > 2) throw new Error('the V splits into more than two arms above its point; this is not a V');
  if (spans.length === 2) notch = [(spans[0][1] + spans[1][0]) / 2, y];
}
if (!notch) throw new Error('the V never parts into two arms');
const up = unit(sub(notch, tip));
const divisionX = (y, shift = 0) => tip[0] + shift + ((y - tip[1]) / up[1]) * up[0];
// Above the notch the hand-over line must run in the gap between the arms. (The left clip's own line, APART to its
// right, may graze the right arm there: the right clip hides that sliver until the right arm's front writes it.)
for (let y = top + 0.05; y < notch[1] - 0.05; y += 0.25) {
  if (crossSection(y).some(([a, b]) => a < divisionX(y) && divisionX(y) < b)) {
    throw new Error(`the line from the point through the notch cuts an arm at y=${fmt(y)}; the hand-over would show`);
  }
}

// Each arm's direction, from the middle of its serif to the point, and the front square to it.
const serifs = crossSection(top + 0.5);
const serifMiddle = (spans) => [(spans[0][0] + spans.at(-1)[1]) / 2, top];
const down = unit(sub(tip, serifMiddle(serifs.filter(([, b]) => b < divisionX(top))))); // the left arm, going down
const rise = unit(sub(serifMiddle(serifs.filter(([a]) => a > divisionX(top))), tip)); // the right arm, going up

// How far each front travels: from just short of its arm to just past it, measured along the arm. The point and the
// notch belong to both arms.
const outline = outlinePoints(launch);
const reachAlong = (points, u) => {
  const s = points.map((p) => dot(p, u));
  return [Math.min(...s) - CLEAR, Math.max(...s) + CLEAR];
};
const leftTravel = reachAlong([...outline.filter(([x, y]) => x < divisionX(y, APART)), tip, notch], down);
const rightTravel = reachAlong([...outline.filter(([x, y]) => x > divisionX(y)), tip, notch], rise);

// The clips. Each is everything except the part of its own arm the pen has not reached yet, as a hexagon inside a box
// past the canvas: the left one is the right side of the hand-over line plus the left side above its front; the right
// one is the left side plus the right side below its front (the left one's line sits APART to the right, for the
// reason given there). Their intersection is what has been written. The
// vertices keep their order from start to end, which a path morph requires, and every front point moves linearly
// with the front, so the morph between the two ends is exact at every frame.
const [lo, hi] = [-OUTER, N + OUTER];
const onDivision = (s, u, shift = 0) => {
  const from = [tip[0] + shift, tip[1]];
  const t = (s - dot(from, u)) / dot(up, u);
  return [from[0] + t * up[0], from[1] + t * up[1]];
};
const onEdge = (s, u, x) => [x, (s - x * u[0]) / u[1]];
const leftClip = (s) =>
  [[lo, lo], [hi, lo], [hi, hi], [divisionX(hi, APART), hi], onDivision(s, down, APART), onEdge(s, down, lo)];
const rightClip = (s) => [[lo, lo], [divisionX(lo), lo], onDivision(s, rise), onEdge(s, rise, hi), [hi, hi], [lo, hi]];
for (const clip of [...leftTravel.map(leftClip), ...rightTravel.map(rightClip)]) {
  if (clip.some(([x, y]) => !(x >= lo && x <= hi && y >= lo && y <= hi))) {
    throw new Error('a front leaves the clip box; widen OUTER');
  }
}
const polygon = (points) => `M ${points.map(pair).join(' L ')} Z`;

// The time is split in proportion to the distance, so the left arm's accelerating front reaches the point at the speed
// the right arm's decelerating front leaves it: one stroke, no hitch at the point.
const leftLength = leftTravel[1] - leftTravel[0];
const rightLength = rightTravel[1] - rightTravel[0];
const LEFT_MS = Math.round((WRITE_MS * leftLength) / (leftLength + rightLength));
const RIGHT_MS = WRITE_MS - LEFT_MS;

const target = (name, [from, to], offset, duration, interpolator) => [
  `    <target android:name="${name}">`,
  '        <aapt:attr name="android:animation">',
  '            <objectAnimator android:propertyName="pathData" android:valueType="pathType"',
  `                android:valueFrom="${polygon(from)}"`,
  `                android:valueTo="${polygon(to)}"`,
  `                android:startOffset="${offset}" android:duration="${duration}"`,
  `                android:interpolator="@android:interpolator/${interpolator}" />`,
  '        </aapt:attr>',
  '    </target>',
];
const leftEnds = leftTravel.map(leftClip);
const rightEnds = rightTravel.map(rightClip);

write('androidApp/src/main/res/drawable/launch_mark_animated.xml', [
  '<?xml version="1.0" encoding="utf-8"?>',
  '<!-- Vinto\'s launch mark on Android 12+: the V written in with one stroke of a pen, down the left arm and up the',
  '     right. GENERATED by tools/make-launch-screen.mjs from brand/vinto-icon.svg: do not edit. Each clip path is one',
  '     arm\'s front; both end clear of the V, so the last frame is drawable/launch_mark.xml exactly. Older Android',
  '     shows that still instead (values/launch_animation.xml). -->',
  '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"',
  '    xmlns:aapt="http://schemas.android.com/aapt">',
  '    <aapt:attr name="android:drawable">',
  '        <vector android:width="288dp" android:height="288dp"',
  `            android:viewportWidth="${N}" android:viewportHeight="${N}">`,
  '            <group>',
  `                <clip-path android:name="left_arm" android:pathData="${polygon(leftEnds[0])}" />`,
  '                <group>',
  `                    <clip-path android:name="right_arm" android:pathData="${polygon(rightEnds[0])}" />`,
  `                    <path android:fillColor="@color/launch_v" android:pathData="${d}" />`,
  '                </group>',
  '            </group>',
  '        </vector>',
  '    </aapt:attr>',
  ...target('left_arm', leftEnds, 0, LEFT_MS, 'accelerate_quad'),
  ...target('right_arm', rightEnds, LEFT_MS, RIGHT_MS, 'decelerate_quad'),
  '</animated-vector>',
  '',
].join('\n'));

write('androidApp/src/main/res/values/launch_animation.xml', [
  '<?xml version="1.0" encoding="utf-8"?>',
  '<!-- GENERATED by tools/make-launch-screen.mjs: do not edit. Which launch icon, and how long it animates. -->',
  '<resources>',
  '    <!-- The still. Before Android 12 there is no animated launch icon, and the compat library would show an',
  '         animated vector\'s first frame, which is an empty felt. values-v31/ picks the animated one. -->',
  '    <item name="launch_icon" type="drawable">@drawable/launch_mark</item>',
  `    <!-- The length of drawable/launch_mark_animated.xml: the left arm ${LEFT_MS} ms, then the right ${RIGHT_MS} ms.`,
  '         MainActivity holds the launch screen until it ends. -->',
  `    <integer name="launch_animation_ms">${WRITE_MS}</integer>`,
  '</resources>',
  '',
].join('\n'));

write('androidApp/src/main/res/values-v31/launch_animation.xml', [
  '<?xml version="1.0" encoding="utf-8"?>',
  '<!-- GENERATED by tools/make-launch-screen.mjs: do not edit. -->',
  '<resources>',
  '    <!-- Android 12+ writes the V in (the owner: "animated icons on supported phones"). With the system\'s',
  '         animations off it stands whole from the first frame. -->',
  '    <item name="launch_icon" type="drawable">@drawable/launch_mark_animated</item>',
  '</resources>',
  '',
].join('\n'));

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
console.log(
  `the pen: point ${pair(tip)}, notch ${pair(notch)}; left arm ${fmt(leftLength)} in ${LEFT_MS} ms,`,
  `right arm ${fmt(rightLength)} in ${RIGHT_MS} ms`,
);
