#!/usr/bin/env node
/**
 * Generate the meaning-based card faces as SVG.
 *
 * Design (docs/design/CARD-IMAGERY.md). Ownership is a colour that needs no legend: felt
 * green is your card, blue is an opponent's, cream the deck's, and every one of them is
 * outlined in ink so it holds on any ground.
 *
 * The ground says what the card DOES, in four families rather than nine tints — green
 * reaches your own cards (7, 8), blue reaches theirs (9, 10), orange moves one between two
 * players (J, Q), yellow is the crown and the deck the Ace throws from (K, A). Two ranks in
 * one family are separated by LIGHTNESS, so the pair survives a dichromat; the plain
 * numbers keep a white ground, because doing nothing is their whole meaning.
 *
 * The emblems: 7/8 stand a card up out of your row under a wide eye, 9/10 drop one out of
 * theirs under a round lens — opposite silhouettes, not one scene twice. J trades the pair
 * under a slashed eye, Q under two open eyes with dashed arrows, K rays a crown at a card
 * in each corner, A throws a deck card at an opponent, the Joker is its cap over its name.
 *
 * Every face carries standard corner indices (bottom-right rotated, 6 and 9 underlined),
 * drawn bold and at large-print size because on the felt they are often the only part of a
 * card big enough to read.
 *
 * Three gates run before anything is written, and each one exists because something got
 * past the two before it: `checkContrast` (WCAG on text and grounds), `checkSeparation`
 * (two cards that do the same thing are still two cards) and `checkEmblemInk` (every
 * shape is visible on the ground it is painted on).
 *
 * Usage:  node tools/make-card-faces.mjs
 * Output: tools/card-faces/*.svg and tools/card-faces/preview.html
 */

import { mkdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));

// ---------------------------------------------------------------- number formatting
//
// This file was ported from Python, and the port is only faithful if the numbers print
// the way Python printed them. Two differences bite, and both are silent:
//
//   * `f"{x:.0f}"` rounds half-to-EVEN on the exact binary value of the double.
//     JavaScript's `toFixed` rounds half-away-from-zero, and on top of that is not
//     required to look at the exact value. So `fmt` below does the rounding itself in
//     BigInt arithmetic over the double's exact mantissa/exponent.
//   * Python's `str(float)` keeps the point: `600.0`, not `600`. `pf` restores it.
//     `bare` is for the values that are Python *ints*, or floats that are never integral.
//
// Nothing here is decorative: change either function and the committed SVGs move.

/** Python's `f"{x:.<nd>f}"` — correct rounding of the exact double, ties to even. */
function fmt(x, nd) {
  const neg = x < 0 || Object.is(x, -0);
  const v = Math.abs(x);
  const buf = new DataView(new ArrayBuffer(8));
  buf.setFloat64(0, v);
  const hi = buf.getUint32(0);
  const lo = buf.getUint32(4);
  const expBits = (hi >>> 20) & 0x7ff;
  let mant = (BigInt(hi & 0xfffff) << 32n) | BigInt(lo);
  let e2;
  if (expBits === 0) {
    e2 = -1074;
  } else {
    mant |= 1n << 52n;
    e2 = expBits - 1075;
  }
  // The value is exactly mant * 2^e2. Scale by 10^nd and divide, keeping the remainder
  // so the tie can be broken on the true value rather than on a decimal approximation.
  let num = mant * 10n ** BigInt(nd);
  let den = 1n;
  if (e2 >= 0) num <<= BigInt(e2);
  else den = 1n << BigInt(-e2);
  let q = num / den;
  const twice = (num % den) * 2n;
  if (twice > den || (twice === den && (q & 1n) === 1n)) q += 1n;
  let s = q.toString();
  if (nd > 0) {
    s = s.padStart(nd + 1, '0');
    s = `${s.slice(0, s.length - nd)}.${s.slice(s.length - nd)}`;
  }
  return (neg ? '-' : '') + s;
}

/** Python's `str(someFloat)` — shortest round-tripping repr, with `.0` on integrals. */
function pf(x) {
  return Number.isInteger(x) ? x.toFixed(1) : String(x);
}

/** Python's `str()` for a value that is an int, or a float that is never integral. */
function bare(x) {
  return String(x);
}

/** Python's `round()` — half to even. */
function pyRound(x) {
  const f = Math.floor(x);
  const d = x - f;
  if (d > 0.5) return f + 1;
  if (d < 0.5) return f;
  return f % 2 === 0 ? f : f + 1;
}

const W = 825;
const H = 1125;
const CX = W / 2;
const CX_INT = Math.trunc(CX);

const INK = '#14181B';
const FELT = '#1B5E43';
const FELT_DARK = '#0E3428';
const GOLD = '#C9A227';
const GOLD_DARK = '#8A6D1B';
const PAPER = '#F7F5EF';
const ORANGE = '#E8791E';
const WHITE = '#FFFFFF';
const PALE = '#A8C2B5';
const BLUE = '#5B9BD5'; // opponents' cards — distinct from your green at any size
const JOKER_INK = '#A34A08'; // the Joker's name: orange darkened to hold on its pink ground
const PENALTY = '#9E2B25'; // the Ace's throw — the one red left in the deck, and it means harm
// The back's four engraved marks. Darker than the deck colours they stand for, because
// they are the one place a colour is drawn with no outline to fall back on, and gold,
// blue and orange all sat under 3:1 on cream — 2.2, 2.7 and 2.7.
const ENGRAVED = ['#8A6D1B', '#2A6BB0', '#1B5E43', '#C25E10'];
const BLUE_EDGE = '#DCE9F5';

// One accent per rank family, dark enough to hold as a corner index on cream.
const ACCENT = {
  2: '#17766B', // teal
  3: '#2F5E8C', // blue
  4: '#4F5AA8', // indigo
  5: '#B03A57', // raspberry
  6: '#1B5E43', // the brand green
  7: '#1B7A3E', // green — the peeks that reach your own cards
  8: '#145C2E',
  9: '#1B477A', // blue — the peeks that reach somebody else's
  10: '#171E75',
  j: '#7A441B', // orange — a card crossing between two players
  q: '#663006',
  k: '#7A641B', // yellow — the crown, and the deck the Ace throws from
  a: '#664916',
};
// Python dicts iterate in insertion order and the gates below report in that order; a JS
// object puts integer-like keys first, so the order is pinned here rather than inferred.
const ACCENT_ORDER = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'j', 'q', 'k', 'a'];

// Tinted grounds for the action cards. Four families, and which family a rank belongs to
// is decided by WHAT THE CARD DOES rather than by picking nine pleasant tints:
//
//     green   the action reaches one of your own cards      7, 8
//     blue    it reaches one of somebody else's             9, 10
//     orange  a card crosses between two players            J, Q
//     yellow  the crown, and the deck the Ace throws from   K, A
//
// which is the same legend the emblems already draw with — your cards are felt green on
// every face, theirs are blue — so the ground now agrees with the picture on it instead of
// being a tenth colour with nothing to say. The numbers keep the plain white: they do
// nothing, which is their whole meaning.
//
// Within a family the two ranks split by LIGHTNESS, not by hue. They used to be
// neighbouring tints — "a sibling, not a twin" — and measured, 9 and 10 were dE 7.7 apart
// and J and Q were 6.2, which is not a sibling, it is the same colour twice; worse, what
// little separated them was hue alone, so the player who most needed the cue was the one
// who could not use it. `checkSeparation` holds all three pairs at arm's length, with a
// simulated deuteranope and protanope looking at them.
const BG = {
  7: '#CCFCDE', // green: this action reaches one of YOUR cards
  8: '#70E099',
  9: '#D7E8FC', // blue: this action reaches one of THEIRS
  10: '#B6BAFC', // periwinkle, shifted off the opponents' own blue
  j: '#F7E2D2', // orange: a card crosses between two players
  q: '#F5A98A', // coral, not tan: the darker of the pair used to be a brown, and a
  //               brown ground reads as a stain on a card table (product owner)
  k: '#FCEDB8', // yellow: the crown, and the deck the Ace throws from
  a: '#F0BE69',
  joker: '#F6CFE1', // pink: the wild card belongs to no family, and grey — the old
  //                   answer to "no family" — read as a disabled card (product owner)
};
const BG_ORDER = ['7', '8', '9', '10', 'j', 'q', 'k', 'a', 'joker'];

