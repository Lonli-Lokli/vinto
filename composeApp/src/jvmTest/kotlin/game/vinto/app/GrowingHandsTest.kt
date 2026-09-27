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
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CARD_PICTURE
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A hand that grows past the deal keeps the deal where it was.
 *
 * Five is the deal and not the limit: a wrong guess, a wrong toss-in and an Ace each add a card,
 * so six, seven or eight in front of somebody is an ordinary way to be losing. Reported from a
 * phone with a screenshot of the player's own eight: six along the bottom and two above them —
 * the line filled as far as it would go, so the sixth card joined the dealt five and every card
 * after it moved along. The table the player had learnt, five in a row, was gone.
 *
 * So a line holds five, the dealt five first, and a sixth card starts the next line — for every
 * seat, the two at the sides included. The row nearest a seat's own rim is the dealt one: your
 * five along the bottom and the extras above, the seat opposite's along the top and the extras
 * below it, a side seat's in the column beside its plate and the extras further in. And a hand
 * that grows must still lie flat and still be a thumb's target per card: no card drawn on
 * another, and 24 points of each its own (WCAG 2.2 AA, SC 2.5.8), on every seat.
 *
 * Laid out as a room lays it — the toss-in clock's row under the felt — on the phones people
 * hold, every seat holding the same number of cards, which is the fullest a table gets.
 */
@OptIn(ExperimentalTestApi::class)
class GrowingHandsTest {

    @Test
    fun theDealtFiveKeepTheFirstRowAndTheRestStartTheNext() = eachTable { phone, held, hands ->
        hands.forEach { (who, cards) ->
            val turned = cards.first().turned
            val line = { card: Card -> if (turned) card.box.center.x else card.box.center.y }
            val dealt = cards.filter { it.number <= DEALT }
            val extra = cards.filter { it.number > DEALT }
            assertEquals(
                1,
                dealt.map { line(it).toInt() }.distinct().size,
                "on a $phone holding $held, $who's dealt five are not one row",
            )
            assertEquals(
                1,
                extra.map { line(it).toInt() }.distinct().size,
                "on a $phone holding $held, $who's extra cards are not one row",
            )
            // The dealt row is the one nearest the seat's own rim, so the extras sit further in.
            val inward = line(extra.first()) - line(dealt.first())
            val rimward = rimSide(cards, phone)
            assertTrue(
                inward * rimward < 0,
                "on a $phone holding $held, $who's extra cards went on the rim side of the dealt five",
            )
            // And the dealt five are in their dealt order along it, counted from the seat's plate.
            val along = { card: Card -> if (turned) card.box.center.y else card.box.center.x }
            val order = dealt.sortedBy { it.number }.map(along)
            assertTrue(
                order == order.sorted() || order == order.sortedDescending(),
                "on a $phone $who's dealt five are out of order",
            )
        }
    }

    @Test
    fun aGrowingHandNeverLiesOnItself() = eachTable { phone, held, hands ->
        hands.forEach { (who, cards) ->
            cards.forEachIndexed { i, a ->
                cards.drop(i + 1).forEach { b ->
                    assertTrue(
                        !a.picture.overlaps(b.picture),
                        "on a $phone holding $held, $who's card ${a.number} lies on card ${b.number}: ${a.box}, ${b.box}",
                    )
                }
            }
        }
        // And no seat's cards touch another seat's.
        val all = hands.flatMap { (who, cards) -> cards.map { who to it } }
        all.forEachIndexed { i, (whoA, a) ->
            all.drop(i + 1).filter { it.first != whoA }.forEach { (whoB, b) ->
                assertTrue(
                    !a.box.overlaps(b.box),
                    "on a $phone holding $held, $whoA's card ${a.number} is on $whoB's card ${b.number}",
                )
            }
        }
    }

