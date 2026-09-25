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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a King's declaration looks like: what it looks like at a table.
 *
 * The King is played, so it lies on the pile. The card it names **pops out of its hand and is
 * shown** to everybody — right or wrong, the table sees what it really was. Then the answer: a
 * right name sends the card onto the pile (played like a card from the hand if it has an action
 * of its own — shown off where it lies, then lit and swelling on the way), and a wrong one puts it
 * **back in the hand**, and the penalty card follows.
 *
 * Reported from a phone, twice. First: *"this enlargement happened directly on side … it should
 * start move from pulled space, to let everyone see it first."* Then, of a version that held the
 * declared rank up as a second, borrowed card beside the King: *"we should have exactly like in
 * real life"* — and at a table there is no second card. The King's claim is said, the card
 * answers it, and the ring on the King says which way it went.
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

    private suspend fun TestScope.declared(rank: (Rank) -> Rank): Pair<List<Scene>, Anchor.Seat> {
        val (session, victim, position) = aKingAimedAt(seed = 8L)
        val real = session.state.players.first { it.id == victim }.cards[position].rank

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }
        runCurrent()
        frames.clear()

        session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(session.playerId, rank(real))))
        runCurrent()
        return frames.flatMap { it.scenes } to Anchor.Seat(victim, position)
    }

    private fun List<Scene>.sceneOf(test: (Beat) -> Boolean) = indexOfFirst { scene -> scene.any(test) }

    @Test
    fun aRightNameIsShownThenJudgedThenGoesOntoThePile() = runTest {
        val (scenes, seat) = declared { it }

        val shown = scenes.sceneOf { it is Beat.Reveal && it.at == seat }
        val judged = scenes.sceneOf { it is Beat.Verdict && it.correct }
        val piled = scenes.sceneOf { it is Beat.Move && it.from == seat && it.to == Anchor.Discard }

        assertTrue(shown >= 0, "the card never popped out to be shown: $scenes")
        assertTrue(shown < judged, "the King was judged before the table saw the card: $scenes")
        assertTrue(judged < piled, "the card left before the table was told it was right: $scenes")
    }

    @Test
    fun aWrongNameIsShownThenJudgedThenGoesBackAndCostsACard() = runTest {
        val (scenes, seat) = declared { real -> Rank.entries.first { it != real } }

        val shown = scenes.sceneOf { it is Beat.Reveal && it.at == seat }
        val judged = scenes.sceneOf { it is Beat.Verdict && !it.correct }
        val fined = scenes.sceneOf { it is Beat.Move && it.from == Anchor.Deck }

        assertTrue(shown >= 0, "the table was not shown what the card really was: $scenes")
        assertTrue(shown < judged, "the King was judged before the table saw the card: $scenes")
        assertTrue(judged < fined, "the penalty came before the verdict it is for: $scenes")
        assertTrue(
            scenes.flatten().none { it is Beat.Move && it.from == seat },
            "a wrongly named card left its hand: $scenes",
        )
    }
}
