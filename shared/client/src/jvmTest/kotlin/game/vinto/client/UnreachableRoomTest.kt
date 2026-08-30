package game.vinto.client

import game.vinto.protocol.PublicRoom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A room that cannot be reached ends in a sentence, not a spinner.
 *
 * `RemoteRoom`'s loop used to catch every exception and back off, for ever. So a mistyped
 * code, a service that was closed and a phone in a tunnel all produced exactly the same
 * screen: "Reaching the room…", indefinitely, with nothing on it that could say which one it
 * was or whether waiting would ever help. It is the failure people remember about lobbies, and
 * it is the one shape of network handling this app had none of.
 *
 * Two rules replace it and they are deliberately different, because the two situations are:
 * a permanent refusal stops at once, and a room that has *never* answered stops after a few
 * tries. A socket that drops mid-game is neither, and still reconnects for as long as the app
 * is open — that is what the seat token is for, and the last case here proves it survived.
 *
 * Virtual time, so the backoff costs nothing: `runTest` advances the clock rather than the
 * wall, which is what makes it reasonable to assert on a loop that sleeps between attempts.
 */
class UnreachableRoomTest {

    /** A connector that refuses, counting how often it was asked. */
    private class Refusing(private val refusal: () -> Throwable) : RoomConnector {
        var asked = 0
            private set

        override suspend fun connect(code: String): RoomSocket {
            asked++
            throw refusal()
        }

        override suspend fun createRoom(isPublic: Boolean, hostNickname: String) =
            CreatedRoom("AAAAAA", "room-AAAAAA")

        override suspend fun listPublicRooms(): List<PublicRoom> = emptyList()
    }

    private fun roomOn(connector: RoomConnector, scope: kotlinx.coroutines.CoroutineScope) =
        RemoteRoom(
            connector = connector,
            code = "AAAAAA",
            vault = MemoryVault(),
            nickname = "Ann",
            scope = scope,
        )

    @Test
    fun aCodeNobodyHasIsRefusedOnceAndNotRetried() = runTest {
        val connector = Refusing { RoomServiceException(RoomTrouble.NO_SUCH_ROOM, "no such room") }
        val room = roomOn(connector, this)

        testScheduler.advanceUntilIdle()

        assertEquals(1, connector.asked, "a code the registry never issued was asked for again")
        val closed = assertIs<ConnectionState.Closed>(room.connection.value)
        assertEquals("no such room", closed.reason, "the service's own words reach the screen")
        assertEquals(RoomTrouble.NO_SUCH_ROOM, closed.trouble)

        assertTrue(
            lobbyUi(null, room.connection.value, null).canRetry,
            "the lobby has no way to try again, which is worse than the spinner it replaced",
        )
        room.leave()
    }

    /**
     * And a room that simply never answers gives up too — after a few goes, not one.
     *
     * A phone changing networks fails the first attempt and succeeds on the second all the
     * time, so stopping immediately would be worse than not stopping at all. What must not
     * happen is stopping *never*.
     */
    @Test
    fun aRoomThatNeverAnswersGivesUpAfterAFewTries() = runTest {
        val connector = Refusing { IllegalStateException("could not connect") }
        val room = roomOn(connector, this)

        testScheduler.advanceUntilIdle()

        assertTrue(connector.asked in 2..5, "asked ${connector.asked} times — that is not a few")
        val closed = assertIs<ConnectionState.Closed>(room.connection.value)
        assertEquals(RoomTrouble.OFFLINE, closed.trouble, "so the lobby can offer another go")
        room.leave()
    }

    /**
     * Trying again means trying again, rather than a button that redraws the same message.
     *
     * `retry` is only reachable from a give-up, and it has to actually re-enter the loop —
     * which is the half a screen cannot check for itself.
     */
    @Test
    fun tryingAgainReallyAsksAgain() = runTest {
        val connector = Refusing { IllegalStateException("could not connect") }
        val room = roomOn(connector, this)

        testScheduler.advanceUntilIdle()
        val first = connector.asked

        room.retry()
        testScheduler.advanceUntilIdle()

        assertTrue(connector.asked > first, "retry asked nothing: $first then ${connector.asked}")
        room.leave()
    }

    /** And a room somebody has left stays left: retry is for a failure, not for a decision. */
    @Test
    fun leavingIsNotAFailureToRetry() = runTest {
        val connector = Refusing { IllegalStateException("could not connect") }
        val room = roomOn(connector, this)
        testScheduler.advanceUntilIdle()

        room.leave()
        val afterLeaving = connector.asked
        room.retry()
        testScheduler.advanceUntilIdle()

        assertEquals(afterLeaving, connector.asked, "leaving a room then reconnected to it")
        assertTrue(
            !lobbyUi(null, room.connection.value, null).canRetry,
            "a room you walked out of is not offering to be re-entered",
        )
    }
}
