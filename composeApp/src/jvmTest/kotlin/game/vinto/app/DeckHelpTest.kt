package game.vinto.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * The deck answers when you touch it, the way the discard does.
 *
 * How many cards are left is the one figure on the felt that decides how a round ends — when the
 * deck runs dry the discard is shuffled back in and everything anybody had learned from watching
 * that pile stops being worth anything. It was already written down, in `deck_body`, and it was
 * already spoken: the deck's own accessible name carries the count, so a screen reader has always
 * been able to hear it.
 *
 * A player looking at the screen could not. The number used to be a chip in the header, and when
 * six controls turned out to be too many for a phone (41f9aa1) the chip went and its explanation
 * moved to the help sheet — but the dialog written for it stayed behind in `GameScreen` with
 * nothing left that could open it: `deckOpen` was set to `false` in two places and to `true` in
 * none. Dead UI, and the feature it belonged to gone with the control that used to reach it.
 *
 * So this asserts the way in rather than the words, which are `HelpSheetTest`'s business: the deck
 * is something you can press, and pressing it says which deck this is.
 */
@OptIn(ExperimentalTestApi::class)
class DeckHelpTest {

    @Test
    fun pressingTheDeckSaysHowManyCardsAreLeftInIt() = runComposeUiTest {
        val game = runBlocking { LocalGame.start(MemoryVault(), SEED, Difficulty.EASY) }
        setContent {
            CompositionLocalProvider(LocalPacing provides 0f) {
                VintoTheme { GameScreen(game, pace = Pace.BRISK, onSettings = {}, onQuit = {}) }
            }
        }
        waitForIdle()

        // By the words the deck is announced with, and by a *part* of them: the count is in that
        // sentence and changes every draw, so matching the whole of it would pin this test to one
        // moment in one deal.
        onNodeWithContentDescription(DECK, substring = true).performClick()
        waitForIdle()

        onNodeWithText(TITLE).assertIsDisplayed()
    }

    private companion object {
        const val SEED = 12L

        /** The tail of `header_deck_left`, which is the deck's accessible name. */
        const val DECK = "cards left in the deck"

        /** `deck_title` — the heading the sheet opens with. */
        const val TITLE = "The deck"
    }
}
