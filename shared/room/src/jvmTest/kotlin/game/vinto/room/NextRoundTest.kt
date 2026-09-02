package game.vinto.room

import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.RoundResult
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.GamePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Agreeing to another round.
 *
 * The rule is one sentence in `readyForNextRound`'s comment — every *connected* human has to
 * agree, and the last one to say so deals — and it ran for as long as the room has existed
 * without a test reaching it: the two-client harness plays one round and stops, and the
 * wrangler gates prove sockets rather than rules. What it decides is real: who a table waits
 * on, whether tapping twice deals early, whether the second deal is a fresh shuffle, and
 * whether the session's clock survives the boundary or restarts with each round.
 */
class NextRoundTest {

    @Test
    fun theLastConnectedHumanToAgreeDealsTheNextRound() {
        val between = betweenRounds(dealtRoom())
        val buzzer = assertNotNull(between.session.endsAtEpochMs)

        val ann = decodeJoin(readyForNextRound(encode(between), TOKEN_A, LATER))
        assertNull(ann.error)
        assertEquals(0, ann.seat)
        assertEquals(RoomPhase.BETWEEN_ROUNDS, ann.state.phase, "one of two is not everyone")
        assertEquals(listOf(0), ann.state.session.readyForNext)

        val bob = decodeJoin(readyForNextRound(encode(ann.state), TOKEN_B, LATER + 1))
        assertNull(bob.error)
        val dealt = bob.state
        assertEquals(RoomPhase.PLAYING, dealt.phase, "the last agreement is the deal")
        assertEquals(GamePhase.SETUP, assertNotNull(dealt.game).phase)
        assertTrue(dealt.session.readyForNext.isEmpty(), "the agreement is spent with the deal")
        assertEquals(1, dealt.session.rounds.size, "the filed round is kept")
        assertEquals(buzzer, dealt.session.endsAtEpochMs, "the clock is the session's, not the round's")

        // The round on record starts here: its actions begin where the log stands now, and
        // the previous round's end is no longer the current round's.
        assertEquals(between.log.size, dealt.roundStartLogIndex)
        assertEquals(LATER + 1, dealt.roundStartedAtEpochMs)
        assertNull(dealt.roundFinal)

        // The same people in the same seats — the room's idea of who is human survives the deal.
        assertEquals(between.seats.map { it.tokenHash }, dealt.seats.map { it.tokenHash })
        val players = dealt.game.players
        assertTrue(players[0].isHuman && players[1].isHuman && players[2].isBot && players[3].isBot)
    }

    @Test
    fun agreeingTwiceCountsOnce() {
        val between = betweenRounds(dealtRoom())

        val once = decodeJoin(readyForNextRound(encode(between), TOKEN_A, LATER))
        val twice = decodeJoin(readyForNextRound(encode(once.state), TOKEN_A, LATER + 1))

        assertNull(twice.error)
        assertEquals(RoomPhase.BETWEEN_ROUNDS, twice.state.phase, "one impatient person dealt the round")
        assertEquals(listOf(0), twice.state.session.readyForNext)
    }

    /**
     * The second round is a different deal.
     *
     * `seedForRound` walks the session seed forward one step per round, so the whole session
     * is reproducible from one number — and so nobody sees the same five cards twice.
     */
    @Test
    fun theNextRoundIsAFreshShuffle() {
        val between = betweenRounds(dealtRoom())
        val agreed = decodeJoin(readyForNextRound(encode(between), TOKEN_A, LATER)).state
        val dealt = decodeJoin(readyForNextRound(encode(agreed), TOKEN_B, LATER)).state

        assertNotEquals(between.roundSeed, dealt.roundSeed, "the same seed deals the same hands")
        val before = assertNotNull(between.roundInitial).players.map { it.cards }
        val after = assertNotNull(dealt.roundInitial).players.map { it.cards }
        assertNotEquals(before, after, "round two was dealt from round one's shuffle")

        // And reproducibly: the same session seed, agreed to the same way, deals the same round.
        val again = decodeJoin(readyForNextRound(encode(agreed), TOKEN_B, LATER)).state
        assertEquals(dealt.roundSeed, again.roundSeed)
        assertEquals(after, assertNotNull(again.roundInitial).players.map { it.cards })
    }

    /**
     * A seat whose phone is in a pocket is not a seat the table waits on.
     *
     * Presence is what decides who has to agree. Without it a player who walked away would
     * hold the other one on the scoreboard until the room's own clocks ended the session.
     */
    @Test
    fun onlyTheHumansStillConnectedHaveToAgree() {
        val between = betweenRounds(dealtRoom()).copy(connectedSeats = listOf(0))

        val alone = decodeJoin(readyForNextRound(encode(between), TOKEN_A, LATER))

        assertNull(alone.error)
        assertEquals(RoomPhase.PLAYING, alone.state.phase, "the only person present agreed, and was waited on")
    }

