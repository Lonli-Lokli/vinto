#!/usr/bin/env python3
"""Generate the meaning-based card faces as SVG.

Design (docs/design/CARD-IMAGERY.md). Ownership is a colour that needs no legend: felt
green is your card, blue is an opponent's, cream the deck's, and every one of them is
outlined in ink so it holds on any ground.

The ground says what the card DOES, in four families rather than nine tints — green
reaches your own cards (7, 8), blue reaches theirs (9, 10), orange moves one between two
players (J, Q), yellow is the crown and the deck the Ace throws from (K, A). Two ranks in
one family are separated by LIGHTNESS, so the pair survives a dichromat; the plain
numbers keep a white ground, because doing nothing is their whole meaning.

The emblems: 7/8 stand a card up out of your row under a wide eye, 9/10 drop one out of
theirs under a round lens — opposite silhouettes, not one scene twice. J trades the pair
under a slashed eye, Q under two open eyes with dashed arrows, K rays a crown at a card
in each corner, A throws a deck card at an opponent, the Joker is its cap over its name.

Every face carries standard corner indices (bottom-right rotated, 6 and 9 underlined),
drawn bold and at large-print size because on the felt they are often the only part of a
card big enough to read.

Three gates run before anything is written, and each one exists because something got
past the two before it: `check_contrast` (WCAG on text and grounds), `check_separation`
(two cards that do the same thing are still two cards) and `check_emblem_ink` (every
shape is visible on the ground it is painted on).

Usage:  python3 tools/make-card-faces.py
Output: tools/card-faces/*.svg and tools/card-faces/preview.html
"""

import math
import pathlib

W, H = 825, 1125
CX = W / 2
CX_INT = int(CX)

INK = "#14181B"
FELT = "#1B5E43"
FELT_DARK = "#0E3428"
GOLD = "#C9A227"
GOLD_DARK = "#8A6D1B"
PAPER = "#F7F5EF"
ORANGE = "#E8791E"
WHITE = "#FFFFFF"
PALE = "#A8C2B5"
BLUE = "#5B9BD5"        # opponents' cards — distinct from your green at any size
JOKER_INK = "#A34A08"   # the Joker's name: orange darkened to hold on its grey ground
PENALTY = "#9E2B25"     # the Ace's throw — the one red left in the deck, and it means harm
# The back's four engraved marks. Darker than the deck colours they stand for, because
# they are the one place a colour is drawn with no outline to fall back on, and gold,
# blue and orange all sat under 3:1 on cream — 2.2, 2.7 and 2.7.
ENGRAVED = ("#8A6D1B", "#2A6BB0", "#1B5E43", "#C25E10")
BLUE_EDGE = "#DCE9F5"

# One accent per rank family, dark enough to hold as a corner index on cream.
ACCENT = {
    "2": "#17766B",   # teal
    "3": "#2F5E8C",   # blue
    "4": "#4F5AA8",   # indigo
    "5": "#B03A57",   # raspberry
    "6": "#1B5E43",   # the brand green
    "7": "#1B7A3E",   # green — the peeks that reach your own cards
    "8": "#145C2E",
    "9": "#1B477A",   # blue — the peeks that reach somebody else's
    "10": "#171E75",
    "j": "#7A441B",   # orange — a card crossing between two players
    "q": "#663006",
    "k": "#7A641B",   # yellow — the crown, and the deck the Ace throws from
    "a": "#664916",
}

# Tinted grounds for the action cards. Four families, and which family a rank belongs to
# is decided by WHAT THE CARD DOES rather than by picking nine pleasant tints:
#
#     green   the action reaches one of your own cards      7, 8
#     blue    it reaches one of somebody else's             9, 10
#     orange  a card crosses between two players            J, Q
#     yellow  the crown, and the deck the Ace throws from   K, A
#
# which is the same legend the emblems already draw with — your cards are felt green on
# every face, theirs are blue — so the ground now agrees with the picture on it instead of
# being a tenth colour with nothing to say. The numbers keep the plain white: they do
# nothing, which is their whole meaning.
#
# Within a family the two ranks split by LIGHTNESS, not by hue. They used to be
# neighbouring tints — "a sibling, not a twin" — and measured, 9 and 10 were dE 7.7 apart
# and J and Q were 6.2, which is not a sibling, it is the same colour twice; worse, what
# little separated them was hue alone, so the player who most needed the cue was the one
# who could not use it. `check_separation` holds all three pairs at arm's length, with a
# simulated deuteranope and protanope looking at them.
BG = {
    "7": "#CCFCDE",   # green: this action reaches one of YOUR cards
    "8": "#70E099",
    "9": "#D7E8FC",   # blue: this action reaches one of THEIRS
    "10": "#B6BAFC",  # periwinkle, shifted off the opponents' own blue
    "j": "#F7E2D2",   # orange: a card crosses between two players
    "q": "#E0A170",
    "k": "#FCEDB8",   # yellow: the crown, and the deck the Ace throws from
    "a": "#F0BE69",
    "joker": "#C6C6C6",  # neutral: the wild card belongs to no family
}

OUT = pathlib.Path(__file__).parent / "card-faces"

# ---------------------------------------------------------------- primitives


def group(rot, cx, cy, body):
    return f'<g transform="rotate({rot} {cx} {cy})">{body}</g>'


def pip_card(cx, cy, w=100, h=138, fill=FELT):
    """A card-shaped pip: the number cards count in the game's own object."""
    return (
        f'<rect x="{cx - w / 2:.0f}" y="{cy - h / 2:.0f}" width="{w}" height="{h}" rx="12" '
        f'fill="{fill}" stroke="{GOLD}" stroke-width="5"/>'
    )


def card_shape(cx, cy, w, h, rot=0.0, fill=FELT, stroke=GOLD, sw=9):
    """A game card: green yours, blue theirs, cream the deck's, and the colour of the
    fill is the entire ownership legend.

    The outline is INK and the rank's own colour is an inner line inside it. It used to
    be the other way round — a gold or pale-blue edge and no dark outline — which put the
    whole burden of being visible on the fill, and measured, the opponents' blue managed
    3:1 against *not one* of the nine grounds it is drawn on. A dark outline costs
    nothing, holds on any ground a card is ever given, and is what makes these read as
    cards rather than as smudges once they are 12 px wide."""
    body = (
        f'<rect x="{cx - w / 2:.0f}" y="{cy - h / 2:.0f}" width="{w:.0f}" height="{h:.0f}" '
        f'rx="{w * 0.09:.0f}" fill="{fill}" stroke="{INK}" stroke-width="{sw}"/>'
        f'<rect x="{cx - w / 2 + w * 0.08:.0f}" y="{cy - h / 2 + w * 0.08:.0f}" '
        f'width="{w * 0.84:.0f}" height="{h - w * 0.16:.0f}" rx="{w * 0.06:.0f}" '
        f'fill="none" stroke="{stroke}" stroke-width="{max(4, sw * 0.5):.0f}"/>'
    )
    return group(rot, cx, cy, body) if rot else body


