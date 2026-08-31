package game.vinto.app

/**
 * A desktop window has no share sheet, so the caller falls back to Compose's clipboard —
 * which on this platform is the thing a person would have reached for anyway.
 */
actual fun shareText(subject: String, body: String): Boolean = false
