package game.vinto.app

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Nothing here can compile an Apple source set, so this is written to be as boring as the
 * language allows and `kmp-ios` is what actually says it works.
 *
 * Two shapes deliberately copied rather than invented, because this family of mistake has cost
 * three CI round trips in this repository: `NSURL.URLWithString` is exactly what `Beacon.ios.kt`
 * already uses and compiles with, and `sharedApplication` is read off the companion rather than
 * imported by name — a class property on the metaclass is not something an `import` can name,
 * which is how `sharedSession` broke the build once.
 *
 * `openURL:` is the one-argument form, deprecated since iOS 10 in favour of
 * `openURL:options:completionHandler:`. Chosen on purpose: a deprecation warning is a warning,
 * and getting the options dictionary's type wrong on a host that cannot check it is an
 * unresolved reference. Worth revisiting on a Mac.
 */
actual fun openUrl(url: String): Boolean {
    val target = NSURL.URLWithString(url) ?: return false
    val app = UIApplication.sharedApplication
    if (!app.canOpenURL(target)) return false
    app.openURL(target)
    return true
}
