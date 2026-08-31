package game.vinto.app

import java.awt.Desktop
import java.net.URI

actual fun openUrl(url: String): Boolean {
    // Headless machines and some Linux desktops have no browse action at all, which is what
    // the return value is for — `isSupported` is false there rather than throwing.
    val desktop = runCatching { Desktop.getDesktop() }.getOrNull() ?: return false
    if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
    return runCatching { desktop.browse(URI(url)) }.isSuccess
}
