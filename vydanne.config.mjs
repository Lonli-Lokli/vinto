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
 * from `PLAY_JSON_KEY_FILE`. Vinto has none of the three yet — task 9.10, blocked on accounts and
 * an upload key, not on anything in this repository.
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
   * FOUR BLOCKS ARE DELIBERATELY ABSENT, and re-adding them with plausible defaults is the bug
   * rather than the fix. Each publishes a *claim* about Vinto, so vydanne refuses silence instead
   * of guessing — and so does this file.
   *
   * * **`privacy`** — App Privacy labels. What Vinto actually sends is narrow and known:
   *   analytics counts with no identifier (`AnalyticsPrivacyTest` proves it) and Sentry crash
   *   reports carrying the deal's `gameId`, round and turn (`CrashReportTest`). Somebody has to
   *   read those two and declare them; "accesses" is not "collects".
   * * **`ageRating`** — only needed above 4+. Vinto is a card game with no chat, no user content
   *   and no gambling; whether that is 4+ needs a person's judgement, not mine.
   * * **`accessibility`** — Accessibility Nutrition Labels, declared true/false from what was
   *   actually tested. The app has spoken descriptions throughout and `TouchTargetTest` holds the
   *   44dp floor, which is evidence for some of the answers and not for all of them.
   * * **`export`** — generates a US export-compliance PDF making factual claims, and `filed`
   *   stays false until the report has genuinely been emailed. Vinto uses TLS for transport only
   *   (the room socket and the two beacons), which is the ordinary ENC exception — but the
   *   statement names a team id this project does not have yet.
   *
   * There are no in-app purchases, so `iaps` is absent because there is nothing to declare.
   */
};
