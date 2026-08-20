package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import game.vinto.client.Pace
import game.vinto.client.loadSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Everything reachable from the home screen, reached.
 *
 * `TableUiTest` proves a game can be played; this proves the ways *into* one exist and lead
 * somewhere. The failure it is looking for is the cheapest and most embarrassing kind — a
 * button on the first screen of the app that does nothing, which no amount of testing the
 * game itself would ever notice.
 */
@OptIn(ExperimentalTestApi::class)
class MenuUiTest {

    @Test
    fun theHomeScreenOffersAWayIntoEachOfTheFourThings() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = MemoryVault()) } }
        waitForIdle()

        onNodeWithText("Play").assertIsDisplayed()
        onNodeWithText("Play online").assertIsDisplayed()
        onNodeWithText("How to play").assertIsDisplayed()
        onNodeWithText("Settings").assertIsDisplayed()
    }

    /**
     * The online button is not a stub that does nothing: it says what is missing, which is a
     * client rather than a server.
     */
    @Test
    fun onlinePlaySaysWhatIsMissingRatherThanNothing() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = MemoryVault()) } }
        waitForIdle()

        onNodeWithText("Play online").performClick()
        waitForIdle()

        onNodeWithText("Not in this build").assertIsDisplayed()
        onNodeWithText("Fair enough").performClick()
        waitForIdle()

        onNodeWithText("Play").assertIsDisplayed()
    }

    /** A setting changed is a setting written down, or it is not a setting. */
    @Test
    fun aSettingSurvivesLeavingTheScreen() = runComposeUiTest {
        val vault = MemoryVault()
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = vault) } }
        waitForIdle()

        onNodeWithText("Settings").performClick()
        waitForIdle()
        onNodeWithText("Calm").performClick()
        waitForIdle()

        assertEquals(Pace.CALM, vault.loadSettings().pace, "the choice reached the vault")

        onNodeWithText("Back").performClick()
        waitForIdle()
        onNodeWithText("Play").assertIsDisplayed()
    }

    /**
     * The lesson is a real table with a coach over it, so what proves it opened is that both
     * are on the screen at once.
     */
    @Test
    fun howToPlayOpensATableWithALessonOnIt() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = MemoryVault()) } }
        waitForIdle()

        onNodeWithText("How to play").performClick()
        waitForIdle()

        // The table's own prompt...
        onNodeWithText("Look at two of your cards").assertIsDisplayed()
        // ...and the coach explaining why it is asking.
        onNodeWithText("Learning the game").assertIsDisplayed()
    }

    private companion object {
        const val FIXED_SEED = 20260819L
    }
}
