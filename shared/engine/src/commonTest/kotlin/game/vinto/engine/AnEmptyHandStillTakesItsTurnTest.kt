package game.vinto.engine

import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Rank
import game.vinto.shapes.SwapCardPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A seat can reach zero cards — throw in your last one — and the rules still give it a turn.
 *
 * Reported from a phone: *"if player has no cards he can draw and just put this card in his
 * hand"*. The engine had no answer. `ActionValidator` asks for a position inside
 * `cards.indices`, which on an empty hand is empty, so every swap was refused; and
 * `handleSwapCard` reads `player.cards[position]` before anything else, so a swap that did get
 * through would have thrown rather than refused.
 *
 * The model is **a swap into the empty place**: the drawn card goes to position 0 and nothing
 * comes out. No card lands on the pile, so there is no window to throw into and no rank to
 * guess — which is why a declaration is refused here rather than ignored. Letting the card go
 * is the other half of the turn and already worked.
 */
class AnEmptyHandStillTakesItsTurnTest {

    private fun holding(drawn: Rank) = testState(
        players = listOf(
            testPlayer("p1", "Player 1", isHuman = true, cards = emptyList()),
            testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p2-0"))),
            testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.SIX, "p3-0"))),
            testPlayer("p4", "Player 4", isHuman = false, cards = listOf(testCard(Rank.SEVEN, "p4-0"))),
        ),
        subPhase = GameSubPhase.CHOOSING,
        drawPile = pileOf(testCard(Rank.FOUR, "deck-0"), testCard(Rank.FOUR, "deck-1")),
        pendingAction = pending(
            testCard(drawn, "drawn"),
            "p1",
            from = PendingCardOrigin.DRAWING,
        ),
    )

    @Test
    fun aSeatWithNoCardsKeepsTheCardItDrew() {
        val after = unsafeReduce(
            holding(Rank.JOKER),
            GameAction.SwapCard(SwapCardPayload("p1", position = 0, declaredRank = null)),
        )

        val hand = after.players.first { it.id == "p1" }
        assertEquals(listOf(Rank.JOKER), hand.cards.map { it.rank })
        // They watched it go in, so they know it — and so does everybody else.
        assertTrue(0 in hand.knownCardPositions)

        // Nothing came out, so nothing landed: the pile is untouched, the turn has moved on,
        // and the window carries no rank anybody could have answered.
        assertEquals(0, after.discardPile.size)
        assertNull(after.pendingAction)
        assertEquals(emptyList(), after.activeTossIn?.ranks)
        assertEquals("p2", after.players[after.currentPlayerIndex].id)
    }

    @Test
    fun anEmptyHandHasNoCardToGuessAt() {
        // A declaration names the card that goes out. Nothing goes out, so there is nothing to
        // be right or wrong about, and a penalty card for it would be a punishment for nothing.
        val refused = GameEngine.reduce(
            holding(Rank.JOKER),
            GameAction.SwapCard(SwapCardPayload("p1", position = 0, declaredRank = Rank.JACK)),
        )
        assertIs<ReduceResult.Failure>(refused)
    }

    @Test
    fun aSeatWithNoCardsMayStillLetItGo() {
        val after = unsafeReduce(
            holding(Rank.JOKER),
            GameAction.DiscardCard(game.vinto.shapes.PlayerIdPayload("p1")),
        )
        assertTrue(after.players.first { it.id == "p1" }.cards.isEmpty())
        assertEquals(Rank.JOKER, after.discardPile.cards.last().rank)
    }

    @Test
    fun onlyTheEmptyPlaceIsOnOffer() {
        // One past the end of a hand that has cards is still nothing: this is the empty hand's
        // case and not a way to grow a hand by one.
        val full = testState(
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.TWO, "p1-0"))),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p2-0"))),
                testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.SIX, "p3-0"))),
                testPlayer("p4", "Player 4", isHuman = false, cards = listOf(testCard(Rank.SEVEN, "p4-0"))),
            ),
            subPhase = GameSubPhase.CHOOSING,
            drawPile = pileOf(testCard(Rank.FOUR, "deck-0")),
            pendingAction = pending(testCard(Rank.JOKER, "drawn"), "p1", from = PendingCardOrigin.DRAWING),
        )
        assertIs<ReduceResult.Failure>(
            GameEngine.reduce(full, GameAction.SwapCard(SwapCardPayload("p1", position = 1, declaredRank = null))),
        )
    }
}
