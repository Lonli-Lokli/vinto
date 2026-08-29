package game.vinto.app

import game.vinto.client.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 3.4: the failures a player experiences and nobody would otherwise hear about.
 *
 * The room reports everything the *server* can see, which is most of what goes wrong online
 * and none of what goes wrong on the device: a stage that stopped drawing, a socket that has
 * plainly lost the room, a move the engine refused after the UI offered it. None of those
 * reaches the room, and all three are things a person would describe as "it froze".
 *
 * Tested against the pure halves rather than through the composition, so the clock is virtual
 * and the assertions are about the rule instead of about a renderer's timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FailureCountingTest {

    @Test
    fun aStageThatStopsMovingIsCounted() = runTest {
        val activity = MutableStateFlow(StageActivity(draining = true, progress = 0))
        var stalls = 0
        val watching = launch { reportStalls(activity, stallMs = 1_000) { stalls++ } }

        advanceTimeBy(999)
        assertEquals(0, stalls, "counted before the window was up")

        advanceTimeBy(2)
        assertEquals(1, stalls, "a stage that played nothing for the whole window was not counted")

        watching.cancel()
    }

    @Test
    fun aSlowButMovingStageIsNotCounted() = runTest {
        val activity = MutableStateFlow(StageActivity(draining = true, progress = 0))
        var stalls = 0
        val watching = launch { reportStalls(activity, stallMs = 1_000) { stalls++ } }

        // Eleven bot moves, each taking most of the window. Slow, and entirely healthy: the
        // watchdog is about a stage that has stopped, not one that is being patient.
        repeat(11) { move ->
            advanceTimeBy(900)
            activity.value = StageActivity(draining = true, progress = move + 1)
        }
        advanceTimeBy(900)
        assertEquals(0, stalls, "a stage playing one move per 900ms was called stalled")

        watching.cancel()
    }

    @Test
    fun anIdleStageIsNeverCounted() = runTest {
        val activity = MutableStateFlow(StageActivity(draining = false, progress = 4))
        var stalls = 0
        val watching = launch { reportStalls(activity, stallMs = 1_000) { stalls++ } }

        advanceTimeBy(60_000)
        assertEquals(0, stalls, "a table with nothing to animate is not a stalled one")

        watching.cancel()
    }

    @Test
    fun aWedgedStageIsCountedOnceAndNotAgain() = runTest {
        val activity = MutableStateFlow(StageActivity(draining = true, progress = 0))
        var stalls = 0
        val watching = launch { reportStalls(activity, stallMs = 1_000) { stalls++ } }

        advanceTimeBy(1_001)
        // It stays wedged, and the app stays open on the table all evening.
        repeat(5) {
            activity.value = StageActivity(draining = true, progress = 0 - it)
            advanceTimeBy(2_000)
        }
        assertEquals(1, stalls, "one bad evening became $stalls points")

        watching.cancel()
    }

    @Test
    fun aRoomBeingLostIsToldApartFromABlink() {
        assertFalse(ConnectionState.Connecting.looksLost())
        assertFalse(ConnectionState.Connected.looksLost())
        assertFalse(ConnectionState.Closed("left the room").looksLost(), "leaving is not losing")

        // The first attempts are a tunnel; the fifth is a room that is not coming back.
        for (attempt in 1 until LOST_AFTER_ATTEMPTS) {
            assertFalse(
                ConnectionState.Reconnecting(attempt).looksLost(),
                "attempt $attempt was called lost — a dropped socket retries and usually wins",
            )
        }
        assertTrue(ConnectionState.Reconnecting(LOST_AFTER_ATTEMPTS).looksLost())
        assertTrue(ConnectionState.Reconnecting(LOST_AFTER_ATTEMPTS + 40).looksLost())
    }
}
