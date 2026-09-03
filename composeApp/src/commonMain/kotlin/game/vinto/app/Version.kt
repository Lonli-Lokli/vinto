package game.vinto.app

/**
 * What build this is, for the corner of the home screen and the foot of the settings.
 *
 * Written here as well as in `androidApp/build.gradle.kts`, because a Compose Multiplatform
 * common source set has no `BuildConfig` and generating one for a single string is a build
 * plugin's worth of machinery for a line of text. `VersionTest` fails if the two drift apart,
 * which is the whole reason the duplication is tolerable.
 *
 * **This is the MARKETING version and only that** — the human semver, bumped by hand at a
 * release. The build number is a separate, machine-monotonic thing that never appears here and
 * is never hand-edited; VERSIONING.md says which is which and why they are decoupled.
 */
const val VERSION = "1.0"
