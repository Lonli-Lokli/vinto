package game.vinto.app

/**
 * Not the activity view, yet.
 *
 * `UIActivityViewController` has to be *presented* from a view controller, and reaching the
 * right one from here means holding a reference nothing else in this module needs. Returning
 * false sends the caller to the clipboard, which is the useful half of a share sheet for a
 * six-character code. Worth finishing on a Mac, where the sheet can be seen rather than
 * assumed — this source set has never been run.
 */
actual fun shareText(subject: String, body: String): Boolean = false
