---
id: vinto/use-the-new-sentry-cli
title: Every Sentry upload still runs the old sentry-cli
written: 2026-11-01
quad-damage: no
---

## Who raised it
> Create kirunak bug to use latest sentry cli everywhere

> We must migrate all our portfolio

owner, 2026-11-01, session session_01NYecnhWG2GNpDqMrxQwRSJ

## Why
Every upload of symbols to Sentry still goes through the old Rust `sentry-cli`, and each place gets a different
copy of it. The two web workflows install `@sentry/cli@3` (whatever 3.x is newest that day). The iOS Release phase
runs whatever `sentry-cli` is on the Mac's PATH, with no pin. The Android Gradle plugin uses the CLI bundled inside
it. Sentry has replaced that CLI with a Node one, the npm package `sentry` (binary `sentry`, code in
`getsentry/toolkit`, to ship as "sentry-cli v4"). The old one gets "no new features, only urgent bug and security
fixes". To see it: `git grep -n 'sentry-cli\|@sentry/cli'` lists the call sites below, and none of them names a
version both a laptop and a runner would get.

The commands are renamed, not just reinstalled. The upload with the most risk is `sourcemaps upload`. In the new CLI
it is `sentry sourcemap upload <directory>`, which takes a directory rather than a file pair and has no `--org` or
`--project` flag at all. Org and project then come from `SENTRY_ORG`/`SENTRY_PROJECT`, then `.sentryclirc`, then a
stored default, and finally DSN detection and a guess from the directory name. The repository's rule is that org and
project are always explicit, because a global default once sent one game's symbols into another game's project. A
straight rename would hand that choice back to the guess.

This is Vinto's share of a migration of the whole portfolio, `gulnya/use-the-new-sentry-cli`. That bug was written in
the same session for the owner to file in gulnya, which the session could not reach. It decides once for every app:
one pin, one org/project rule, a shared iOS archive upload, and one bug per app. Questions 1, 2, 4, 5 and 6 below apply
those decisions here, and follow that bug wherever it decides otherwise.

