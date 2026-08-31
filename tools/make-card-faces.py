#!/usr/bin/env python3
"""Generate the meaning-based card faces as SVG.

Design (docs/design/CARD-IMAGERY.md): each rank has its own tinted ground and accent,
and ownership is a colour that needs no legend: felt green with a gold border is your
card, blue is an opponent's, pale gold the deck's. The four peeks (7-10) sit on the
four-player table scene — seats, hands, and the examined card popping out — with the
board TRANSPARENT (gold outline only) so each rank's tint carries the face. 7/8 use the
eye (yours), 9/10 the lens (theirs), sized differently within each pair. J trades the
pair under a slashed eye, Q under two open eyes with dashed arrows, K reads a crystal
ball raying the corner cards, A throws a deck card at an opponent, Joker is the cap.
Every face keeps standard corner indices (bottom-right rotated, 6 and 9 underlined).

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
BLUE_EDGE = "#DCE9F5"

# One accent per rank family, dark enough to hold as a corner index on cream.
ACCENT = {
    "2": "#17766B",   # teal
    "3": "#2F5E8C",   # blue
    "4": "#4F5AA8",   # indigo
    "5": "#B03A57",   # raspberry
    "6": "#1B5E43",   # the brand green
    "7": "#1E6B4B",   # deep green — the 7 peeks your own green card
    "8": "#94430D",   # burnt orange — a sibling, not a twin; 4.5:1-safe
    "9": "#256D85",   # steel cyan
    "10": "#3D4EA0",  # indigo — a sibling, not a twin
    "j": "#7C3AA0",   # violet
    "q": "#A23B72",   # plum
    "k": "#7E5C11",   # deep gold, darkened for 4.5:1 on its ground
    "a": "#9E2B25",   # red
}

# Tinted grounds for the action cards — colour mass tells them apart across a table.
# The numbers keep the plain cream: they already read perfectly.
BG = {
    "7": "#DBEEDC",   # light green — your-card peek
    "8": "#FAD5A5",   # orange — the other your-card peek
    "9": "#DCEBF2",   # cyan
    "10": "#DEE2F5",  # periwinkle
    "j": "#EBDFF4",   # lilac
    "q": "#F6DFEB",   # pink
    "k": "#F3E5A8",   # the one strong gold — royalty owns it
    "a": "#F7CEC7",   # salmon red
    "joker": "#EBEBEB",  # neutral gray: the wild card belongs to no family
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
    """A game card: green/gold yours, blue theirs, cream the deck's."""
    body = (
        f'<rect x="{cx - w / 2:.0f}" y="{cy - h / 2:.0f}" width="{w:.0f}" height="{h:.0f}" '
        f'rx="{w * 0.09:.0f}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'
        f'<rect x="{cx - w / 2 + w * 0.07:.0f}" y="{cy - h / 2 + w * 0.07:.0f}" '
        f'width="{w * 0.86:.0f}" height="{h - w * 0.14:.0f}" rx="{w * 0.06:.0f}" '
        f'fill="none" stroke="{PAPER}" stroke-width="3" opacity="0.35"/>'
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


def star(cx, cy, outer, inner, n=8, color=GOLD):
    pts = []
    for i in range(n * 2):
        r = outer if i % 2 == 0 else inner
        a = math.pi * i / n - math.pi / 2
        pts.append(f"{cx + r * math.cos(a):.0f},{cy + r * math.sin(a):.0f}")
    return f'<polygon points="{" ".join(pts)}" fill="{color}"/>'


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


def crown(cx, cy, s=1.0):
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
        f'stroke="{GOLD}" stroke-width="{7 * s:.0f}"/>'
        for x, y in ((cx - w, cy - h * 0.42), (cx, cy - h * 0.78), (cx + w, cy - h * 0.42))
    )
    return (
        f'<path d="{d}" fill="none" stroke="{GOLD}" stroke-width="{13 * s:.0f}" '
        f'stroke-linejoin="round"/>'
        f'<line x1="{cx - w}" y1="{cy + h * 0.74}" x2="{cx + w}" y2="{cy + h * 0.74}" '
        f'stroke="{GOLD}" stroke-width="{13 * s:.0f}" stroke-linecap="round"/>' + dots
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
    "J": ["M68 8 L68 88 Q68 116 44 116 Q22 116 16 96"],
    "Q": ["M50 8 Q15 8 15 62 Q15 116 50 116 Q85 116 85 62 Q85 8 50 8", "M60 94 L88 124"],
    "K": ["M22 8 L22 116", "M82 8 L24 66", "M44 50 L86 116"],
    "A": ["M12 116 L50 8 L88 116", "M27 82 L73 82"],
}