def bust(cx, cy, s=1.0):
    """A small opponent, head and shoulders — the 'someone else' marker."""
    return (
        f'<circle cx="{cx}" cy="{cy - 66 * s:.0f}" r="{34 * s:.0f}" fill="{INK}" '
        f'stroke="{PALE}" stroke-width="{5 * s:.0f}"/>'
        f'<path d="M {cx - 72 * s:.0f},{cy + 10 * s:.0f} Q {cx - 64 * s:.0f},{cy - 46 * s:.0f} '
        f'{cx},{cy - 52 * s:.0f} Q {cx + 64 * s:.0f},{cy - 46 * s:.0f} '
        f'{cx + 72 * s:.0f},{cy + 10 * s:.0f} Z" '
        f'fill="{INK}" stroke="{PALE}" stroke-width="{5 * s:.0f}"/>'
    )


def big_eye(cx, cy, s=1.0, gaze=0.0, iris=GOLD):
    """gaze shifts iris and pupil vertically: positive looks down, negative up."""
    w, h = 170 * s, 108 * s
    g = gaze * s
    return (
        f'<path d="M {cx - w},{cy} Q {cx},{cy - h} {cx + w},{cy} Q {cx},{cy + h} {cx - w},{cy} Z" '
        f'fill="{WHITE}" stroke="{INK}" stroke-width="{16 * s:.0f}" stroke-linejoin="round"/>'
        f'<circle cx="{cx}" cy="{cy + g:.0f}" r="{56 * s:.0f}" fill="{iris}"/>'
        f'<circle cx="{cx}" cy="{cy + g:.0f}" r="{27 * s:.0f}" fill="{INK}"/>'
        f'<circle cx="{cx + 16 * s:.0f}" cy="{cy + g - 18 * s:.0f}" r="{10 * s:.0f}" fill="{WHITE}"/>'
    )


def lashed_eye(cx, cy, s=1.0, iris=GOLD, side=1):
    """The Queen's eye: the same eye, with lashes swept out from its outer corner.

    She is the only face with a person in it rather than a tool or a diagram, and the
    Jack beside her is the same two cards under the same eye — so the pair needs telling
    apart by more than an open lid versus a slashed one. `side` is which way the lashes
    sweep, so the two eyes lean away from each other rather than both leaning right."""
    w, h = 170 * s, 108 * s
    lashes = ""
    for t, reach in ((0.58, 0.86), (0.71, 1.0), (0.84, 0.82)):
        u = t if side > 0 else 1 - t
        # A point on the upper lid, which is one quadratic: x is linear in u and the
        # lid's height is 2h*u*(1-u), so the lashes sit on the curve rather than near it.
        x, y = cx + w * (2 * u - 1), cy - 2 * h * u * (1 - u)
        dx, dy = side * 0.6, -1.0
        n = math.hypot(dx, dy)
        length = 52 * s * reach
        lashes += (
            f'<line x1="{x:.0f}" y1="{y:.0f}" x2="{x + length * dx / n:.0f}" '
            f'y2="{y + length * dy / n:.0f}" stroke="{INK}" '
            f'stroke-width="{12 * s:.0f}" stroke-linecap="round"/>'
        )
    return big_eye(cx, cy, s, iris=iris) + lashes


def arrowhead(x, y, ang, size=40, color=GOLD):
    p1 = (x - size * 1.4 * math.cos(ang - 0.45), y - size * 1.4 * math.sin(ang - 0.45))
    p2 = (x - size * 1.4 * math.cos(ang + 0.45), y - size * 1.4 * math.sin(ang + 0.45))
    return f'<polygon points="{x:.0f},{y:.0f} {p1[0]:.0f},{p1[1]:.0f} {p2[0]:.0f},{p2[1]:.0f}" fill="{color}"/>'


def arc_arrow(cx, cy, r, a1, a2, dashed=False, sw=18, color=GOLD):
    """A circular swap arrow from angle a1 to a2 (degrees, screen coords,
    clockwise). Dashed arcs are emitted as sub-arc segments — vector drawables
    have no stroke-dasharray."""

    def pt(a):
        return cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))

    tangent = math.atan2(math.cos(math.radians(a2)), -math.sin(math.radians(a2)))
    if not dashed:
        x1, y1 = pt(a1)
        x2, y2 = pt(a2)
        body = (
            f'<path d="M {x1:.0f},{y1:.0f} A {r} {r} 0 0 1 {x2:.0f},{y2:.0f}" fill="none" '
            f'stroke="{color}" stroke-width="{sw}" stroke-linecap="round"/>'
        )
    else:
        dash_deg = math.degrees(34 / r)
        gap_deg = math.degrees(26 / r)
        segs = []
        a = a1
        while a < a2:
            b = min(a + dash_deg, a2)
            xa, ya = pt(a)
            xb, yb = pt(b)
            segs.append(
                f'<path d="M {xa:.0f},{ya:.0f} A {r} {r} 0 0 1 {xb:.0f},{yb:.0f}" fill="none" '
                f'stroke="{color}" stroke-width="{sw}" stroke-linecap="round"/>'
            )
            a = b + gap_deg
        body = "".join(segs)
    x2, y2 = pt(a2)
    return body + arrowhead(x2, y2, tangent, color=color)


def ray(x1, y1, x2, y2, color=GOLD):
    """A dashed sight-ray with an arrowhead — dashes drawn as real segments,
    because vector drawables have no stroke-dasharray."""
    ang = math.atan2(y2 - y1, x2 - x1)
    length = math.hypot(x2 - x1, y2 - y1)
    ca, sa = math.cos(ang), math.sin(ang)
    segs = []
    t = 0.0
    while t < length:
        end = min(t + 16, length)
        segs.append(
            f'<line x1="{x1 + t * ca:.0f}" y1="{y1 + t * sa:.0f}" '
            f'x2="{x1 + end * ca:.0f}" y2="{y1 + end * sa:.0f}" stroke="{color}" '
            f'stroke-width="9" stroke-linecap="round"/>'
        )
        t += 30
    return "".join(segs) + arrowhead(x2, y2, ang, size=24, color=color)


