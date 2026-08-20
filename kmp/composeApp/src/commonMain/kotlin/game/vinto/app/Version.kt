package game.vinto.app

/**
 * What build this is, for the corner of the home screen and the foot of the settings.
 *
 * Written here as well as in `composeApp/build.gradle.kts`, because a Compose Multiplatform
 * common source set has no `BuildConfig` and generating one for a single string is a build
 * plugin's worth of machinery for a line of text. `VersionTest` fails if the two drift apart,
 * which is the whole reason the duplication is tolerable.
 */
const val VERSION = "0.1.0"
