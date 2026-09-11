package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CardStage
import game.vinto.app.game.TableLayout
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Anchor
import game.vinto.client.Beat
import game.vinto.client.Frame
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A card thrown while the table is already moving joins the scramble.
 *
 * A toss-in window belongs to the whole table at once: the card lands, and everybody holding a
 * match throws it. `tossedTogether` already knows that and merges throws so they leave in one
 * scene — but only throws that arrive in the *same* batch, which means only the bots'. A
 * person's own move is emitted alone and before the bots (`LocalGameSession.dispatch` says
 * why), so a throw made while somebody else's card is still crossing the felt could never join
 * it: it waited out the flight, the read beat after it, and only then left the hand.
 *
 * Reported from a phone as a tap that does nothing — and the tap is not lost, which is what
 * made it hard to place. The engine takes it at once; it is the picture that queues. So the
 * player sees no answer, and a player who sees no answer taps again.
 *
 * Held at the stage rather than through a screen because what is wrong is the *timing* of the
 * drawing, and a stage takes its frames and its clock directly.
 */
@OptIn(ExperimentalTestApi::class)
class ThrownTogetherTest {

    @Test
    fun aThrowMadeWhileTheTableIsMovingDoesNotWaitOutTheMoveItLandedIn() = runComposeUiTest {
        val start = teachingSession().view.value
        val bot = start.players.first { it.id != start.viewerId }.id
        val afterTheBot = start.copy(discardTop = Thrown, discardCount = 1)
        val afterMine = afterTheBot.copy(discardCount = 2)

        val frames = MutableSharedFlow<List<Frame>>(extraBufferCapacity = 4)
        val live = mutableStateOf(start)
        var drawn: PlayerView? = null

        mainClock.autoAdvance = false
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(
                        frames = frames,
                        live = live.value,
                        sizes = TableLayout.forScreen(PHONE_H).sizes,
                        pace = 1f,
                    ) { view, _ -> drawn = view }
                }
            }
        }
        mainClock.advanceTimeBy(SETTLE_MS)

        // A bot throws into the window; its card starts crossing the felt.
        frames.tryEmit(listOf(throwFrom(bot, afterTheBot)))
        live.value = afterTheBot
        mainClock.advanceTimeBy(INTO_THE_FLIGHT)

        // And now this seat throws too, which is what the window is for.
        frames.tryEmit(listOf(throwFrom(start.viewerId, afterMine)))
        live.value = afterMine
        mainClock.advanceTimeBy(A_MOMENT)

        assertEquals(
            afterMine,
            drawn,
            "the throw waited out the flight it was thrown into rather than joining it",
        )
    }

    /**
     * Two hands that came down before the first had been drawn come down *together*.
     *
     * The other half of the model, and the one the merge is named for: a window that resolves
     * one card at a time reads as four people taking turns, not as a scramble. The engine still
     * resolves them one at a time — it must, since a wrong throw costs a card and the order
     * decides who pays — so what is asserted is that the table in between is never drawn.
     */
    @Test
    fun twoHandsThatCameDownTogetherAreDrawnTogether() = runComposeUiTest {
        val start = teachingSession().view.value
        val bot = start.players.first { it.id != start.viewerId }.id
        val afterTheBot = start.copy(discardTop = Thrown, discardCount = 1)
        val afterMine = afterTheBot.copy(discardCount = 2)

        val frames = MutableSharedFlow<List<Frame>>(extraBufferCapacity = 4)
        val live = mutableStateOf(start)
        val seen = mutableListOf<PlayerView>()

        mainClock.autoAdvance = false
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(
                        frames = frames,
                        live = live.value,
                        sizes = TableLayout.forScreen(PHONE_H).sizes,
                        pace = 1f,
                    ) { view, _ -> if (seen.lastOrNull() != view) seen += view }
                }
            }
        }
        mainClock.advanceTimeBy(SETTLE_MS)

        // Both throws are in before either has been drawn — the beat before a bot's move is
        // long enough for a person to react in, which is the whole of what a window is.
        frames.tryEmit(listOf(throwFrom(bot, afterTheBot)))
        frames.tryEmit(listOf(throwFrom(start.viewerId, afterMine)))
        live.value = afterMine
        mainClock.advanceTimeBy(PAST_THE_FLIGHT)

        assertEquals(afterMine, seen.lastOrNull(), "neither throw was drawn")
        assertTrue(
            afterTheBot !in seen,
            "the throws were drawn one at a time: the table between them was on screen",
        )
    }

    /** One card leaving [seat] for the discard: a throw, which is what merges. */
    private fun throwFrom(seat: String, after: PlayerView) = Frame(
        action = GameAction.ParticipateInTossIn(ParticipateInTossInPayload(seat, listOf(0))),
        scenes = listOf(listOf(Beat.Move(card = Thrown, from = Anchor.Seat(seat, 0), to = Anchor.Discard))),
        view = after,
    )

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp

        val Thrown = Card("thrown", Rank.SEVEN, 7, played = false, actionText = null)

        const val SETTLE_MS = 200L

        /** Past the beat before the bot's move and into its card's flight. */
        const val INTO_THE_FLIGHT = 900L

        /**
         * Long enough for a throw to be drawn, far short of the read beat that used to hold it.
         *
         * The old path cost the rest of the flight plus `Pacing.READ_MS`, which is well over a
         * second — so this is a margin, not a race.
         */
        const val A_MOMENT = 400L

        /** Past the beat, the flight and the pause after it: everything has been drawn by here. */
        const val PAST_THE_FLIGHT = 4_000L
    }
}