const OUT = join(HERE, 'card-faces');

// ---------------------------------------------------------------- primitives

function group(rot, cx, cy, body) {
  return `<g transform="rotate(${bare(rot)} ${bare(cx)} ${bare(cy)})">${body}</g>`;
}

/** A card-shaped pip: the number cards count in the game's own object. */
function pipCard(cx, cy, { w = 100, h = 138, fill = FELT } = {}) {
  return (
    `<rect x="${fmt(cx - w / 2, 0)}" y="${fmt(cy - h / 2, 0)}" width="${bare(w)}" height="${bare(h)}" rx="12" ` +
    `fill="${fill}" stroke="${GOLD}" stroke-width="5"/>`
  );
}

/**
 * A game card: green yours, blue theirs, cream the deck's, and the colour of the
 * fill is the entire ownership legend.
 *
 * The outline is INK and the rank's own colour is an inner line inside it. It used to
 * be the other way round — a gold or pale-blue edge and no dark outline — which put the
 * whole burden of being visible on the fill, and measured, the opponents' blue managed
 * 3:1 against *not one* of the nine grounds it is drawn on. A dark outline costs
 * nothing, holds on any ground a card is ever given, and is what makes these read as
 * cards rather than as smudges once they are 12 px wide.
 */
function cardShape(cx, cy, w, h, { rot = 0.0, fill = FELT, stroke = GOLD, sw = 9 } = {}) {
  const body =
    `<rect x="${fmt(cx - w / 2, 0)}" y="${fmt(cy - h / 2, 0)}" width="${fmt(w, 0)}" height="${fmt(h, 0)}" ` +
    `rx="${fmt(w * 0.09, 0)}" fill="${fill}" stroke="${INK}" stroke-width="${bare(sw)}"/>` +
    `<rect x="${fmt(cx - w / 2 + w * 0.08, 0)}" y="${fmt(cy - h / 2 + w * 0.08, 0)}" ` +
    `width="${fmt(w * 0.84, 0)}" height="${fmt(h - w * 0.16, 0)}" rx="${fmt(w * 0.06, 0)}" ` +
    `fill="none" stroke="${stroke}" stroke-width="${fmt(Math.max(4, sw * 0.5), 0)}"/>`;
  return rot ? group(rot, cx, cy, body) : body;
}

/** A small opponent, head and shoulders — the 'someone else' marker. */
function bust(cx, cy, s = 1.0) {
  return (
    `<circle cx="${bare(cx)}" cy="${fmt(cy - 66 * s, 0)}" r="${fmt(34 * s, 0)}" fill="${INK}" ` +
    `stroke="${PALE}" stroke-width="${fmt(5 * s, 0)}"/>` +
    `<path d="M ${fmt(cx - 72 * s, 0)},${fmt(cy + 10 * s, 0)} Q ${fmt(cx - 64 * s, 0)},${fmt(cy - 46 * s, 0)} ` +
    `${bare(cx)},${fmt(cy - 52 * s, 0)} Q ${fmt(cx + 64 * s, 0)},${fmt(cy - 46 * s, 0)} ` +
    `${fmt(cx + 72 * s, 0)},${fmt(cy + 10 * s, 0)} Z" ` +
    `fill="${INK}" stroke="${PALE}" stroke-width="${fmt(5 * s, 0)}"/>`
  );
}

/** gaze shifts iris and pupil vertically: positive looks down, negative up. */
function bigEye(cx, cy, s = 1.0, gaze = 0.0, iris = GOLD) {
  const w = 170 * s;
  const h = 108 * s;
  const g = gaze * s;
  return (
    `<path d="M ${pf(cx - w)},${bare(cy)} Q ${bare(cx)},${pf(cy - h)} ${pf(cx + w)},${bare(cy)} Q ${bare(cx)},${pf(cy + h)} ${pf(cx - w)},${bare(cy)} Z" ` +
    `fill="${WHITE}" stroke="${INK}" stroke-width="${fmt(16 * s, 0)}" stroke-linejoin="round"/>` +
    `<circle cx="${bare(cx)}" cy="${fmt(cy + g, 0)}" r="${fmt(56 * s, 0)}" fill="${iris}"/>` +
    `<circle cx="${bare(cx)}" cy="${fmt(cy + g, 0)}" r="${fmt(27 * s, 0)}" fill="${INK}"/>` +
    `<circle cx="${fmt(cx + 16 * s, 0)}" cy="${fmt(cy + g - 18 * s, 0)}" r="${fmt(10 * s, 0)}" fill="${WHITE}"/>`
  );
}

/**
 * The Queen's eye: the same eye, with lashes swept out from its outer corner.
 *
 * She is the only face with a person in it rather than a tool or a diagram, and the
 * Jack beside her is the same two cards under the same eye — so the pair needs telling
 * apart by more than an open lid versus a slashed one. `side` is which way the lashes
 * sweep, so the two eyes lean away from each other rather than both leaning right.
 */
function lashedEye(cx, cy, s = 1.0, iris = GOLD, side = 1) {
  const w = 170 * s;
  const h = 108 * s;
  let lashes = '';
  for (const [t, reach] of [
    [0.58, 0.86],
    [0.71, 1.0],
    [0.84, 0.82],
  ]) {
    const u = side > 0 ? t : 1 - t;
    // A point on the upper lid, which is one quadratic: x is linear in u and the
    // lid's height is 2h*u*(1-u), so the lashes sit on the curve rather than near it.
    const x = cx + w * (2 * u - 1);
    const y = cy - 2 * h * u * (1 - u);
    const dx = side * 0.6;
    const dy = -1.0;
    const n = Math.hypot(dx, dy);
    const length = 52 * s * reach;
    lashes +=
      `<line x1="${fmt(x, 0)}" y1="${fmt(y, 0)}" x2="${fmt(x + (length * dx) / n, 0)}" ` +
      `y2="${fmt(y + (length * dy) / n, 0)}" stroke="${INK}" ` +
      `stroke-width="${fmt(12 * s, 0)}" stroke-linecap="round"/>`;
  }
  return bigEye(cx, cy, s, 0.0, iris) + lashes;
}

function arrowhead(x, y, ang, size = 40, color = GOLD) {
  const p1 = [x - size * 1.4 * Math.cos(ang - 0.45), y - size * 1.4 * Math.sin(ang - 0.45)];
  const p2 = [x - size * 1.4 * Math.cos(ang + 0.45), y - size * 1.4 * Math.sin(ang + 0.45)];
  return `<polygon points="${fmt(x, 0)},${fmt(y, 0)} ${fmt(p1[0], 0)},${fmt(p1[1], 0)} ${fmt(p2[0], 0)},${fmt(p2[1], 0)}" fill="${color}"/>`;
}

const radians = (d) => (d * Math.PI) / 180;
const degrees = (r) => (r * 180) / Math.PI;

/**
 * A circular swap arrow from angle a1 to a2 (degrees, screen coords,
 * clockwise). Dashed arcs are emitted as sub-arc segments — vector drawables
 * have no stroke-dasharray.
 */
function arcArrow(cx, cy, r, a1, a2, { dashed = false, sw = 18, color = GOLD } = {}) {
  const pt = (a) => [cx + r * Math.cos(radians(a)), cy + r * Math.sin(radians(a))];

  const tangent = Math.atan2(Math.cos(radians(a2)), -Math.sin(radians(a2)));
  let body;
  if (!dashed) {
    const [x1, y1] = pt(a1);
    const [x2, y2] = pt(a2);
    body =
      `<path d="M ${fmt(x1, 0)},${fmt(y1, 0)} A ${bare(r)} ${bare(r)} 0 0 1 ${fmt(x2, 0)},${fmt(y2, 0)}" fill="none" ` +
      `stroke="${color}" stroke-width="${bare(sw)}" stroke-linecap="round"/>`;
  } else {
    const dashDeg = degrees(34 / r);
    const gapDeg = degrees(26 / r);
    const segs = [];
    let a = a1;
    while (a < a2) {
      const b = Math.min(a + dashDeg, a2);
      const [xa, ya] = pt(a);
      const [xb, yb] = pt(b);
      segs.push(
        `<path d="M ${fmt(xa, 0)},${fmt(ya, 0)} A ${bare(r)} ${bare(r)} 0 0 1 ${fmt(xb, 0)},${fmt(yb, 0)}" fill="none" ` +
          `stroke="${color}" stroke-width="${bare(sw)}" stroke-linecap="round"/>`,
      );
      a = b + gapDeg;
    }
    body = segs.join('');
  }
  const [x2, y2] = pt(a2);
  return body + arrowhead(x2, y2, tangent, 40, color);
}

