#!/usr/bin/env python3
"""Generate the meaning-based card faces as SVG.

The deck's visual grammar lives in docs/design/CARD-IMAGERY.md: every action card's emblem is
a miniature of the table (your row of face-down cards at the bottom under a gold chevron, an
opponent's row at the top), with an open eye for a peek, a closed eye for the Jack's blind
swap, dashed arrows for the Queen's optional one. This script exists because an image LLM
cannot hold that grammar consistent across fourteen faces — code can.

Every face carries the standard corner indices (top-left, bottom-right rotated 180°, with the
underline on 6 and 9) exactly like a normal deck; the emblem never replaces the rank.

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


def mini_card(cx, cy, w=96, h=134, fill=FELT, stroke=GOLD, sw=5, rot=0.0, opacity=1.0):
    body = (
        f'<rect x="{cx - w / 2:.0f}" y="{cy - h / 2:.0f}" width="{w}" height="{h}" rx="10" '
        f'fill="{fill}" stroke="{stroke}" stroke-width="{sw}" opacity="{opacity}"/>'
    )
    return group(rot, cx, cy, body) if rot else body


def revealed_card(cx, cy, rot=0.0, w=96, h=134):
    """A card tilted out of its row, face showing, with a warm halo."""
    halo = (
        f'<rect x="{cx - w / 2 - 16}" y="{cy - h / 2 - 16}" width="{w + 32}" height="{h + 32}"'
        f' rx="18" fill="{GOLD}" opacity="0.22"/>'
        f'<rect x="{cx - w / 2 - 8}" y="{cy - h / 2 - 8}" width="{w + 16}" height="{h + 16}"'
        f' rx="14" fill="{GOLD}" opacity="0.30"/>'
    )
    face = (
        f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="10" '
        f'fill="{WHITE}" stroke="{GOLD}" stroke-width="6"/>'
        f'<circle cx="{cx}" cy="{cy}" r="10" fill="{GOLD}"/>'
    )
    return group(rot, cx, cy, halo + face)


def my_row(cy, skip=None, fills=None):
    """Five cards, the viewer's hand. skip: index (0-4) left out of the row."""
    parts = []
    for i in range(5):
        if i == skip:
            continue
        fill = fills[i] if fills else FELT
        parts.append(mini_card(CX + (i - 2) * 112, cy, fill=fill))
    return "".join(parts)


def their_row(cy, skip=None, count=5, spacing=112, x0=None):
    """An opponent's hand: darker cards, ink border, no chevron."""
    parts = []
    start = x0 if x0 is not None else CX - (count - 1) * spacing / 2
    for i in range(count):
        if i == skip:
            continue
        parts.append(mini_card(start + i * spacing, cy, fill=FELT_DARK, stroke=INK, sw=4))
    return "".join(parts)


def chevron(cx, cy, s=1.0):
    return (
        f'<polyline points="{cx - 40 * s},{cy + 20 * s} {cx},{cy - 16 * s} {cx + 40 * s},{cy + 20 * s}" '
        f'fill="none" stroke="{GOLD}" stroke-width="{12 * s:.0f}" '
        f'stroke-linecap="round" stroke-linejoin="round"/>'
    )


def open_eye(cx, cy, s=1.0):
    w, h = 82 * s, 52 * s
    return (
        f'<path d="M {cx - w},{cy} Q {cx},{cy - h} {cx + w},{cy} Q {cx},{cy + h} {cx - w},{cy} Z" '
        f'fill="{WHITE}" stroke="{INK}" stroke-width="{9 * s:.0f}" stroke-linejoin="round"/>'
        f'<circle cx="{cx}" cy="{cy}" r="{26 * s:.0f}" fill="{GOLD}"/>'
        f'<circle cx="{cx}" cy="{cy}" r="{12 * s:.0f}" fill="{INK}"/>'
    )


def closed_eye(cx, cy, s=1.0):
    w = 74 * s
    lashes = "".join(
        f'<line x1="{cx + dx * s}" y1="{cy + (34 - abs(dx) * 0.12) * s}" '
        f'x2="{cx + dx * 1.22 * s}" y2="{cy + (62 - abs(dx) * 0.12) * s}" '
        f'stroke="{INK}" stroke-width="{8 * s:.0f}" stroke-linecap="round"/>'
        for dx in (-46, 0, 46)
    )
    return (
        f'<path d="M {cx - w},{cy} Q {cx},{cy + 52 * s} {cx + w},{cy}" '
        f'fill="none" stroke="{INK}" stroke-width="{10 * s:.0f}" stroke-linecap="round"/>' + lashes
    )


def sight_line(x1, y1, x2, y2):
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{INK}" stroke-width="6" '
        f'stroke-linecap="round" stroke-dasharray="1 22" opacity="0.85"/>'
    )


