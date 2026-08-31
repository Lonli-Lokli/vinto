#!/usr/bin/env python3
"""Generate the meaning-based card faces as SVG.

Design (third revision, docs/design/CARD-IMAGERY.md): every action card carries one big
heraldic emblem — a single bold symbol of what the card does, the way a poker court card
carries its figure. Colour says whose card is touched: felt green with a gold border is
yours, dark ink-bordered green is an opponent's. Number cards are clean pip cards whose
pips are card shapes — the count is the rank. Every face keeps the standard corner
indices (top-left, bottom-right rotated 180°, underlined 6 and 9).

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


def big_card(cx, cy, w=320, h=440, mine=True, rot=0.0):
    """A large emblem card. Green + gold border = yours; darker + ink border = theirs."""
    fill, stroke = (FELT, GOLD) if mine else (FELT_DARK, INK)
    body = (
        f'<rect x="{cx - w / 2:.0f}" y="{cy - h / 2:.0f}" width="{w}" height="{h}" rx="26" '
        f'fill="{fill}" stroke="{stroke}" stroke-width="10"/>'
        f'<rect x="{cx - w / 2 + 22:.0f}" y="{cy - h / 2 + 22:.0f}" width="{w - 44}" '
        f'height="{h - 44}" rx="16" fill="none" stroke="{PAPER}" stroke-width="4" opacity="0.35"/>'
    )
    return group(rot, cx, cy, body) if rot else body


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


def star(cx, cy, outer, inner, n=8, color=GOLD):
    pts = []
    for i in range(n * 2):
        r = outer if i % 2 == 0 else inner
        a = math.pi * i / n - math.pi / 2
        pts.append(f"{cx + r * math.cos(a):.0f},{cy + r * math.sin(a):.0f}")
    return f'<polygon points="{" ".join(pts)}" fill="{color}"/>'


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


def teardrop(cx, cy, s=1.0):
    return (
        f'<path d="M {cx},{cy - 34 * s} Q {cx + 26 * s},{cy + 4 * s} {cx},{cy + 26 * s} '
        f'Q {cx - 26 * s},{cy + 4 * s} {cx},{cy - 34 * s} Z" '
        f'fill="{ORANGE}" stroke="{INK}" stroke-width="{5 * s:.0f}"/>'
    )


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


def frame(label, emblem, underline=False, joker=False, accent=None):
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
        f'<rect width="{W}" height="{H}" rx="44" fill="{PAPER}"/>'
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
    """2-6: pip cards whose pips are the game's own object, each rank in its own colour."""
    return "".join(pip_card(x, y, fill=ACCENT[str(n)]) for x, y in PIP_LAYOUTS[n])


PALE = "#A8C2B5"
BLUE = "#5B9BD5"        # opponents' cards: 4.4:1 against the dark felt, distinct from your green
BLUE_EDGE = "#DCE9F5"

# One accent per rank family — the deck is colourful the way the chip rack is,
# while the board's meaning-colours (green yours, blue theirs, cream deck) stay fixed.
# Each is dark enough to hold its own as a corner index on cream.
ACCENT = {
    "2": "#17766B",   # teal
    "3": "#2F5E8C",   # blue
    "4": "#4F5AA8",   # indigo
    "5": "#B03A57",   # raspberry
    "6": "#1B5E43",   # the brand green
    "7": "#A96A00",   # amber
    "8": "#A96A00",
    "9": "#256D85",   # steel cyan
    "10": "#256D85",
    "j": "#7C3AA0",   # violet
    "q": "#A23B72",   # plum
    "k": "#A8791B",   # deep gold
    "a": "#9E2B25",   # red
}


def table_felt():
    """The board: a dark felt table seen from above, as the app itself draws it."""
    return (
        f'<rect x="140" y="290" width="545" height="600" rx="130" fill="{FELT_DARK}" '
        f'stroke="{GOLD}" stroke-width="8"/>'
        f'<rect x="162" y="312" width="501" height="556" rx="112" fill="none" '
        f'stroke="{GOLD}" stroke-width="3" opacity="0.4"/>'
    )


