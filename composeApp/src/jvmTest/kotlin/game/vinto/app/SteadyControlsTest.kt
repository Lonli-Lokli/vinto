package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.theme.VintoTheme
import game.vinto.client.CreatedRoom
import game.vinto.client.RoomAnswer
import game.vinto.client.RoomConnector
import game.vinto.client.RoomSocket
import game.vinto.client.RoomTrouble
import game.vinto.protocol.PublicRoom
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The controls under the public list do not move when the answer arrives.
 *
 * They used to ride on top of the list, which is four different heights — still asking,
 * unable to ask, asked and empty, a row per table — so the two buttons landed somewhere new
 * each time the room answered. Reported from a phone, and it is worse than untidy: a thumb
 * already travelling towards Refresh arrives at Back.
 *
 * So the list takes the space between a fixed head and a fixed foot. This measures the foot
 * in every state the screen has.
 */
@OptIn(ExperimentalTestApi::class)
class SteadyControlsTest {

    @Test
    fun theWayOutIsInTheSamePlaceWhateverTheRoomSaid() {
        val whileAsking = backAt(neverAnswers())
        val whenEmpty = backAt(listing(emptyList()))
        val whenRefused = backAt(unreachable())
        val whenListed = backAt(listing(listOf(table("ABC234"), table("QWE567"))))

        assertEquals(whileAsking, whenEmpty, "Back moved between asking and an empty list")
        assertEquals(whileAsking, whenRefused, "Back moved between asking and a refusal")
        assertEquals(whileAsking, whenListed, "Back moved between asking and a list of tables")
    }

    /** Where the Back button's top edge lands, in this screen's own pixels. */
    private fun backAt(connector: RoomConnector): Int {
        var y = -1
        runComposeUiTest {
            setContent {
                VintoTheme {
                    Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                        DiscoverScreen(connector = connector, onJoin = {}, onBack = {})
                    }
                }
            }
            waitForIdle()
            y = onNodeWithContentDescription("Back").fetchSemanticsNode().positionInRoot.y.toInt()
        }
        return y
    }

    private fun table(code: String) =
        PublicRoom(code = code, hostNickname = "Ada", humans = 1, seatsFilled = 2)

    /** A connector that never comes back, so the screen stays on its first load. */
    private fun neverAnswers(): RoomConnector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = awaitCancellation()
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            awaitCancellation()

        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = awaitCancellation()
    }

    private fun listing(rooms: List<PublicRoom>): RoomConnector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = refused()
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            refused()

        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = RoomAnswer.Ok(rooms)
    }

    private fun unreachable(): RoomConnector = object : RoomConnector {
        override suspend fun connect(code: String): RoomAnswer<RoomSocket> = refused()
        override suspend fun createRoom(isPublic: Boolean, hostNickname: String): RoomAnswer<CreatedRoom> =
            refused()

        override suspend fun listPublicRooms(): RoomAnswer<List<PublicRoom>> = refused()
    }

    private fun refused(): RoomAnswer.Failed =
        RoomAnswer.Failed(RoomTrouble.OFFLINE, "this test has no network")

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
