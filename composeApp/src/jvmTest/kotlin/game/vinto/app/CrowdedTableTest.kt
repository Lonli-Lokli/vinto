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
 * A table nobody planned for: every seat holding nine cards.
 *
 * Five is a dealt hand, and five is what the table was drawn for. But a hand only ever grows
 * — a wrong guess, a wrong toss-in and an Ace all add a card and nothing but a Vinto ever
 * takes one away — so nine is a real position, not a stress test. What has to survive it is
 * not the look of the thing but the two properties a card game cannot be played without:
 * every card visible, and every card reachable by a thumb on its own.
 *
 * Bounds are read from the composition rather than eyeballed on a screenshot, which is the
 * only way to catch two cards *nearly* overlapping — the kind that takes two taps to hit and
 * gets blamed on the player.
 */
@OptIn(ExperimentalTestApi::class)
class CrowdedTableTest {

    @Test
    fun everyCardOfANineCardHandIsOnTheTableAndCanBeTapped() = runComposeUiTest {
        show(crowded(dealt(), HELD))

        val hands = cardBounds().groupBy { (label, _) -> label.substringBefore(", card ") }
        assertEquals(SEATS, hands.size, "all four seats are still on the table: ${hands.keys}")

        hands.forEach { (who, cards) ->
            assertEquals(HELD, cards.size, "$who is holding all $HELD of them")

            cards.forEach { (what, box) ->
                assertTrue(box.width > 0 && box.height > 0, "$what was given no room at all")
                assertTrue(
                    box.left >= 0 && box.top >= 0 &&
                        box.right <= PHONE_W.value && box.bottom <= PHONE_H.value,
                    "$what is off the screen: $box",
                )
            }

            // The line may close up — that is what a hand of nine does — but never so far
            // that a card has no strip of its own to be tapped by.
            cards.zipWithNext { (whatA, a), (_, b) ->
                val strip = maxOf(b.left - a.left, b.top - a.top)
                assertTrue(
                    strip >= STRIP,
                    "only ${strip.toInt()}dp of $whatA is left showing, under a ${STRIP.toInt()}dp thumb",
                )
            }
        }

        // Between hands there is no crowding at all: one seat's cards never touch another's.
        val everyCard = hands.values.flatten()
        everyCard.forEachIndexed { i, (whatA, a) ->
            everyCard.drop(i + 1)
                .filter { (whatB, _) -> whatB.substringBefore(",") != whatA.substringBefore(",") }
                .forEach { (whatB, b) ->
                    assertTrue(!a.touches(b), "$whatA is sitting on $whatB: $a vs $b")
                }
        }
    }

    /**
     * And the ordinary case is not crowded: the hand the player reads and taps — their own —
     * is five separate cards with daylight between them, on the smallest screen tested.
     *
     * The three hands opposite may close up a little on a short phone, and should: they are
     * face down, they are counted rather than read, and a hand of cards overlapping is what a
     * hand of cards does. Yours is the one that must never need a second look.
     */
    @Test
    fun yourOwnDealtHandIsFiveSeparateCards() = runComposeUiTest {
        show(dealt())

        val mine = cardBounds().filter { (label, _) -> label.startsWith(ME) }
        assertEquals(DEALT, mine.size, "five cards, dealt")

        mine.zipWithNext { (whatA, a), (whatB, b) ->
            assertTrue(!a.touches(b), "$whatA and $whatB are touching: $a, $b")
        }
    }

    private fun Rect.touches(other: Rect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** The table, at the size of an ordinary phone. */
    private fun ComposeUiTest.show(view: PlayerView) {
        setContent {
            VintoTheme {
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
    private fun dealt(): PlayerView =
        teachingSession().view.value

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

        /** The narrowest strip of a card a thumb can be asked to find on its own. */
        const val STRIP = 24f

        /**
         * A phone, at the largest that fits the test's own window — which is 1024x768, so
         * this is a small handset rather than a flagship. That suits it: whatever survives
         * here has room to spare on a taller screen.
         */
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
