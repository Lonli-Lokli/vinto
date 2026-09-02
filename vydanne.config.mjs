/**
 * vydanne — the App Store Connect / Google Play half of Vinto's release. It writes the listing
 * and pushes it; `zdymak.config.mjs` makes the media it uploads.
 *
 *   npx vydanne auth                   # what resolved, from where — run this before debugging a 401
 *   npx vydanne bridge                 # zdymak's output -> the folders `fill` reads. Every time.
 *   npx vydanne preflight              # the completeness gate
 *   npx vydanne fill --apply           # push the listing (dry run without --apply)
 *
 * Pinned to vydanne 0.9.0 — the version `.claude/skills/vydanne/SKILL.md` was vendored from.
 *
 * **No credentials here, ever.** This file is committed and vydanne refuses a keyId or issuerId
 * found in it. The signing key lives at `~/.appstoreconnect/private_keys/AuthKey_<id>.p8`, the
 * ids come from `ASC_KEY_ID` / `ASC_ISSUER_ID` or the `.env` cascade, and Play's service account
 * from `PLAY_JSON_KEY_FILE`.
 *
 * All three RESOLVE (checked 2026-09-02, `npm run store:auth`): they come from the account-level
 * `~/.appstoreconnect/config.json` the whole portfolio shares, so nothing here needs per-repo
 * setup. This note used to say Vinto had none of them and was blocked on accounts — that was
 * true when it was written and has not been true since the studio's other four apps shipped.
 *
 * What IS still missing is one thing per store, and neither is in this file:
 *   Apple — the app record exists (id 6803030533, 1.0 PREPARE_FOR_SUBMISSION). Nothing blocks
 *           the listing; `preflight` names what is left.
 *   Play  — there is NO package record. `inspect --store google` answers
 *           "Package not found: app.kupalinka.vinto", and the Publishing API cannot create one:
 *           somebody has to make the app in the Play Console and upload the first signed bundle
 *           by hand, which also needs the upload key (ship-and-operate 2.1).
 */