/**
 * A dashed sight-ray with an arrowhead — dashes drawn as real segments,
 * because vector drawables have no stroke-dasharray.
 */
function ray(x1, y1, x2, y2, color = GOLD) {
  const ang = Math.atan2(y2 - y1, x2 - x1);
  const length = Math.hypot(x2 - x1, y2 - y1);
  const ca = Math.cos(ang);
  const sa = Math.sin(ang);
  const segs = [];
  let t = 0.0;
  while (t < length) {
    const end = Math.min(t + 16, length);
    segs.push(
      `<line x1="${fmt(x1 + t * ca, 0)}" y1="${fmt(y1 + t * sa, 0)}" ` +
        `x2="${fmt(x1 + end * ca, 0)}" y2="${fmt(y1 + end * sa, 0)}" stroke="${color}" ` +
        `stroke-width="9" stroke-linecap="round"/>`,
    );
    t += 30;
  }
  return segs.join('') + arrowhead(x2, y2, ang, 24, color);
}

/** Hollow outline crown — mighty, weighs nothing. */
function crown(cx, cy, s = 1.0, color = GOLD) {
  const w = 130 * s;
  const h = 96 * s;
  const d =
    `M ${pf(cx - w)},${pf(cy + h * 0.5)} L ${pf(cx - w)},${pf(cy - h * 0.28)} ` +
    `L ${pf(cx - w * 0.5)},${pf(cy + h * 0.06)} L ${bare(cx)},${pf(cy - h * 0.62)} ` +
    `L ${pf(cx + w * 0.5)},${pf(cy + h * 0.06)} L ${pf(cx + w)},${pf(cy - h * 0.28)} ` +
    `L ${pf(cx + w)},${pf(cy + h * 0.5)} Z`;
  const dots = [
    [cx - w, cy - h * 0.42],
    [cx, cy - h * 0.78],
    [cx + w, cy - h * 0.42],
  ]
    .map(
      ([x, y]) =>
        `<circle cx="${fmt(x, 0)}" cy="${fmt(y, 0)}" r="${fmt(11 * s, 0)}" fill="none" ` +
        `stroke="${color}" stroke-width="${fmt(7 * s, 0)}"/>`,
    )
    .join('');
  return (
    `<path d="${d}" fill="none" stroke="${color}" stroke-width="${fmt(13 * s, 0)}" ` +
    `stroke-linejoin="round"/>` +
    `<line x1="${pf(cx - w)}" y1="${pf(cy + h * 0.74)}" x2="${pf(cx + w)}" y2="${pf(cy + h * 0.74)}" ` +
    `stroke="${color}" stroke-width="${fmt(13 * s, 0)}" stroke-linecap="round"/>` +
    dots
  );
}

/**
 * Three floppy horns over a band, a bell on each tip — and motley, as a
 * jester's cap traditionally is: each horn its own colour.
 */
function jesterCap(cx, cy, s = 1.0, colors = ['#17766B', ORANGE, '#B03A57']) {
  const base = cy + 40 * s;

  const petal = (x1, ctrl, tip, back, color) =>
    `<path d="M ${pf(cx + x1 * s)},${pf(base)} Q ${pf(cx + ctrl[0] * s)},${pf(cy + ctrl[1] * s)} ` +
    `${pf(cx + tip[0] * s)},${pf(cy + tip[1] * s)} Q ${pf(cx + back[0] * s)},${pf(cy + back[1] * s)} ` +
    `${pf(cx + (x1 / 3) * s)},${pf(base - 6 * s)} Z" fill="${color}" stroke="${INK}" ` +
    `stroke-width="${fmt(5 * s, 0)}" stroke-linejoin="round"/>`;

  const left = petal(-64, [-132, -64], [-150, -2], [-78, -24], colors[0]);
  const right = petal(64, [132, -64], [150, -2], [78, -24], colors[2]);
  const middle =
    `<path d="M ${pf(cx - 30 * s)},${pf(base)} Q ${pf(cx - 12 * s)},${pf(cy - 102 * s)} ${bare(cx)},${pf(cy - 100 * s)} ` +
    `Q ${pf(cx + 12 * s)},${pf(cy - 102 * s)} ${pf(cx + 30 * s)},${pf(base)} Z" fill="${colors[1]}" ` +
    `stroke="${INK}" stroke-width="${fmt(5 * s, 0)}" stroke-linejoin="round"/>`;
  const band =
    `<rect x="${pf(cx - 70 * s)}" y="${pf(base - 7 * s)}" width="${fmt(140 * s, 0)}" height="${fmt(24 * s, 0)}" ` +
    `rx="${fmt(11 * s, 0)}" fill="${GOLD}" stroke="${INK}" stroke-width="${fmt(4 * s, 0)}"/>`;
  const bells = [
    [-156, 10],
    [0, -108],
    [156, 10],
  ]
    .map(
      ([dx, dy]) =>
        `<circle cx="${pf(cx + dx * s)}" cy="${pf(cy + dy * s)}" r="${fmt(11 * s, 0)}" fill="${GOLD}" ` +
        `stroke="${INK}" stroke-width="${fmt(3 * s, 0)}"/>`,
    )
    .join('');
  return left + right + middle + band + bells;
}

/** A knotted band across both cards: the swap is blind. */
function blindfold(cx, cy, hw, rot) {
  const body =
    `<polygon points="${bare(cx + hw - 10)},${bare(cy - 26)} ${bare(cx + hw + 128)},${bare(cy - 96)} ` +
    `${bare(cx + hw + 74)},${bare(cy - 2)}" ` +
    `fill="${GOLD}" stroke="${INK}" stroke-width="7" stroke-linejoin="round"/>` +
    `<polygon points="${bare(cx + hw - 10)},${bare(cy + 22)} ${bare(cx + hw + 136)},${bare(cy + 62)} ` +
    `${bare(cx + hw + 58)},${bare(cy + 106)}" ` +
    `fill="${GOLD}" stroke="${INK}" stroke-width="7" stroke-linejoin="round"/>` +
    `<rect x="${bare(cx - hw)}" y="${bare(cy - 44)}" width="${bare(hw * 2)}" height="88" rx="40" ` +
    `fill="${GOLD}" stroke="${INK}" stroke-width="7"/>` +
    `<path d="M ${bare(cx - hw + 40)},${bare(cy - 12)} Q ${bare(cx)},${bare(cy - 30)} ${bare(cx + hw - 40)},${bare(cy - 12)}" ` +
    `fill="none" stroke="${GOLD_DARK}" stroke-width="6" stroke-linecap="round"/>` +
    `<circle cx="${bare(cx + hw + 8)}" cy="${bare(cy)}" r="20" fill="${GOLD_DARK}" ` +
    `stroke="${INK}" stroke-width="5"/>`;
  return group(rot, cx, cy, body);
}

// ---------------------------------------------------------------- the frame

// The corner index is drawn bold and at large-print size, and that is an accessibility
// decision rather than a style one. On the felt a card is 32-56 dp wide, and below the tap
// floor the emblem is a smudge while the index still has to be a number — CARD-IMAGERY.md
// says as much: "only the corner index carries the card". At the old 0.95 and stroke 17 the
// numeral was 11% of the card's width, about 6 dp on the table, in a stroke a third of a
// device pixel wide on the side seats. At 1.55 it is 19% of the width in a bold weight,
// which is what a large-print ("jumbo index") deck uses — the affordance low-vision players
// already buy a whole deck to get.
const INDEX_SCALE = 1.55;
const INDEX_X = 66;
const INDEX_Y = 62;
// The 10 is two glyphs, so it is set smaller to keep the pair inside the same corner and
// dropped so both indices share an optical centre. The 1 is a stem in a full-width box, so
// the pair is kerned tighter than two boxes would otherwise sit.
const TEN_SCALE = INDEX_SCALE * 0.78;
const TEN_KERN = 86;
const TEN_DROP = 26;
// Bold. Measured against the glyph skeletons rather than chosen: at 26 the 8's counters and
// the 4's triangle start to fill in, which costs more legibility at a thumbnail than the
// extra weight buys.
const GLYPH_STROKE = 23;

