Every task that changes behaviour starts with a test watched failing on the code as it stands.
Builds and device runs go through `heavy-lock.mjs`, one at a time. **[deep]** marks the step to
hand one rung up the escalation ladder before it is built on.

## 1. The hand-built reporter, mended where it stays

- [x] 1.1 `debug_meta` inside the event rather than after its closing brace.
      `CrashFramesTest.theMappingIsNamedByTheEventItself` parses the body as Sentry does; it
      failed on the old writer with "Expected EOF after parsing, but had `,`" and passes now.
      This is what the web and the desktop keep, and what Android 1.1 sends until 2.x ships

## 2. Android through `sentry-android`

- [x] 2.1 Tests first, on the JVM against `io.sentry` core types: the options a pure
      `vintoSentryOptions()` builds (`sendDefaultPii` false, no screenshot or view hierarchy,
      no tracing, `release = vinto@<version>`, `dist = <build number>`, the environment), and the
      `beforeSend` scrubber — an event carrying the SDK's default `user.id`, a device id and
      name, a server name and a room code in its message comes out with none of them, and keeps
      the surface tag and the deal's address. Watch both go red.
      `PhoneReportsTest`, 10 tests, each event through every processor the options carry and then
      `beforeSend`. Red on a do-nothing stub, 8 of 10, each for its own reason (no `beforeSend`,
      no DSN, the server name attached, sessions on); the two that passed assert 8.55.0 defaults
      already off (tracing, replay) or on (the uncaught handler) and stay as guards. Green since.
      Also pinned: sessions off (each carries the install id as `did`, D5), the boot time cleared,
      and an earlier run's ANR or native crash not filed under today's screen. The screenshot and
      view hierarchy are `SentryAndroidOptions`-only, so they are set beside `SentryAndroid.init`
      and are not under this JVM test (D2, as built)
