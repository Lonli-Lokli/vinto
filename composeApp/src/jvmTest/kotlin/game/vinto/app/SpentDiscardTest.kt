package game.vinto.app

import game.vinto.app.game.liesFaceDown
import game.vinto.app.game.spent
import game.vinto.client.teachingSession
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A card whose action has been used lies face down on the discard pile.
 *
 * Reported from a phone: Don took the 8 off the pile and played it, it went back on top face
 * up, and to the next player it looked exactly like a card they could take — the gold ring
 * that marks a live one is too quiet to carry that difference on its own. Turned over, the
 * pile says "nothing here for you" in the one way a card can. What stays face up is anything
 * that still means something to the table: a live action card, which the next player may
 * take, and a plain card, which never had an action to spend.
 */
class SpentDiscardTest {

    @Test
    fun aPlayedActionCardIsTurnedOver() {
        val eight = Card(id = "8_0", rank = Rank.EIGHT, value = 8, actionText = "peek", played = true)
        assertTrue(eight.spent(), "an 8 somebody has played is no use to anybody")
    }

    @Test
    fun anUnplayedActionCardStaysFaceUp() {
        val eight = Card(id = "8_0", rank = Rank.EIGHT, value = 8, actionText = "peek", played = false)
        assertFalse(eight.spent(), "the next player may take it, so it has to be readable")
    }

    /**
     * Not while the window it opened is still the viewer's to answer: "the 8 went down —
     * toss in a match?" over a card back read as an empty pile. It turns over when they
     * press Continue, which is the moment it is spent for them.
     */
    @Test
    fun aPlayedCardStaysFaceUpWhileItsWindowIsStillYours() {
        val eight = Card(id = "8_0", rank = Rank.EIGHT, value = 8, actionText = "peek", played = true)
        val view = teachingSession().view.value
        val window = ActiveTossIn(
            ranks = listOf(Rank.EIGHT),
            initiatorId = view.viewerId,
            originalPlayerIndex = 0,
            participants = emptyList(),
            queuedActions = emptyList(),
            waitingForInput = true,
            playersReadyForNextTurn = emptyList(),
        )

        assertFalse(eight.liesFaceDown(view.copy(activeTossIn = window)), "still yours to throw into")
        assertTrue(
            eight.liesFaceDown(view.copy(activeTossIn = window.copy(playersReadyForNextTurn = listOf(view.viewerId)))),
            "answered, so spent for you",
        )
        assertTrue(
            eight.liesFaceDown(view.copy(activeTossIn = window.copy(ranks = listOf(Rank.KING)))),
            "a window for another rank says nothing about this card",
        )
        assertTrue(eight.liesFaceDown(view.copy(activeTossIn = null)), "and no window at all")
    }

    @Test
    fun aPlainCardStaysFaceUpWhateverItsFlagSays() {
        val four = Card(id = "4_0", rank = Rank.FOUR, value = 4, actionText = null, played = true)
        assertFalse(four.spent(), "a 4 was never anything to take, so nothing is being hidden")
    }
}
