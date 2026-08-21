package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test

/**
 * The three controls in the header, each of which answers something.
 *
 * They are the smallest things on the screen and the easiest to leave inert — a "?" that
 * opens nothing, a number that explains nothing, an icon that copies something and says so.
 * Clicking each one here is the cheapest way to know they still lead somewhere.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderControlsTest {

    @Test
    fun theDeckCountExplainsItself() = onATable {
        onNodeWithContentDescription(DECK, substring = true).performClick()
        waitForIdle()
        onNodeWithText("The deck").assertIsDisplayed()
    }

    @Test
    fun theBugIconOffersToSendTheGame() = onATable {
        onNodeWithContentDescription(REPORT).performClick()
        waitForIdle()
        onNodeWithText("Report a problem", substring = true).assertIsDisplayed()
    }

    @Test
    fun theQuestionMarkOpensTheRules() = onATable {
        onNodeWithText("?").performClick()
        waitForIdle()
        onNodeWithText("The cards", substring = true).assertIsDisplayed()
    }

    /** A game, played far enough that the table is on screen. */
    private fun onATable(check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
            waitForIdle()
            onNodeWithContentDescription("Play").performClick()
            waitForIdle()
            check()
        }

    private companion object {
        const val SEED = 20260819L
        const val DECK = "cards left in the deck"
        const val REPORT = "Report a problem"
    }
}
