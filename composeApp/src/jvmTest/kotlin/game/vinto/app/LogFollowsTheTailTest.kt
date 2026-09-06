package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Say
import game.vinto.client.Speaker
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The foot of the log is the part worth reading, and it is the part that goes missing.
 *
 * Two ways it can, and the box used to lose both. One run of moves by the same actor folds
 * into a *single* line that grows in place — "You drew the Q ➜ You play the Q ➜ You left them
 * alone" — so the number of lines in the box does not change when the newest thing happens,
 * and an effect watching that number never fires. And a line long enough to wrap past the
 * depth of the well cannot be brought into view by landing its top at the top of the box:
 * what that shows is its beginning, which is the oldest half of it.
 *
 * Both are measured the same way, and the way `RailFitsTest` measures a button under the
 * edge of the screen: the clipped bounds of the last line against its own height. If the
 * tail of the newest line is below the fold, the box is not following the game.
 */
@OptIn(ExperimentalTestApi::class)
class LogFollowsTheTailTest {

    /** A turn long enough to overflow the well, all of it one actor, so it is one folded line. */
    @Test
    fun aFoldedLineTallerThanTheWellIsReadFromItsFoot() = runComposeUiTest {
        val view = atMyTurn()
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) { Rail(view, longTurn()) }
            }
        }
        waitForIdle()

        assertTailIsOnScreen()
    }

    /**
     * The newest move joins the line already there rather than adding one. Nothing about the
     * box's *shape* changes, which is exactly why watching its shape was not enough.
     */
    @Test
    fun theBoxFollowsALineThatGrowsWithoutAddingOne() = runComposeUiTest {
        val view = atMyTurn()
        val log = longTurn()

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) { Rail(view, log) }
            }
        }
        waitForIdle()

        // One more move by the same actor: the fold puts it on the end of the last line.
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    Rail(view, log + Say.LeftThemAlone(Speaker.You))
                }
            }
        }
        waitForIdle()

        assertTailIsOnScreen()
    }

    /** The lowest line in the box shows its own last row of words, not just its first. */
    private fun androidx.compose.ui.test.ComposeUiTest.assertTailIsOnScreen() {
        val lines = onAllNodes(hasLogText()).fetchSemanticsNodes()
        assertTrue(lines.isNotEmpty(), "the box drew no lines at all")

        val tail = lines.maxBy { it.positionInRoot.y }
        val clipped = tail.boundsInRoot.bottom
        val whole = tail.positionInRoot.y + tail.size.height
        assertTrue(
            clipped >= whole - 1f,
            "the newest line ends ${whole - clipped}px below the fold: ${tail.text()}",
        )
    }

    /** Every line of the well, and nothing else: the log is the only place a `➜` is written. */
    private fun hasLogText() = androidx.compose.ui.test.SemanticsMatcher("a line of the log") { node ->
        node.text()?.contains("➜") == true
    }

    private fun SemanticsNode.text() =
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }

    /**
     * A run of moves by one actor. Folded, this is one line several rows deep in the well —
     * which is the case the box got wrong, and the case a real toss-in cascade produces.
     */
    private fun longTurn(): List<Say> = listOf(
        Say.RoundBegins,
        Say.Drew(Speaker.You),
        Say.Swapped(Speaker.You, slot = 2, dropped = Rank.QUEEN),
        Say.DeclaredRank(Speaker.You, Rank.QUEEN),
    ) + List(THROWS) { Say.TossedIn(Speaker.You, Rank.QUEEN) } + Say.ThrewAway(Speaker.You, Rank.NINE)

    @Composable
    private fun Rail(view: PlayerView, said: List<Say>) {
        TableScreen(
            state = TableState(view, tableFor(view), null, said, 1),
            layout = TableLayout.forScreen(PHONE_H),
            onMove = {},
            onHelp = {},
            onSettings = {},
            onReport = {},
            onDeck = {},
        )
    }

    private fun atMyTurn(): PlayerView {
        lateinit var view: PlayerView
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            view = session.view.value
        }
        return view
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp

        /** Enough throws into one window that the folded line outgrows the well. */
        const val THROWS = 12
    }
}
