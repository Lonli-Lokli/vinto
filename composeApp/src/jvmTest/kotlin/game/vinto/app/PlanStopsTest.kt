package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.PLAN_STOPS_MORE
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.CardAt
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Step
import game.vinto.shapes.coalitionInTurnOrder
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The plan's strip of stops shows the stop being read, and says when it runs on.
 *
 * The strip scrolls, on purpose: the play buttons beside it are thumbs and never give up width,
 * and online a lit stop spells a minted name as long as "Patient Harbour". A layout review found
 * what that cost on a phone: the last stops ran on under the play button, one of them cut in half
 * against it and reading as broken, and nothing said there was more. And the stop being read could
 * be one of those out of sight.
 */
@OptIn(ExperimentalTestApi::class)
class PlanStopsTest {

    @Test
    fun theStopBeingReadIsAlwaysInView() {
        PHONES.forEach { (phone, size) ->
            (1..PAGES).forEach { page ->
                runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                    show(finalRound(), page, size.first, size.second)
                    val strip = strip()
                    val lit = litStop()
                    assertTrue(
                        lit.left >= strip.left - EDGE && lit.right <= strip.right + EDGE,
                        "on a $phone reading page $page the lit stop at $lit is outside the strip at $strip",
                    )
                }
            }
        }
    }

    @Test
    fun aStripThatRunsOnSaysSo() {
        PHONES.forEach { (phone, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(finalRound(), 1, size.first, size.second)
                val runsOn = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
                    .fetchSemanticsNodes()
                    .any { it.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0f }
                val said = onAllNodes(
                    SemanticsMatcher(
                        "says the strip runs on",
                    ) { it.config.getOrNull(SemanticsProperties.TestTag) == PLAN_STOPS_MORE },
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
                assertTrue(!runsOn || said, "on a $phone the stops run on past the strip and nothing says so")
                assertTrue(runsOn, "on a $phone this test no longer stages a strip that runs on")
            }
        }
    }

    private fun ComposeUiTest.strip(): Rect =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .fetchSemanticsNodes()
            .first { node -> node.children.isNotEmpty() && hasTabs(node) }
            .boundsInRoot

    private fun hasTabs(node: SemanticsNode): Boolean =
        node.config.getOrNull(SemanticsProperties.Role) == Role.Tab ||
            node.children.any { hasTabs(it) }

    /**
     * Where the lit stop is drawn, whether or not the strip shows it. Not `boundsInRoot`, which is
     * clipped to the strip: a stop scrolled out of sight measures as a sliver at its edge and would
     * pass.
     */
    private fun ComposeUiTest.litStop(): Rect {
        val stop = onAllNodes(
            SemanticsMatcher("the lit stop") {
                it.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
                    it.config.getOrNull(SemanticsProperties.Selected) == true
            },
        ).fetchSemanticsNodes().single()
        val at = stop.positionInRoot
        return Rect(at.x, at.y, at.x + stop.size.width, at.y + stop.size.height)
    }

    private fun ComposeUiTest.show(view: PlayerView, page: Int, wide: Dp, high: Dp) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(wide, high)) {
                    CompositionLocalProvider(LocalStage provides Stage()) {
                        TableScreen(
                            state = TableState(
                                view,
                                tableFor(view, question = Question.ThePlan(at = page), plan = settled(view)),
                                null,
                                emptyList(),
                                1,
                            ),
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

    /** Every turn said, so every page can be read and every stop lit in turn. */
    private fun settled(view: PlayerView): CoalitionPlan = CoalitionPlan(
        lanes = coalitionInTurnOrder(view.players.map { it.id }, view.vintoCallerId.orEmpty())
            .map { seat -> Lane(seat, Step.PutDown(CardAt(seat, 0))) },
    )

    /** A final round online: four people with minted names, the longest the room can mint. */
    private fun finalRound(): PlayerView {
        val whole = teachingSession().view.value
        val named = whole.copy(
            players = whole.players.mapIndexed { i, seat -> seat.copy(name = NAMES[i], nickname = NAMES[i]) },
        )
        val caller = named.players.first { it.id != named.viewerId }
        return named.copy(
            phase = GamePhase.FINAL,
            finalTurnTriggered = true,
            vintoCallerId = caller.id,
            currentPlayerIndex = named.players.indexOfFirst { it.id == caller.id },
            players = named.players.map { if (it.id == caller.id) it.copy(isVintoCaller = true) else it },
        )
    }

    private companion object {
        val NAMES = listOf("Patient Harbour", "Clever Lantern", "Golden Sparrow", "Frosty Compass")

        /** Three coalition turns and where the plan lands. */
        const val PAGES = 4
        const val EDGE = 1f

        val PHONES = mapOf(
            "Galaxy S23" to (360.dp to 740.dp),
            "iPhone 17 Pro" to (402.dp to 778.dp),
        )

        const val WINDOW_W = 900
        const val WINDOW_H = 1000
    }
}
