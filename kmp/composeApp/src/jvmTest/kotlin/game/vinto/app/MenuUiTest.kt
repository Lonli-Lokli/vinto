package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
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

        button("Play").assertIsDisplayed()
        button("Play online").assertIsDisplayed()
        button("How to play").assertIsDisplayed()
        button("Settings").assertIsDisplayed()
    }

    /**
     * The online button opens the way into a room: a name, a code to join, a room to make.
     * The way back works without ever touching the network.
     */
    @Test
    fun onlinePlayOpensTheWayIntoARoom() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = MemoryVault()) } }
        waitForIdle()

        button("Play online").performClick()
        waitForIdle()

        button("Join the room").assertIsDisplayed()
        button("Open a new room").assertIsDisplayed()
        button("Back").performClick()
        waitForIdle()

        button("Play").assertIsDisplayed()
    }

    /** A setting changed is a setting written down, or it is not a setting. */
    @Test
    fun aSettingSurvivesLeavingTheScreen() = runComposeUiTest {
        val vault = MemoryVault()
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = vault) } }
        waitForIdle()

        button("Settings").performClick()
        waitForIdle()
        button("Calm").performClick()
        waitForIdle()

        assertEquals(Pace.CALM, vault.loadSettings().pace, "the choice reached the vault")

        button("Back").performClick()
        waitForIdle()
        button("Play").assertIsDisplayed()
    }

    /**
     * The lesson opens on the object of the game and holds the table until that has been read,
     * which is the whole difference between a tutorial and a game with captions.
     */
    @Test
    fun howToPlayOpensByExplainingTheGame() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = MemoryVault()) } }
        waitForIdle()

        button("How to play").performClick()
        waitForIdle()

        onNodeWithText("Four players, five cards each").assertIsDisplayed()
        button("Go on").assertIsDisplayed()
    }

    /**
     * And it is a real table underneath: acknowledge the opening and the game's own prompt is
     * there, on the deal the lesson was dealt.
     */
    @Test
    fun theLessonIsPlayedOnARealTable() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { FIXED_SEED }, vault = MemoryVault()) } }
        waitForIdle()

        button("How to play").performClick()
        waitForIdle()

        onNodeWithText("Look at two of your cards").assertIsDisplayed()
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
