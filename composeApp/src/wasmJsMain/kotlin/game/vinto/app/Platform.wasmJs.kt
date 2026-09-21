package game.vinto.app

import game.vinto.app.link.INVITE_HOST
import kotlinx.browser.window

actual fun platformName(): String = "Web (Kotlin/Wasm)"

actual fun freshSeed(): Long = kotlin.random.Random.Default.nextLong()

/**
 * The web build has no build type to read, so it reads where it is being served from: the one
 * host a player can reach it at is the site `deploy-web.yml` publishes. A development server, a
 * preview deployment and a file opened off disk are all somewhere else, and all ours.
 */
actual fun isReleaseBuild(): Boolean = window.location.hostname.equals(INVITE_HOST, ignoreCase = true)

/** The browser. Wasm frames arrive through the same JS error shapes Sentry reads. */
actual val crashPlatform: game.vinto.app.crash.SentryPlatform =
    game.vinto.app.crash.SentryPlatform.JAVASCRIPT