def crown(cx, cy, s=1.0, color=GOLD):
    """Hollow outline crown — mighty, weighs nothing."""
    w, h = 130 * s, 96 * s
    d = (
        f"M {cx - w},{cy + h * 0.5} L {cx - w},{cy - h * 0.28} "
        f"L {cx - w * 0.5},{cy + h * 0.06} L {cx},{cy - h * 0.62} "
        f"L {cx + w * 0.5},{cy + h * 0.06} L {cx + w},{cy - h * 0.28} "
        f"L {cx + w},{cy + h * 0.5} Z"
    )
    dots = "".join(
        f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{11 * s:.0f}" fill="none" '
        f'stroke="{color}" stroke-width="{7 * s:.0f}"/>'
        for x, y in ((cx - w, cy - h * 0.42), (cx, cy - h * 0.78), (cx + w, cy - h * 0.42))
    )
    return (
        f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{13 * s:.0f}" '
        f'stroke-linejoin="round"/>'
        f'<line x1="{cx - w}" y1="{cy + h * 0.74}" x2="{cx + w}" y2="{cy + h * 0.74}" '
        f'stroke="{color}" stroke-width="{13 * s:.0f}" stroke-linecap="round"/>' + dots
    )


def jester_cap(cx, cy, s=1.0, colors=("#17766B", ORANGE, "#B03A57")):
    """Three floppy horns over a band, a bell on each tip — and motley, as a
    jester's cap traditionally is: each horn its own colour."""
    base = cy + 40 * s

    def petal(x1, ctrl, tip, back, color):
        return (
            f'<path d="M {cx + x1 * s},{base} Q {cx + ctrl[0] * s},{cy + ctrl[1] * s} '
            f'{cx + tip[0] * s},{cy + tip[1] * s} Q {cx + back[0] * s},{cy + back[1] * s} '
            f'{cx + x1 / 3 * s},{base - 6 * s} Z" fill="{color}" stroke="{INK}" '
            f'stroke-width="{5 * s:.0f}" stroke-linejoin="round"/>'
        )

    left = petal(-64, (-132, -64), (-150, -2), (-78, -24), colors[0])
    right = petal(64, (132, -64), (150, -2), (78, -24), colors[2])
    middle = (
        f'<path d="M {cx - 30 * s},{base} Q {cx - 12 * s},{cy - 102 * s} {cx},{cy - 100 * s} '
        f'Q {cx + 12 * s},{cy - 102 * s} {cx + 30 * s},{base} Z" fill="{colors[1]}" '
        f'stroke="{INK}" stroke-width="{5 * s:.0f}" stroke-linejoin="round"/>'
    )
    band = (
        f'<rect x="{cx - 70 * s}" y="{base - 7 * s}" width="{140 * s:.0f}" height="{24 * s:.0f}" '
        f'rx="{11 * s:.0f}" fill="{GOLD}" stroke="{INK}" stroke-width="{4 * s:.0f}"/>'
    )
    bells = "".join(
        f'<circle cx="{cx + dx * s}" cy="{cy + dy * s}" r="{11 * s:.0f}" fill="{GOLD}" '
        f'stroke="{INK}" stroke-width="{3 * s:.0f}"/>'
        for dx, dy in ((-156, 10), (0, -108), (156, 10))
    )
    return left + right + middle + band + bells


def blindfold(cx, cy, hw, rot):
    """A knotted band across both cards: the swap is blind."""
    body = (
        f'<polygon points="{cx + hw - 10},{cy - 26} {cx + hw + 128},{cy - 96} '
        f'{cx + hw + 74},{cy - 2}" '
        f'fill="{GOLD}" stroke="{INK}" stroke-width="7" stroke-linejoin="round"/>'
        f'<polygon points="{cx + hw - 10},{cy + 22} {cx + hw + 136},{cy + 62} '
        f'{cx + hw + 58},{cy + 106}" '
        f'fill="{GOLD}" stroke="{INK}" stroke-width="7" stroke-linejoin="round"/>'
        f'<rect x="{cx - hw}" y="{cy - 44}" width="{hw * 2}" height="88" rx="40" '
        f'fill="{GOLD}" stroke="{INK}" stroke-width="7"/>'
        f'<path d="M {cx - hw + 40},{cy - 12} Q {cx},{cy - 30} {cx + hw - 40},{cy - 12}" '
        f'fill="none" stroke="{GOLD_DARK}" stroke-width="6" stroke-linecap="round"/>'
        f'<circle cx="{cx + hw + 8}" cy="{cy}" r="20" fill="{GOLD_DARK}" '
        f'stroke="{INK}" stroke-width="5"/>'
    )
    return group(rot, cx, cy, body)


# ---------------------------------------------------------------- the frame


# The corner index is drawn bold and at large-print size, and that is an accessibility
# decision rather than a style one. On the felt a card is 32-56 dp wide, and below the tap
# floor the emblem is a smudge while the index still has to be a number — CARD-IMAGERY.md
# says as much: "only the corner index carries the card". At the old 0.95 and stroke 17 the
# numeral was 11% of the card's width, about 6 dp on the table, in a stroke a third of a
# device pixel wide on the side seats. At 1.55 it is 19% of the width in a bold weight,
# which is what a large-print ("jumbo index") deck uses — the affordance low-vision players
# already buy a whole deck to get.
INDEX_SCALE = 1.55
INDEX_X, INDEX_Y = 66, 62
# The 10 is two glyphs, so it is set smaller to keep the pair inside the same corner and
# dropped so both indices share an optical centre. The 1 is a stem in a full-width box, so
# the pair is kerned tighter than two boxes would otherwise sit.
TEN_SCALE = INDEX_SCALE * 0.78
TEN_KERN = 86
TEN_DROP = 26
# Bold. Measured against the glyph skeletons rather than chosen: at 26 the 8's counters and
# the 4's triangle start to fill in, which costs more legibility at a thumbnail than the
# extra weight buys.
GLYPH_STROKE = 23

