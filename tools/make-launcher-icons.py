#!/usr/bin/env python3
"""Generate the Android launcher icons from the web app's mark.

The source is `tools/brand/vinto-mark.png` — the orange V the web client shipped,
so the phone and the browser answer to the same mark. It is 144 px of flat #FF6000 on
transparency and the only artwork there is; everything below is layout, not drawing.

Run from anywhere:

    python3 tools/make-launcher-icons.py

It rewrites `composeApp/src/androidMain/res/mipmap-*`. Re-run it when the mark
changes; nothing at build time depends on it, the generated PNGs are committed.

Three sets come out, because Android has asked for three different things over time:

  * `mipmap-anydpi-v26/ic_launcher.xml` — the adaptive icon (API 26+). Two layers the
    launcher masks to whatever shape it likes, so the foreground keeps its content
    inside the guaranteed-visible 66 dp circle of a 108 dp canvas. The V is 50 dp tall,
    which is the largest that fits that circle at its aspect ratio: any taller and a
    circular mask starts eating the tips of the arms.
  * `mipmap-<density>/ic_launcher.png` and `ic_launcher_round.png` — the legacy pair,
    still used on API 24–25, which this app supports. Full-bleed, since no mask is
    coming: the shape has to be in the pixels.
  * `ic_launcher_monochrome.png` — themed icons (API 33+). Only its alpha is read; the
    launcher tints it with the wallpaper palette.
"""

from pathlib import Path

from PIL import Image, ImageDraw

# tools/ sits at the repository root, which is also the Gradle root.
ROOT = Path(__file__).resolve().parents[1]
# The mark comes from the retired web client — the same V the site used.
SOURCE = ROOT / "tools" / "brand" / "vinto-mark.png"
# `androidApp`, not `composeApp`. AGP 9 refuses to let an Android *application* be a KMP
# module, so the application was split out and the launcher icons went with it — and this
# path was left behind pointing at a directory that no longer holds them. Running the
# script then silently created a second, unused set beside the real ones, which is how it
# was found. The manifest that reads them is `androidApp/src/main/AndroidManifest.xml`.
RES = ROOT / "androidApp" / "src" / "main" / "res"

# The rail — the dark band the app's own controls sit on (`theme/VintoTheme.kt`). The icon
# is the first frame of the app, and it is the colour the window opens on.
BACKGROUND = (0x1B, 0x24, 0x30, 0xFF)

# Density buckets, as multiples of mdpi.
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

LEGACY_DP = 48
ADAPTIVE_DP = 108

# Fractions of the canvas the mark occupies, by height.
ADAPTIVE_GLYPH = 50 / 108  # inside the 66 dp safe circle
LEGACY_GLYPH = 0.64
ROUND_GLYPH = 0.60


def mark(height: int) -> Image.Image:
    """The V, resampled to `height` and with its edges re-sharpened.

    The source is small enough that most targets are an upscale, and a smooth interpolation
    of a hard-edged flat shape reads as a blur. Pushing the alpha away from the midpoint
    afterwards restores an edge roughly where the original had one.
    """
    src = Image.open(SOURCE).convert("RGBA")
    box = src.getbbox()  # the mark bleeds to the top and bottom edges of its own file
    src = src.crop(box)
    width = round(src.width * height / src.height)
    scaled = src.resize((width, height), Image.LANCZOS)

    r, g, b, a = scaled.split()
    a = a.point(lambda v: max(0, min(255, round((v - 128) * 3 + 128))))
    return Image.merge("RGBA", (r, g, b, a))


def centered(canvas: Image.Image, glyph: Image.Image) -> None:
    canvas.alpha_composite(
        glyph,
        ((canvas.width - glyph.width) // 2, (canvas.height - glyph.height) // 2),
    )


def adaptive_foreground(px: int) -> Image.Image:
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    centered(canvas, mark(round(px * ADAPTIVE_GLYPH)))
    return canvas


def monochrome(px: int) -> Image.Image:
    canvas = adaptive_foreground(px)
    white = Image.new("RGBA", canvas.size, (0xFF, 0xFF, 0xFF, 0))
    white.putalpha(canvas.split()[3])
    return white


def legacy_square(px: int) -> Image.Image:
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    ImageDraw.Draw(canvas).rounded_rectangle(
        (0, 0, px - 1, px - 1), radius=round(px * 0.222), fill=BACKGROUND
    )
    centered(canvas, mark(round(px * LEGACY_GLYPH)))
    return canvas


def legacy_round(px: int) -> Image.Image:
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    ImageDraw.Draw(canvas).ellipse((0, 0, px - 1, px - 1), fill=BACKGROUND)
    centered(canvas, mark(round(px * ROUND_GLYPH)))
    return canvas


def main() -> None:
    for density, factor in DENSITIES.items():
        out = RES / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        legacy_px = round(LEGACY_DP * factor)
        adaptive_px = round(ADAPTIVE_DP * factor)

        legacy_square(legacy_px).save(out / "ic_launcher.png")
        legacy_round(legacy_px).save(out / "ic_launcher_round.png")
        adaptive_foreground(adaptive_px).save(out / "ic_launcher_foreground.png")
        monochrome(adaptive_px).save(out / "ic_launcher_monochrome.png")
        print(f"{density}: legacy {legacy_px}px, adaptive {adaptive_px}px")


if __name__ == "__main__":
    main()
