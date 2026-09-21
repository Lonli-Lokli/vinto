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

/** Kotlin/Native on Apple: `cocoa` is the platform a dSYM is read under. */
actual val crashPlatform: game.vinto.app.crash.SentryPlatform =
    game.vinto.app.crash.SentryPlatform.COCOA
