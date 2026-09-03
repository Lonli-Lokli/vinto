# VERSIONING.md — how Vinto versions across platforms

The same rule the rest of the portfolio follows (`game-deduction`, `game-dots`, `asilak` each
carry this file). Two numbers, deliberately decoupled. Don't conflate them.

| | What | Who sets it | Synced across platforms? |
|---|---|---|---|
| **Marketing version** | User-facing release name — `1.0`, `1.1`, `2.0` (semver). | **Human, manually**, at a release. | **No.** iOS can be `1.1` while Android is `1.0`. |
| **Build number** | Machine-monotonic counter that must strictly increase per store. | **Automatic** = `git rev-list --count HEAD`. | Same commit → same number, but each store only needs its own monotonicity. |

> **Why decoupled:** stores gate *uploads* on the build number strictly increasing within a
> marketing version; they don't care that two platforms share a number. The marketing version
> tells the user-facing story and each platform ships on its own cadence. Forcing lockstep would
> mean burning a version on one platform just to match the other. Don't.

## Where each value lives

**Single source of truth for the build number:** `Scripts/build-number.sh` →
`git rev-list --count HEAD` (falls back to `1` outside git). Feeds both platforms.

**iOS** — set **once at the Xcode project level** (`iosApp/project.yml` → `settings.base`), so
every target inherits it and cannot drift; App Store validation rejects a version/build mismatch
between the app and any embedded content.

- `MARKETING_VERSION` — the human semver (`"1.0"`).
- `CURRENT_PROJECT_VERSION` — the build number; the `"1"` in `project.yml` is only a local
  fallback, overridden on the archive command.
- `iosApp/iosApp/Info.plist` reads both through `$(MARKETING_VERSION)` /
  `$(CURRENT_PROJECT_VERSION)` rather than carrying its own copy.
- `project.yml` is the source: run `xcodegen generate --spec iosApp/project.yml --project iosApp`
  after editing it, and commit the regenerated `.xcodeproj`.

**Android** — `androidApp/build.gradle.kts` → `defaultConfig`. Note `androidApp`, not
`composeApp`: the application module is the one that has a version at all (see its header for why
the two are split).

- `versionName = "1.0"` — manual semver, independent of iOS.
- `versionCode` — `git rev-list --count HEAD` at configuration time, overridable with
  `-PversionCode=<n>`, falling back to `1` in a non-git or exported tree. It goes through
  `providers.exec` because this build runs with the configuration cache on.

## Releasing

**Bump the marketing version** (only when shipping a user-facing release):

- iOS: edit `MARKETING_VERSION` in `iosApp/project.yml`, then re-run `xcodegen`.
- Android: edit `versionName` in `androidApp/build.gradle.kts`.

**Stamp the build number** — always from `Scripts/build-number.sh`, never by hand:

```sh
# iOS — App Store archive (needs a full clone, not a shallow checkout):
xcodebuild archive \
  -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/Vinto.xcarchive \
  -allowProvisioningUpdates \
  CURRENT_PROJECT_VERSION="$(Scripts/build-number.sh)"

# Android — the bundle Play takes:
./gradlew :androidApp:bundleRelease -PversionCode="$(Scripts/build-number.sh)"
```

**Tag per platform**, so "what shipped where" is explicit without forcing the two into step:

```sh
git tag ios-1.0
git tag android-1.0
```

## Invariants (don't break)

- **The build number strictly increases** per marketing version, per store.
  `git rev-list --count` guarantees it — as long as the checkout is a full clone. A shallow CI
  checkout counts only the commits it fetched, which is not monotonic; use `fetch-depth: 0` or
  pass `-PversionCode` explicitly.
- **No per-target version overrides on iOS.** One project-level source, or things drift and Apple
  rejects the upload.
- **The marketing version is a human decision**, never auto-derived. The build number is never
  hand-edited.

> Vinto's first store release is `1.0` on both platforms. `androidApp` previously declared
> `versionCode = 1` and `versionName = "0.1.0"`, neither of which was ever uploaded anywhere, so
> there is no monotonicity to preserve across the change — the first Play upload is the commit
> count, which is far above 1.
