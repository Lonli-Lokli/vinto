package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.HeaderHeight
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableSizes
import game.vinto.app.game.TableState
import game.vinto.app.game.railWidth
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The table on a phone lying on its side — [CrowdedTableTest]'s bar, held in landscape.
 *
 * Landscape is not a second design, it is the same table with the rail moved: the felt keeps
 * its four-sided arrangement in the left of the screen and the controls stand in a column on
 * the right, where the portrait rail's own *minimum height* would have been most of the
 * screen. What these tests hold is the consequence of that move: everything stays where it is
 * claimed to be — every card on the felt's side of the join, all four seats present, and the
 * hand the player reads and taps still legible.
 */
@OptIn(ExperimentalTestApi::class)
class LandscapeTableTest {

    @Test
    fun aWideScreenStandsTheRailBesideTheFeltATallOneKeepsItUnder() {
        val wide = TableLayout.forScreen(LAND_W, LAND_H)
        assertTrue(wide.landscape, "a 740x411 screen is landscape")
        assertTrue(
            wide.railWidth >= 240.dp && wide.railWidth <= 300.dp,
            "the side rail is clamped like the bottom one: ${wide.railWidth}",
        )
        assertEquals(
            TableSizes.forHeight(LAND_H - HeaderHeight),
            wide.sizes,
            "landscape cards are sized from the height beside the rail, not under it",
        )

        val tall = TableLayout.forScreen(LAND_H, LAND_W)
        assertFalse(tall.landscape, "the same phone upright is portrait")
        assertEquals(
            TableLayout.forScreen(LAND_W),
            tall,
            "the two-argument portrait answer is the one-argument answer",
        )
    }

    @Test
    fun everyCardStaysOnTheFeltSideOfTheJoin() {
        show(dealt())

        val join = (LAND_W - railWidth(LAND_W)).value
        val cards = cardBounds()
        assertTrue(cards.isNotEmpty(), "the table dealt no cards at all")

        cards.forEach { (what, box) ->
            assertTrue(box.width > 0 && box.height > 0, "$what was given no room at all")
            assertTrue(
                box.left >= 0 && box.top >= 0 &&
                    box.right <= LAND_W.value && box.bottom <= LAND_H.value,
                "$what is off the screen: $box",
            )
            assertTrue(
                box.right <= join + 1f,
                "$what is lying under the rail: $box crosses the join at $join",
            )
        }

        val hands = cards.groupBy { (label, _) -> label.substringBefore(", card ") }
        assertEquals(SEATS, hands.size, "all four seats are on the landscape table: ${hands.keys}")
    }

    @Test
    fun yourOwnDealtHandIsStillFiveSeparateCards() {
        // The hand the player reads and taps keeps the portrait bar exactly: five cards with
        // daylight between them. The opponents' hands may close up — they are face down and
        // counted rather than read — but yours must never need a second look.
        show(dealt())

        val mine = cardBounds().filter { (label, _) -> label.startsWith(ME) }
        assertEquals(DEALT, mine.size, "five cards, dealt")

        mine.zipWithNext { (whatA, a), (whatB, b) ->
            assertTrue(!a.touches(b), "$whatA and $whatB are touching: $a, $b")
        }
    }

    @Test
    fun everySeatOfACrowdedTableSurvivesTheRotation() {
        // Nine cards a hand is an ordinary way to lose, not a stress test — see
        // CrowdedTableTest. On a rotated phone the side seats have the least height they
        // will ever have, and what must survive is presence: four seats, nine cards each,
        // every one of them drawn somewhere on the screen with a strip of its own.
        show(crowded(dealt(), HELD))

        val hands = cardBounds().groupBy { (label, _) -> label.substringBefore(", card ") }
        assertEquals(SEATS, hands.size, "all four seats are still on the table: ${hands.keys}")

        hands.forEach { (who, cards) ->
            assertEquals(HELD, cards.size, "$who is holding all $HELD of them")

            cards.forEach { (what, box) ->
                assertTrue(box.width > 0 && box.height > 0, "$what was given no room at all")
                assertTrue(
                    box.left >= 0 && box.top >= 0 &&
                        box.right <= LAND_W.value && box.bottom <= LAND_H.value,
                    "$what is off the screen: $box",
                )
            }

            // The line may close up hard — a column of nine in a rotated phone's height is
            // the tightest squeeze on the table — but every card keeps a visible strip.
            cards.zipWithNext { (whatA, a), (_, b) ->
                val strip = maxOf(b.left - a.left, b.top - a.top)
                assertTrue(
                    strip >= STRIP,
                    "only ${strip.toInt()}dp of $whatA is left showing in landscape",
                )
            }
        }
    }

    private fun Rect.touches(other: Rect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** The table, on a phone lying on its side. */
    private fun ComposeUiTest.show(view: PlayerView) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(LAND_W, LAND_H)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(LAND_W, LAND_H),
                        onMove = {},
                        onHelp = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()
    }

    /** Every card on the table, by the description a screen reader would read out. */
    private fun ComposeUiTest.cardBounds(): List<Pair<String, Rect>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                val label = node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.firstOrNull()
                    ?.takeIf { it.contains(", card ") }
                label?.let { it to node.boundsInRoot }
            }

    /** A real deal, so the hands below are grown from real cards rather than invented ones. */
    private fun dealt(): PlayerView = teachingSession().view.value

    /** The same table, with every hand grown to [held] cards. */
    private fun crowded(view: PlayerView, held: Int): PlayerView = view.copy(
        players = view.players.map { seat ->
            seat.copy(cards = List(held) { i -> seat.cards[i % seat.cards.size] })
        },
    )

    private companion object {
        const val HELD = 9
        const val DEALT = 5
        const val ME = "You,"
        const val SEATS = 4

        /**
         * The narrowest strip of a card that still reads as a card. Lower than the portrait
         * bar on purpose: a rotated phone's height is the tightest room the table is ever
         * given, and a face-down hand of nine is counted rather than read — but a card that
         * has vanished under its neighbour is a card the game has lost, at any size.
         */
        const val STRIP = 10f

        /**
         * The portrait tests' phone, rotated: [CrowdedTableTest] holds 411x740, this holds
         * 740x411. Same handset, same bar, on its side.
         */
        val LAND_W = 740.dp
        val LAND_H = 411.dp
    }
}