export default {
  // Both stores, one identifier: `androidApp/build.gradle.kts` sets `applicationId` and
  // `iosApp.xcodeproj` sets `PRODUCT_BUNDLE_IDENTIFIER` to this same string.
  bundleId: 'app.kupalinka.vinto',

  // The fallback for every locale without its own listing, so it must be populated. British
  // rather than American because that is the English the app is written in.
  primaryLocale: 'en-GB',

  // iOS only. The Compose desktop target exists to look at a UI change quickly (ARCHITECTURE §8),
  // not to ship — so there is no Mac App Store listing to keep in step. Android is the separate
  // `google` block below, not a platform here.
  platforms: ['IOS'],

  /**
   * Only what is actually translated. `composeApp/.../composeResources/` has `values/` and
   * `values-ru/` and nothing else, and a listing in a language the app does not speak sends
   * people to a screen they cannot read.
   *
   * Two things to know before this list grows to match `Language.kt`'s twenty:
   *
   * * **Belarusian has no App Store language.** `be` is in `Language.kt` and Apple has no
   *   listing locale for it, so it can never appear here — vydanne flags it rather than failing
   *   an upload halfway. Google Play does have `be`, so it can go in `google` when translated.
   * * Apple's codes are not the resource folder names. vydanne maps `ru` -> `ru`, `de` -> `de-DE`
   *   and so on; `localeMap` below is only for disagreements, and there are none yet.
   */
  uiLocales: ['en', 'ru'],

  metadataDir: 'fastlane/metadata',

  // FREE, and no in-app purchases in 1.0. `docs/design/MONETIZATION.md` sequences cosmetics —
  // decks, backs, felts, a supporter pack — AFTER store releases exist, and its own rule is that
  // the shop ships with its first premium deck rather than empty. So `iaps: []` is this release's
  // decision, not a permanent one; the day a deck exists this array and the App Store's own IAP
  // records grow together. Price itself is not here and cannot be — pricing is agreement-bound in
  // App Store Connect and vydanne does not touch money.
  iaps: [],

  rating: '4+', // see the ageRating note below before trusting this

  // The two app-level facts that block **Add for Review** and that nothing else in vydanne sets.
  // `npm run store:appinfo` once per app; re-running is a no-op.
  categories: {
    primary: 'GAMES',
    primarySubcategoryOne: 'GAMES_CARD', // it is a card game before it is anything else
    primarySubcategoryTwo: 'GAMES_STRATEGY', // deduction and a called bluff, not a dexterity game
  },

  // "Does the app contain, show or access third-party content?"
  //
  // **UNSET ON PURPOSE, and this is a blocker rather than an oversight.** The four seat portraits
  // in `composeApp/.../composeResources/drawable/avatar_*.png` are anthropomorphic ninja turtles
  // named Leonardo, Raphael, Michelangelo and Donatello, nicknamed Leo, Raph, Mikey and Don
  // (`RoomCore.BOT_NAMES`, `InitializeGame`, `SeatPlate.portraitFor`, and `beat_seats_title` in
  // strings.xml, which puts "Raph, Mikey and Don" in front of the player). That is the Teenage
  // Mutant Ninja Turtles cast, which Nickelodeon/Paramount owns — App Store Review 5.2 and Play's
  // IP policy both refuse it, and the exposure is the developer's rather than the store's.
  //
  // Answering this field either way before the art and the names are replaced would be making a
  // claim about somebody else's property. Replace them, then set `contentRights: false` and the
  // sentence is true. See the note at the foot of this file.
  // contentRights: false,

  // No sign-in wall anywhere — online play asks for a nickname, not an account — so App Review
  // needs no demo account. If that ever changes the credentials go in
  // fastlane/metadata/review_information/{demo_user,demo_password}.txt, which .gitignore excludes
  // because that whole directory is PII.
  reviewContact: { demoAccountRequired: false },

  /**
   * What actually leaves the device. Wider than the rest of the portfolio's apps, and the reason
   * is online play: Niva, Vodar and Palon have no server to talk to, and Vinto has a room.
   *
   *   PRODUCT_INTERACTION  anonymous counts. `AnalyticsPrivacyTest` walks the sealed event
   *                        hierarchy by reflection and fails on any field that is not a number,
   *                        a boolean or an enum — so a room code, a nickname or a token has
   *                        nowhere to sit. Opt-OUT, in Settings, and `consentChanged` discards
   *                        what was buffered rather than flushing it.
   *   CRASH_DATA           Sentry. `CrashReportTest` pins what rides along: the deal's gameId,
   *                        the round and the turn. No identifier.
   *   PERFORMANCE_DATA     the same pipe.
   *   USER_ID              the guest id (`Identity.mintGuestId` — `guest-` + 32 hex digits),
   *                        which persists in the vault and goes to the room as `ownerId`. It is
   *                        minted on the device and tied to nothing, but it IS a persistent
   *                        pseudonymous identifier and declaring otherwise would be false.
   *   OTHER_USER_CONTENT   the nickname. Free text, 1–16 characters after `cleanNickname`, shown
   *                        to everyone in the room and — for a public room — to strangers in the
   *                        `/rooms` list.
   *
   * `tracking: false` is exact: nothing here is joined to data from other companies' apps or
   * sites, and there is no advertising identifier anywhere in the build.
   *
   * All five are collected only for the feature that needs them, and none is linked to a person,
   * because there is no person to link to — there are no accounts.
   */
  privacy: {
    collected: ['PRODUCT_INTERACTION', 'CRASH_DATA', 'PERFORMANCE_DATA', 'USER_ID', 'OTHER_USER_CONTENT'],
    tracking: false,
  },

  /**
   * Accessibility Nutrition Labels. These are CLAIMS made to Apple, so each is stated only where
   * this repo can show its work. Audited 2026-09-02 against the code, not assumed:
   *
   *   voiceover        ~53 contentDescription/semantics sites across 17 files in commonMain.
   *   voiceControl     `TouchTargetTest` asserts every clickable node carries a content
   *                    description AND clears 44dp, which is exactly what Voice Control needs to
   *                    name a target.
   *   largerText       FALSE, and deliberately. 50 `.sp` sites means text is in scalable units,
   *                    but nothing pins that a layout SURVIVES Dynamic Type at its largest — and
   *                    Palon set this false for the same reason. Flip it in the pass that adds
   *                    the test, not before.
   *   sufficientContrast  `ContrastTest` + `ScreenContrastTest`, both schemes, WCAG 1.4.3/1.4.11
   *                    ratios asserted through the theme rather than off the constants.
   *   darkInterface    both schemes ship and the switch is in Settings.
   *   differentiateWithoutColorAlone  the deck was redrawn for this: large-print rank indices on
   *                    every card, four colour FAMILIES rather than four hues, one row per peek.
   *                    A card is read by its index, not its colour.
   *   reducedMotion    genuinely wired, not merely declared — `systemPrefersReducedMotion` has
   *                    real actuals on Android (ANIMATOR_DURATION_SCALE) and iOS
   *                    (UIAccessibilityIsReduceMotionEnabled), resolves through
   *                    `LocalReducedMotion` in `App`, and is READ at the animation sites
   *                    (`CardStage.travel` returns 0, `Progress` takes the still branch).
   *                    Vodar once shipped this unwired, so the check is the read site.
   *   captions / audioDescriptions  there is sound but no speech and no video.
   */
  accessibility: {
    voiceover: true,
    voiceControl: true,
    largerText: false,
    sufficientContrast: true,
    darkInterface: true,
    differentiateWithoutColorAlone: true,
    reducedMotion: true,
    captions: false,
    audioDescriptions: false,
  },

  /**
   * EXEMPT, not "standard" — so there is no `algorithms`/`statement` to write and
   * `npm run store:compliance` prints "nothing to self-classify" and files nothing with BIS.
   *
   * Vinto implements no cryptography of its own. Everything that leaves the device goes over
   * plain HTTPS/WSS — the room socket, the two beacons, Sentry — which is encryption supplied by
   * the operating system's TLS stack and exempt under the EAR. Do NOT copy asilak's "standard"
   * here: that app ships its own end-to-end encryption, which is the only reason it needs the
   * self-classification report. The team id is the studio's, the same one Palon files under.
   */
  export: { encryption: 'exempt', appName: 'Vinto', version: '1.0', teamId: 'JNHFD8PCM8' },

  // The signed .ipa for `npm run store:prerelease`; a directory takes its newest, matching how
  // google.aab works. The build number comes from the archive's own CFBundleVersion, so
  // re-uploading one Apple already holds fails loudly instead of silently.
  ios: { ipa: './dist' },

  // App Preview videos, from zdymak's `appstore-preview` target. Apple wants 15–30s and no bezel;
  // `zdymak.config.mjs` is set up to produce exactly that.
  previews: [
    {
      platform: 'IOS',
      type: 'IPHONE_67', // 6.9"
      file: 'marketing/out/appstore-preview.mp4',
      poster: '00:00:03:00',
      locales: ['en-GB'],
    },
  ],

  // Google Play. Package-scoped: vydanne only ever touches this packageName.
  google: {
    packageName: 'app.kupalinka.vinto',
    metadataDir: 'fastlane/metadata/android',
    defaultLocale: 'en-GB',
    // Stays a closed track. `production` is a refusal in vydanne, not a flag, and that is the
    // right shape: shipping to the public is a person pressing a button. Every store-mutating
    // command is a dry run until `--apply`, so a mistake here costs a diff and not a release.
    track: 'internal',
  },

  /**
   * WHAT IS STILL UNANSWERED, and why each is a person's call rather than a default.
   *
   * Three of the four blocks this note used to list are filled in above — `privacy`,
   * `accessibility` and `export` — each against evidence named at the block. Two remain, and one
   * of them stops the release.
   *
   * **1. `contentRights`, and the art behind it. THIS BLOCKS SUBMISSION.**
   *
   * The four seat portraits are ninja turtles called Leonardo, Raphael, Michelangelo and
   * Donatello, nicknamed Leo, Raph, Mikey and Don. The names are not only in filenames: they are
   * `BOT_NAMES` in `shared/room`, the seats `initializeGame` deals, the mapping in
   * `SeatPlate.portraitFor`, and the string `beat_seats_title` — "Raph, Mikey and Don" — which the
   * tutorial says to the player. Nickelodeon/Paramount owns that cast. App Store Review 5.2 and
   * Play's IP policy both refuse it, screenshots of the table would publish it, and the liability
   * lands on the developer rather than on the store.
   *
   * Nothing in this file can answer around it. What has to change is narrower than "all of it",
   * though, and it is worth being exact about which half:
   *
   *   THE ART IS THE PROBLEM. Copyright protects the character, not one drawing of it, so a
   *   freshly generated picture of a ninja turtle called Michelangelo is a derivative work of the
   *   character however it was made. Four PNGs.
   *
   *   THE SHORT NAMES ARE NOT, ON THEIR OWN. Leo, Raph, Mikey and Don are ordinary diminutives
   *   and none is protectable by itself. What points at the property is the COMBINATION — those
   *   four names on four turtles. Break the species and the names stop pointing anywhere, so the
   *   short forms can stay in `BOT_NAMES`, in `SeatPlate` and in `beat_seats_title`.
   *
   *   THE FULL NAMES SHOULD GO with the turtles. Four Renaissance painters naming four cartoon
   *   opponents is still the pointer even after the shells come off; `InitializeGame` should seat
   *   the short forms as the real names.
   *
   * Until the portraits are replaced the media pipeline should not run at all: `zdymak` would bake
   * them into every screenshot and into the App Preview, and the one screenshot on
   * kupalinka.app/games/vinto is deliberately the home screen for the same reason.
   *
   * **2. `ageRating`.** `rating: '4+'` above is the intent, and the game itself supports it: no
   * gambling, no violence, no chat. What is NOT settled is Apple's user-generated-content
   * question. Online play takes a free-text nickname, `cleanNickname` filters CHARACTERS and
   * length (1-16, letters/digits/space/`-_.'`) and nothing else, and a public room posts that
   * nickname to `/rooms` where strangers read it. There is no word filter, no report path and no
   * block. Guideline 1.2 asks for all three of an app with UGC. Two honest ways out, and both are
   * decisions rather than code someone can add quietly:
   *
   *   - keep public rooms, and add filtering + a report route + a block; or
   *   - ship 1.0 with code-only rooms, which removes the strangers and the surface with them.
   *
   * Answer that before `age-rating --apply`, because the questionnaire asks it directly.
   */
};
