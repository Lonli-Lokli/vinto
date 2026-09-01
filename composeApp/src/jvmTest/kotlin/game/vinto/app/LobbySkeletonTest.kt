package game.vinto.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.SeatRow
import game.vinto.app.game.WaitingSeat
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LobbySeatUi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A seat that has not arrived yet is exactly as tall as the seat that replaces it.
 *
 * The lobby's column is centred, so its height *is* the screen's layout: when the room
 * answered, four seat rows appeared where a 40 dp spinner had been and the title, the
 * invitation and the leave button all jumped a couple of hundred pixels — at the one moment
 * the player is reading the screen hardest. Reported from a phone, twice.
 *
 * The placeholder matches by construction: the same `Surface`, the same padding, and a bar
 * built around a blank line of the row's own type rather than a height in dp. This is the
 * check that says so, because "by construction" survives exactly as long as nobody edits one
 * of the two.
 */
@OptIn(ExperimentalTestApi::class)
class LobbySkeletonTest {

    @Test
    fun aWaitingSeatIsTheSameHeightAsTheSeatItStandsIn() {
        // The seats a room has at the moment it answers: whoever is here, and open chairs.
        // None of them can carry a remove button yet — that arrives with a bot — so these
        // are the plain rows the placeholder is standing in for.
        val arriving = listOf(
            LobbySeatUi(
                index = 0,
                occupied = true,
                isBot = false,
                removable = false,
                nickname = "Player 1",
                isMine = true,
            ),
            LobbySeatUi(
                index = 1,
                occupied = false,
                isBot = false,
                removable = false,
                nickname = null,
                isMine = false,
            ),
        )
        val waiting = heightOf { WaitingSeat() }
        arriving.forEach { seat ->
            val real = heightOf { SeatRow(seat, changing = false) {} }
            assertEquals(
                real,
                waiting,
                "a waiting seat is ${waiting}px where seat ${seat.index} is ${real}px — " +
                    "the lobby will jump by four times the difference when the room answers",
            )
        }
    }

    /** The rendered height of one row, at a phone's width. */
    private fun heightOf(content: @Composable () -> Unit): Int {
        var height = -1
        runComposeUiTest {
            setContent {
                VintoTheme {
                    Column(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                        Column(modifier = Modifier.width(ROW_W)) { content() }
                    }
                }
            }
            waitForIdle()
            // The row itself is the first node under the two layout columns; measuring the
            // root would measure the phone. `size` is the unclipped semantics size — the
            // clipped `boundsInRoot` is the trap this repository has recorded hitting twice.
            height = onRoot()
                .fetchSemanticsNode()
                .children
                .firstOrNull()
                ?.size
                ?.height
                ?: error("nothing was composed")
        }
        return height
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
        val ROW_W = 360.dp
    }
}