# Monoline glyph skeletons on a 100-wide, 124-tall box, drawn as strokes.
# Vector drawables cannot render text, so the indices are geometry like
# everything else — which also frees them from platform fonts.
GLYPHS = {
    "2": ["M14 34 Q14 8 50 8 Q86 8 86 36 Q86 60 50 88 L14 116 L88 116"],
    "3": ["M16 28 Q24 8 50 8 Q84 8 84 32 Q84 56 50 60 Q86 64 86 92 Q86 116 50 116 Q22 116 14 98"],
    "4": ["M64 116 L64 8 L10 82 L92 82"],
    "5": ["M82 8 L24 8 L18 54 Q34 46 52 46 Q86 50 86 82 Q86 116 50 116 Q22 116 14 98"],
    "6": ["M76 18 Q64 6 48 8 Q14 14 14 66 Q14 116 50 116 Q84 116 84 84 Q84 56 50 56 Q22 56 15 76"],
    "7": ["M12 8 L88 8 L44 116"],
    "8": [
        "M50 58 Q17 53 17 32 Q17 8 50 8 Q83 8 83 32 Q83 53 50 58 "
        "Q13 64 13 90 Q13 116 50 116 Q87 116 87 90 Q87 64 50 58"
    ],
    "9": ["M24 106 Q36 118 52 116 Q86 110 86 58 Q86 8 50 8 Q16 8 16 40 Q16 68 50 68 Q78 68 85 48"],
    "1": ["M28 24 L50 8 L50 116"],
    "0": ["M50 8 Q15 8 15 62 Q15 116 50 116 Q85 116 85 62 Q85 8 50 8"],
    "O": ["M50 8 Q15 8 15 62 Q15 116 50 116 Q85 116 85 62 Q85 8 50 8"],
    "E": ["M24 8 L24 116", "M24 8 L86 8", "M24 62 L76 62", "M24 116 L86 116"],
    "J": ["M68 8 L68 88 Q68 116 44 116 Q22 116 16 96"],
    "Q": ["M50 8 Q15 8 15 62 Q15 116 50 116 Q85 116 85 62 Q85 8 50 8", "M60 94 L88 124"],
    "K": ["M22 8 L22 116", "M82 8 L24 66", "M44 50 L86 116"],
    "R": ["M24 116 L24 8 L58 8 Q86 8 86 35 Q86 62 58 62 L24 62", "M54 62 L88 116"],
    "A": ["M12 116 L50 8 L88 116", "M27 82 L73 82"],
}


def glyph(ch, x, y, scale, color, weight=GLYPH_STROKE):
    body = "".join(
        f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{weight}" '
        f'stroke-linecap="round" stroke-linejoin="round"/>'
        for d in GLYPHS[ch]
    )
    return f'<g transform="translate({x} {y}) scale({scale})">{body}</g>'


def word(text, cx, y, scale, color, kern=86):
    """A word set in the index letterforms. The deck draws no text anywhere else — a
    vector drawable cannot render a font, and every other label a card might want is
    something the emblem says better."""
    span = (len(text) - 1) * kern * scale
    x0 = cx - span / 2 - 50 * scale
    return "".join(
        glyph(c, x0 + i * kern * scale, y, scale, color) for i, c in enumerate(text)
    )


def index_glyph(label, underline, color=INK):
    if label == "10":
        t = glyph("1", INDEX_X, INDEX_Y + TEN_DROP, TEN_SCALE, color) + glyph(
            "0", INDEX_X + TEN_KERN * TEN_SCALE, INDEX_Y + TEN_DROP, TEN_SCALE, color
        )
    else:
        t = glyph(label, INDEX_X, INDEX_Y, INDEX_SCALE, color)
    if underline:
        # Tucked under the digit rather than floating below it. It used to sit 32 units
        # clear of the glyph and 10 off the emblem underneath, which is the wrong way
        # round for a mark whose whole job is to belong to the 6 or the 9 above it.
        k = INDEX_SCALE
        t += (
            f'<rect x="{INDEX_X + 8 * k:.0f}" y="{INDEX_Y + 138 * k:.0f}" '
            f'width="{85 * k:.0f}" height="{13 * k:.0f}" rx="{6 * k:.0f}" fill="{color}"/>'
        )
    return t


def joker_index():
    """The Joker's corner mark is its cap rather than a letter, and it grows with the
    indices — but not as far, because a silhouette carries at a size a stroke does not
    and the emblem below it is already the widest on the deck."""
    return jester_cap(168, 178, s=0.72)


def frame(label, emblem, underline=False, joker=False, accent=None, bg=PAPER, edge=False):
    """One card: a ground, two indices and an emblem, and as little else as possible.

    `edge` draws the one ink rule, and only the plain numbers ask for it — their ground is
    white, so without it the card has no edge at all. A tinted card needs none: the ground
    is the edge. There used to be two rules on every card, an ink one and a thinner accent
    one inside it, which on a white 3 read as a double border and on a coloured card was a
    frame drawn around a frame."""
    corner = joker_index() if joker else index_glyph(label, underline, color=accent or INK)
    mirrored = f'<g transform="rotate(180 {W / 2} {H / 2})">{corner}</g>'
    rule = (
        f'<rect x="26" y="26" width="{W - 52}" height="{H - 52}" rx="30" '
        f'fill="none" stroke="{INK}" stroke-width="6"/>'
        if edge
        else ""
    )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
        f'<rect width="{W}" height="{H}" rx="44" fill="{bg}"/>'
        f"{rule}{corner}{mirrored}{emblem}</svg>"
    )


# ---------------------------------------------------------------- the faces

PIP_LAYOUTS = {
    2: [(CX, 400), (CX, 780)],
    3: [(CX, 375), (CX, 590), (CX, 805)],
    4: [(300, 400), (525, 400), (300, 780), (525, 780)],
    5: [(300, 400), (525, 400), (CX, 590), (300, 780), (525, 780)],
    6: [(300, 375), (525, 375), (300, 590), (525, 590), (300, 805), (525, 805)],
}


def face_number(n):
    """2-6: pip cards whose pips are the game's own object, each rank its own colour."""
    return "".join(pip_card(x, y, fill=ACCENT[str(n)]) for x, y in PIP_LAYOUTS[n])


def popped(cx, cy, w, h, fill, stroke, halo):
    """The one card the action is about, with a glow of the rank's accent behind it."""
    return (
        f'<rect x="{cx - w / 2 - 13:.0f}" y="{cy - h / 2 - 13:.0f}" width="{w + 26}" '
        f'height="{h + 26}" rx="22" fill="{halo}" opacity="0.45"/>'
        + card_shape(cx, cy, w, h, fill=fill, stroke=stroke, sw=10)
    )


def lens(cx, cy, r, color, sw=16, angle=128, reach=165):
    """The spying glyph for peeking at THEIR card — a tool, not an eye.

    The glass is empty rather than tinted. It is drawn over the card it is looking at,
    and a wash across that card was the difference between reading their card through a
    lens and reading a smudge. The handle leans down-left for one reason: down-right is
    where the second index lives."""
    a = math.radians(angle)
    return (
        f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{color}" '
        f'stroke-width="{sw}"/>'
        f'<line x1="{cx + r * math.cos(a):.0f}" y1="{cy + r * math.sin(a):.0f}" '
        f'x2="{cx + (r + reach) * math.cos(a):.0f}" '
        f'y2="{cy + (r + reach) * math.sin(a):.0f}" '
        f'stroke="{color}" stroke-width="{sw + 12}" stroke-linecap="round"/>'
    )


