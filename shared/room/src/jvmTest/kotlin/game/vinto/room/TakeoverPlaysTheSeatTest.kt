package game.vinto.room

import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A seat a bot has taken over is a seat the room actually plays.
 *
 * The takeover was a flag and nothing more: it flipped the **seat's** `isBot` while the
 * engine's player kept `isHuman = true` — that is set once, at deal time, from
 * `tokenHash != null` — and the bot driver stopped on any seat holding a token, which is
 * exactly what a held seat looks like. So the room marked the seat as bot-played and then
 * waited for the person who had gone.
 *
 * The final round is where it costs the most: one turn per coalition member, so one dropped
 * teammate stops the round the coalition is trying to win.
 */
class TakeoverPlaysTheSeatTest {

    @Test
    fun aDroppedCoalitionMemberDoesNotStallTheFinalRound() {
        // Bob calls Vinto, so the coalition is Ann and the two filler bots. Bob has had his
        // turn; nothing in the round is waiting on him.
        val state = finalRoundCalledBy(seat = 1, dealtRoom())

        val room = decodeRoom(state)
        val game = checkNotNull(room.game)
        assertEquals(GamePhase.FINAL, game.phase, "the round is the final one")
        // Turn order runs 2, 3, then Ann's seat 0 before it comes back to the caller — so
        // hers is the last turn of the round, and the only one a person owes.
        assertEquals(2, game.currentPlayerIndex, "the seat after the caller is on play")

        // Ann's socket goes away; only Bob reports connected. Her seat is held, not given up.
        val dropped = decodeLifecycle(updatePresence(state, "1", LATER))
        assertEquals(LATER + SEAT_GRACE_MS, dropped.state.seatGrace[0])

        // The grace expires and a bot takes the seat over.
        val expired = decodeLifecycle(onAlarm(encode(dropped.state), LATER + SEAT_GRACE_MS + 1))
        assertEquals(listOf(0), expired.tookOver)
        assertTrue(expired.state.seats[0].isBot, "the seat is marked as being played")
        assertNotNull(expired.state.seats[0].tokenHash, "and still belongs to its token")

        // The point of taking a seat over is to play it. Ann's one remaining turn is the last
        // thing between the table and its score.
        val after = checkNotNull(expired.state.game)
        assertEquals(
            GamePhase.SCORING,
            after.phase,
            "a taken-over coalition seat must be played, not merely flagged — " +
                "the round stopped on the player who left",
        )
        assertEquals(RoomPhase.PLAYING, expired.state.phase, "a bot playing a seat is not the session ending")
    }

    @Test
    fun theTableIsToldWhichSeatABotIsCovering() {
        val state = finalRoundCalledBy(seat = 1, dealtRoom())
        val ann = checkNotNull(decodeRoom(state).seats[0].playerId)

        val dropped = decodeLifecycle(updatePresence(state, "1", LATER))
        val envelopes = decodeAlarm(
            alarmEnvelopes(encode(dropped.state), LATER + SEAT_GRACE_MS + 1),
        )

        // The takeover cannot ride on the view — `isHuman` is inside the state hash, so the
        // room never writes it — so the envelope has to say it out loud, and say it to
        // everybody rather than only to the person coming back.
        val away = envelopes.messages.values.map { text ->
            when (val message = ProtocolJson.decodeFromString(ServerMessage.serializer(), text)) {
                is ServerMessage.Events -> message.away
                is ServerMessage.Sync -> message.away
                else -> emptyList()
            }
        }
        assertTrue(
            away.any { ann in it },
            "the seat a bot is covering is named to the table, not just on its owner's return",
        )
    }

    /** The dealt room, moved into a final round called by [seat]. */
    private fun finalRoundCalledBy(seat: Int, dealtJson: String): String {
        val room = decodeRoom(dealtJson)
        val dealt = checkNotNull(room.game) { "a dealt room has a game" }
        val caller = room.seats[seat].playerId

        // The room deals a person's seat with nothing seen, so the two humans owe their
        // setup peeks before the round can start.
        var peeked = dealt
        for (player in dealt.players) {
            repeat(SETUP_PEEKS - player.knownCardPositions.size) { seen ->
                peeked = reduce(peeked, GameAction.PeekSetupCard(PositionPayload(player.id, seen)))
            }
        }

        val ready = reduce(peeked, GameAction.FinishSetup(PlayerIdPayload(peeked.players.first().id)))
        val onPlay = ready.copy(
            currentPlayerIndex = ready.players.indexOfFirst { it.id == caller },
            subPhase = GameSubPhase.IDLE,
        )
        val called = reduce(onPlay, GameAction.CallVinto(PlayerIdPayload(checkNotNull(caller))))

        // Vinto is called at the *end* of a turn, and the engine leaves the index on the
        // caller for the turn-end to move; the round proper starts on the seat after them.
        val running = called.copy(
            currentPlayerIndex = (called.currentPlayerIndex + 1) % called.players.size,
            subPhase = GameSubPhase.IDLE,
        )

        return encode(room.copy(game = running))
    }

    private fun reduce(state: GameState, action: GameAction): GameState =
        when (val result = GameEngine.reduce(state, action)) {
            is ReduceResult.Success -> result.state
            is ReduceResult.Failure -> error("fixture move refused: ${result.reason}")
        }

    private companion object {
        const val SETUP_PEEKS = 2
    }
}
