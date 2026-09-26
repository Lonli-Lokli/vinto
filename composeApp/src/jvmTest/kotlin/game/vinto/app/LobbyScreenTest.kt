package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.RoomScreen
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Pace
import game.vinto.client.RemoteRoom
import game.vinto.protocol.NoticeSeverity
import game.vinto.protocol.ServerMessage
import game.vinto.protocol.UPDATE_NEEDED_CODE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lobby, as a phone draws it: everything on one screen, and the right answer to a refusal.
 *
 * Reported from an iPhone 17 Pro with a screenshot. The seats took four full-width rows, the
 * invitation card took a third of the screen with its QR code centred on a line of its own — and
 * between them the scrolling middle had room for exactly half of the green "Add a bot" button,
 * cut off at the top of the invitation. The one control that starts a game with fewer than four
 * people was the one a player could not see.
 */
@OptIn(ExperimentalTestApi::class)
class LobbyScreenTest {

    /**
     * Every control in a busy lobby is on the screen whole — none scrolled out of sight, none cut
     * off at the edge of the scrolling middle — on the phones people hold, down to the small ones.
     *
     * Measured as `node.size` against `boundsInRoot`: the first is what the control is, the second
     * is what of it can be seen, and a control that is partly scrolled away has the second smaller
     * than the first. That difference is exactly the half-button the report was about.
     */
    @Test
    fun aBusyLobbyFitsOnAPhoneWithEveryControlWhole() {
        PHONES.forEach { (phone, size) ->
            runComposeUiTest {
                val room = show(size.first, size.second, stagedRoom(busyLobby()))
                CONTROLS.forEach { label ->
                    val nodes = onAllNodesWithContentDescription(label).fetchSemanticsNodes()
                    assertEquals(1, nodes.size, "on a $phone the lobby has ${nodes.size} controls saying \"$label\"")
                    val node = nodes.single()
                    val seen = node.boundsInRoot
                    val whole = node.size
                    assertTrue(
                        seen.height.toInt() >= whole.height - 1 && seen.width.toInt() >= whole.width - 1,
                        "on a $phone \"$label\" is ${whole.width}x${whole.height} but only " +
                            "${seen.width.toInt()}x${seen.height.toInt()} of it is on the screen",
                    )
                    assertTrue(
                        whole.height >= TAP && whole.width >= TAP,
                        "on a $phone \"$label\" is ${whole.width}x${whole.height}, under a ${TAP}dp thumb",
                    )
                }
                room.leave()
            }
        }
    }

    /**
     * Every name in the lobby is drawn whole — no "Dusty Peb…".
     *
     * Two seats, two minted names, and the room guarantees they differ; an ellipsis is where two
     * of them stop being told apart.
     */
    @Test
    fun everyNameInTheLobbyIsDrawnWhole() {
        PHONES.forEach { (phone, size) ->
            runComposeUiTest {
                val room = show(size.first, size.second, stagedRoom(busyLobby()))
                listOf("Dusty Pebble", "Dusty Rowan", "Tide").forEach { name ->
                    val node = onAllNodes(saying { name in it }).fetchSemanticsNodes().singleOrNull()
                        ?: error("on a $phone $name is not in the lobby once")
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    assertTrue(layouts.none { it.cutShort() }, "on a $phone $name is cut short in the lobby")
                }
                room.leave()
            }
        }
    }

    /**
     * A build the room will not seat is sent to the store, not back to the door.
     *
     * It used to read "Could not reach that room. Check the code, or your connection." with a
     * Try again under it — to somebody whose code and connection were both fine, and for whom
     * trying again is the one thing certain to be refused.
     */
    @Test
    fun aBuildTheRoomWillNotSeatIsSentToTheStore() = runComposeUiTest {
        val room = show(
            402.dp,
            778.dp,
            stagedRoom(
                ServerMessage.Notice(UPDATE_NEEDED_CODE, TOO_OLD, NoticeSeverity.WARNING),
                ServerMessage.Error(message = TOO_OLD, code = UPDATE_NEEDED_CODE),
            ),
        )
        onNodeWithText("This version of the game is too old for online play. Update it from the store.")
            .assertExists("the lobby did not say why it will not seat this build")
        onNodeWithContentDescription("Update").assertExists("there is no way to the store")
        onNodeWithContentDescription("Try again").assertDoesNotExist()
        onNodeWithText("Could not reach that room. Check the code, or your connection.").assertDoesNotExist()
        room.leave()
    }

    /**
     * Whether a line of text lost any of itself: an ellipsis, a line past `maxLines`, or
     * characters the last line never reached. Not `didOverflowWidth`, which a Row sets on a
     * name drawn whole — measured once for its intrinsic width and flagged against that.
     */
    private fun TextLayoutResult.cutShort(): Boolean =
        (0 until lineCount).any { isLineEllipsized(it) } ||
            didOverflowHeight ||
            getLineEnd(lineCount - 1, visibleEnd = true) < layoutInput.text.length

    /** Text nodes whose words pass [test]. */
    private fun saying(test: (String) -> Boolean) = SemanticsMatcher("says something") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { test(it.text) } == true
    }

    private fun ComposeUiTest.show(wide: Dp, high: Dp, room: RemoteRoom): RemoteRoom {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(wide, high)) {
                    RoomScreen(room, Pace.STEADY, onSettings = {}, onLeft = {})
                }
            }
        }
        waitForIdle()
        return room
    }

    private companion object {
        /** An iPhone SE is the shortest phone the table is held to; the lobby is held to it too. */
        val PHONES = listOf(
            "iPhone SE" to (375.dp to 667.dp),
            "Galaxy S23" to (360.dp to 740.dp),
            "iPhone 17 Pro" to (402.dp to 778.dp),
        )

        val CONTROLS = listOf(
            "Add a bot",
            "Remove the bot in seat 3",
            "Share the code",
            "Copy the code",
            "Leave the room",
        )

        const val TAP = 44
        const val TOO_OLD = "This version of the app is too old for online play. Please update it from the store."
    }
}
