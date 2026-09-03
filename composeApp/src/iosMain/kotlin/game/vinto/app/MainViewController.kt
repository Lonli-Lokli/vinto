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
    return ComposeUIViewController { App(marketing = captureScene()) }
}

/**
 * The state a store capture asked for, from a launch argument.
 *
 *     xcrun simctl launch <device> app.kupalinka.vinto -vinto.capture table
 *
 * `zdymak` drives exactly this; `MarketingScene` lists the states and says what each is.
 *
 * **No debug/release split here, unlike Android.** The counterpart there reads an intent extra —
 * an entry point any app on the phone can send — so it is gated by a `src/debug` source set. A
 * launch argument is different in kind: only whoever *starts the process* can set one, which on a
 * device means Xcode, `simctl`, or nobody. There is no caller to defend against, so there is no
 * variant to maintain. `zdymak.config.mjs` reached the same conclusion when the handle was
 * specified.
 *
 * `NSUserDefaults` rather than `NSProcessInfo.arguments`: iOS parses `-key value` launch
 * arguments into the argument domain, so this reads the pair without splitting the array by hand
 * — and it is the same call the vault already makes, so no new API is reached for.
 */
private fun captureScene(): String? =
    platform.Foundation.NSUserDefaults.standardUserDefaults.stringForKey("vinto.capture")

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