// Monoline glyph skeletons on a 100-wide, 124-tall box, drawn as strokes.
// Vector drawables cannot render text, so the indices are geometry like
// everything else — which also frees them from platform fonts.
const GLYPHS = {
  2: ['M14 34 Q14 8 50 8 Q86 8 86 36 Q86 60 50 88 L14 116 L88 116'],
  3: ['M16 28 Q24 8 50 8 Q84 8 84 32 Q84 56 50 60 Q86 64 86 92 Q86 116 50 116 Q22 116 14 98'],
  4: ['M64 116 L64 8 L10 82 L92 82'],
  5: ['M82 8 L24 8 L18 54 Q34 46 52 46 Q86 50 86 82 Q86 116 50 116 Q22 116 14 98'],
  6: ['M76 18 Q64 6 48 8 Q14 14 14 66 Q14 116 50 116 Q84 116 84 84 Q84 56 50 56 Q22 56 15 76'],
  7: ['M12 8 L88 8 L44 116'],
  8: [
    'M50 58 Q17 53 17 32 Q17 8 50 8 Q83 8 83 32 Q83 53 50 58 ' +
      'Q13 64 13 90 Q13 116 50 116 Q87 116 87 90 Q87 64 50 58',
  ],
  9: ['M24 106 Q36 118 52 116 Q86 110 86 58 Q86 8 50 8 Q16 8 16 40 Q16 68 50 68 Q78 68 85 48'],
  1: ['M28 24 L50 8 L50 116'],
  0: ['M50 8 Q15 8 15 62 Q15 116 50 116 Q85 116 85 62 Q85 8 50 8'],
  O: ['M50 8 Q15 8 15 62 Q15 116 50 116 Q85 116 85 62 Q85 8 50 8'],
  E: ['M24 8 L24 116', 'M24 8 L86 8', 'M24 62 L76 62', 'M24 116 L86 116'],
  J: ['M68 8 L68 88 Q68 116 44 116 Q22 116 16 96'],
  Q: ['M50 8 Q15 8 15 62 Q15 116 50 116 Q85 116 85 62 Q85 8 50 8', 'M60 94 L88 124'],
  K: ['M22 8 L22 116', 'M82 8 L24 66', 'M44 50 L86 116'],
  R: ['M24 116 L24 8 L58 8 Q86 8 86 35 Q86 62 58 62 L24 62', 'M54 62 L88 116'],
  A: ['M12 116 L50 8 L88 116', 'M27 82 L73 82'],
};

function glyph(ch, x, y, scale, color, weight = GLYPH_STROKE) {
  const body = GLYPHS[ch]
    .map(
      (d) =>
        `<path d="${d}" fill="none" stroke="${color}" stroke-width="${bare(weight)}" ` +
        `stroke-linecap="round" stroke-linejoin="round"/>`,
    )
    .join('');
  return `<g transform="translate(${bare(x)} ${bare(y)}) scale(${bare(scale)})">${body}</g>`;
}

/**
 * A word set in the index letterforms. The deck draws no text anywhere else — a
 * vector drawable cannot render a font, and every other label a card might want is
 * something the emblem says better.
 */
function word(text, cx, y, scale, color, kern = 86) {
  const span = (text.length - 1) * kern * scale;
  const x0 = cx - span / 2 - 50 * scale;
  return [...text].map((c, i) => glyph(c, x0 + i * kern * scale, y, scale, color)).join('');
}

function indexGlyph(label, underline, color = INK) {
  let t;
  if (label === '10') {
    t =
      glyph('1', INDEX_X, INDEX_Y + TEN_DROP, TEN_SCALE, color) +
      glyph('0', INDEX_X + TEN_KERN * TEN_SCALE, INDEX_Y + TEN_DROP, TEN_SCALE, color);
  } else {
    t = glyph(label, INDEX_X, INDEX_Y, INDEX_SCALE, color);
  }
  if (underline) {
    // Tucked under the digit rather than floating below it. It used to sit 32 units
    // clear of the glyph and 10 off the emblem underneath, which is the wrong way
    // round for a mark whose whole job is to belong to the 6 or the 9 above it.
    const k = INDEX_SCALE;
    t +=
      `<rect x="${fmt(INDEX_X + 8 * k, 0)}" y="${fmt(INDEX_Y + 138 * k, 0)}" ` +
      `width="${fmt(85 * k, 0)}" height="${fmt(13 * k, 0)}" rx="${fmt(6 * k, 0)}" fill="${color}"/>`;
  }
  return t;
}

/**
 * The Joker's corner mark is its cap rather than a letter, and it grows with the
 * indices — but not as far, because a silhouette carries at a size a stroke does not
 * and the emblem below it is already the widest on the deck.
 */
function jokerIndex() {
  return jesterCap(168, 178, 0.72);
}

/**
 * One card: a ground, two indices and an emblem, and as little else as possible.
 *
 * `edge` draws the one ink rule, and only the plain numbers ask for it — their ground is
 * white, so without it the card has no edge at all. A tinted card needs none: the ground
 * is the edge. There used to be two rules on every card, an ink one and a thinner accent
 * one inside it, which on a white 3 read as a double border and on a coloured card was a
 * frame drawn around a frame.
 */
function frame(
  label,
  emblem,
  { underline = false, joker = false, accent = null, bg = PAPER, edge = false } = {},
) {
  const corner = joker ? jokerIndex() : indexGlyph(label, underline, accent || INK);
  const mirrored = `<g transform="rotate(180 ${pf(W / 2)} ${pf(H / 2)})">${corner}</g>`;
  const rule = edge
    ? `<rect x="26" y="26" width="${bare(W - 52)}" height="${bare(H - 52)}" rx="30" ` +
      `fill="none" stroke="${INK}" stroke-width="6"/>`
    : '';
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${bare(W)} ${bare(H)}">` +
    `<rect width="${bare(W)}" height="${bare(H)}" rx="44" fill="${bg}"/>` +
    `${rule}${corner}${mirrored}${emblem}</svg>`
  );
}

// ---------------------------------------------------------------- the faces

const PIP_LAYOUTS = {
  2: [
    [CX, 400],
    [CX, 780],
  ],
  3: [
    [CX, 375],
    [CX, 590],
    [CX, 805],
  ],
  4: [
    [300, 400],
    [525, 400],
    [300, 780],
    [525, 780],
  ],
  5: [
    [300, 400],
    [525, 400],
    [CX, 590],
    [300, 780],
    [525, 780],
  ],
  6: [
    [300, 375],
    [525, 375],
    [300, 590],
    [525, 590],
    [300, 805],
    [525, 805],
  ],
};

/** 2-6: pip cards whose pips are the game's own object, each rank its own colour. */
function faceNumber(n) {
  return PIP_LAYOUTS[n].map(([x, y]) => pipCard(x, y, { fill: ACCENT[String(n)] })).join('');
}

/** The one card the action is about, with a glow of the rank's accent behind it. */
function popped(cx, cy, w, h, fill, stroke, halo) {
  return (
    `<rect x="${fmt(cx - w / 2 - 13, 0)}" y="${fmt(cy - h / 2 - 13, 0)}" width="${bare(w + 26)}" ` +
    `height="${bare(h + 26)}" rx="22" fill="${halo}" opacity="0.45"/>` +
    cardShape(cx, cy, w, h, { fill, stroke, sw: 10 })
  );
}

/**
 * The spying glyph for peeking at THEIR card — a tool, not an eye.
 *
 * The glass is empty rather than tinted. It is drawn over the card it is looking at,
 * and a wash across that card was the difference between reading their card through a
 * lens and reading a smudge. The handle leans down-left for one reason: down-right is
 * where the second index lives.
 */
function lens(cx, cy, r, color, { sw = 16, angle = 128, reach = 165 } = {}) {
  const a = radians(angle);
  return (
    `<circle cx="${bare(cx)}" cy="${bare(cy)}" r="${bare(r)}" fill="none" stroke="${color}" ` +
    `stroke-width="${bare(sw)}"/>` +
    `<line x1="${fmt(cx + r * Math.cos(a), 0)}" y1="${fmt(cy + r * Math.sin(a), 0)}" ` +
    `x2="${fmt(cx + (r + reach) * Math.cos(a), 0)}" ` +
    `y2="${fmt(cy + (r + reach) * Math.sin(a), 0)}" ` +
    `stroke="${color}" stroke-width="${bare(sw + 12)}" stroke-linecap="round"/>`
  );
}

