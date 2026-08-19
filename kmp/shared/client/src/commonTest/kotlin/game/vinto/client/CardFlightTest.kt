package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SwapCardPayload
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What visibly moves, and where from.
 *
 * Worth testing rather than eyeballing: a flight is three hundred milliseconds long, which is
 * too short to catch on a screenshot and long enough for a player to notice it going the wrong
 * way. Every case here was checked by watching it as well, but only these will notice when it
 * breaks.
 */
class CardFlightTest {

    private fun TestScope.flightsOf(session: LocalGameSession): List<List<CardFlight>> {
        val seen = mutableListOf<List<CardFlight>>()
        backgroundScope.launch { session.flights.collect { seen.add(it) } }
        runCurrent()
        return seen
    }

    private suspend fun started(seed: Long = 8L): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        return session
    }

    @Test
    fun drawingBringsACardFromTheDeckFaceUp() = runTest {
        val session = started()
        val flights = flightsOf(session)

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        runCurrent()

        val drawn = flights.last().first()
        assertEquals(Anchor.Deck, drawn.from)
        assertEquals(Anchor.Pending, drawn.to)
        assertEquals(Rank.NINE, drawn.card?.rank, "your own draw flies face-up")
    }

    @Test
    fun throwingACardAwaySendsItToTheDiscard() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val flights = flightsOf(session)
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        runCurrent()

        val thrown = flights.last().first()
        assertEquals(Anchor.Pending, thrown.from)
        assertEquals(Anchor.Discard, thrown.to)
        assertEquals(Rank.FIVE, thrown.card?.rank)
    }

    /**
     * A swap is two cards crossing, and the order is the point: the new one goes into the
     * hand, the old one comes out onto the pile.
     */
    @Test
    fun aSwapMovesTwoCardsInTheOrderTheyMove() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val flights = flightsOf(session)
        session.dispatch(GameAction.SwapCard(SwapCardPayload(session.playerId, 2)))
        runCurrent()

        val moved = flights.last()
        val seat = Anchor.Seat(session.playerId, 2)
        assertEquals(Anchor.Pending, moved[0].from)
        assertEquals(seat, moved[0].to, "the drawn card goes into slot 3")
        assertEquals(Rank.FIVE, moved[0].card?.rank)

        assertEquals(seat, moved[1].from, "and the card it replaced comes out")
        assertEquals(Anchor.Discard, moved[1].to)
    }

    /** A card somebody else drew is not yours to see, in the air any more than on the table. */
    @Test
    fun aCardDrawnByABotFliesFaceDown() = runTest {
        val session = started()
        val flights = flightsOf(session)

        // Ending your own turn hands play to the bots, whose draws come back in the same batch.
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId)))
        runCurrent()

        val botDraws = flights.flatten().filter { it.from == Anchor.Deck && it.to == Anchor.Pending }
        assertTrue(botDraws.size > 1, "the bots drew too: ${botDraws.size}")
        assertNull(botDraws.last().card, "and what they drew is not shown")
    }

    /** Aiming an action moves nothing, and should not pretend to. */
    @Test
    fun aimingAnActionFliesNothing() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.SEVEN)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val flights = flightsOf(session)
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))
        runCurrent()

        assertTrue(flights.last().isEmpty(), "playing a 7 aims it, it does not move it")
    }
}
