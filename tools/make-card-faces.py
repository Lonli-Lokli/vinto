#!/usr/bin/env python3
"""Generate the meaning-based card faces as SVG.

Design (docs/design/CARD-IMAGERY.md): every action card carries one big emblem with a
UNIQUE SILHOUETTE in its family colour, so cards are distinguishable from across a table
the way real cards are — by shape and colour mass, not by reading. Ownership is a colour
that needs no legend: felt green with a gold border is your card, blue is an opponent's,
cream is the deck. There is no table background — it made every action card the same
dark blob at a distance.

Silhouettes: amber eye over a green card (7/8), steel lens ring over a blue card (9/10),
violet circle of arrows around a blindfolded pair (J), plum eye on the same pair with
dashed arrows (Q), gold crown-oracle raying three cards (K), red diagonal throw from the
deck (A), orange jester cap (Joker). Numbers are pip cards, each rank its own colour.
Every face keeps standard corner indices (bottom-right rotated, 6 and 9 underlined).

Usage:  python3 tools/make-card-faces.py
Output: tools/card-faces/*.svg and tools/card-faces/preview.html
"""

import math
import pathlib

W, H = 825, 1125
CX = W / 2

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
    "7": "#A96A00",   # amber
    "8": "#B4571E",   # burnt orange — a sibling, not a twin
    "9": "#256D85",   # steel cyan
    "10": "#3D4EA0",  # indigo — a sibling, not a twin
    "j": "#7C3AA0",   # violet
    "q": "#A23B72",   # plum
    "k": "#A8791B",   # deep gold
    "a": "#9E2B25",   # red
}

# Tinted grounds for the action cards — colour mass tells them apart across a table.
# The numbers keep the plain cream: they already read perfectly.
BG = {
    "7": "#F6E9C9",
    "8": "#F7DFC8",
    "9": "#DCEBF2",
    "10": "#DEE2F5",
    "j": "#EBDFF4",
    "q": "#F6DFEB",
    "k": "#F4E9C7",
    "a": "#F7DCD7",
    "joker": "#FBE8D2",
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
    """A circular swap arrow from angle a1 to a2 (degrees, screen coords, clockwise)."""
    x1 = cx + r * math.cos(math.radians(a1))
    y1 = cy + r * math.sin(math.radians(a1))
    x2 = cx + r * math.cos(math.radians(a2))
    y2 = cy + r * math.sin(math.radians(a2))
    dash = ' stroke-dasharray="34 26"' if dashed else ""
    tangent = math.atan2(math.cos(math.radians(a2)), -math.sin(math.radians(a2)))
    return (
        f'<path d="M {x1:.0f},{y1:.0f} A {r} {r} 0 0 1 {x2:.0f},{y2:.0f}" fill="none" '
        f'stroke="{color}" stroke-width="{sw}" stroke-linecap="round"{dash}/>'
        + arrowhead(x2, y2, tangent, color=color)
    )


def star(cx, cy, outer, inner, n=8, color=GOLD):
    pts = []
    for i in range(n * 2):
        r = outer if i % 2 == 0 else inner
        a = math.pi * i / n - math.pi / 2
        pts.append(f"{cx + r * math.cos(a):.0f},{cy + r * math.sin(a):.0f}")
    return f'<polygon points="{" ".join(pts)}" fill="{color}"/>'


def sight(x1, y1, x2, y2, color=GOLD):
    """A dashed glance from an eye to a card."""
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="10" '
        f'stroke-linecap="round" stroke-dasharray="4 26"/>'
    )


def ray(x1, y1, x2, y2, color=GOLD):
    """A dashed sight-ray with an arrowhead: the oracle pointing at a card."""
    ang = math.atan2(y2 - y1, x2 - x1)
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="9" '
        f'stroke-linecap="round" stroke-dasharray="16 14"/>' + arrowhead(x2, y2, ang, size=24, color=color)
    )


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


