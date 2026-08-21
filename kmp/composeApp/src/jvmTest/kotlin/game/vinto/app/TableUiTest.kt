package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.game.GameScreen
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LocalGame
import game.vinto.client.MemoryVault
import game.vinto.client.Pace
import game.vinto.shapes.Difficulty
import kotlin.test.Test

/**
 * The screen itself, rendered.
 *
 * `TableModelTest` proves the game offers the right moves; this proves a player can reach
 * them. The two failures it catches are the ones no amount of pure-function testing can see:
 * a composition that throws, and a control the table decided on that never makes it onto the
 * screen. Both look identical from the outside — a game that does not respond.
 *
 * Headless, on the JVM, so it runs on any machine rather than on whichever one has an
 * emulator warmed up.
 */
@OptIn(ExperimentalTestApi::class)
class TableUiTest {

    @Test
    fun aGameCanBePlayedFromTheHomeScreenToTheFirstDraw() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = MemoryVault()) } }
        waitForIdle()

        button("Play").assertIsDisplayed()
        button("Play").performClick()
        waitForIdle()

        // The deal: four seats, and the table asking for the two setup peeks.
        onNodeWithText("Look at two of your cards").assertIsDisplayed()

        onNodeWithContentDescription("You, card 1").performClick()
        waitForIdle()
        onNodeWithText("One more card to look at").assertIsDisplayed()

        onNodeWithContentDescription("You, card 2").performClick()
        waitForIdle()
        button("Start the round").performClick()
        waitForIdle()

        onNodeWithText("Your turn").assertIsDisplayed()
        button("Draw a card").performClick()
        waitForIdle()

        // Something was drawn, and the panel offers what to do with it. Which card it is
        // depends on the seed, so the assertion is on the choice rather than on the rank.
        button("Throw it away").assertIsDisplayed()
    }

    @Test
    fun aFreshlyDealtTableComposes() = runComposeUiTest {
        val game = LocalGame.start(MemoryVault(), FIXED_SEED, Difficulty.EASY)
        setContent { VintoTheme { GameScreen(game, pace = Pace.STEADY, onQuit = {}) } }
        waitForIdle()

        // Nothing is asserted about the words; the point is that a freshly dealt table
        // composes at all. A throw in any of the four seats fails here rather than on a phone.
        onNodeWithText("Look at two of your cards").assertIsDisplayed()
    }

    /**
     * The reason persistence exists: an app reopened offers to continue, and continuing lands
     * you in the round you left rather than a new deal.
     */
    @Test
    fun aSavedGameIsOfferedAndResumed() = runComposeUiTest {
        val vault = MemoryVault()
        LocalGame.start(vault, FIXED_SEED, Difficulty.EASY)

        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = vault) } }
        waitForIdle()

        button("Continue").assertIsDisplayed()
        button("Continue").performClick()
        waitForIdle()

        onNodeWithText("Look at two of your cards").assertIsDisplayed()
    }

    /** With nothing saved, there is nothing to continue and the button is not there. */
    @Test
    fun aFirstRunOffersOnlyToPlay() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = MemoryVault()) } }
        waitForIdle()

        button("Play").assertIsDisplayed()
        onAllNodesWithText("Continue").assertCountEquals(0)
    }

    private companion object {
        const val FIXED_SEED = 20260819L
    }

    /**
     * A button, by the words it was given.
     *
     * The face of a button is stamped in caps — `GameButton` uppercases it — while the name
     * it answers to stays as written, for screen readers and for these cases.
     */
    private fun ComposeUiTest.button(label: String) = onNodeWithContentDescription(label)
}
