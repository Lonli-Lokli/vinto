package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Anchor
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A hand does not shuffle along while one of its cards is in the air.
 *
 * The table steps to the position a move produced *before* the cards fly, so that a card
 * landing has somewhere to land — which means a hand that just lost a card has already closed
 * up by the time the card starts moving. Left alone, the other four slide sideways underneath
 * a card that is still travelling, and a card thrown from the last slot sets off from a place
 * that no longer exists.
 *
 * The web app draws a transparent card in the space. This holds the space itself: the hand
 * keeps a gap wherever a flight has left, until it lands.
 */
@OptIn(ExperimentalTestApi::class)
class HandGapTest {

    @Test
    fun aHandKeepsItsShapeWhileACardIsLeavingIt() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId

        // The same hand a card short, as the table would show it the instant a toss-in lands.
        val short = whole.copy(
            players = whole.players.map { seat ->
                if (seat.id == me) {
                    seat.copy(cards = seat.cards.filterIndexed { i, _ -> i != GONE })
                } else {
                    seat
                }
            },
        )

        val settled = handWidth(whole, Stage())
        val midFlight = handWidth(short, leaving(me, GONE))
        val closedUp = handWidth(short, Stage())

        assertEquals(
            settled,
            midFlight,
            "the hand holds its shape while the card is in the air",
        )
        assertTrue(closedUp < settled, "and closes up once it has landed: $closedUp vs $settled")
    }

    /**
     * A swap keeps the hand exactly as wide as it was.
     *
     * The card that leaves and the card that arrives share a slot, so the hand neither shrinks
     * nor grows — and holding a gap open for the departure as well inserted a slot the hand
     * does not have, drew the arriving card one place along while it was still in the air, and
     * pushed every anchor after it off by one.
     */
    @Test
    fun aSwapDoesNotWidenTheHand() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId

        val settled = handWidth(whole, Stage())
        val swapping = handWidth(whole, swapping(me, GONE))

        assertEquals(settled, swapping, "a card leaving and another arriving is one slot, not two")
    }

    /**
     * And while it is only ABOUT to leave — shown where it lies, before any flight.
     *
     * A King's named card pops out and is shown, and the King is answered, before the card goes
     * anywhere: several scenes in which the table has already stepped to a hand a card short and
     * nothing is flying yet. The gap used to be held only by a flight, so the hand closed up the
     * instant the table stepped and the held-up card, measured against its old place, caught up a
     * frame later — the whole hand hopping sideways under it. Seen, frame by frame, in the App
     * Store preview.
     */
    @Test
    fun aHandKeepsItsShapeWhileACardWaitsToLeaveIt() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId
        val short = whole.copy(
            players = whole.players.map { seat ->
                if (seat.id == me) seat.copy(cards = seat.cards.filterIndexed { i, _ -> i != GONE }) else seat
            },
        )

        val settled = handWidth(whole, Stage())
        val waiting = handWidth(short, Stage().apply { departing[Anchor.Seat(me, GONE)] = CardView.Hidden })

        assertEquals(settled, waiting, "the hand closed up before the card had left it")
    }

    /** The same promise for a swap still makes one slot, not two: a card is arriving there too. */
    @Test
    fun aSwapAboutToHappenDoesNotWidenTheHand() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId
        val seat = Anchor.Seat(me, GONE)

        val settled = handWidth(whole, Stage())
        val aboutTo = handWidth(
            whole,
            Stage().apply {
                departing[seat] = CardView.Hidden
                expecting[seat] = CardView.Hidden
            },
        )

        assertEquals(settled, aboutTo, "a card about to leave and another about to arrive is one slot")
    }

    /**
     * And once the card has gone, the hand slides together rather than snapping shut.
     *
     * The gap used to vanish in one frame when the flight landed, and every card after it jumped
     * sideways by a slot — the one movement on the table with no animation at all, found by a
     * frame-by-frame scan of the App Store preview. The gap now closes over a few frames.
     */
    @Test
    fun aHandSlidesTogetherOnceTheCardHasGone() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId
        val short = whole.copy(
            players = whole.players.map { seat ->
                if (seat.id == me) seat.copy(cards = seat.cards.filterIndexed { i, _ -> i != GONE }) else seat
            },
        )
        val stage = leaving(me, GONE)
        mainClock.autoAdvance = false
        setContent {
            VintoTheme {
                CompositionLocalProvider(LocalStage provides stage) {
                    Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                        TableScreen(
                            state = TableState(short, tableFor(short), null, emptyList(), 1),
                            layout = TableLayout.forScreen(PHONE_H),
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                        )
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        val open = mine()

        stage.flying.clear()
        val widths = listOf(open) + List(FRAMES_TO_CLOSE) {
            mainClock.advanceTimeByFrame()
            mine()
        }
        val closed = widths.last()
        val biggestStep = widths.zipWithNext { a, b -> a - b }.max()

        assertTrue(closed < open, "the hand never closed up: $widths")
        assertTrue(biggestStep <= STEP_PX, "the hand snapped shut rather than sliding: $widths")
    }

    private fun ComposeUiTest.mine(): Int {
        val cards = cards().filter { (label, _) -> label.startsWith(ME) }.map { it.second }
        return if (cards.isEmpty()) 0 else (cards.maxOf { it.right } - cards.minOf { it.left }).toInt()
    }

    /** A stage mid-swap: one card leaving a slot and another landing in the same one. */
    private fun swapping(playerId: String, position: Int) = Stage().apply {
        val seat = Anchor.Seat(playerId, position)
        flying += Stage.Flight(
            id = 1,
            card = CardView.Hidden,
            from = Offset.Zero,
            to = Offset.Zero,
            landingAt = Anchor.Discard,
            leftFrom = seat,
            fromCard = Size.Zero,
            toCard = Size.Zero,
            fromTurn = 0f,
            toTurn = 0f,
        )
        flying += Stage.Flight(
            id = 2,
            card = CardView.Hidden,
            from = Offset.Zero,
            to = Offset.Zero,
            landingAt = seat,
            leftFrom = Anchor.Pending,
            fromCard = Size.Zero,
            toCard = Size.Zero,
            fromTurn = 0f,
            toTurn = 0f,
        )
    }

    /** A stage with one card in the air, having left [position] of [playerId]'s hand. */
    private fun leaving(playerId: String, position: Int) = Stage().apply {
        flying += Stage.Flight(
            id = 1,
            card = CardView.Hidden,
            from = Offset.Zero,
            to = Offset.Zero,
            landingAt = Anchor.Discard,
            leftFrom = Anchor.Seat(playerId, position),
            fromCard = Size.Zero,
            toCard = Size.Zero,
            fromTurn = 0f,
            toTurn = 0f,
        )
    }

    /** How wide the player's own hand is drawn, from the first card's left to the last's right. */
    private fun handWidth(view: PlayerView, stage: Stage): Int {
        var width = 0
        runComposeUiTest {
            setContent {
                VintoTheme {
                    CompositionLocalProvider(LocalStage provides stage) {
                        Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                            TableScreen(
                                state = TableState(view, tableFor(view), null, emptyList(), 1),
                                layout = TableLayout.forScreen(PHONE_H),
                                onMove = {},
                                onHelp = {},
                                onSettings = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()

            val mine = cards().filter { (label, _) -> label.startsWith(ME) }.map { it.second }
            width = if (mine.isEmpty()) 0 else (mine.maxOf { it.right } - mine.minOf { it.left }).toInt()
        }
        return width
    }

    private fun ComposeUiTest.cards(): List<Pair<String, Rect>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.firstOrNull()
                    ?.takeIf { it.contains(", card ") }
                    ?.let { it to node.boundsInRoot }
            }

    private companion object {
        const val ME = "You,"
        const val GONE = 2
        const val FRAMES_TO_CLOSE = 40

        /** A fifth of a slot: a frame that moves the hand further than this is a jump, not a slide. */
        const val STEP_PX = 12
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
