---
id: vinto/preview-serves-source-maps
title: Web previews publish the JavaScript source map
written: 2026-11-01
quad-damage: yes
---

## Who raised it
> First fix . Then All leftovers must be created as kirunak bug so I can run them eg on iOS.

owner, 2026-11-01, session session_01NYecnhWG2GNpDqMrxQwRSJ

Found by the agent: the preview workflow publishes the dist without the step that takes the source maps out.

## Why
The rule is that a source map is the source, so it is uploaded to Sentry and then removed before anything is
published; `deploy-web.yml` runs `strip-web-sourcemaps.mjs` unconditionally for that reason. `preview-web.yml` builds
the same dist, content-addresses it and uploads a preview version with `composeApp.<hash>.js.map` and its
`sourceMappingURL` still in it, so every pull request's preview serves the client's Kotlin sources. To see it: the
workflow has no strip step; the dist holds the map until a strip removes it, as the last deploy's log shows.

## What was found
- the preview steps: build, content-address, symbols upload, Worker version upload; no strip [file .github/workflows/preview-web.yml]
- the deploy strips after the upload, unconditionally [file .github/workflows/deploy-web.yml]
- the strip script and its own check that nothing is left [file tools/strip-web-sourcemaps.mjs]
- the rule, in RELIABILITY.md's "A source map is the source, so it is uploaded and then removed" [file docs/kotlin/RELIABILITY.md]
- the last production deploy's log: "removed …/composeApp.38cf5e2d285bef88.js.map", so the map is in the dist the preview publishes as is [research https://github.com/Lonli-Lokli/vinto/actions/runs/39785028826]
- the preview uploads its symbols to Sentry since the web symbolication work, so stripping loses nothing [commit 0db0d3f]

## Questions
1. Add `node tools/strip-web-sourcemaps.mjs "$DIST"` to `preview-web.yml` after the symbol upload and before the version upload, unconditionally, as the deploy does?
   Proposed: yes.
2. Criteria?
   Proposed: the strip script's own check fails the job when a `.map` or a `sourceMappingURL` is left; a preview URL answers 404 for its `composeApp.<hash>.js.map`.
3. Edge cases?
   Proposed: a preview whose symbol upload is refused fails before anything is published, as now, so the strip needs no condition of its own.
4. Aspects?
   Proposed: CI only; no app change; all other aspects n/a.
