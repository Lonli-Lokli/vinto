---
id: vinto/firefox-safari-frames-unmeasured
title: Firefox and Safari crash frames are parsed from documentation, not measured
written: 2026-11-01
quad-damage: no
---

## Who raised it
> First fix . Then All leftovers must be created as kirunak bug so I can run them eg on iOS.

owner, 2026-11-01, session session_01NYecnhWG2GNpDqMrxQwRSJ

## Why
The web crash frames were settled by reading Chrome's console from the real production bundle; Firefox's and
Safari's were not. Firefox frames are now parsed from the shape `name@url:wasm-function[N]:0xOFFSET` and
`name@url:line:column`, taken from documentation and Sentry's own parser, not from a real Firefox; Safari's wasm
frames follow WebKit's own format, which WebKit's tracker records as non-standard, and nothing parses them. A crash in
either browser may therefore reach Sentry unparsed, untitled by our frame, and with no address to symbolicate. To see
it: throw from the production bundle in Firefox and in Safari (macOS and iOS) and compare the console's stack with
`CrashFramesTest`.

## What was found
- the frame shapes, the V8 ones measured and the Firefox ones added with the web symbolication work [file composeApp/src/commonMain/kotlin/game/vinto/app/crash/CrashFrame.kt]
- the Firefox patterns and their test came from documentation, not a browser [commit 0db0d3f]
- the measured lines the V8 patterns are held to [file composeApp/src/jvmTest/kotlin/game/vinto/app/CrashFramesTest.kt]
- JavaScriptCore's wasm stack format differs from the WebAssembly convention and can drop the function index when a name section exists [research https://bugs.webkit.org/show_bug.cgi?id=278991]
- the rule this repo set for frame shapes: measure them, do not reason about them [file docs/kotlin/RELIABILITY.md]

## Questions
1. Measure in Safari on macOS, Safari on iOS and Firefox: throw on purpose from the production bundle, copy the console's stack, and add each shape to `CrashFramesTest` as the V8 lines were?
   Proposed: yes; this needs the owner's devices, which is why it is a bug for the owner's machine.
2. If Safari's wasm frames carry no module offset, accept a name-only frame for Safari?
   Proposed: yes: parse the name so the issue is titled after our frame; a line needs an offset Safari does not give.
3. Criteria?
   Proposed: `CrashFramesTest` holds one real line per browser and shape, each parsed into a frame with its function, and its module and offset where the browser gives them; a Firefox wasm frame is marked `addr_mode` like a Chrome one.
4. Aspects?
   Proposed: web only; Safari on iOS included; no UI change.
