package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This is not our game, and the app has to say so where somebody will read it.
 *
 * VINTO is a card game other people designed; everything in this repository is an unofficial
 * client for it. Two things follow, and both are held here rather than trusted to a reviewer
 * noticing a line of copy going missing in a refactor:
 *
 * - **The first screen says it**, before a card is dealt and without scrolling. An attribution
 *   that lives two taps away in a settings list is one almost nobody is shown, and "we did
 *   mention it" is not the same promise as telling somebody whose game they are playing.
 * - **The address is reachable, not just printed.** The line is the way to the original, so a
 *   player who wants the real thing — the rules, the deck, the people — gets there in one tap.
 *
 * The words are asserted rather than the string id, which is the split this suite uses
 * throughout (`CardHelpTest`, `LessonCopyTest`): a resource lookup that resolves to the wrong
 * entry passes an id check and fails a person reading the screen.
 */
@OptIn(ExperimentalTestApi::class)
class AttributionTest {

    /**
     * On the phone-sized screen, unscrolled, in the same breath as the wordmark.
     *
     * The size is fixed rather than left to the harness so that "without scrolling" means
     * something: `assertIsDisplayed` on an unbounded test surface is true of everything.
     */
    @Test
    fun theFirstScreenSaysWhoseGameThisIs() = runComposeUiTest {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    App(seeds = { SEED }, vault = MemoryVault())
                }
            }
        }
        waitForIdle()

        onNode(hasText(UNOFFICIAL, substring = true, ignoreCase = true) and hasText(OFFICIAL, substring = true))
            .assertIsDisplayed()
    }

    /** And it is a way there, not a sentence about it. */
    @Test
    fun theCreditIsTheWayToTheOriginal() = runComposeUiTest {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    App(seeds = { SEED }, vault = MemoryVault())
                }
            }
        }
        waitForIdle()

        onNode(hasText(OFFICIAL, substring = true) and hasClickAction()).assertIsDisplayed()
    }

    /**
     * The settings say it again, with the address itself and beside the app's own page.
     *
     * Twice on purpose: the home screen is where somebody is told, and About is where they
     * go looking when they want to check. The first is a courtesy and the second is what a
     * person does when they suspect an app of pretending to be something it is not.
     */
    @Test
    fun theSettingsNameTheGameAndTheAppSeparately() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()
        onNodeWithContentDescription("Settings").performScrollTo().performClick()
        waitForIdle()

        listOf("The original game", "About this app").forEach {
            onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        onNodeWithText(OFFICIAL, substring = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * The address is the game's own, and it is the only one in `Pages` that is not ours.
     *
     * A pure check, and the reason `SettingsLinksTest` has an exception rather than a looser
     * rule: "every link is on a host we own" is the property worth keeping for the studio's
     * pages, and this one link exists precisely because it is somebody else's.
     */
    @Test
    fun theOfficialGameIsNamedExactly() {
        assertEquals("https://vinto.game", Pages.OFFICIAL, "the game's own address")
        assertTrue(
            Pages.THIS_APP.startsWith("https://kupalinka.app/games/"),
            "this app's page is on the studio's site: ${Pages.THIS_APP}",
        )
    }

    private companion object {
        const val SEED = 20_260_902L

        /** What the credit has to contain, in the language the app is written in. */
        const val UNOFFICIAL = "unofficial"
        const val OFFICIAL = "vinto.game"

        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