def glyph(ch, x, y, scale, color):
    body = "".join(
        f'<path d="{d}" fill="none" stroke="{color}" stroke-width="17" '
        f'stroke-linecap="round" stroke-linejoin="round"/>'
        for d in GLYPHS[ch]
    )
    return f'<g transform="translate({x} {y}) scale({scale})">{body}</g>'


def index_glyph(label, underline, color=INK):
    if label == "10":
        t = glyph("1", 44, 64, 0.72, color) + glyph("0", 106, 64, 0.72, color)
    else:
        t = glyph(label, 64, 54, 0.95, color)
    if underline:
        t += f'<rect x="70" y="196" width="80" height="13" rx="6" fill="{color}"/>'
    return t


def joker_index():
    return jester_cap(120, 148, s=0.5)


def frame(label, emblem, underline=False, joker=False, accent=None, bg=PAPER):
    corner = joker_index() if joker else index_glyph(label, underline, color=accent or INK)
    mirrored = f'<g transform="rotate(180 {W / 2} {H / 2})">{corner}</g>'
    inner = (
        f'<rect x="38" y="38" width="{W - 76}" height="{H - 76}" rx="26" '
        f'fill="none" stroke="{accent}" stroke-width="5"/>'
        if accent
        else ""
    )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
        f'<rect width="{W}" height="{H}" rx="44" fill="{bg}"/>'
        f'<rect x="20" y="20" width="{W - 40}" height="{H - 40}" rx="32" '
        f'fill="none" stroke="{INK}" stroke-width="6"/>'
        f"{inner}{corner}{mirrored}{emblem}</svg>"
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


# --- the table scene the four peeks share -----------------------------------


def table_felt():
    """The board: the four-player table, as the app itself draws it."""
    return (
        f'<rect x="140" y="290" width="545" height="600" rx="130" fill="none" '
        f'stroke="{GOLD}" stroke-width="8"/>'
        f'<rect x="162" y="312" width="501" height="556" rx="112" fill="none" '
        f'stroke="{GOLD}" stroke-width="3" opacity="0.4"/>'
    )


def opponent_seat(cx, cy, rot=0.0, gap=None):
    """A bust with three small blue cards in front of it, facing the center."""
    seat_bust = (
        f'<circle cx="0" cy="-66" r="27" fill="{INK}" stroke="{PALE}" stroke-width="4"/>'
        f'<path d="M -58,-2 Q -52,-48 0,-52 Q 52,-48 58,-2 Z" '
        f'fill="{INK}" stroke="{PALE}" stroke-width="4"/>'
    )
    cards = "".join(
        f'<rect x="{(i - 1) * 64 - 27}" y="14" width="54" height="74" rx="8" '
        f'fill="{BLUE}" stroke="{BLUE_EDGE}" stroke-width="4"/>'
        for i in range(3)
        if i != gap
    )
    return f'<g transform="translate({cx} {cy}) rotate({rot})">{seat_bust}{cards}</g>'


def your_hand(gap=None):
    """Your three green cards at the bottom edge, larger — the seat the app gives you."""
    return "".join(
        f'<rect x="{CX + (i - 1) * 116 - 50:.0f}" y="772" width="100" height="138" rx="12" '
        f'fill="{FELT}" stroke="{GOLD}" stroke-width="7"/>'
        for i in range(3)
        if i != gap
    )


def board(top_gap=None, my_gap=None):
    return (
        table_felt()
        + opponent_seat(CX, 384, gap=top_gap)
        + opponent_seat(224, 590, rot=90)
        + opponent_seat(601, 590, rot=-90)
        + your_hand(gap=my_gap)
    )


def trail(x1, y1, x2, y2, hw=36, color=GOLD):
    """A streak from a seat's gap to the card pulled out of it: whose card this is."""
    return (
        f'<polygon points="{x1 - hw},{y1} {x1 + hw},{y1} {x2 + 12},{y2} {x2 - 12},{y2}" '
        f'fill="{color}" opacity="0.35"/>'
    )


