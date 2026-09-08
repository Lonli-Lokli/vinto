package game.vinto.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The four controls in the header, each of which answers something.
 *
 * They are the smallest things on the screen and the easiest to leave inert — a "?" that
 * opens nothing, a number that explains nothing, an icon that copies something and says so.
 * Clicking each one here is the cheapest way to know they still lead somewhere.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderControlsTest {

    @Test
    fun theDeckIsExplainedInTheRules() = onATable {
        // No chip of its own any more: six controls is too many for a phone's header, and this
        // was the one of them that was a number rather than something to press. The explanation
        // went where the rest of "what does this mean" already lives.
        onNodeWithText("?").performClick()
        waitForIdle()
        // Scrolled to within the sheet's own list: it is the last section, and a LazyColumn has
        // not composed what is below the fold. `onAllNodes` because the table behind the sheet
        // has scrollables of its own and a strict matcher picks one of those.
        onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("The deck", substring = true))
        onNodeWithText("The deck", substring = true).assertIsDisplayed()
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
    fun theHeaderNoLongerCarriesAReport() = onATable {
        // It is in the settings now, under the review button — a control every player saw on
        // every turn, for the sake of the few who would ever press it, on the one screen with
        // no room to spare. `SettingsScreen` is where it is tested.
        onAllNodesWithContentDescription(REPORT).fetchSemanticsNodes().let {
            assertTrue(it.isEmpty(), "the header still has a report control")
        }
    }

    @Test
    fun theQuestionMarkOpensTheRules() = onATable {
        onNodeWithText("?").performClick()
        waitForIdle()
        onNodeWithText("The cards", substring = true).assertIsDisplayed()
    }

    /**
     * The gear, and the half of it that is easy to get wrong.
     *
     * Opening the settings from a round is worth nothing if coming back out of them starts a
     * new one — pace is the setting somebody wants to change *during* the round that is too
     * slow, and a trip that costs them the round is a trip they take once. So this goes in,
     * changes nothing, comes back, and looks for the table rather than the front door.
     */
    @Test
    fun theGearOpensTheSettingsAndComesBackToTheSameTable() = onATable {
        onNodeWithContentDescription(SETTINGS).performClick()
        waitForIdle()
        // The front page now, where sound and haptics are; pace lives behind "Game".
        onNodeWithText("Sound", substring = true).assertIsDisplayed()

        // Scrolled to first: the settings column is taller than the window, and Compose clips
        // `boundsInRoot` to what is on screen — so a click aimed at an off-screen control lands
        // on the window's corner and reports success (CI.md §1c).
        onNodeWithContentDescription("Back").performScrollTo().performClick()
        waitForIdle()

        onAllNodesWithText("DRAW", substring = true).fetchSemanticsNodes().let {
            assertTrue(it.isNotEmpty(), "coming back out of the settings did not land on a table")
        }
        onAllNodesWithText("Play").fetchSemanticsNodes().let {
            assertEquals(0, it.size, "it went home instead of back to the round")
        }
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
        const val SETTINGS = "Settings"
    }
}
