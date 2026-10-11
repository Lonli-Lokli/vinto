package game.vinto.app

import game.vinto.app.crash.CrashPlace
import game.vinto.app.crash.CrashSurface
import game.vinto.app.crash.SdkBuild
import game.vinto.app.crash.vintoSentryOptions
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.hints.Backfillable
import io.sentry.protocol.Device
import io.sentry.protocol.Message
import io.sentry.protocol.User
import io.sentry.util.HintUtils
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What Android hands the Sentry SDK, held on the JVM against the SDK's own types.
 *
 * The SDK is not neutral about what it collects. `sentry-android` 8.55.0 gives every event a
 * `user.id` and a `device.id`, both the per-install id it keeps in a file of its own
 * (`DefaultAndroidEventProcessor.mergeUser`, `DeviceInfoUtil.getDeviceId`, read from the bytecode),
 * and every session the same id as `did`. Vinto has no accounts and a crash report is the pipe that
 * quietly grows a user record if nobody says otherwise — ARCHITECTURE.md §2, "nothing identifying is
 * ever counted or reported". `CrashReportTest` held that for the hand-built envelope; this holds it
 * for the SDK.
 *
 * An event goes through what the SDK runs on it — every event processor the options carry, then
 * `beforeSend` — so what is asserted is what would leave, not what one function returns.
 */
class PhoneReportsTest {

    private var surface = CrashSurface.ONLINE
    private var place = CrashPlace(gameId = "g-42", round = 3, turn = 17)

    @Test
    fun theSdkIsToldTheNamesTheAppHasAlwaysSent() {
        val options = configured()
        assertEquals(DSN, options.dsn)
        // The names the hand-built reporter sent, so the issue history and gulnya's read-back gate
        // carry on across the change rather than starting again under new ones.
        assertEquals("vinto@1.1", options.release)
        assertEquals("676", options.dist)
        assertEquals("production", options.environment)
        assertEquals("70f1ae9a", options.tags["commit"], "the revision no longer rides along")
    }

    @Test
    fun theSdkCollectsNothingPersonalOfItsOwnAccord() {
        val options = configured()
        assertFalse(options.isSendDefaultPii, "the SDK may send what it calls default PII")
        assertFalse(options.isAttachServerName, "the SDK names the machine")
    }

    @Test
    fun theSdkTracesNothingAndRecordsNoScreen() {
        val options = configured()
        assertFalse(options.isTracingEnabled, "tracing is on")
        assertNull(options.tracesSampleRate)
        assertNull(options.profilesSampleRate)
        assertNull(options.sessionReplay.sessionSampleRate, "session replay records the screen")
        assertNull(options.sessionReplay.onErrorSampleRate, "session replay records the screen")
    }

    /**
     * A session carries the per-install id as `did`, and sessions do not pass through `beforeSend`,
     * so the only way to send no such id is to send no sessions. Release health is what that costs,
     * and Vinto never had it.
     */
    @Test
    fun theSdkKeepsNoSessionsBecauseEachOneNamesTheInstall() {
        assertFalse(configured().isEnableAutoSessionTracking, "every session would carry the install id")
    }

    /** The SDK's handler is what replaces `installCrashHandler` on Android. */
    @Test
    fun theSdkCatchesWhatEscapes() {
        assertTrue(configured().isEnableUncaughtExceptionHandler, "nothing catches an uncaught exception")
    }

    @Test
    fun anEventLeavesWithNothingThatNamesAPhoneAPersonOrARoom() {
        val sent = send(anEventAsAndroidBuildsIt())

        assertNull(sent.user, "the per-install user.id left the phone")
        val device = assertNotNull(sent.contexts.device)
        assertNull(device.id, "the per-install device.id left the phone")
        assertNull(device.name, "the phone's name left it")
        // The same instant on every report from one phone until it restarts, so it ties them
        // together as surely as an id does.
        assertNull(device.bootTime, "the boot time left the phone")
        assertNull(sent.serverName, "the machine's name left it")

        val words = listOfNotNull(
            sent.exceptions?.joinToString { it.value.orEmpty() },
            sent.message?.formatted,
            sent.message?.message,
            sent.breadcrumbs?.joinToString { it.message.orEmpty() + it.data.values.joinToString() },
        ).joinToString(" | ")
        assertFalse(words.contains(CODE), "a room code left the phone: $words")
        assertTrue(words.contains("lost the room"), "the scrubbing ate the message: $words")
    }