/**
 * One seat's row of face-down cards. Colour is the whole ownership legend — felt
 * green under a gold border is yours, blue is theirs — so a row needs no chevron and
 * no label to say whose hand it is.
 */
function handRow(cy, fill, stroke, { gap = null, n = 3, w = 150, h = 205, step = 172 } = {}) {
  const out = [];
  for (let i = 0; i < n; i += 1) {
    if (i === gap) continue;
    out.push(cardShape(CX + (i - (n - 1) / 2) * step, cy, w, h, { fill, stroke }));
  }
  return out.join('');
}

/**
 * The 7's composition, shared verbatim by the 8: a card stands up out of YOUR row
 * along the bottom, and one large eye looks down at it.
 *
 * Only your row is drawn, and the absence is the point — there is no opponent on this
 * card because the action cannot reach one. It replaced a four-seat table that carried
 * the same fact and charged the emblem everything to carry it: at the 96 px a side seat
 * actually renders at, four busts and twelve cards are a texture, and the eye that says
 * what the card DOES was a fifth of the width and lost inside it.
 */
function peekOwn(accent) {
  return (
    // THE ROW HAS TO CLEAR THE POPPED CARD'S HALO, and at step 162 it did not.
    //
    // `popped` draws a glow 13 units proud of the card on every side, so a 200-wide card at
    // the centre occupies ±113. A row of 140-wide cards stepping by 162 puts its inner edges
    // at ±92 — twenty-one units INSIDE the halo. The three shapes therefore interlocked, and
    // at the ~96 px this renders at in the "?" sheet and on the felt they stopped reading as
    // three cards at all: reported, exactly, as looking like Lego bricks.
    //
    // 205 is measured rather than nudged: ±205 minus half of 140 puts the inner edges at
    // ±135, which clears the halo by 22 — about the gap between the two blue cards on the
    // 9, so the two compositions space their cards alike. The row still spans only 134..691
    // of an 825-wide face, so nothing moves closer to the indices in the corners.
    handRow(734, FELT, GOLD, { gap: 1, w: 140, h: 192, step: 205 }) +
    popped(CX, 616, 200, 274, FELT, GOLD, accent) +
    // Lifted clear of the card it looks at. The eye used to sit on the popped card's
    // halo — a stack rather than a look — where the 9's lens clears its row by a
    // card's margin; the same margin here, measured from the lid to the halo.
    bigEye(CX, 305, 1.12, 16, accent)
  );
}

/**
 * The 9's composition, shared verbatim by the 10, and the 7/8 turned over: THEIR row
 * sits at the top in blue, one of their cards drops out of it, and the magnifier — a
 * tool you point at somebody else, where an eye is simply yours — closes over it.
 *
 * The two silhouettes are opposites deliberately. Mass low under a wide flat eye is one
 * of mine; mass high over a round lens is one of theirs. Colour says it a third time, so
 * none of the three cues is carrying it alone — which is the whole point of saying it
 * three ways to somebody who cannot use one of them.
 */
function peekThem(accent) {
  return (
    handRow(418, BLUE, BLUE_EDGE, { gap: 1, w: 140, h: 192, step: 162 }) +
    cardShape(CX, 670, 190, 260, { fill: BLUE, stroke: BLUE_EDGE }) +
    lens(CX, 670, 158, accent, { sw: 26 })
  );
}

// The two arcs are one circle, and they are not the same length. They look as though they
// should be: rotate the top one by 180 degrees and you get the bottom one's angles exactly.
// The CARDS underneath are not point-symmetric though — they are mirrored, tilted -13 and
// +13 — so their top outer corners sit 291 units from the emblem's centre where the bottom
// ones sit 265. Against a 295 circle that is the difference between an arc clearing the
// cards by 21 and an arc cutting 4 units into them, which is what the top one was doing.
// Measured both ways, and the top arc is shortened by exactly enough to match the bottom's
// clearance rather than by eye.
const TOP_ARC = [217, 323];
const BOTTOM_ARC = [28, 152];

/** The rotation the Jack and the Queen share: two arcs of one circle around the pair. */
function swapArrows(color, dashed = false) {
  return (
    arcArrow(CX, 600, 295, TOP_ARC[0], TOP_ARC[1], { dashed, sw: 17, color }) +
    arcArrow(CX, 600, 295, BOTTOM_ARC[0], BOTTOM_ARC[1], { dashed, sw: 17, color })
  );
}

/** One big card of yours and one of theirs, crossed — the two-player trade. */
function swapPair() {
  return (
    cardShape(322, 600, 255, 350, { rot: -13, fill: FELT, stroke: GOLD }) +
    cardShape(502, 600, 255, 350, { rot: 13, fill: BLUE, stroke: BLUE_EDGE })
  );
}

/**
 * The blind swap: the pair trades inside a violet circle of arrows, and the
 * slashed eye — the same glyph every password field uses — says nobody looks.
 * A slashed eye upside down is still a slashed eye.
 */
function faceJack() {
  const violet = ACCENT.j;
  const arrows = swapArrows(violet);
  // A pale bar in a dark casing, and that way round for a reason: the slash crosses two
  // dark cards and one white eye, so whichever colour is the core, the casing has to be
  // the one that reads against the other. It used to be a cream casing around an INK
  // core — which put the visible half of the line dark on dark green and dark on blue
  // for all but the 80 units of it that cross the eye.
  const slash = [
    [INK, 38],
    [PAPER, 20],
  ]
    .map(
      ([color, width]) =>
        `<line x1="${bare(CX - 108)}" y1="702" x2="${bare(CX + 108)}" y2="486" stroke="${color}" ` +
        `stroke-width="${bare(width)}" stroke-linecap="round"/>`,
    )
    .join('');
  return swapPair() + bigEye(CX, 594, 0.82, 0.0, violet) + slash + arrows;
}

/**
 * The Queen peeks TWO cards, so she has two eyes — one opened on each card of the
 * pair — and her arrows stay dashed: the trade is hers to decline. The eyes are lashed,
 * which is the one thing on the deck drawn as a person rather than as a diagram, and
 * the only thing separating her emblem from the Jack's beyond an open lid.
 */
function faceQueen() {
  const plum = ACCENT.q;
  const arrows = swapArrows(plum, true);
  const eyes =
    group(-13, 322, 600, lashedEye(322, 600, 0.52, plum, -1)) +
    group(13, 502, 600, lashedEye(502, 600, 0.52, plum, 1));
  return swapPair() + eyes + arrows;
}

/**
 * The crowned oracle: the crown at the center, its rays still reaching a card in
 * every corner — greens and blues placed so a 180° turn keeps the reading true, and
 * the four pulled in off the ends of the card so the bigger indices keep their two —
 * measured, not judged: the top-left card came within 5 units of the K above it.
 */
function faceKing() {
  const gold = ACCENT.k;
  const cards =
    cardShape(238, 382, 118, 162, { rot: -8, fill: FELT, stroke: GOLD }) +
    cardShape(587, 382, 118, 162, { rot: 8, fill: BLUE, stroke: BLUE_EDGE }) +
    cardShape(238, 743, 118, 162, { rot: 8, fill: BLUE, stroke: BLUE_EDGE }) +
    cardShape(587, 743, 118, 162, { rot: -8, fill: FELT, stroke: GOLD });
  const rays =
    ray(330, 470, 288, 428, gold) +
    ray(495, 470, 537, 428, gold) +
    ray(330, 655, 288, 697, gold) +
    ray(495, 655, 537, 697, gold);
  return rays + crown(CX, 530, 1.5, gold) + cards;
}

/**
 * The throw: a card hurled from the deck stack across the table at an
 * opponent. Silhouette: a big red diagonal with a tilted card riding it.
 */
