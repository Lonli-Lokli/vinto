package game.vinto.room

import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.TableTalk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The room's door for table talk.
 *
 * A sentence is checked the way an action is — the token names the seat, and a seat speaks
 * only as itself — and charged to the same budget, because talk is broadcast to every socket
 * and an uncapped sentence is a flood with extra steps.
 *
 * What it is *not* is a move. Nothing here reaches the engine, the log or a recording: claims
 * are game state and travel as `DECLARE_CARDS`, and everything in the phrasebook is the
 * transient half (design D6).
 */
class TableTalkDoorTest {

    private fun sayFrom(state: String, token: String, talk: TableTalk, at: Double = START + 1_000.0) =
        decodeEnvelopes(sayEnvelopes(state, token, ProtocolJson.encodeToString(TableTalk.serializer(), talk), at))

    private fun standing(by: String) = TableTalk.Standing(by, TableTalk.Standing.Where.LOW)

    @Test
    fun everySeatHearsWhatAnybodySays() {
        val state = dealtRoom()
        val ann = checkNotNull(decodeRoom(state).seats[0].playerId)

        val spoken = sayFrom(state, TOKEN_A, standing(ann))

        assertNull(spoken.error)
        // Broadcast, the Vinto caller included: the coalition confers out loud at a table, and
        // it costs them nothing, because the caller has already had their turn.
        assertEquals(
            decodeRoom(state).seats.count { it.playerId != null },
            spoken.messages.size,
            "somebody at the table did not hear it",
        )
        val heard = ProtocolJson.decodeFromString(
            ServerMessage.serializer(),
            spoken.messages.values.first(),
        )
        assertTrue(heard is ServerMessage.Said && heard.talk == standing(ann))
    }

    @Test
    fun aSeatMayNotSpeakAsAnother() {
        val state = dealtRoom()
        val bob = checkNotNull(decodeRoom(state).seats[1].playerId)

        // Ann's token, Bob's name on the sentence. The same rule an action's `actorId` gets:
        // a phrasebook that let one seat put words in another's mouth would be worse than a
        // chat box, not better.
        val spoken = sayFrom(state, TOKEN_A, standing(bob))

        assertNotNull(spoken.error, "one seat spoke as another")
        assertTrue(spoken.messages.isEmpty(), "and it was broadcast anyway")
    }

    @Test
    fun aStrangersTokenSaysNothing() {
        val spoken = sayFrom(dealtRoom(), STRANGER, standing("anyone"))
        assertNotNull(spoken.error)
    }

    @Test
    fun talkIsCappedOnTheSameBudgetAsMoves() {
        var state = dealtRoom()
        val ann = checkNotNull(decodeRoom(state).seats[0].playerId)

        // Said over and over at the same instant, so nothing refills between them.
        var refusedAt = -1
        repeat(TOO_MUCH) { attempt ->
            val spoken = sayFrom(state, TOKEN_A, standing(ann), at = START + 1_000.0)
            state = encode(spoken.state)
            if (spoken.error != null && refusedAt < 0) refusedAt = attempt
        }

        assertTrue(refusedAt in 1 until TOO_MUCH, "talk was never capped (refused at $refusedAt)")
    }

    @Test
    fun talkNeverBecomesAMove() {
        val state = dealtRoom()
        val ann = checkNotNull(decodeRoom(state).seats[0].playerId)
        val before = decodeRoom(state)

        val spoken = sayFrom(state, TOKEN_A, standing(ann))

        assertEquals(before.log.size, spoken.state.log.size, "a sentence reached the log")
        assertEquals(before.nextIndex, spoken.state.nextIndex)
        assertEquals(before.game, spoken.state.game, "a sentence moved the game")
    }

    @Test
    fun anUnreadableSentenceIsRefusedRatherThanThrown() {
        val nonsense = """{"type":"nonsense"}"""
        val spoken = decodeEnvelopes(sayEnvelopes(dealtRoom(), TOKEN_A, nonsense, START))
        assertNotNull(spoken.error)
    }

    @Test
    fun aProposalIsRelayedAndNeverReduced() {
        // The rule the whole design turns on: a proposal is a move the *proposer cannot make*,
        // addressed to the seat that can. It carries no authority — the room passes it on and
        // the engine never sees it. What reaches the engine is the recipient's own move, if
        // they choose to make it.
        val state = dealtRoom()
        val room = decodeRoom(state)
        val ann = checkNotNull(room.seats[0].playerId)
        val bob = checkNotNull(room.seats[1].playerId)

        val proposal = TableTalk.Proposal(
            by = ann,
            to = bob,
            move = GameAction.DrawCard(PlayerIdPayload(bob)),
        )
        val spoken = sayFrom(state, TOKEN_A, proposal)

        assertNull(spoken.error, "the room refused to carry a suggestion")
        assertEquals(room.log.size, spoken.state.log.size, "a suggestion was recorded as a move")
        assertEquals(room.game, spoken.state.game, "a suggestion moved the game")
        assertTrue(
            spoken.messages.values.any { it.contains(bob) },
            "the seat it was addressed to was not told",
        )
    }

    @Test
    fun theRoomsBotsAnswerASuggestionTheSameWayASoloGameDoes() {
        // A person talking to a bot must get the same game whichever session they are in.
        // Before this the room relayed a proposal and nothing answered it, so the whole
        // "propose, never command" design was reachable only in a solo game.
        val state = finalRoundWithBots()
        val room = decodeRoom(state)
        val ann = checkNotNull(room.seats[0].playerId)
        val botSeat = checkNotNull(room.seats.first { it.tokenHash == null }.playerId)

        val spoken = sayFrom(
            state,
            TOKEN_A,
            TableTalk.Proposal(ann, botSeat, GameAction.DrawCard(PlayerIdPayload(botSeat))),
        )

        assertNull(spoken.error)
        val said = spoken.messages.values.flatMap { text ->
            val message = ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
            if (message is ServerMessage.Events) message.said else emptyList()
        }
        assertTrue(
            said.any { it is TableTalk.Answer && it.by == botSeat },
            "no bot answered the suggestion: $said",
        )
    }

    /** A dealt room moved into a final round called by a *bot*, so the coalition holds people. */
    private fun finalRoundWithBots(): String {
        val room = decodeRoom(dealtRoom())
        val game = checkNotNull(room.game)
        val caller = checkNotNull(room.seats.last { it.tokenHash == null }.playerId)
        return encode(
            room.copy(
                game = game.copy(
                    phase = GamePhase.FINAL,
                    finalTurnTriggered = true,
                    vintoCallerId = caller,
                    players = game.players.map { it.copy(isVintoCaller = it.id == caller) },
                ),
            ),
        )
    }

    private companion object {
        /** Comfortably past `BUCKET_CAPACITY`, whatever it is set to. */
        const val TOO_MUCH = 60
    }
}
