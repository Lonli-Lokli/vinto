package game.vinto.client

import game.vinto.protocol.PublicRoom
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A room that is not there any more stops being offered.
 *
 * The front door keeps a way back into the room this device stepped out of, and it offers it
 * from the *vault* rather than by asking the service first — deliberately, so the door works
 * on a bad network and a refusal is handled where every other refusal is, on the way in.
 *
 * What was missing was the other half of that sentence. Nothing on the way in ever wrote the
 * refusal down, so a room that had been deleted — its last player gone, its two-minute
 * emptiness clock run out — went on being offered for ever: "return to your room", pointing at
 * a table that does not exist, on a screen whose public list is empty. Reported as exactly
 * that pair.
 *
 * The rule is the narrow one. Only the two answers that mean *the table is not there* forget
 * it; a network that cannot be reached, a room that is busy, a build that needs updating all
 * leave the seat alone, because in each of those the seat may well still be waiting.
 */
class GoneRoomIsForgottenTest {

    @Test
    fun aRoomThatDoesNotExistIsForgotten() = runTest {
        val vault = heldRoom()
        connectAnswering(this, vault, RoomTrouble.NO_SUCH_ROOM)

        assertNull(vault.currentRoom(), "the door still offers a room the service has never heard of")
        assertNull(vault.seatToken(WIRE_CODE), "the seat token outlived the room it was for")
    }

    /** A closed service is the same answer for this purpose: there is no table to go back to. */
    @Test
    fun aClosedRoomIsForgottenToo() = runTest {
        val vault = heldRoom()
        connectAnswering(this, vault, RoomTrouble.CLOSED)

        assertNull(vault.currentRoom(), "a closed room is still offered")
    }

    /** Offline is the case the door was built for: the seat is probably still there. */
    @Test
    fun aRoomIsKeptWhenTheProblemIsTheNetwork() = runTest {
        val vault = heldRoom()
        connectAnswering(this, vault, RoomTrouble.OFFLINE)

        assertEquals(WIRE_CODE, vault.currentRoom(), "a network problem gave the seat away")
        assertTrue(vault.seatToken(WIRE_CODE) != null, "and took the token with it")
    }

    /**
     * And a build below the room's floor keeps its seat.
     *
     * Permanent — no retry helps — but the room is *there*, and the answer is to update the
     * app and walk back in. Forgetting the seat would make an update cost the round.
     */
    @Test
    fun aRoomIsKeptWhenTheAppNeedsUpdating() = runTest {
        val vault = heldRoom()
        connectAnswering(this, vault, RoomTrouble.UPDATE_NEEDED)

        assertEquals(WIRE_CODE, vault.currentRoom(), "an old build gave the seat away")
    }

    // ------------------------------------------------------------------ plumbing

    /** A device that stepped out of a room and still holds its seat. */
    private fun heldRoom(): MemoryVault = MemoryVault().apply {
        rememberRoom(WIRE_CODE)
        saveSeatToken(WIRE_CODE, "tok-held")
    }

    /** Tries to walk back in against a service that answers with [trouble], and settles. */
    private fun connectAnswering(scope: TestScope, vault: MemoryVault, trouble: RoomTrouble) {
        val room = RemoteRoom(
            connector = RefusingConnector(trouble),
            code = WIRE_CODE,
            vault = vault,
            nickname = "Ann",
            scope = scope,
        )
        scope.testScheduler.advanceUntilIdle()
        room.leave()
        scope.testScheduler.advanceUntilIdle()
    }

    private class RefusingConnector(private val trouble: RoomTrouble) : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> =
            RoomAnswer.Failed(trouble, "this test refuses")

        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            RoomAnswer.Failed(trouble, "this test refuses")

        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> =
            RoomAnswer.Ok(emptyList())
    }
}