function faceAce() {
  const red = PENALTY;
  const target =
    bust(600, 300, 1.1) + cardShape(600, 402, 96, 132, { fill: BLUE, stroke: BLUE_EDGE });
  const stack = [
    [18, 22],
    [9, 11],
    [0, 0],
  ]
    .map(([dx, dy]) =>
      cardShape(250 + dx, 800 + dy, 180, 246, { fill: '#EAD9A6', stroke: GOLD_DARK, sw: 7 }),
    )
    .join('');
  const ang = Math.atan2(430 - 700, 560 - 320);
  const throw_ =
    `<line x1="320" y1="700" x2="545" y2="447" stroke="${red}" stroke-width="16" ` +
    `stroke-linecap="round"/>` +
    arrowhead(560, 430, ang, 36, red);
  const streaks = [
    [300, 640, 0.7],
    [352, 700, 0.5],
  ]
    .map(
      ([x, y, op]) =>
        `<line x1="${bare(x)}" y1="${bare(y)}" x2="${bare(x + 34)}" y2="${bare(y - 38)}" stroke="${red}" ` +
        `stroke-width="8" stroke-linecap="round" opacity="${bare(op)}"/>`,
    )
    .join('');
  const flying = cardShape(455, 545, 180, 246, { rot: -24, fill: '#EAD9A6', stroke: GOLD_DARK });
  return target + throw_ + streaks + stack + flying;
}

/**
 * The fool's cap over its own name.
 *
 * The cap was drawn at s=2.9 — 930 units wide on an 825-wide card — so both its outer
 * horns and their bells were cropped off the edges, and what was left read as a tent.
 * It fits now. The name is spelled out underneath because the Joker is the one rank
 * with no numeral to enlarge: every other card got a bigger index out of this pass and
 * this one had only a silhouette, which is fine on the felt and no help at all to
 * somebody meeting the deck for the first time in the help sheet.
 */
function faceJoker() {
  return jesterCap(CX, 470, 2.05) + word('JOKER', CX, 700, 0.92, JOKER_INK);
}

/**
 * A light back: the V in a diamond medallion, ringed by eight large engraved marks
 * in the deck's four colours, point-symmetric so the back has no upside down.
 *
 * Light rather than felt green, which is what the brief asked for, because the back is
 * dealt onto green felt and `checkContrast` holds every ground to 3:1 against it — a
 * green back would be a card you cannot see the edge of.
 */
function cardBack() {
  // Four engraved marks, one per deck colour, mirrored to eight. They were six kinds
  // in thirty copies at a fifth of this size, which is not a pattern at the size a card
  // is dealt — it is a speckle, unreadable at 330 px and dirt at 96. Kept rather than
  // dropped: they are the deck's own element-stone motif and the only place the four
  // colours appear together. Bigger, and far fewer, is the whole fix.
  const colors = ENGRAVED;

  const mark = (kind, x, y, c) => {
    if (kind === 'bar') {
      return `<rect x="${bare(x - 62)}" y="${bare(y - 14)}" width="124" height="28" rx="14" fill="${c}"/>`;
    }
    if (kind === 'dots') {
      return [-52, 0, 52]
        .map((dx) => `<circle cx="${bare(x + dx)}" cy="${bare(y)}" r="19" fill="${c}"/>`)
        .join('');
    }
    if (kind === 'chev') {
      return (
        `<polyline points="${bare(x - 57)},${bare(y + 24)} ${bare(x)},${bare(y - 28)} ${bare(x + 57)},${bare(y + 24)}" ` +
        `fill="none" stroke="${c}" stroke-width="21" stroke-linecap="round" ` +
        `stroke-linejoin="round"/>`
      );
    }
    return `<circle cx="${bare(x)}" cy="${bare(y)}" r="30" fill="none" stroke="${c}" stroke-width="17"/>`;
  };

  const kinds = ['bar', 'dots', 'chev', 'ring'];
  const spots = [
    [104, 250],
    [721, 250],
    [CX_INT, 132],
    [104, 562],
  ];
  let deco = spots.map(([x, y], i) => mark(kinds[i], x, y, colors[i])).join('');
  deco += `<g transform="rotate(180 ${pf(W / 2)} ${pf(H / 2)})">${deco}</g>`;

  const diamond = (rw, rh, stroke, sw) =>
    `<path d="M ${pf(CX)},${pf(H / 2 - rh)} L ${pf(CX + rw)},${pf(H / 2)} L ${pf(CX)},${pf(H / 2 + rh)} ` +
    `L ${pf(CX - rw)},${pf(H / 2)} Z" fill="none" stroke="${stroke}" stroke-width="${bare(sw)}"/>`;

  const v =
    `<path d="M ${pf(CX - 68)},${pf(H / 2 - 78)} L ${pf(CX - 28)},${pf(H / 2 - 78)} L ${pf(CX)},${pf(H / 2 + 18)} ` +
    `L ${pf(CX + 28)},${pf(H / 2 - 78)} L ${pf(CX + 68)},${pf(H / 2 - 78)} L ${pf(CX + 24)},${pf(H / 2 + 82)} ` +
    `L ${pf(CX - 24)},${pf(H / 2 + 82)} Z" fill="${ORANGE}" stroke="${INK}" stroke-width="7" ` +
    `stroke-linejoin="round"/>`;
  const pips = [
    [0, -246],
    [0, 246],
    [-212, 0],
    [212, 0],
  ]
    .map(
      ([dx, dy]) =>
        `<path d="M ${pf(CX + dx)},${pf(H / 2 + dy - 12)} L ${pf(CX + dx + 10)},${pf(H / 2 + dy)} ` +
        `L ${pf(CX + dx)},${pf(H / 2 + dy + 12)} L ${pf(CX + dx - 10)},${pf(H / 2 + dy)} Z" fill="${GOLD}"/>`,
    )
    .join('');
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${bare(W)} ${bare(H)}">` +
    `<rect width="${bare(W)}" height="${bare(H)}" rx="44" fill="${PAPER}"/>` +
    `<rect x="20" y="20" width="${bare(W - 40)}" height="${bare(H - 40)}" rx="32" ` +
    `fill="none" stroke="${INK}" stroke-width="6"/>` +
    `<rect x="38" y="38" width="${bare(W - 76)}" height="${bare(H - 76)}" rx="26" ` +
    `fill="none" stroke="${GOLD}" stroke-width="4"/>` +
    deco +
    diamond(240, 300, INK, 6) +
    diamond(200, 252, GOLD, 8) +
    pips +
    v +
    '</svg>'
  );
}

// ---------------------------------------------------------------- output

const FACES = {
  card_2: frame('2', faceNumber(2), { accent: ACCENT['2'], bg: WHITE, edge: true }),
  card_3: frame('3', faceNumber(3), { accent: ACCENT['3'], bg: WHITE, edge: true }),
  card_4: frame('4', faceNumber(4), { accent: ACCENT['4'], bg: WHITE, edge: true }),
  card_5: frame('5', faceNumber(5), { accent: ACCENT['5'], bg: WHITE, edge: true }),
  card_6: frame('6', faceNumber(6), {
    underline: true,
    accent: ACCENT['6'],
    bg: WHITE,
    edge: true,
  }),
  card_7: frame('7', peekOwn(ACCENT['7']), { accent: ACCENT['7'], bg: BG['7'] }),
  card_8: frame('8', peekOwn(ACCENT['8']), { accent: ACCENT['8'], bg: BG['8'] }),
  card_9: frame('9', peekThem(ACCENT['9']), {
    underline: true,
    accent: ACCENT['9'],
    bg: BG['9'],
  }),
  card_10: frame('10', peekThem(ACCENT['10']), { accent: ACCENT['10'], bg: BG['10'] }),
  card_j: frame('J', faceJack(), { accent: ACCENT.j, bg: BG.j }),
  card_q: frame('Q', faceQueen(), { accent: ACCENT.q, bg: BG.q }),
  card_k: frame('K', faceKing(), { accent: ACCENT.k, bg: BG.k }),
  card_a: frame('A', faceAce(), { accent: ACCENT.a, bg: BG.a }),
  card_joker: frame('', faceJoker(), { joker: true, accent: ORANGE, bg: BG.joker }),
  card_back: cardBack(),
};

// ---------------------------------------------------------------- vector drawables

const APP_DRAWABLE = join(HERE, '..', 'composeApp/src/commonMain/composeResources/drawable');

