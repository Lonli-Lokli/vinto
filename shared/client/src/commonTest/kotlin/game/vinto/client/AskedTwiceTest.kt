package game.vinto.client

import game.vinto.engine.tossInIsOpen
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The toss-in window asked twice, and the seat that has already answered it.
 *
 * Reported from a phone: *"if I press continue to mark I have nothing to toss in, and somebody
 * tossin card, I again see continue button I need to press, why? Rank list did not change and I
 * already responded that no tossin from me."*
 *
 * The engine really does reopen the window — every card thrown into it is played, and each one
 * landing puts the sub-phase back and clears the ready list. That is not the thing to change:
 * `playersReadyForNextTurn` is inside the canonical state hash, and leaving a seat marked ready
 * diverges **48 of the 50** parity recordings. So [tossInAsk] is what the screen compares, and
 * `GameHolder` answers a question it has already answered rather than putting the button up.
 */
class AskedTwiceTest {

    private fun table(ranks: List<Rank>, owner: Int, ready: List<String> = emptyList()) =
        teachingSession().view.value.let { view ->
            view.copy(
                subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
                activeTossIn = ActiveTossIn(
                    ranks = ranks,
                    initiatorId = view.players[owner].id,
                    originalPlayerIndex = owner,
                    participants = emptyList(),
                    queuedActions = emptyList(),
                    waitingForInput = true,
                    playersReadyForNextTurn = ready,
                ),
            )
        }

    /** A seat other than the viewer's, so the window is one the viewer is only answering. */
    private fun someoneElsesWindow(ranks: List<Rank> = listOf(Rank.NINE)) =
        teachingSession().view.value.let { whole ->
            table(ranks, owner = whole.players.indexOfFirst { it.id != whole.viewerId })
        }

    @Test
    fun theSameWindowAskedAgainIsTheSameQuestion() {
        val first = assertNotNull(tossInAsk(someoneElsesWindow()), "the window asked nothing")

        // What the engine does between the two asks: a nine is thrown in, played, and the
        // window comes back on the same rank with the ready list wiped. Nothing about this
        // seat has moved.
        val again = assertNotNull(tossInAsk(someoneElsesWindow()), "the reopened window asked nothing")

        assertEquals(first, again, "the same window read as a new question")
    }

    @Test
    fun aWiderWindowIsANewQuestion() {
        // A thrown King declares a rank, and `addTossInCard` widens the window to both. That
        // is a rank this seat has never answered about.
        val asked = assertNotNull(tossInAsk(someoneElsesWindow()))
        val widened = assertNotNull(tossInAsk(someoneElsesWindow(listOf(Rank.KING, Rank.NINE))))

        assertEquals(false, asked == widened, "a widened window read as the same question")
    }

    @Test
    fun aSeatThatHasAnsweredIsNotAsked() {
        val whole = teachingSession().view.value
        val owner = whole.players.indexOfFirst { it.id != whole.viewerId }
        val view = table(listOf(Rank.NINE), owner, ready = listOf(whole.viewerId))

        assertNull(tossInAsk(view), "a seat already marked ready was asked again")
    }

    /**
     * And never in the window this seat owns, where Continue is also the last chance to call
     * Vinto before the turn passes. One extra press is cheaper than a call spent in silence.
     */
    @Test
    fun yourOwnWindowKeepsAsking() {
        val whole = teachingSession().view.value
        val mine = table(listOf(Rank.NINE), owner = whole.players.indexOfFirst { it.id == whole.viewerId })

        assertEquals(true, mine.tossInIsOpen, "the fixture's window is not open")
        assertNull(tossInAsk(mine), "the window this seat owns was answered for them")
    }
}
