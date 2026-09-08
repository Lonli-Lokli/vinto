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
    // The mark IS the V, so the word carries on from it: the lockup reads "V into!" as one
    // wordmark rather than showing a V and then spelling Vinto beside it, which said the letter
    // twice. `graphic.mjs` sets the name 26px right of the logo, which is the join.
    name: 'into!',
    tagline: 'Hold less. Know more.', // the app's own tagline, not a new one invented for a store
    endline: 'Call it, and hold the lowest hand.',
    endsub: 'Free · no ads · no account',
    logo: './tools/brand/vinto-mark.png',

    // The mark is the V of the word, so the type has to sit against it rather than beside it.
    // zdymak's default 26 is spacing for a logo that is a separate app icon; here it left a gap
    // wide enough to read "V into!" as two things.
    // A RATIO of the icon, so the join holds at 92px on the feature graphic and at 200px in the
    // reel bookend. Negative because the mark has its own padding and the word has to sit inside
    // it: at zdymak.s default the two read as "V into!" rather than as one word.
    lockupGap: -0.2,

    // The deck itself, fanned along the bottom. A feature graphic made of a logo, a tagline and
    // a phone is the same graphic every game on the shelf has; these five cards are the one
    // thing on it that could not belong to another game. They are the app's own art — the same
    // faces `card_*.xml` draws — rather than something drawn for a store.
    // Four, not the whole deck, and placed in the gap between the words and the phone: the
    // tagline owns the left of this graphic and must not be drawn over. The hero is drawn after
    // the fan, so the phone overlaps the right-hand card and the two read as one arrangement
    // rather than as two things that happen to be on the same picture.
    fan: [
      './tools/brand/card-renders/card_7.png',
      './tools/brand/card-renders/card_joker.png',
      './tools/brand/card-renders/card_q.png',
    ],
    // Raised and enlarged: at 240 tall and sitting on the baseline they left the whole middle of
    // the graphic empty green, with the tops of the cards below the wordmark. Now they rise to
    // the wordmark.s own line and run off the bottom edge, which is what makes them read as a
    // hand on a table rather than as four stickers.
    fanX: 622,
    fanY: 400,
    fanHeight: 250,
    fanSpread: 0.09,

    // The block hangs from the top by default, which left the bottom third of the left column
    // empty while the cards filled the middle. Down 40 centres it against them.
    textOffsetY: 40,
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
    { id: 'plan', title: 'The last round is a team effort.', sub: 'Plan it together. The caller cannot see it.', move: 'driftUp' },
    { id: 'score', title: 'Call Vinto when you are lowest.', sub: '+3 if you are right. −1 if you are not.', move: 'pullBackSlow' },
  ],

  // Nothing at the top level: each store gets its own encode from the `devices` block, because
  // Apple and Google want opposite things out of the same five scenes.
  targets: [],

  devices: {
    // App Store. Marketing styling is expected here — frames, headlines, a background.
    iphone: {
      capturesDir: './marketing/captures/ios',
      // No `videos` here on purpose. A video built from this device's *screenshots* is a camera
      // move over stills, and the store slot it would fill wants footage from inside the app —
      // so the only preview this config makes is the `reel` above, from recorded clips.
      screenshots: [
        { target: 'appstore-iphone-6.9', style: 'framed' },
        // `size` pinned, because the target's default is 1242x2688 and Apple's current
        // specification for the 6.5" class is 1284x2778 — the older size is not on the list any
        // more. Found the hard way on the IAP review screenshot, which was refused with "The
        // dimensions of one or more screenshots are wrong" for the equivalent mistake one class
        // up; this set had the same fault waiting for the first listing upload.
        { target: 'appstore-iphone-6.5', size: [1284, 2778], style: 'framed' },
      ],
    },
    ipad: {
      // Required because the app ships on iPad: `composeApp` has no phone-only gate, and the
      // landscape layout in `TableLayout.forScreen` is written for exactly this screen.
      capturesDir: './marketing/captures/ios-ipad',
      screenshots: [{ target: 'appstore-ipad-13', style: 'framed' }],
    },

    // Google Play. The opposite rules: no device frames, no added text, no backgrounds on the
    // uploaded shots. Two sets from one capture — `-plain` is what gets uploaded, the styled one
    // is for the website — because a rejection here costs a review cycle.
    android: {
      capturesDir: './marketing/captures/android',
      // As with the iPhone: the promo is a `reel` entry from real footage, not a pan over stills.
      screenshots: [
        { target: 'play-phone', dir: 'play-phone-plain', style: 'bleed', caption: false, theme: { anchor: 'top' } },
        { target: 'play-tablet', dir: 'play-tablet-plain', style: 'bleed', caption: false, theme: { anchor: 'top' } },
        { target: 'play-phone', style: 'premium' }, // the styled set, for the site
        { target: 'play-feature-graphic' }, // required by Play even with no video
      ],
    },

    /**
     * The review screenshot App Store Connect will not create an in-app purchase without.
     *
     * Not a listing asset — it never reaches a shopper. It goes in the purchase's own slot, and
     * a reviewer looks at it once to see where in the app the thing being sold appears. So it is
     * here rather than uploaded by hand for the reason every other image is here: **no alpha** is
     * a store rule, this is the tool that knows it, and a flatten written into an npm script is a
     * rule kept somewhere nobody looks.
     *
     * `bleed` and `caption: false` — the same pairing the Play uploads use, and for a stricter
     * version of the same reason. Play merely dislikes a frame; here a bezel or a headline would
     * be answering "where is this in your app?" with a marketing graphic.
     *
     * Its own `scenes`, because this device has ONE capture and it is not one of the six the
     * listing tells its story with. `IapShotTest` renders it — the real settings screen, with the
     * offer the console is about to make and deliberately no price in it.
     */
    iap: {
      capturesDir: './marketing/captures/iap',
      scenes: [{ id: 'support-review' }],
      screenshots: [
        {
          target: 'appstore-iphone-6.9',
          // `dir` keeps it out of `appstore-iphone-6.9/`, which is the listing set `vydanne
          // bridge` uploads — this must never be mistaken for one of the ten a shopper sees.
          dir: 'iap-review',
          // NO `size` OVERRIDE — the target's own 1320x2868, and that is the whole lesson here.
          //
          // This said `size: [1290, 2796]`, which zdymak lists among the 6.9" slot's accepted
          // sizes, and App Store Connect answered "The dimensions of one or more screenshots are
          // wrong." The file was faultless in every other respect. Apple's current screenshot
          // specification lists ONE size per display class — 6.9" is 1320x2868 — and 1290x2796
          // (the old 6.7") is not on it any more. An `accepts` list that has not caught up is
          // exactly how a retired size gets chosen on purpose.
          //
          // The rule for a review screenshot is "any of the screenshot specifications your app
          // supports", and the safest reading of that is the size the listing itself uses.
          style: 'bleed',
          caption: false,
        },
      ],
    },
  },

  /**
   * The App Store's preview slot, and the only asset here that is not a picture.
   *
   * Apple's slot wants **footage from inside the app**: full-bleed, no device frame, 15–30
   * seconds. So this is built from `zdymak capture --record` clips — the app actually playing,
   * driven through the same six scenes the screenshots use — rather than a camera move over a
   * still, which is what the screenshot reel does and what Apple's own guidance rules out for
   * this slot. Captions are omitted for the same reason: the store draws its own title beside
   * the video, and a burnt-in one reads as an advert rather than the app.
   */
  reel: [
    {
      name: 'appstore-preview',
      size: [886, 1920],
      level: '4.0',
      sceneDur: 5.2, // 5 beats ≈ 26s: past Apple’s floor with room, and under its ceiling
      transition: 'dissolve',
      theme: { bleed: true, frame: false },
      music: { path: './marketing/music/bed.mp3', volume: 0.75, fadeIn: 0.8, fadeOut: 1.5 },
      // A film with a shape, in five beats: the front door, a round the bots play out, the
      // final round with the coalition's board open, what it came to, and the way to a table
      // with friends on it. The two stills are deliberate bookends — zdymak gives an `image`
      // segment a slow push-in, so neither of them freezes — and everything between them is
      // real recorded motion. `demo.mov` is the DEMO capture at double speed: the app plays at
      // the pace a player sees and the FOOTAGE is what hurries, because a round sped up in the
      // app would film its own choreography being skipped.
      segments: [
        { image: './marketing/captures/ios/home.png' },
        { clip: './marketing/clips/demo.mov' },
        { clip: './marketing/clips/plan.mov' },
        { clip: './marketing/clips/score.mov' },
        { image: './marketing/captures/ios/lobby.png' },
      ],
    },
    {
      // Play's listing video is a YouTube link rather than a file, so this one is uploaded by
      // hand; it is the same footage cut to Play's frame and kept silent, because a ContentID
      // claim on a music bed can take a listing's video down without warning.
      name: 'play-promo',
      size: [1080, 1920],
      sceneDur: 5.2,
      transition: 'dissolve',
      theme: { bleed: true, frame: false },
      segments: [
        { image: './marketing/captures/ios/home.png' },
        { clip: './marketing/clips/demo.mov' },
        { clip: './marketing/clips/plan.mov' },
        { clip: './marketing/clips/score.mov' },
        { image: './marketing/captures/ios/lobby.png' },
      ],
    },
  ],

  theme: {
    // The captures carry the simulator's own status bar, and 'auto' misread the dark band above
    // it as empty and painted a second one — two clocks, two batteries, one above the other.
    statusBar: false,
    bgTop: '#14442F', // FeltDarkTop
    bgBottom: '#0A2A1D', // FeltDarkBottom
    glowAlpha: 0.16,
    vignette: 0.3,
  },

  // 6 scenes ≈ 19.4s after the crossfades eat into them — Apple's window is 15–30s,
  sceneDur: 3.5,
  // and at 3.2 the finished reel came out 14.7s and was refused. Measured, not budgeted.
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
