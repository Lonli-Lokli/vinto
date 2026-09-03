package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.StandingsSheet
import game.vinto.app.theme.VintoTheme
import game.vinto.client.RoundResult
import game.vinto.client.roundPoints
import kotlin.test.Test

/**
 * The end of a round says which side won, before it says by how much.
 *
 * The sheet used to open with "Round 3" and a table of numbers, and the answer to the only
 * question anybody has — *did we win* — had to be derived from a column of `+3` and `−1`. The
 * web client ends a round on a sentence instead, and it is plainly better at the moment a
 * player looks up from a hand they have spent ten minutes on.
 *
 * What is checked here is the words, because the *decision* behind them is checked in
 * `RoundOutcomeTest` where it can be checked without a renderer. Same split as `CardHelpTest`
 * and `LessonCopyTest`: the model says which verdict, the resources say it in a language.
 */
@OptIn(ExperimentalTestApi::class)
class ScoreSheetTest {

    private val seats = listOf(
        "p1" to "You",
        "p2" to "Ember",
        "p3" to "Sky",
        "p4" to "Dune",
    )

    private fun ComposeUiTest.sheetFor(hands: Map<String, Int>, caller: String?) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    StandingsSheet(
                        open = true,
                        round = 3,
                        you = "p1",
                        result = RoundResult(
                            callerId = caller,
                            hands = hands,
                            points = roundPoints(hands, caller),
                            seats = seats,
                        ),
                        standings = emptyMap(),
                        onNextRound = {},
                        onQuit = {},
                    )
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun aCallThatHeldSaysSoAndShowsTheTwoNumbersItTurnedOn() = runComposeUiTest {
        sheetFor(mapOf("p1" to 6, "p2" to 20, "p3" to 15, "p4" to 30), caller = "p1")

        onNodeWithText("The Vinto call held").assertIsDisplayed()
        onNodeWithText("Vinto 6 against their best 15").assertIsDisplayed()
        // The row the round was decided against is named rather than merely lowest.
        onNodeWithText("best of the others").assertIsDisplayed()
        onNodeWithText("called Vinto").assertIsDisplayed()
    }

    @Test
    fun aCallThatWasBeatenSaysThatInstead() = runComposeUiTest {
        sheetFor(mapOf("p1" to 30, "p2" to 20, "p3" to 4, "p4" to 25), caller = "p1")

        onNodeWithText("The others beat the call").assertIsDisplayed()
        onNodeWithText("Vinto 30 against their best 4").assertIsDisplayed()
    }

    /** The tie is its own answer: the caller keeps the round and the others lose nothing. */
    @Test
    fun levelIsNamedRatherThanRoundedIntoAWin() = runComposeUiTest {
        sheetFor(mapOf("p1" to 12, "p2" to 12, "p3" to 15, "p4" to 30), caller = "p1")

        onNodeWithText("Level — the call held").assertIsDisplayed()
    }

    /** And a round nobody called is not a contest, so it is not reported as one. */
    @Test
    fun aRoundTheDeckEndedIsNotAVictory() = runComposeUiTest {
        sheetFor(mapOf("p1" to 12, "p2" to 20, "p3" to 15, "p4" to 30), caller = null)

        onNodeWithText("Nobody called").assertIsDisplayed()
        onNodeWithText("Every hand counted, nothing paid").assertIsDisplayed()
    }

    /** The round number survives: it is no longer the headline, and it is still needed. */
    @Test
    fun theRoundIsStillNumbered() = runComposeUiTest {
        sheetFor(mapOf("p1" to 6, "p2" to 20, "p3" to 15, "p4" to 30), caller = "p1")
        onNodeWithText("Round 3").assertIsDisplayed()
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