def opponent_seat(cx, cy, rot=0.0, gap=None):
    """A bust with three small cards in front of it, facing the table's center."""
    bust = (
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
    return f'<g transform="translate({cx} {cy}) rotate({rot})">{bust}{cards}</g>'


def your_hand(gap=None):
    """Your three cards at the bottom edge, larger — the seat the app gives you."""
    return "".join(
        f'<rect x="{CX + (i - 1) * 116 - 50:.0f}" y="772" width="100" height="138" rx="12" '
        f'fill="{FELT}" stroke="{GOLD}" stroke-width="7"/>'
        for i in range(3)
        if i != gap
    )


def trail(x1, y1, x2, y2, hw=36, color=GOLD):
    """A streak from a seat's gap to the card pulled out of it: whose card this is."""
    return (
        f'<polygon points="{x1 - hw},{y1} {x1 + hw},{y1} {x2 + 12},{y2} {x2 - 12},{y2}" '
        f'fill="{color}" opacity="0.3"/>'
    )


def popped_card(cx, cy, w=140, h=192, rot=0.0, fill=WHITE, stroke=GOLD, halo=GOLD):
    """The card under examination, enlarged at the table's center.
    It keeps its owner's colour, so whose card it is travels with it."""
    body = (
        f'<rect x="{cx - w / 2 - 12}" y="{cy - h / 2 - 12}" width="{w + 24}" height="{h + 24}" '
        f'rx="22" fill="{halo}" opacity="0.35"/>'
        f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="14" '
        f'fill="{fill}" stroke="{stroke}" stroke-width="8"/>'
    )
    return group(rot, cx, cy, body) if rot else body


def trace_path(x1, y1, ctrl, x2, y2, color=GOLD):
    """A dashed curved path with an arrowhead: where the card was taken from."""
    ang = math.atan2(y2 - ctrl[1], x2 - ctrl[0])
    return (
        f'<path d="M {x1},{y1} Q {ctrl[0]},{ctrl[1]} {x2},{y2}" fill="none" '
        f'stroke="{color}" stroke-width="9" stroke-linecap="round" stroke-dasharray="18 16"/>'
        + arrowhead(x2, y2, ang, size=26, color=color)
    )


def board(top_gap=None, my_gap=None):
    return (
        table_felt()
        + opponent_seat(CX, 384, gap=top_gap)
        + opponent_seat(224, 590, rot=90)
        + opponent_seat(601, 590, rot=-90)
        + your_hand(gap=my_gap)
    )


def face_peek_own():
    """7 and 8: the four-player board; one of YOUR cards rises to the light —
    still in your green — with the trace showing the gap it left."""
    amber = ACCENT["7"]
    return (
        board(my_gap=1)
        + trail(CX, 800, CX, 700, color=amber)
        + popped_card(CX, 620, fill=FELT, stroke=GOLD, halo=amber)
        + trace_path(CX - 40, 794, (270, 730), 334, 652, color=amber)
        + big_eye(CX, 588, s=0.62, gaze=6, iris=amber)
    )


def face_peek_them():
    """9 and 10: the same board; the card comes from the TOP opponent's hand,
    keeps their blue, and the trace runs back to the gap it left."""
    steel = ACCENT["9"]
    scene = (
        board(top_gap=1)
        + trail(CX, 420, CX, 480, color=steel)
        + popped_card(CX, 552, fill=BLUE, stroke=BLUE_EDGE, halo=steel)
        + trace_path(CX - 40, 436, (270, 476), 334, 542, color=steel)
    )
    lens_x, lens_y, r = CX + 52, 592, 108
    handle_a = math.radians(52)
    hx1 = lens_x + r * math.cos(handle_a)
    hy1 = lens_y + r * math.sin(handle_a)
    hx2 = lens_x + (r + 130) * math.cos(handle_a)
    hy2 = lens_y + (r + 130) * math.sin(handle_a)
    lens = (
        f'<line x1="{hx1:.0f}" y1="{hy1:.0f}" x2="{hx2:.0f}" y2="{hy2:.0f}" '
        f'stroke="{steel}" stroke-width="26" stroke-linecap="round"/>'
        f'<circle cx="{lens_x}" cy="{lens_y}" r="{r}" fill="none" stroke="{steel}" stroke-width="15"/>'
    )
    return scene + lens + big_eye(lens_x, lens_y, s=0.5, gaze=-6, iris=steel)


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


def swap_board(revealed, accent):
    """The board with one card pulled from your hand and one from the top
    opponent's, meeting at the center. Trails say whose cards are trading."""
    scene = board(top_gap=1, my_gap=1)
    trails = trail(CX - 20, 800, 340, 660, hw=30, color=accent) + trail(
        CX + 20, 420, 484, 520, hw=30, color=accent
    )
    if revealed:
        pair = popped_card(
            340, 590, w=112, h=154, rot=-9, fill=FELT, stroke=GOLD, halo=accent
        ) + popped_card(484, 590, w=112, h=154, rot=9, fill=BLUE, stroke=BLUE_EDGE, halo=accent)
    else:
        mine = (
            f'<rect x="284" y="513" width="112" height="154" rx="14" '
            f'fill="{FELT}" stroke="{GOLD}" stroke-width="8"/>'
        )
        theirs = (
            f'<rect x="428" y="513" width="112" height="154" rx="14" '
            f'fill="{BLUE}" stroke="{BLUE_EDGE}" stroke-width="8"/>'
        )
        pair = group(-9, 340, 590, mine) + group(9, 484, 590, theirs)
    arrows = arc_arrow(CX, 592, 158, 210, 330, dashed=revealed, sw=13, color=accent) + arc_arrow(
        CX, 592, 158, 30, 150, dashed=revealed, sw=13, color=accent
    )
    return scene + trails + pair + arrows


def face_jack():
    """The blind swap: yours for theirs, and the blindfold means nobody looks."""
    fold = blindfold(CX, 590, 128, -6)
    return (
        swap_board(revealed=False, accent=ACCENT["j"])
        + f'<g transform="translate({CX} 590) scale(0.78) translate(-{CX} -590)">{fold}</g>'
    )


def face_queen():
    """Look first, then trade if you like: both faces up, one open eye,
    and the arrows still dashed — undecided."""
    return swap_board(revealed=True, accent=ACCENT["q"]) + big_eye(
        CX, 590, s=0.55, iris=ACCENT["q"]
    )


def ray(x1, y1, x2, y2):
    """A dashed sight-ray with an arrowhead: the oracle pointing at a card."""
    ang = math.atan2(y2 - y1, x2 - x1)
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{GOLD}" stroke-width="9" '
        f'stroke-linecap="round" stroke-dasharray="16 14"/>' + arrowhead(x2, y2, ang, size=24)
    )


