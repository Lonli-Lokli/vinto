---
id: vinto/wasm-frames-without-lines
title: Skia's and Kotlin's runtime wasm frames still have no line
written: 2026-11-01
quad-damage: no
---

## Who raised it
> First fix . Then All leftovers must be created as kirunak bug so I can run them eg on iOS.

owner, 2026-11-01, session session_01NYecnhWG2GNpDqMrxQwRSJ

## Why
Since the web symbolication work, a wasm frame in our code or in a library resolves to a Kotlin file and line. Two
kinds still do not. Skia's module, `skiko.wasm`, ships with no custom section at all, so its frames are
`wasm-function[N]` with neither a name nor a line. And Kotlin's own runtime built-ins (`kotlin.Throwable.<init>`, the
coroutine intrinsics) are named but have no line, because the compiler writes no mapping for them even with library
mappings switched on. To see it: `tools/check-wasm-names.mjs` on the dist's Skia module finds no names; Sentry's
`symbolic` resolves `kotlin.Throwable.<init>` to a name with no file.

## What was found
- `skiko.wasm` has no custom sections: no name section, no debug info [command node tools/check-wasm-names.mjs on a dist holding only skiko.wasm]
- library mappings are kept with `-Xwasm-source-map-include-mappings-from-unavailable-sources`, which leaves the runtime built-ins unmapped [file composeApp/build.gradle.kts]
- Sentry's `symbolic` 13.9 resolved real V8 offsets from a local production build: our frames and coroutines' to file and line, `kotlin.Throwable.<init>` and `startCoroutineUninterceptedOrReturn` to a name and line 0 [commit 0db0d3f]
- the debug file is built from the map `wasm-opt` keeps [file tools/wasm-symbols.mjs]

## Questions
1. Skia: accept name-less frames, or ask JetBrains for a `skiko.wasm` with a name section or DWARF?
   Proposed: accept for now. A crash inside Skia is rare and the Compose frames that called it resolve; ask upstream only if a Skia frame ever heads an issue.
2. Kotlin's runtime built-ins: accept name-only frames, or report the missing mappings to Kotlin?
   Proposed: accept; they are named, and they are where every exception begins rather than where it was thrown.
3. Close this bug if both are accepted?
   Proposed: yes, dropped with the reason, so the gap is written down rather than rediscovered.