def jester_cap(cx, cy, s=1.0, color=ORANGE):
    """Three floppy horns over a band, a bell on each tip — unmistakably a jester,
    never a crown: the side horns droop outward and down."""
    base = cy + 40 * s

    def petal(x1, ctrl, tip, back):
        return (
            f'<path d="M {cx + x1 * s},{base} Q {cx + ctrl[0] * s},{cy + ctrl[1] * s} '
            f'{cx + tip[0] * s},{cy + tip[1] * s} Q {cx + back[0] * s},{cy + back[1] * s} '
            f'{cx + x1 / 3 * s},{base - 6 * s} Z" fill="{color}" stroke="{INK}" '
            f'stroke-width="{5 * s:.0f}" stroke-linejoin="round"/>'
        )

    left = petal(-64, (-132, -64), (-150, -2), (-78, -24))
    right = petal(64, (132, -64), (150, -2), (78, -24))
    middle = (
        f'<path d="M {cx - 30 * s},{base} Q {cx - 12 * s},{cy - 102 * s} {cx},{cy - 100 * s} '
        f'Q {cx + 12 * s},{cy - 102 * s} {cx + 30 * s},{base} Z" fill="{color}" '
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


def index_glyph(label, underline, color=INK):
    size = 150 if len(label) == 1 else 118
    t = (
        f'<text x="66" y="188" font-family="Georgia, \'Times New Roman\', serif" '
        f'font-size="{size}" font-weight="bold" fill="{color}">{label}</text>'
    )
    if underline:
        t += f'<rect x="70" y="206" width="76" height="12" rx="6" fill="{color}"/>'
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
        f'<rect x="140" y="290" width="545" height="600" rx="130" fill="{FELT_DARK}" '
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


def face_seven():
    """7: the table; a small card of YOURS rises, your eye above it."""
    amber = ACCENT["7"]
    return (
        board(my_gap=1)
        + trail(CX, 800, CX, 724, color=amber)
        + popped(CX, 680, 122, 168, FELT, GOLD, amber)
        + big_eye(CX, 524, s=0.55, gaze=8, iris=amber)
    )


def face_eight():
    """8: the table; a BIG card of yours rises, the eye opened on the card
    itself — same act as the 7, bigger card, different picture."""
    burnt = ACCENT["8"]
    return (
        board(my_gap=1)
        + trail(CX, 800, CX, 680, color=burnt)
        + popped(CX, 590, 190, 262, FELT, GOLD, burnt)
        + big_eye(CX, 590, s=0.62, iris=burnt)
    )


def face_nine():
    """9: the table; a small card of THEIRS drops, a slim lens ring on it."""
    steel = ACCENT["9"]
    return (
        board(top_gap=1)
        + trail(CX, 420, CX, 500, color=steel)
        + popped(CX, 560, 122, 168, BLUE, BLUE_EDGE, steel)
        + lens(CX, 560, 96, steel, sw=13)
    )


def face_ten():
    """10: the table; a BIG card of theirs drops under a full magnifier."""
    indigo = ACCENT["10"]
    return (
        board(top_gap=1)
        + trail(CX, 420, CX, 480, color=indigo)
        + popped(CX, 580, 190, 262, BLUE, BLUE_EDGE, indigo)
        + lens(CX + 10, 566, 132, indigo, handle=True)
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
    """The oracle, double-ended like a real court card and without an eye —
    an oracle radiates, he does not peer. Crown above, its mirror below, the
    naming-star between them, rays to a card in every corner; green and blue
    placed so a 180° turn maps the face onto itself."""
    crowns = crown(CX, 412, s=0.95) + group(180, CX, 712, crown(CX, 712, s=0.95))
    named = star(CX, 562, 66, 28)
    cards = (
        card_shape(228, 292, 118, 162, rot=-8, fill=FELT, stroke=GOLD)
        + card_shape(597, 292, 118, 162, rot=8, fill=BLUE, stroke=BLUE_EDGE)
        + card_shape(228, 832, 118, 162, rot=8, fill=BLUE, stroke=BLUE_EDGE)
        + card_shape(597, 832, 118, 162, rot=-8, fill=FELT, stroke=GOLD)
    )
    rays = (
        ray(322, 480, 278, 388, color=GOLD)
        + ray(503, 480, 547, 388, color=GOLD)
        + ray(322, 644, 278, 736, color=GOLD)
        + ray(503, 644, 547, 736, color=GOLD)
    )
    return rays + crowns + named + cards


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
    """The fool's cap, and the minus it is worth."""
    badge = (
        f'<circle cx="{CX}" cy="810" r="78" fill="none" stroke="{GOLD}" stroke-width="14"/>'
        f'<rect x="{CX - 42}" y="800" width="84" height="20" rx="10" fill="{GOLD}"/>'
    )
    return jester_cap(CX, 500, s=2.7) + badge


def card_back():
    lattice = []
    for gy in range(140, 1020, 90):
        for gx in range(100 + (45 if (gy // 90) % 2 else 0), 760, 90):
            if abs(gx - CX) < 220 and abs(gy - H / 2) < 260:
                continue
            lattice.append(
                f'<path d="M {gx},{gy - 20} L {gx + 16},{gy} L {gx},{gy + 20} L {gx - 16},{gy} Z" '
                f'fill="{FELT_DARK}"/>'
            )
    pips = "".join(
        f'<path d="M {x},{y - 30} L {x + 22},{y} L {x},{y + 30} L {x - 22},{y} Z" '
        f'fill="{GOLD}"/>'
        for x, y in ((CX, H / 2 - 200), (CX, H / 2 + 200), (CX - 160, H / 2), (CX + 160, H / 2))
    )
    v = (
        f'<path d="M {CX - 96},{H / 2 - 110} L {CX - 40},{H / 2 - 110} L {CX},{H / 2 + 26} '
        f'L {CX + 40},{H / 2 - 110} L {CX + 96},{H / 2 - 110} L {CX + 34},{H / 2 + 116} '
        f'L {CX - 34},{H / 2 + 116} Z" fill="{ORANGE}" stroke="{GOLD}" stroke-width="8" '
        f'stroke-linejoin="round"/>'
    )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
        f'<rect width="{W}" height="{H}" rx="44" fill="{PAPER}"/>'
        f'<rect x="22" y="22" width="{W - 44}" height="{H - 44}" rx="32" fill="{FELT}"/>'
        f'<rect x="40" y="40" width="{W - 80}" height="{H - 80}" rx="26" '
        f'fill="none" stroke="{GOLD}" stroke-width="8"/>'
        f'<rect x="58" y="58" width="{W - 116}" height="{H - 116}" rx="22" '
        f'fill="none" stroke="{GOLD}" stroke-width="4"/>'
        + "".join(lattice)
        + pips
        + v
        + "</svg>"
    )


# ---------------------------------------------------------------- output

FACES = {
    "card_2": frame("2", face_number(2), accent=ACCENT["2"]),
    "card_3": frame("3", face_number(3), accent=ACCENT["3"]),
    "card_4": frame("4", face_number(4), accent=ACCENT["4"]),
    "card_5": frame("5", face_number(5), accent=ACCENT["5"]),
    "card_6": frame("6", face_number(6), underline=True, accent=ACCENT["6"]),
    "card_7": frame("7", face_seven(), accent=ACCENT["7"], bg=BG["7"]),
    "card_8": frame("8", face_eight(), accent=ACCENT["8"], bg=BG["8"]),
    "card_9": frame("9", face_nine(), underline=True, accent=ACCENT["9"], bg=BG["9"]),
    "card_10": frame("10", face_ten(), accent=ACCENT["10"], bg=BG["10"]),
    "card_j": frame("J", face_jack(), accent=ACCENT["j"], bg=BG["j"]),
    "card_q": frame("Q", face_queen(), accent=ACCENT["q"], bg=BG["q"]),
    "card_k": frame("K", face_king(), accent=ACCENT["k"], bg=BG["k"]),
    "card_a": frame("A", face_ace(), accent=ACCENT["a"], bg=BG["a"]),
    "card_joker": frame("", face_joker(), joker=True, accent=ORANGE, bg=BG["joker"]),
    "card_back": card_back(),
}


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


def main():
    OUT.mkdir(exist_ok=True)
    for name, svg in FACES.items():
        (OUT / f"{name}.svg").write_text(svg)
    (OUT / "preview.html").write_text(preview_html())
    print(f"wrote {len(FACES)} faces + preview.html to {OUT}")


if __name__ == "__main__":
    main()
