package game.vinto.client

import game.vinto.protocol.ClientMessage
import game.vinto.protocol.ProtocolJson
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * A room goes on when the browser, rather than the network, says no.
 *
 * Every catch on the way to the socket caught `Exception`, and a JavaScript exception on
 * Kotlin/Wasm is not one ([BrowserFailure]). A socket that refused a write, or a connection the
 * browser dropped, therefore failed the room's own coroutines instead of being the failed send or
 * the reconnect the same failure is on every other platform. `runTest` fails a test whose
 * coroutines fail, which is what these catch.
 */
class ABrowserFailureLeavesTheRoomStandingTest {

    @Test
    fun aMoveTheBrowserWillNotSendIsARefusal() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)

        wire.socket.sendFails = BrowserFailure("InvalidStateError: Still in CONNECTING state.")
        assertNotNull(
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId))),
            "a move that never left was reported as accepted",
        )
        wire.room.leave()
    }

    @Test
    fun aMessageTheBrowserWillNotSendDoesNotEndTheRoom() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()

        wire.socket.sendFails = BrowserFailure("InvalidStateError: Still in CONNECTING state.")
        wire.room.moreTime()
        wire.room.quit()
        wire.settle()
    }

    @Test
    fun aSocketTheBrowserDropsIsRejoined() = runTest {
        val second = ScriptedSocket()
        val wire = Wire(this, spareSockets = listOf(second))
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()

        wire.socket.fail(BrowserFailure("NetworkError: the socket was closed"))
        wire.settle()

        val rejoin = ProtocolJson.decodeFromString(ClientMessage.serializer(), assertNotNull(second.sent.firstOrNull()))
        assertIs<ClientMessage.Join>(rejoin)
        wire.room.leave()
    }
}
