package game.vinto.client

import game.vinto.protocol.ClientMessage
import game.vinto.protocol.NoticeSeverity
import game.vinto.protocol.PROTOCOL_VERSION
import game.vinto.protocol.ServerMessage
import game.vinto.protocol.UPDATE_AVAILABLE_CODE
import game.vinto.protocol.UPDATE_NEEDED_CODE
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The client says which wire it speaks; takes "update" as the one refusal that is not a retry;
 * and carries a notice the room asked to have said once, until the screen has said it.
 */
class RemoteVersionTest {

    @Test
    fun theJoinCarriesTheProtocolVersion() = runTest {
        val wire = Wire(this)
        wire.settle()
        val join = assertIs<ClientMessage.Join>(wire.socket.lastSent().message)
        assertEquals(PROTOCOL_VERSION, join.protocol, "the join did not say which wire it speaks")
        wire.room.leave()
    }

    @Test
    fun aRoomThatWantsANewerBuildIsATroubleTheScreenCanActOn() = runTest {
        val wire = Wire(this)
        wire.settle()
        wire.deliver(ServerMessage.Error(message = "too old", code = UPDATE_NEEDED_CODE))
        wire.settle()
        val closed = assertIs<ConnectionState.Closed>(wire.room.connection.value, "the refusal was not final")
        assertEquals(RoomTrouble.UPDATE_NEEDED, closed.trouble)
        wire.room.leave()
    }

    @Test
    fun aNoticeIsHeldUntilTheScreenHasSaidIt() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.settle()
        assertNull(wire.room.notice.value)

        wire.deliver(ServerMessage.Notice(UPDATE_AVAILABLE_CODE, "a newer build is waiting", NoticeSeverity.WARNING))
        wire.settle()
        val notice = assertNotNull(wire.room.notice.value, "the notice was lost")
        assertEquals(UPDATE_AVAILABLE_CODE, notice.code)
        assertEquals(true, notice.warning)

        wire.room.dismissNotice()
        assertNull(wire.room.notice.value, "a dismissed notice came back")
        wire.room.leave()
    }
}