function rectPath(a) {
  const x = Number(a.get('x') ?? 0);
  const y = Number(a.get('y') ?? 0);
  const w = Number(a.get('width'));
  const h = Number(a.get('height'));
  const r = Math.min(Number(a.get('rx') ?? 0), w / 2, h / 2);
  if (r <= 0) {
    return `M${pf(x)},${pf(y)} L${pf(x + w)},${pf(y)} L${pf(x + w)},${pf(y + h)} L${pf(x)},${pf(y + h)} Z`;
  }
  return (
    `M${pf(x + r)},${pf(y)} L${pf(x + w - r)},${pf(y)} A${pf(r)},${pf(r)} 0 0 1 ${pf(x + w)},${pf(y + r)} ` +
    `L${pf(x + w)},${pf(y + h - r)} A${pf(r)},${pf(r)} 0 0 1 ${pf(x + w - r)},${pf(y + h)} ` +
    `L${pf(x + r)},${pf(y + h)} A${pf(r)},${pf(r)} 0 0 1 ${pf(x)},${pf(y + h - r)} ` +
    `L${pf(x)},${pf(y + r)} A${pf(r)},${pf(r)} 0 0 1 ${pf(x + r)},${pf(y)} Z`
  );
}

function shapePath(el, tag) {
  const a = el.attrs;
  if (tag === 'rect') return rectPath(a);
  if (tag === 'circle') {
    const cx = Number(a.get('cx'));
    const cy = Number(a.get('cy'));
    const r = Number(a.get('r'));
    return `M${pf(cx - r)},${pf(cy)} A${pf(r)},${pf(r)} 0 1 1 ${pf(cx + r)},${pf(cy)} A${pf(r)},${pf(r)} 0 1 1 ${pf(cx - r)},${pf(cy)} Z`;
  }
  if (tag === 'line') {
    return `M${a.get('x1')},${a.get('y1')} L${a.get('x2')},${a.get('y2')}`;
  }
  if (tag === 'polygon' || tag === 'polyline') {
    const pts = a.get('points').split(/\s+/).filter(Boolean);
    const d = `M${pts.join(' L')}`;
    return tag === 'polygon' ? `${d} Z` : d;
  }
  if (tag === 'path') return a.get('d');
  die(`vector-drawable emitter: unsupported element <${tag}>`);
}

function paint(el, tag) {
  const a = el.attrs;
  const out = [];
  let fill = a.get('fill');
  if (fill === undefined && (tag === 'line' || tag === 'polyline')) fill = 'none';
  if (fill === undefined) die(`<${tag}> without explicit fill`);
  const opacity = Number(a.get('opacity') ?? 1);
  if (fill !== 'none') {
    out.push(`android:fillColor="${fill}"`);
    if (opacity < 1) out.push(`android:fillAlpha="${pf(opacity)}"`);
  }
  const stroke = a.get('stroke');
  if (stroke && stroke !== 'none') {
    out.push(`android:strokeColor="${stroke}"`);
    out.push(`android:strokeWidth="${a.get('stroke-width') ?? '1'}"`);
    if (opacity < 1) out.push(`android:strokeAlpha="${pf(opacity)}"`);
    const cap = a.get('stroke-linecap');
    if (cap) out.push(`android:strokeLineCap="${cap}"`);
    const join = a.get('stroke-linejoin');
    if (join) out.push(`android:strokeLineJoin="${join}"`);
  }
  return out;
}

function groupAttrs(transform) {
  let m = /^rotate\(([-\d.]+) ([\d.]+) ([\d.]+)\)$/.exec(transform);
  if (m) {
    return `android:rotation="${m[1]}" android:pivotX="${m[2]}" android:pivotY="${m[3]}"`;
  }
  m = /^translate\(([-\d.]+) ([-\d.]+)\) rotate\(([-\d.]+)\)$/.exec(transform);
  if (m) {
    return `android:translateX="${m[1]}" android:translateY="${m[2]}" android:rotation="${m[3]}"`;
  }
  m = /^translate\(([-\d.]+) ([-\d.]+)\) scale\(([\d.]+)\)$/.exec(transform);
  if (m) {
    return (
      `android:translateX="${m[1]}" android:translateY="${m[2]}" ` +
      `android:scaleX="${m[3]}" android:scaleY="${m[3]}"`
    );
  }
  die(`vector-drawable emitter: unsupported transform ${JSON.stringify(transform)}`);
}

/**
 * A minimal XML reader for the generator's own output — Python had ElementTree in the
 * standard library and Node has nothing, so this stands in. It only needs to handle what
 * this file writes: elements with double-quoted attributes, no text, no comments, no
 * entities. Attribute order is preserved, because the emitter below reads it back out in
 * document order.
 */
function parseXml(src) {
  let i = 0;
  const stack = [];
  let root = null;
  while (i < src.length) {
    const lt = src.indexOf('<', i);
    if (lt < 0) break;
    const gt = src.indexOf('>', lt);
    let inner = src.slice(lt + 1, gt);
    if (inner.startsWith('/')) {
      stack.pop();
      i = gt + 1;
      continue;
    }
    const selfClose = inner.endsWith('/');
    if (selfClose) inner = inner.slice(0, -1);
    const tag = /^([A-Za-z][\w:.-]*)/.exec(inner)[1];
    const attrs = new Map();
    const re = /([A-Za-z_][\w:.-]*)\s*=\s*"([^"]*)"/g;
    const rest = inner.slice(tag.length);
    let am;
    while ((am = re.exec(rest)) !== null) attrs.set(am[1], am[2]);
    const el = { tag, attrs, children: [] };
    if (stack.length) stack[stack.length - 1].children.push(el);
    else root = el;
    if (!selfClose) stack.push(el);
    i = gt + 1;
  }
  return root;
}

/**
 * Translate the generator's own SVG subset into an Android vector drawable.
 * Not a general converter: it refuses anything it does not know, so a new
 * SVG feature fails the build here rather than rendering wrong in the app.
 */
function svgToVectorDrawable(svg) {
  const root = parseXml(svg.replace('xmlns="http://www.w3.org/2000/svg"', ''));
  const lines = [
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
    `    android:width="${bare(W)}dp" android:height="${bare(H)}dp"`,
    `    android:viewportWidth="${bare(W)}" android:viewportHeight="${bare(H)}">`,
  ];

  const walk = (el, depth) => {
    const pad = '    '.repeat(depth);
    for (const child of el.children) {
      const tag = child.tag;
      if (tag === 'g') {
        lines.push(`${pad}<group ${groupAttrs(child.attrs.get('transform'))}>`);
        walk(child, depth + 1);
        lines.push(`${pad}</group>`);
        continue;
      }
      const p = paint(child, tag);
      const d = shapePath(child, tag);
      lines.push(`${pad}<path android:pathData="${d}" ` + p.join(' ') + '/>');
    }
  };

  walk(root, 1);
  lines.push('</vector>');
  return lines.join('\n');
}

function previewHtml() {
  const cell = (name, height) =>
    `<figure style="margin:0;text-align:center">` +
    `<img src="${name}.svg" style="height:${bare(height)}px;border-radius:6px;` +
    `box-shadow:0 4px 14px rgba(0,0,0,.35)"/>` +
    `<figcaption style="color:#aaa;font:12px sans-serif;margin-top:6px">${name}</figcaption>` +
    `</figure>`;

  const big = Object.keys(FACES)
    .map((n) => cell(n, 320))
    .join('');
  const small = Object.keys(FACES)
    .map((n) => cell(n, 120))
    .join('');
  return (
    "<!doctype html><meta charset='utf-8'><title>Vinto deck preview</title>" +
    "<body style='margin:0;background:#0E3428;padding:24px'>" +
    "<h2 style='color:#F7F5EF;font-family:Georgia,serif'>Full size</h2>" +
    `<div style='display:flex;flex-wrap:wrap;gap:18px'>${big}</div>` +
    "<h2 style='color:#F7F5EF;font-family:Georgia,serif;margin-top:36px'>" +
    'Thumbnail (the crowded-table test)</h2>' +
    `<div style='display:flex;flex-wrap:wrap;gap:10px'>${small}</div>` +
    '</body>'
  );
}

// ---------------------------------------------------------------- the gates

function die(message) {
  process.stderr.write(`${message}\n`);
  process.exit(1);
}