def arrow(x1, y1, x2, y2, dashed=False, sw=14, color=GOLD):
    ang = math.atan2(y2 - y1, x2 - x1)
    head = 34
    hx, hy = x2 - head * math.cos(ang), y2 - head * math.sin(ang)
    dash = f' stroke-dasharray="26 20"' if dashed else ""
    p1 = (x2 - head * 1.5 * math.cos(ang - 0.42), y2 - head * 1.5 * math.sin(ang - 0.42))
    p2 = (x2 - head * 1.5 * math.cos(ang + 0.42), y2 - head * 1.5 * math.sin(ang + 0.42))
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{hx:.0f}" y2="{hy:.0f}" stroke="{color}" '
        f'stroke-width="{sw}" stroke-linecap="round"{dash}/>'
        f'<polygon points="{x2},{y2} {p1[0]:.0f},{p1[1]:.0f} {p2[0]:.0f},{p2[1]:.0f}" fill="{color}"/>'
    )


def weight(cx, top_y, r):
    """A round scale-weight hanging from top_y on a short cord."""
    cy = top_y + 34 + r
    return (
        f'<line x1="{cx}" y1="{top_y}" x2="{cx}" y2="{cy - r}" stroke="{GOLD_DARK}" stroke-width="7"/>'
        f'<rect x="{cx - 10}" y="{cy - r - 16}" width="20" height="16" rx="5" fill="{GOLD_DARK}"/>'
        f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{GOLD}" stroke="{GOLD_DARK}" stroke-width="6"/>'
        f'<circle cx="{cx - r * 0.3:.0f}" cy="{cy - r * 0.35:.0f}" r="{r * 0.22:.0f}" fill="{PAPER}" opacity="0.5"/>'
    )


def jester_cap(cx, cy, s=1.0, color=ORANGE):
    """Three-pointed cap over a band, bells on the tips — the Joker's glyph."""
    base = cy + 34 * s
    spikes = (
        f"M {cx - 62 * s},{base} L {cx - 66 * s},{cy - 44 * s} L {cx - 22 * s},{base - 20 * s} "
        f"L {cx},{cy - 66 * s} L {cx + 22 * s},{base - 20 * s} "
        f"L {cx + 66 * s},{cy - 44 * s} L {cx + 62 * s},{base} Z"
    )
    band = (
        f'<rect x="{cx - 62 * s}" y="{base - 6 * s}" width="{124 * s:.0f}" height="{22 * s:.0f}" '
        f'rx="{10 * s:.0f}" fill="{GOLD}" stroke="{INK}" stroke-width="{4 * s:.0f}"/>'
    )
    bells = "".join(
        f'<circle cx="{cx + dx * s}" cy="{cy + dy * s}" r="{10 * s:.0f}" fill="{GOLD}" '
        f'stroke="{INK}" stroke-width="{3 * s:.0f}"/>'
        for dx, dy in ((-66, -44), (0, -66), (66, -44))
    )
    return (
        f'<path d="{spikes}" fill="{color}" stroke="{INK}" stroke-width="{5 * s:.0f}" '
        f'stroke-linejoin="round"/>' + band + bells
    )


# ---------------------------------------------------------------- the frame


def index_glyph(label, underline):
    """One corner index, drawn at the top-left; the caller mirrors it."""
    size = 150 if len(label) == 1 else 118
    t = (
        f'<text x="66" y="188" font-family="Georgia, \'Times New Roman\', serif" '
        f'font-size="{size}" font-weight="bold" fill="{INK}">{label}</text>'
    )
    if underline:
        t += f'<rect x="70" y="206" width="76" height="12" rx="6" fill="{INK}"/>'
    return t


def joker_index():
    return jester_cap(118, 140, s=0.75)


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


def face_number(n):
    """2-6: your own hand dragged down by a growing weight. Value = burden."""
    fills = {2: "#B9CFBD", 3: "#8FB59A", 4: "#5E9678", 5: "#337052", 6: FELT_DARK}
    spacing = 136 if n <= 4 else 120
    cards = "".join(
        mini_card(CX + (i - (n - 1) / 2) * spacing, 540, w=118, h=164, fill=fills[n])
        for i in range(n)
    )
    right_x = CX + ((n - 1) - (n - 1) / 2) * spacing
    r = 18 + (n - 2) * 12
    return cards + weight(right_x, 628, r) + chevron(CX, 800)


def face_peek_own(idx):
    """7 and 8: your row, one card tilted up revealed, your eye looking at it."""
    cy = 730
    tx = CX + (idx - 2) * 112
    return (
        my_row(cy, skip=idx)
        + revealed_card(tx, cy - 96, rot=-14)
        + open_eye(CX, 358)
        + sight_line(CX + (26 if tx > CX else -26), 408, tx, cy - 180)
        + chevron(CX, 860)
    )


def face_peek_them(idx):
    """9 and 10: their row on top, one card tilted down, your eye below."""
    cy = 340
    tx = CX + (idx - 2) * 112
    return (
        their_row(cy, skip=idx)
        + revealed_card(tx, cy + 96, rot=14)
        + sight_line(tx, cy + 184, CX + (26 if tx > CX else -26), 704)
        + open_eye(CX, 752)
        + chevron(CX, 866)
    )


