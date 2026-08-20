package game.vinto.client

import game.vinto.engine.createDeck
import game.vinto.engine.projectView
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The lesson's round, played.
 *
 * A tutorial is the most fragile thing in a game: it depends on cards turning up, on bots
 * behaving, and on a script that nobody re-reads once it works. These cases are its drift
 * alarm — a bot change, an engine fix or a mis-edited deck breaks one of them long before it
 * breaks somebody's first five minutes with the game.
 */
class TeachingRoundTest {

    @Test
    fun theTeachingDeckIsARealDeck() {
        val deck = TeachingDeal.deck()
        val real = createDeck()

        assertEquals(real.size, deck.size, "a stacked deck is still 54 cards")
        assertEquals(
            real.map { it.id }.sorted(),
            deck.map { it.id }.sorted(),
            "a stacked deck that is not a permutation of the real one is a silent rules change",
        )
        assertEquals(
            real.groupingBy { it.rank }.eachCount(),
            deck.groupingBy { it.rank }.eachCount(),
            "four of every rank, two Jokers",
        )
    }

    /** The lesson leans on these: something to peek at, and a plain card to draw first. */
    @Test
    fun theDealPutsTheTeachingCardsWhereTheLessonLooksForThem() = runTest {
        val session = teachingSession()
        val you = session.state.players.first { it.isHuman }

        assertTrue(Rank.SEVEN in you.cards.map { it.rank }, "a peek-own card to find and use")
        assertTrue(Rank.JOKER in you.cards.map { it.rank }, "the card worth minus one, to meet")
        assertEquals(Rank.FOUR, session.state.drawPile.peekTop()?.rank, "a plain card to draw")
    }

    /**
     * The ending is the half of the game a free-play tutorial never reaches, so it is the half
     * most worth a test: somebody calls Vinto, the coalition forms, and the round scores.
     */
    @Test
    fun aBotCallsVintoAndTheRoundIsScored() = runTest {
        val session = teachingSession()
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        // The simplest player there is: take a card, throw it away, pass every window.
        repeat(TURNS) {
            if (session.isOver) return@repeat
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
        }

        val caller = session.state.vintoCallerId
        assertNotNull(caller, "the lesson's director must have somebody call Vinto")
        assertTrue(caller != me, "and it must be one of the bots, not the player being taught")
        assertEquals(GamePhase.SCORING, session.state.phase, "the round has to finish")
    }

    /**
     * The coalition is the rule that is hardest to explain and easiest to get wrong, so the
     * lesson only claims it if the state actually shows it.
     */
    @Test
    fun theCoalitionFormsAgainstTheCaller() = runTest {
        val session = teachingSession()
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        repeat(TURNS) {
            if (session.isOver) return@repeat
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
        }

        val caller = assertNotNull(session.state.vintoCallerId)
        val view = projectView(session.state, me)
        assertEquals(caller, view.vintoCallerId, "the player can see who called")

        val everyoneElse = session.state.players.filter { it.id != caller }
        assertTrue(
            everyoneElse.all { it.coalitionWith.isNotEmpty() },
            "everybody who did not call plays as one",
        )
    }

    /** Enough turns for the round to reach its end however the player dawdles. */
    private companion object {
        const val TURNS = 12
    }
}
