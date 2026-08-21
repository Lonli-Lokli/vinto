package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Everything a thumb has to hit, measured.
 *
 * WCAG 2.2 asks for 24x24 at AA (SC 2.5.8) and 44x44 at AAA (SC 2.5.5); Material asks for
 * 48dp and Apple for 44pt. This holds the app to **44dp in both directions**, which clears
 * the enhanced level and both platform guidelines, because a card game is played by tapping
 * small things quickly and the difference between 24 and 44 is the difference between playing
 * and aiming.
 *
 * It reads the bounds out of the composition rather than trusting the modifiers, which is how
 * it caught the fourteen rank chips: a `heightIn` had been set and no `widthIn`, so a chip
 * offering "2" was 44dp tall and 27dp wide — legal at AA, and far too small to hit while
 * three bots wait.
 */
@OptIn(ExperimentalTestApi::class)
class TouchTargetTest {

    @Test
    fun everythingOnATurnCanBeHit() = eachTapTarget(Question.None) { what, box ->
        assertTrue(box.width >= TAP && box.height >= TAP, tooSmall(what, box))
    }

    /** The worst case: fourteen ranks, a confirm and a cancel, all in one rail. */
    @Test
    fun everyRankChipCanBeHit() = eachTapTarget(Question.CallRank(0)) { what, box ->
        assertTrue(box.width >= TAP && box.height >= TAP, tooSmall(what, box))
    }

    /** And the header, whose controls are the smallest things drawn. */
    @Test
    fun theHeaderControlsCanBeHit() = eachTapTarget(Question.None, header = true) { what, box ->
        assertTrue(box.width >= TAP && box.height >= TAP, tooSmall(what, box))
    }

    private fun tooSmall(what: String, box: Rect) =
        "$what is ${box.width.toInt()}x${box.height.toInt()}dp, under a ${TAP.toInt()}dp thumb"

    /**
     * Renders the table and hands every tappable thing on it to [check].
     *
     * @param header when true, looks only at the strip above the felt, whose controls are
     *   otherwise lost among forty cards.
     */
    private fun eachTapTarget(
        question: Question,
        header: Boolean = false,
        check: (String, Rect) -> Unit,
    ) = runComposeUiTest {
        val view = teachingSession().view.value
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    TableScreen(
                        state = TableState(view, tableFor(view, question), null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H),
                        onMove = {},
                        onHelp = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()

        val targets = tapTargets().filter { (_, box) ->
            if (header) box.top < HEADER else box.top >= HEADER
        }
        assertTrue(targets.isNotEmpty(), "nothing to tap at all")
        targets.forEach { (what, box) -> check(what, box) }
    }

    private fun ComposeUiTest.tapTargets(): List<Pair<String, Rect>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .fetchSemanticsNodes()
            .map { node ->
                val name = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                    ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
                    ?: "an unnamed control"
                name to node.boundsInRoot
            }

    private companion object {
        const val TAP = 44f
        const val HEADER = 44f
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
