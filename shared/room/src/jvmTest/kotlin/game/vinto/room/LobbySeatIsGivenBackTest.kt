package game.vinto.room

import game.vinto.protocol.PROTOCOL_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A seat in a lobby belongs to whoever is sitting in it, and before the deal nobody is.
 *
 * Reported from a real room: a host opened a table, shared the invitation, and the link was
 * opened by something that was only looking at it — a scanner's preview, a browser beside the
 * app. That client had no seat token, so it was seated as a stranger, and the seat was then
 * held for a token nobody would ever send again: thirty seconds later it became a bot that is
 * not [Seat.isFiller] and therefore cannot be removed either. One glance at an invitation cost
 * a table one of its four chairs for the whole ten minutes a lobby lives.
 *
 * The seat grace exists so that a **hand** a bot is keeping warm comes back to its owner. In a
 * lobby there is no hand, so there is nothing to keep and nothing to lose by giving the chair
 * back — and everything to lose by holding it. In a dealt game the takeover is unchanged, which
 * the last test here holds.
 */
class LobbySeatIsGivenBackTest {

    @Test
    fun aSeatNobodyIsConnectedToInALobbyIsGivenBackRatherThanHeld() {
        // Ann and Bob are seated. Bob's socket goes; his seat starts its grace.
        val dropped = decodeLifecycle(updatePresence(lobbyOfTwo(), "0", LATER))
        assertEquals(LATER + LOBBY_SEAT_GRACE_MS, dropped.state.seatGrace[1], "the lobby's own grace")

        val expired = decodeLifecycle(onAlarm(encode(dropped.state), LATER + LOBBY_SEAT_GRACE_MS + 1))

        val seat = expired.state.seats[1]
        assertFalse(seat.occupied, "the chair is empty again")
        assertNull(seat.tokenHash, "and belongs to nobody")
        assertNull(seat.profile, "so it carries no name for the lobby to draw")
        assertFalse(seat.isBot, "a lobby seat is given back, not played")
        assertEquals(listOf(1), expired.gaveBack, "the room says which, so the lobby is redrawn")
        assertEquals(emptyList(), expired.tookOver, "nothing was taken over — there is no hand yet")
    }

    @Test
    fun andSomebodyElseCanThenSitDown() {
        val dropped = decodeLifecycle(updatePresence(lobbyOfTwo(), "0", LATER))
        val expired = decodeLifecycle(onAlarm(encode(dropped.state), LATER + LOBBY_SEAT_GRACE_MS + 1))

        // The whole point: the table is not one chair short for the rest of its life.
        val arrived = decodeJoin(
            joinRoom(encode(expired.state), STRANGER, "Cara", LATER + LOBBY_SEAT_GRACE_MS + 2, PROTOCOL_VERSION),
        )
        assertEquals(1, arrived.seat, "the freed seat is the first one free")
        assertNull(arrived.error)
    }

    @Test
    fun aSeatWithAHandInItIsStillCoveredRatherThanEmptied() {
        // Once the cards are dealt the old rule is the right one: the seat holds a hand, the
        // bot plays it, and the token brings its owner back to it.
        val dropped = decodeLifecycle(updatePresence(dealtRoom(), "1", LATER))
        assertEquals(LATER + SEAT_GRACE_MS, dropped.state.seatGrace[0], "a dealt seat keeps the short grace")

        val expired = decodeLifecycle(onAlarm(encode(dropped.state), LATER + SEAT_GRACE_MS + 1))

        assertTrue(expired.state.seats[0].isBot, "the seat is played")
        assertTrue(expired.state.seats[0].occupied, "and still held")
        assertEquals(listOf(0), expired.tookOver)
        assertEquals(emptyList(), expired.gaveBack)
    }
}
