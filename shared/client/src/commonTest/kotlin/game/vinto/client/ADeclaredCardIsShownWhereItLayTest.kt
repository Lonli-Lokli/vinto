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
 * **And then it joins the name.** The King's declared rank is held up beside the King while the
 * real card is held up at its seat, so a right call put the same rank on the table twice — a large
 * one stranded at the side and a small one crossing to the pile. Seen in the App Store preview. The
 * real card now flies from its seat *into* the declared one, and the two go on to the pile as one
 * card: the name the King said, proved by the card it was about.
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
        assertEquals(Anchor.Borrowed, (beats[flewAt] as Beat.Move).to, "it did not go to the name: $beats")
    }

    @Test
    fun theCardJoinsTheNameAndTheyGoToThePileAsOne() = runTest {
        val (session, victim, position) = aKingAimedAt(seed = 8L)
        val real = session.state.players.first { it.id == victim }.cards[position].rank

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }
        runCurrent()
        frames.clear()

        session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(session.playerId, real)))
        runCurrent()

        val beats = frames.flatMap { it.scenes.flatten() }
        val named = beats.indexOfFirst { it is Beat.Borrowed }
        val joined = beats.indexOfFirst { it is Beat.Move && it.to == Anchor.Borrowed }
        val piled = beats.indexOfFirst { it is Beat.Move && it.from == Anchor.Borrowed }

        assertTrue(named >= 0, "the King never said what it named: $beats")
        assertTrue(named < joined, "the card joined a name that was not there yet: $beats")
        assertTrue(joined < piled, "the name went to the pile before the card had joined it: $beats")
        assertEquals(Anchor.Discard, (beats[piled] as Beat.Move).to)
        assertEquals(
            1,
            beats.count { it is Beat.Move && it.to == Anchor.Discard },
            "the rank went to the pile twice — the name and the card are one card: $beats",
        )
    }

    /**
     * Each step is its own **scene**, not just a later beat. The stage starts every beat of a scene
     * at the same moment, so a flight into the name that shared a scene with the name set off
     * before the name had been drawn anywhere — there was nowhere to fly to, and the card simply
     * appeared on the pile. Filmed for the App Store preview, which is how it was found.
     */
    @Test
    fun theNameTheFlightIntoItAndTheFlightOnAreThreeScenesInThatOrder() = runTest {
        val (session, victim, position) = aKingAimedAt(seed = 8L)
        val real = session.state.players.first { it.id == victim }.cards[position].rank

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }
        runCurrent()
        frames.clear()

        session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(session.playerId, real)))
        runCurrent()

        val scenes = frames.flatMap { it.scenes }
        fun sceneOf(test: (Beat) -> Boolean) = scenes.indexOfFirst { scene -> scene.any(test) }
        val named = sceneOf { it is Beat.Borrowed }
        val joins = sceneOf { it is Beat.Move && it.to == Anchor.Borrowed }
        val piles = sceneOf { it is Beat.Move && it.from == Anchor.Borrowed }

        assertTrue(named in 0 until joins, "the card flew into a name drawn in the same scene: $scenes")
        assertTrue(joins < piles, "the name left for the pile in the same scene the card joined it: $scenes")
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
