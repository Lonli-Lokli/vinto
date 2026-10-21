// Capture code (kupalinka-capture-a47d267ea8c1795fbb7df224): only in the capture build,
// See CLAUDE.md, "The capture build".
package game.vinto.app

import game.vinto.app.capture.CAPTURE_ID
import game.vinto.app.capture.Staged
import game.vinto.app.theme.mixWithOtherAudio
import platform.Foundation.NSUserDefaults

/**
 * The Capture configuration's view controller, which Swift opens under `#if CAPTURE`
 * (`ContentView.swift`): the app on the store scene the launch argument names, or with none the
 * app as a player opens it.
 *
 *     xcrun simctl launch <device> app.kupalinka.vinto.capture \
 *       -kupalinka-capture-a47d267ea8c1795fbb7df224 table
 *
 * zdymak drives exactly this (`Scripts/capture-ios.sh`). iOS parses `-key value` into the argument
 * domain, so `NSUserDefaults` reads the pair. Set up as [MainViewController] is, without crash
 * reports, which this build never sends (CLAUDE.md, "The capture build"), and without StoreKit: the
 * `.capture` bundle sells nothing and asks no store (E-5).
 */
@Suppress("FunctionNaming") // Swift calls this as `CaptureViewControllerKt.CaptureViewController()`.
fun CaptureViewController(): platform.UIKit.UIViewController {
    mixWithOtherAudio()
    val handle = NSUserDefaults.standardUserDefaults.stringForKey(CAPTURE_ID)
    return GameUIViewController { Staged(handle) { App(firstScreen = it) } }
}
