package game.vinto.app

import game.vinto.app.game.foldedByActor
import game.vinto.app.game.lastTurns
import game.vinto.client.Say
import game.vinto.client.Speaker
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the box of recent moves keeps, and how it reads.
 *
 * It keeps the current turn and the one before — what was just played, and what the player
 * was answering — and folds one actor's run of moves onto one line, so a turn reads as one
 * thing that happened. The deal's own line stands alone and starts the count over.
 */
class LogFoldTest {

    private val dune = Speaker.Named("Dune")
    private val ember = Speaker.Named("Ember")

    @Test
    fun theLogKeepsThisTurnAndTheOneBefore() {
        val round = listOf(
            Say.RoundBegins,
            Say.DrewKnown(dune, Rank.SEVEN),
            Say.Swapped(dune, slot = 3, dropped = Rank.SEVEN),
            Say.DrewKnown(Speaker.You, Rank.EIGHT),
            Say.Played(Speaker.You, Rank.EIGHT),
            Say.TossedIn(ember, Rank.EIGHT),
            Say.DrewKnown(ember, Rank.JOKER),
            Say.Swapped(ember, slot = 5, dropped = Rank.JACK),
        )
        assertEquals(round.drop(3), lastTurns(round))
    }

    @Test
    fun theDealStartsTheCountOver() {
        val round = listOf(Say.DrewKnown(dune, Rank.TWO), Say.RoundBegins, Say.DrewKnown(ember, Rank.THREE))
        assertEquals(round.drop(1), lastTurns(round))
    }

    @Test
    fun oneActorsRunFoldsOntoOneLineAndTheTablesOwnLineStandsAlone() {
        val lines = listOf(
            Speaker.Nobody to "the round begins",
            dune to "Don drew the 7",
            dune to "Don swaps card 3, dropping the 7",
            Speaker.You to "You drew the 8",
            ember to "Raph tossed in the 8",
            ember to "Raph drew the Joker",
        )
        assertEquals(
            listOf(
                "the round begins",
                "Don drew the 7 \u279c Don swaps card 3, dropping the 7",
                "You drew the 8",
                "Raph tossed in the 8 \u279c Raph drew the Joker",
            ),
            foldedByActor(lines),
        )
    }

    @Test
    fun twoOfTheTablesOwnLinesDoNotFold() {
        val lines = listOf(Speaker.Nobody to "the round begins", Speaker.Nobody to "the round begins")
        assertEquals(listOf("the round begins", "the round begins"), foldedByActor(lines))
    }
}
