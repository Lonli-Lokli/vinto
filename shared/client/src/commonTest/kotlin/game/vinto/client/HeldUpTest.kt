package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.projectView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the view holds up — the state half of the lift.
 *
 * A card an action has taken up stays up until the action resolves, on the game's clock
 * rather than the table's, so its lifetime cannot live in a beat: `heldUp` reads it from the
 * view, and the stage keeps lifting whatever it names. These cases pin the three properties
 * the whole fix rests on — the cards accumulate as they are aimed, they *all* come down the
 * moment the action is answered, and the face rides only where the engine sent it. The last
 * case pins the seam between the halves: every rise the choreography announces is a card the
 * view will go on holding, so the beat and the state can never disagree about what is up.
 */
class HeldUpTest {

    private suspend fun started(seed: Long = 8L): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = game.vinto.shapes.Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        return session
    }

    /** Deals [rank] to the player and plays its action, ready to be aimed. */
    private suspend fun aiming(rank: Rank): LocalGameSession {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))
        return session
    }

    private suspend fun LocalGameSession.aim(target: String, position: Int) = dispatch(
        GameAction.SelectActionTarget(
            SelectActionTargetPayload.Positional(playerId, target, position),
        ),
    )

    private fun LocalGameSession.opponent(): String =
        view.value.players.first { it.id != playerId }.id

    /** The same table through a bystander's eyes, which is what a room would send them. */
    private fun LocalGameSession.watcher() = projectView(state, WATCHER)

    @Test
    fun aQueensTwoCardsStayUpTogetherUntilTheSwapIsAnswered() = runTest {
        val session = aiming(Rank.QUEEN)
        val me = session.playerId
        val opp = session.opponent()

        session.aim(opp, 0)
        assertEquals(setOf(Anchor.Seat(opp, 0)), session.view.value.heldUp().keys)

        session.aim(me, 3)
        val held = session.view.value.heldUp()
        assertEquals(
            listOf(Anchor.Seat(opp, 0), Anchor.Seat(me, 3)),
            held.keys.toList(),
            "both cards are up, in the order they were taken",
        )
        assertTrue(
            held.values.all { it is CardView.Visible },
            "a Queen looks, so her player holds two faces",
        )
        val watched = session.watcher().heldUp()
        assertEquals(held.keys, watched.keys, "a watcher sees the same cards held up")
        assertTrue(
            watched.values.all { it is CardView.Hidden },
            "and none of the faces: $watched",
        )

        session.dispatch(GameAction.SkipQueenSwap(PlayerIdPayload(me)))
        assertEquals(
            emptyMap(),
            session.view.value.heldUp(),
            "declining the swap puts both cards down",
        )
    }

    @Test
    fun aJacksCardsAreHeldUpBlankEvenForItsOwnPlayer() = runTest {
        val session = aiming(Rank.JACK)
        val me = session.playerId
        val opp = session.opponent()

        session.aim(me, 3)
        session.aim(opp, 0)

        val held = session.view.value.heldUp()
        assertEquals(2, held.size)
        assertTrue(
            held.values.all { it is CardView.Hidden },
            "a Jack swaps blind — its player is shown two backs: $held",
        )

        session.dispatch(GameAction.ExecuteJackSwap(PlayerIdPayload(me)))
        assertEquals(emptyMap(), session.view.value.heldUp(), "the flight takes them")
    }

    @Test
    fun aPeekIsHeldForExactlyAsLongAsTheLookRuns() = runTest {
        val session = aiming(Rank.NINE)
        val opp = session.opponent()

        session.aim(opp, 0)
        val held = session.view.value.heldUp()
        assertEquals(setOf(Anchor.Seat(opp, 0)), held.keys)
        assertTrue(held.values.single() is CardView.Visible, "the peeker sees the face")
        assertTrue(
            session.watcher().heldUp().values.single() is CardView.Hidden,
            "a watcher sees that they looked, never at what",
        )

        session.dispatch(GameAction.ConfirmPeek(PlayerIdPayload(session.playerId)))
        assertEquals(emptyMap(), session.view.value.heldUp(), "the look is over")
    }

    /** Every rise the choreography announces is a card the view goes on holding. */
    @Test
    fun theRiseAndTheStateAgree() = runTest {
        val session = aiming(Rank.QUEEN)
        val me = session.playerId

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { batch -> frames += batch } }
        runCurrent()

        session.aim(session.opponent(), 0)
        session.aim(me, 3)
        runCurrent()

        val rises = frames.filter { it.action is GameAction.SelectActionTarget }
        assertEquals(2, rises.size, "two aims, two frames")
        rises.forEach { frame ->
            frame.scenes.flatten().filterIsInstance<Beat.Peek>().forEach { peek ->
                assertTrue(
                    peek.at in frame.view.heldUp(),
                    "${peek.at} rose but the view is not holding it",
                )
            }
        }
    }

    private companion object {
        /** A seat that neither acts nor is aimed at, watching everything. */
        const val WATCHER = "bot-2"
    }
}
