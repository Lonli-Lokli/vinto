package game.vinto.app.crash

import game.vinto.app.isReleaseBuild

/** The bucket a crash a player had lands in. */
internal const val PRODUCTION: String = "production"

/** And the one every crash of ours does: a simulator, a desktop window, a CI job, a dev server. */
internal const val DEVELOPMENT: String = "development"

/**
 * Which of the two a crash from this build belongs in.
 *
 * A function of the build rather than a flag on a release command, because a flag is a thing to
 * forget and the day it is forgotten is the day it matters. [isReleaseBuild] is the per-target
 * half, and it says no wherever a target cannot tell.
 */
internal fun sentryEnvironment(release: Boolean = isReleaseBuild()): String =
    if (release) PRODUCTION else DEVELOPMENT
