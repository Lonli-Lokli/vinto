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

- [ ] 1.1 `/.well-known/assetlinks.json` — **needs the release keystore's SHA-256 fingerprint**
      (`keytool -list -v -keystore …`), which does not exist until 2.1. Goes in
      `composeApp/src/wasmJsMain/resources/.well-known/`, which every deploy publishes
- [ ] 1.2 `/.well-known/apple-app-site-association` — **needs the Apple team id and bundle id**,
      served as `application/json` with no extension
- [ ] 1.3 Verify both from a device: an `https` invitation opens the app rather than the
      browser, on Android and on iOS. **Needs a phone of each kind**, and 1.1/1.2 deployed

## 2. Store releases

- [ ] 2.1 An upload key, and `keystore.properties` beside it. **Needs somebody to generate and
      keep a secret.** Built already: `assembleRelease` signs with the upload key when that file
      exists and with the debug key when it does not, so the path is exercised without it
      (was migrate 8.1)
- [ ] 2.2 A Play Console account and an internal track; an Apple developer account and
      TestFlight. **Needs accounts and money** (was migrate 8.1, 9.10)
- [ ] 2.3 A release job on tags, publishing to both. **Needs 2.1 and 2.2**; R8 and everything
      iOS sit behind the same accounts (was migrate 8.1)
- [ ] 2.4 The iOS privacy manifest and permissions review. **Needs Xcode** (was migrate 8.2)
- [ ] 2.5 Settings — "rate this app". **Needs a published listing.** Deliberately absent rather
      than built and hidden: a review button that opens nothing reads as the app being broken,
      to the one person most inclined to say so in public. Four lines and a `market://` URL the
      day 2.3 ships

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
- [ ] 5.2 A language selector in Settings. **Needs a second translation to select between** —
      and there is one now: `values-ru/` exists with 403 of the 404 strings. This stopped being
      blocked while nobody was looking, and is the one item here that is now ordinary work

## What "done" means

Every task ticked or retired with a reason. Explicitly **not** a release gate: the game ships
when phase 2 is done, and phases 3–5 continue after it. Ticking an item nobody ran on the
hardware it is about is the one thing that would make archiving this wrong (design D5).