    @Test
    fun everyCardOfAGrowingHandKeepsAThumbOfItsOwn() = eachTable { phone, held, hands ->
        hands.forEach { (who, cards) ->
            cards.forEach { card ->
                assertTrue(
                    card.box.width >= STRIP && card.box.height >= STRIP,
                    "on a $phone holding $held, $who's card ${card.number} is ${card.box.width}x${card.box.height}",
                )
            }
            // Along each line, what a card keeps of its own is the step to the next one over it.
            cards.groupBy { it.lineKey() }.values.forEach { line ->
                line.sortedBy { it.along() }.zipWithNext { a, b ->
                    val step = abs(b.along() - a.along())
                    assertTrue(
                        step >= STRIP,
                        "on a $phone holding $held, only ${step.toInt()}pt of $who's card ${a.number} is its own",
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ the fixtures

/** One card: its number in the hand, the footprint it reserves, and the picture drawn inside it. */
    private data class Card(val number: Int, val box: Rect, val turned: Boolean, private val drawn: IntSize) {
        /**
         * The card as drawn: its picture's own size, centred on its footprint, lying on its side
         * at a side seat. Read from the picture's size rather than its bounds, which are clipped
         * to the footprint and do not count the turn.
         */
        val picture: Rect
            get() {
                val (w, h) = if (turned) {
                    drawn.height.toFloat() to drawn.width.toFloat()
                } else {
                    drawn.width.toFloat() to
                        drawn.height.toFloat()
                }
                return Rect(box.center.x - w / 2, box.center.y - h / 2, box.center.x + w / 2, box.center.y + h / 2)
            }
        fun lineKey(): Int = (if (turned) box.center.x else box.center.y).toInt()
        fun along(): Float = if (turned) box.center.y else box.center.x
    }

    private fun Rect.overlaps(other: Rect): Boolean =
        left < other.right - EDGE && other.left < right - EDGE && top < other.bottom - EDGE && other.top < bottom - EDGE

    /** -1 when the seat's rim is towards smaller coordinates along its lines' cross axis, +1 otherwise. */
    private fun rimSide(cards: List<Card>, phone: String): Int {
        val (wide, high) = PHONES.getValue(phone)
        val turned = cards.first().turned
        val middle = cards.map { if (turned) it.box.center.x else it.box.center.y }.average()
        val half = if (turned) wide.value / 2 else high.value / 2
        return if (middle < half) -1 else 1
    }

    private fun eachTable(check: (phone: String, held: Int, hands: Map<String, List<Card>>) -> Unit) {
        PHONES.forEach { (phone, size) ->
            HELD.forEach { held ->
                runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                    show(holding(named(teachingSession().view.value), held), size.first, size.second)
                    val hands = cards().groupBy { it.who }
                        .mapValues { (who, list) ->
                            list.map {
                                Card(
                                    it.number,
                                    it.box,
                                    turned = who in SIDES,
                                    it.drawn,
                                )
                            }
                        }
                    assertEquals(SEATS, hands.size, "on a $phone holding $held the table has ${hands.keys}")
                    hands.forEach { (who, cards) ->
                        assertEquals(
                            held,
                            cards.size,
                            "on a $phone $who holds ${cards.size}",
                        )
                    }
                    check(phone, held, hands)
                }
            }
        }
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
                    Box(modifier = Modifier.height(CLOCK_ROW))
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

    private class Found(val who: String, val number: Int, val box: Rect, val drawn: IntSize)

    /** Every card on the table, by the words a screen reader says for it, with the picture inside it. */
    private fun ComposeUiTest.cards(): List<Found> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                val label = node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.firstOrNull()
                    ?.takeIf { ", card " in it }
                    ?: return@mapNotNull null
                val picture = node.children.first { it.config.getOrNull(SemanticsProperties.TestTag) == CARD_PICTURE }
                val number = label.substringAfter(", card ").takeWhile(Char::isDigit).toInt()
                Found(label.substringBefore(", card "), number, node.boundsInRoot, picture.size)
            }

    private companion object {
        val NAMES = listOf("Dusty Pebble", "Clever Harbour", "Golden Lantern", "Patient Sparrow")
        const val ME = "Dusty Pebble"

        /** The seats at the sides of the felt, whose cards lie turned: the next seat round and the last. */
        val SIDES = setOf("Clever Harbour", "Patient Sparrow")
        const val SEATS = 4
        const val DEALT = 5
        val HELD = listOf(6, 7, 8)

        /** WCAG 2.2 AA, SC 2.5.8: a target of at least 24 by 24. */
        const val STRIP = 24f
        const val EDGE = 0.5f

        val CLOCK_ROW = 52.dp
        const val WINDOW_W = 900
        const val WINDOW_H = 1000

        /**
         * Every phone with at least 740 points for the app, which is today's smallest phones. An
         * iPhone SE (667 points, discontinued) cannot hold a second row anywhere and still give
         * the side seats 24-point targets: online its felt is 331 points, the two plates above
         * and below take 81 each, and five side targets need 146 — so it is held to the dealt
         * table (`TheDealLiesFlatTest`) and not to this.
         */
        val PHONES = mapOf(
            "Pixel 7" to (412.dp to 805.dp),
            "iPhone 17 Pro" to (402.dp to 778.dp),
            "iPhone 17 Pro Max" to (440.dp to 860.dp),
            "Galaxy S23" to (360.dp to 740.dp),
        )
    }
}