def hand_row(cy, fill, stroke, gap=None, n=3, w=150, h=205, step=172):
    """One seat's row of face-down cards. Colour is the whole ownership legend — felt
    green under a gold border is yours, blue is theirs — so a row needs no chevron and
    no label to say whose hand it is."""
    return "".join(
        card_shape(CX + (i - (n - 1) / 2) * step, cy, w, h, fill=fill, stroke=stroke)
        for i in range(n)
        if i != gap
    )


def peek_own(accent):
    """The 7's composition, shared verbatim by the 8: a card stands up out of YOUR row
    along the bottom, and one large eye looks down at it.

    Only your row is drawn, and the absence is the point — there is no opponent on this
    card because the action cannot reach one. It replaced a four-seat table that carried
    the same fact and charged the emblem everything to carry it: at the 96 px a side seat
    actually renders at, four busts and twelve cards are a texture, and the eye that says
    what the card DOES was a fifth of the width and lost inside it."""
    return (
        hand_row(734, FELT, GOLD, gap=1, w=140, h=192, step=162)
        + popped(CX, 616, 200, 274, FELT, GOLD, accent)
        + big_eye(CX, 340, s=1.15, gaze=16, iris=accent)
    )


def peek_them(accent):
    """The 9's composition, shared verbatim by the 10, and the 7/8 turned over: THEIR row
    sits at the top in blue, one of their cards drops out of it, and the magnifier — a
    tool you point at somebody else, where an eye is simply yours — closes over it.

    The two silhouettes are opposites deliberately. Mass low under a wide flat eye is one
    of mine; mass high over a round lens is one of theirs. Colour says it a third time, so
    none of the three cues is carrying it alone — which is the whole point of saying it
    three ways to somebody who cannot use one of them."""
    return (
        hand_row(418, BLUE, BLUE_EDGE, gap=1, w=140, h=192, step=162)
        + card_shape(CX, 670, 190, 260, fill=BLUE, stroke=BLUE_EDGE)
        + lens(CX, 670, 158, accent, sw=26)
    )


# The two arcs are one circle, and they are not the same length. They look as though they
# should be: rotate the top one by 180 degrees and you get the bottom one's angles exactly.
# The CARDS underneath are not point-symmetric though — they are mirrored, tilted -13 and
# +13 — so their top outer corners sit 291 units from the emblem's centre where the bottom
# ones sit 265. Against a 295 circle that is the difference between an arc clearing the
# cards by 21 and an arc cutting 4 units into them, which is what the top one was doing.
# Measured both ways, and the top arc is shortened by exactly enough to match the bottom's
# clearance rather than by eye.
TOP_ARC = (217, 323)
BOTTOM_ARC = (28, 152)


def swap_arrows(color, dashed=False):
    """The rotation the Jack and the Queen share: two arcs of one circle around the pair."""
    return arc_arrow(
        CX, 600, 295, *TOP_ARC, dashed=dashed, sw=17, color=color
    ) + arc_arrow(CX, 600, 295, *BOTTOM_ARC, dashed=dashed, sw=17, color=color)


def swap_pair():
    """One big card of yours and one of theirs, crossed — the two-player trade."""
    return card_shape(322, 600, 255, 350, rot=-13, fill=FELT, stroke=GOLD) + card_shape(
        502, 600, 255, 350, rot=13, fill=BLUE, stroke=BLUE_EDGE
    )


def face_jack():
    """The blind swap: the pair trades inside a violet circle of arrows, and the
    slashed eye — the same glyph every password field uses — says nobody looks.
    A slashed eye upside down is still a slashed eye."""
    violet = ACCENT["j"]
    arrows = swap_arrows(violet)
    # A pale bar in a dark casing, and that way round for a reason: the slash crosses two
    # dark cards and one white eye, so whichever colour is the core, the casing has to be
    # the one that reads against the other. It used to be a cream casing around an INK
    # core — which put the visible half of the line dark on dark green and dark on blue
    # for all but the 80 units of it that cross the eye.
    slash = "".join(
        f'<line x1="{CX - 108}" y1="702" x2="{CX + 108}" y2="486" stroke="{color}" '
        f'stroke-width="{width}" stroke-linecap="round"/>'
        for color, width in ((INK, 38), (PAPER, 20))
    )
    return swap_pair() + big_eye(CX, 594, s=0.82, iris=violet) + slash + arrows


def face_queen():
    """The Queen peeks TWO cards, so she has two eyes — one opened on each card of the
    pair — and her arrows stay dashed: the trade is hers to decline. The eyes are lashed,
    which is the one thing on the deck drawn as a person rather than as a diagram, and
    the only thing separating her emblem from the Jack's beyond an open lid."""
    plum = ACCENT["q"]
    arrows = swap_arrows(plum, dashed=True)
    eyes = group(-13, 322, 600, lashed_eye(322, 600, s=0.52, iris=plum, side=-1)) + group(
        13, 502, 600, lashed_eye(502, 600, s=0.52, iris=plum, side=1)
    )
    return swap_pair() + eyes + arrows


def face_king():
    """The crowned oracle: the crown at the center, its rays still reaching a card in
    every corner — greens and blues placed so a 180° turn keeps the reading true, and
    the four pulled in off the ends of the card so the bigger indices keep their two —
    measured, not judged: the top-left card came within 5 units of the K above it."""
    gold = ACCENT["k"]
    cards = (
        card_shape(238, 382, 118, 162, rot=-8, fill=FELT, stroke=GOLD)
        + card_shape(587, 382, 118, 162, rot=8, fill=BLUE, stroke=BLUE_EDGE)
        + card_shape(238, 743, 118, 162, rot=8, fill=BLUE, stroke=BLUE_EDGE)
        + card_shape(587, 743, 118, 162, rot=-8, fill=FELT, stroke=GOLD)
    )
    rays = (
        ray(330, 470, 288, 428, color=gold)
        + ray(495, 470, 537, 428, color=gold)
        + ray(330, 655, 288, 697, color=gold)
        + ray(495, 655, 537, 697, color=gold)
    )
    return rays + crown(CX, 530, s=1.5, color=gold) + cards


