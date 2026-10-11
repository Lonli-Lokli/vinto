package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
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
 * The seats at the sides face the middle of the table: cards along the rim, the plate between
 * them and the piles.
 *
 * Reported 2026-10-09 from a phone: *"Ember must be displayed between cards and draw area - it
 * looks more natural, also when he has more cards they can be displayed below her avatar if space
 * allows it."* The plates stood against the rim with the cards between them and the piles, and a
 * hand that grew took a second column further in still. Both sides now face the piles (the
 * product owner chose the mirror over moving the left seat alone), and a grown hand's extra cards
 * lie in the plate's own column — under it on the left, where the plate is at the top, and above
 * it on the right, where it is at the foot — wherever they fit there at their own size.
 *
 * Up to ten, by the owner's rule for the table: "accommodate as much as possible cards per seat
 * without losing empty space or cards overlapping". `GrowingHandsTest` holds the rest of it.
 */
@OptIn(ExperimentalTestApi::class)
class SidePlatesFaceThePilesTest {

    @Test
    fun bothSidePlatesStandBetweenTheirCardsAndThePiles() {
        SCREENS.forEach { (screen, size) ->
            HELD.forEach { held ->
                runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                    show(holding(named(teachingSession().view.value), held), size.first, size.second)

                    // The dealt five lie wholly on the rim side of the plate, and no card lies further
                    // in than the plate's own column: a card under the plate may be a little wider.
                    val (leftPlate, leftCards) = seat(LEFT)
                    leftCards.take(DEALT).forEach { card ->
                        assertTrue(
                            card.right <= leftPlate.left + EDGE,
                            "on a $screen holding $held a dealt card $card is not rimward of the left plate $leftPlate",
                        )
                    }
                    leftCards.forEach { card ->
                        assertTrue(
                            card.center.x <= leftPlate.right + EDGE,
                            "on a $screen holding $held a card $card lies further in than the left plate $leftPlate",
                        )
                    }

                    val (rightPlate, rightCards) = seat(RIGHT)
                    rightCards.take(DEALT).forEach { card ->
                        assertTrue(
                            card.left >= rightPlate.right - EDGE,
                            "on a $screen holding $held a dealt card $card is not rimward of the right plate $rightPlate",
                        )
                    }
                    rightCards.forEach { card ->
                        assertTrue(
                            card.center.x >= rightPlate.left - EDGE,
                            "on a $screen holding $held a card $card lies further in than the right plate $rightPlate",
                        )
                    }
                }
            }
        }
    }

    /**
     * A sixth card at a side seat goes in the plate's column, on every phone, while the seats above
     * and below hold the deal.
     *
     * Not when they have grown too: on a Galaxy S23 with every seat holding six, the hands above and
     * below take two rows each and leave the middle 146 points, of which the plate is 129 — there
     * is no room under it, and the sixth card takes the column between instead. That is the "if
     * space allows it" half of the request, and `GrowingHandsTest` holds that table to lying flat.
     */
    @Test
    fun aSixthCardLiesUnderTheLeftPlateAndAboveTheRight() {
        PHONES.forEach { (phone, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(sidesHolding(named(teachingSession().view.value), SIX), size.first, size.second)

                val (leftPlate, leftCards) = seat(LEFT)
                val leftSixth = leftCards.last()
                assertTrue(
                    leftSixth.inColumnOf(leftPlate) && leftSixth.top >= leftPlate.bottom - EDGE,
                    "on a $phone the left seat's sixth card $leftSixth is not under its plate $leftPlate",
                )

                val (rightPlate, rightCards) = seat(RIGHT)
                val rightSixth = rightCards.last()
                assertTrue(
                    rightSixth.inColumnOf(rightPlate) && rightSixth.bottom <= rightPlate.top + EDGE,
                    "on a $phone the right seat's sixth card $rightSixth is not above its plate $rightPlate",
                )
            }
        }
    }

    private fun Rect.inColumnOf(plate: Rect): Boolean = left < plate.right - EDGE && right > plate.left + EDGE

    /** A seat's plate and its cards in dealt order. */
    private fun ComposeUiTest.seat(who: String): Pair<Rect, List<Rect>> {
        val cards = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                    ?.takeIf { it.startsWith("$who, card ") }
                    ?: return@mapNotNull null
                label.substringAfter(", card ").takeWhile(Char::isDigit).toInt() to node.boundsInRoot
            }
            .sortedBy { it.first }
            .map { it.second }
        assertTrue(cards.isNotEmpty(), "$who has no cards on the table")
        return plateOf(who, cards) to cards
    }

    /**
     * A seat's plate, found from its face: the largest thing round the face that holds none of the
     * seat's cards. Not "the smallest named thing", because the plate now shares a column with the
     * cards that go under it.
     */
    private fun ComposeUiTest.plateOf(who: String, cards: List<Rect>): Rect {
        var node: SemanticsNode = onNodeWithTag(faceTag(who), useUnmergedTree = true).fetchSemanticsNode()
        while (true) {
            val up = node.parent ?: break
            val bounds = up.boundsInRoot
            if (bounds.width >= PLATE_WIDEST || cards.any { bounds.contains(it.center) }) break
            node = up
        }
        return node.boundsInRoot
    }

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
                    // A room's toss-in clock under the felt, which is the tightest a table gets.
                    Box(modifier = Modifier.height(52.dp))
                }
            }
        }
        waitForIdle()
    }

    private fun named(view: PlayerView): PlayerView = view.copy(
        players = view.players.mapIndexed { i, seat -> seat.copy(name = NAMES[i], nickname = NAMES[i]) },
    )

    /** The two side seats holding [held], the seats above and below the deal. */
    private fun sidesHolding(view: PlayerView, held: Int): PlayerView = view.copy(
        players = view.players.map { seat ->
            if (seat.nickname in setOf(LEFT, RIGHT)) {
                seat.copy(cards = List(held) { i -> seat.cards[i % seat.cards.size] })
            } else {
                seat
            }
        },
    )

    private fun holding(view: PlayerView, held: Int): PlayerView = view.copy(
        players = view.players.map { seat -> seat.copy(cards = List(held) { i -> seat.cards[i % seat.cards.size] }) },
    )

    private companion object {
        val NAMES = listOf("Dusty Pebble", "Clever Harbour", "Golden Lantern", "Patient Sparrow")

        /** The next seat round sits on the left, the last on the right. */
        const val LEFT = "Clever Harbour"
        const val RIGHT = "Patient Sparrow"

        const val SIX = 6
        const val DEALT = 5
        val HELD = listOf(5, 6, 7, 8, 9, 10)
        const val EDGE = 0.5f

        /** Wider than any plate: the search for a plate stops before it reaches the whole seat. */
        const val PLATE_WIDEST = 170f

        val PHONES = mapOf(
            "Pixel 7" to (412.dp to 805.dp),
            "Galaxy S23" to (360.dp to 740.dp),
            "iPhone 17 Pro Max" to (440.dp to 860.dp),
        )

        val SCREENS = PHONES + mapOf(
            "iPad Air 11, portrait" to (820.dp to 1136.dp),
            "Galaxy Tab S9, landscape" to (1280.dp to 752.dp),
            "Desktop window" to (1920.dp to 1040.dp),
        )

        const val WINDOW_W = 2000
        const val WINDOW_H = 1200
    }
}
