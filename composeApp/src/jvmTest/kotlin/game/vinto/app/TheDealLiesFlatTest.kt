package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CardScale
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A dealt table, online, on the phones people hold: five cards in front of everybody, lying flat.
 *
 * Reported from a phone with a screenshot of the first moment of a room's first round. The two
 * side players' cards sat on top of each other down the edges of the felt, and the player's own
 * five had gone onto two rows — two over three — beside a plate reading "Dusty Pebb…".
 *
 * They were one fault. The plate was a pill as wide as its name allowed, so on a phone about 400
 * points across the five cards beside it did not fit and wrapped; the second row took a card's
 * height from the middle of the felt; and the side columns, which live in that middle, had five
 * cards' worth of tap targets to lay in about four cards' worth of height. Online is where it
 * shows, because the room's toss-in clock keeps a row under the felt that a solo game does not.
 *
 * Laid out as `RemoteGameScreen` lays it: the table's sizes decided from the whole screen, and
 * the clock's row taken from under it.
 */
@OptIn(ExperimentalTestApi::class)
class TheDealLiesFlatTest {

    /**
     * No two of one seat's cards overlap at the deal. A dealt hand is five cards and five is what
     * every seat was drawn for, so nothing about it should need to give way yet.
     *
     * Judged on the **pictures**, not the tap footprints. A card smaller than a thumb reserves a
     * 44-point box round itself, and on a short phone the side columns' boxes may overlap by a
     * few points while every card is still drawn apart with daylight between — which is fine, and
     * each still keeps far more than the 24 points of its own a thumb needs (`CrowdedTableTest`).
     * What the report showed was the other thing: cards drawn on top of each other.
     */
    @Test
    fun atTheDealNoSeatsCardsLieOnEachOther() {
        PHONES.forEach { (phone, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(size.first, size.second)
                val sizes = TableLayout.forScreen(size.first, size.second).sizes
                val hands = cardBounds().groupBy { (label, _) -> label.substringBefore(", card ") }
                assertEquals(SEATS, hands.size, "on a $phone the table has ${hands.keys}")
                hands.forEach { (who, cards) ->
                    assertEquals(DEALT, cards.size, "on a $phone $who holds ${cards.size}")
                    val scale = if (who == ME) sizes.mine else sizes.theirs
                    val drawn = cards.map { (label, box) -> label to box.picture(scale) }
                    drawn.forEachIndexed { i, (a, boxA) ->
                        drawn.drop(i + 1).forEach { (b, boxB) ->
                            assertTrue(!boxA.overlaps(boxB), "on a $phone $a lies on $b: $boxA, $boxB")
                        }
                    }
                }
            }
        }
    }

    /** Your own five, on one row beside your plate — not two over three. */
    @Test
    fun yourDealtHandIsOneRow() {
        (PHONES + NARROW).forEach { (phone, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(size.first, size.second)
                val mine = cardBounds().filter { (label, _) -> label.startsWith("$ME,") }
                assertEquals(DEALT, mine.size)
                val rows = mine.map { (_, box) -> box.top.toInt() }.distinct()
                assertEquals(1, rows.size, "on a $phone your five cards are on ${rows.size} rows")
            }
        }
    }

    /**
     * Every name on the felt is drawn whole — the text shrinks before it is cut.
     *
     * A minted name is two words and the room guarantees two players' differ, but only a reader
     * who can see both words can tell "Dusty Pebble" from "Dusty Rowan".
     */
    @Test
    fun everyNameOnTheFeltIsDrawnWhole() {
        (PHONES + NARROW + TABLETS).forEach { (phone, size) ->
            runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
                show(size.first, size.second)
                NAMES.forEach { name ->
                    val node = onAllNodes(saying(name)).fetchSemanticsNodes().firstOrNull()
                        ?: error("on a $phone nobody on the felt is called $name")
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    assertTrue(layouts.isNotEmpty(), "on a $phone $name has no text layout")
                    assertTrue(layouts.none { it.cutShort() }, "on a $phone $name is cut short on the felt")
                    assertTrue(layouts.none { it.breaksAWord() }, "on a $phone $name is broken inside a word")
                }
            }
        }
    }

    private fun TextLayoutResult.cutShort(): Boolean =
        (0 until lineCount).any { isLineEllipsized(it) } ||
            didOverflowHeight ||
            getLineEnd(lineCount - 1, visibleEnd = true) < layoutInput.text.length

    private fun TextLayoutResult.breaksAWord(): Boolean {
        val text = layoutInput.text
        return (0 until lineCount - 1).any { line ->
            val end = getLineEnd(line)
            end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()
        }
    }

    /**
     * The card as drawn inside its footprint: centred, at the scale's size, turned a quarter when
     * the footprint lies on its side (a side seat's, which is wider than it is tall).
     */
    private fun Rect.picture(scale: CardScale): Rect {
        val (wide, tall) = scale.width.value to scale.height.value
        val (w, h) = if (width > height) tall to wide else wide to tall
        return Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
    }

    /** The text node that says exactly [name]. */
    private fun saying(name: String) = SemanticsMatcher("names $name") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == name } == true
    }

    private fun Rect.overlaps(other: Rect): Boolean =
        left < other.right - EDGE && other.left < right - EDGE && top < other.bottom - EDGE && other.top < bottom - EDGE

    /** The table as a room draws it: sized from the whole screen, with the clock's row under it. */
    private fun ComposeUiTest.show(wide: Dp, high: Dp) {
        val view = named(teachingSession().view.value)
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

    /** The four seats wearing the names from the report: two minted, two bots. */
    private fun named(view: PlayerView): PlayerView = view.copy(
        players = view.players.mapIndexed { i, seat -> seat.copy(name = NAMES[i], nickname = NAMES[i]) },
    )

    private fun ComposeUiTest.cardBounds(): List<Pair<String, Rect>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                val label = node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.firstOrNull()
                    ?.takeIf { it.contains(", card ") }
                label?.let { it to node.boundsInRoot }
            }

    private companion object {
        val NAMES = listOf("Dusty Pebble", "Dusty Rowan", "Tide", "Dune")
        const val ME = "Dusty Pebble"
        const val SEATS = 4
        const val DEALT = 5

        /** A hair, for bounds that meet exactly at a shared edge. */
        const val EDGE = 0.5f

        /** `TossClockHeight` in `RoomScreen`: the row a room keeps under its felt. */
        val CLOCK_ROW = 52.dp

        /**
         * The app's own area on each — the screen less the status bar and the home indicator —
         * which is what the table is actually given.
         */
        val PHONES = listOf(
            "Pixel 7" to (412.dp to 805.dp),
            "iPhone 17 Pro" to (402.dp to 778.dp),
            "iPhone 17 Pro Max" to (440.dp to 860.dp),
            "Galaxy S23" to (360.dp to 740.dp),
        )

        /** Narrow enough that the question is only whether your own hand stays one row. */
        val NARROW = listOf("iPhone SE" to (375.dp to 667.dp))

        val TABLETS = listOf("iPad, portrait" to (820.dp to 1150.dp))

        /**
         * The test's own window, large enough for every screen above. The default one is 1024 by
         * 768, and a phone taller than that is quietly given 768 — a shorter phone than it says.
         */
        const val WINDOW_W = 900
        const val WINDOW_H = 1200
    }
}
