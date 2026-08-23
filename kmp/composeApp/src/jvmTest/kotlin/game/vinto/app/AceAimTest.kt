package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
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
import game.vinto.client.Attention
import game.vinto.client.Beat
import game.vinto.client.Frame
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.shapes.GameAction
import game.vinto.shapes.SelectActionTargetPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An Ace names a victim, and the table keeps pointing at them until the card lands.
 *
 * The naming is the only part of the move nobody can reconstruct afterwards: the card arrives
 * face-down, so a player who missed *who* was chosen sees a hand quietly grow by one and has
 * no way to find out whose Ace did it. It is also the one action whose whole effect happens to
 * somebody other than the player taking it.
 *
 * The ring used to be cleared at the end of every scene, and an Ace is two scenes — the naming,
 * then the card flying. So the mark went out at exactly the moment the player looked up to see
 * who had been named, and came back a beat later as if it were a second event. What this holds
 * is that it is one mark, unbroken: the sequence of frames where the seat is pointed at has one
 * run, not two.
 */
@OptIn(ExperimentalTestApi::class)
class AceAimTest {

    @Test
    fun theVictimIsMarkedFromTheNamingUntilTheCardLands() = runComposeUiTest {
        mainClock.autoAdvance = false

        val whole = teachingSession().view.value
        val victim = whole.players.first { it.id != whole.viewerId }
        // The table as it is while the penalty flies: the hand has already grown by the card
        // that is still in the air, which is what leaves a gap for it to land in.
        val during = whole.copy(
            players = whole.players.map { seat ->
                if (seat.id == victim.id) seat.copy(cards = seat.cards + CardView.Hidden) else seat
            },
        )
        val landing = Anchor.Seat(victim.id, victim.cards.size)
        val layout = TableLayout.forScreen(PHONE_H)
        val frames = MutableStateFlow(emptyList<Frame>())

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(frames = frames, live = whole, sizes = layout.sizes, pace = 1f) { shown ->
                        TableScreen(
                            state = TableState(shown, tableFor(shown), null, emptyList(), 1),
                            layout = layout,
                            onMove = {},
                            onHelp = {},
                            onReport = {},
                            onDeck = {},
                        )
                    }
                }
            }
        }
        settle()

        // The two scenes an Ace produces: it names somebody, and then a card goes to them.
        frames.value = listOf(
            Frame(
                action = GameAction.SelectActionTarget(
                    SelectActionTargetPayload.Ace(whole.viewerId, victim.id),
                ),
                scenes = listOf(
                    listOf(
                        Beat.Attend(victim.id, Attention.PENALTY),
                        Beat.Say(victim.id, "Drawing…"),
                    ),
                    listOf(
                        Beat.Move(Anchor.Deck, landing),
                        Beat.Flinch(landing),
                        Beat.Attend(victim.id, Attention.PENALTY),
                    ),
                ),
                view = during,
            ),
        )

        val marked = "${victim.nickname} is taking a card from the deck"
        val seen = mutableListOf<Boolean>()
        repeat(STEPS) {
            mainClock.advanceTimeBy(STEP_MS)
            seen += nodes(marked) > 0
        }

        assertTrue(seen.any { it }, "the Ace never pointed at anybody")
        val runs = seen.filterIndexed { i, marked -> marked && (i == 0 || !seen[i - 1]) }.size
        assertEquals(
            1,
            runs,
            "the mark blinked $runs times: the table stopped pointing at the victim part-way " +
                "through, then pointed again",
        )
        assertTrue(seen.last().not(), "the mark never went away; it is a moment, not a state")
    }

    private fun ComposeUiTest.nodes(description: String): Int =
        onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    private companion object {
        const val STEP_MS = 50L
        const val STEPS = 90
        const val SETTLE_MS = 4_000L
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
