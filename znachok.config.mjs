/**
 * znachok — every store's app icon, from one master SVG.
 *
 *     node ../znachok/bin/znachok.mjs --config znachok.config.mjs
 *
 * Re-run after any edit to `brand/vinto-icon.svg`; the PNGs it writes under `brand/icons/` are
 * committed, so nothing at build time depends on this.
 *
 * ANDROID IS DELIBERATELY OFF. `tools/make-launcher-icons.py` already generates the adaptive,
 * legacy and monochrome mipmaps into `androidApp/src/main/res/`, they ship in the APK today, and
 * the composition here was authored to match them rather than to replace them. Turning `android`
 * on would write a second, unused set into `brand/icons/android/` and leave two pipelines
 * claiming the same output — the exact failure `make-launcher-icons.py`'s own header records
 * having hit once already, when its RES path went stale after the `androidApp` split and it
 * silently wrote icons nobody read.
 *
 * The day that script goes, flip this to `true`, copy `brand/icons/android/` into
 * `androidApp/src/main/res/`, and delete it — but that is a change with a before-and-after to
 * look at on a launcher, not a tidy-up to fold into a release.
 *
 * `play` is on because the 512² store icon is a thing nothing else in this repository produces,
 * and `vydanne` looks for it at `brand/icons/play/icon-512.png`.
 */
export default {
  master: './brand/vinto-icon.svg',
  out: './brand/icons',

  // Unused while `android` is off, but it must agree with the master's ground for the day it is
  // switched on: Android's parallax slides the foreground over this colour, and a mismatch shows
  // as a seam at the edge of the icon rather than as an obviously wrong colour.
  androidBackground: '#1B5E43',

  themes: {
    light: {}, // the master as-is: the orange V on the felt

    /**
     * iOS 18's dark appearance. A real step down rather than a dimmed copy.
     *
     * The ground goes to the deck's own `FELT_DARK` (`tools/make-card-faces.py`), which is the
     * colour the table already uses where it needs to recede — so a dark icon and a dark table
     * are the same green, not two guesses at one. The V lifts instead of staying put: brand
     * orange on near-black holds its edge, but it reads heavy at icon size, and the whole point
     * of the dark appearance is that the glyph is what the eye lands on.
     */
    dark: {
      '1B5E43': '0E3428', // felt → felt dark
      E8791E: 'FF9040', // the V, lifted a step so it sings on the darker ground
    },
  },

  targets: { ios: true, android: false, play: true },
};
