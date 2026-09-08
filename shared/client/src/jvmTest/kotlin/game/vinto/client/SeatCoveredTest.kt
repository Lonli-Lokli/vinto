package game.vinto.client

import game.vinto.protocol.ServerMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The table is told when a bot takes somebody's seat, and told when they come back.
 *
 * The room already says which seats a bot is covering — `away`, on every batch and every
 * sync, because the takeover is deliberately not written into the game state. What nothing
 * did was *announce* it: the set changed, one small mark appeared on a plate, and from every
 * other seat a person appeared to be playing themselves at machine speed. A takeover is the
 * biggest thing that can happen to a table without anybody moving a card, and the strip that
 * narrates every draw and every toss-in said nothing about it.
 *
 * Said once per change, in both directions, and to everyone — the line is in each seat's own
 * log because each seat's session builds it from the same `away` the room broadcasts.
 */
class SeatCoveredTest {

    @Test
    fun theTableIsToldWhenABotTakesASeat() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.deliver(ServerMessage.Started(view = wire.dealtView, nextIndex = 0))
        wire.settle()

        val session = assertNotNull(wire.room.session.value, "a deal creates the session")
        val before = session.log.value.size
        val gone = wire.dealtView.players[1].id

        wire.deliver(ServerMessage.Events(events = emptyList(), nextIndex = 0, away = listOf(gone)))
        wire.settle()

        assertTrue(
            session.log.value.size > before,
            "a bot took a seat and the table was told nothing",
        )
        assertTrue(gone in session.away.value, "the session did not record who is being covered")

        wire.room.leave()
    }

    /** And once, not once per batch: the same set arriving again is not news. */
    @Test
    fun aSeatIsOnlyAnnouncedWhenItChanges() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.deliver(ServerMessage.Started(view = wire.dealtView, nextIndex = 0))
        wire.settle()

        val session = assertNotNull(wire.room.session.value)
        val gone = wire.dealtView.players[1].id

        wire.deliver(ServerMessage.Events(events = emptyList(), nextIndex = 0, away = listOf(gone)))
        wire.settle()
        val told = session.log.value.size

        wire.deliver(ServerMessage.Events(events = emptyList(), nextIndex = 0, away = listOf(gone)))
        wire.settle()

        assertTrue(session.log.value.size == told, "the same takeover was announced twice")

        wire.room.leave()
    }

    /** Coming back is news too — the seat stops being a bot and the table should hear so. */
    @Test
    fun theTableIsToldWhenSomebodyComesBack() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.deliver(ServerMessage.Started(view = wire.dealtView, nextIndex = 0))
        wire.settle()

        val session = assertNotNull(wire.room.session.value)
        val gone = wire.dealtView.players[1].id

        wire.deliver(ServerMessage.Events(events = emptyList(), nextIndex = 0, away = listOf(gone)))
        wire.settle()
        val told = session.log.value.size

        wire.deliver(ServerMessage.Events(events = emptyList(), nextIndex = 0, away = emptyList()))
        wire.settle()

        assertTrue(session.log.value.size > told, "somebody came back and the table was not told")
        assertTrue(gone !in session.away.value, "the seat is still marked as covered")

        wire.room.leave()
    }
}
