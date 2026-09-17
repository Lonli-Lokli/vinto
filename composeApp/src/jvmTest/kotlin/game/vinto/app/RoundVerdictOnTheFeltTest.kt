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
import game.vinto.app.game.RoundOver
import game.vinto.app.theme.VintoTheme
import game.vinto.client.RoundOutcome
import game.vinto.client.RoundVerdict
import kotlin.test.Test

/**
 * The felt says who won, before anybody opens anything.
 *
 * Reported 2026-09-16: *"we must clearly show and write who won and why after coalition last
 * turn (without opening scores)"*. The round ended on a chime and a button reading "See the
 * score", so the one question a player has at that moment was answered only inside a table of
 * numbers.
 *
 * The same split the score sheet's own test uses: `VerdictTest` decides *which* verdict without
 * a renderer, and this checks the words reach the screen.
 */
@OptIn(ExperimentalTestApi::class)
class RoundVerdictOnTheFeltTest {

    private fun ComposeUiTest.feltFor(verdict: RoundVerdict?, caller: String?) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(width = 411.dp, height = 740.dp)) {
                    RoundOver(verdict = verdict, caller = caller, onSee = {})
                }
            }
        }
    }

    @Test
    fun aCoalitionMemberWhoWonIsToldSoOnTheFelt() = runComposeUiTest {
        feltFor(
            RoundVerdict(
                outcome = RoundOutcome.CoalitionWon(caller = 12, best = 2),
                callerPoints = -1,
                coalitionPoints = 3,
                viewerWon = true,
            ),
            caller = "Ember",
        )

        onNodeWithText("You won this round", substring = true).assertIsDisplayed()
        // And what it paid, both sides, without the sheet: the caller is named because it was
        // not this seat, and the numbers carry their own signs.
        onNodeWithText("Ember takes -1, and everyone else takes +3 each.").assertIsDisplayed()
        onNodeWithText("See the score", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun theBeatenCallerIsToldSoInTheirOwnWords() = runComposeUiTest {
        feltFor(
            RoundVerdict(
                outcome = RoundOutcome.CoalitionWon(caller = 12, best = 2),
                callerPoints = -1,
                coalitionPoints = 3,
                viewerWon = false,
            ),
            caller = null,
        )

        onNodeWithText("You lost this round", substring = true).assertIsDisplayed()
        onNodeWithText("You take -1, and the others take +3 each.").assertIsDisplayed()
    }

    /** Nobody called, so there were no sides: a sign either way would be a lie. */
    @Test
    fun aRoundNobodyCalledClaimsNoWinner() = runComposeUiTest {
        feltFor(
            RoundVerdict(
                outcome = RoundOutcome.DeckRanOut,
                callerPoints = 0,
                coalitionPoints = 0,
                viewerWon = null,
            ),
            caller = null,
        )

        onNodeWithText("won this round", substring = true).assertDoesNotExist()
        onNodeWithText("lost this round", substring = true).assertDoesNotExist()
        onNodeWithText("See the score", ignoreCase = true).assertIsDisplayed()
    }
}
