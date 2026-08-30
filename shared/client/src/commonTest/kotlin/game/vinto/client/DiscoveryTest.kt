package game.vinto.client

import game.vinto.protocol.PublicRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The public-room browser's rows, decided by the pure model rather than by a composable. */
class DiscoveryTest {

    @Test
    fun aRoomWithRoomToSitIsJoinable() {
        val row = discoveryRows(listOf(room(seatsFilled = 2, humans = 2))).single()

        assertTrue(row.joinable)
        assertEquals(2, row.seatsFree)
        assertEquals(2, row.humans)
    }

    @Test
    fun aFullTableIsListedButNotJoinable() {
        // Still shown: a table empties as often as it fills, and a row that vanishes while
        // somebody is reading it is worse than one that says "full".
        val row = discoveryRows(listOf(room(seatsFilled = 4, humans = 4))).single()

        assertFalse(row.joinable)
        assertEquals(0, row.seatsFree)
    }

    @Test
    fun aCountdownIsRoundedUpSoItNeverSitsOnZero() {
        val row = discoveryRows(listOf(room(seatsFilled = 4, humans = 2, startsIn = 1))).single()

        assertEquals(1, row.startsInSeconds)
    }

    @Test
    fun aCountdownThatHasRunOutIsNotACountdown() {
        // The service clamps at zero, and zero is a room mid-deal rather than one to wait for.
        val row = discoveryRows(listOf(room(seatsFilled = 4, humans = 2, startsIn = 0))).single()

        assertNull(row.startsInSeconds)
    }

    @Test
    fun theServicesOrderIsKept() {
        // Two people looking at one lobby must see one list. Re-sorting on the client is how
        // they end up seeing two, and one of them taps the row the other was reading.
        val rooms = listOf(
            room(code = "ZZZZZZ", seatsFilled = 1, humans = 1),
            room(code = "AAAAAA", seatsFilled = 3, humans = 3),
        )

        assertEquals(listOf("ZZZZZZ", "AAAAAA"), discoveryRows(rooms).map { it.code })
    }

    @Test
    fun aHostWithNoNameIsNoName() {
        assertNull(discoveryRows(listOf(room(host = null))).single().host)
        assertNull(discoveryRows(listOf(room(host = "   "))).single().host)
    }

    @Test
    fun nonsenseFromTheServiceIsClampedRatherThanDrawn() {
        // The service is trusted to be correct, not to be undamaged: a seat count of nine
        // would otherwise draw nine seats, or a negative number of free ones.
        val row = discoveryRows(listOf(room(seatsFilled = 9, humans = -2))).single()

        assertEquals(4, row.seatsFilled)
        assertEquals(0, row.seatsFree)
        assertEquals(0, row.humans)
    }

    @Test
    fun quietMeansAnsweredAndEmptyRatherThanStillAsking() {
        assertTrue(DiscoveryState().quiet)
        assertFalse(DiscoveryState(loading = true).quiet)
        assertFalse(
            DiscoveryState(
                failure = RoomAnswer.Failed(RoomTrouble.OFFLINE, "no answer"),
            ).quiet,
        )
        assertFalse(DiscoveryState(rows = discoveryRows(listOf(room()))).quiet)
    }

    private fun room(
        code: String = "ABC234",
        host: String? = "Ada",
        seatsFilled: Int = 1,
        humans: Int = 1,
        startsIn: Long? = null,
    ) = PublicRoom(
        code = code,
        hostNickname = host,
        humans = humans,
        seatsFilled = seatsFilled,
        msUntilStart = startsIn?.toDouble(),
    )
}
