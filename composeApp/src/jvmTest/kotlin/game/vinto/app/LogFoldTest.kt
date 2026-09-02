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

    private val don = Speaker.Named("Don")
    private val raph = Speaker.Named("Raph")

    @Test
    fun theLogKeepsThisTurnAndTheOneBefore() {
        val round = listOf(
            Say.RoundBegins,
            Say.DrewKnown(don, Rank.SEVEN),
            Say.Swapped(don, slot = 3, dropped = Rank.SEVEN),
            Say.DrewKnown(Speaker.You, Rank.EIGHT),
            Say.Played(Speaker.You, Rank.EIGHT),
            Say.TossedIn(raph, Rank.EIGHT),
            Say.DrewKnown(raph, Rank.JOKER),
            Say.Swapped(raph, slot = 5, dropped = Rank.JACK),
        )
        assertEquals(round.drop(3), lastTurns(round))
    }

    @Test
    fun theDealStartsTheCountOver() {
        val round = listOf(Say.DrewKnown(don, Rank.TWO), Say.RoundBegins, Say.DrewKnown(raph, Rank.THREE))
        assertEquals(round.drop(1), lastTurns(round))
    }

    @Test
    fun oneActorsRunFoldsOntoOneLineAndTheTablesOwnLineStandsAlone() {
        val lines = listOf(
            Speaker.Nobody to "the round begins",
            don to "Don drew the 7",
            don to "Don swaps card 3, dropping the 7",
            Speaker.You to "You drew the 8",
            raph to "Raph tossed in the 8",
            raph to "Raph drew the Joker",
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
