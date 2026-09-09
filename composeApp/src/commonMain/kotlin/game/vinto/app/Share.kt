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

/**
 * The same hand-off, with a picture beside the words.
 *
 * Two callers, and both of them are an invitation: the room code on the lobby's invite sheet, and
 * the game itself under About. In each the [picture] is a QR of the very link [body] carries, so
 * the message works twice — tapped by whoever receives it, scanned by whoever is standing next to
 * them. That is the shape every other game in the portfolio shares its cards in.
 *
 * Kept separate from [shareText] rather than folded into it with a nullable parameter, because
 * the third caller — a crash report — has no picture and never will, and a `null` there would read
 * as an omission rather than a decision.
 *
 * @return false when this platform has nothing to hand it to, so the caller can fall back to
 *   [shareText] and then to the clipboard. A picture is the better share and the text is the one
 *   that must always happen; failing all the way down to a dead button is the answer none of them
 *   may give.
 */
expect fun sharePicture(subject: String, body: String, picture: ByteArray): Boolean
