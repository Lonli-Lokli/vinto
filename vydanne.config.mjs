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
  // Every language the APP ships in, so the listing can be read by the same people the app can.
  // It said ['en', 'ru'] while `values-*` held twenty: a German player got a German game behind an
  // English store page, which is the half of localisation nobody sees until they are the one
  // reading it. `vydanne locales` maps these to each store's own codes — Apple and Play disagree,
  // and Play is the one with the surprises (`zh-CN`, `iw-IL`, `in-ID`).
  uiLocales: [
    'en', 'id', 'de', 'es', 'fr', 'it', 'pl', 'pt', 'tr',
    'be', 'ru', 'uk', 'he', 'ar', 'ur', 'hi', 'bn', 'ko', 'ja', 'zh',
  ],

  metadataDir: 'fastlane/metadata',

  // FREE, and no in-app purchases in 1.0. `docs/design/MONETIZATION.md` sequences cosmetics —
  // decks, backs, felts, a supporter pack — AFTER store releases exist, and its own rule is that
  // the shop ships with its first premium deck rather than empty. So `iaps: []` is this release's
  // decision, not a permanent one; the day a deck exists this array and the App Store's own IAP
  // records grow together. Price itself is not here and cannot be — pricing is agreement-bound in
  // App Store Connect and vydanne does not touch money.
  iaps: [],

  /**
   * 4+, and every content question below is genuinely NONE.
   *
   * It was briefly not. For one pass this app declared `userGeneratedContent: true`, because
   * online play took a typed nickname and a public room posted it to `/rooms` where strangers
   * read it — text one player authors and others see, which is what Guideline 1.2 is about. The
   * declaration was correct and the rating went up with it.
   *
   * **The feature changed rather than the answer.** Names are now minted from a fixed vocabulary
   * (`shared/protocol`'s `Nickname.kt`): there is no text field anywhere in the app, and the room
   * refuses any name its own vocabulary could not have produced — so the guarantee holds for the
   * SERVICE and not merely for this build, which matters because a modified client can send
   * anything. Nobody authors anything, so there is nothing to filter, report or block, and `false`
   * is a true statement again.
   *
   * If a text field ever comes back, this is the field that has to change with it, and the 1.2
   * obligations come back at the same time.
   */
  rating: '4+',

  // The two app-level facts that block **Add for Review** and that nothing else in vydanne sets.
  // `npm run store:appinfo` once per app; re-running is a no-op.
  categories: {
    primary: 'GAMES',
    primarySubcategoryOne: 'GAMES_CARD', // it is a card game before it is anything else
    primarySubcategoryTwo: 'GAMES_STRATEGY', // deduction and a called bluff, not a dexterity game
  },

  // "Does the app contain, show or access third-party content?"
  //
  // **Answerable now, and it was the one field blocking submission.**
  //
  // It used to be unset, with a long note explaining why: the four seat portraits were
  // anthropomorphic ninja turtles named Leonardo, Raphael, Michelangelo and Donatello, nicknamed
  // Leo, Raph, Mikey and Don, and those names reached `BOT_NAMES`, `initializeGame`,
  // `SeatPlate.portraitFor` and the tutorial string `beat_seats_title`. One background read
  // "SHELL SHOCK". That is the Teenage Mutant Ninja Turtles cast however it was arrived at, App
  // Store Review 5.2 and Play's IP policy both refuse it, and screenshots of the table would have
  // published it.
  //
  // What replaced them is original and generated in this repository: the four classical elements,
  // each drawn as three engraved lines — fire standing up and wavering, water lying down and
  // rolling, earth dead straight, air curling away — from the deck's own palette, with the seats
  // named Ember, Tide, Dune and Gale to match. The elements are nobody's property; the drawings
  // are this repository's own, and deliberately not a copy of any particular depiction of them.
  // Masters in `brand/avatars/`, converted to vector drawables by
  // `tools/svg-to-drawable.mjs`, and `brand/avatars/_shared.md` records the whole reasoning.
  // Nothing in the build is anybody else's any more, so `false` is now a true statement rather
  // than a convenient one.
  //
  // The one piece of third-party *anything* left is the name Vinto! itself, which is somebody
  // else's game — and that is a licensing question about the app's subject, not third-party
  // content shipped inside it. Every client says so on its first screen and links to
  // <https://vinto.game>, which is what `AttributionTest` holds.
  contentRights: false,

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


    /**
     * A draft app can only be given draft releases.
     *
     * This app has never been published, and Play refuses a `completed` release on any track but
     * internal while that is true — "Only releases with status draft may be created on draft
     * app.", which names neither the track nor the fix. So the release lands in Play Console and
     * waits for a human to start the rollout, which is where an unpublished app's first one
     * belongs. Drop this line the day the app is live and rollouts should begin on upload.
     */
    // 'completed' rolls the build out to the track's testers as the track's one release. It was
    // 'draft' while the app had never been published anywhere, which Play insists on; a draft
    // beside a live release is what that setting produces once a track is live, and it is not
    // what "a build for the testers" means.
    releaseStatus: 'completed',
    metadataDir: 'fastlane/metadata/android',
    defaultLocale: 'en-GB',
    // The signed bundle `prerelease` uploads. A DIRECTORY, so it takes the newest build in it and
    // there is no path to update on every release — the same shape as `ios.ipa` above.
    //
    // `androidApp`, not `composeApp`: since AGP 9 the application half lives in its own module,
    // and `bundleRelease` is a task only that module has. Build it with the version stamped from
    // git, never by hand:
    //
    //     ./gradlew :androidApp:bundleRelease -PversionCode="$(Scripts/build-number.sh)"
    //
    // See VERSIONING.md — Play refuses an upload whose versionCode does not strictly exceed the
    // last one on the track, and the commit count is what guarantees that.
    aab: './androidApp/build/outputs/bundle/release',

    // THE CLOSED TRACK, always — `alpha` is Play's API name for it and the Console calls it
    // "Closed testing". This line said `internal` while its comment claimed to be closed, and the
    // two are different audiences: internal testing is capped at 100 addresses on a list and
    // bypasses review, which makes it the track for checking that an upload works at all. Closed
    // testing is where a build meets people who are going to play it, and it is the one this
    // project ships to.
    //
    // `production` is a refusal in vydanne rather than a flag, and that is the right shape:
    // shipping to the public is a person pressing a button. Every store-mutating command is a dry
    // run until `--apply`, so a mistake here costs a diff and not a release.
    track: 'alpha',
  },

  /**
   * WHAT USED TO BE UNANSWERED HERE, and how each was answered. Both blocked submission; neither
   * does now, and both were closed by changing the app rather than by choosing a kinder answer.
   *
   * **1. `contentRights`.** The seat portraits were derivative of somebody else's characters. They
   * are original and generated now (`brand/avatars/_shared.md`), so the field is `false` and true.
   * The media pipeline is unblocked with it: `zdymak` was barred from running at all, because it
   * would have baked the old art into every screenshot and into the App Preview.
   *
   * **2. `ageRating`, and the user-generated content behind it.** Online play used to take a typed
   * nickname, and a public room posted it to `/rooms` where strangers read it. That is UGC, it
   * obliges an app to ship filtering, reporting and blocking under Guideline 1.2, and it takes the
   * rating off 4+.
   *
   * Three ways out were put to the product owner: build the moderation, hide the public list, or
   * stop taking typed text at all. **The third was chosen**, and it is the only one that removes
   * the category instead of managing it — hiding the public list would have shrunk the audience
   * for a typed name without changing what it is. Names are minted from a shared vocabulary now,
   * and the room refuses anything else, so the claim holds for the service rather than for the
   * build.
   *
   * What is left is ordinary and named by `preflight`: the review contact (PII, so it lives in the
   * gitignored `fastlane/metadata/review_information/`) and the screenshots.
   */
};
