package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.RoomNotice
import game.vinto.client.RoomTrouble
import game.vinto.protocol.UPDATE_AVAILABLE_CODE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The room asks for a newer build two ways, and the screen answers each with the way to the store. */
@OptIn(ExperimentalTestApi::class)
class UpdatingTest {

    @Test
    fun aRefusalAtTheDoorGetsTheWayToTheStoreAndNothingElse() = runComposeUiTest {
        var updates = 0
        setContent { VintoTheme { UpdateTheApp(RoomTrouble.UPDATE_NEEDED, onUpdate = { updates++ }) } }
        onNodeWithText("Update", ignoreCase = true).performClick()
        assertEquals(1, updates)

        setContent { VintoTheme { UpdateTheApp(RoomTrouble.BUSY, onUpdate = { updates++ }) } }
        assertTrue(
            onAllNodesWithText("Update", ignoreCase = true).fetchSemanticsNodes().isEmpty(),
            "a retryable trouble offered an update",
        )
    }

    @Test
    fun aNoticeIsADialogWithAWayToCarryOn() = runComposeUiTest {
        var updated = false
        var dismissed = false
        setContent {
            VintoTheme {
                UpdateNoticeDialog(
                    RoomNotice(UPDATE_AVAILABLE_CODE, "a newer build is waiting", warning = true),
                    onUpdate = { updated = true },
                    onNotNow = { dismissed = true },
                )
            }
        }
        assertTrue(onAllNodesWithText("Update your app", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        onNodeWithText("Not now", ignoreCase = true).performClick()
        assertTrue(dismissed, "not now did not carry on")
        assertTrue(!updated)
    }
}
