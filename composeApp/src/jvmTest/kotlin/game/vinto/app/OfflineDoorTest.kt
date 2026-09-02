package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import game.vinto.client.Reachability
import game.vinto.client.rememberNickname
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In aeroplane mode the online menu says so, and its three doors go nowhere.
 *
 * Before this, every one of them opened: Browse asked the service and came back with a
 * failure, Open a room did the same one screen later, and Join spent a tap and a code on a
 * socket that could not be made. The platform knew all along. Now the menu reads the
 * platform once and shows the sentence *before* the tap — and the tap still lands, because
 * that is how `ActionTile` works: a press on a tile that is not ready is how a player finds
 * out why, so it dims rather than dies.
 *
 * The other half is that nothing changes when the platform cannot tell. The desktop has no
 * dependable signal and answers `UNKNOWN`; a test that rendered the menu without saying
 * anything about the network must see the same menu it always did.
 */
@OptIn(ExperimentalTestApi::class)
class OfflineDoorTest {

    @Test
    fun withNoNetworkTheMenuSaysSoAndThePressGoesNowhere() {
        var opened = 0
        var joined = 0
        var browsed = 0

        runComposeUiTest {
            setContent {
                Menu(Reachability.OFFLINE, onOpen = { opened++ }, onJoin = { joined++ }, onBrowse = { browsed++ })
            }
            waitForIdle()

            onNodeWithText(OFFLINE_SENTENCE).assertIsDisplayed()

            onNodeWithContentDescription("Open a room").performClick()
            onNodeWithContentDescription("Join with a code").performClick()
            onNodeWithContentDescription("Browse public rooms").performClick()
            waitForIdle()
        }

        assertEquals(0, opened, "Open a room went ahead with no network")
        assertEquals(0, joined, "Join with a code went ahead with no network")
        assertEquals(0, browsed, "Browse went ahead with no network")
    }

    @Test
    fun aPlatformThatCannotTellLeavesTheMenuAsItWas() {
        var opened = 0

        runComposeUiTest {
            setContent {
                Menu(Reachability.UNKNOWN, onOpen = { opened++ }, onJoin = {}, onBrowse = {})
            }
            waitForIdle()

            // The desktop, which cannot tell, must not tell the player they are offline.
            onAllNodesWithText(OFFLINE_SENTENCE).assertCountEquals(0)
            onNodeWithContentDescription("Open a room").performClick()
            waitForIdle()
        }

        assertEquals(1, opened, "a menu with nothing known about the network stopped working")
    }

    /** The online menu with a name already remembered, so only the network can hold it. */
    @Composable
    private fun Menu(
        reachability: Reachability,
        onOpen: (String) -> Unit,
        onJoin: (String) -> Unit,
        onBrowse: (String) -> Unit,
    ) {
        val vault = MemoryVault().apply { rememberNickname("Ann") }
        VintoTheme {
            CompositionLocalProvider(LocalReachability provides reachability) {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    OnlineScreen(
                        vault = vault,
                        onOpenRoom = onOpen,
                        onJoinByCode = onJoin,
                        onBrowse = onBrowse,
                        onBack = {},
                    )
                }
            }
        }
    }

    private companion object {
        /** `online_offline` as English renders it — the same way `SteadyControlsTest` names "Back". */
        const val OFFLINE_SENTENCE =
            "No network right now. Online play needs one; single player and the lesson work as normal."
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
