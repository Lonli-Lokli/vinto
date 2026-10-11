package game.vinto.app

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Two shapes deliberately copied rather than invented, because this family of mistake has cost
 * three CI round trips in this repository: `NSURL.URLWithString` is exactly what `Beacon.ios.kt`
 * already uses and compiles with, and `sharedApplication` is read off the companion rather than
 * imported by name — a class property on the metaclass is not something an `import` can name,
 * which is how `sharedSession` broke the build once.
 *
 * `openURL:options:completionHandler:`, never the one-argument `openURL:`. This file used to call
 * the one-argument form on purpose, deprecated since iOS 10, on the reasoning that a deprecation
 * is only a warning. It stopped being only a warning: from iOS 18 UIKit refuses that call, logs
 * "BUG IN CLIENT OF UIKIT" and returns NO, so every link in the game opened nothing on a current
 * iPhone (owner, 2026-10-11). The options map is typed exactly as the sibling repo `vyrai` writes
 * it (`ui/src/iosMain/.../IosPlatformSlots.kt`), where this form compiles.
 *
 * The completion handler is `null` because there is nothing to wait for: UIKit answers later, on
 * the main queue, and the caller wants its answer now. So `true` means UIKit was asked to open a
 * link it said it can open, which is all the one-argument form ever promised either.
 */
actual fun openUrl(url: String): Boolean {
    val target = NSURL.URLWithString(url) ?: return false
    val app = UIApplication.sharedApplication
    if (!app.canOpenURL(target)) return false
    app.openURL(target, options = emptyMap<Any?, Any?>(), completionHandler = null)
    return true
}
