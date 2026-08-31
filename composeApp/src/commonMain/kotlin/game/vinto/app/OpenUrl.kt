package game.vinto.app

/**
 * Hands a web address to whatever the platform uses to open one.
 *
 * The sibling of [shareText] and the same shape for the same reason: the machinery exists on
 * every platform, and which browser opens it is the player's business rather than this app's.
 *
 * There are four callers and all of them are the same kind of thing — a page that belongs to
 * the game but is not *in* the game: the privacy policy, the terms, the way to get in touch,
 * and the other games. Each is a page somebody may want to keep open, mail to themselves, or
 * read properly; none of them is worth an in-app browser, which would mean shipping a second
 * rendering engine to display four documents.
 *
 * @return false when there is nothing on this platform that opens a link, so a caller can say
 *   so rather than appearing to have done nothing. That case is real on a locked-down desktop
 *   and it is the difference between a button that failed and a button that is broken.
 */
expect fun openUrl(url: String): Boolean
