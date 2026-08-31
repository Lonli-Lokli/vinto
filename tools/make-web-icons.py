#!/usr/bin/env python3
"""Generate the web client's icons and its share card.

`composeApp/src/wasmJsMain/resources/index.html` asked for a `favicon.png` that was not in
that directory, and nothing noticed — for the same reason nothing noticed the page itself was
missing until recently (docs/kotlin/README.md §7): the build *compiles* the web client and
never *serves* it, so a broken asset reference costs a compile gate nothing. On Cloudflare
Pages the consequence is worse than a broken icon: a path that does not exist is answered with
the SPA fallback — `index.html`, **200**, `text/html` — so the browser is handed a web page
where it asked for a picture, and a `_headers` rule then caches that for a year.

Run from anywhere:

    python3 tools/make-web-icons.py

It writes into `composeApp/src/wasmJsMain/resources/`, and the PNGs are committed. Nothing at
build time runs this; re-run it when the mark or the card art changes.

Two families come out, and they are drawn from different sources on purpose.

**The icons** are the launcher icon's mark, at the sizes a browser and a phone home screen
ask for. The same orange V the Android launcher uses on the same dark rail, because §6g's
reasoning about the launcher applies at least as strongly here: a different icon in the tab
would make it a different game to anybody who has played both. `icon-512-maskable.png` keeps
the mark inside the safe circle Android's `purpose: maskable` reserves; the others are drawn
full-bleed, since nothing is going to mask them.

**The share card** is the game rather than the mark. A 1200x630 open-graph image is the only
thing most people ever see of a link, and a bare logo on a colour tells them nothing about
what is behind it. This one is the table: the lamp on the baize, a fan of five real cards from
the deck the game deals, and the name in the same engraved Roman the home screen stamps on the
cloth. The five are chosen rather than arbitrary — the Joker, the King, the Queen, the seven
and the Ace are the ranks with something to say about how the game is played, and they are the
ones the card beats in the lesson spend longest on.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# tools/ sits at the repository root, which is also the Gradle root.
ROOT = Path(__file__).resolve().parents[1]

# The same V the launcher uses, so the phone and the browser answer to one mark.
MARK = ROOT / "tools" / "brand" / "vinto-mark.png"
# The card art the app itself draws with, so the share card is the real deck.
# The faces are vector drawables in the app now; the share card reads committed
# renders of the same SVGs (chromium screenshots of tools/card-faces/*.svg).
ART = ROOT / "tools" / "brand" / "card-renders"
# Cinzel, the wordmark face. `theme/Type.kt` uses it for the name of the game and nothing else.
WORDMARK_FONT = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "font" / "cinzel_bold.ttf"
BODY_FONT = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "font" / "fira_medium.ttf"

OUT = ROOT / "composeApp" / "src" / "wasmJsMain" / "resources"

# Straight out of `theme/VintoTheme.kt` and `androidApp/.../values-night/colors.xml`, so the
# share card is the colour of the app rather than an approximation of it.
RAIL = (0x1B, 0x5E, 0x43, 0xFF)  # the felt; the launcher icon's background
FELT = (0x1B, 0x5E, 0x43)  # Felt
FELT_DARK = (0x0E, 0x34, 0x28)  # FeltDark, the cloth away from the lamp
LEAF_GOLD = (0xF2, 0xDF, 0xA6)  # LeafGold — the wordmark, gold leaf rather than gold paint
FELT_INK = (0xF2, 0xF5, 0xF0)  # FeltInk, what is legible on the cloth

# Icon sizes, and what each is for.
ICONS = {
    "favicon.png": 192,  # the tab, and what `index.html` has always referenced
    "icon-192.png": 192,  # the web app manifest's small icon
    "icon-512.png": 512,  # the manifest's large icon, and the og:image fallback
    "apple-touch-icon.png": 180,  # iOS home screen, which ignores the manifest
}

# Fractions of the canvas the mark occupies, by height. Full-bleed icons can be larger than
# the maskable one, which has to survive a circular crop.
ICON_GLYPH = 0.62
MASKABLE_GLYPH = 0.42

CARD = 1200, 630
CARDS = ["card_joker", "card_k", "card_q", "card_7", "card_a"]


def mark(height: int) -> Image.Image:
    """The V, resampled to `height` and with its edges re-sharpened.

    Lifted from `make-launcher-icons.py`, and deliberately not shared with it: two scripts
    that each run once and write committed PNGs are cheaper to read separately than a third
    module neither of them would otherwise need. If a fourth caller appears, extract it then.
    """
    src = Image.open(MARK).convert("RGBA")
    src = src.crop(src.getbbox())  # the mark bleeds to the edges of its own file
    width = round(src.width * height / src.height)
    scaled = src.resize((width, height), Image.LANCZOS)

    r, g, b, a = scaled.split()
    a = a.point(lambda v: max(0, min(255, round((v - 128) * 3 + 128))))
    return Image.merge("RGBA", (r, g, b, a))


def icon(px: int, glyph: float, round_corners: bool) -> Image.Image:
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    if round_corners:
        ImageDraw.Draw(canvas).rounded_rectangle(
            (0, 0, px - 1, px - 1), radius=round(px * 0.222), fill=RAIL
        )
    else:
        canvas.paste(RAIL, (0, 0, px, px))
    v = mark(round(px * glyph))
    canvas.alpha_composite(v, ((px - v.width) // 2, (px - v.height) // 2))
    return canvas


def felt(size: tuple[int, int]) -> Image.Image:
    """The cloth under a lamp: a radial from `Felt` at the middle to `FeltDark` at the rim.

    Drawn at an eighth scale and blown back up rather than per-pixel. It is a smooth gradient,
    so the interpolation is invisible and the script finishes in a moment instead of a minute.
    """
    w, h = size
    small = (w // 8, h // 8)
    lamp = Image.new("RGB", small)
    px = lamp.load()
    cx, cy = small[0] / 2, small[1] / 2
    far = (cx**2 + cy**2) ** 0.5
    for y in range(small[1]):
        for x in range(small[0]):
            t = min(1.0, ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / far)
            t = t**0.85  # hold the light a little wider than a linear falloff would
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(FELT, FELT_DARK))
    return lamp.resize(size, Image.BICUBIC)


def fan(height: int) -> Image.Image:
    """Five cards, spread the way the home screen spreads them.

    Each is rotated about its own bottom edge rather than its middle, which is what makes a
    fan look held rather than scattered, and each carries a soft shadow so the overlaps read
    as cards in front of cards rather than as one flat collage.
    """
    faces = [Image.open(ART / f"{name}.png").convert("RGBA") for name in CARDS]
    width = round(faces[0].width * height / faces[0].height)
    faces = [f.resize((width, height), Image.LANCZOS) for f in faces]

    spread = round(width * 0.74)  # how far apart the cards sit
    canvas_w = spread * (len(faces) - 1) + width * 3
    canvas_h = round(height * 2.2)
    canvas = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))

    middle = (len(faces) - 1) / 2
    for i, face in enumerate(faces):
        offset = i - middle
        # Positive is counter-clockwise in PIL, so a card left of centre leans left and one
        # right of centre leans right — the way a fan opens in a hand.
        angle = -offset * 10.0
        # And the outer cards sit lower, so the top edges describe an arc rather than a line.
        drop = round(offset**2 * height * 0.05)

        shadow = Image.new("RGBA", face.size, (0, 0, 0, 0))
        shadow.paste((0, 0, 0, 150), (0, 0) + face.size, face.split()[3])
        stack = Image.new("RGBA", face.size, (0, 0, 0, 0))
        stack.alpha_composite(shadow)
        stack.alpha_composite(face)

        # About the card's own middle: the arc above does the work a pivot would, and a
        # centre rotation keeps each card's placement predictable from `offset` alone.
        turned = stack.rotate(angle, resample=Image.BICUBIC, expand=True)

        x = round(canvas_w / 2 - turned.width / 2 + offset * spread)
        y = round((canvas_h - turned.height) / 2 + drop)
        canvas.alpha_composite(turned, (x, y))

    return canvas.crop(canvas.getbbox())


def share_card() -> Image.Image:
    w, h = CARD
    canvas = felt(CARD).convert("RGBA")

    # A vignette, so the wordmark has somewhere dark to sit and the cloth has an edge.
    vignette = Image.new("L", (w, h), 0)
    ImageDraw.Draw(vignette).ellipse(
        (-w * 0.15, -h * 0.35, w * 1.15, h * 1.35), fill=110
    )
    vignette = vignette.filter(ImageFilter.GaussianBlur(w * 0.05)).point(lambda v: 150 - v)
    canvas.paste(Image.new("RGBA", (w, h), (0, 0, 0, 255)), (0, 0), vignette)

    # Tall enough to be read as cards, and hung low enough that the bottom edges run off
    # the frame — a fan that fits inside the picture reads as a diagram of a fan.
    cards = fan(round(h * 0.47))
    canvas.alpha_composite(cards, ((w - cards.width) // 2, round(h * 0.40)))

    draw = ImageDraw.Draw(canvas)
    title = ImageFont.truetype(str(WORDMARK_FONT), 128)
    body = ImageFont.truetype(str(BODY_FONT), 36)

    # Tracked caps, like the home screen. PIL has no letter-spacing, so the glyphs are placed
    # one at a time — which is also the only way to centre a tracked line honestly.
    tracking = 18
    name = "VINTO"
    widths = [draw.textlength(c, font=title) for c in name]
    line = sum(widths) + tracking * (len(name) - 1)
    x = (w - line) / 2
    for glyph, glyph_w in zip(name, widths):
        draw.text((x, h * 0.13), glyph, font=title, fill=LEAF_GOLD, anchor="lt")
        x += glyph_w + tracking

    draw.text(
        (w / 2, h * 0.325),
        "Lowest hand wins.",
        font=body,
        fill=FELT_INK + (0xCC,),
        anchor="mt",
    )
    return canvas.convert("RGB")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)

    for name, px in ICONS.items():
        icon(px, ICON_GLYPH, round_corners=name == "apple-touch-icon.png").save(OUT / name)
        print(f"{name}: {px}px")

    icon(512, MASKABLE_GLYPH, round_corners=False).save(OUT / "icon-512-maskable.png")
    print("icon-512-maskable.png: 512px, mark inside the safe circle")

    share_card().save(OUT / "share-card.png", optimize=True)
    print(f"share-card.png: {CARD[0]}x{CARD[1]}")


if __name__ == "__main__":
    main()
