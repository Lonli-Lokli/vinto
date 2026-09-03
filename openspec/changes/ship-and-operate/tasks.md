# Tasks: ship the apps, and operate the live service

Every item here arrived blocked, from `migrate-to-kotlin-multiplatform` or
`add-live-analytics`, and each names what would unblock it. The phases are ordered by what
they produce, not by what they wait on — see design D1.

`docs/kotlin/README.md` §1f is the maintainer-facing version of the same list and should say
the same things; if the two ever disagree, this file is the one under version control as a
plan and §1f is the one somebody reads on a phone.

## 1. Invitations reach the app

The app half is built and tested; what is missing is two files on the website, each naming a
credential nothing here has. Until they exist the `https` links open the site instead of the
app — which is why the `vinto://` scheme is there and works today.

- [~] 1.1 `/.well-known/assetlinks.json` — **written**, carrying the upload key's SHA-256 now
      that 2.1 exists. Still half done, knowingly: with Play App Signing the delivered app is
      signed by Google's key rather than ours, so Google's fingerprint has to go beside this one
      once the Play Console shows it. `docs/kotlin/APP-LINKS.md` has the steps and the verifier
      URL. No code — a file edit and a website deploy
- [x] 1.2 `/.well-known/apple-app-site-association` — written, naming
      `JNHFD8PCM8.app.kupalinka.vinto` and claiming `/r/*` and nothing else. Served as
      `application/json` by an explicit `_headers` rule, because the file has no extension and
      Cloudflare would otherwise guess a type iOS silently refuses. The matching
      `com.apple.developer.associated-domains` entitlement is in the app
- [ ] 1.3 Verify both from a device: an `https` invitation opens the app rather than the
      browser, on Android and on iOS. **Needs a phone of each kind**, and 1.1/1.2 deployed

## 2. Store releases

- [x] 2.1 An upload key, and `keystore.properties` beside it. Generated: 4096-bit RSA at
      `keystore/vinto-upload.jks`, both files gitignored. `bundleRelease` produces a signed .aab
      (verified with `jarsigner`), and its fingerprint is what 1.1 carries. **It exists on one
      machine and nowhere else — back both files up** (was migrate 8.1)
- [~] 2.2 A Play Console account and an internal track; an Apple developer account and
      TestFlight. **The accounts exist and are reachable by API**: Apple holds the app record
      (6803030533, 1.0 PREPARE_FOR_SUBMISSION) with its listing, categories, content rights and
      age rating all pushed, and Play holds `app.kupalinka.vinto` with its listing and icon. What
      has not happened is a BUILD reaching either — TestFlight needs Xcode signed in to the
      developer account on the build machine (automatic provisioning fails with "No Accounts"),
      and the Play internal track needs the .aab uploaded. Neither is a decision; both are one
      person at a keyboard (was migrate 8.1, 9.10)
- [ ] 2.3 A release job on tags, publishing to both. The *versioning* it needs is in place now
      — `Scripts/build-number.sh`, VERSIONING.md, and both platforms reading it — so what is left
      is the workflow. R8 is still off (was migrate 8.1)
- [x] 2.4 The iOS privacy manifest and permissions review. `PrivacyInfo.xcprivacy` declares the
      five collected types (matching `vydanne.config.mjs`) and `NSUserDefaults` under CA92.1 —
      without which an upload is rejected with ITMS-91053. The app asks for no permissions at all
      (was migrate 8.2)
- [x] 2.5 Settings — "rate this game". Built, pointing at listings that are not live yet, which
      reverses the reasoning that kept it out: adding two constants later costs a review cycle per
      store, and the window in which the links are dead is one where only TestFlight and
      Play-internal testers can press them. `storeReviewUrl()` picks per platform; the web and
      desktop builds, which have no store, go to the app's own page

## 3. The gates only a person can walk

- [ ] 3.1 The eight screenshot goldens. **Needs a maintainer's machine and a human looking at
      the images.** `ScreenshotTest` writes them and CI deliberately does not run it — a fresh
      runner would write its own and assert nothing, and glyph rasterisation differs by JVM
      (was ROOM.md §6i step 1)
