package game.vinto.app

import game.vinto.app.game.pileFace
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals(king, pileFace(top = king, under = queen, inPlay = null, landing = false))
    }

    @Test
    fun anEmptyPileShowsNothing() {
        assertNull(pileFace(top = null, under = null, inPlay = null, landing = false))
    }

    /** A card played from a hand is in play *on the pile*, so it is what the pile shows. */
    @Test
    fun aCardInPlaySitsOnTop() {
        assertEquals(seven, pileFace(top = king, under = queen, inPlay = seven, landing = false))
    }

    /** The bug: a second King thrown onto the first must not empty the pile. */
    @Test
    fun aPileWithACardInTheAirShowsWhatIsAboutToBeCovered() {
        assertEquals(
            queen,
            pileFace(top = king, under = queen, inPlay = null, landing = true),
            "the card underneath stays visible while the next one lands on it",
        )
    }

    /** And with a hand's card being played over the pile, the pile's own top holds the place. */
    @Test
    fun aPileUnderAPlayedCardShowsItsTopWhileSomethingLands() {
        assertEquals(king, pileFace(top = king, under = queen, inPlay = seven, landing = true))
    }

    /** Only a pile that really was empty shows nothing while a card arrives. */
    @Test
    fun theFirstCardOfARoundArrivesOnNothing() {
        assertNull(pileFace(top = king, under = null, inPlay = null, landing = true))
    }

    private fun card(rank: Rank, id: String) =
        Card(id = id, rank = rank, value = 10, played = false)
}
