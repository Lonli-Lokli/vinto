package game.vinto.client

import game.vinto.protocol.LobbySeat
import game.vinto.protocol.LobbyView
import game.vinto.protocol.RoomPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The lobby's one line and one button, decided by the pure model rather than a composable. */
class LobbyModelTest {

    @Test
    fun theWordFollowsTheRoomsState() {
        assertEquals(
            LobbyWord.CONNECTING,
            lobbyUi(null, ConnectionState.Connecting, null).word,
        )
        assertEquals(
            LobbyWord.CONNECTING,
            lobbyUi(lobby(humans = 1), ConnectionState.Reconnecting(2), 0).word,
            "a lobby held from before the drop is stale until the socket is back",
        )
        assertEquals(
            LobbyWord.NEEDS_ANOTHER_HUMAN,
            lobbyUi(lobby(humans = 1), ConnectionState.Connected, 0).word,
        )
        assertEquals(
            LobbyWord.FILL_THE_SEATS,
            lobbyUi(lobby(humans = 2), ConnectionState.Connected, 0).word,
        )
        assertEquals(
            LobbyWord.COUNTING_DOWN,
            lobbyUi(
                lobby(humans = 2, phase = RoomPhase.STARTING, msUntilStart = 9_000.0),
                ConnectionState.Connected,
                0,
            ).word,
        )
        assertEquals(
            LobbyWord.OVER,
            lobbyUi(lobby(humans = 2), ConnectionState.Closed("the room ended"), 0).word,
        )
    }

    /**
     * A room that will not seat this build is not a room that could not be reached.
     *
     * Both are a `Closed` with a trouble, and the lobby read them as one: "Could not reach that
     * room. Check the code, or your connection." with a Retry under it — to a player whose code
     * and connection were both fine, and for whom retrying is the one thing certain to fail
     * again. What they need is the store.
     */
    @Test
    fun aBuildTheRoomWillNotSeatIsToldToUpdateNotToRetry() {
        val refused = lobbyUi(null, ConnectionState.Closed("too old", RoomTrouble.UPDATE_NEEDED), null)
        assertEquals(LobbyWord.UPDATE_NEEDED, refused.word)
        assertFalse(refused.canRetry, "a build below the floor was offered another go")
        assertFalse(refused.canAddBot)

        // Every other trouble is still the one worth another go.
        val lost = lobbyUi(null, ConnectionState.Closed("no signal", RoomTrouble.OFFLINE), null)
        assertEquals(LobbyWord.UNREACHABLE, lost.word)
        assertTrue(lost.canRetry)
    }

    /**
     * A countdown the room started says so; one a person started does not.
     *
     * They are the same state — `STARTING`, with a deadline — and a different event, which is
     * the whole reason the flag has to come off the wire rather than be inferred here. Somebody
     * who tapped "add a bot" a second ago needs no explanation. Somebody five minutes into a
     * wait, who put their phone down and picked it back up to a countdown they did not start,
     * needs to be told it is an offer and that removing a bot declines it.
     */
    @Test
    fun anOfferFromTheRoomIsNotTheSameSentenceAsACountdownSomebodyStarted() {
        assertEquals(
            LobbyWord.OFFERED_BOTS,
            lobbyUi(
                lobby(
                    humans = 2,
                    phase = RoomPhase.STARTING,
                    msUntilStart = 30_000.0,
                    botsOffered = true,
                ),
                ConnectionState.Connected,
                0,
            ).word,
        )
        assertEquals(
            30_000.0,
            lobbyUi(
                lobby(
                    humans = 2,
                    phase = RoomPhase.STARTING,
                    msUntilStart = 30_000.0,
                    botsOffered = true,
                ),
                ConnectionState.Connected,
                0,
            ).msUntilStart,
            "an offered deal counts down silently",
        )
    }

    @Test
    fun theCountdownShowsOnlyWhileItRuns() {
        val counting = lobbyUi(
            lobby(humans = 2, phase = RoomPhase.STARTING, msUntilStart = 7_000.0),
            ConnectionState.Connected,
            0,
        )
        assertEquals(7_000.0, counting.msUntilStart)

        val idle = lobbyUi(lobby(humans = 1), ConnectionState.Connected, 0)
        assertNull(idle.msUntilStart)
    }

    @Test
    fun addingABotNeedsAFreeSeatAndALiveSocket() {
        assertTrue(lobbyUi(lobby(humans = 2), ConnectionState.Connected, 0).canAddBot)
        assertFalse(
            lobbyUi(lobby(humans = 2), ConnectionState.Reconnecting(1), 0).canAddBot,
            "a button that fires into a dead socket is a lie",
        )
        assertFalse(
            lobbyUi(fullLobby(), ConnectionState.Connected, 0).canAddBot,
            "no seat, no bot",
        )
    }

    @Test
    fun mySeatIsMarkedAndOnlyMine() {
        val ui = lobbyUi(lobby(humans = 2), ConnectionState.Connected, mySeat = 1)
        assertEquals(listOf(false, true, false, false), ui.seats.map { it.isMine })
    }

    // ------------------------------------------------------------------ fixtures

    private fun lobby(
        humans: Int,
        phase: RoomPhase = RoomPhase.LOBBY,
        msUntilStart: Double? = null,
        botsOffered: Boolean = false,
    ) = LobbyView(
        phase = phase,
        seats = List(SEATS) { index ->
            LobbySeat(
                index = index,
                occupied = index < humans,
                isBot = false,
                removable = false,
                nickname = if (index < humans) "P$index" else null,
            )
        },
        humans = humans,
        botsOffered = botsOffered,
        msUntilStart = msUntilStart,
    )

    private fun fullLobby() = LobbyView(
        phase = RoomPhase.STARTING,
        seats = List(SEATS) { index ->
            LobbySeat(index, occupied = true, isBot = index >= 2, removable = index >= 2, nickname = "P$index")
        },
        humans = 2,
    )

    private companion object {
        const val SEATS = 4
    }
}
