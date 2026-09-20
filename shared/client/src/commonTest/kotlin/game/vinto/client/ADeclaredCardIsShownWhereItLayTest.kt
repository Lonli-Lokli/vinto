package game.vinto.client

import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.Difficulty
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
 * What a King's declaration looks like, from the seat the card leaves.
 *
 * Reported from a phone: *"declared card (eg ace) pulled out of hand, then animated to enlarged
 * state and then went to discard. And this enlargement happened directly on side — I think more
 * natural will be enlarged card moving towards discard? It should start move from pulled space,
 * to let everyone see it first. Also no animation if declared wrong as it goes back to hand and
 * not played."*
 *
 * The card did fly from its seat to the pile, and `InFlight` swells a `shown` flight as it goes —
 * but the swell peaks at the **midpoint**, and for a seat at the side of the table the midpoint
 * is still over by that seat. So the card appeared to grow off to one side and then set off,
 * which is not what it was doing and is not what a table does either. At a table the card is
 * held up where it was taken from, so everybody can see which card it was and whose, and *then*
 * it goes on the pile.
 *
 * So the reveal comes first, at the seat, and the flight picks the card up out of the air — a
 * hand-off `Stage.fly` already supports: *"a card the flight is taking out of the air is released
 * in the same call that starts the flight, and the flight sets off from where the card is
 * hovering"*.
 *
 * And a **wrong** name plays nothing. The card stays in the hand, so nothing should be staged as
 * though it had left: the borrowed rank the King was pretending to be used to grow at the middle
 * of the table either way, which reads as a card being played when none was.
 */
class ADeclaredCardIsShownWhereItLayTest {

    private suspend fun aKingAimedAt(seed: Long): Triple<LocalGameSession, String, Int> {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.KING)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))

        val victim = session.view.value.players.first { it.id != me }.id
        session.dispatch(
            GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(me, victim, 0)),
        )
        return Triple(session, victim, 0)
    }

    @Test
    fun aCorrectNameHoldsTheCardUpWhereItLayBeforeItTravels() = runTest {
        val (session, victim, position) = aKingAimedAt(seed = 8L)
        val real = session.state.players.first { it.id == victim }.cards[position].rank

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }
        runCurrent()
        frames.clear()

        session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(session.playerId, real)))
        runCurrent()

        val seat = Anchor.Seat(victim, position)
        val beats = frames.flatMap { it.scenes.flatten() }
        val shownAt = beats.indexOfFirst { it is Beat.Reveal && it.at == seat }
        val flewAt = beats.indexOfFirst { it is Beat.Move && it.from == seat }

        assertTrue(shownAt >= 0, "the card was never held up where it lay: $beats")
        assertTrue(flewAt >= 0, "the card never went to the pile: $beats")
        assertTrue(shownAt < flewAt, "it set off before the table had seen it: $beats")
        assertEquals(Anchor.Discard, (beats[flewAt] as Beat.Move).to)
    }

    @Test
    fun aWrongNameStagesNothingBecauseNothingWasPlayed() = runTest {
        val (session, victim, position) = aKingAimedAt(seed = 8L)
        val real = session.state.players.first { it.id == victim }.cards[position].rank
        val wrong = Rank.entries.first { it != real }

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }
        runCurrent()
        frames.clear()

        session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(session.playerId, wrong)))
        runCurrent()

        val beats = frames.flatMap { it.scenes.flatten() }
        assertTrue(
            beats.none { it is Beat.Borrowed },
            "the King's borrowed rank was staged for a call that played nothing: $beats",
        )
        // The reveal stays: the table is owed what the card really was, which is the whole
        // cost of a wrong guess (`KingRevealTest`).
        assertTrue(
            beats.any { it is Beat.Reveal && it.at == Anchor.Seat(victim, position) },
            "the table was not shown what the card really was: $beats",
        )
    }
}
