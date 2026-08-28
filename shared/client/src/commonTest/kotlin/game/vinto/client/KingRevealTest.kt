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
 * A King names a card, and the table is shown what it really was.
 *
 * The rules turn that card face up whether the name was right or wrong: right, and it comes
 * out of the hand onto the pile where everyone can read it; wrong, and it stays where it is
 * but everybody has seen it. Being shown it is the price of the guess, and it is what makes a
 * King worth watching from the other side of the table.
 *
 * The wrong case is the hard one, and it is why a reveal travels *beside* the move rather
 * than inside the state. The card stays face-down afterwards — everybody is expected to
 * remember it — so a state that carried the reveal would have to carry an expiry for it too,
 * in both engines and in every recorded hash. What the table saw is an event.
 */
class KingRevealTest {

    @Test
    fun namingACardWronglyTurnsItFaceUpForTheTable() = runTest {
        val session = LocalGameSession(seed = 8L, difficulty = Difficulty.EASY)
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

        val real = session.state.players.first { it.id == victim }.cards[0].rank
        val wrong = Rank.entries.first { it != real }

        val frames = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { frames += it } }
        session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(me, wrong)))
        runCurrent()

        val shown = frames.flatMap { it.scenes.flatten() }.filterIsInstance<Beat.Reveal>()
        assertEquals(1, shown.size, "one card was turned over: $shown")
        assertEquals(Anchor.Seat(victim, 0), shown.single().at, "the one that was named")
        assertEquals(real, shown.single().card.rank, "showing what it really was")
    }

    /** And it goes back down: what the table saw is a moment, not a new fact about the game. */
    @Test
    fun theCardIsFaceDownAgainAfterwards() = runTest {
        val session = LocalGameSession(seed = 8L, difficulty = Difficulty.EASY)
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
        val real = session.state.players.first { it.id == victim }.cards[0].rank
        session.dispatch(
            GameAction.DeclareKingAction(
                DeclareKingActionPayload(me, Rank.entries.first { it != real }),
            ),
        )

        val seat = session.view.value.players.first { it.id == victim }
        assertTrue(
            seat.cards[0] !is game.vinto.engine.CardView.Visible,
            "the view goes on hiding it — remembering it is the table's job",
        )
    }
}