- [ ] 3.2 The four sounds, listened to once through `./gradlew :composeApp:run`. **Needs ears**
- [ ] 3.3 The animation layer watched on real hardware — an Android phone, and the iOS
      simulator looked at by a person. Compiled and simulator-tested by `kmp-ios`; never
      *watched*. **Needs a phone and a Mac** (was migrate 7.1)
- [ ] 3.4 A four-human table, which is 9.7's second verification and cannot be scripted. The
      scripted half is done against the live deployment (ROOM.md §6q). **Needs four people**
- [ ] 3.5 A native crash on iOS — a signal or a Swift trap, which is what the Sentry KMP SDK
      would add over `setUnhandledExceptionHook`. **Needs Xcode, and a decision on the SDK**
      against its weight in a 3.7 MB wasm bundle; flagged rather than settled in
      `add-live-analytics/design.md` §A9 (was migrate 8.2)
- [ ] 3.6 A crash report arriving in a real Sentry project. The pipe is built and gated
      (`CrashReporterTest`, `CrashInstallTest`, `CrashReportTest`); the DSN is a build input.
      **Needs a build carrying a real DSN, in somebody's hands**

## 4. Reading the live service

- [ ] 4.1 Confirm the account's **current** Workers Analytics Engine allowances — writes/day,
      read allowance, retention. **Needs the Cloudflare dashboard for the account that owns the
      Worker.** `design.md` §A1 carries published figures and says plainly they are not measured
      (was analytics 1.1)
- [x] 4.2 Correct `DEPLOYMENT.md` §7b, which sends the maintainer to the leftover `vinto` Pages
      project rather than the `vinto-web` Worker the site actually is — so following it would
      switch on counting for a dead site and report nothing, and look like it worked. Design D3
- [ ] 4.3 Switch on Cloudflare Web Analytics for the real site. **Needs the dashboard.** Not a
      code change either way, but 4.2 is what makes the instruction lead somewhere
      (was analytics 5.3)
- [ ] 4.4 The three dashboard secrets — `ANALYTICS_TOKEN`, `ANALYTICS_ACCOUNT_ID`,
      `DASHBOARD_KEY`. **Needs the dashboard**, and they can be added from a phone.
      `GET /counts?key=…` is built and gated by `gate-dashboard.mjs` (51 checks); what it cannot
      cover is a single number, the WAE SQL API being the one part of Analytics Engine
      `wrangler dev` does not emulate (was analytics 5.1)
- [ ] 4.5 Revisit the sampling rates in §A8 and the cost model in 2.3 against real volume and
      the actual bill. **Needs a week of traffic**, which can accrue now the room is open
      (was analytics 5.4)
- [ ] 4.6 A load test with 100 concurrent rooms. No longer blocked on a deployment — blocked on
      **the decision to run one against a service people may be playing on**. It cannot go
      against `wrangler dev`, which enforces no CPU limit at all and would measure the laptop
      (was migrate 9.9)

## 5. The two that want a machine rather than a credential

- [ ] 5.1 The corpus replayed on an Android emulator. Wants an instrumented
      `connectedAndroidTest` reading the corpus from an asset, so `androidx.test` — **needs a
      host that can resolve androidx and an emulator in CI**. The iOS half is done: `kmp-ios`
      runs `:shared:client:iosSimulatorArm64Test`, so a whole game is generated and replayed
      through the real harness on Kotlin/Native every run. An hour's work on a machine that can
      build `composeApp` (was migrate 4.8)
- [x] 5.2 A language selector in Settings. The control was built while this was still blocked —
      `Tongue` in `SettingsScreen`, with "follow the device" and the twenty entries of
      `Language.kt` — and what it lacked was anything to choose between. There are twenty now:
      `values-<loc>` for every entry, filled from the English source and checked by
      `tools/check-translations.mjs` (key set, placeholders, quote escaping, double-escaped
      entities)

## What "done" means

Every task ticked or retired with a reason. Explicitly **not** a release gate: the game ships
when phase 2 is done, and phases 3–5 continue after it. Ticking an item nobody ran on the
hardware it is about is the one thing that would make archiving this wrong (design D5).
