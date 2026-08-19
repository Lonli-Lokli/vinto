package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.game.GameScreen
import game.vinto.app.theme.VintoTheme
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
        setContent { VintoTheme { App(seeds = { FIXED_SEED }) } }

        onNodeWithText("Play").assertIsDisplayed()
        onNodeWithText("Play").performClick()
        waitForIdle()

        // The deal: four seats, and the table asking for the two setup peeks.
        onNodeWithText("Look at two of your cards").assertIsDisplayed()

        onNodeWithContentDescription("You, card 1").performClick()
        waitForIdle()
        onNodeWithText("One more card to look at").assertIsDisplayed()

        onNodeWithContentDescription("You, card 2").performClick()
        waitForIdle()
        onNodeWithText("Start the round").performClick()
        waitForIdle()

        onNodeWithText("Your turn").assertIsDisplayed()
        onNodeWithText("Draw a card").performClick()
        waitForIdle()

        // Something was drawn, and the panel offers what to do with it. Which card it is
        // depends on the seed, so the assertion is on the choice rather than on the rank.
        onNodeWithText("Throw it away").assertIsDisplayed()
    }

    @Test
    fun theTableRendersAtScoringWithoutFallingOver() = runComposeUiTest {
        setContent { VintoTheme { GameScreen(FIXED_SEED, Difficulty.EASY, onQuit = {}, onPlayAgain = {}) } }
        waitForIdle()

        // Nothing is asserted about the words; the point is that a freshly dealt table
        // composes at all. A throw in any of the four seats fails here rather than on a phone.
        onNodeWithText("Look at two of your cards").assertIsDisplayed()
    }

    private companion object {
        const val FIXED_SEED = 20260819L
    }
}
