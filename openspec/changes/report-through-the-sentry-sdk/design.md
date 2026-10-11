# Design: Report through the Sentry SDK on phones

## Context

`Crashes` (`composeApp/.../crash/`) is a process-level facade with four platform actuals and one
hand-built envelope writer. It does three jobs: it catches what escapes to the top, it reports
what the app chooses to report without dying (`TableTrouble` — a stalled stage, a refused move, a
lost room, from `Failures.kt`), and it says *where* the app was (`Where`: the surface, the
deal's game id, round and turn). Everything leaving goes through `scrubReport`, and
`CrashReportTest` holds that nothing identifying survives.

The facade is right and stays. What changes is what is behind it on a phone.

## Goals / Non-goals

Goals: a phone's report is readable in Sentry because the SDK names its own symbols; Kotlin's
own reports reach the SDK on both phones with their type and message; a native crash is caught;
nothing identifying leaves; the iOS symbols Sentry holds are the dSYMs, not the executable.

Non-goals: the web reporter (it stays, §A9's reason still holds there); performance tracing;
session replay; lifting any of this into games-core (see "Follow-ups").

## Decisions

### D1. The platform SDKs on the phones, the hand-built reporter on the web

Chosen by the product owner on 2026-10-11 over two alternatives:

- **The Sentry Kotlin Multiplatform SDK.** One API from common code, and it already converts a
  Kotlin throwable for `sentry-cocoa`. Rejected: it pins its own `sentry-cocoa`, which can clash
  with the 9.x every other game resolves, and no app in the portfolio uses it.
- **Keep the hand-built reporter, fixed.** Cheapest today. Rejected: it stays the one client that
  must get Sentry's event format right by itself, and that is the thing that failed — silently,
  for weeks, behind a green gate. It also cannot catch a signal or an ANR.

The web keeps the hand-built reporter because §A9's argument is categorical there: Kotlin/Wasm
has no native crash for an SDK to catch, and the bundle has no headroom. The desktop target keeps
it too — nobody ships it, and the JVM suites exercise the shared writer through it.

### D2. Android: an `Application` starts the SDK before anything draws

`sentry-android` 8.55.0, the version gulnya pins for every game — one Sentry project format
across the portfolio. Started in a new `VintoApplication.onCreate`, which runs before
`MainActivity` and before any provider the app owns; the SDK's own auto-init providers
(`SentryInitProvider`, `SentryPerformanceProvider`) are removed in the manifest so that the
emulator gate and these options decide, not the SDK's defaults. The Gradle plugin keeps
`autoInstallation` off (the dependency is declared, not injected) and keeps uploading the
mapping exactly as now.

The options, built by one pure function so a test can read them: `dsn`, `environment` (the
existing `sentryEnvironment()`), `release = "vinto@<version>"` and `dist = <build number>` — the
names the app already sends, so the issue history and the gulnya read-back gate's idea of a
build carry on — `sendDefaultPii = false`, no screenshot, no view hierarchy, no tracing, and the
`beforeSend` of D5.

`Crashes.report` on Android becomes `Sentry.captureException` with the surface as a tag and the
deal's address as a context, read at the moment of the report as `Where` is today. The SDK's
uncaught-exception integration replaces `installCrashHandler` there, and its own outbox replaces
the vault's one pending envelope.

### D3. iOS: `sentry-cocoa` through SwiftPM, started in `iOSApp.init`

The same package entry and the same place as hronka and vodar (`project.yml` `packages:`,
`from: "9.0.0"`; `SentrySDK.start` in `iOSApp.init`, after `WarmUpFirstFrame`). The simulator gate
is Kotlin's own `isEmulatedDevice`, exported, so the two phones ask one question. The options
mirror D2. App-hang tracking stays at the SDK default, as it does in every game.

### D4. Kotlin reaches `sentry-cocoa` through a Swift bridge  **[deep]**

Kotlin/Native cannot call `sentry-cocoa`, so the bridge is a closure Swift hands to Kotlin at
start-up, and Kotlin calls it with a plain value: the throwable's qualified type, its message,
`getStackTraceAddresses()`, whether it is fatal, and the surface and the deal's address.

Swift turns that into a Sentry event whose stack is **Kotlin's addresses**, not the Swift call
site's. This is the part that is easy to get subtly wrong and impossible to see in a test that
does not read the event back: `SentrySDK.capture(exception:)` takes the current thread's stack
unless told otherwise, and an event whose frames carry addresses but no debug images is exactly
the unreadable shape this change exists to end. The shape to build — frames with
`instructionAddress` on the event's thread or exception, and the debug images for them — is to be
settled against `sentry-cocoa` 9.x's source before any code is written, and proven by a release
build's event read back through the gulnya gate (tasks 3.3, 3.6).

**An uncaught Kotlin exception is reported once.** `setUnhandledExceptionHook` sends it through
the bridge as fatal and waits for the SDK to flush; the process then ends without a signal, so
`sentry-cocoa`'s crash handler does not file a second, nameless SIGABRT for the same death. A
genuine native crash — a signal, a Swift trap — never passes through Kotlin and is the SDK's
alone, which is the capability §A9 left flagged.

### D5. Nothing identifying leaves

The SDKs are not neutral about this. `sentry-android` 8.55.0's `DefaultAndroidEventProcessor`
sets `user.id` to a per-install id on every event that has no user (read from its bytecode on
2026-10-11), and `sentry-cocoa` does the same. A `beforeSend` on each phone removes the user, the
device's id and name, and the server name, and runs the existing `scrubReport` over every string a
person or the room could have put there — the room code in a stack trace is the case
`CrashReportTest` already pins. Automatic breadcrumbs that record URLs stay off.

Sentry also derives a location from the connecting address on its side, whatever the SDK sends
(the events of 2026-10-08 carry a city). That is a project setting, not code: "Prevent storing
of IP addresses" for the `vinto` project, a task for the owner.

### D6. iOS symbols come from the finished archive, and a check proves they arrived

The upload phase in `project.yml` runs inside the build and, with no declared input, before
Xcode has written the dSYM. Since 2026-09-25 every Apple debug file Sentry holds for Vinto is the
executable — 61 MB, `symtab` and `unwind`, no `debug` — and sentry-cli exited 0 each time.
asilak's `every-shipped-binary-has-its-dsym` shows the same ordering from its build logs.

So the upload moves to after the archive exists, from the `.xcarchive` itself, and a release
check reads every Mach-O slice the archive ships (`dwarfdump --uuid`), asks Sentry for each debug
id, and refuses the archive unless each one Sentry holds has debug information. An executable
under the same id does not count — it is what let every build since 09-25 through. asilak's
change proposes the same check; it is one script, and it belongs in gulnya where both can call it.

### D7. What proves it

The unit tests prove the options and the scrubbing. They cannot prove the thing that failed,
which is the event Sentry stores. So the change is finished when a release build on each phone
has sent a deliberate report and the gulnya nightly gate (`tools/sentry-readable.mjs`) reads it
back as readable — and when that same event, opened in Sentry, has no `user`.

## Risks

- **The bridge's event shape** (D4) is the costly part: wrong, it produces the same unreadable
  iOS event as today, with a green build. Mitigated by settling it against the SDK source first
  and by the read-back in D7.
- **Ending the process without a signal** after a fatal Kotlin report trades the SDK's own crash
  record for ours. If the flush times out, that death is not reported at all. The flush gets a
  ceiling, as `awaitCrashReport` has today, and the trade is checked on a device.
- **APK size.** `sentry-android` with NDK adds to the download; the other games carry it. Measured
  in task 2.7 rather than assumed.

## Follow-ups (not in this change)

- **Lift the bridge into games-core.** No game reports anything from Kotlin today, so an uncaught
  Kotlin exception in hronka or vodar on iOS arrives as a bare SIGABRT without its type or
  message. The Kotlin half belongs in games-core's telemetry and the Swift half in the games'
  `iOSApp.swift`; Vinto carries the marked copy, as the portfolio contract already expects.
- **The other games send the SDK's per-install `user.id`**, because none of them has a
  `beforeSend`. A portfolio decision, not this game's.
