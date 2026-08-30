package game.vinto.app

import androidx.compose.ui.window.ComposeUIViewController
import game.vinto.app.crash.Crashes
import game.vinto.app.crash.appReportingScope
import game.vinto.app.link.offerOpenedLink

/**
 * Entry point the Swift side embeds. Exported through the `ComposeApp` framework, so the
 * name is part of the contract with `iosApp/iosApp/ContentView.swift`.
 */
// PascalCase deliberately: Swift calls this as `MainViewControllerKt.MainViewController()`,
// so the name is part of the contract with `iosApp/iosApp/ContentView.swift`.
@Suppress("FunctionNaming")
fun MainViewController(): platform.UIKit.UIViewController {
    // Before the controller is built, so a failure in the first composition is reported.
    // `install` is idempotent, and Swift may well ask for a second controller.
    Crashes.install(appReportingScope())
    return ComposeUIViewController { App() }
}

/**
 * An invitation, handed over from Swift.
 *
 * Kotlin cannot see a Universal Link on its own: the URL arrives in
 * `application(_:continue:restorationHandler:)` (and `application(_:open:options:)` for the
 * custom scheme), both of which are `AppDelegate` methods on the Swift side. So the Swift
 * half is three lines that call this, and everything about *what a link means* stays here
 * where it is tested.
 *
 * Exported by name for the same reason `MainViewController` is: renaming either side breaks
 * the other, and nothing checks that for you.
 */
@Suppress("FunctionNaming")
fun HandleOpenedLink(url: String?) = offerOpenedLink(url)
