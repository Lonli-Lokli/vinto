---
id: vinto/screen-scope-failure-stops-the-app
title: A failed job in a screen's own scope stops the whole app drawing
written: 2026-11-01
quad-damage: yes
---

## Who raised it
> First fix . Then All leftovers must be created as kirunak bug so I can run them eg on iOS.

owner, 2026-11-01, session session_01NYecnhWG2GNpDqMrxQwRSJ

Found by the agent: four screens launch work on a bare `rememberCoroutineScope()`, the shape that froze the whole
app when it was the app's own scope.

## Why
`rememberCoroutineScope()` gives its scope a plain `Job` under the Recomposer's effect job. A coroutine on it that
fails cancels that job, the effect job with it, and the Recomposer stops: the screen stays as it is and no tap does
anything again. The app's long-lived scope was moved off this shape (`rememberAppScope`, held by
`OneFailedJobLeavesTheAppRunningTest`), but Public rooms, Open a room, the share card and the support button still
launch on it, with no handler, so a failure there also goes unreported on the web except through the final resort.
To see it: the test's shape with `rememberCoroutineScope()` in place of `rememberAppScope()` fails; before 79559db,
Public rooms in a web build froze the first time `fetch` threw where it was called.

## What was found
- Public rooms loads the list on a bare `rememberCoroutineScope()` [file composeApp/src/commonMain/kotlin/game/vinto/app/DiscoverScreen.kt]
- Open a room creates the room on one [file composeApp/src/commonMain/kotlin/game/vinto/app/OnlineScreen.kt]
- the share card renders and shares on one [file composeApp/src/commonMain/kotlin/game/vinto/app/share/CodeToShare.kt]
- the support button runs `buySupport()` on one [file composeApp/src/commonMain/kotlin/game/vinto/app/SettingsScreen.kt]
- the app scope's fix and the test that holds it [file composeApp/src/jvmTest/kotlin/game/vinto/app/OneFailedJobLeavesTheAppRunningTest.kt]
- `RememberedCoroutineScope` builds `Job(parentContext[Job])`, a plain job under the composition's [research https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.0/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/Effects.kt]
- the Recomposer shuts down when its effect job completes [research https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.0/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/Recomposer.kt]
- the browser failure that froze Public rooms is now an answer, which closes one way in and not the shape [commit 79559db]

## Questions
1. Give each of the four a supervised scope that reports, the shape of `rememberAppScope` (a `SupervisorJob` under the composition's job, with `Crashes.handler()`), rather than a `try` in each launch?
   Proposed: yes. A `try` guards only the launch it is written around; the scope guards every launch on it, including the next one somebody adds.
2. Hold it in a test that fails on a bare `rememberCoroutineScope()` anywhere in `composeApp`, as `CrashInstallTest` reads source?
   Proposed: yes, with the supervised helper as the one allowed caller.
3. Criteria?
   Proposed: a Compose test per screen, or one over all four, where the launched job throws and the screen still recomposes, seen red first; the failure reaches `Crashes.report`.
4. Edge cases?
   Proposed: leaving the screen cancels its jobs and reports nothing; a store purchase failing in `buySupport()` on Android or iOS leaves the button usable; a share sheet the browser refuses is still the `false` it is today.
5. Aspects?
   Proposed: every platform, the code is common; no change to what any screen shows; themes, locales and offline n/a.
