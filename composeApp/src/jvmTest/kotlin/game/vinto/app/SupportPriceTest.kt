package game.vinto.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import game.vinto.client.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the thank-you button says when the store hands back no figure.
 *
 * `settings_support_buy` is "Say thanks — %1$s", so a blank price renders **"Say thanks — "** with
 * the dash hanging off the end. It looks like a string that failed to load, on the one control in
 * the app that asks somebody for money.
 *
 * It is not hypothetical. `formattedPrice` is Play's own field and it comes back **empty for a
 * product that is active but has no price in the buyer's country** — a real state, reachable by
 * getting one checkbox wrong in the console, and one that arrives as a live product rather than a
 * missing one, so `AndroidBilling.price()` has nothing to reject. Apple's formatter can only
 * answer nil, which already falls to [Support.Unavailable].
 *
 * The answer is `settings_support_link` — the label the web and desktop already use, which is the
 * same sentence without the figure. So there is no new string, and no locale to re-translate.
 */
@OptIn(ExperimentalTestApi::class)
class SupportPriceTest {

    @Test
    fun aStoreThatNamesNoPriceGetsAButtonWithNoDanglingDash() = runComposeUiTest {
        settingsOffering("")

        // Matched on the description `GameButton` sets rather than the text it draws, which is
        // the label uppercased — so this is the string a screen reader speaks as well.
        onNodeWithContentDescription("Say thanks").assertIsDisplayed()
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Say thanks —", substring = true))
                .fetchSemanticsNodes()
                .size,
            "the button kept the separator with nothing after it",
        )
    }

    /** And the ordinary case is untouched: a price the store gave is a price the button shows. */
    @Test
    fun aStoreThatNamesAPriceStillShowsIt() = runComposeUiTest {
        settingsOffering("£4.99")

        onNodeWithContentDescription("Say thanks — £4.99").assertIsDisplayed()
    }

    private fun ComposeUiTest.settingsOffering(price: String) {
        setContent {
            VintoTheme {
                CompositionLocalProvider(
                    LocalVault provides MemoryVault(),
                    LocalSupport provides Support.Offered(price),
                ) {
                    SettingsScreen(
                        settings = Settings(),
                        canForget = false,
                        page = SettingsPage.ROOT,
                        onOpen = {},
                        onChange = {},
                        onForget = {},
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()
    }
}
