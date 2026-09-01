/**
 * zdymak — the store's *media* for Vinto: screenshots, the App Preview, the Play feature graphic.
 * Its other half is `vydanne.config.mjs`, which pushes the listing text and uploads these files.
 *
 *   npx zdymak specs                      # the exact dimensions, printed from the code
 *   npx zdymak capture --platform android # drive the app and take the shots
 *   npx zdymak build --clean              # screenshots + videos for every device below
 *
 * Pinned to zdymak 0.22.0 — the version `.claude/skills/zdymak/SKILL.md` was vendored from.
 * Behaviour here is version-specific (the output layout changed at 0.15), so bump both together.
 *
 * **`npx zdymak capture` cannot drive this app yet.** Android capture works by
 * `am start --es <arg> <state>`, and `MainActivity` reads no such extra — it reads a deep link and
 * nothing else. Until that handle exists, `capture` is the one verb here that does not apply and
 * the scene ids below are names for shots taken by hand. Everything else works on any folder of
 * PNGs. See the note at the foot of this file.
 */
export default {
  // The table's own palette, from `composeApp/.../theme/VintoTheme.kt` rather than picked to
  // match it — a caption scrim in a different green than the felt is the tell that marketing
  // was made somewhere the app was not.
  brand: {
    ink: '#0A2A1D', // FeltDarkBottom — the scrim and letterbox fill
    title: '#F2F5F0', // FeltInk
    sub: '#F2DFA6', // LeafGold
    name: 'Vinto',
    tagline: 'Hold less. Know more.', // the app's own tagline, not a new one invented for a store
    endline: 'Call it, and hold the lowest hand.',
    endsub: 'Free · no ads · no account',
    logo: './tools/brand/vinto-mark.png',
  },

  // Where captures land. One folder per platform, because a Play screenshot may not wear an
  // iPhone bezel and an App Store one taken on Android would misrepresent the app.
  screenshotsDir: './marketing/captures/ios',

  /**
   * The narrative, and it is the game's own order of discovery rather than a feature list:
   * what the goal is, what a turn costs you, that the app teaches itself, that other people
   * can sit down, and what winning looks like.
   *
   * Each `id` is the screen it shows. They double as capture state names for the day the
   * handle exists, which is why they are screens and not slogans.
   */
  scenes: [
    { id: 'home', title: 'Hold less. Know more.', sub: 'Five cards. You may look at two.', move: 'pushInSlow' },
    { id: 'table', title: 'Draw, or take the discard.', sub: 'Then swap, or play the card’s action.', move: 'driftUp' },
    { id: 'teach', title: 'Learn by playing a round.', sub: 'Not by reading the rules.', move: 'pullBack' },
    { id: 'lobby', title: 'Or sit down with friends.', sub: 'A six-character code is the whole invitation.', move: 'pushIn' },
    { id: 'score', title: 'Call Vinto when you are lowest.', sub: '+3 if you are right. −1 if you are not.', move: 'pullBackSlow' },
  ],

  // Nothing at the top level: each store gets its own encode from the `devices` block, because
  // Apple and Google want opposite things out of the same five scenes.
  targets: [],

  devices: {
    // App Store. Marketing styling is expected here — frames, headlines, a background.
    iphone: {
      capturesDir: './marketing/captures/ios',
      videos: [{ target: 'appstore-preview' }], // full-bleed, 15–30s, no bezel, or Apple rejects it
      screenshots: [
        { target: 'appstore-iphone-6.9', style: 'premium' },
        { target: 'appstore-iphone-6.5', style: 'premium' },
      ],
    },
    ipad: {
      // Required because the app ships on iPad: `composeApp` has no phone-only gate, and the
      // landscape layout in `TableLayout.forScreen` is written for exactly this screen.
      capturesDir: './marketing/captures/ios-ipad',
      screenshots: [{ target: 'appstore-ipad-13', style: 'premium' }],
    },

    // Google Play. The opposite rules: no device frames, no added text, no backgrounds on the
    // uploaded shots. Two sets from one capture — `-plain` is what gets uploaded, the styled one
    // is for the website — because a rejection here costs a review cycle.
    android: {
      capturesDir: './marketing/captures/android',
      videos: [{ target: 'play-promo' }], // Play takes a YouTube URL; keep it silent unless music is cleared
      screenshots: [
        { target: 'play-phone', dir: 'play-phone-plain', style: 'bleed', caption: false, theme: { anchor: 'top' } },
        { target: 'play-tablet', dir: 'play-tablet-plain', style: 'bleed', caption: false, theme: { anchor: 'top' } },
        { target: 'play-phone', style: 'premium' }, // the styled set, for the site
        { target: 'play-feature-graphic' }, // required by Play even with no video
      ],
    },
  },

  theme: {
    bgTop: '#14442F', // FeltDarkTop
    bgBottom: '#0A2A1D', // FeltDarkBottom
    glowAlpha: 0.16,
    vignette: 0.3,
  },

  sceneDur: 3.2, // 5 scenes ≈ 16s, inside Apple's 15–30s window with the crossfades
  xfade: 0.32,

  // `store-assets/<locale>/<target>/…` since zdymak 0.15. vydanne reads different roots, so
  // `npx vydanne bridge` runs between this and `vydanne fill` — every time, or the store keeps
  // whatever was bridged last and reports success.
  out: './store-assets',
};

/**
 * To make `npx zdymak capture --platform android` work, `MainActivity` needs to read a state
 * extra and open that screen — roughly `intent.getStringExtra("vinto.screen")` mapped to the
 * `Screen` the name picks, beside the `offerOpenedLink` call that already reads a deep link.
 * iOS capture needs the same as a launch argument in `MainViewController`.
 *
 * It is deliberately not added here, because it is a way to open any screen from outside the app
 * and that is a decision about the shipped build rather than about marketing: debug-only keeps
 * the release surface unchanged and means captures come from a debug build, which is what the
 * store sees; always-on captures the real thing and adds one entry point to it. Neither is
 * obviously right and it is not a call to make silently.
 */
