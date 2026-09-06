package game.vinto.app.crash

import game.vinto.app.AndroidStorage
import java.util.Properties

/**
 * The id of the R8 mapping this build was minified with, or null when there is not one.
 *
 * The Sentry Gradle plugin uploads `mapping.txt` and records the id it uploaded it under. Sentry
 * will not apply a mapping to an event that does not name it, so without this the upload is wasted
 * and every release frame stays `a.b.c`. The sentry-android SDK reads this for itself; this app
 * reports crashes by hand (`design.md` §A9), so it reads it here.
 *
 * **It is in an asset, not in the manifest.** Older plugin versions — including the 5.8.0 the rest
 * of the portfolio pins — wrote `io.sentry.proguard-uuid` as manifest meta-data, and every guide
 * still says so. From 6.x it is `assets/sentry-debug-meta.properties` instead, written by
 * `injectSentryDebugMetaPropertiesIntoAssets…`. Reading the manifest finds nothing and fails
 * silently, which looks exactly like a build that was never minified.
 *
 * Null on a debug build — nothing is minified, the plugin ignores that build type, and the asset is
 * not written. Null too if anything at all goes wrong: an unsymbolicated crash report is worth far
 * more than one that fails to send because a file could not be read.
 */
internal actual fun proguardUuid(): String? = runCatching {
    val context = AndroidStorage.context ?: return null
    context.assets.open(DEBUG_META).use { stream ->
        Properties().apply { load(stream) }.getProperty(UUID_KEY)
    }
}.getOrNull()

/** Written by the plugin's `injectSentryDebugMetaPropertiesIntoAssets…` task. */
private const val DEBUG_META = "sentry-debug-meta.properties"

/**
 * Plural in the file, singular in the event.
 *
 * The plugin can record several ids — one build can produce more than one mapping — but this app
 * has a single minified variant, so the value is one id and is sent as one image.
 */
private const val UUID_KEY = "io.sentry.ProguardUuids"