def face_king():
    """The oracle: the crowned eye at the table's center, and its sight-rays
    reaching every hand — the opponents' and your own alike."""
    oracle = crown(CX, 528, s=0.62) + big_eye(CX, 606, s=0.5)
    rays = (
        ray(CX, 552, CX, 476)            # the top opponent's hand
        + ray(CX - 96, 606, 314, 600)    # the left opponent's
        + ray(CX + 96, 606, 510, 600)    # the right opponent's
        + ray(CX, 662, CX, 750)          # your own
    )
    return board() + rays + oracle


def face_ace():
    """A card hurled from the deck at the table's center into an opponent's hand."""
    stack = "".join(
        f'<rect x="{CX - 55 + dx}" y="{620 + dy}" width="110" height="150" rx="12" '
        f'fill="{PAPER}" stroke="{GOLD}" stroke-width="6"/>'
        for dx, dy in ((10, 14), (5, 7), (0, 0))
    )
    red = ACCENT["a"]
    throw = (
        f'<line x1="{CX}" y1="640" x2="{CX}" y2="470" stroke="{red}" stroke-width="13" '
        f'stroke-linecap="round"/>' + arrowhead(CX, 452, -math.pi / 2, size=32, color=red)
    )
    streaks = "".join(
        f'<line x1="{CX + dx}" y1="{y}" x2="{CX + dx}" y2="{y + 40}" stroke="{red}" '
        f'stroke-width="7" stroke-linecap="round" opacity="{op}"/>'
        for dx, y, op in ((-64, 560, 0.7), (64, 560, 0.7))
    )
    fx = CX + 74
    flying = group(
        -18,
        fx,
        512,
        f'<rect x="{fx - 45}" y="{512 - 62}" width="90" height="124" rx="10" '
        f'fill="{PAPER}" stroke="{GOLD}" stroke-width="6"/>',
    )
    return board() + throw + streaks + stack + flying


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
    "card_7": frame("7", face_peek_own(), accent=ACCENT["7"]),
    "card_8": frame("8", face_peek_own(), accent=ACCENT["8"]),
    "card_9": frame("9", face_peek_them(), underline=True, accent=ACCENT["9"]),
    "card_10": frame("10", face_peek_them(), accent=ACCENT["10"]),
    "card_j": frame("J", face_jack(), accent=ACCENT["j"]),
    "card_q": frame("Q", face_queen(), accent=ACCENT["q"]),
    "card_k": frame("K", face_king(), accent=ACCENT["k"]),
    "card_a": frame("A", face_ace(), accent=ACCENT["a"]),
    "card_joker": frame("", face_joker(), joker=True, accent=ORANGE),
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
