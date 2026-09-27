package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tablet's control rail is a tablet's, not a phone's stretched.
 *
 * Two things a layout review found. Beside a landscape tablet's felt, "Swap cards" wrapped onto
 * two lines with most of the rail's height to spare below it: the side rail kept the phone's rule,
 * which lets a label break between words because under a phone's felt height is what runs out. And
 * under a portrait tablet's felt, the phone's controls were spread across the whole width — two
 * buttons each wider than a phone — which reads as a phone app blown up.
 */
@OptIn(ExperimentalTestApi::class)
class TabletRailTest {

    @Test
    fun besideTheFeltAChoiceKeepsItsWordsOnOneLine() {
        LANDSCAPE.forEach { (tablet, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(drawn(), size.first, size.second)
                CHOICES.forEach { choice ->
                    val lines = labelLayout(choice).lineCount
                    assertTrue(lines == 1, "beside a $tablet's felt \"$choice\" is drawn on $lines lines")
                }
            }
        }
    }

    @Test
    fun underATabletsFeltTheControlsAreNotAPhoneStretched() {
        PORTRAIT.forEach { (tablet, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(drawn(), size.first, size.second)
                val buttons = CHOICES.map { button(it) }
                buttons.forEach { box ->
                    assertTrue(
                        box.width <= WIDEST_CHOICE,
                        "under a $tablet's felt a choice is ${box.width.toInt()} points wide",
                    )
                }
                // And centred under the felt rather than pushed to one side.
                val left = buttons.minOf { it.left }
                val right = size.first.value - buttons.maxOf { it.right }
                assertTrue(
                    kotlin.math.abs(left - right) <= CENTRED,
                    "under a $tablet's felt the choices sit $left from one edge and $right from the other",
                )
            }
        }
    }

    private fun ComposeUiTest.button(label: String): Rect =
        onAllNodes(
            SemanticsMatcher(
                "names $label",
            ) { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true },
        )
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot

    /** The text a choice is drawn with, found inside the button that names it. */
    private fun ComposeUiTest.labelLayout(label: String): TextLayoutResult {
        val text = onAllNodes(
            SemanticsMatcher("draws $label") { node ->
                node.config.getOrNull(
                    SemanticsProperties.Text,
                )?.any { it.text.equals(label, ignoreCase = true) } == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().first()
        val layouts = mutableListOf<TextLayoutResult>()
        text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun ComposeUiTest.show(view: PlayerView, wide: Dp, high: Dp) {
        setContent {
            // The words a tablet draws: a little larger than a phone's, as `App` scales them.
            val base = LocalDensity.current
            val scaled = Density(base.density, base.fontScale * typeScaleFor(minOf(wide, high)))
            CompositionLocalProvider(LocalDensity provides scaled) {
                VintoTheme {
                    Box(modifier = Modifier.size(wide, high)) {
                        TableScreen(
                            state = TableState(view, tableFor(view), null, emptyList(), 1),
                            layout = TableLayout.forScreen(wide, high),
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    /** Your turn with a card drawn, so the rail is asking whether to swap it or discard it. */
    private fun drawn(): PlayerView {
        lateinit var view: PlayerView
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            view = session.view.value
        }
        return view
    }

    private companion object {
        val CHOICES = listOf("Swap Cards", "Discard")

        val LANDSCAPE = mapOf(
            "iPad Pro 13" to (1376.dp to 988.dp),
            "Galaxy Tab S9" to (1280.dp to 752.dp),
        )

        val PORTRAIT = mapOf(
            "iPad Air 11" to (820.dp to 1136.dp),
            "iPad mini" to (744.dp to 1089.dp),
        )

        /** A phone's widest choice, give or take: two to a row under a 440-point phone's felt. */
        const val WIDEST_CHOICE = 320f
        const val CENTRED = 2f

        const val WINDOW_W = 1500
        const val WINDOW_H = 1200
    }
}
