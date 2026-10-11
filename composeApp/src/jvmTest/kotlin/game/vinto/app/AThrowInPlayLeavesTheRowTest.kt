package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.engine.GameEngine
import game.vinto.engine.PlayerView
import game.vinto.engine.ReduceResult
import game.vinto.engine.cardInPlay
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SwapHandWithDeckPayload
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A thrown card whose action is being played is on the pile, and only on the pile.
 *
 * Reported 2026-10-09 from a phone, with a screenshot: *"tossed in card now animates to discard
 * when it's turn, but also it stays in toss in area."* Dune's King was being played — named in
 * the panel, on the pile — and the Tossed row under the pile still showed it, "Tossed (1)", as a
 * card waiting its turn.
 *
 * The engine keeps a thrown card at the head of its queue for as long as its action runs, which
 * is how it knows a throw is in flight; the row drew the queue as it stood. It draws the throws
 * still to come.
 */
@OptIn(ExperimentalTestApi::class)
class AThrowInPlayLeavesTheRowTest {

    @Test
    fun aThrownCardBeingPlayedIsNoLongerWaitingInTheRow() {
        var state = ready()
        fun act(action: GameAction) {
            state = (GameEngine.reduce(state, action) as? ReduceResult.Success)?.state
                ?: error("${action.type} refused")
        }

        act(GameAction.SwapHandWithDeck(SwapHandWithDeckPayload(ME, 3, Rank.SEVEN)))
        act(GameAction.SetNextDrawCard(RankPayload(Rank.SEVEN)))
        act(GameAction.DrawCard(PlayerIdPayload(ME)))
        act(GameAction.DiscardCard(PlayerIdPayload(ME)))
        act(GameAction.ParticipateInTossIn(ParticipateInTossInPayload(ME, listOf(3))))

        val waiting = projectView(state, ME)
        val thrown = "${waiting.players.first { it.id == ME }.nickname} threw 7"
        assertEquals(1, shown(waiting, thrown), "while the window is open the 7 waits in the row")

        for (seat in listOf("bot-1", "bot-2", "bot-3", ME)) {
            act(GameAction.PlayerTossInFinished(PlayerIdPayload(seat)))
        }
        val playing = projectView(state, ME)
        assertEquals(Rank.SEVEN, playing.cardInPlay?.rank, "the staging is a thrown 7 being played")
        assertEquals(1, playing.activeTossIn?.queuedActions?.size, "and the engine still holds it at the head")

        assertEquals(0, shown(playing, thrown), "the 7 is on the pile, and still waiting in the row")
    }

    /** How many times the table draws a card described as [label]. */
    private fun shown(view: PlayerView, label: String): Int {
        var count = 0
        runComposeUiTest {
            setContent {
                VintoTheme {
                    CompositionLocalProvider(LocalStage provides Stage()) {
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
            count = onAllNodes(hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().size
        }
        return count
    }

    private fun ready(): GameState {
        var state = initializeGame(SEED, Difficulty.EASY)
        for (action in listOf(
            GameAction.PeekSetupCard(PositionPayload(ME, 0)),
            GameAction.PeekSetupCard(PositionPayload(ME, 1)),
            GameAction.FinishSetup(PlayerIdPayload(ME)),
        )) {
            state = (GameEngine.reduce(state, action) as ReduceResult.Success).state
        }
        return state
    }

    private companion object {
        const val SEED = 7L
        const val ME = "human-1"
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
