package game.vinto.app

import game.vinto.app.game.pileFace
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the discard pile shows, in the four positions it can be in.
 *
 * This is one line of UI logic with a bug in it that only appeared while something was
 * moving: the pile hid its top card whenever a card was in the air towards it — so that the
 * overlay could draw the card without it appearing twice — which is right when the pile is
 * empty underneath and wrong every other time. Throwing a King onto a King emptied the pile
 * for the length of the throw and refilled it, and it read as a glitch because it was one.
 */
class PileFaceTest {

    private val king = card(Rank.KING, "k1")
    private val queen = card(Rank.QUEEN, "q1")
    private val seven = card(Rank.SEVEN, "s1")

    @Test
    fun aSettledPileShowsItsTopCard() {
        assertEquals(king, pileFace(top = king, covered = queen, inPlay = null, landing = false))
    }

    @Test
    fun anEmptyPileShowsNothing() {
        assertNull(pileFace(top = null, covered = null, inPlay = null, landing = false))
    }

    /** A card played from a hand is in play *on the pile*, so it is what the pile shows. */
    @Test
    fun aCardInPlaySitsOnTop() {
        assertEquals(seven, pileFace(top = king, covered = queen, inPlay = seven, landing = false))
    }

    /**
     * The bug: a second King thrown onto the first must not empty the pile.
     *
     * The covered card is the caller's memory of the last face it drew, not something the
     * engine sends — a client is told the top of the pile and how thick it is, and nothing
     * about what is under there.
     */
    @Test
    fun aPileWithACardInTheAirShowsWhatIsAboutToBeCovered() {
        assertEquals(
            queen,
            pileFace(top = king, covered = queen, inPlay = null, landing = true),
            "the card underneath stays visible while the next one lands on it",
        )
    }

    /** And with a hand's card being played over the pile, the pile's own top holds the place. */
    @Test
    fun aPileUnderAPlayedCardShowsItsTopWhileSomethingLands() {
        assertEquals(king, pileFace(top = king, covered = queen, inPlay = seven, landing = true))
    }

    /** Only a pile that really was empty shows nothing while a card arrives. */
    @Test
    fun theFirstCardOfARoundArrivesOnNothing() {
        assertNull(pileFace(top = king, covered = null, inPlay = null, landing = true))
    }

    private fun card(rank: Rank, id: String) =
        Card(id = id, rank = rank, value = 10, played = false)

    /**
     * The covered card is the one *before* the card in the air, not the pile's current top.
     *
     * The table steps to the new position before the cards fly, so for one frame the pile's
     * top is already the card about to be thrown at it. A pile that showed its own top while
     * that card was flying drew it twice — a Joker on the discard and a Joker crossing the
     * table at the same moment.
     */
    @Test
    fun aLandingCardIsNeverAlsoDrawnOnThePile() {
        val arriving = king
        val shown = pileFace(top = arriving, covered = queen, inPlay = null, landing = true)

        assertEquals(queen, shown, "the pile shows what the arriving card will cover")
        assertTrue(shown != arriving, "and never the card that is still in the air")
    }
}
