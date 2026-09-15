package game.vinto.app

import android.content.pm.ApplicationInfo
import android.os.Build

actual fun platformName(): String = "Android ${Build.VERSION.SDK_INT}"

/**
 * A seed from the platform's own randomness.
 *
 * `nanoTime` would do and would be reproducible-looking in a way that hides collisions when
 * two games start in the same millisecond; a random long is one line and has neither problem.
 */
actual fun freshSeed(): Long = java.security.SecureRandom().nextLong()

/**
 * A package the system will let a debugger attach to is one of ours — `assembleDebug`, an
 * emulator, a sideloaded build — and what the stores take is not. Read off the package rather
 * than off `BuildConfig`, whose `DEBUG` in a *library* is the library’s own variant and not
 * the app’s, which is the trap this would otherwise walk into.
 *
 * The context arrives with storage, one line before the reporter is installed
 * (`CrashEnvironmentTest`). Without one the build cannot say, and a build that cannot say is
 * not a release.
 */
actual fun isReleaseBuild(): Boolean {
    val info = AndroidStorage.context?.applicationInfo ?: return false
    return info.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0
}
