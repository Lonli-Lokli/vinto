package game.vinto.app

/**
 * Hands a piece of text to the platform, and lets the platform decide where it goes.
 *
 * Two callers with the same shape: a bug report, and an invitation to a room. Both are a
 * subject and a body that belong to the player rather than to this app, and both are worse
 * as a clipboard-only feature — the difference between "share" and "copy" is whether the
 * person has to go and find somewhere to paste.
 *
 * Every platform already has the machinery — Android's share sheet, iOS's activity view, the
 * browser's Web Share — and the right of it belongs to the player: mail it, message it, drop
 * it in a notes app, send it to themselves.
 *
 * @return false when there is nothing on this platform to hand it to, so the caller can fall
 *   back to the clipboard. That fallback is deliberately *not* a second `expect`: Compose
 *   already carries a clipboard on all four targets, and four hand-written ones would be four
 *   platform APIs to get right for a job the framework has already done.
 */
expect fun shareText(subject: String, body: String): Boolean
