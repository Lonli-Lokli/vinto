package game.vinto.app

import platform.UIKit.UIPasteboard

/**
 * Not the activity view, yet.
 *
 * `UIActivityViewController` has to be presented from a view controller, and reaching the
 * right one from here means holding a reference the rest of this file does not need. The
 * pasteboard below covers the case that matters — a code somebody wants to send a friend —
 * and the caller falls back to it, so the button works. Worth finishing on a Mac, where the
 * result can be seen rather than assumed.
 */
actual fun shareText(subject: String, body: String): Boolean = false

/** The general pasteboard, which is the whole of the iOS clipboard. */
actual fun copyToClipboard(text: String): Boolean {
    UIPasteboard.generalPasteboard.string = text
    return true
}