- [x] 2.2 `sentry-android` 8.55.0 (gulnya's pin) on `composeApp`'s `androidMain`; the plugin keeps
      `autoInstallation` off and the mapping upload as it is.
      Without `sentry-android-replay`, and with the plugin's bytecode instrumentation off (D2).
      The bundle's `sentry-external-modules.txt` lists core, android-core, ndk and native-ndk, no
      replay. The upload task still runs on `bundleRelease`; on 2026-10-11 it failed 403, because
      the only token on this Mac (`~/.sentryclirc`, rewritten that day) has `event:read` and
      `project:read` and no `project:releases` — so mapping `2f229fa5-700f-3c14-9e86-db55f185a431`
      (build 676) is not in Sentry
- [x] 2.3 `VintoApplication` starts the SDK before anything draws, behind `isEmulatedDevice`;
      the SDK's auto-init providers removed in the manifest; `AndroidStorage.attach` moves with it
      if the reporter still needs it there.
      It does (the version and the environment are read through it), so it moved, and
      `MainActivity` does neither now. The merged release manifest names `VintoApplication` and has
      neither provider; its permissions are unchanged. The NDK's `SentryNdkPreloadProvider` merges in
      and does nothing without `io.sentry.ndk.preload` meta-data, which Vinto does not set
- [x] 2.4 `Crashes.report` → `Sentry.captureException` with the surface tag and the deal's
      address; the SDK's uncaught-exception integration replaces `installCrashHandler` on Android.
      Through `CrashPipe.Sdk` (`CrashPipe.android.kt`), gated by the same `ReportBudget` the
      envelope uses; the address is an event processor reading `Crashes.where()` and `Where` when an
      event is made, so an uncaught exception carries it too
- [x] 2.5 Delete what the SDK now does: `ProguardUuid.android.kt`, the Android
      `CrashHandler` actual, the vault's pending envelope on Android. `ProguardUuid` and
      `debug_meta` stay only if a non-Android target still needs them (none does — remove both).
      All gone, with the common `expect` and its three `null` actuals; `installCrashHandler` and
      `awaitCrashReport` became `CrashPipe.ByHand`'s two functions on the targets that keep the envelope
- [x] 2.6 `ACrashCanBeReadTest`, `CrashInstallTest`, `AReportSaysWhereItHappenedTest` read again
      for what is now true on Android, and corrected rather than deleted where they still hold.
      `CrashInstallTest` checks the Application, the manifest and the removed providers for Android
      in place of `MainActivity`; `CrashEnvironmentTest` reads `VintoApplication` for the storage-first
      order; the other two say what holds now. In `CrashFramesTest` the two mapping tests went with the
      field, and the parse-as-Sentry-does test now holds the event as one JSON object
- [x] 2.7 Measure the release APK and AAB before and after; write the figure into `design.md`.
      Risks, "APK size": AAB +1,964,526 bytes (+24.6%), arm64 download +588,025 (+17.3%), most of
      it `libsentry.so`

## 3. iOS through `sentry-cocoa`, and the bridge

- [ ] 3.1 `project.yml`: the `Sentry` package (`from: "9.0.0"`, as the games) and the target
      dependency; `xcodegen generate`
- [ ] 3.2 `iOSApp.swift`: `SentrySDK.start` in `init`, after `WarmUpFirstFrame`, behind Kotlin's
      `isEmulatedDevice`; the options mirror 2.1, with the same `beforeSend` rules
- [x] 3.3 **[deep]** Settle the bridge's event shape against `sentry-cocoa` 9.x's source: how an
      event carries frames at Kotlin's addresses *and* the debug images for them, so the dSYM can
      be applied. Written into `design.md` D4 before any code — done 2026-10-11 against 9.29.2
      and the pinned 9.27.0, cross-checked with sentry-kotlin-multiplatform, NSExceptionKt, the
      Kotlin/Native runtime and the server's symbolicator; D4 carries the shape and every citation
- [ ] 3.4 The Kotlin half, test first on the simulator (`iosSimulatorArm64Test`): the value D4
      names — for the throwable and each cause, the qualified type, the message and the frames
      (`getStackTraceAddresses()`, each with the `game.vinto.` in-app flag), the fatal flag, the
      surface and the deal's address. The leading constructor frames are dropped by matching the
      address printed in each `getStackTrace()` line (the stored address minus one) to the stored
      list, never by index; a line that does not match drops nothing. Tests: a thrown
      `IllegalStateException` arrives with a non-empty list whose first frame is the `throw`
      site and no `#<init>` frame; a cause arrives as a second entry after the throwable; the
      in-app flag is true for a `game.vinto.` frame and false for a stdlib one; `report` is
      fatal = false and the hook is fatal = true
- [ ] 3.5 The Swift half, per D4: the closure registered at start-up; a handled report as one
      hand-built `Event` through `capture(event:block:)` — the exceptions with Kotlin's frames,
      one `SentryThread` carrying them with `imageAddress` from `dladdr`, no `debugMeta` of our
      own; a fatal report as a `KotlinException: NSException` whose `callStackReturnAddresses`
      are Kotlin's, handed to `NSGetUncaughtExceptionHandler()` after the scope is set, then
      return and let the runtime abort — no `exit`, no flush on that path; the fallback when the
      handler is nil (capture, `flush(timeout: 2)`, the `vinto.kotlin_crashed` tag) and the
      `beforeSend` rule that drops a fatal event carrying it. The frame and event shape under a
      test where one can reach it; what only a device can show is 5.x
- [ ] 3.6 Delete the iOS `CrashHandler` actual's envelope path and `awaitCrashReport` there

## 4. iOS symbols from the archive

- [ ] 4.1 One release check, written once in gulnya with asilak's
      `every-shipped-binary-has-its-dsym` (they propose the same thing): every Mach-O slice in an
      `.xcarchive` (`dwarfdump --uuid`) is in Sentry *with debug information*; an executable under
      the same id fails. Test first against recorded Sentry answers
- [ ] 4.2 `Scripts/archive-ios.mjs` (Node, not shell — the repository's tooling rule): the
      `xcodebuild archive` from `VERSIONING.md`, then `sentry-cli debug-files upload` from the
      finished `.xcarchive`, then 4.1. `VERSIONING.md` points to it
- [ ] 4.3 The post-build upload phase in `project.yml` goes, or becomes a guard that a Release
      build made outside the script cannot pass unnoticed
- [ ] 4.4 Prove it on the next archive: the check passes, and Sentry lists the app's dSYM with
      `debug` among its features

## 5. Proof, read back

- [ ] 5.1 A release build on an Android phone and on an iPhone each send one deliberate
      `TableTrouble` report and one deliberate uncaught Kotlin exception. Emulators and simulators
      send nothing by design, so this needs real hardware
- [ ] 5.2 `node ~/sources/gulnya/tools/sentry-readable.mjs` reads those builds back as readable,
      on both phones
- [ ] 5.3 The same events, opened in Sentry: no `user`, no device id or name, the surface tag and
      the deal's address present, the Kotlin type and message in the title, and one event — not
      two — for the uncaught exception on iOS

## 6. The record

- [ ] 6.1 `add-live-analytics/design.md` §A9: a note at its head that this change supersedes it
      for the phones and that it stands for the web
- [ ] 6.2 `docs/kotlin/RELIABILITY.md` §6m, `docs/kotlin/ARCHITECTURE.md` §2 (the "nothing
      identifying" row names the new scrubbing test), `docs/kotlin/README.md` §1f (the 8.2 row),
      `TRAPS.md` (a substring test of a wire format proves nothing about where the field sits)
- [ ] 6.3 `ship-and-operate` 3.5 closed against this change
- [ ] 6.4 Owner: "Prevent storing of IP addresses" on the `vinto` Sentry project (D5)