## What was found
- web: both workflows run `npm install -g @sentry/cli@3`, then the upload script, with `SENTRY_AUTH_TOKEN` from secrets [file .github/workflows/deploy-web.yml]
- web, previews: the same install and script [file .github/workflows/preview-web.yml]
- web: the script runs `sentry-cli sourcemaps inject <bundle> <map>`, then `sourcemaps upload --org echo-xl --project vinto --release vinto@<WEB_VERSION> --url-prefix ~/ <bundle> <map>`, then `debug-files upload --org echo-xl --project vinto --include-sources --wait <wasm debug file>`. It refuses to run without `sentry-cli --version`, or without `SENTRY_AUTH_TOKEN` or `~/.sentryclirc` [file tools/upload-web-sourcemaps.mjs]
- web: the wasm debug file it uploads is a code-free companion, the workaround for symbolic's rec-group bug; TRAPS.md says not to "fix" it by uploading the full module [file tools/wasm-symbols.mjs]
- iOS: the Release phase runs `sentry-cli debug-files upload --org echo-xl --project vinto --include-sources "$DWARF_DSYM_FOLDER_PATH"`; its comment explains why org and project must stay explicit [file iosApp/project.yml]
- iOS: the generated copy of that phase [file iosApp/iosApp.xcodeproj/project.pbxproj]
- iOS: task 4.2 plans a `Scripts/archive-ios.mjs` that runs `sentry-cli debug-files upload` on the `.xcarchive` and is still to be written [file openspec/changes/report-through-the-sentry-sdk/tasks.md]
- Android: `sentry { org, projectName, autoUploadProguardMapping }`, the plugin's bundled CLI, and a guard that fails `uploadSentryProguardMappings*` without `SENTRY_AUTH_TOKEN` or `~/.sentryclirc` [file androidApp/build.gradle.kts]
- Android: plugin `io.sentry.android.gradle` pinned at `sentryGradle = "6.21.0"` [file gradle/libs.versions.toml]
- Android: CI's `bundleRelease` uploads the mapping with the real token [file .github/workflows/kmp.yml]
- the root `package.json` already pins Node tooling with a lockfile (lefthook, vydanne, zdymak), so one exact `sentry` pin can live there [file package.json]
- the new CLI's org/project order: explicit flags, then `SENTRY_ORG`/`SENTRY_PROJECT` (`SENTRY_PROJECT` also takes `<org>/<project>`), then `.sentryclirc` walked up from the cwd and merged with the global one, then SQLite defaults, then DSN detection, then the directory name [research https://github.com/getsentry/toolkit/blob/c577a45d92c7e2fcf38b5bf270fd26466a1863b1/packages/cli/src/lib/resolve-target.ts]
- `sentry sourcemap upload` takes one positional `<directory>`, has `--release`, `--dist`, `--url-prefix` (default `~/`), `--strip-prefix`, `--no-rewrite`, and resolves org/project with no flag of its own [research https://github.com/getsentry/toolkit/blob/c577a45d92c7e2fcf38b5bf270fd26466a1863b1/packages/cli/src/commands/sourcemap/upload.ts]
- the new CLI still reads `.sentryclirc`: `[auth] token` maps to `SENTRY_AUTH_TOKEN`, and `[defaults] org/project` feed the order above [research https://github.com/getsentry/toolkit/blob/c577a45d92c7e2fcf38b5bf270fd26466a1863b1/packages/cli/src/lib/sentryclirc.ts]
- upload injects the debug id by default, and "adds the snippet and adjusts the sourcemap mappings"; it exits non-zero on zero JS+map pairs unless `--allow-empty` [research https://github.com/getsentry/toolkit/blob/c577a45d92c7e2fcf38b5bf270fd26466a1863b1/apps/cli-docs/src/fragments/commands/sourcemap.md]
- `debug-files upload` keeps `--include-sources` and `--wait` (non-zero if any file fails processing). `check`, `print-sources` and `bundle-sources` run locally through "a bundled `symbolic` WASM module", so the rec-group bug and the companion workaround carry over [research https://github.com/getsentry/toolkit/blob/c577a45d92c7e2fcf38b5bf270fd26466a1863b1/apps/cli-docs/src/fragments/commands/debug-files.md]
- `upload-proguard` is now `sentry proguard upload`; `proguard uuid` matches the legacy UUID byte for byte [research https://github.com/getsentry/toolkit/blob/c577a45d92c7e2fcf38b5bf270fd26466a1863b1/apps/cli-docs/src/fragments/commands/proguard.md]
- npm `sentry` 0.47.0 is the published release (the repo's `package.json` is at 0.48.0-dev.0), binary `sentry`, `engines.node >= 20` [research https://www.npmjs.com/package/sentry]
- the tracking issue: v4 is the Node CLI in `getsentry/toolkit`; the old CLI gets only urgent fixes; the Android Gradle plugin and the Java SDK plan to move in early Q1 2027 [research https://github.com/getsentry/sentry-maven-plugin/issues/292]
- the new CLI's docs [research https://cli.sentry.dev/]
- not run here: installing the npm `sentry` package in this container was refused, so every command above comes from its docs and source at toolkit c577a45, not from a run [command npm install sentry@0.47.0]

## Questions
1. Where does the one pinned version live?
   Proposed: an exact `"sentry": "0.47.0"` in the root `package.json` devDependencies, with its lockfile entry. Every call site runs `npx --no-install sentry`, so a laptop, both workflows and the Xcode phase get the same build. Drop `npm install -g @sentry/cli@3` from both workflows in favour of an `npm ci` at the root.
2. How do org and project stay explicit, now that `sourcemap upload` has no flag for them?
   Proposed: every call gets `SENTRY_ORG=echo-xl` and `SENTRY_PROJECT=vinto` in its own environment. The web script sets them on each spawn, and the Xcode phase sets them on its one line. They beat `.sentryclirc` and are never left to DSN detection or the directory name. Keep the comment in `project.yml` and point it at the env vars instead of the flags.
3. Web: what does `tools/upload-web-sourcemaps.mjs` become?
   Proposed: `sentry sourcemap upload "$DIST" --release vinto@<WEB_VERSION> --url-prefix '~/'` on the dist directory in place, replacing the separate inject, then `sentry debug-files upload --include-sources --wait <debug file>`. The rename of `composeApp.<hash>.js` stays before it, and `strip-web-sourcemaps.mjs` stays after it. The script keeps its "exactly one composeApp script" check, and adds `sentry sourcemap resolve "$DIST"` to show that only that pair is found.
4. iOS: what replaces the Release phase's `sentry-cli`?
   Proposed: `npx --no-install sentry debug-files upload --include-sources "$DWARF_DSYM_FOLDER_PATH"` with the two env vars. Its "absent" check tests for `npx`/Node and the installed package instead of `which sentry-cli`, and `project.pbxproj` is regenerated with `xcodegen`. `Scripts/archive-ios.mjs` (report-through-the-sentry-sdk 4.2) is written against the new CLI from the start.
5. Android: switch now, or wait for the plugin?
   Proposed: wait. Bump `sentryGradle` to the first release that runs the new CLI (planned for early Q1 2027) and keep `autoUploadProguardMapping` and the credentials guard. A hand-rolled `sentry proguard upload` would have to reproduce the UUID pairing the plugin writes into the app, for a gap the plugin closes itself. Until then this bug stays open with Android as its only remainder.
6. Credentials: the guards accept `SENTRY_AUTH_TOKEN` or `~/.sentryclirc`. Should they also accept a `sentry auth login` stored in the new CLI's own config?
   Proposed: no, not in this change. The new CLI still reads both of the existing sources, so the rule "one mechanism for a laptop and a runner" holds as written. A third source would need all three guards (web script, Xcode phase, Gradle) changed together.
7. Criteria?
   Proposed: all four of these hold.
   - `git grep -n 'sentry-cli\|@sentry/cli'` finds only comments that explain the history, plus the Android plugin until question 5 is settled.
   - `npx --no-install sentry debug-files check composeApp/build/wasm-symbols/<file>` says the wasm companion is usable, and `print-sources` lists the Kotlin files (the old CLI's `bundle-sources` found 211).
   - A preview deploy's `--wait` upload succeeds, and a web crash from that preview reads to a Kotlin file and line in Sentry.
   - One Release archive's dSYM appears in Sentry with `debug` among its features.
8. Edge cases?
   Proposed: four, each with what it rules out.
   - Never upload a staged copy of the bundle. Injection shifts the map's mappings, so the uploaded map is correct only for the injected bundle that is actually served.
   - Xcode runs build phases with a short PATH, so the phase has to find Node itself, as it had to find `sentry-cli` before.
   - The CLI is pre-1.0, so an exact pin is moved deliberately and is never a range.
   - The new CLI bundles its own `symbolic`, so the wasm check runs again whenever the pin moves.
9. Aspects?
   Proposed: CI, release tooling and the iOS build phase. No app code changes, and nothing a player sees. Security: the token handling is unchanged, and no new secret is added. All other aspects n/a.