def face_ace():
    """The throw: a card hurled from the deck stack across the table at an
    opponent. Silhouette: a big red diagonal with a tilted card riding it."""
    red = PENALTY
    target = bust(600, 300, s=1.1) + card_shape(600, 402, 96, 132, fill=BLUE, stroke=BLUE_EDGE)
    stack = "".join(
        card_shape(250 + dx, 800 + dy, 180, 246, fill="#EAD9A6", stroke=GOLD_DARK, sw=7)
        for dx, dy in ((18, 22), (9, 11), (0, 0))
    )
    ang = math.atan2(430 - 700, 560 - 320)
    throw = (
        f'<line x1="320" y1="700" x2="545" y2="447" stroke="{red}" stroke-width="16" '
        f'stroke-linecap="round"/>' + arrowhead(560, 430, ang, size=36, color=red)
    )
    streaks = "".join(
        f'<line x1="{x}" y1="{y}" x2="{x + 34}" y2="{y - 38}" stroke="{red}" '
        f'stroke-width="8" stroke-linecap="round" opacity="{op}"/>'
        for x, y, op in ((300, 640, 0.7), (352, 700, 0.5))
    )
    flying = card_shape(455, 545, 180, 246, rot=-24, fill="#EAD9A6", stroke=GOLD_DARK)
    return target + throw + streaks + stack + flying


def face_joker():
    """The fool's cap over its own name.

    The cap was drawn at s=2.9 — 930 units wide on an 825-wide card — so both its outer
    horns and their bells were cropped off the edges, and what was left read as a tent.
    It fits now. The name is spelled out underneath because the Joker is the one rank
    with no numeral to enlarge: every other card got a bigger index out of this pass and
    this one had only a silhouette, which is fine on the felt and no help at all to
    somebody meeting the deck for the first time in the help sheet."""
    return jester_cap(CX, 470, s=2.05) + word("JOKER", CX, 700, 0.92, JOKER_INK)


def card_back():
    """A light back: the V in a diamond medallion, ringed by eight large engraved marks
    in the deck's four colours, point-symmetric so the back has no upside down.

    Light rather than felt green, which is what the brief asked for, because the back is
    dealt onto green felt and `check_contrast` holds every ground to 3:1 against it — a
    green back would be a card you cannot see the edge of."""
    # Four engraved marks, one per deck colour, mirrored to eight. They were six kinds
    # in thirty copies at a fifth of this size, which is not a pattern at the size a card
    # is dealt — it is a speckle, unreadable at 330 px and dirt at 96. Kept rather than
    # dropped: they are the deck's own element-stone motif and the only place the four
    # colours appear together. Bigger, and far fewer, is the whole fix.
    colors = ENGRAVED

    def mark(kind, x, y, c):
        if kind == "bar":
            return f'<rect x="{x - 62}" y="{y - 14}" width="124" height="28" rx="14" fill="{c}"/>'
        if kind == "dots":
            return "".join(
                f'<circle cx="{x + dx}" cy="{y}" r="19" fill="{c}"/>' for dx in (-52, 0, 52)
            )
        if kind == "chev":
            return (
                f'<polyline points="{x - 57},{y + 24} {x},{y - 28} {x + 57},{y + 24}" '
                f'fill="none" stroke="{c}" stroke-width="21" stroke-linecap="round" '
                f'stroke-linejoin="round"/>'
            )
        return f'<circle cx="{x}" cy="{y}" r="30" fill="none" stroke="{c}" stroke-width="17"/>'

    kinds = ["bar", "dots", "chev", "ring"]
    spots = ((104, 250), (721, 250), (CX_INT, 132), (104, 562))
    deco = "".join(mark(kinds[i], x, y, colors[i]) for i, (x, y) in enumerate(spots))
    deco += f'<g transform="rotate(180 {W / 2} {H / 2})">{deco}</g>'

    def diamond(rw, rh, stroke, sw):
        return (
            f'<path d="M {CX},{H / 2 - rh} L {CX + rw},{H / 2} L {CX},{H / 2 + rh} '
            f'L {CX - rw},{H / 2} Z" fill="none" stroke="{stroke}" stroke-width="{sw}"/>'
        )

    v = (
        f'<path d="M {CX - 68},{H / 2 - 78} L {CX - 28},{H / 2 - 78} L {CX},{H / 2 + 18} '
        f'L {CX + 28},{H / 2 - 78} L {CX + 68},{H / 2 - 78} L {CX + 24},{H / 2 + 82} '
        f'L {CX - 24},{H / 2 + 82} Z" fill="{ORANGE}" stroke="{INK}" stroke-width="7" '
        f'stroke-linejoin="round"/>'
    )
    pips = "".join(
        f'<path d="M {CX + dx},{H / 2 + dy - 12} L {CX + dx + 10},{H / 2 + dy} '
        f'L {CX + dx},{H / 2 + dy + 12} L {CX + dx - 10},{H / 2 + dy} Z" fill="{GOLD}"/>'
        for dx, dy in ((0, -246), (0, 246), (-212, 0), (212, 0))
    )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
        f'<rect width="{W}" height="{H}" rx="44" fill="{PAPER}"/>'
        f'<rect x="20" y="20" width="{W - 40}" height="{H - 40}" rx="32" '
        f'fill="none" stroke="{INK}" stroke-width="6"/>'
        f'<rect x="38" y="38" width="{W - 76}" height="{H - 76}" rx="26" '
        f'fill="none" stroke="{GOLD}" stroke-width="4"/>'
        + deco
        + diamond(240, 300, INK, 6)
        + diamond(200, 252, GOLD, 8)
        + pips
        + v
        + "</svg>"
    )


# ---------------------------------------------------------------- output

FACES = {
    "card_2": frame("2", face_number(2), accent=ACCENT["2"], bg=WHITE, edge=True),
    "card_3": frame("3", face_number(3), accent=ACCENT["3"], bg=WHITE, edge=True),
    "card_4": frame("4", face_number(4), accent=ACCENT["4"], bg=WHITE, edge=True),
    "card_5": frame("5", face_number(5), accent=ACCENT["5"], bg=WHITE, edge=True),
    "card_6": frame("6", face_number(6), underline=True, accent=ACCENT["6"], bg=WHITE, edge=True),
    "card_7": frame("7", peek_own(ACCENT["7"]), accent=ACCENT["7"], bg=BG["7"]),
    "card_8": frame("8", peek_own(ACCENT["8"]), accent=ACCENT["8"], bg=BG["8"]),
    "card_9": frame("9", peek_them(ACCENT["9"]), underline=True, accent=ACCENT["9"], bg=BG["9"]),
    "card_10": frame("10", peek_them(ACCENT["10"]), accent=ACCENT["10"], bg=BG["10"]),
    "card_j": frame("J", face_jack(), accent=ACCENT["j"], bg=BG["j"]),
    "card_q": frame("Q", face_queen(), accent=ACCENT["q"], bg=BG["q"]),
    "card_k": frame("K", face_king(), accent=ACCENT["k"], bg=BG["k"]),
    "card_a": frame("A", face_ace(), accent=ACCENT["a"], bg=BG["a"]),
    "card_joker": frame("", face_joker(), joker=True, accent=ORANGE, bg=BG["joker"]),
    "card_back": card_back(),
}


