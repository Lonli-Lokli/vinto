---
id: vinto/sentry-7817278407-original-failure
title: The web failure behind Sentry 7817278407 is still unknown
written: 2026-11-01
quad-damage: no
---

## Who raised it
> First fix . Then All leftovers must be created as kirunak bug so I can run them eg on iOS.

owner, 2026-11-01, session session_01NYecnhWG2GNpDqMrxQwRSJ

## Why
Sentry 7817278407 (web, 1.5 (720), surface MENU) read "RuntimeException: Exception while trying to handle coroutine
exception": kotlinx.coroutines saying a `CoroutineExceptionHandler` threw while handling another failure. The report
kept neither half, so what failed first is still unknown. The reporter can no longer throw and a web report now
carries the whole chain, the cause and the suppressed failure, each with its Kotlin file and line once deployed; the
failure itself has not been found. To see it: the Sentry issue, and the next web events after the fixed build ships.

## What was found
- the reporter cannot throw any more, and the web listener relays the browser's whole trace as chained exceptions [commit a63037b]
- wasm frames read back to a Kotlin file and line through an uploaded debug file [commit 0db0d3f]
- a browser's exception no longer escapes `answering` and the room's catches, one known way a coroutine failed on the web [commit 79559db]
- on Kotlin/Wasm, `stackTraceToString()` throws for a browser error with no `stack`, which made the reporter throw: measured on V8 [file docs/kotlin/TRAPS.md]
- the only handlers on the web path that can throw something other than what they were given are the app's own; read from Compose 1.12's `Effects.kt` and kotlinx.coroutines 1.11 [research https://github.com/Kotlin/kotlinx.coroutines/blob/1.11.0/kotlinx-coroutines-core/common/src/CoroutineExceptionHandler.kt]

## Questions
1. After the next web deploy, read this issue's new events, or the issue its failure is now titled under, for the original failure and fix that?
   Proposed: yes; until a new event arrives there is nothing to fix, only the cause to wait for.
2. Use the same events to confirm the web symbols pipeline: wasm frames with a Kotlin file and line, the debug file listed under Debug Files with its sources?
   Proposed: yes; the first deploy with `tools/wasm-symbols.mjs` is the first time the upload runs against the real project.
3. Drop this bug if nothing recurs?
   Proposed: drop it four weeks after the deploy with no new event, since the reporter's own failure is fixed.