    /**
     * And when presence has never been recorded at all, every seated human counts.
     *
     * The failure this rules out is counting an empty set: one tap would deal the round,
     * which is the opposite of what "everyone agrees" means.
     */
    @Test
    fun whenNobodysPresenceIsKnownEverySeatedHumanMustAgree() {
        val between = betweenRounds(dealtRoom()).copy(connectedSeats = emptyList())

        val one = decodeJoin(readyForNextRound(encode(between), TOKEN_A, LATER))
        assertEquals(RoomPhase.BETWEEN_ROUNDS, one.state.phase, "an empty presence list counted as nobody")

        val both = decodeJoin(readyForNextRound(encode(one.state), TOKEN_B, LATER))
        assertEquals(RoomPhase.PLAYING, both.state.phase)
    }

    @Test
    fun thereIsNothingToAgreeToOutsideTheBoundary() {
        val playing = dealtRoom()
        assertEquals(
            "there is no round to agree to",
            decodeJoin(readyForNextRound(playing, TOKEN_A, LATER)).error,
            "a round in play was agreed to early",
        )
        assertEquals(
            "there is no round to agree to",
            decodeJoin(readyForNextRound(lobbyOfTwo(), TOKEN_A, LATER)).error,
            "a lobby has no round behind it",
        )

        val between = encode(betweenRounds(playing))
        val stranger = decodeJoin(readyForNextRound(between, STRANGER, LATER))
        assertEquals("no seat holds that token", stranger.error)
        assertEquals(-1, stranger.seat)
        assertTrue(decodeRoom(between).session.readyForNext.isEmpty(), "a stranger's agreement was counted")
    }

    /**
     * The envelope form: every seat is told where the table stands, as its own view.
     *
     * A first agreement leaves the room between rounds, so each seat gets `between-rounds`
     * with the standings; the last one deals, so each gets `started` with the new hand it may
     * see and — because this deal followed an agreement — the standings again, which the
     * countdown's own `started` does not carry.
     */
    @Test
    fun theEnvelopesTellEverySeatWhereTheTableStands() {
        val between = betweenRounds(dealtRoom())

        val first = decodeEnvelopes(readyEnvelopes(encode(between), TOKEN_A, LATER))
        assertNull(first.error)
        assertEquals(setOf(0, 1, 2, 3), first.messages.keys, "one message per seated seat")
        first.messages.forEach { (seatIndex, text) ->
            val message = assertIs<ServerMessage.BetweenRounds>(decodeServer(text))
            assertEquals(between.session.rounds, message.standings)
            val view = assertNotNull(message.view, "seat $seatIndex is shown the scored table")
            assertEquals(first.state.seats[seatIndex].playerId, view.viewerId)
            assertEquals(GamePhase.SCORING, view.phase)
        }

        val second = decodeEnvelopes(readyEnvelopes(encode(first.state), TOKEN_B, LATER))
        assertNull(second.error)
        assertEquals(RoomPhase.PLAYING, second.state.phase)
        second.messages.forEach { (seatIndex, text) ->
            val message = assertIs<ServerMessage.Started>(decodeServer(text))
            assertEquals(between.session.rounds, message.standings, "a deal after an agreement reports the rounds")
            assertEquals(second.state.nextIndex, message.nextIndex, "the cursor starts where the log stands")
            val view = assertNotNull(message.view)
            assertEquals(second.state.seats[seatIndex].playerId, view.viewerId, "each seat is dealt its own view")
            assertEquals(GamePhase.SETUP, view.phase)
        }

        val refused = decodeEnvelopes(readyEnvelopes(encode(between), STRANGER, LATER))
        assertNotNull(refused.error)
        assertTrue(refused.messages.isEmpty(), "a refusal goes to the sender alone; the socket layer owns that send")
    }

    /** Cumulative points, which is what the final ranking is made of. */
    @Test
    fun theStandingsAreTheSumOfEveryRoundsPoints() {
        val session = SessionState(
            rounds = listOf(
                RoundResult(
                    roundNumber = 1,
                    vintoCallerId = "p1",
                    scores = mapOf("p1" to 4, "p2" to 9, "p3" to 12),
                    points = mapOf("p1" to 3, "p2" to -1, "p3" to -1),
                ),
                RoundResult(
                    roundNumber = 2,
                    vintoCallerId = "p2",
                    scores = mapOf("p1" to 2, "p2" to 7, "p3" to 5),
                    points = mapOf("p1" to 3, "p2" to -1, "p3" to 3),
                ),
            ),
        )

        assertEquals(mapOf("p1" to 6, "p2" to -2, "p3" to 2), session.standings)
        assertEquals(emptyMap(), SessionState().standings, "no rounds, no points")
    }

    private fun decodeServer(text: String): ServerMessage =
        ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
}
