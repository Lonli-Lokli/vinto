package game.vinto.app

import android.os.Build

actual fun platformName(): String = "Android ${Build.VERSION.SDK_INT}"

/**
 * A seed from the platform's own randomness.
 *
 * `nanoTime` would do and would be reproducible-looking in a way that hides collisions when
 * two games start in the same millisecond; a random long is one line and has neither problem.
 */
actual fun freshSeed(): Long = java.security.SecureRandom().nextLong()
