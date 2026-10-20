package game.vinto.app

import game.vinto.app.crash.Crashes
import game.vinto.app.crash.appReportingScope
import game.vinto.app.link.offerOpenedLink
import game.vinto.app.theme.mixWithOtherAudio

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

    // The payment queue's observer belongs at launch rather than on the screen that offers the
    // purchase: StoreKit delivers an interrupted transaction on the *next* launch, and nothing
    // listening at that moment means a charge that never reaches the app. `attach` is
    // idempotent for the same reason `install` is — Swift may ask for a second controller.
    IosBilling.attach()

    // Before the first composition, so before anything can play: the game's sounds mix with the
    // player's music instead of stopping it. Setting a category twice is harmless.
    mixWithOtherAudio()

    // Compose inside the parent that lets SystemBars turn the status bar's icons to the table's theme.
    return GameUIViewController { App() }
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

/**
 * Compile the Metal programs the first frame needs, off the main thread — see [FirstFrameWarmUp].
 * Swift calls this as `MainViewControllerKt.WarmUpFirstFrame()`, as the FIRST line of `iOSApp.init`:
 * the warm-up races Compose's first frame, so every millisecond of head start counts.
 */
@Suppress("FunctionNaming") // Swift-facing, named like MainViewController above.
fun WarmUpFirstFrame() = FirstFrameWarmUp.start()
