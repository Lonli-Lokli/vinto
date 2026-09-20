package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A bot calling Vinto stops the table, and the person in the coalition is asked before anybody
 * plays on.
 *
 * Reported from a phone: *"the bot started playing without me confirming that I understand the
 * plan. He didn't even wait until I declare my card knowledge."* The fault is one read:
 * `LocalGameSession.playBots` asked whether the window was open **once, above its loop**, and the
 * call that opens it is a move *inside* that loop — a bot ends its turn with Vinto. So for the
 * rest of that batch the session believed there was no window, and the view it published at the
 * end said so: the person was handed a final round with no window and no way to say anything,
 * and only their *next* action opened one, by which point they had already taken their turn.
 *
 * On this deal the caller is the last seat, so the person is next and the batch stops there
 * anyway. **A caller in the middle of the table is the worse half of the same fault** — every
 * coalition seat between them and the person would take its one turn in the same breath as the
 * call — which is why the claim below is about the turns as well as about the window, even
 * though this deal only ever exhibited the second.
 *
 * The declarations are the exception and always were: the window exists so the coalition can pool
 * what it knows, and one that silenced the bots is a conversation with nothing in it.
 *
 * Played forward through the real session rather than resumed into a constructed final round,
 * because a constructed one cannot exhibit this at all: the window opens correctly for a round
 * that was already final when the session was handed it. What goes wrong is the *transition*, and
 * a fixture that starts after it proves nothing.
 */
class TheCallOpensTheWindowTest {

    @Test
    fun nobodyTakesAFinalTurnInTheSameBreathAsTheCall() = runTest(timeout = WHOLE_GAME) {
        val session = LocalGameSession(seed = COALITION_SEED, difficulty = Difficulty.EASY)
        val me = session.playerId

        val seen = mutableListOf<Frame>()
        val watching = launch { session.frames.collect { seen += it } }
        // Started before anything is dispatched, and let run between laps. `dispatch` need not
        // suspend, and a collector on the test's own dispatcher that is never given the thread
        // reads nothing at all — which is how this first "passed" against a list of no frames.
        yield()

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        // One action per pass, and the loop stops the instant the call lands. A lap that sent a
        // whole turn's worth would run past the moment being measured — and `ProcessAiTurn` is
        // *acting*, so a nudge sent after the call closes the very window this is asking about.
        var acted = 0
        while (session.view.value.vintoCallerId == null && acted < MAX_ACTIONS) {
            val view = session.view.value
            val mine = view.players.getOrNull(view.currentPlayerIndex)?.id == me
            when {
                view.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE ->
                    session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))

                view.pendingAction?.playerId == me ->
                    session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))

                mine -> session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))

                // Only when the table is waiting on nobody: every dispatch above already runs
                // the bots behind it, so this is the pass where the person has nothing to do.
                else -> session.dispatch(GameAction.ProcessAiTurn(PlayerIdPayload(me)))
            }
            yield()
            acted++
        }
        watching.cancel()

        val caller = session.view.value.vintoCallerId
        assertNotNull(caller, "no bot called Vinto in $MAX_ACTIONS moves, so this proves nothing")
        assertTrue(caller != me, "the person called it themselves; there is no coalition to confer with")

        val call = seen.indexOfFirst { it.action is GameAction.CallVinto }
        assertTrue(call >= 0, "the call is not among the frames the screen was given")

        // Talk is welcome after the call; turns are not. `ProcessAiTurn` is the nudge this test
        // itself sends and moves nothing, so it is not one of somebody's turns either.
        val playedOn = seen.drop(call + 1).filter { frame ->
            frame.action !is GameAction.DeclareCards && frame.action !is GameAction.ProcessAiTurn
        }
        assertTrue(
            playedOn.isEmpty(),
            "the final round played on before the person was asked: " +
                playedOn.joinToString { it.action::class.simpleName.orEmpty() },
        )

        assertNotNull(
            session.view.value.conferMsRemaining,
            "the call did not open the window the person confers in",
        )

        // And the round is still whole: the seat on play is the caller's neighbour, so not one
        // of the three coalition turns has been spent while the person was not looking.
        val after = session.view.value
        val seats = after.players.map { it.id }
        val onPlay = seats[after.currentPlayerIndex]
        val owed = after.activeTossIn?.queuedActions.orEmpty().filter { it.playerId != caller }

        // Two positions are correct here and only these two, so the claim is about the seats
        // rather than about one index.
        //
        // Usually the call clears the window and play moves to the caller's neighbour. But a
        // throw **another seat** made is owed its action, and the call does not take it away —
        // `handleCallVinto` keeps that window, and the turn advances when the queue drains
        // instead. So the caller staying on play with somebody else's card still in the queue is
        // the round holding still, not a seat losing its turn.
        //
        // This asserted the neighbour outright, and went red the day the caller stopped being
        // allowed to call over its *own* queued throw: this deal's caller now calls a beat
        // later, with a teammate's Queen still owed. Either position leaves all three coalition
        // turns unspent, which is the thing worth holding; a *middle* coalition seat on play
        // would fail both halves, which is the thing worth catching.
        if (owed.isEmpty()) {
            assertEquals(
                seats[(seats.indexOf(caller) + 1) % seats.size],
                onPlay,
                "a coalition seat was skipped between the call and the window",
            )
        } else {
            assertEquals(
                caller,
                onPlay,
                "the round moved on while a throw was still owed its action: $owed",
            )
        }
    }

    private companion object {
        /** The deal `MarketingScene.PLAN` is staged from: a bot calls Vinto on the thirteenth lap. */
        const val COALITION_SEED = 20_260_079L

        /** A bound rather than a budget — this deal reaches the call in a fraction of it. */
        const val MAX_ACTIONS = 200
    }
}
