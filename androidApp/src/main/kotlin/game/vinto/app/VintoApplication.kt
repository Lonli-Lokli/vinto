package game.vinto.app

import android.app.Application
import game.vinto.app.crash.Crashes
import game.vinto.app.crash.appReportingScope

/**
 * The process, before any screen: storage, then crash reporting.
 *
 * Crash reporting starts here rather than in `MainActivity` because this runs first — before the
 * activity, before its first frame, and before any provider the app owns — so a crash on the way to
 * the launcher is caught. On Android, installing the reporter starts `sentry-android`
 * (`CrashPipe.android.kt`), whose own auto-init providers would run earlier still; the manifest
 * removes them, so that this call and the emulator gate decide whether the SDK starts, not its
 * defaults.
 */
class VintoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Storage first, because the reporter reads the build through it: the version off the
        // installed package, and whether the package is a release (`CrashEnvironmentTest`). The
        // game's vault comes from it too, and nothing has asked for that yet.
        AndroidStorage.attach(this)
        // A scope that outlives every screen. The SDK needs none of its own; it is the shape the
        // other platforms' reporter takes, and the one entry point every client calls.
        Crashes.install(appReportingScope())
    }
}
