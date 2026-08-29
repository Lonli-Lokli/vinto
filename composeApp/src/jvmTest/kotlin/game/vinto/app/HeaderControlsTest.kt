package game.vinto.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test
import kotlin.test.assertEquals

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
        onNodeWithContentDescription(BADGE, substring = true).performClick()
        waitForIdle()
        onNodeWithText("The deck").assertIsDisplayed()
    }

    /**
     * And only the pile says how many cards are left.
     *
     * The badge and the draw pile both used to carry "N cards left in the deck", so a screen
     * reader met the same sentence twice on one screen — and the first case above could not
     * even click the badge, because two nodes answered to the name. The two are different
     * things: the pile is what is being counted, the badge is what explains the count.
     */
    @Test
    fun theCountIsSpokenInOnePlace() = onATable {
        val said = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
            .filter { it.contains(DECK) }

        assertEquals(
            1,
            said.size,
            "the deck's count is read out more than once on one screen: $said",
        )
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
        /** The felt's draw pile, which is the thing being counted. */
        const val DECK = "cards left in the deck"

        /** The header control, which is the thing that explains the count. */
        const val BADGE = "in the deck — what that means"
        const val REPORT = "Report a problem"
    }
}
