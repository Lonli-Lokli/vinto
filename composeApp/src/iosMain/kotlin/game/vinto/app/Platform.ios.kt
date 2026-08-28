package game.vinto.app

import platform.UIKit.UIDevice

actual fun platformName(): String =
    UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun freshSeed(): Long = kotlin.random.Random.Default.nextLong()
