package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Anchor
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A place expecting a card does not draw it until it has arrived.
 *
 * The table steps to the position a move produced *before* its cards fly, so that a card has
 * a laid-out place to land in. In between, the engine's answer is on screen while the card is
 * still in the hand it is leaving — so the discard drew the new top, took it away again when
 * the flight began, and put it back when the flight landed. One card, three appearances, and
 * the middle one reads as a blink over the pile.
 *
 * The fix is that the stage is told what a move is about to move before the view steps, and
 * that is what these cases pin: a pile expecting a card draws the one underneath instead, and
 * the same pile with nothing expected draws the card itself. Both from the same view, so the
 * only difference is the expectation.
 */
@OptIn(ExperimentalTestApi::class)
class DiscardBlinkTest {

    @Test
    fun aPileExpectingACardDrawsWhatIsStillLyingOnIt() = runComposeUiTest {
        val arriving = drawnOn(expecting = true)
        assertTrue(
            arriving.none { it.startsWith("discarded ${Landing.rank.serialName}") },
            "the pile drew the card while it was still crossing the table: $arriving",
        )
    }

    @Test
    fun andDrawsItOnceItHasLanded() = runComposeUiTest {
        val landed = drawnOn(expecting = false)
        assertTrue(
            landed.any { it.startsWith("discarded ${Landing.rank.serialName}") },
            "the pile never drew the card that landed on it: $landed",
        )
    }

    /** Every description on the table, with the discard either expecting a card or not. */
    private fun drawnOn(expecting: Boolean): List<String> {
        var seen = emptyList<String>()
        runComposeUiTest {
            val view = teachingSession().view.value.copy(discardTop = Landing, discardCount = 1)
            val stage = Stage().apply {
                if (expecting) this.expecting[Anchor.Discard] = CardView.Visible(Landing)
            }

            setContent {
                VintoTheme {
                    CompositionLocalProvider(LocalStage provides stage) {
                        Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                            TableScreen(
                                state = TableState(view, tableFor(view), null, emptyList(), 1),
                                layout = TableLayout.forScreen(PHONE_H),
                                onMove = {},
                                onHelp = {},
                                onReport = {},
                                onDeck = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
            seen = descriptions()
        }
        return seen
    }

    private fun ComposeUiTest.descriptions(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            }

    private companion object {
        /** The card the engine has already recorded as the pile's top. */
        val Landing = Card(
            id = "9_1",
            rank = Rank.NINE,
            value = 9,
            actionText = null,
            played = true,
        )
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
