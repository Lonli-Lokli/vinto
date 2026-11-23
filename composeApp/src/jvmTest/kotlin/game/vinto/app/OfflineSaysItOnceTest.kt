package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.theme.VintoTheme
import game.vinto.client.CreatedRoom
import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.client.RoomTrouble
import game.vinto.client.answering
import game.vinto.protocol.PublicRoom
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Offline, the room service is named once.
 *
 * Reported from a local web build with `fetch` refusing: Public rooms said "No connection to
 * the room service. Check the network and try again." and under it "Could not reach the room
 * service: could not reach the room service" — the detail line was the generic reason every
 * offline failure carries, wrapped in a sentence that already said it. The service's own words
 * (a full room, a refusal) are still worth the small line; the generic one is not.
 *
 * The offline answer here is the real one, made by [answering] from a thrown failure, so the
 * test cannot drift from what a platform's connector produces.
 */
@OptIn(ExperimentalTestApi::class)
class OfflineSaysItOnceTest {

    @Test
    fun publicRoomsOfflineNamesTheRoomServiceOnce() = runComposeUiTest {
        discover(offline())
        assertEquals(1, linesNaming("room service"), "Public rooms offline said it more than once")
    }

    @Test
    fun publicRoomsStillShowsWhatTheServiceItselfSaid() = runComposeUiTest {
        discover(answeringEverything(RoomAnswer.Failed(RoomTrouble.REFUSED, "no rooms for you")))
        onNodeWithText("Could not reach the room service: no rooms for you").assertExists()
    }

    @Test
    fun openingARoomOfflineNamesTheRoomServiceOnce() = runComposeUiTest {
        openRoom(offline())
        assertEquals(1, linesNaming("room service"), "Open a room offline said it more than once")
    }

    @Test
    fun openingARoomStillShowsWhatTheServiceItselfSaid() = runComposeUiTest {
        openRoom(answeringEverything(RoomAnswer.Failed(RoomTrouble.BUSY, "too many rooms at once")))
        onNodeWithText("too many rooms at once").assertExists()
    }

    private fun ComposeUiTest.discover(connector: RoomConnector) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    DiscoverScreen(connector = connector, onJoin = {}, onBack = {})
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.openRoom(connector: RoomConnector) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    OpenRoomScreen(connector = connector, nickname = "Ada", onEnterRoom = { _, _ -> }, onBack = {})
                }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("Open a new room").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.linesNaming(words: String): Int =
        onAllNodesWithText(words, substring = true, ignoreCase = true).fetchSemanticsNodes().size

    /** What every connector answers when the platform threw: the network was not there. */
    private fun offline(): RoomConnector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = answering { error("Failed to fetch") }
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            answering { error("Failed to fetch") }
        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = answering { error("Failed to fetch") }
    }

    private fun answeringEverything(failed: RoomAnswer.Failed): RoomConnector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = failed
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> = failed
        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = failed
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
