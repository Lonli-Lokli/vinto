package game.vinto.client

import game.vinto.protocol.AnalyticsEvent
import game.vinto.protocol.FunnelStep
import game.vinto.protocol.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The four promises the sink makes, each tested as the failure it prevents.
 *
 * These are cheap tests of an expensive property: analytics is the one subsystem allowed to
 * lose its own data and not allowed to cost the game anything, and every rule below is there
 * because the obvious implementation breaks it.
 */
class AnalyticsTest {

    private fun step(n: Int) = AnalyticsEvent.Funnel(
        step = FunnelStep.entries[n % FunnelStep.entries.size],
        surface = Surface.MENU,
    )

    private class Collecting : AnalyticsTransport {
        val payloads = mutableListOf<String>()
        override suspend fun send(payloadJson: String) {
            payloads += payloadJson
        }
    }

    @Test
    fun aFloodIsDroppedRatherThanQueued() = runTest {
        // No scope draining it, so everything recorded has to fit in the buffer or be refused.
        val idle = TestScope()
        val sink = Analytics(
            transport = Collecting(),
            consent = AnalyticsConsent(optedIn = true, platformObjects = false),
            scope = idle,
            capacity = 8,
        )

        repeat(1_000) { sink.record(step(it)) }

        assertEquals(8, sink.accepted, "the cap did not hold: an unbounded buffer grew")
        assertEquals(992, sink.dropped, "every event past the cap should be counted as dropped")
    }

    @Test
    fun nothingIsRecordedWhenTheUserHasOptedOut() = runTest {
        val transport = Collecting()
        val sink = Analytics(
            transport = transport,
            consent = AnalyticsConsent(optedIn = false, platformObjects = false),
            scope = TestScope(),
        )

        repeat(20) { sink.record(step(it)) }

        assertEquals(0, sink.accepted, "an opted-out session buffered an event")
        assertTrue(transport.payloads.isEmpty(), "an opted-out session sent something")
    }

    @Test
    fun theePlatformSignalOverridesTheSetting() = runTest {
        // Opted *in*, and the platform says do not track. The platform wins; there is no
        // reduced mode, because "do not track me" is not an invitation to send less.
        val sink = Analytics(
            transport = Collecting(),
            consent = AnalyticsConsent(optedIn = true, platformObjects = true),
            scope = TestScope(),
        )

        assertFalse(sink.record(step(0)), "Do-Not-Track did not suppress the event")
        assertEquals(0, sink.accepted)
    }

    @Test
    fun optingOutMidSessionDiscardsWhatWasBuffered() = runTest {
        val transport = Collecting()
        val sink = Analytics(
            transport = transport,
            consent = AnalyticsConsent(optedIn = true, platformObjects = false),
            scope = TestScope(),
            capacity = 32,
        )

        repeat(10) { sink.record(step(it)) }
        assertEquals(10, sink.accepted)

        sink.consentChanged(AnalyticsConsent(optedIn = false, platformObjects = false))

        assertFalse(sink.record(step(0)), "events were still accepted after opting out")
        assertTrue(transport.payloads.isEmpty(), "the buffer was flushed on opt-out instead of discarded")
    }

    @Test
    fun aFailingTransportDoesNotStopTheSink() = runTest {
        var attempts = 0
        val angry = AnalyticsTransport {
            attempts++
            error("the network is down")
        }
        // Unconfined, so the drain loop runs the moment an event is recorded rather than on
        // the next scheduler pass — the test is about the loop surviving, not about when it
        // is dispatched.
        val draining = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val sink = Analytics(
            transport = angry,
            consent = AnalyticsConsent(optedIn = true, platformObjects = false),
            scope = draining,
            batchSize = 1,
        )

        sink.record(step(0))
        sink.record(step(1))

        assertTrue(attempts >= 2, "the loop died on the first failure: only $attempts attempt(s)")
        assertEquals(2, sink.accepted, "a failing transport must not refuse later events")
        draining.cancel()
    }

    @Test
    fun eventsReachTheTransportInOnePayload() = runTest {
        val transport = Collecting()
        // Standard rather than unconfined here, and that is the point of the case: the loop
        // must not run between the five `record` calls, so it sees them as one batch. Under
        // an unconfined dispatcher it wakes on each one and sends five times, which is the
        // behaviour this test exists to rule out.
        val draining = CoroutineScope(StandardTestDispatcher(testScheduler))
        val sink = Analytics(
            transport = transport,
            consent = AnalyticsConsent(optedIn = true, platformObjects = false),
            scope = draining,
            batchSize = 20,
        )

        repeat(5) { sink.record(step(it)) }
        testScheduler.advanceUntilIdle()

        assertEquals(1, transport.payloads.size, "five events should batch, not send five times")
        assertTrue(transport.payloads.single().contains("funnel"), "the payload does not name the event")
        assertFalse(transport.payloads.single().contains("null"), "a null leaked into the payload")
        draining.cancel()
    }
}