def popped(cx, cy, w, h, fill, stroke, halo):
    return (
        f'<rect x="{cx - w / 2 - 11:.0f}" y="{cy - h / 2 - 11:.0f}" width="{w + 22}" '
        f'height="{h + 22}" rx="20" fill="{halo}" opacity="0.4"/>'
        f'<rect x="{cx - w / 2:.0f}" y="{cy - h / 2:.0f}" width="{w}" height="{h}" rx="13" '
        f'fill="{fill}" stroke="{stroke}" stroke-width="8"/>'
    )


def lens(cx, cy, r, color, handle=False, sw=16):
    """The spying glyph for peeking at THEIR card — a tool, not an eye."""
    parts = (
        f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{PAPER}" opacity="0.25"/>'
        f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{color}" stroke-width="{sw}"/>'
        f'<circle cx="{cx}" cy="{cy}" r="{r * 0.14:.0f}" fill="{color}"/>'
    )
    if handle:
        a = math.radians(52)
        parts += (
            f'<line x1="{cx + r * math.cos(a):.0f}" y1="{cy + r * math.sin(a):.0f}" '
            f'x2="{cx + (r + 150) * math.cos(a):.0f}" y2="{cy + (r + 150) * math.sin(a):.0f}" '
            f'stroke="{color}" stroke-width="{sw + 10}" stroke-linecap="round"/>'
        )
    return parts


def peek_own(accent):
    """The 7's composition, shared verbatim by the 8: your card rises from
    your hand, the eye above it. Only number, ground and accent differ."""
    return (
        board(my_gap=1)
        + trail(CX, 800, CX, 724, color=accent)
        + popped(CX, 680, 122, 168, FELT, GOLD, accent)
        + big_eye(CX, 524, s=0.55, gaze=8, iris=accent)
    )


def peek_them(accent):
    """The 9's composition, shared verbatim by the 10: their card drops from
    their hand, the magnifier on it. Only number, ground and accent differ."""
    return (
        board(top_gap=1)
        + trail(CX, 420, CX, 500, color=accent)
        + popped(CX, 560, 122, 168, BLUE, BLUE_EDGE, accent)
        + lens(CX + 4, 550, 92, accent, handle=True, sw=13)
    )



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
    arrows = arc_arrow(CX, 600, 295, 208, 332, sw=17, color=violet) + arc_arrow(
        CX, 600, 295, 28, 152, sw=17, color=violet
    )
    slash = (
        f'<line x1="{CX - 108}" y1="702" x2="{CX + 108}" y2="486" stroke="{PAPER}" '
        f'stroke-width="34" stroke-linecap="round"/>'
        f'<line x1="{CX - 108}" y1="702" x2="{CX + 108}" y2="486" stroke="{INK}" '
        f'stroke-width="16" stroke-linecap="round"/>'
    )
    return swap_pair() + big_eye(CX, 594, s=0.82, iris=violet) + slash + arrows


def face_queen():
    """The Queen peeks TWO cards, so she has two eyes — one opened on each card
    of the pair — and her arrows stay dashed: the trade is hers to decline."""
    plum = ACCENT["q"]
    arrows = arc_arrow(CX, 600, 295, 208, 332, dashed=True, sw=17, color=plum) + arc_arrow(
        CX, 600, 295, 28, 152, dashed=True, sw=17, color=plum
    )
    eyes = group(-13, 322, 600, big_eye(322, 600, s=0.5, iris=plum)) + group(
        13, 502, 600, big_eye(502, 600, s=0.5, iris=plum)
    )
    return swap_pair() + eyes + arrows


def face_king():
    """The crowned oracle: the crown restored at the center, its rays still
    reaching a card in every corner — greens and blues placed so a 180° turn
    keeps the reading true."""
    cards = (
        card_shape(210, 268, 118, 162, rot=-8, fill=FELT, stroke=GOLD)
        + card_shape(615, 268, 118, 162, rot=8, fill=BLUE, stroke=BLUE_EDGE)
        + card_shape(210, 880, 118, 162, rot=8, fill=BLUE, stroke=BLUE_EDGE)
        + card_shape(615, 880, 118, 162, rot=-8, fill=FELT, stroke=GOLD)
    )
    rays = (
        ray(295, 430, 255, 355, color=GOLD)
        + ray(530, 430, 570, 355, color=GOLD)
        + ray(295, 640, 252, 790, color=GOLD)
        + ray(530, 640, 573, 790, color=GOLD)
    )
    return rays + crown(CX, 530, s=1.5) + cards


