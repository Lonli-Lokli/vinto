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
        // Rate is on the front page — it is one of the three things people actually come here
        // to press — and it is a bare button now rather than a panel, so it is matched on the
        // description `GameButton` sets rather than on a title that no longer exists.
        onNodeWithContentDescription("Rate this game").performScrollTo().assertIsDisplayed()

        door("What leaves this device")
        listOf("Privacy", "Terms of use").forEach {
            onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }

        settings()
        door("About")
        listOf("Get in touch", "The original game", "About this app", "Tell somebody").forEach {
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

        // Three doors and the three controls people actually come for, rather than eighteen
        // panels in one column. "Look and feel" is gone as a heading: its contents moved in
        // with the rest of the game's settings, and sound and haptics came to the front.
        listOf("The game", "What leaves this device", "About").forEach { group ->
            onNodeWithText(group.uppercase()).performScrollTo().assertIsDisplayed()
        }
        listOf("Sound", "Haptics").forEach {
            onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        onNodeWithContentDescription("Rate this game").performScrollTo().assertIsDisplayed()
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

        // The rulebook is on the original game's own site, so it is excluded by the same
        // argument and pinned to that host rather than merely to https — an outside link that
        // drifted onto a different domain would still pass a bare scheme check.
        assertTrue(
            Pages.RULES.startsWith(Pages.OFFICIAL + "/"),
            "the rules are not on the original game's site: ${Pages.RULES}",
        )

        // The third outside host, and the one with a policy attached: it may appear only in the
        // web and desktop builds, which are in no store. `SupportLinkTest` is what holds that;
        // here it is named so it cannot arrive by loosening the rule above.
        assertTrue(
            Pages.SUPPORT.startsWith("https://"),
            "the support page is not absolute https: ${Pages.SUPPORT}",
        )
    }

    /**
     * The two store listings, which are the other addresses here we deliberately do not own.
     *
     * Named one at a time for the same reason `Pages.OFFICIAL` is: the rule above exists so that
     * an outside host has to be argued for in a test rather than slipped past one, and a store
     * link is exactly the kind of thing somebody would otherwise add by loosening the rule.
     *
     * What is pinned is the shape, not the id — that each is absolute https on the store's real
     * host, that Apple's carries the review action rather than just opening the listing, and that
     * Play's names this app's package. **Neither listing is live yet**, and that is on purpose
     * (see `Pages`); a 404 is not something a test can catch anyway, so what it can catch is a
     * malformed address that would still 404 after the listings appear.
     */
    @Test
    fun theStoreLinksAreWellFormedEvenBeforeTheListingsExist() {
        assertTrue(
            Pages.APPLE_REVIEW.startsWith("https://apps.apple.com/app/id"),
            "the Apple review link is not an App Store address: ${Pages.APPLE_REVIEW}",
        )
        assertTrue(
            Pages.APPLE_REVIEW.endsWith("?action=write-review"),
            "the Apple link opens the listing rather than the review sheet: ${Pages.APPLE_REVIEW}",
        )
        assertTrue(
            Pages.PLAY_REVIEW == "https://play.google.com/store/apps/details?id=app.kupalinka.vinto",
            "the Play link does not name this app's package: ${Pages.PLAY_REVIEW}",
        )
    }

    private fun ComposeUiTest.settings() {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()
        press("Settings")
    }

    /** Opens one of the three doors the settings' front page offers. */
    private fun ComposeUiTest.door(name: String) {
        onNodeWithText(name.uppercase()).performScrollTo().performClick()
        waitForIdle()
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