def swap_scene(revealed, dashed, eye):
    """Shared J/Q composition: two rows, the two pulled cards side by side mid-table,
    crossing arrows between them, and the eye (open or shut) above the trade."""
    lx, rx, cy = 322, 502, 604
    if revealed:
        mine_pulled = revealed_card(lx, cy, rot=-8)
        theirs_pulled = revealed_card(rx, cy, rot=8)
    else:
        mine_pulled = mini_card(lx, cy, fill=FELT, rot=-8)
        theirs_pulled = mini_card(rx, cy, fill=FELT_DARK, stroke=INK, sw=4, rot=8)
    arrows = (
        arrow(lx, cy - 118, rx - 6, cy - 118, dashed=dashed, sw=12)
        + arrow(rx, cy + 118, lx + 6, cy + 118, dashed=dashed, sw=12)
    )
    return (
        their_row(300, skip=3)
        + my_row(830, skip=1)
        + mine_pulled
        + theirs_pulled
        + arrows
        + eye
        + chevron(CX, 936)
    )


def face_jack():
    return swap_scene(revealed=False, dashed=False, eye=closed_eye(CX, 402, s=0.95))


def face_queen():
    return swap_scene(revealed=True, dashed=True, eye=open_eye(CX, 408, s=0.85))


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


def face_king():
    """A hollow crown choosing among the other emblems in miniature."""
    fan = []
    glyphs = []
    positions = [(226, 790, -12), (412, 758, 0), (598, 790, 12)]
    for i, (x, y, rot) in enumerate(positions):
        fan.append(mini_card(x, y, w=158, h=220, fill=WHITE, stroke=INK, sw=5, rot=rot))
        if i == 0:  # a peek: eye over a green card
            g = mini_card(x, y + 40, w=56, h=76, sw=4) + open_eye(x, y - 44, s=0.44)
        elif i == 1:  # the swap: two cards trading
            g = (
                mini_card(x - 34, y + 44, w=50, h=68, sw=4)
                + mini_card(x + 34, y - 44, w=50, h=68, fill=FELT_DARK, stroke=INK, sw=4)
                + arrow(x - 30, y - 24, x + 26, y - 24, sw=8)
                + arrow(x + 30, y + 24, x - 26, y + 24, sw=8)
            )
        else:  # force draw: a card pushed away
            g = mini_card(x - 20, y + 32, w=56, h=76, sw=4, rot=8) + arrow(x - 4, y - 4, x + 46, y - 56, sw=9)
        glyphs.append(group(rot, x, y, g))
    beam = (
        f'<polygon points="{CX - 30},408 {CX + 30},408 {CX + 74},628 {CX - 74},628" '
        f'fill="{GOLD}" opacity="0.18"/>'
    )
    return crown(CX, 320, s=1.2) + beam + "".join(fan) + "".join(glyphs)


def face_ace():
    """The deck pushes one more card into an opponent's growing row."""
    sx, sy = 260, 680
    stack = (
        mini_card(sx + 16, sy + 20, w=118, h=164, fill=FELT)
        + mini_card(sx + 8, sy + 10, w=118, h=164, fill=FELT)
        + mini_card(sx, sy, w=118, h=164, fill=FELT)
    )
    row = their_row(300, count=5, spacing=108, x0=166)
    slot = (
        f'<rect x="{646 - 48}" y="{300 - 67}" width="96" height="134" rx="10" fill="none" '
        f'stroke="{INK}" stroke-width="5" stroke-dasharray="16 14" opacity="0.55"/>'
    )
    flying = mini_card(520, 500, fill=FELT_DARK, stroke=INK, sw=4, rot=24)
    a = arrow(346, 620, 630, 392, sw=15)
    return stack + row + slot + flying + a + chevron(CX, 880)


def face_joker():
    """The one card that lifts your total instead of adding to it."""
    lift = (
        mini_card(CX, 620, w=118, h=164, fill=ORANGE, stroke=GOLD, sw=6, rot=-4)
        + jester_cap(CX, 620, s=0.55, color=GOLD)
    )
    motion = "".join(
        f'<path d="M {CX + dx - 24},{y} Q {CX + dx},{y + 16} {CX + dx + 24},{y}" '
        f'fill="none" stroke="{GOLD_DARK}" stroke-width="7" stroke-linecap="round" opacity="{op}"/>'
        for dx, y, op in ((-36, 736, 0.85), (36, 736, 0.85), (0, 772, 0.5))
    )
    badge = (
        f'<circle cx="{CX}" cy="384" r="54" fill="none" stroke="{GOLD}" stroke-width="10"/>'
        f'<rect x="{CX - 28}" y="377" width="56" height="14" rx="7" fill="{GOLD}"/>'
    )
    return my_row(856, skip=2) + lift + motion + badge + chevron(CX, 952)


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
    "card_7": frame("7", face_peek_own(1)),
    "card_8": frame("8", face_peek_own(3)),
    "card_9": frame("9", face_peek_them(1), underline=True),
    "card_10": frame("10", face_peek_them(3)),
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