def face_ace():
    """The throw: a card hurled from the deck stack across the table at an
    opponent. Silhouette: a big red diagonal with a tilted card riding it."""
    red = ACCENT["a"]
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
    """The fool's cap, and nothing else — the one card that needs no diagram."""
    return jester_cap(CX, 562, s=2.9)


def card_back():
    """A light back: the V in a diamond medallion, flanked by columns of small
    engraved marks in the deck's four colours — the element-stone look, and
    point-symmetric so the back has no upside down."""
    colors = [GOLD, BLUE, FELT, ORANGE]

    def mark(kind, x, y, c):
        if kind == "bar":
            return f'<rect x="{x - 26}" y="{y - 6}" width="52" height="12" rx="6" fill="{c}"/>'
        if kind == "dots":
            return "".join(
                f'<circle cx="{x + dx}" cy="{y}" r="8" fill="{c}"/>' for dx in (-22, 0, 22)
            )
        if kind == "chev":
            return (
                f'<polyline points="{x - 24},{y + 10} {x},{y - 12} {x + 24},{y + 10}" '
                f'fill="none" stroke="{c}" stroke-width="9" stroke-linecap="round" '
                f'stroke-linejoin="round"/>'
            )
        if kind == "diam":
            return (
                f'<path d="M {x},{y - 16} L {x + 14},{y} L {x},{y + 16} L {x - 14},{y} Z" '
                f'fill="{c}"/>'
            )
        if kind == "ring":
            return f'<circle cx="{x}" cy="{y}" r="13" fill="none" stroke="{c}" stroke-width="7"/>'
        return star(x, y, 17, 7, n=4, color=c)

    kinds = ["bar", "dots", "chev", "diam", "ring", "star"]
    half = []
    for col, x in enumerate((150, 675)):
        for i, y in enumerate(range(150, 540, 78)):
            k = kinds[(i + col * 3) % 6]
            half.append(mark(k, x, y, colors[(i + col) % 4]))
    for i, x in enumerate(range(CX_INT - 156, CX_INT + 157, 78)):
        half.append(mark(kinds[i % 6], x, 150, colors[(i + 2) % 4]))
    deco = "".join(half)
    deco += f'<g transform="rotate(180 {W / 2} {H / 2})">{deco}</g>'

    def diamond(rw, rh, stroke, sw):
        return (
            f'<path d="M {CX},{H / 2 - rh} L {CX + rw},{H / 2} L {CX},{H / 2 + rh} '
            f'L {CX - rw},{H / 2} Z" fill="none" stroke="{stroke}" stroke-width="{sw}"/>'
        )

    v = (
        f'<path d="M {CX - 68},{H / 2 - 78} L {CX - 28},{H / 2 - 78} L {CX},{H / 2 + 18} '
        f'L {CX + 28},{H / 2 - 78} L {CX + 68},{H / 2 - 78} L {CX + 24},{H / 2 + 82} '
        f'L {CX - 24},{H / 2 + 82} Z" fill="{ORANGE}" stroke="{GOLD}" stroke-width="7" '
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
    "card_2": frame("2", face_number(2), accent=ACCENT["2"], bg=WHITE),
    "card_3": frame("3", face_number(3), accent=ACCENT["3"], bg=WHITE),
    "card_4": frame("4", face_number(4), accent=ACCENT["4"], bg=WHITE),
    "card_5": frame("5", face_number(5), accent=ACCENT["5"], bg=WHITE),
    "card_6": frame("6", face_number(6), underline=True, accent=ACCENT["6"], bg=WHITE),
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


def relative_luminance(hex_color):
    r, g, b = (int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5))
    lin = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def contrast(a, b):
    hi, lo = sorted((relative_luminance(a), relative_luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def check_contrast():
    """WCAG gate: indices need 4.5:1 on their grounds (large text would allow 3,
    but the indices are the one thing that must always read); card grounds need
    3:1 against the felt they sit on in the app. The light theme's surface is
    covered by every card's 6px ink border (1.4.11 boundary), not by the ground."""
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
