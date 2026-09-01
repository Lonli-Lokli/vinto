package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CardStage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Anchor
import game.vinto.client.Beat
import game.vinto.client.Frame
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.SelectActionTargetPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A card in the air is the card it becomes at rest — to the pixel.
 *
 * The handoff between the overlay and the table is where jumps live: a flight ends and the
 * slot starts drawing the card, and if the flight's final centre, size or angle differ from
 * the resting drawing's, the swap between the two is a visible snap on one frame. The rule
 * (`docs/kotlin/CHOREOGRAPHY.md`) is that every overlay drawing converges on the *berth* —
 * the measured centre and drawn card size of the place the card lies — so the landing frame
 * and the resting card are the same pixels.
 *
 * The worst case is a side seat: its cards lie sideways, are drawn smaller than the 44dp tap
 * target that pads their boxes, and both of the old sins met there — the flight scaled to
 * the padded box rather than the card, and aligned box corners rather than card centres, so
 * a card landed seventeen pixels high and a third too large, then snapped. Both cases below
 * fail against that geometry.
 */
@OptIn(ExperimentalTestApi::class)
class LandingTest {

    @Test
    fun aFlightsLastFrameIsTheCardItBecomesAtRest() = runComposeUiTest {
        mainClock.autoAdvance = false

        val whole = teachingSession().view.value
        val me = whole.viewerId
        val victim = whole.players.first { it.id != me }
        val landing = victim.cards.size
        val during = whole.copy(
            players = whole.players.map { seat ->
                if (seat.id == victim.id) {
                    seat.copy(cards = seat.cards + CardView.Hidden)
                } else {
                    seat
                }
            },
        )
        val layout = TableLayout.forScreen(PHONE_H)
        val frames = MutableStateFlow(emptyList<Frame>())
        var scale = 1f

        setContent {
            scale = LocalDensity.current.density
            Staged(frames, during, layout)
        }
        settle()

        frames.value = listOf(
            Frame(
                action = GameAction.SelectActionTarget(
                    SelectActionTargetPayload.Ace(me, victim.id),
                ),
                scenes = listOf(listOf(Beat.Move(Anchor.Deck, Anchor.Seat(victim.id, landing)))),
                view = during,
            ),
        )

        // The flight is the one unlabelled face-down card on the table. Its bounds on the
        // last frame it exists are the landing geometry.
        var lastFlight: Rect? = null
        var flew = false
        repeat(STEPS) {
            mainClock.advanceTimeBy(STEP_MS)
            val flights = onAllNodesWithContentDescription(FACE_DOWN).fetchSemanticsNodes()
            if (flights.size == 1) {
                lastFlight = flights.single().boundsInRoot
                flew = true
            }
        }
        settle()

        val final = lastFlight
        assertTrue(flew && final != null, "the card never flew")

        val rested = onAllNodesWithContentDescription("${victim.nickname}, card ${landing + 1}")
            .fetchSemanticsNodes()
        assertTrue(rested.size == 1, "the card never landed: ${rested.size} nodes")
        val slot = rested.single().boundsInRoot

        // Centres, because the centre is the one point the padded slot box and the drawn
        // card agree on. The size against the *picture* drawn at a side seat, lying
        // sideways: the slot's own box is padded up to the tap target and cannot be compared.
        val drift = (final!!.center - slot.center).getDistance()
        assertTrue(
            drift <= SLACK_PX * scale,
            "the card jumped ${drift / scale}dp as it landed: flight $final, rest $slot",
        )

        val side = layout.sizes.side
        val expectedW = side.height.value * scale
        val expectedH = side.width.value * scale
        assertTrue(
            kotlin.math.abs(final.width - expectedW) <= SLACK_PX * scale &&
                kotlin.math.abs(final.height - expectedH) <= SLACK_PX * scale,
            "the card changed size as it landed: flew ${final.width}×${final.height}, " +
                "drawn $expectedW×$expectedH",
        )
    }

    @Test
    fun aLiftBeginsExactlyOverTheRestingCard() = runComposeUiTest {
        mainClock.autoAdvance = false

        val whole = teachingSession().view.value
        val victim = whole.players.first { it.id != whole.viewerId }
        val layout = TableLayout.forScreen(PHONE_H)
        val frames = MutableStateFlow(emptyList<Frame>())
        var scale = 1f

        setContent {
            scale = LocalDensity.current.density
            Staged(frames, whole, layout)
        }
        settle()

        val label = "${victim.nickname}, card 1"
        val resting = onAllNodesWithContentDescription(label)
            .fetchSemanticsNodes().single().boundsInRoot

        frames.value = listOf(
            Frame(
                action = GameAction.ConfirmPeek(PlayerIdPayload(whole.viewerId)),
                scenes = listOf(listOf(Beat.Peek(Anchor.Seat(victim.id, 0)))),
                view = whole,
            ),
        )

        // The first frame the card is up: it has barely risen, so it must still cover the
        // resting card it replaced — same centre, same drawn size, not the padded box's.
        var first: Rect? = null
        repeat(STEPS) {
            if (first != null) return@repeat
            mainClock.advanceTimeBy(RISE_STEP_MS)
            val up = onAllNodesWithContentDescription(FACE_DOWN).fetchSemanticsNodes()
            if (up.size == 1 && nodes(label) == 0) first = up.single().boundsInRoot
        }

        val lifted = first
        assertTrue(lifted != null, "the card never lifted")

        val drift = (lifted!!.center - resting.center).getDistance()
        assertTrue(
            drift <= RISE_SLACK_PX * scale,
            "the lift began ${drift / scale}dp away from the card it took up: " +
                "lifted $lifted, resting $resting",
        )

        val side = layout.sizes.side
        assertTrue(
            kotlin.math.abs(lifted.width - side.height.value * scale) <= SLACK_PX * scale &&
                kotlin.math.abs(lifted.height - side.width.value * scale) <= SLACK_PX * scale,
            "the card changed size as it lifted: ${lifted.width}×${lifted.height}",
        )
    }

    @Composable
    private fun Staged(
        frames: MutableStateFlow<List<Frame>>,
        view: PlayerView,
        layout: TableLayout,
    ) {
        VintoTheme {
            Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                CardStage(frames = frames, live = view, sizes = layout.sizes, pace = 1f) { shown, _ ->
                    TableScreen(
                        state = TableState(shown, tableFor(shown), null, emptyList(), 1),
                        layout = layout,
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.nodes(description: String): Int =
        onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    private companion object {
        /** The one description a card wears only while an overlay is drawing it, here. */
        const val FACE_DOWN = "a face-down card"

        const val STEP_MS = 40L
        const val RISE_STEP_MS = 16L
        const val STEPS = 120
        const val SETTLE_MS = 8_000L

        /** Within a couple of pixels: the last animation frame is a whisker from its target. */
        const val SLACK_PX = 3f

        /** The rise is sampled a frame or two in, so it is allowed a hair more. */
        const val RISE_SLACK_PX = 5f

        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
