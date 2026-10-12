package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import game.vinto.client.Say
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * When the table changes shape, it moves there rather than jumping.
 *
 * Opening a fold, closing it, turning a tablet, dragging a desktop window across the line between
 * a phone and a tablet: the screen was a different arrangement in the next frame, with every seat
 * somewhere else and the controls on another edge. The owner's request, 2026-10-12: "adding
 * animations like our names slowly moving into new position, plus some good animation of moving
 * controls part".
 *
 * So everything starts where it was — as a share of the old screen, laid over the new one — and
 * travels to where it now is. The hands and the piles arrive first; the plates, with the names on
 * them, follow slowly and settle beside their cards; and the rail swings round the felt's corner to
 * its new edge. Under reduced motion the table is simply there. A window resized by a few points
 * is the same table and does not set anything moving.
 */
@OptIn(ExperimentalTestApi::class)
class TheTableGlidesTest {

    @Test
    fun openingTheFoldGlidesTheTableToItsNewShape() = runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
        var screen by mutableStateOf(CLOSED)
        show({ screen }, reduced = false)
        val before = face(LEFT)

        screen = OPEN
        mainClock.advanceTimeByFrame()
        val first = face(LEFT)
        val railFirst = button(CHOICE)
        mainClock.advanceTimeBy(SETTLED_MS)
        val after = face(LEFT)
        val railAfter = button(CHOICE)

        // Where the plate was, as a share of the closed screen, laid over the open one.
        val start = before.center.scaledFrom(CLOSED, OPEN)
        assertTrue(
            after.center.distance(start) > MOVED,
            "the test is not testing anything: $LEFT's plate is where it was",
        )
        assertTrue(
            first.center.distance(start) < first.center.distance(after.center),
            "$LEFT's plate jumped: it started at ${first.center}, nearer its new place ${after.center} than " +
                "its old one $start",
        )
        assertTrue(
            railFirst.center.distance(railAfter.center) > MOVED,
            "the rail jumped to its new edge: it started at ${railFirst.center}, already at ${railAfter.center}",
        )
    }

    /** "Our names slowly moving into new position": the cards are home while the plates still travel. */
    @Test
    fun theNamesArriveAfterTheirCards() = runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
        var screen by mutableStateOf(CLOSED)
        show({ screen }, reduced = false)

        screen = OPEN
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(CARDS_HOME_MS)
        val cardThen = card(LEFT)
        val plateThen = face(LEFT)
        mainClock.advanceTimeBy(SETTLED_MS)

        assertTrue(
            cardThen.center.distance(card(LEFT).center) < STILL,
            "$LEFT's first card is still moving after $CARDS_HOME_MS ms",
        )
        assertTrue(
            plateThen.center.distance(face(LEFT).center) > STILL,
            "$LEFT's plate arrived with its cards rather than slowly after them",
        )
    }

    @Test
    fun underReducedMotionTheTableIsSimplyThere() = runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
        var screen by mutableStateOf(CLOSED)
        show({ screen }, reduced = true)

        screen = OPEN
        mainClock.advanceTimeByFrame()
        val first = face(LEFT)
        val railFirst = button(CHOICE)
        mainClock.advanceTimeBy(SETTLED_MS)

        assertTrue(first.center.distance(face(LEFT).center) < STILL, "under reduced motion the plate travelled")
        assertTrue(railFirst.center.distance(button(CHOICE).center) < STILL, "under reduced motion the rail travelled")
    }

    @Test
    fun aWindowResizedByAFewPointsIsTheSameTable() = runDesktopComposeUiTest(WINDOW_W, WINDOW_H) {
        var screen by mutableStateOf(OPEN)
        show({ screen }, reduced = false)

        screen = OPEN_NUDGED
        mainClock.advanceTimeByFrame()
        val first = face(LEFT)
        mainClock.advanceTimeBy(SETTLED_MS)

        assertTrue(
            first.center.distance(face(LEFT).center) < STILL,
            "a window a few points larger set the table moving",
        )
    }

    private fun ComposeUiTest.show(screen: () -> Pair<Dp, Dp>, reduced: Boolean) {
        val (view, said) = drawn()
        mainClock.autoAdvance = false
        setContent {
            val (wide, high) = screen()
            CompositionLocalProvider(LocalReducedMotion provides reduced) {
                VintoTheme {
                    Box(modifier = Modifier.size(wide, high)) {
                        TableScreen(
                            state = TableState(view, tableFor(view), null, said, 1),
                            layout = TableLayout.forScreen(wide, high),
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                            onLeave = {},
                        )
                    }
                }
            }
        }
        mainClock.advanceTimeBy(SETTLED_MS)
        waitForIdle()
    }

    private fun ComposeUiTest.face(who: String): Rect =
        onNodeWithTag(faceTag(who), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.card(who: String): Rect = described("$who, card 1")

    private fun ComposeUiTest.button(label: String): Rect = described(label)

    private fun ComposeUiTest.described(label: String): Rect =
        onAllNodes(
            SemanticsMatcher("names $label") { node ->
                val said = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                said.any { it.startsWith(label) }
            },
        ).fetchSemanticsNodes().first().boundsInRoot

    /** The same share of [to] as this point is of [from], in pixels at the test's density of 1. */
    private fun Offset.scaledFrom(from: Pair<Dp, Dp>, to: Pair<Dp, Dp>): Offset =
        Offset(x / from.first.value * to.first.value, y / from.second.value * to.second.value)

    private fun Offset.distance(other: Offset): Float = abs(x - other.x) + abs(y - other.y)

    /** A card drawn — swap or discard — so the rail holds two choices, with the side seats named. */
    private fun drawn(): Pair<PlayerView, List<Say>> {
        lateinit var view: PlayerView
        lateinit var said: List<Say>
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            view = session.view.value
            said = session.log.value
        }
        return view.copy(
            players = view.players.mapIndexed { i, seat -> seat.copy(name = NAMES[i], nickname = NAMES[i]) },
        ) to said
    }

    private companion object {
        val NAMES = listOf("Dusty Pebble", "Clever Harbour", "Golden Lantern", "Patient Sparrow")
        const val LEFT = "Clever Harbour"
        const val CHOICE = "Swap Cards"

        /** The iPhone Duo, closed and open: Apple's screenshot sizes at 3x. */
        val CLOSED = 466.dp to 678.dp
        val OPEN = 951.dp to 669.dp
        val OPEN_NUDGED = 960.dp to 676.dp

        /** By when the cards have arrived and the names have not. */
        const val CARDS_HOME_MS = 700L
        const val SETTLED_MS = 4_000L

        /** Far enough to call it a move, and near enough to call it still, in pixels. */
        const val MOVED = 40f
        const val STILL = 2f

        const val WINDOW_W = 1200
        const val WINDOW_H = 900
    }
}
