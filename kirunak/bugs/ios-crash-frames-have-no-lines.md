---
id: vinto/ios-crash-frames-have-no-lines
title: iOS crash reports never use the uploaded dSYMs
written: 2026-11-01
quad-damage: no
---

## Who raised it
> First fix . Then All leftovers must be created as kirunak bug so I can run them eg on iOS.

owner, 2026-11-01, session session_01NYecnhWG2GNpDqMrxQwRSJ

## Why
An iOS crash report reaches Sentry with each Kotlin/Native frame's `kfun:` name and address, and never a file or a
line, though every Release build uploads its dSYMs. Sentry finds a dSYM for a native address only through the image
the event names in `debug_meta` (a `macho` image with its `LC_UUID` and load address), and the iOS report is the app's
hand-built envelope, which names no image. So the dSYMs are uploaded and never used. To see it: any iOS event in
Sentry (frames with no line), or `crashEnvelope` in `Crash.kt`, which writes `debug_meta` for the web's wasm module
only.

## What was found
- iOS reports through the hand-built envelope: `crashPipe` is `CrashPipe.ByHand` [file composeApp/src/iosMain/kotlin/game/vinto/app/crash/CrashHandler.ios.kt]
- `crashEnvelope` writes `debug_meta.images` only from `WasmImage`, the web's module; a native frame carries `instruction_addr` and `package` and nothing names its image [file composeApp/src/commonMain/kotlin/game/vinto/app/crash/Crash.kt]
- the Release build phase uploads dSYMs with `sentry-cli debug-files upload --include-sources` [file iosApp/project.yml]
- Sentry maps a native address into the event's list of debug images to retrieve a debug file; a `macho` image needs `type`, `image_addr` and the `LC_UUID` as `debug_id` [research https://develop.sentry.dev/sdk/data-model/event-payloads/debugmeta/]
- the plan of record moves iOS onto `sentry-cocoa`, which sends its own images: tasks 3.1–3.6 and 4.1–4.4 are open [file openspec/changes/report-through-the-sentry-sdk/tasks.md]
- the frame writer's comment used to claim `instruction_addr` and `package` were all a dSYM lookup needs; corrected in the web symbolication work [commit 0db0d3f]
- RELIABILITY.md's iOS row now says the same [file docs/kotlin/RELIABILITY.md]

## Questions
1. Fix it the way `report-through-the-sentry-sdk` §3–4 already plans, moving iOS onto `sentry-cocoa`, rather than adding Mach-O images to the hand-built envelope?
   Proposed: yes. The SDK sends its own `debug_meta` images and also catches what the Kotlin hook cannot (a signal, a Swift trap); images added to the hand-built envelope would be deleted again by task 3.6.
2. Goal and criteria: an iOS Release crash shows the Kotlin file and line in Sentry?
   Proposed: yes, held by the change's own 5.1–5.2: a deliberate crash from a release build on an iPhone, read back readable with `sentry-readable.mjs`; plus 3.6, the envelope path deleted, and 4.1–4.4, symbols uploaded from the archive.
3. Edge cases?
   Proposed: a simulator sends nothing (`isEmulatedDevice`); the Capture build sends nothing (`CRASH_REPORTS`, no SDK attached); a crash before Kotlin starts; a crash in Swift; the surface tag and the deal's address still carried (`AReportSaysWhereItHappenedTest`'s iOS half).
4. Aspects?
   Proposed: iOS only; no screen, theme or locale changes; offline: a report queued by the SDK is sent on the next launch.
5. Plan?
   Proposed: tasks 3.1–3.6, then 4.1–4.4, then 5.1–5.3 of the change, in order; every Xcode build and simulator test through the machine's heavy lock.