function lin(c) {
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function channels(hexColor) {
  return [1, 3, 5].map((i) => lin(parseInt(hexColor.slice(i, i + 2), 16) / 255));
}

function relativeLuminance(hexColor) {
  const [r, g, b] = channels(hexColor);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a, b) {
  const [hi, lo] = [relativeLuminance(a), relativeLuminance(b)].sort((p, q) => q - p);
  return (hi + 0.05) / (lo + 0.05);
}

function lab(hexColor) {
  const [r, g, b] = channels(hexColor);
  const x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
  const y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  const z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;

  const f = (t) => (t > 216 / 24389 ? t ** (1 / 3) : (841 / 108) * t + 4 / 29);

  const [fx, fy, fz] = [f(x), f(y), f(z)];
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
}

function deltaE(a, b) {
  const p = lab(a);
  const q = lab(b);
  return Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]);
}

// Viénot's linear dichromat simulation. Approximate, and the right kind of approximate:
// it answers "do these two still differ" rather than "what exactly does he see".
const RGB2LMS = [
  [17.8824, 43.5161, 4.11935],
  [3.45565, 27.1554, 3.86714],
  [0.0299566, 0.184309, 1.46709],
];
const LMS2RGB = [
  [0.080944, -0.130504, 0.116721],
  [-0.0102485, 0.0540194, -0.113615],
  [-0.000365294, -0.00412163, 0.693513],
];
const DICHROMAT = {
  deuteranope: [
    [1, 0, 0],
    [0.494207, 0, 1.24827],
    [0, 0, 1],
  ],
  protanope: [
    [0, 2.02344, -2.52581],
    [0, 1, 0],
    [0, 0, 1],
  ],
};

function apply(matrix, vector) {
  return matrix.map((row) => row.reduce((sum, m, i) => sum + m * vector[i], 0));
}

function simulate(hexColor, kind) {
  const v = [1, 3, 5].map((i) => lin(parseInt(hexColor.slice(i, i + 2), 16) / 255));
  const out = apply(LMS2RGB, apply(DICHROMAT[kind], apply(RGB2LMS, v)));

  const gamma = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);

  return (
    '#' +
    out
      .map((c) =>
        Math.max(0, Math.min(255, pyRound(gamma(c) * 255)))
          .toString(16)
          .toUpperCase()
          .padStart(2, '0'),
      )
      .join('')
  );
}

// Two cards that do the same thing still have to be two cards. The WCAG gate below says
// nothing about this — every ground passed it while 9 and 10 were dE 7.7 apart — so the
// distance is held here, in lightness as well as hue, and with the hue taken away.
const SIBLINGS = [
  ['7', '8'],
  ['9', '10'],
  ['j', 'q'],
];

function checkSeparation() {
  const problems = [];
  for (const [a, b] of SIBLINGS) {
    const ga = BG[a];
    const gb = BG[b];
    const gap = deltaE(ga, gb);
    const lightness = Math.abs(lab(ga)[0] - lab(gb)[0]);
    if (gap < 24) problems.push(`${a}/${b} grounds only dE ${fmt(gap, 1)} apart`);
    if (lightness < 12) {
      problems.push(`${a}/${b} differ by L* ${fmt(lightness, 1)} — hue is doing it alone`);
    }
    for (const kind of Object.keys(DICHROMAT)) {
      const seen = deltaE(simulate(ga, kind), simulate(gb, kind));
      if (seen < 18) problems.push(`${a}/${b} collapse to dE ${fmt(seen, 1)} for a ${kind}`);
    }
  }
  const grounds = BG_ORDER.map((r) => [r, BG[r]]).concat([['numbers', WHITE]]);
  grounds.forEach(([ra, ca], i) => {
    for (const [rb, cb] of grounds.slice(i + 1)) {
      if (deltaE(ca, cb) < 12) {
        problems.push(`${ra} and ${rb} grounds are dE ${fmt(deltaE(ca, cb), 1)} apart`);
      }
    }
  });
  if (problems.length) die(`card separation gate failed:\n  ${problems.join('\n  ')}`);
}

// What each emblem paints, and on which ground. A shape is perceivable when its fill OR
// its outline clears 3:1 (WCAG 1.4.11) — the outline is why every emblem card has an ink
// one, and this is the check that says so out loud.
function checkEmblemInk() {
  const yours = [FELT, INK];
  const theirs = [BLUE, INK];
  const eye = [WHITE, INK];
  const deck = ['#EAD9A6', INK];
  const painted = [
    ['7', [yours, eye]],
    ['8', [yours, eye]],
    ['9', [theirs]],
    ['10', [theirs]],
    ['j', [yours, theirs, eye]],
    ['q', [yours, theirs, eye]],
    ['k', [yours, theirs, ['none', ACCENT.k]]],
    ['a', [deck, theirs, [INK, PALE], ['none', PENALTY]]],
    ['joker', [[ORANGE, INK], [JOKER_INK, 'none']]],
    // the back is cream, and its marks carry no outline at all
    ['back', [[ORANGE, INK], ...ENGRAVED.map((c) => [c, 'none'])]],
  ];
  const problems = [];
  for (const [rank, shapes] of painted) {
    const ground = rank === 'back' ? PAPER : BG[rank];
    for (const [fill, stroke] of shapes) {
      const best = Math.max(...[fill, stroke].filter((c) => c !== 'none').map((c) => contrast(c, ground)));
      if (best < 3.0) problems.push(`${rank}: a ${fill}/${stroke} shape is ${fmt(best, 2)} on ${ground}`);
    }
  }
  // The Jack's slash is the one shape drawn over other shapes rather than over a ground,
  // so it is checked against each of the three it crosses. Neither of its two colours
  // reads on all three alone — which is the whole reason it is a cased line.
  for (const crossed of [FELT, BLUE, WHITE]) {
    if (Math.max(contrast(PAPER, crossed), contrast(INK, crossed)) < 3.0) {
      problems.push(`the Jack's slash cannot be seen over ${crossed}`);
    }
  }
  if (problems.length) die(`emblem ink gate failed:\n  ${problems.join('\n  ')}`);
}

/**
 * WCAG gate: indices need 4.5:1 on their grounds (large text would allow 3, but the
 * indices are the one thing that must always read); card grounds need 3:1 against the
 * felt they sit on in the app.
 *
 * Against the light theme's Paper surface — the help sheet and the lesson — no ground
 * reaches 3:1, the white number cards included, and that is not held here. It cannot
 * usefully be: the ratio is luminance only, and what separates a pale green card from
 * cream is hue, which the formula cannot see and the eye can. The plain numbers keep an
 * ink rule because white on cream really is nothing; the tinted ones are checked by
 * looking at them.
 */
function checkContrast() {
  const problems = [];
  for (const rank of ACCENT_ORDER) {
    const accent = ACCENT[rank];
    const bg = BG[rank] ?? WHITE;
    if (contrast(accent, bg) < 4.5) {
      problems.push(`index ${rank}: ${fmt(contrast(accent, bg), 2)} on ${bg}`);
    }
  }
  for (const rank of [...BG_ORDER, 'numbers']) {
    const bg = rank === 'numbers' ? WHITE : BG[rank];
    for (const felt of [FELT, FELT_DARK]) {
      if (contrast(bg, felt) < 3.0) {
        problems.push(`ground ${rank} vs ${felt}: ${fmt(contrast(bg, felt), 2)}`);
      }
    }
  }
  if (problems.length) die(`WCAG contrast gate failed:\n  ${problems.join('\n  ')}`);
}

function isDir(p) {
  try {
    return statSync(p).isDirectory();
  } catch {
    return false;
  }
}

function main() {
  checkContrast();
  checkSeparation();
  checkEmblemInk();
  mkdirSync(OUT, { recursive: true });
  for (const [name, svg] of Object.entries(FACES)) {
    writeFileSync(join(OUT, `${name}.svg`), svg);
  }
  writeFileSync(join(OUT, 'preview.html'), previewHtml());
  const count = Object.keys(FACES).length;
  process.stdout.write(`wrote ${count} faces + preview.html to ${OUT}\n`);
  if (isDir(APP_DRAWABLE)) {
    for (const [name, svg] of Object.entries(FACES)) {
      writeFileSync(join(APP_DRAWABLE, `${name}.xml`), svgToVectorDrawable(svg));
    }
    process.stdout.write(`wrote ${count} vector drawables to ${APP_DRAWABLE}\n`);
  }
}

main();
