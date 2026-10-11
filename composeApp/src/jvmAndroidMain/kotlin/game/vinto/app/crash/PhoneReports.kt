package game.vinto.app.crash

import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.hints.Backfillable
import io.sentry.hints.Cached
import io.sentry.util.HintUtils

/**
 * What a phone hands the Sentry SDK, and what it takes back out before anything leaves.
 *
 * Written against the SDK's JVM core, in the source set Android shares with the JVM, so the JVM
 * suites can hold every decision here (`PhoneReportsTest`). Android's own two switches — the
 * screenshot and the view hierarchy, which exist only on `SentryAndroidOptions` — sit beside the one
 * call that starts the SDK (`CrashPipe.android.kt`). The web and the desktop do not come through
 * here: they keep the hand-built envelope in `Crash.kt` (design D1).
 */

/**
 * The SDK's options, from the build and nothing ambient, so one test reads every decision.
 *
 * [surface] and [place] are read when an event is made, not when this runs, as `Where` always was: a
 * report wants the screen that was showing, not the one showing when reporting started.
 */
internal fun vintoSentryOptions(
    options: SentryOptions,
    build: SdkBuild,
    surface: () -> CrashSurface,
    place: () -> CrashPlace,
) {
    options.dsn = build.dsn
    // The names the hand-built reporter sent, so the issue history and gulnya's read-back gate
    // (`sentry-readable.mjs`) carry on across the change rather than starting again.
    options.release = "vinto@${build.version}"
    options.dist = build.buildNumber
    options.environment = build.environment
    options.setTag(COMMIT_TAG, build.commit)

    // Nothing personal of the SDK's own accord: no `{{auto}}` IP address, no machine name.
    options.isSendDefaultPii = false
    options.isAttachServerName = false

    // No tracing, no profiling, no replay. None of them is something this app does, and a replay
    // records the screen. The defaults say the same today; said here so a new default cannot.
    options.tracesSampleRate = null
    options.profilesSampleRate = null
    options.sessionReplay.sessionSampleRate = null
    options.sessionReplay.onErrorSampleRate = null

    // No sessions. `sentry-android` gives each one the per-install id as `did`, and a session
    // never passes through `beforeSend` (design D5). What that costs is release health, which
    // Vinto never had.
    options.isEnableAutoSessionTracking = false

    // The SDK's handler in place of the app's own: it reports, then chains to the platform's, as
    // `installCrashHandler` did.
    options.isEnableUncaughtExceptionHandler = true

    // Our frames, as `CrashFrame.isOurs()` reads them, so an issue is named after Vinto's code and
    // not Compose's. The SDK's own guess is the application id, `app.kupalinka.vinto`, which is
    // not the package any of the code is in.
    options.addInAppInclude(OUR_PACKAGE)

    options.addEventProcessor(Address(surface, place))
    options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrubEvent(event) }
}

/** What a build tells the Sentry SDK about itself. */
internal data class SdkBuild(
    val dsn: String,
    /** The marketing version, `1.1`. Sentry's `release` is `vinto@` and this. */
    val version: String,
    /** The build number, which is Sentry's `dist`: two builds of `1.1` are two builds. */
    val buildNumber: String,
    /** The commit, short, so a report opens against its own code. */
    val commit: String,
    val environment: String,
)

/**
 * The surface and the deal, stamped onto an event this process made.
 *
 * Not onto one from an earlier run — an ANR or a native crash that the SDK finds on the next launch
 * and sends then — because the app is somewhere else by that time, and an address that is
 * confidently wrong is worse than none. Those arrive with no surface and no deal.
 */
private class Address(
    private val surface: () -> CrashSurface,
    private val place: () -> CrashPlace,
) : EventProcessor {

    override fun process(event: SentryEvent, hint: Hint): SentryEvent {
        if (fromAnEarlierRun(hint)) return event

        event.setTag(SURFACE_TAG, surface().name)
        // Omitted rather than sent empty, as the hand-built `extra` was: a block that is always
        // there teaches a reader to skim past it.
        val deal = place()
        if (!deal.isEmpty) {
            event.contexts.put(
                DEAL_CONTEXT,
                buildMap<String, Any> {
                    deal.gameId?.let { put("gameId", it) }
                    deal.round?.let { put("round", it) }
                    deal.turn?.let { put("turn", it) }
                },
            )
        }
        return event
    }

    private fun fromAnEarlierRun(hint: Hint): Boolean =
        HintUtils.hasType(hint, Backfillable::class.java) || HintUtils.hasType(hint, Cached::class.java)
}

/**
 * Takes out everything that names a person, a phone or a room, the SDK's own additions first.
 *
 * `sentry-android` 8.55.0 puts the per-install id it keeps in a file of its own on every event twice,
 * as `user.id` and as `device.id` (`DefaultAndroidEventProcessor.mergeUser`,
 * `DeviceInfoUtil.getDeviceId`), and adds the phone's boot time, which is the same instant on every
 * report from one phone until it restarts and so ties them together as surely as an id. The name
 * and the server name are not set by this version, and are cleared so that a later one cannot.
 *
 * Then the words: every message, exception and breadcrumb runs through [scrubReport], the rule the
 * hand-built envelope applies, so a room code that reached a message is redacted on a phone exactly
 * as it is on the web. What is left — the model, the OS, the stack — is what a crash is read against
 * and identifies nobody.
 */
internal fun scrubEvent(event: SentryEvent): SentryEvent {
    event.user = null
    event.serverName = null
    event.contexts.device?.let { device ->
        device.id = null
        device.name = null
        device.bootTime = null
    }

    event.message?.let { message ->
        message.message = message.message?.let(::scrubReport)
        message.formatted = message.formatted?.let(::scrubReport)
    }
    event.exceptions?.forEach { exception -> exception.value = exception.value?.let(::scrubReport) }
    event.breadcrumbs?.forEach { crumb ->
        crumb.message = crumb.message?.let(::scrubReport)
        crumb.data.entries.toList().forEach { (key, value) ->
            if (value is String) crumb.setData(key, scrubReport(value))
        }
    }
    return event
}

private const val COMMIT_TAG = "commit"
private const val SURFACE_TAG = "surface"

/** The deal's address, as one context — the same three fields the web sends as `extra`. */
private const val DEAL_CONTEXT = "deal"

/** The prefix of every class this app's code is in — the one `CrashFrame.isOurs()` reads. */
private const val OUR_PACKAGE = "game.vinto."
