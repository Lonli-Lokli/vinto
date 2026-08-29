package game.vinto.app

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** Seconds, on this platform; the shared clock is in milliseconds. */
private const val MS_PER_SECOND = 1_000.0

/** See the expect in `Counting.kt`. */
actual fun elapsedMs(): Long = (NSDate().timeIntervalSince1970 * MS_PER_SECOND).toLong()
