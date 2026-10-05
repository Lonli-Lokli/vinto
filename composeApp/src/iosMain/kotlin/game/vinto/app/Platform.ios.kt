package game.vinto.app

import platform.UIKit.UIDevice

actual fun platformName(): String =
    UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun freshSeed(): Long = kotlin.random.Random.Default.nextLong()

/**
 * The binary says so itself: a simulator run and an Xcode debug build are debug binaries, and the
 * archive that goes to TestFlight and the App Store is not.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun isReleaseBuild(): Boolean = !kotlin.native.Platform.isDebugBinary

/**
 * No heuristics needed: the simulator sets `SIMULATOR_DEVICE_NAME` in the process environment and
 * a real device never has it. `#if targetEnvironment(simulator)` would answer at compile time, but
 * only inside Swift, and the reporter that asks is here.
 */
actual val isEmulatedDevice: Boolean
    get() = platform.Foundation.NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null

/** Kotlin/Native on Apple: `cocoa` is the platform a dSYM is read under. */
actual val crashPlatform: game.vinto.app.crash.SentryPlatform =
    game.vinto.app.crash.SentryPlatform.COCOA

/**
 * `CFBundleShortVersionString`, which Info.plist takes from `MARKETING_VERSION` in `project.yml`
 * — the number App Store Connect reads off the same binary. `?` only for a bundle with no
 * Info.plist, which is not one that runs.
 */
actual fun appVersion(): String =
    platform.Foundation.NSBundle.mainBundle
        .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: UNKNOWN_VERSION

private const val UNKNOWN_VERSION = "?"
