package game.vinto.app

/** No share sheet here; the caller falls back to the clipboard and says so. */
actual fun shareReport(subject: String, body: String): Boolean = false

actual fun copyToClipboard(text: String): Boolean = false
