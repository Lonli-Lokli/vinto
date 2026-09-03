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
 * **`npx zdymak capture` drives this app now.** The handle the note at the foot of this file
 * specified is built: `MarketingScene` in `composeApp`, read from an intent extra on Android
 * (debug source set) and a launch argument on iOS. `npm run capture-ios`, `capture-ipad` and
 * `capture-android` pass the five scene ids below; `npm run capture-headless` renders the same
 * five without a device at all.
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
 * `npx zdymak capture` — BUILT, to the design this note used to specify.
 *
 * The owner's call was a **debug-only** handle, so the release surface is unchanged and the store
 * shots come from a debug binary. That is what shipped:
 *
 *   Android  `captureScene(intent)` in `androidApp/src/debug`, with a no-op twin in
 *            `src/release`. A build-variant gate rather than an `if`, because an intent extra is
 *            an entry point any app on the phone can send and a runtime check leaves the reader in
 *            the shipped binary.
 *   iOS      a launch argument read in `MainViewController`. No variant needed: only whoever
 *            starts the process can set one.
 *
 * **The handle names a STATE, not a screen**, which was the important half. `MarketingScene`
 * carries the five ids and `MarketingState.stagedGame` builds the two that are not screens, from
 * a pinned seed with the bots on the calling thread — so the table in the shot is the same table
 * next release, and a listing does not need re-reviewing for a change nobody made.
 *
 * What each scene turned out to need, against this note's own estimate:
 *
 *   home   a screen. As predicted.
 *   teach  a screen. As predicted.
 *   table  the peeks spent and a card drawn — a Queen, as it happens. As predicted.
 *   score  MORE than predicted. Calling Vinto is not the end of a round: the drawn card has to be
 *          dealt with first and the coalition each take one more turn. Stopping at the call leaves
 *          the FINAL ROUND banner up with every hand still face down — a perfectly good picture,
 *          and not this one. It also turned up a real defect: a screen first composed onto an
 *          already-finished round hid the result behind "See the score", which is now open.
 *   lobby  NOT staged, and that is the honest answer rather than a shortfall. Both options this
 *          note listed are still the only two — a live socket during a screenshot run, or a second
 *          implementation of the room's state machine for one picture — and neither is worth it.
 *          The id opens the online MENU, which is a real screen showing what online play offers.
 *
 * `CaptureHandleTest` holds all five. It is worth knowing WHY that test is strict: a broken handle
 * does not crash, it opens the home screen — so a capture run photographs five home screens and
 * the listing gets five identical pictures nobody notices until review. Two drafts of that test
 * passed on the wrong screen before the third one could not.
 *
 * **There is also a device-free path.** `npm run capture-headless` renders the same five states at
 * 1290×2796 through `ImageComposeScene`, which is the same Compose that draws the phone. No
 * simulator, no emulator, no handle — and the shots cannot drift from the app because they are the
 * app. Use it for a quick set; use `zdymak capture` when the shot needs a real device's status bar.
 *
 * **The media pipeline is unblocked.** The seat portraits were the ninja-turtle cast and are
 * original emblems now (`brand/avatars/_shared.md`), so screenshots and the App Preview no longer
 * bake somebody else's characters into the listing.
 */
