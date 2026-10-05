package game.vinto.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import game.vinto.app.crash.AndroidBuild
import game.vinto.app.crash.isEmulatorBuild

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

/**
 * Read once: these fields are baked into the system image and cannot change while the process
 * lives, and asking on every crash would put string matching in a process that is failing.
 *
 * Public `Build` fields only. `ro.kernel.qemu` is the most definitive signal and is reachable only
 * through `SystemProperties`, a hidden API greylisted since Android 9; a guard that a future
 * release throttles fails by quietly letting emulator crashes back in.
 */
actual val isEmulatedDevice: Boolean = isEmulatorBuild(
    AndroidBuild(
        fingerprint = Build.FINGERPRINT,
        model = Build.MODEL,
        product = Build.PRODUCT,
        device = Build.DEVICE,
        hardware = Build.HARDWARE,
        brand = Build.BRAND,
        manufacturer = Build.MANUFACTURER,
    ),
)

/** Android is a JVM to Sentry, and `java` is the word that gets the R8 mapping applied. */
actual val crashPlatform: game.vinto.app.crash.SentryPlatform =
    game.vinto.app.crash.SentryPlatform.JAVA

/**
 * `versionName` off the installed package — the number `androidApp` stamped and Play shows. Read
 * rather than copied, because `composeApp` is a library and cannot see the application's
 * `defaultConfig`. `?` before storage has handed over a context, which is before any screen.
 */
actual fun appVersion(): String {
    val context = AndroidStorage.context ?: return UNKNOWN_VERSION
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") // The flags overload above is API 33; below it this is the only one.
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName ?: UNKNOWN_VERSION
}

private const val UNKNOWN_VERSION = "?"
