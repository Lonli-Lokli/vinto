package game.vinto.app

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * A desktop has no share sheet, so the caller falls back to the clipboard — which, here,
 * actually works.
 */
actual fun shareText(subject: String, body: String): Boolean = false

/**
 * The system clipboard, through AWT.
 *
 * Headless is the case worth handling rather than ignoring: the Compose test suites run with
 * `java.awt.headless=true`, where asking for a clipboard throws. A copy button that crashes a
 * test run is a worse bug than a copy button that reports it could not copy.
 */
actual fun copyToClipboard(text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    true
}.getOrDefault(false)