    /** And the scrubbing is surgical: what says where it happened, and on what, stays. */
    @Test
    fun anEventKeepsWhereItHappenedAndWhatItHappenedOn() {
        val sent = send(anEventAsAndroidBuildsIt())

        assertEquals("ONLINE", sent.getTag("surface"))
        assertEquals(mapOf("gameId" to "g-42", "round" to 3, "turn" to 17), sent.contexts[DEAL])
        assertEquals("Pixel 8", sent.contexts.device?.model, "the model is what a crash is read against")
    }

    /** Read at the moment of the report, as `Where` always was, not when reporting started. */
    @Test
    fun theAddressIsTheOneShowingWhenTheReportIsMade() {
        val options = configured()
        surface = CrashSurface.SOLO
        place = CrashPlace(gameId = "g-43", round = 1, turn = 2)

        val sent = send(SentryEvent(), options = options)

        assertEquals("SOLO", sent.getTag("surface"))
        assertEquals(mapOf("gameId" to "g-43", "round" to 1, "turn" to 2), sent.contexts[DEAL])
    }

    /** Omitted rather than sent empty, as the hand-built `extra` was. */
    @Test
    fun withNoGameThereIsNoDealAtAll() {
        place = CrashPlace()
        surface = CrashSurface.MENU

        val sent = send(SentryEvent())

        assertEquals("MENU", sent.getTag("surface"))
        assertNull(sent.contexts[DEAL], "an empty deal was sent")
    }

    /**
     * An ANR or a native crash is sent on the launch after it, and the app is somewhere else by
     * then. Filing it under the screen showing now would be an address that is confidently wrong.
     */
    @Test
    fun aReportFromAnEarlierRunIsNotFiledWhereTheAppIsNow() {
        val earlier = HintUtils.createWithTypeCheckHint(EarlierRun())

        val sent = send(anEventAsAndroidBuildsIt(), earlier)

        assertNull(sent.getTag("surface"), "an earlier run's crash was filed under today's screen")
        assertNull(sent.contexts[DEAL], "an earlier run's crash was filed under today's deal")
        assertNull(sent.user, "the scrubbing skipped an earlier run's crash")
    }

    private class EarlierRun : Backfillable {
        override fun shouldEnrich() = true
    }

    private fun configured(): SentryOptions = SentryOptions().also { options ->
        vintoSentryOptions(
            options = options,
            build = SdkBuild(
                dsn = DSN,
                version = "1.1",
                buildNumber = "676",
                commit = "70f1ae9a",
                environment = "production",
            ),
            surface = { surface },
            place = { place },
        )
    }

    /** Every event processor the options carry, in order, and then `beforeSend`: the SDK's path. */
    private fun send(
        event: SentryEvent,
        hint: Hint = Hint(),
        options: SentryOptions = configured(),
    ): SentryEvent {
        val processed = options.eventProcessors.fold<_, SentryEvent?>(event) { current, processor ->
            current?.let { processor.process(it, hint) }
        }
        val kept = assertNotNull(processed, "an event processor dropped the event")
        val beforeSend = assertNotNull(options.beforeSend, "nothing scrubs an event before it leaves")
        return assertNotNull(beforeSend.execute(kept, hint), "beforeSend dropped the event")
    }

    /** What `sentry-android` 8.55.0 has put on an event by the time `beforeSend` sees it. */
    private fun anEventAsAndroidBuildsIt() = SentryEvent(IllegalStateException("lost the room=$CODE")).apply {
        user = User().apply { id = INSTALL_ID }
        serverName = "localhost"
        contexts.setDevice(
            Device().apply {
                id = INSTALL_ID
                name = "Somebody's Pixel"
                model = "Pixel 8"
                bootTime = Date(BOOTED_AT)
            },
        )
        message = Message().apply {
            message = "join failed, code: $CODE"
            formatted = "join failed, code: $CODE"
        }
        addBreadcrumb(Breadcrumb("socket closed for ?room=$CODE"))
    }

    private companion object {
        const val DSN = "https://abc123@o1.ingest.us.sentry.io/456"
        const val CODE = "7KQ2MP"
        const val DEAL = "deal"

        /** The shape `Installation.id` writes: a random UUID, kept for the life of the install. */
        const val INSTALL_ID = "4f1d2b8e-9c3a-4e7f-8a61-2d5c0b9e7f13"
        const val BOOTED_AT = 1_791_700_000_000L
    }
}
