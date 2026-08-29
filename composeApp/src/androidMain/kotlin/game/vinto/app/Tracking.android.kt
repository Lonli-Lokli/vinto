package game.vinto.app

/**
 * Android has no system-wide Do-Not-Track for apps.
 *
 * The advertising ID's "limit ad tracking" flag is the nearest thing, and reading it needs
 * Play Services and a permission — a dependency and a manifest entry, in aid of a signal this
 * app would honour anyway because it collects nothing identifying in the first place. The
 * in-app opt-out is the control here.
 */
actual fun platformObjectsToTracking(): Boolean = false