# ---------------------------------------------------------------- vector drawables

APP_DRAWABLE = pathlib.Path(__file__).parent.parent / (
    "composeApp/src/commonMain/composeResources/drawable"
)


def _rect_path(a):
    x, y = float(a.get("x", 0)), float(a.get("y", 0))
    w, h = float(a["width"]), float(a["height"])
    r = min(float(a.get("rx", 0)), w / 2, h / 2)
    if r <= 0:
        return f"M{x},{y} L{x + w},{y} L{x + w},{y + h} L{x},{y + h} Z"
    return (
        f"M{x + r},{y} L{x + w - r},{y} A{r},{r} 0 0 1 {x + w},{y + r} "
        f"L{x + w},{y + h - r} A{r},{r} 0 0 1 {x + w - r},{y + h} "
        f"L{x + r},{y + h} A{r},{r} 0 0 1 {x},{y + h - r} "
        f"L{x},{y + r} A{r},{r} 0 0 1 {x + r},{y} Z"
    )


def _shape_path(el, tag):
    a = el.attrib
    if tag == "rect":
        return _rect_path(a)
    if tag == "circle":
        cx, cy, r = float(a["cx"]), float(a["cy"]), float(a["r"])
        return (
            f"M{cx - r},{cy} A{r},{r} 0 1 1 {cx + r},{cy} A{r},{r} 0 1 1 {cx - r},{cy} Z"
        )
    if tag == "line":
        return f'M{a["x1"]},{a["y1"]} L{a["x2"]},{a["y2"]}'
    if tag in ("polygon", "polyline"):
        pts = a["points"].split()
        d = "M" + " L".join(pts)
        return d + " Z" if tag == "polygon" else d
    if tag == "path":
        return a["d"]
    raise SystemExit(f"vector-drawable emitter: unsupported element <{tag}>")


def _paint(el, tag):
    a = el.attrib
    out = []
    fill = a.get("fill")
    if fill is None and tag in ("line", "polyline"):
        fill = "none"
    if fill is None:
        raise SystemExit(f"<{tag}> without explicit fill")
    opacity = float(a.get("opacity", 1))
    if fill != "none":
        out.append(f'android:fillColor="{fill}"')
        if opacity < 1:
            out.append(f'android:fillAlpha="{opacity}"')
    stroke = a.get("stroke")
    if stroke and stroke != "none":
        out.append(f'android:strokeColor="{stroke}"')
        out.append(f'android:strokeWidth="{a.get("stroke-width", "1")}"')
        if opacity < 1:
            out.append(f'android:strokeAlpha="{opacity}"')
        cap = a.get("stroke-linecap")
        if cap:
            out.append(f'android:strokeLineCap="{cap}"')
        join = a.get("stroke-linejoin")
        if join:
            out.append(f'android:strokeLineJoin="{join}"')
    return out


def _group_attrs(transform):
    import re as _re

    m = _re.fullmatch(r"rotate\(([-\d.]+) ([\d.]+) ([\d.]+)\)", transform)
    if m:
        return (
            f'android:rotation="{m.group(1)}" android:pivotX="{m.group(2)}" '
            f'android:pivotY="{m.group(3)}"'
        )
    m = _re.fullmatch(r"translate\(([-\d.]+) ([-\d.]+)\) rotate\(([-\d.]+)\)", transform)
    if m:
        return (
            f'android:translateX="{m.group(1)}" android:translateY="{m.group(2)}" '
            f'android:rotation="{m.group(3)}"'
        )
    m = _re.fullmatch(r"translate\(([-\d.]+) ([-\d.]+)\) scale\(([\d.]+)\)", transform)
    if m:
        return (
            f'android:translateX="{m.group(1)}" android:translateY="{m.group(2)}" '
            f'android:scaleX="{m.group(3)}" android:scaleY="{m.group(3)}"'
        )
    raise SystemExit(f"vector-drawable emitter: unsupported transform {transform!r}")


def svg_to_vector_drawable(svg):
    """Translate the generator's own SVG subset into an Android vector drawable.
    Not a general converter: it refuses anything it does not know, so a new
    SVG feature fails the build here rather than rendering wrong in the app."""
    import xml.etree.ElementTree as ET

    root = ET.fromstring(svg.replace('xmlns="http://www.w3.org/2000/svg"', ""))
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{W}dp" android:height="{H}dp"',
        f'    android:viewportWidth="{W}" android:viewportHeight="{H}">',
    ]

    def walk(el, depth):
        pad = "    " * depth
        for child in el:
            tag = child.tag
            if tag == "g":
                lines.append(f'{pad}<group {_group_attrs(child.attrib["transform"])}>')
                walk(child, depth + 1)
                lines.append(f"{pad}</group>")
                continue
            paint = _paint(child, tag)
            d = _shape_path(child, tag)
            lines.append(f'{pad}<path android:pathData="{d}" ' + " ".join(paint) + "/>")

    walk(root, 1)
    lines.append("</vector>")
    return "\n".join(lines)


def preview_html():
    def cell(name, height):
        return (
            f'<figure style="margin:0;text-align:center">'
            f'<img src="{name}.svg" style="height:{height}px;border-radius:6px;'
            f'box-shadow:0 4px 14px rgba(0,0,0,.35)"/>'
            f'<figcaption style="color:#aaa;font:12px sans-serif;margin-top:6px">{name}</figcaption>'
            f"</figure>"
        )

    big = "".join(cell(n, 320) for n in FACES)
    small = "".join(cell(n, 120) for n in FACES)
    return (
        "<!doctype html><meta charset='utf-8'><title>Vinto deck preview</title>"
        "<body style='margin:0;background:#0E3428;padding:24px'>"
        "<h2 style='color:#F7F5EF;font-family:Georgia,serif'>Full size</h2>"
        f"<div style='display:flex;flex-wrap:wrap;gap:18px'>{big}</div>"
        "<h2 style='color:#F7F5EF;font-family:Georgia,serif;margin-top:36px'>"
        "Thumbnail (the crowded-table test)</h2>"
        f"<div style='display:flex;flex-wrap:wrap;gap:10px'>{small}</div>"
        "</body>"
    )


def _lin(c):
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4


def relative_luminance(hex_color):
    r, g, b = (_lin(int(hex_color[i:i + 2], 16) / 255) for i in (1, 3, 5))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    hi, lo = sorted((relative_luminance(a), relative_luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def lab(hex_color):
    r, g, b = (relative_luminance.__globals__["_lin"](int(hex_color[i:i + 2], 16) / 255)
               for i in (1, 3, 5))
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883

    def f(t):
        return t ** (1 / 3) if t > 216 / 24389 else (841 / 108) * t + 4 / 29

    fx, fy, fz = f(x), f(y), f(z)
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))


