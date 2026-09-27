package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.game.faceTag
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A seat's plate sits beside its own cards, at every width.
 *
 * On a phone the two touch because the phone is narrow. On a desktop the hand was centred in the
 * whole width of the felt and the plate pinned to its rim, so your name sat 400 points from your
 * cards and the seat opposite's the same distance from theirs: a layout review found it on every
 * tablet and desktop. The plate belongs to the hand, so it stands beside it.
 *
 * And the other half, which was reported from a phone before this: a plate must not slide towards
 * the middle when its hand grows onto a second row, because that reads as the seat jumping.
 */
@OptIn(ExperimentalTestApi::class)
class PlatesBesideTheirHandsTest {

    @Test
    fun onEveryScreenAPlateStandsBesideItsHand() {
        SCREENS.forEach { (screen, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(named(teachingSession().view.value), size.first, size.second)
                listOf(ME, OPPOSITE).forEach { who ->
                    val plate = plateOf(who)
                    val cards = cardsOf(who)
                    val gap = if (plate.center.x < cards.minOf { it.left }) {
                        cards.minOf { it.left } - plate.right
                    } else {
                        plate.left - cards.maxOf { it.right }
                    }
                    assertTrue(gap <= BESIDE, "on a $screen $who's plate is ${gap.toInt()} points from their cards")
                }
            }
        }
    }

    @Test
    fun aPlateNeverSlidesInwardsAsItsHandGrows() {
        PHONES.forEach { (phone, size) ->
            val dealt = platesAt(size, 5)
            listOf(6, 7, 8).forEach { held ->
                val grown = platesAt(size, held)
                listOf(ME, OPPOSITE).forEach { who ->
                    val was = dealt.getValue(who)
                    val now = grown.getValue(who)
                    val middle = size.first.value / 2
                    val inwards = kotlin.math.abs(was.center.x - middle) - kotlin.math.abs(now.center.x - middle)
                    assertTrue(
                        inwards <= 1f,
                        "on a $phone $who's plate slid ${inwards.toInt()} points inwards holding $held",
                    )
                }
            }
        }
    }

    private fun platesAt(size: Pair<Dp, Dp>, held: Int): Map<String, Rect> {
        var plates = emptyMap<String, Rect>()
        runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
            show(holding(named(teachingSession().view.value), held), size.first, size.second)
            plates = listOf(ME, OPPOSITE).associateWith { plateOf(it) }
        }
        return plates
    }

    /** A seat's plate, found from its face: the smallest named thing round it. */
    private fun ComposeUiTest.plateOf(who: String): Rect {
        val face = onNodeWithTag(faceTag(who), useUnmergedTree = true).fetchSemanticsNode()
        var node = face
        while (node.parent != null && node.parent!!.boundsInRoot.width < PLATE_WIDEST) node = node.parent!!
        return node.boundsInRoot
    }

    private fun ComposeUiTest.cardsOf(who: String): List<Rect> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .filter {
                it.config.getOrNull(
                    SemanticsProperties.ContentDescription,
                )?.firstOrNull()?.startsWith("$who, card ") == true
            }
            .map { it.boundsInRoot }

    private fun ComposeUiTest.show(view: PlayerView, wide: Dp, high: Dp) {
        setContent {
            VintoTheme {
                Column(modifier = Modifier.size(wide, high)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(wide, high),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                        modifier = Modifier.weight(1f),
                        onLeave = {},
                    )
                    Box(modifier = Modifier.height(52.dp))
                }
            }
        }
        waitForIdle()
    }

    private fun named(view: PlayerView): PlayerView = view.copy(
        players = view.players.mapIndexed { i, seat -> seat.copy(name = NAMES[i], nickname = NAMES[i]) },
    )

    private fun holding(view: PlayerView, held: Int): PlayerView = view.copy(
        players = view.players.map { seat -> seat.copy(cards = List(held) { i -> seat.cards[i % seat.cards.size] }) },
    )

    private companion object {
        val NAMES = listOf("Dusty Pebble", "Clever Harbour", "Golden Lantern", "Patient Sparrow")
        const val ME = "Dusty Pebble"
        const val OPPOSITE = "Golden Lantern"

        /** A plate is beside its hand when the gap between them is no more than a card's margin. */
        const val BESIDE = 16f

        /** Wider than any plate: the search for a plate stops before it reaches the whole seat. */
        const val PLATE_WIDEST = 170f

        val SCREENS = mapOf(
            "Pixel 7" to (412.dp to 805.dp),
            "Galaxy Z Fold, open" to (673.dp to 800.dp),
            "iPad Air 11, portrait" to (820.dp to 1136.dp),
            "Galaxy Tab S9, landscape" to (1280.dp to 752.dp),
            "iPad Pro 13, landscape" to (1376.dp to 988.dp),
            "Laptop browser" to (1440.dp to 800.dp),
            "Desktop window" to (1920.dp to 1040.dp),
        )

        val PHONES = mapOf(
            "Pixel 7" to (412.dp to 805.dp),
            "Galaxy S23" to (360.dp to 740.dp),
            "iPhone 17 Pro Max" to (440.dp to 860.dp),
        )

        const val WINDOW_W = 2000
        const val WINDOW_H = 1200
    }
}
