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


def big_eye(cx, cy, s=1.0, gaze=0.0):
    """gaze shifts iris and pupil vertically: positive looks down, negative up."""
    w, h = 170 * s, 108 * s
    g = gaze * s
    return (
        f'<path d="M {cx - w},{cy} Q {cx},{cy - h} {cx + w},{cy} Q {cx},{cy + h} {cx - w},{cy} Z" '
        f'fill="{WHITE}" stroke="{INK}" stroke-width="{16 * s:.0f}" stroke-linejoin="round"/>'
        f'<circle cx="{cx}" cy="{cy + g:.0f}" r="{56 * s:.0f}" fill="{GOLD}"/>'
        f'<circle cx="{cx}" cy="{cy + g:.0f}" r="{27 * s:.0f}" fill="{INK}"/>'
        f'<circle cx="{cx + 16 * s:.0f}" cy="{cy + g - 18 * s:.0f}" r="{10 * s:.0f}" fill="{WHITE}"/>'
    )


def arrowhead(x, y, ang, size=40, color=GOLD):
    p1 = (x - size * 1.4 * math.cos(ang - 0.45), y - size * 1.4 * math.sin(ang - 0.45))
    p2 = (x - size * 1.4 * math.cos(ang + 0.45), y - size * 1.4 * math.sin(ang + 0.45))
    return f'<polygon points="{x:.0f},{y:.0f} {p1[0]:.0f},{p1[1]:.0f} {p2[0]:.0f},{p2[1]:.0f}" fill="{color}"/>'


