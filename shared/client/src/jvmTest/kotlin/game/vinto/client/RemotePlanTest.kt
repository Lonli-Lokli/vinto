package game.vinto.client

import game.vinto.protocol.ClientMessage
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.PublicSeat
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Lane
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared plan over the wire (design D7a): the board comes **in** on whatever message carries
 * it and an edit goes **out** as one part. The room is the authority — the session never edits
 * its copy locally — so what a screen reads is always what the room last said.
 */
class RemotePlanTest {

    @Test
    fun theBoardComesInOnEventsAndAnEditGoesOutAsAPart() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)
        assertNull(session.plan.value, "a fresh table has no plan")

        val me = session.playerId
        val board = CoalitionPlan(lanes = listOf(Lane(me, Step.TakeTheDiscard)), agreed = listOf(me), editedBy = me)
        // An edit is answered as `more-time` is: an empty events carrying the board.
        wire.deliver(ServerMessage.Events(events = emptyList(), nextIndex = 0, view = wire.dealtView, plan = board))
        wire.settle()
        assertEquals(board, session.plan.value, "the board did not come off the events")

        val edit = PlanEdit.SetLane(me, Step.Declare(Rank.KING))
        assertNull(session.editPlan(edit))
        wire.pump()
        assertEquals(edit, assertIs<ClientMessage.EditPlan>(wire.socket.lastSent().message).edit)
        assertEquals(board, session.plan.value, "the session edited its own copy instead of waiting for the room")

        assertNull(session.agreePlan(agree = true))
        wire.pump()
        assertTrue(assertIs<ClientMessage.AgreePlan>(wire.socket.lastSent().message).agree)

        wire.room.leave()
    }

    @Test
    fun aSyncLandsOnThePresentBoardAndTheRoundsEndClearsIt() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)
        val me = session.playerId
        val board = CoalitionPlan(lanes = listOf(Lane(me, Step.TakeTheDiscard)), agreed = listOf(me), editedBy = me)

        // The reconnect case D11 was fixed for: the sync carries the plan as it now stands.
        wire.deliver(ServerMessage.Sync(events = emptyList(), nextIndex = 0, view = wire.dealtView, plan = board))
        wire.settle()
        assertEquals(board, session.plan.value, "a reconnect lost the plan")

        // And between rounds there is none: the room threw last round's away at scoring.
        wire.deliver(ServerMessage.BetweenRounds(view = wire.dealtView, standings = emptyList(), nextIndex = 0))
        wire.settle()
        assertNull(session.plan.value, "last round's plan was carried into the next")

        wire.room.leave()
    }

    @Test
    fun anAppRestartedMidRoundLandsOnTheBoardFromTheJoin() = runTest {
        val wire = Wire(this)
        val me = wire.dealtView.viewerId
        val board = CoalitionPlan(lanes = listOf(Lane(me, Step.TakeTheDiscard)), agreed = listOf(me), editedBy = me)

        wire.socket.deliver(
            ProtocolJson.encodeToString(
                ServerMessage.serializer(),
                ServerMessage.Joined(
                    seat = 0,
                    token = "tok-1",
                    seats = List(WIRE_SEATS) { PublicSeat(index = it, occupied = it == 0) },
                    nextIndex = 0,
                    lobby = wire.lobbyWith(occupied = setOf(0)),
                    view = wire.dealtView,
                    plan = board,
                ),
            ),
        )
        wire.settle()

        val session = assertNotNull(wire.room.session.value, "a join with a view creates the session")
        assertEquals(board, session.plan.value, "the join answer's plan was dropped")

        wire.room.leave()
    }
}
