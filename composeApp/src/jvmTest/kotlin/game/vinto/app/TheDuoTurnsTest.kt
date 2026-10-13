package game.vinto.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import game.vinto.app.game.Posture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The closed iPhone Duo turned on its side, which it does like Apple's own apps (owner, 2026-10-13):
 * 678x466 points, and iOS keeps its status column down one edge with the camera at one end of it.
 *
 * Not measured on the simulator yet, so nothing here is a constant: the column is wherever the edge
 * insets say, and what stands in it is wherever iOS reports it. The suites stand the camera at the
 * column's foot, on either edge — the case that would put a pill under the lens — and once with a
 * clock at the top as well.
 */
@OptIn(ExperimentalTestApi::class)
class TheDuoTurnsTest {

    /**
     * Turned, it is a phone's screen, and the phone's rule does not stand it back up: the platform
     * decides whether a phone may turn, and once it has, the table lies on its side beside the column,
     * the rail is the narrow stack, and the felt takes the whole height.
     */
    @Test
    fun theTurnedClosedDuoIsDealtTheTableOnItsSide() {
        listOf(true, false).forEach { onRight ->
            val layout = Duos.turned(Duos.turnedColumn(onRight))
            assertEquals(
                Posture.ON_ITS_SIDE,
                layout.posture,
                "the turned Duo was stood upright (column right: $onRight)",
            )
            assertTrue(layout.narrowRail, "beside the column the rail is not the narrow stack")
            assertEquals(Duos.TURNED_H.dp, layout.feltHeight, "the turned Duo's felt does not take the whole height")
        }
    }

    @Test
    fun theTurnedClosedDuoKeepsItsChoicesClearOfTheCamera() {
        listOf(
            "on the right" to Duos.turnedColumn(onRight = true),
            "on the left" to Duos.turnedColumn(onRight = false),
            "on the right, a clock at its top" to Duos.turnedColumn(onRight = true, clock = true),
        ).forEach { (side, chrome) ->
            runDesktopComposeUiTest(Duos.TURNED_W, Duos.TURNED_H) {
                val (view, said) = Duos.drawn()
                val layout = Duos.turned(chrome)
                val run = assertNotNull(layout.run, "the turned Duo has no column $side")
                showTable(view, said, layout)

                val columnLeft = if (chrome.columnOnRight) Duos.TURNED_W - Duos.COLUMN else 0
                val columnRight = columnLeft + Duos.COLUMN
                val controls = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
                    .fetchSemanticsNodes()
                    .map { it.calledAloud() to it.boundsInRoot }
                val inColumn = controls.filter { (_, at) -> at.between(columnLeft, columnRight) }
                assertTrue(
                    inColumn.map { it.first }.containsAll(listOf(LEAVE, RULES, SETTINGS)),
                    "the column $side does not hold the table's controls: $inColumn",
                )
                // Under whatever stands at the column's top, and above the camera at its foot.
                inColumn.forEach { (what, at) ->
                    assertTrue(
                        at.top >= run.top.value - EDGE && at.bottom <= run.bottom.value + EDGE,
                        "$what $at is outside the column's run ${run.top}..${run.bottom} $side",
                    )
                }
                // Nothing of the table's under the camera or the clock, the turn's choices least of all.
                assertTrue(CHOICES.all { name -> controls.any { it.first == name } }, "the choices are missing $side")
                controls.forEach { (what, at) ->
                    chrome.occlusions.forEach { lens ->
                        assertTrue(!at.overlaps(lens.inPixels()), "$what $at lies under ${lens.inPixels()} $side")
                    }
                }
                // And the table itself keeps out of the column.
                onAllNodes(SemanticsMatcher("a seat's card") { it.calledAloud().contains(", card ") })
                    .fetchSemanticsNodes()
                    .forEach { card ->
                        val at = card.boundsInRoot
                        assertTrue(
                            at.right <= columnLeft + EDGE || at.left >= columnRight - EDGE,
                            "$at is in the column $side",
                        )
                    }
            }
        }
    }

    private fun DpRect.inPixels(): Rect = Rect(left.value, top.value, right.value, bottom.value)

    /** Within the column from [left] to [right], give or take a point. */
    private fun Rect.between(left: Int, right: Int): Boolean = this.left >= left - EDGE && this.right <= right + EDGE

    private companion object {
        const val LEAVE = "Leave the table"
        const val RULES = "Rules"
        const val SETTINGS = "Settings"
        val CHOICES = listOf("Swap Cards", "Discard")
        const val EDGE = 1f
    }
}