def delta_e(a, b):
    return math.dist(lab(a), lab(b))


# Viénot's linear dichromat simulation. Approximate, and the right kind of approximate:
# it answers "do these two still differ" rather than "what exactly does he see".
_RGB2LMS = ((17.8824, 43.5161, 4.11935), (3.45565, 27.1554, 3.86714),
            (0.0299566, 0.184309, 1.46709))
_LMS2RGB = ((0.080944, -0.130504, 0.116721), (-0.0102485, 0.0540194, -0.113615),
            (-0.000365294, -0.00412163, 0.693513))
_DICHROMAT = {
    "deuteranope": ((1, 0, 0), (0.494207, 0, 1.24827), (0, 0, 1)),
    "protanope": ((0, 2.02344, -2.52581), (0, 1, 0), (0, 0, 1)),
}


def _apply(matrix, vector):
    return [sum(m * v for m, v in zip(row, vector)) for row in matrix]


def simulate(hex_color, kind):
    lin = relative_luminance.__globals__["_lin"]
    v = [lin(int(hex_color[i:i + 2], 16) / 255) for i in (1, 3, 5)]
    out = _apply(_LMS2RGB, _apply(_DICHROMAT[kind], _apply(_RGB2LMS, v)))

    def gamma(c):
        return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055

    return "#" + "".join(f"{max(0, min(255, round(gamma(c) * 255))):02X}" for c in out)


# Two cards that do the same thing still have to be two cards. The WCAG gate below says
# nothing about this — every ground passed it while 9 and 10 were dE 7.7 apart — so the
# distance is held here, in lightness as well as hue, and with the hue taken away.
SIBLINGS = (("7", "8"), ("9", "10"), ("j", "q"))


def check_separation():
    problems = []
    for a, b in SIBLINGS:
        ga, gb = BG[a], BG[b]
        gap = delta_e(ga, gb)
        lightness = abs(lab(ga)[0] - lab(gb)[0])
        if gap < 24:
            problems.append(f"{a}/{b} grounds only dE {gap:.1f} apart")
        if lightness < 12:
            problems.append(f"{a}/{b} differ by L* {lightness:.1f} — hue is doing it alone")
        for kind in _DICHROMAT:
            seen = delta_e(simulate(ga, kind), simulate(gb, kind))
            if seen < 18:
                problems.append(f"{a}/{b} collapse to dE {seen:.1f} for a {kind}")
    grounds = list(BG.items()) + [("numbers", WHITE)]
    for i, (ra, ca) in enumerate(grounds):
        for rb, cb in grounds[i + 1:]:
            if delta_e(ca, cb) < 12:
                problems.append(f"{ra} and {rb} grounds are dE {delta_e(ca, cb):.1f} apart")
    if problems:
        raise SystemExit("card separation gate failed:\n  " + "\n  ".join(problems))


# What each emblem paints, and on which ground. A shape is perceivable when its fill OR
# its outline clears 3:1 (WCAG 1.4.11) — the outline is why every emblem card has an ink
# one, and this is the check that says so out loud.
def check_emblem_ink():
    yours, theirs = (FELT, INK), (BLUE, INK)
    eye, deck = (WHITE, INK), ("#EAD9A6", INK)
    painted = {
        "7": (yours, eye), "8": (yours, eye), "9": (theirs,), "10": (theirs,),
        "j": (yours, theirs, eye), "q": (yours, theirs, eye),
        "k": (yours, theirs, ("none", ACCENT["k"])),
        "a": (deck, theirs, (INK, PALE), ("none", PENALTY)),
        "joker": ((ORANGE, INK), (JOKER_INK, "none")),
        # the back is cream, and its marks carry no outline at all
        "back": ((ORANGE, INK),) + tuple((c, "none") for c in ENGRAVED),
    }
    problems = []
    for rank, shapes in painted.items():
        ground = PAPER if rank == "back" else BG[rank]
        for fill, stroke in shapes:
            best = max(contrast(c, ground) for c in (fill, stroke) if c != "none")
            if best < 3.0:
                problems.append(f"{rank}: a {fill}/{stroke} shape is {best:.2f} on {ground}")
    # The Jack's slash is the one shape drawn over other shapes rather than over a ground,
    # so it is checked against each of the three it crosses. Neither of its two colours
    # reads on all three alone — which is the whole reason it is a cased line.
    for crossed in (FELT, BLUE, WHITE):
        if max(contrast(PAPER, crossed), contrast(INK, crossed)) < 3.0:
            problems.append(f"the Jack's slash cannot be seen over {crossed}")
    if problems:
        raise SystemExit("emblem ink gate failed:\n  " + "\n  ".join(problems))


def check_contrast():
    """WCAG gate: indices need 4.5:1 on their grounds (large text would allow 3, but the
    indices are the one thing that must always read); card grounds need 3:1 against the
    felt they sit on in the app.

    Against the light theme's Paper surface — the help sheet and the lesson — no ground
    reaches 3:1, the white number cards included, and that is not held here. It cannot
    usefully be: the ratio is luminance only, and what separates a pale green card from
    cream is hue, which the formula cannot see and the eye can. The plain numbers keep an
    ink rule because white on cream really is nothing; the tinted ones are checked by
    looking at them."""
    problems = []
    for rank, accent in ACCENT.items():
        bg = BG.get(rank, WHITE)
        if contrast(accent, bg) < 4.5:
            problems.append(f"index {rank}: {contrast(accent, bg):.2f} on {bg}")
    for rank, bg in {**BG, "numbers": WHITE}.items():
        for felt in (FELT, FELT_DARK):
            if contrast(bg, felt) < 3.0:
                problems.append(f"ground {rank} vs {felt}: {contrast(bg, felt):.2f}")
    if problems:
        raise SystemExit("WCAG contrast gate failed:\n  " + "\n  ".join(problems))


def main():
    check_contrast()
    check_separation()
    check_emblem_ink()
    OUT.mkdir(exist_ok=True)
    for name, svg in FACES.items():
        (OUT / f"{name}.svg").write_text(svg)
    (OUT / "preview.html").write_text(preview_html())
    print(f"wrote {len(FACES)} faces + preview.html to {OUT}")
    if APP_DRAWABLE.is_dir():
        for name, svg in FACES.items():
            (APP_DRAWABLE / f"{name}.xml").write_text(svg_to_vector_drawable(svg))
        print(f"wrote {len(FACES)} vector drawables to {APP_DRAWABLE}")


if __name__ == "__main__":
    main()
