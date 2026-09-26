package game.vinto.room

import game.vinto.protocol.MIN_PROTOCOL
import game.vinto.protocol.NoticeSeverity
import game.vinto.protocol.PROTOCOL_VERSION
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.ServerMessage
import game.vinto.protocol.UPDATE_AVAILABLE_CODE
import game.vinto.protocol.UPDATE_NEEDED_CODE
import game.vinto.shapes.VintoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The room's floor: a build below it is told to update at the door and never seated; a build
 * between the floor and the current number is seated and told once that a newer one waits.
 *
 * Refused at the door on purpose. A client that sat down is a client the room can talk to for
 * the whole game; the alternative — seating it and letting the first message it cannot read
 * freeze its table — is what an old build did in the final round before the number existed.
 */
class ProtocolFloorTest {
    private fun fresh() = newRoom("floor", 4242.0, "moderate", 0.0)

    private fun join(
        json: String,
        token: String,
        protocol: Int,
        floor: Int = MIN_PROTOCOL,
        current: Int = PROTOCOL_VERSION,
    ): JoinResult = VintoJson.decodeFromString(
        JoinResult.serializer(),
        joinRoom(json, token, "Ann", 0.0, protocol, floor, current),
    )

    @Test
    fun aBuildBelowTheFloorIsToldToUpdateBeforeItSits() {
        val old = join(fresh(), "tok-a", protocol = MIN_PROTOCOL - 1)
        assertEquals(-1, old.seat, "an old build was seated")
        assertEquals(UPDATE_NEEDED_CODE, old.code)
        assertTrue(assertNotNull(old.error).contains("update", ignoreCase = true), old.error)
    }

    /**
     * What an old build is sent at the door, and in which order: a notice, then the refusal.
     *
     * The refusal alone reached nobody. Every build in the stores turns `update-needed` into a
     * closed connection with a trouble, and its lobby reads any trouble as "Could not reach that
     * room. Check the code, or your connection." with a Retry that fails again. What those builds
     * *do* put in front of a player is a `notice`: a dialog titled "Update your app", with the
     * room's own sentence as its text and a button to the store. So the room says it that way
     * first. A build that knows the code takes the notice as the refusal (`RemoteVersionTest`),
     * and a build older than notices skips it and still has the error.
     */
    @Test
    fun anOldBuildIsToldInTheWordsEveryShippedBuildShows() {
        val old = join(fresh(), "tok-a", protocol = MIN_PROTOCOL - 1)
        val said = old.refusal.map { ProtocolJson.decodeFromString(ServerMessage.serializer(), it) }
        assertEquals(2, said.size, "the door said ${said.size} things: $said")

        val notice = assertIs<ServerMessage.Notice>(said[0], "the dialog has to be up before the lobby closes")
        assertEquals(UPDATE_NEEDED_CODE, notice.code)
        assertEquals(NoticeSeverity.WARNING, notice.severity)
        assertEquals(old.error, notice.message, "the dialog and the refusal said different things")

        val error = assertIs<ServerMessage.Error>(said[1])
        assertEquals(UPDATE_NEEDED_CODE, error.code)
        assertEquals(old.error, error.message)

        // A build that is seated is sent nothing of the kind.
        assertTrue(join(fresh(), "tok-b", PROTOCOL_VERSION).refusal.isEmpty())
    }

    @Test
    fun aCurrentBuildSitsQuietlyAndANewerOneIsNotTurnedAway() {
        // The wire only ever grows: a room never refuses a client for being ahead of it.
        val current = join(fresh(), "tok-a", PROTOCOL_VERSION)
        assertEquals(0, current.seat)
        assertNull(current.code)
        assertNull(current.advice, "a current build was nagged")
        assertEquals(0, join(fresh(), "tok-b", PROTOCOL_VERSION + 1).seat)
    }

    @Test
    fun aBuildStillAboveTheFloorIsSeatedAndToldOnceThatANewerOneWaits() {
        // The floor and the current number are parameters here because today they are equal:
        // nothing is between them yet, and the mechanism has to be right before anything is.
        val older = join(fresh(), "tok-a", protocol = 2, floor = 2, current = 3)
        assertEquals(0, older.seat, "a supported build was turned away")
        assertNull(older.code)
        assertEquals(UPDATE_AVAILABLE_CODE, older.advice)
    }

    @Test
    fun aSeatComingBackIsHeldToTheFloorToo() {
        // A token proves the seat is yours; it does not prove the build can read the game.
        val seated = join(fresh(), "tok-a", PROTOCOL_VERSION)
        val back = join(VintoJson.encodeToString(RoomState.serializer(), seated.state), "tok-a", MIN_PROTOCOL - 1)
        assertEquals(-1, back.seat, "an old build resumed a seat")
        assertEquals(UPDATE_NEEDED_CODE, back.code)
    }
}