def arc_arrow(cx, cy, r, a1, a2, dashed=False, sw=18):
    """A circular swap arrow from angle a1 to a2 (degrees, screen coords, clockwise)."""
    x1 = cx + r * math.cos(math.radians(a1))
    y1 = cy + r * math.sin(math.radians(a1))
    x2 = cx + r * math.cos(math.radians(a2))
    y2 = cy + r * math.sin(math.radians(a2))
    dash = ' stroke-dasharray="34 26"' if dashed else ""
    tangent = math.atan2(math.cos(math.radians(a2)), -math.sin(math.radians(a2)))
    return (
        f'<path d="M {x1:.0f},{y1:.0f} A {r} {r} 0 0 1 {x2:.0f},{y2:.0f}" fill="none" '
        f'stroke="{GOLD}" stroke-width="{sw}" stroke-linecap="round"{dash}/>'
        + arrowhead(x2, y2, tangent)
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


def index_glyph(label, underline):
    size = 150 if len(label) == 1 else 118
    t = (
        f'<text x="66" y="188" font-family="Georgia, \'Times New Roman\', serif" '
        f'font-size="{size}" font-weight="bold" fill="{INK}">{label}</text>'
    )
    if underline:
        t += f'<rect x="70" y="206" width="76" height="12" rx="6" fill="{INK}"/>'
    return t


def joker_index():
    return jester_cap(120, 148, s=0.5)


def frame(label, emblem, underline=False, joker=False):
    corner = joker_index() if joker else index_glyph(label, underline)
    mirrored = f'<g transform="rotate(180 {W / 2} {H / 2})">{corner}</g>'
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
        f'<rect width="{W}" height="{H}" rx="44" fill="{PAPER}"/>'
        f'<rect x="20" y="20" width="{W - 40}" height="{H - 40}" rx="32" '
        f'fill="none" stroke="{INK}" stroke-width="6"/>'
        f"{corner}{mirrored}{emblem}</svg>"
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
    """2-6: pip cards whose pips are the game's own object. Count = rank."""
    return "".join(pip_card(x, y) for x, y in PIP_LAYOUTS[n])


def face_peek_own():
    """7 and 8: the poker hole-card peek — your card's corner lifted, your eye
    glancing down under the flap. Nobody peeks at someone else's card like this."""
    cw, ch = 320, 440
    left, top = CX - cw / 2, 520
    right = left + cw
    cut = 130
    card = (
        f'<rect x="{left}" y="{top}" width="{cw}" height="{ch}" rx="26" '
        f'fill="{FELT}" stroke="{GOLD}" stroke-width="10"/>'
        f'<rect x="{left + 22}" y="{top + 22}" width="{cw - 44}" height="{ch - 44}" rx="16" '
        f'fill="none" stroke="{PAPER}" stroke-width="4" opacity="0.35"/>'
    )
    # the lifted corner: the top-right of the card folds down-inward, showing its pale face
    notch = (
        f'<path d="M {right - cut},{top - 5} L {right + 5},{top - 5} L {right + 5},{top + cut} '
        f'L {right - cut},{top - 5} Z" fill="{PAPER}"/>'
    )
    flap = (
        f'<path d="M {right - cut},{top} L {right},{top + cut} L {right - cut + 4},{top + cut - 6} '
        f'Z" fill="{WHITE}" stroke="{GOLD}" stroke-width="9" stroke-linejoin="round"/>'
    )
    peek = sight_dots(CX + 74, 412, right - cut / 2 - 16, top + 30)
    return card + notch + flap + peek + big_eye(CX + 40, 344, gaze=16)


def sight_dots(x1, y1, x2, y2):
    return (
        f'<line x1="{x1:.0f}" y1="{y1:.0f}" x2="{x2:.0f}" y2="{y2:.0f}" stroke="{INK}" '
        f'stroke-width="7" stroke-linecap="round" stroke-dasharray="1 26" opacity="0.85"/>'
    )


def face_peek_them():
    """9 and 10: an opponent sits across the table, their card in front of them,
    your lens reaching onto it. The person is what says 'theirs'."""
    bust = (
        f'<circle cx="{CX}" cy="252" r="82" fill="{FELT_DARK}" stroke="{INK}" stroke-width="8"/>'
        f'<path d="M {CX - 175},480 Q {CX - 160},330 {CX - 62},322 Q {CX},302 {CX + 62},322 '
        f'Q {CX + 160},330 {CX + 175},480 Z" '
        f'fill="{FELT_DARK}" stroke="{INK}" stroke-width="8"/>'
    )
    card = big_card(CX, 560, w=270, h=370, mine=False)
    lens_x, lens_y, r = CX + 66, 720, 145
    handle_a = math.radians(52)
    hx1 = lens_x + r * math.cos(handle_a)
    hy1 = lens_y + r * math.sin(handle_a)
    hx2 = lens_x + (r + 160) * math.cos(handle_a)
    hy2 = lens_y + (r + 160) * math.sin(handle_a)
    lens = (
        f'<circle cx="{lens_x}" cy="{lens_y}" r="{r}" fill="{PAPER}" opacity="0.25"/>'
        f'<line x1="{hx1:.0f}" y1="{hy1:.0f}" x2="{hx2:.0f}" y2="{hy2:.0f}" '
        f'stroke="{GOLD}" stroke-width="32" stroke-linecap="round"/>'
        f'<circle cx="{lens_x}" cy="{lens_y}" r="{r}" fill="none" stroke="{GOLD}" stroke-width="18"/>'
    )
    return bust + card + lens + big_eye(lens_x, lens_y, s=0.66, gaze=-10)


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


def swap_pair(dashed):
    """Two cards — yours and theirs — circled by exchange arrows."""
    cards = big_card(320, 600, w=270, h=380, mine=True, rot=-13) + big_card(
        505, 600, w=270, h=380, mine=False, rot=13
    )
    arrows = arc_arrow(CX, 600, 300, 208, 332, dashed=dashed) + arc_arrow(
        CX, 600, 300, 28, 152, dashed=dashed
    )
    return cards, arrows


def face_jack():
    """The blind swap: the trade happens, the blindfold means nobody looks."""
    cards, arrows = swap_pair(dashed=False)
    return cards + blindfold(CX, 590, 250, -6) + arrows


def face_queen():
    """Look first, then trade if you like: the eye is open, the arrows undecided."""
    cards, arrows = swap_pair(dashed=True)
    return cards + big_eye(CX, 590, s=1.05) + arrows


def face_king():
    """The crown commands any card's power by naming it."""
    beam = (
        f'<polygon points="{CX - 34},420 {CX + 34},420 {CX + 96},650 {CX - 96},650" '
        f'fill="{GOLD}" opacity="0.16"/>'
    )
    card = (
        f'<rect x="{CX - 160}" y="510" width="320" height="440" rx="26" fill="{WHITE}" '
        f'stroke="{GOLD}" stroke-width="10"/>'
        f'<rect x="{CX - 138}" y="532" width="276" height="396" rx="16" fill="none" '
        f'stroke="{GOLD}" stroke-width="4" opacity="0.5"/>'
    )
    return crown(CX, 330, s=1.6) + beam + card + star(CX, 730, 120, 52)


def face_ace():
    """Your idea of a poison: the flask empties onto an opponent's card."""
    card = big_card(520, 770, w=290, h=400, mine=False, rot=8)
    flask_body = (
        f'<path d="M -34,-150 L 34,-150 L 34,-58 L 96,86 Q 112,126 74,126 L -74,126 '
        f'Q -112,126 -96,86 L -34,-58 Z" fill="{WHITE}" stroke="{INK}" stroke-width="10" '
        f'stroke-linejoin="round"/>'
        f'<path d="M -70,56 L 70,56 L 96,86 Q 112,126 74,126 L -74,126 Q -112,126 -96,86 Z" '
        f'fill="{ORANGE}" stroke="{INK}" stroke-width="6"/>'
        f'<rect x="-44" y="-186" width="88" height="40" rx="12" fill="{GOLD}" '
        f'stroke="{INK}" stroke-width="7"/>'
    )
    flask = f'<g transform="translate(300 400) rotate(38)">{flask_body}</g>'
    drops = teardrop(432, 468, 1.15) + teardrop(478, 552, 0.9) + teardrop(510, 628, 0.7)
    return card + flask + drops


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
    "card_2": frame("2", face_number(2)),
    "card_3": frame("3", face_number(3)),
    "card_4": frame("4", face_number(4)),
    "card_5": frame("5", face_number(5)),
    "card_6": frame("6", face_number(6), underline=True),
    "card_7": frame("7", face_peek_own()),
    "card_8": frame("8", face_peek_own()),
    "card_9": frame("9", face_peek_them(), underline=True),
    "card_10": frame("10", face_peek_them()),
    "card_j": frame("J", face_jack()),
    "card_q": frame("Q", face_queen()),
    "card_k": frame("K", face_king()),
    "card_a": frame("A", face_ace()),
    "card_joker": frame("", face_joker(), joker=True),
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
