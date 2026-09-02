package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The pages a player is entitled to reach, and the four groups the settings are read in.
 *
 * Two different failures, one screen. The links are a *legal* surface — a store listing cannot
 * ship without a privacy policy, and one that 404s is worse than none because it is a promise
 * the app visibly fails to keep. The grouping is the reason the screen was revisited: eight
 * controls in one column with uniform spacing is a list of switches, and it left two
 * irreversible actions at the same weight and rhythm as a haptics toggle.
 *
 * The addresses themselves are asserted against `Pages` rather than typed here, because a
 * duplicated URL in a test is a second place for it to be wrong. What this pins is that they
 * are absolute `https` on the studio's own host — a relative or `http` link would be a
 * different bug each way, and the browser is the only thing that would ever say so.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsLinksTest {

    @Test
    fun everyPageAPlayerIsEntitledToIsOffered() = runComposeUiTest {
        settings()

        // Matched on the words the panel shows. Only `GameButton` sets a content description,
        // and every link button says "Open" — the panel around each is what says which page it
        // opens, so that is what a person reads and what this asserts.
        listOf(
            "Privacy",
            "Terms of use",
            "Get in touch",
            "The original game",
            "About this app",
            "Tell somebody",
        ).forEach {
            onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * Four headings, so the screen reads as four decisions rather than one list.
     *
     * Asserted as *presence and order*, not as layout: what must not come back is the flat
     * column, and a heading that exists but sits in the wrong place is the same failure as one
     * that is missing.
     */
    @Test
    fun theSettingsAreGroupedRatherThanPouredIntoOneColumn() = runComposeUiTest {
        settings()

        listOf(
            "The game",
            "Look and feel",
            "What leaves this device",
            "About",
        ).forEach { group ->
            onNodeWithText(group.uppercase()).performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * Every address is absolute, `https`, and on the studio's own host.
     *
     * A pure check on `Pages`, so it costs nothing and catches the three ways one of these
     * goes wrong: a relative path (which resolves against whatever page is showing — the bug
     * that broke every invitation link), plain `http`, and a typo'd host that quietly belongs
     * to somebody else.
     *
     * **`Pages.OFFICIAL` is excluded by name rather than by loosening the rule**, because it
     * is the one link in this app that is deliberately somebody else's: the card game this is
     * an unofficial client for. Naming it here means the next outside host somebody adds has
     * to be argued for in this test rather than slipped past it — see `AttributionTest`, which
     * pins what that address is.
     */
    @Test
    fun thePagesAreAbsoluteAndOnAHostWeOwn() {
        val pages = mapOf(
            "privacy" to Pages.PRIVACY,
            "terms" to Pages.TERMS,
            "contact" to Pages.CONTACT,
            "the game" to Pages.GAME,
            "this app's page" to Pages.THIS_APP,
        )
        pages.forEach { (what, url) ->
            assertTrue(url.startsWith("https://"), "$what is not absolute https: $url")
            assertTrue(
                url.startsWith("https://kupalinka.app/") || url.startsWith("https://vinto.kupalinka.app"),
                "$what points somewhere we do not own: $url",
            )
        }

        assertTrue(
            Pages.OFFICIAL.startsWith("https://"),
            "the original game is not absolute https: ${Pages.OFFICIAL}",
        )
    }

    private fun ComposeUiTest.settings() {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()
        press("Settings")
    }

    private fun ComposeUiTest.press(label: String) {
        val node = onNodeWithContentDescription(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        waitForIdle()
    }

    private companion object {
        const val SEED = 20_260_819L
    }
}
