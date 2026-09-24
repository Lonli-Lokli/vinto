package game.vinto.client

import game.vinto.engine.STARTING_POINTS
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An online table's game column counts from the four everybody starts on.
 *
 * The room files its standings as `STARTING_POINTS + the rounds' points` (`RoomCore`), and the
 * client summed the same rounds without the four — so the sheet read four below the room for the
 * whole of every online session. Found by watching the score sheet say "−1" for a player who,
 * under the rules, was on 3.
 */
class GameTotalsTest {

    @Test
    fun nothingFiledYetIsNothingToShow() {
        assertEquals(emptyMap(), gameTotals(emptyList()))
    }

    @Test
    fun oneRoundIsTheStartingFourPlusWhatItPaid() {
        val round = mapOf("p1" to 3, "p2" to -1, "p3" to -1, "p4" to -1)

        assertEquals(
            mapOf("p1" to 7, "p2" to 3, "p3" to 3, "p4" to 3),
            gameTotals(listOf(round)),
        )
    }

    @Test
    fun theFourIsCountedOnceNotOncePerRound() {
        val rounds = listOf(mapOf("p1" to 3, "p2" to -1), mapOf("p1" to -1, "p2" to 3))

        assertEquals(
            mapOf("p1" to STARTING_POINTS + 2, "p2" to STARTING_POINTS + 2),
            gameTotals(rounds),
        )
    }
}
