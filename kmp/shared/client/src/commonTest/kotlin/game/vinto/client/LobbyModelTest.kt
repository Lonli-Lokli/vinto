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
