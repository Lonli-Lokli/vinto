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
import game.vinto.client.Move
import game.vinto.client.Pacing
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The table never shows a move before it plays it.
 *
 * A session publishes the view its whole dispatch arrived at *before* the frames that get
 * there — it has to, because the game really has moved on and the frames are how the screen
 * catches up. The stage covers that by drawing the table each frame left behind rather than
 * the live one… but only from the moment it *steps* to the first frame, and the step comes
 * after the beat where a person would be thinking (`Pacing.THINK_MS`, two thirds of a second
 * before another seat's move).
 *
 * So for that beat the screen was drawing the live view: the whole of the bots' turns, already
 * done. The card a bot was about to discard sat on the pile, and then vanished when the frames
 * finally rewound the table and put it back by flying it there. Reported as *"on a bot's turn
 * I see for a second the card he will discard, before he discards it"* — and it is the reason
 * the hold below exists, rather than the beat being made shorter.
 */
@OptIn(ExperimentalTestApi::class)
class ThinkingAheadTest {

    @Test
    fun theBeatBeforeABotsMoveShowsTheTableAsItWasNotAsItWillBe() = runComposeUiTest {
        val before = teachingSession().view.value
        // Where the dispatch has already arrived: the bot's card is on the pile.
        val after = before.copy(discardTop = Discarded, discardCount = 1)

        val frames = MutableSharedFlow<List<Frame>>(extraBufferCapacity = 4)
        var drawn: PlayerView? = null
        // The session's own `StateFlow`, in miniature: the screen watches it, and it jumps to
        // where the dispatch arrived the moment the bots have finished searching.
        val live = mutableStateOf(before)

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

        // The order the session publishes in: the batch the bots' turns produced, and then the
        // view they arrived at. Reversed — which is how it was — the assertion below fails.
        frames.tryEmit(listOf(botsTurn(before)))
        live.value = after
        mainClock.advanceTimeBy(INTO_THE_BEAT)

        assertEquals(
            before.discardTop,
            drawn?.discardTop,
            "during the beat before a bot's move the table showed the move already made",
        )
    }

    @Test
    fun theControlsDoNotFlickerOnDuringTheBeatEither() = runComposeUiTest {
        // The same bug seen from the rail. Every control on the table is derived from the view
        // the screen is handed, so a view that jumped to the end of the bots' turns offered the
        // player *their own* turn's buttons — "Draw card" appearing for the beat, vanishing
        // when the frames rewound the table, and coming back when they finished. Reported
        // separately as the controls blinking, and it is one fault, not two.
        // A round under way, not the setup the lesson's deal starts in: a setup table offers no
        // controls to anybody, so a fixture left there would pass this whether or not the bug
        // was present. The first assertion below is what says so.
        val playing = teachingSession().view.value.copy(phase = GamePhase.PLAYING, subPhase = GameSubPhase.IDLE)
        val onABotsTurn = playing.copy(
            currentPlayerIndex = playing.players.indexOfFirst { it.id != playing.viewerId },
        )
        val mine = playing.copy(
            currentPlayerIndex = playing.players.indexOfFirst { it.id == playing.viewerId },
        )

        val frames = MutableSharedFlow<List<Frame>>(extraBufferCapacity = 4)
        val live = mutableStateOf(onABotsTurn)
        var offered: List<Move> = emptyList()

        mainClock.autoAdvance = false
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(
                        frames = frames,
                        live = live.value,
                        sizes = TableLayout.forScreen(PHONE_H).sizes,
                        pace = 1f,
                    ) { view, _ -> offered = tableFor(view).choices.map { it.move } }
                }
            }
        }
        mainClock.advanceTimeBy(SETTLE_MS)
        assertTrue(offered.none { it is Move.Send }, "a bot's turn already offered this seat a move")
        assertTrue(
            tableFor(mine).choices.any { it.move is Move.Send },
            "the fixture's own turn offers no control, so this case could never fail",
        )

        frames.tryEmit(listOf(botsTurn(onABotsTurn)))
        live.value = mine
        mainClock.advanceTimeBy(INTO_THE_BEAT)

        assertTrue(
            offered.none { it is Move.Send },
            "the rail offered this seat's own controls during a bot's beat: $offered",
        )
    }

    /** One bot move with something to see, by a seat that is not the viewer — so it gets the beat. */
    private fun botsTurn(before: PlayerView): Frame {
        val bot = before.players.first { it.id != before.viewerId }.id
        return Frame(
            action = GameAction.DiscardCard(PlayerIdPayload(bot)),
            scenes = listOf(
                listOf(
                    Beat.Move(
                        card = Discarded,
                        from = Anchor.Seat(bot, 0),
                        to = Anchor.Discard,
                    ),
                ),
            ),
            view = before,
        )
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp

        /** The card the bot is about to put down, and must not be seen putting down early. */
        val Discarded = Card("late", Rank.QUEEN, 10, played = false, actionText = null)

        /** Long enough for the stage to settle and start collecting. */
        const val SETTLE_MS = 200L

        /** Part-way into the beat, so the move is still to come. */
        const val INTO_THE_BEAT = Pacing.THINK_MS / 2
    }
}
