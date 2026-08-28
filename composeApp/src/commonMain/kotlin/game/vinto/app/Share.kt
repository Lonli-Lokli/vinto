package game.vinto.app

/**
 * Hands a problem report to the platform, and lets the platform decide where it goes.
 *
 * A game that can only put a bug report on the clipboard has asked the player to find
 * somewhere to paste it, which is where most reports stop. Every platform already has the
 * machinery for this — Android's share sheet, iOS's activity view, the desktop clipboard —
 * and the right of it belongs to the player: mail it, message it, drop it in a notes app,
 * send it to themselves.
 *
 * @return false when there is nothing on this platform to hand it to, so the caller can fall
 *   back to the clipboard and say so.
 */
expect fun shareReport(subject: String, body: String): Boolean

/** The fallback, and the second button in the dialog. */
expect fun copyToClipboard(text: String): Boolean
