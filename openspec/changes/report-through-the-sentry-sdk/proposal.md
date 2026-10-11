# Change: Report through the Sentry SDK on phones

## Why

A crash report from Vinto's phones cannot be read, and nothing said so.

Sentry issue 7781529123 (`STAGE_STALLED in ONLINE`, Android 1.1, build 656) arrived as
`vm0 in d`, `ia in a` — every frame minified, every frame "not in app". The R8 mapping for that
build had been uploaded (`e02fa662…`, 2026-09-29), and the build gate that refuses a release
whose mapping cannot reach Sentry had passed, correctly. What failed was the other half: Sentry
applies a mapping only to an event that names it in `debug_meta`, and the hand-built envelope in
`Crash.kt` wrote `debug_meta` **after the event's closing brace**. Sentry kept the event,
dropped the rest without a word, and no Android release stack has ever been read through its
mapping. `CrashFramesTest` asked whether the words were somewhere in the body, and they were.

iOS is worse in a different way. The envelope sends no debug images at all, so Sentry has never
used a dSYM for a Vinto event; the stacks read only because Kotlin/Native writes names into the
stack text. And since 2026-09-25 the dSYM upload has not been uploading dSYMs: every Apple debug
file Sentry holds for Vinto from then on is the 61 MB executable with a symbol table and no
debug information. The upload phase runs before Xcode has written the dSYM — the same fault
asilak's `every-shipped-binary-has-its-dsym` documents from its build logs — and sentry-cli
exits 0 having uploaded the wrong thing.

Every other app in the portfolio reports through `sentry-android` and `sentry-cocoa`, and read
back from Sentry on 2026-10-11 their events are symbolicated: hronka and vodar name their
mapping on Android and resolve their images on iOS. Vinto is the one client hand-writing
Sentry's event format, and it got that format wrong in the one field that matters most.

`add-live-analytics/design.md` §A9 chose the hand-built reporter for every client, and its
reason was the web: the SDK can catch nothing on Kotlin/Wasm that the hand-built reporter
cannot, and the bundle has no headroom. That reason still holds **for the web and only there.**
On a phone the SDK catches what no Kotlin `try` will ever see — a signal, an ANR, a Swift trap
(`ship-and-operate` 3.5) — and it names its own symbols.

## What Changes

- **Android reports through `sentry-android`**, pinned to the portfolio's version (8.55.0,
  gulnya's `libs.versions.toml`), started in a new `Application` before anything draws. The
  SDK names the R8 mapping itself; `ProguardUuid` and the hand-written `debug_meta` go.
- **iOS reports through `sentry-cocoa`**, added through SwiftPM as the other games do, started in
  `iOSApp.init` before Kotlin runs anything.
- **Kotlin's own reports reach the SDK on both phones.** Android calls `Sentry` from `androidMain`.
  iOS cannot call `sentry-cocoa` from Kotlin, so a **Swift bridge** carries a Kotlin throwable's
  type, message and stack *addresses* across. A report the app survives becomes an event built
  at Kotlin's addresses; an uncaught exception goes to Sentry's own uncaught-exception handler
  as an `NSException` carrying them, which files it once, with its type and message, rather
  than as a bare SIGABRT. Either way the dSYM symbolicates it (design D4).
- **Nothing identifying leaves, on the SDK as before it.** Both SDKs add a per-install
  `user.id` by default (verified in `sentry-android` 8.55.0's `DefaultAndroidEventProcessor`).
  A `beforeSend` on each phone strips it and everything else that names a person or a device,
  and a test holds it — the invariant `CrashReportTest` held for the hand-built reporter.
- **The web keeps the hand-built reporter**, with the `debug_meta` brace fixed (already done,
  `CrashFramesTest.theMappingIsNamedByTheEventItself`). The desktop target keeps it too.
- **The iOS symbols are uploaded from the finished archive**, and a release check refuses an
  archive unless every Mach-O it ships has its debug id in Sentry *with debug information* —
  an executable under the same id is exactly what let every build since 09-25 through.
- **The spec of record is corrected.** `mobile-app` has said "Sentry Kotlin Multiplatform SDK"
  since before the hand-built reporter replaced it; it now says what ships.

## Impact

- Affected specs: `mobile-app` (Performance and stability)
- Affected code: `composeApp` crash package (common facade kept; Android and iOS actuals
  replaced), `androidApp` (a new `Application`, the SDK dependency, the manifest), `iosApp`
  (`project.yml` package and upload phase, `iOSApp.swift`, a small Swift bridge), the release
  scripts that archive iOS
- Supersedes `add-live-analytics/design.md` §A9 for the two phones; §A9 stands for the web
- Closes `ship-and-operate` 3.5 (a native crash on iOS)
- Not in this change, recorded in `design.md`: lifting the bridge into games-core so every game
  gets Kotlin's type and message on an iOS crash, and the other games' own per-install
  `user.id`
