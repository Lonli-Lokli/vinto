Every task that changes behaviour starts with a test watched failing on the code as it stands.
Builds and device runs go through `heavy-lock.mjs`, one at a time. **[deep]** marks the step to
hand one rung up the escalation ladder before it is built on.

## 1. The hand-built reporter, mended where it stays

- [x] 1.1 `debug_meta` inside the event rather than after its closing brace.
      `CrashFramesTest.theMappingIsNamedByTheEventItself` parses the body as Sentry does; it
      failed on the old writer with "Expected EOF after parsing, but had `,`" and passes now.
      This is what the web and the desktop keep, and what Android 1.1 sends until 2.x ships

## 2. Android through `sentry-android`

- [ ] 2.1 Tests first, on the JVM against `io.sentry` core types: the options a pure
      `vintoSentryOptions()` builds (`sendDefaultPii` false, no screenshot or view hierarchy,
      no tracing, `release = vinto@<version>`, `dist = <build number>`, the environment), and the
      `beforeSend` scrubber — an event carrying the SDK's default `user.id`, a device id and
      name, a server name and a room code in its message comes out with none of them, and keeps
      the surface tag and the deal's address. Watch both go red
- [ ] 2.2 `sentry-android` 8.55.0 (gulnya's pin) on `composeApp`'s `androidMain`; the plugin keeps
      `autoInstallation` off and the mapping upload as it is
- [ ] 2.3 `VintoApplication` starts the SDK before anything draws, behind `isEmulatedDevice`;
      the SDK's auto-init providers removed in the manifest; `AndroidStorage.attach` moves with it
      if the reporter still needs it there
- [ ] 2.4 `Crashes.report` → `Sentry.captureException` with the surface tag and the deal's
      address; the SDK's uncaught-exception integration replaces `installCrashHandler` on Android
- [ ] 2.5 Delete what the SDK now does: `ProguardUuid.android.kt`, the Android
      `CrashHandler` actual, the vault's pending envelope on Android. `ProguardUuid` and
      `debug_meta` stay only if a non-Android target still needs them (none does — remove both)
- [ ] 2.6 `ACrashCanBeReadTest`, `CrashInstallTest`, `AReportSaysWhereItHappenedTest` read again
      for what is now true on Android, and corrected rather than deleted where they still hold
- [ ] 2.7 Measure the release APK and AAB before and after; write the figure into `design.md`

## 3. iOS through `sentry-cocoa`, and the bridge

- [ ] 3.1 `project.yml`: the `Sentry` package (`from: "9.0.0"`, as the games) and the target
      dependency; `xcodegen generate`
- [ ] 3.2 `iOSApp.swift`: `SentrySDK.start` in `init`, after `WarmUpFirstFrame`, behind Kotlin's
      `isEmulatedDevice`; the options mirror 2.1, with the same `beforeSend` rules
- [ ] 3.3 **[deep]** Settle the bridge's event shape against `sentry-cocoa` 9.x's source: how an
      event carries frames at Kotlin's addresses *and* the debug images for them, so the dSYM can
      be applied. Written into `design.md` D4 before any code
- [ ] 3.4 The Kotlin half, test first on the simulator (`iosSimulatorArm64Test`):
      `Crashes.report` on iOS hands the bridge the qualified type, the message, a non-empty list
      of `getStackTraceAddresses()`, the fatal flag, the surface and the deal's address
- [ ] 3.5 The Swift half: the closure registered at start-up, the event built per 3.3, and the
      uncaught path — report as fatal, flush with a ceiling, end the process without a signal so
      no second, nameless SIGABRT is filed
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
