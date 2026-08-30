package game.vinto.app

import game.vinto.app.crash.CrashReporter
import game.vinto.app.crash.CrashSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the reporter behaves, as opposed to what it writes (`CrashReportTest`).
 *
 * Three properties, each protecting against a different way a crash reporter becomes a
 * liability: one that reports when it was never configured, one that sends the same stack a
 * hundred times from a device stuck in a loop, and one that throws while reporting and so
 * replaces the crash with its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashReporterTest {

    private class Sent {
        val bodies = mutableListOf<String>()
        val auths = mutableListOf<String>()
        val urls = mutableListOf<String>()
    }

    private fun reporterFor(
        dsn: String?,
        scope: CoroutineScope,
        sent: Sent = Sent(),
        post: suspend (String, String, String) -> Unit = { url, auth, body ->
            sent.urls += url
            sent.auths += auth
            sent.bodies += body
        },
    ) = CrashReporter(
        dsn = dsn,
        platform = "java",
        release = "vinto@0.1.0",
        environment = "production",
        scope = scope,
        now = { 1_756_512_000_000 },
        nowIso = { "2026-08-30T00:00:00Z" },
        surface = { CrashSurface.ONLINE },
        post = post,
    )

    @Test
    fun aBuildWithNoDsnReportsNothingAndSaysSo() = runTest {
        val sent = Sent()
        val reporter = reporterFor(dsn = null, scope = CoroutineScope(Dispatchers.Unconfined), sent = sent)

        assertFalse(reporter.enabled, "a build with no DSN claimed to be reporting")
        reporter.report(IllegalStateException("the stage never drained"))
        assertTrue(sent.bodies.isEmpty(), "something was sent with nowhere to send it: ${sent.bodies}")
    }

    @Test
    fun aConfiguredBuildSendsOneEnvelopeToTheProjectsEndpoint() = runTest {
        val sent = Sent()
        val reporter = reporterFor(
            dsn = "https://abc123@o1.ingest.us.sentry.io/456",
            scope = CoroutineScope(Dispatchers.Unconfined),
            sent = sent,
        )

        assertTrue(reporter.enabled)
        reporter.report(IllegalStateException("the stage never drained"))

        assertEquals(1, sent.bodies.size, "expected exactly one report")
        assertEquals("https://o1.ingest.us.sentry.io/api/456/envelope/", sent.urls.single())
        assertTrue(sent.auths.single().contains("sentry_key=abc123"), sent.auths.single())
        assertTrue(sent.bodies.single().contains("the stage never drained"), sent.bodies.single())
        assertTrue(sent.bodies.single().contains("\"surface\":\"ONLINE\""), sent.bodies.single())
    }

    @Test
    fun aDeviceStuckInALoopStillSendsOneReport() = runTest {
        val sent = Sent()
        val reporter = reporterFor(
            dsn = "https://abc123@o1.ingest.us.sentry.io/456",
            scope = CoroutineScope(Dispatchers.Unconfined),
            sent = sent,
        )

        repeat(100) { reporter.report(IllegalStateException("again")) }

        assertEquals(
            1,
            sent.bodies.size,
            "${sent.bodies.size} reports from one process — a loop would spend the project's quota",
        )
    }

    @Test
    fun aReporterThatCannotReachSentryDoesNotThrow() = runTest {
        val reporter = reporterFor(
            dsn = "https://abc123@o1.ingest.us.sentry.io/456",
            scope = CoroutineScope(Dispatchers.Unconfined),
            post = { _, _, _ -> error("the network is down") },
        )

        // The crash being reported has already happened. If this throws, the reporter has
        // turned one failure into two — and it throws on the *crash* path, where nothing is
        // left to catch it.
        reporter.report(IllegalStateException("the stage never drained"))
    }
}
