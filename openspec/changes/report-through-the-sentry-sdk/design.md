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

Settled on 2026-10-11 against `sentry-cocoa`'s source rather than its documentation: 9.29.2
(commit 8689e78, the newest 9.x checked out on this Mac) and 9.27.0 (aaf96c4, the version the
portfolio's `Package.resolved` pins; `from: "9.0.0"` resolves to whatever is newest). Every file
named below is identical in both except `SentryClient.m`, where the same lines sit about 190
lines earlier. Cross-checked against Sentry's own Kotlin Multiplatform SDK
(`getsentry/sentry-kotlin-multiplatform`, `main`, `nsexception/SentryUnhandledExceptions.kt`),
Rick Clephas's NSExceptionKt (which it copies, with attribution), the Kotlin/Native runtime
(`kotlin-native/runtime/src/main/cpp/Exceptions.cpp`, `StackTrace.cpp`), and the server's half
(`sentry/lang/native/processing.py`, symbolicator's `symbolication/native.rs`, symbolic's
`heuristics.rs`). Line numbers are 9.29.2's.

**The bridge is a closure Swift hands to Kotlin at start-up**, and Kotlin calls it with a plain
value: for the throwable and each of its causes, the qualified type, the message and the frames
(`getStackTraceAddresses()`, `kotlin.native`, `@ExperimentalNativeApi`, each with an "ours"
flag), plus whether it is fatal, the surface and the deal's address. Nothing in it is a Sentry
type: Kotlin/Native cannot see `sentry-cocoa`, and the value is what the simulator test of 3.4
asserts on.

**What the SDK does with an `NSException`, and why that settles the shape.**
`SentrySDK.capture(exception:)` builds the event from `exception.name` and `exception.reason`
only (`SentryClient.m` `buildExceptionEvent:`, 241–250) and then attaches the *current* thread's
stack: `captureException:withScope:hint:` (485–495) goes through
`captureEventIncrementingSessionErrorCount:` with `alwaysAttachStacktrace:YES` (514–523), and
`prepareEvent` fills `event.threads` from the thread inspector whenever none are set
(1032–1043). An `NSException` subclass overriding `callStackReturnAddresses` changes nothing
there. So a *handled* report is a hand-built `Event`, not an `NSException`. The one place the SDK
does read `callStackReturnAddresses` is its crash monitor: `SentryCrashMonitor_NSException.m`
`handleException` (66–100) calls `sentrycrashcm_nsexception_initStackCursor`
(`…_StackCursor.m` 39–62), which copies them into a backtrace cursor, and the whole crash report
is written from it — which is what the fatal path uses, below.

**Where the debug images come from.** `prepareEvent` attaches `debugMeta` only when the event
has none, and computes it from the *threads'* frames, never from the exceptions':
`getDebugImagesFromCacheForThreads:` (`SentryClient.m` 1046–1052) collects each frame's
`imageAddress` string, parses it and asks `SentryBinaryImageCache.imageByAddress`, which returns
the image *containing* that address (`SentryDebugImageProvider.swift` 26–57;
`SentryBinaryImageCache.swift` 194–201). Each image becomes a `DebugMeta` with `debugID` (the
`LC_UUID`), `type = "macho"`, `imageAddress`, `imageSize`, `imageVmAddress` and `codeFile`
(`SentryDebugImageProvider.swift` 68–84). So the event carries the dSYM's id if, and only if,
its frames sit on a `SentryThread` with `imageAddress` filled.

**The handled report (`Crashes.report`, fatal = false): one hand-built event through
`SentrySDK.capture(event:block:)`.**

- `Event(level: .error)`; `platform` stays the SDK's `cocoa`.
- `exceptions`: one `Exception(value: message, type: qualifiedType)` per throwable in the chain,
  oldest cause first and the throwable itself last (the order Sentry's exception interface
  wants); each with `mechanism = Mechanism(type: "generic")`, `handled = true`, its own
  `stacktrace`, and `threadId` naming the one thread below.
- Frames, oldest first (Kotlin's list is newest first): `instructionAddress =
  String(format: "0x%016llx", addr)`, the SDK's own format (`SentryFormatter.h` 10,
  `SentryDebugImageProvider.swift` 87; Sentry itself only asks for a `0x` hex string);
  `imageAddress` and `package` from `dladdr(3)`'s `dli_fbase` and `dli_fname`, no SDK call;
  `inApp` from Kotlin's flag. `function`, `symbolAddress` and `module` stay empty: symbolication
  fills them, and a name supplied by the client would be shown in place of the dSYM's.
- `threads`: one `SentryThread(threadId:)` with `current = true`, `crashed = false` and the
  throwable's stacktrace. Not the real current thread — `Crashes.report` is often called from a
  coroutine handler on another thread, and the SDK's "current threads" would be the Swift call
  site — but the one thread is what lets `getDebugImagesFromCacheForThreads:` see the frames and
  what stops `prepareEvent` attaching the SDK's own stacks (`threadsNotAttached`, 1032).
- `debugMeta`: left nil, deliberately; the SDK fills it from that thread. D7's read-back checks
  that `debug_meta.images` names the app's debug id. If a later SDK stopped filling it, the
  fallbacks are `SentryDebugImageProvider.getDebugImagesFromCacheForFrames` (SPI, below) or
  reading `LC_UUID` ourselves, and the gate would say so before a player did.
- The surface tag and the deal context go through the `block:` scope, which
  `capture(event:block:)` applies to this event alone (`SentrySDK.swift` 174–176).

Static framework or not makes no difference to the shape: `dladdr` and the image cache find
whichever Mach-O an address is in. With the static framework Vinto ships, every Kotlin frame —
ours, the stdlib's, Compose's — is in `Vinto.app/Vinto` and covered by `Vinto.app.dSYM`.

**The fatal report (`setUnhandledExceptionHook`): hand the SDK's own crash handler an
`NSException` whose stack is Kotlin's, then let the runtime end the process.**

This replaces the earlier plan here (capture, flush with a ceiling, `exit` without a signal), and
it is what Sentry's Kotlin Multiplatform SDK does on `main` today
(`SentryUnhandledExceptions.kt` 57–75: `crashReporter.uncaughtExceptionHandler()` invoked with
`throwable.asNSException(appendCausedBy = true)`, falling back to a stored envelope only when the
handler is nil).

1. Swift writes the surface tag and the deal context into the live scope first
   (`SentrySDK.configureScope`). The scope observer copies them synchronously into the crash
   handler's memory (`SentryCrashScopeObserver.m` 45–66), so the report written in step 3
   carries them.
2. Swift builds a `KotlinException: NSException` — `name` the qualified type, `reason` the
   message with a `Caused by: type: message` line per cause, `userInfo` nil — overriding
   `callStackReturnAddresses` with the frames: the throwable's, then each cause's with the tail
   it shares with the one above dropped, as `asNSException(appendCausedBy = true)` does in the
   KMP SDK's `NSException.kt` (28–75) and NSExceptionKt's `Throwable.kt`.
3. Swift invokes `NSGetUncaughtExceptionHandler()` on it. After `SentrySDK.start` with
   `enableCrashHandler` (the default, `Options.swift` 85) that is `handleUncaughtException`:
   `setEnabled` installs the same function with `NSSetUncaughtExceptionHandler` and on
   `crashReporter.uncaughtExceptionHandler` (`SentryCrashMonitor_NSException.m` 129–143). It
   suspends every other thread (`sentrycrashcm_notifyFatalException`, `SentryCrashMonitor.c`
   203–270), builds the stack from `callStackReturnAddresses`, writes the whole report to disk
   synchronously and async-safely — every thread, every binary image, the synced scope
   (`SentryCrashC.c` `onCrash`, 93–106) — then **disables every monitor**
   (`sentrycrashcm_handleException`, `SentryCrashMonitor.c` 292–295: "Exception is fatal.
   Restoring original handlers") and calls whatever handler was installed before it. A direct
   call rather than `raise()`: an Objective-C exception thrown through Swift and Kotlin frames is
   outside what Swift supports, it would reach the same handler only by way of Kotlin's own C++
   terminate handler (`Exceptions.cpp` 126–151), and the function pointer is public Foundation
   API — what the KMP SDK reaches through SPI, Vinto reaches without it.
4. Swift returns, Kotlin's hook returns, and the runtime does what it always did: prints the
   trace and `std::abort()`s (`Exceptions.cpp` `terminateWithUnhandledException`, 77–95). The
   SIGABRT finds no monitor listening, so there is **no second, nameless report**. Nothing calls
   `exit`.
5. On the next launch the crash integration sends the report (`SentryCrashIntegration.swift`
   163–184) as a fatal event: `exception.type` Kotlin's type and `value` the message
   (`SentryCrashReportConverter.m` `parseNSException`, 623–636), `mechanism.type =
   "nsexception"`, `handled = false` (713–718), the exception's stacktrace the crashed thread's,
   that is Kotlin's addresses, with `image_addr` and `package` per frame (322–335) and
   `debug_meta` for every image those frames touch (396–416; `exception.stacktrace =
   crashedThread.stacktrace`, 513). The session it belonged to ends **crashed** (`SentryHub.m`
   `captureFatalEventWithResult:`, 291–320); the `exit` plan would have ended it *abnormal*
   (`closeCachedSessionWithTimestamp:`, 217–247). `beforeSend` runs on it too (`SentryClient.m`
   1165–1176), so D5's scrubbing covers crashes.

The handler is nil in two cases, and the bridge then falls back to the handled shape at level
`fatal` with `handled = false`, `SentrySDK.flush(timeout: 2)` (`SentrySDK.swift` 822), and a
scope tag `vinto.kotlin_crashed` that `beforeSend` uses to drop any fatal event carrying it —
the KMP SDK's `dropKotlinCrashEvent` (`SentryUnhandledExceptions.kt` 46–49, 78). Under a
debugger SentryCrash masks out its `DebuggerUnsafe` monitors, the NSException one included
(`SentryCrashMonitorType.h` 67–79, `SentryCrashMonitor.c` 155–165; `SentryCrash.h` 162–164:
"Is nil if debugger is running"), so a developer's crash reaches Sentry through the fallback and
nothing records the abort. On a simulator the SDK is not started at all (D3), the fallback finds
no client, and Kotlin terminates as today. The flush works the way the fallback needs: the
transport stores the envelope on its serial queue before it sends (`SentryHttpTransport.m`
109–134), and `flush:` queues its send behind that store and waits for the group up to the
timeout (179–224), so a flush that returns `success` has at least stored the event and a
timed-out one has most likely done so.

The report is delivered on the next launch, as every native crash is, rather than before the
process dies. Accepted: the hand-built reporter's "send before dying" existed because it had no
crash store; a player who never opens the app again is lost either way, and the SDK's store is
async-safe where a `runBlocking` on a dying thread is not.

**Addresses: raw return addresses, adjusted by nobody on the device.** Kotlin captures them with
`_Unwind_GetIP`/`backtrace` and stores them as they come (`StackTrace.cpp` 129–161); the `- 1`
it applies when printing its own stack strings (`adjustAddressForSourceInfo`, 244–258) is for its
symbolizer, not the stored values. `sentry-cocoa` 9.x likewise sends its own frames raw: both
stack cursors store `sentrycrashcpu_normaliseInstructionPointer(nextAddress)`, which only strips
pointer-authentication bits (`SentryCrashStackCursor_Backtrace.c` 45,
`…_MachineContext.c` 106); 8.x's `CALL_INSTRUCTION_FROM_RETURN_ADDRESS` survives only in its local
debug symbolicator. The server reverses the frames to callee-first (`processing.py` 401,
482–484) and, with no `instruction_addr_adjustment` on the stacktrace — `SentryStacktrace`
serialises none (`SentryStacktrace.m` 55–62) — the symbolicator treats the first frame as the
crashing one and adjusts every other by one instruction (`native.rs` 137–158; `heuristics.rs`
`should_adjust_caller` 290–312, `previous_address` 233–244). For Vinto's frames that means every
call site resolves exactly, and the top frame — the `throw` site, a return address from the
exception's constructor — resolves one arm64 instruction after the call, in practice the same
source line. Vinto subtracts nothing: doing so would double-adjust every frame below the top
once the server applies its rule, and the SDK's own crash frames and ours stay comparable.
Checked in 5.x by throwing from a known line.

**Frames Kotlin drops before handing over.** `Throwable` captures its stack in its constructor
(`Throwable.kt` 36–41), so the first addresses are `kfun:kotlin.Throwable#<init>`, the subclass
constructors, and only then the `throw` site — and Sentry names an issue after the top in-app
frame, which `CrashFrame.kt` already learned the hard way. Kotlin drops the leading constructor
frames by matching each `getStackTrace()` line to its address — the line prints the address
minus one (`StackTrace.cpp` 290, then 223), and one address can print several lines when frames
were inlined — never by index, as NSExceptionKt does; a line that cannot be matched drops
nothing. `inApp` is `CrashFrame.isOurs()`'s rule, the `game.vinto.` package, read from the same
lines. On the fatal path the SDK decides in-app by image instead (`SentryCrashReportConverter.m`
327–333, `SentryInAppLogic`), which with a static framework is every Kotlin frame — accepted, the
same as hronka's and vodar's crashes; a Sentry stack-trace rule (`stack.function:kfun:kotlin.*
-app`) is the owner's knob if it ever matters.

**No SPI.** `SentryDebugImageProvider` and `SentryDependencyContainer` are `@_spi(Private)`
(`SentryDebugImageProvider.swift` 4, `SentryDependencyContainer.swift` 34;
`develop-docs/SWIFT.md` 47 says the marker is there "to discourage use outside of the SDK", and
`KSCRASH_DOWNSTREAM_AUDIT.md` 150 lists whether those classes remain a contract in V10 as an open
question). Nothing above needs them: the model classes (`Event`, `Exception`, `Mechanism`,
`SentryStacktrace`, `Frame`, `SentryThread`, `DebugMeta`) are public, the handler pointer is
Foundation's, and the images are the SDK's own lookup.

**Rejected:** `SentrySDK.capture(exception:)` with an `NSException` subclass (ignores the
addresses, above); `exit` after a flush (the session reads abnormal, a flush can time out before
the store, `exit` runs atexit handlers on a live process, and it trades the SDK's async-safe
store for ours); `raise()` (above); `debugMeta` set by hand from
`PrivateSentrySDKOnly.getDebugImages()` (every image on every event, and SPI).

A genuine native crash — a signal, a Swift trap — never passes through Kotlin and is the SDK's
alone, which is the capability §A9 left flagged.

### D5. Nothing identifying leaves

The SDKs are not neutral about this. `sentry-android` 8.55.0's `DefaultAndroidEventProcessor`
sets `user.id` to a per-install id on every event that has no user (read from its bytecode on
2026-10-11), and `sentry-cocoa` does the same: `setUserIdIfNoUserSet:` (`SentryClient.m`
1301–1315, 9.29.2) writes the id it keeps in a file called `INSTALLATION` in its cache directory
(`SentryInstallation.swift`), after the scope is applied and before `beforeSend` — on crash
events from the previous run as well. A `beforeSend` on each phone removes the user, the
device's id and name, and the server name, and runs the existing `scrubReport` over every string a
person or the room could have put there — the room code in a stack trace is the case
`CrashReportTest` already pins. Automatic breadcrumbs that record URLs stay off.

What the iOS `beforeSend` has to clear, read from the same source in the D4 pass (2026-10-11):
the `user`; `contexts.app.device_app_hash`, a SHA-1 over `identifierForVendor`, the hardware
model and the bundle id (`SentryScopeContextEnricher.swift` 383, `SentryCrashMonitor_System.m`
392–420), a per-install id under another name; and the `culture` context (locale, timezone,
calendar; `SentryClient.m` 1333–1346). There is no device name on iOS and `server_name` is never
set, so the test asserts their absence rather than removing them. What `beforeSend` cannot reach
is the **session**: every session envelope carries `did`, the same installation id
(`SentryHub.m` 127–131, `SentrySession.swift` 259), and sessions do not pass through
`beforeSend`. So `enableAutoSessionTracking = false` on iOS — and the Android tasks check
`sentry-android` for the same `did` — which gives up release-health sessions Vinto never had; or
the owner decides a per-install id in session counts is acceptable, which reopens this paragraph.

Sentry also derives a location from the connecting address on its side, whatever the SDK sends
(the events of 2026-10-08 carry a city). With `sendDefaultPii = false` the cocoa SDK asks it not
to: the envelope's `sdk.settings.infer_ip` is `"never"` (`SentrySdkInfo.swift` 22,
`SentrySDKSettings.swift` 25–29). The project setting "Prevent storing of IP addresses" for the
`vinto` project stays as the second lock, a task for the owner.

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
- **The fatal path leans on the SDK's crash handler being installed** (D4). It is, whenever
  `SentrySDK.start` ran on a device without a debugger; when it is not, the bridge's fallback
  captures and flushes with a 2 s ceiling and the abort goes unrecorded. The report is sent on
  the next launch, as every native crash is — a death nobody relaunches after is lost, which the
  hand-built reporter's immediate POST also lost whenever the POST did not finish. Checked on a
  device in 5.1–5.3: one event, Kotlin's type and message, the frames with file and line, the
  session read as crashed, and the process gone after the hook.
- **APK size.** `sentry-android` with NDK adds to the download; the other games carry it. Measured
  in task 2.7 rather than assumed.

## Follow-ups (not in this change)

- **Lift the bridge into games-core.** No game reports anything from Kotlin today, so an uncaught
  Kotlin exception in hronka or vodar on iOS arrives as a bare SIGABRT without its type or
  message. The Kotlin half belongs in games-core's telemetry and the Swift half in the games'
  `iOSApp.swift`; Vinto carries the marked copy, as the portfolio contract already expects.
- **The other games send the SDK's per-install `user.id`**, because none of them has a
  `beforeSend`. A portfolio decision, not this game's.
